package cmd

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"math/rand"
	"os"
	"time"

	"agent-host/audit"
	log "agent-host/log_zap"
	"agent-host/model"
	"agent-host/probe"
	"agent-host/storage"
	"agent-host/transport"
)

const maxEnvelopeBytes = 256 << 10

type schedule struct{ collectEvery, collectFor, uploadEvery, uploadFor time.Duration }

func parseSchedule(values []float64) (schedule, error) {
	if len(values) != 5 {
		return schedule{}, errors.New("startConfig must contain exactly 5 values")
	}
	for _, value := range values {
		if math.IsNaN(value) || math.IsInf(value, 0) || value < 0 {
			return schedule{}, errors.New("startConfig values must be finite and non-negative")
		}
	}
	if values[0] != 1 {
		return schedule{}, errors.New("collection is disabled by server")
	}
	durations := make([]time.Duration, 4)
	for index, value := range values[1:] {
		if value <= 0 || value > float64(time.Duration(1<<63-1))/float64(time.Second) {
			return schedule{}, errors.New("startConfig intervals and windows must be positive, representable durations")
		}
		durations[index] = time.Duration(value * float64(time.Second))
		if durations[index] <= 0 {
			return schedule{}, errors.New("startConfig duration is below clock resolution")
		}
	}
	return schedule{durations[0], durations[1], durations[2], durations[3]}, nil
}

func StartProbe(ctx context.Context, config *model.ProbeCfg, session model.Session, client *transport.Client, spool *storage.Spool) error {
	if config == nil {
		return errors.New("probe config is nil")
	}
	timing, err := parseSchedule(config.StartConfig)
	if err != nil {
		return err
	}
	if config.AuditPolicy.Enabled {
		if err := audit.ValidateConfig(config.AuditConfig); err != nil {
			return err
		}
	}
	collectCtx, cancelCollect := context.WithTimeout(ctx, timing.collectFor)
	defer cancelCollect()
	go collectLoop(collectCtx, timing.collectEvery, config, session, spool)
	go uploadLoop(ctx, timing.uploadEvery, client, spool)
	auditMonitor, err := audit.New(spool.Root(), config.AuditPolicy, func(fact model.AuditData) error {
		return enqueueFact(session, spool, fact)
	})
	if err != nil {
		return fmt.Errorf("initialize audit monitor: %w", err)
	}
	go auditMonitor.Run(ctx)
	<-ctx.Done()
	select {
	case <-auditMonitor.Done():
	case <-time.After(4 * time.Second):
		log.W("audit monitor shutdown timed out")
	}
	drainCtx, cancelDrain := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancelDrain()
	flush(drainCtx, client, spool)
	return nil
}

func collectLoop(ctx context.Context, interval time.Duration, config *model.ProbeCfg, session model.Session, spool *storage.Spool) {
	collectOnce(config, session, spool)
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			collectOnce(config, session, spool)
		}
	}
}

func collectOnce(config *model.ProbeCfg, session model.Session, spool *storage.Spool) {
	for _, fact := range probe.GetData(config) {
		if err := enqueueFact(session, spool, fact); err != nil {
			log.E("enqueue collector record failed: %v", err)
		}
	}
}

func enqueueFact(session model.Session, spool *storage.Spool, fact interface{}) error {
	encoded, err := json.Marshal(fact)
	if err != nil {
		return err
	}
	if len(encoded) > maxEnvelopeBytes {
		return errors.New("collector record exceeds 256 KiB envelope limit")
	}
	return spool.EnqueueFact(session, fact)
}

func uploadLoop(ctx context.Context, interval time.Duration, client *transport.Client, spool *storage.Spool) {
	delay := time.Second
	for {
		retry := flush(ctx, client, spool)
		wait := interval
		if retry {
			jitter := time.Duration(rand.Int63n(int64(delay/2 + 1)))
			wait = delay + jitter
			if delay < 5*time.Minute {
				delay *= 2
			}
			if delay > 5*time.Minute {
				delay = 5 * time.Minute
			}
		} else {
			delay = time.Second
		}
		select {
		case <-ctx.Done():
			return
		case <-time.After(wait):
		}
	}
}

func flush(ctx context.Context, client *transport.Client, spool *storage.Spool) bool {
	for {
		select {
		case <-ctx.Done():
			return false
		default:
		}
		item, err := spool.Next()
		if err != nil {
			log.W("read spool failed: %v", err)
			return true
		}
		if item == nil {
			return false
		}
		prepared, err := spool.PrepareUpload(item)
		if err != nil {
			if errors.Is(err, storage.ErrInvalidTrackingRecord) {
				log.W("invalid queued tracking record moved to dead-letter: %v", err)
				if rejectErr := spool.Reject(item); rejectErr != nil {
					log.E("move invalid record to dead-letter failed: %v", rejectErr)
					return true
				}
				continue
			}
			log.W("prepare upload attempt failed: %v", err)
			return true
		}
		response, err := client.Upload(prepared)
		if err == nil {
			outcome, ackErr := classifyUploadAck(prepared, response)
			if ackErr != nil {
				log.W("upload acknowledgement invalid; retaining message: %v", ackErr)
				return true
			}
			notify(response)
			switch outcome {
			case ackAccepted, ackDuplicate:
				if err := spool.Ack(item); err != nil {
					log.W("ack spool item failed: %v", err)
					return true
				}
				continue
			case ackRetryable:
				return true
			case ackPermanent:
				if err := spool.Reject(item); err != nil {
					log.E("move permanently rejected record to dead-letter failed: %v", err)
					return true
				}
				continue
			}
		}
		if transport.IsRetryable(err) {
			log.W("upload deferred: %v", err)
			return true
		}
		log.W("upload permanently rejected: %v", err)
		if err := spool.Reject(item); err != nil {
			log.E("move record to dead-letter failed: %v", err)
			return true
		}
	}
}

type ackOutcome int

const (
	ackAccepted ackOutcome = iota
	ackDuplicate
	ackRetryable
	ackPermanent
)

func classifyUploadAck(record model.QueueRecord, response model.Resp) (ackOutcome, error) {
	if record.Envelope.Meta == nil || response.Ack == nil || response.Ack.BatchID != record.Envelope.Meta.BatchID {
		return ackRetryable, errors.New("missing or mismatched batch acknowledgement")
	}
	if len(record.Envelope.Data) != 1 {
		return ackRetryable, errors.New("tracked host batch must contain exactly one message")
	}
	message, ok := record.Envelope.Data[0].(map[string]interface{})
	if !ok {
		return ackRetryable, errors.New("tracked host message is not an object")
	}
	messageID, _ := message["messageId"].(string)
	if messageID == "" {
		return ackRetryable, errors.New("tracked host messageId is empty")
	}
	matches := 0
	outcome := ackRetryable
	for _, accepted := range response.Ack.AcceptedMessageIDs {
		if accepted == messageID {
			matches++
			outcome = ackAccepted
		}
	}
	for _, duplicate := range response.Ack.DuplicateMessageIDs {
		if duplicate == messageID {
			matches++
			outcome = ackDuplicate
		}
	}
	for _, rejected := range response.Ack.RejectedItems {
		if rejected.MessageID == messageID {
			matches++
			if rejected.Retryable {
				outcome = ackRetryable
			} else {
				outcome = ackPermanent
			}
		}
	}
	if matches != 1 {
		return ackRetryable, fmt.Errorf("message %s is covered %d times by acknowledgement", messageID, matches)
	}
	return outcome, nil
}

func notify(response model.Resp) {
	for _, agenda := range response.Agendas {
		log.I("agenda received: %v", agenda)
	}
	for _, action := range response.Punishes {
		if len(action) < 3 {
			continue
		}
		typeNumber, ok := action[0].(float64)
		if !ok || int(typeNumber) != 1 {
			continue
		}
		title, _ := action[1].(string)
		message, _ := action[2].(string)
		log.W("server reminder title=%q message=%q", title, message)
		fmt.Fprintf(os.Stderr, "[%s] %s\n", title, message)
		desktopNotify(title, message)
	}
}
