package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"sync"
	"time"

	"github.com/segmentio/kafka-go"
)

// spoolRecord is the on-disk envelope for a record that could not be delivered.
type spoolRecord struct {
	Key   string          `json:"key"`
	Value json.RawMessage `json:"value"`
}

// Spool is an append-only JSON-lines file used to keep records that Kafka could
// not accept. It is safe for concurrent use.
type Spool struct {
	path    string
	metrics *Metrics

	mu sync.Mutex
	f  *os.File
}

// OpenSpool opens (creating if needed) the spool file for appending.
func OpenSpool(path string, metrics *Metrics) (*Spool, error) {
	s := &Spool{path: path, metrics: metrics}
	if err := s.reopenLocked(); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Spool) reopenLocked() error {
	f, err := os.OpenFile(s.path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return fmt.Errorf("open spool %s: %w", s.path, err)
	}
	s.f = f
	return nil
}

// Write appends one record to the spool.
func (s *Spool) Write(key string, value []byte) error {
	line, err := marshalJSON(spoolRecord{Key: key, Value: json.RawMessage(value)})
	if err != nil {
		return err
	}
	line = append(line, '\n')

	s.mu.Lock()
	defer s.mu.Unlock()
	if s.f == nil {
		if err := s.reopenLocked(); err != nil {
			return err
		}
	}
	_, err = s.f.Write(line)
	return err
}

// Close flushes and closes the spool file.
func (s *Spool) Close() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.f == nil {
		return nil
	}
	err := s.f.Close()
	s.f = nil
	return err
}

// Rotate atomically detaches the current spool contents for replay and starts a
// fresh file. It returns the path of the rotated file, or "" when the spool was
// empty.
func (s *Spool) Rotate() (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.f == nil {
		return "", nil
	}
	info, err := s.f.Stat()
	if err != nil {
		return "", err
	}
	if info.Size() == 0 {
		return "", nil
	}
	if err := s.f.Close(); err != nil {
		return "", err
	}
	s.f = nil

	rotated := fmt.Sprintf("%s.replay.%d", s.path, time.Now().UnixNano())
	if err := os.Rename(s.path, rotated); err != nil {
		// Best effort: reopen the original so new writes keep working.
		_ = s.reopenLocked()
		return "", err
	}
	if err := s.reopenLocked(); err != nil {
		return rotated, err
	}
	return rotated, nil
}

// readSpoolFile parses every record in a rotated spool file.
func readSpoolFile(path string) ([]spoolRecord, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()

	var recs []spoolRecord
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 0, 64*1024), 64*1024*1024)
	for sc.Scan() {
		line := sc.Bytes()
		if len(line) == 0 {
			continue
		}
		var r spoolRecord
		if err := json.Unmarshal(line, &r); err != nil {
			// Skip a corrupt line rather than losing the rest.
			continue
		}
		recs = append(recs, r)
	}
	return recs, sc.Err()
}

// Replayer periodically pushes spooled records back to Kafka.
type Replayer struct {
	spool    *Spool
	interval time.Duration
	metrics  *Metrics
	send     sendFunc
	logf     func(format string, args ...any)
	done     chan struct{}
}

// newReplayer builds a replayer ready to Start.
func newReplayer(spool *Spool, interval time.Duration, metrics *Metrics, send sendFunc, logf func(string, ...any)) *Replayer {
	if logf == nil {
		logf = func(string, ...any) {}
	}
	return &Replayer{
		spool:    spool,
		interval: interval,
		metrics:  metrics,
		send:     send,
		logf:     logf,
		done:     make(chan struct{}),
	}
}

// Start launches the replay loop in the background.
func (r *Replayer) Start(ctx context.Context) { go r.Run(ctx) }

// Run blocks until ctx is cancelled, retrying the spool on every tick.
func (r *Replayer) Run(ctx context.Context) {
	defer close(r.done)
	ticker := time.NewTicker(r.interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			r.replayOnce(ctx)
		}
	}
}

// Wait blocks until Run has returned.
func (r *Replayer) Wait() { <-r.done }

// replayOnce rotates the spool and attempts to deliver everything in it. Records
// that still fail are appended back to the spool.
func (r *Replayer) replayOnce(ctx context.Context) {
	rotated, err := r.spool.Rotate()
	if err != nil {
		r.logf("spool rotate failed: %v", err)
		return
	}
	if rotated == "" {
		return
	}

	recs, err := readSpoolFile(rotated)
	if err != nil {
		r.logf("read spool %s failed: %v", rotated, err)
		return
	}

	msgs := make([]kafka.Message, 0, len(recs))
	for _, rec := range recs {
		msgs = append(msgs, kafka.Message{
			Key:   []byte(rec.Key),
			Value: []byte(rec.Value),
			Time:  time.Now(),
		})
	}

	if len(msgs) > 0 {
		if err := r.send(ctx, msgs); err != nil {
			r.metrics.ReplayErrors.Add(1)
			r.logf("replay of %d records failed: %v", len(msgs), err)
			// Put everything back so nothing is lost.
			for _, rec := range recs {
				if werr := r.spool.Write(rec.Key, rec.Value); werr != nil {
					r.logf("re-spool failed: %v", werr)
				}
			}
			_ = os.Remove(rotated)
			return
		}
		r.metrics.Replayed.Add(int64(len(msgs)))
	}

	_ = os.Remove(rotated)
}
