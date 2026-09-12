package audit

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"regexp"
	"slices"
	"sync"
	"time"

	"github.com/google/uuid"

	"agent-host/model"
)

const (
	defaultPollInterval    = 2 * time.Second
	defaultProcessSnapshot = 60 * time.Second
	defaultDigestLimit     = int64(64 << 20)
	defaultBatchEvents     = 100
	defaultBatchBytes      = 128 << 10
)

type enqueueFunc func(model.AuditData) error

type Monitor struct {
	policy    model.AuditPolicy
	store     *stateStore
	state     state
	bootID    string
	enqueue   enqueueFunc
	done      chan struct{}
	closeOnce sync.Once
	mu        sync.Mutex
	excluded  []*regexp.Regexp
	lastProc  time.Time
}

func New(dataDir string, policy model.AuditPolicy, enqueue enqueueFunc) (*Monitor, error) {
	if enqueue == nil {
		return nil, errors.New("audit enqueue callback is required")
	}
	policy = normalizePolicy(policy)
	store, err := newStateStore(dataDir)
	if err != nil {
		return nil, err
	}
	current, err := store.load()
	if err != nil {
		return nil, fmt.Errorf("load audit state: %w", err)
	}
	bootID := platformBootID()
	if bootID == "" {
		bootID = "unknown"
	}
	monitor := &Monitor{policy: policy, store: store, state: current, bootID: bootID, enqueue: enqueue, done: make(chan struct{})}
	for _, expression := range policy.ExcludeProcesses {
		if compiled, err := regexp.Compile(expression); err == nil {
			monitor.excluded = append(monitor.excluded, compiled)
		}
	}
	return monitor, nil
}

func ValidateConfig(config model.AuditConfig) error {
	if !slices.Equal(config.Events, Columns) {
		return errors.New("auditConfig.events must contain the fixed 18 AuditData columns in protocol order")
	}
	return nil
}

func normalizePolicy(policy model.AuditPolicy) model.AuditPolicy {
	if policy.PollIntervalSeconds < 1 || policy.PollIntervalSeconds > 300 {
		policy.PollIntervalSeconds = int(defaultPollInterval / time.Second)
	}
	if policy.ProcessSnapshotSeconds < 5 || policy.ProcessSnapshotSeconds > 86400 {
		policy.ProcessSnapshotSeconds = int(defaultProcessSnapshot / time.Second)
	}
	if policy.DigestMaxBytes <= 0 || policy.DigestMaxBytes > 1<<30 {
		policy.DigestMaxBytes = defaultDigestLimit
	}
	if policy.BatchMaxEvents <= 0 || policy.BatchMaxEvents > defaultBatchEvents {
		policy.BatchMaxEvents = defaultBatchEvents
	}
	if policy.BatchMaxBytes <= 0 || policy.BatchMaxBytes > defaultBatchBytes {
		policy.BatchMaxBytes = defaultBatchBytes
	}
	if policy.Categories == nil {
		policy.Categories = map[string]bool{}
		for name := range categories {
			policy.Categories[name] = true
		}
	}
	return policy
}

func (m *Monitor) Run(ctx context.Context) {
	defer close(m.done)
	if !m.policy.Enabled {
		return
	}
	now := time.Now()
	var initial []Event
	if m.state.Initialized && !m.state.CleanShutdown {
		e := NewEvent(m.bootID, "audit-state", "unclean:"+m.state.BootID, "device", "unclean_shutdown", "detected", now)
		e.Detail = JSONDetail(map[string]any{"previousBootId": m.state.BootID, "currentBootId": m.bootID})
		initial = append(initial, e)
	}
	start := NewEvent(m.bootID, "agent-host", "start:"+m.bootID, "device", "start", "success", now)
	start.Process = "agent-host"
	initial = append(initial, start)
	m.state.CleanShutdown = false
	m.state.BootID = m.bootID
	_ = m.store.save(m.state)
	if err := m.collect(now, !m.state.Initialized, &initial); err != nil {
		initial = append(initial, m.capabilityOnce("snapshot", err.Error(), "process,network,config_file,business_file")...)
	}
	m.state.Initialized = true
	m.persist(initial)

	ticker := time.NewTicker(time.Duration(m.policy.PollIntervalSeconds) * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			m.shutdown()
			return
		case now := <-ticker.C:
			var events []Event
			if err := m.collect(now, false, &events); err != nil {
				events = append(events, m.capabilityOnce("snapshot", err.Error(), "process,network,config_file,business_file")...)
			}
			m.persist(events)
		}
	}
}

func (m *Monitor) Done() <-chan struct{} { return m.done }

func (m *Monitor) shutdown() {
	m.closeOnce.Do(func() {
		e := NewEvent(m.bootID, "agent-host", "shutdown:"+time.Now().UTC().Format(time.RFC3339Nano), "device", "shutdown", "success", time.Now())
		e.Process = "agent-host"
		if err := m.emit([]Event{e}); err != nil {
			_ = m.store.save(m.state)
			return
		}
		m.state.CleanShutdown = true
		_ = m.store.save(m.state)
	})
}

func (m *Monitor) persist(events []Event) {
	if err := m.emit(events); err != nil {
		if restored, loadErr := m.store.load(); loadErr == nil {
			m.state = restored
		}
		return
	}
	_ = m.store.save(m.state)
}

func (m *Monitor) collect(now time.Time, baseline bool, events *[]Event) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	var failures []error
	if err := m.collectFiles(now, baseline, events); err != nil {
		failures = append(failures, err)
	}
	if err := m.collectProcesses(now, baseline, events); err != nil {
		failures = append(failures, err)
	}
	if err := m.collectConnections(now, baseline, events); err != nil {
		failures = append(failures, err)
	}
	native, err := platformEvents(&m.state, m.bootID, now, baseline)
	if err != nil {
		failures = append(failures, err)
	} else {
		*events = append(*events, native...)
	}
	return errors.Join(failures...)
}

func (m *Monitor) emit(events []Event) error {
	rows := make([][]string, 0, len(events))
	for _, event := range events {
		if !m.policy.Categories[event.Category] {
			continue
		}
		rows = append(rows, event.Row())
	}
	for len(rows) > 0 {
		count := m.policy.BatchMaxEvents
		if count > len(rows) {
			count = len(rows)
		}
		for count > 1 && batchSize(rows[:count]) > m.policy.BatchMaxBytes {
			count--
		}
		if batchSize(rows[:count]) > m.policy.BatchMaxBytes {
			rows = rows[1:]
			continue
		}
		fact := model.AuditData{Type: "AuditData", BuildDate: time.Now().Format("2006-01-02 15:04:05"), BatchID: uuid.NewString(), Events: rows[:count]}
		if err := m.enqueue(fact); err != nil {
			return err
		}
		rows = rows[count:]
	}
	return nil
}

func batchSize(rows [][]string) int { encoded, _ := json.Marshal(rows); return len(encoded) }

func (m *Monitor) capability(source, reason, affected string) Event {
	now := time.Now()
	e := NewEvent(m.bootID, source, stableKey(source, reason, affected, now.UTC().Format("2006-01-02")), "capability", "degraded", "unavailable", now)
	e.Detail = JSONDetail(map[string]any{"platform": platformName(), "missingSource": source, "reason": reason, "affectedCategories": affected})
	return e
}

func (m *Monitor) capabilityOnce(source, reason, affected string) []Event {
	key := "capability:" + stableKey(source, reason, affected)
	if _, exists := m.state.Cursors[key]; exists {
		return nil
	}
	m.state.Cursors[key] = cursor{ID: key}
	return []Event{m.capability(source, reason, affected)}
}
