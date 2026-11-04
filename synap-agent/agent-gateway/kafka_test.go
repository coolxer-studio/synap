package main

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/segmentio/kafka-go"
)

func waitFor(t *testing.T, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatalf("timeout waiting for %s", what)
}

func testKafkaCfg() *KafkaConfig {
	return &KafkaConfig{
		QueueSize:    16,
		BatchSize:    8,
		BatchTimeout: 20 * time.Millisecond,
		WriteTimeout: time.Second,
		MaxAttempts:  1,
	}
}

func TestKafkaSinkSendsSuccessfully(t *testing.T) {
	var (
		mu  sync.Mutex
		got []kafka.Message
	)
	send := func(_ context.Context, msgs []kafka.Message) error {
		mu.Lock()
		got = append(got, msgs...)
		mu.Unlock()
		return nil
	}
	metrics := &Metrics{}
	k := newKafkaSink(testKafkaCfg(), nil, send, nil, metrics, nil)
	k.start()
	defer k.Close()

	if err := k.Write("k1", []byte(`{"n":1}`)); err != nil {
		t.Fatalf("write: %v", err)
	}
	if err := k.Write("k2", []byte(`{"n":2}`)); err != nil {
		t.Fatalf("write: %v", err)
	}

	waitFor(t, "records sent", func() bool { return metrics.Sent.Load() == 2 })
	if metrics.Spooled.Load() != 0 {
		t.Errorf("spooled = %d, want 0", metrics.Spooled.Load())
	}
	mu.Lock()
	defer mu.Unlock()
	if len(got) != 2 || string(got[0].Key) != "k1" {
		t.Errorf("unexpected messages: %+v", got)
	}
}

func TestKafkaSinkSpoolsOnFailure(t *testing.T) {
	dir := t.TempDir()
	metrics := &Metrics{}
	spool, err := OpenSpool(filepath.Join(dir, "spool.ndjson"), metrics)
	if err != nil {
		t.Fatalf("open spool: %v", err)
	}
	defer spool.Close()

	send := func(_ context.Context, _ []kafka.Message) error { return errors.New("kafka down") }
	k := newKafkaSink(testKafkaCfg(), nil, send, spool, metrics, nil)
	k.start()

	for i := 0; i < 3; i++ {
		if err := k.Write("k", []byte(`{"n":1}`)); err != nil {
			t.Fatalf("write: %v", err)
		}
	}

	waitFor(t, "records spooled", func() bool { return metrics.Spooled.Load() == 3 })
	if err := k.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}
	if metrics.SendErrors.Load() == 0 {
		t.Error("expected send errors")
	}
	recs, err := readSpoolFile(filepath.Join(dir, "spool.ndjson"))
	if err != nil {
		t.Fatalf("read spool: %v", err)
	}
	if len(recs) != 3 {
		t.Errorf("spool has %d records, want 3", len(recs))
	}
}

func TestKafkaSinkSpoolsOnQueueOverflow(t *testing.T) {
	dir := t.TempDir()
	metrics := &Metrics{}
	spool, err := OpenSpool(filepath.Join(dir, "spool.ndjson"), metrics)
	if err != nil {
		t.Fatalf("open spool: %v", err)
	}
	defer spool.Close()

	entered := make(chan struct{}, 1)
	release := make(chan struct{})
	send := func(_ context.Context, _ []kafka.Message) error {
		entered <- struct{}{}
		<-release
		return nil
	}

	cfg := testKafkaCfg()
	cfg.QueueSize = 1
	cfg.BatchSize = 1
	cfg.BatchTimeout = time.Hour // avoid timer-driven flush
	k := newKafkaSink(cfg, nil, send, spool, metrics, nil)
	k.start()
	defer k.Close()

	_ = k.Write("a", []byte(`{"n":1}`))
	<-entered // worker is now blocked inside send

	_ = k.Write("b", []byte(`{"n":2}`)) // fills the queue
	_ = k.Write("c", []byte(`{"n":3}`)) // queue full -> spill

	if metrics.QueueOverflow.Load() != 1 {
		t.Errorf("queue overflow = %d, want 1", metrics.QueueOverflow.Load())
	}
	recs, err := readSpoolFile(filepath.Join(dir, "spool.ndjson"))
	if err != nil {
		t.Fatalf("read spool: %v", err)
	}
	if len(recs) != 1 {
		t.Errorf("spool has %d records, want 1", len(recs))
	}
	close(release)
}

func TestReplayerReplaysAndClearsSpool(t *testing.T) {
	dir := t.TempDir()
	metrics := &Metrics{}
	spoolPath := filepath.Join(dir, "spool.ndjson")
	spool, err := OpenSpool(spoolPath, metrics)
	if err != nil {
		t.Fatalf("open spool: %v", err)
	}
	defer spool.Close()

	if err := spool.Write("k1", []byte(`{"n":1}`)); err != nil {
		t.Fatal(err)
	}
	if err := spool.Write("k2", []byte(`{"n":2}`)); err != nil {
		t.Fatal(err)
	}

	var mu sync.Mutex
	var sent []kafka.Message
	send := func(_ context.Context, msgs []kafka.Message) error {
		mu.Lock()
		sent = append(sent, msgs...)
		mu.Unlock()
		return nil
	}
	r := &Replayer{spool: spool, interval: time.Hour, metrics: metrics, send: send, logf: t.Logf}
	r.replayOnce(context.Background())

	waitFor(t, "replay done", func() bool { return metrics.Replayed.Load() == 2 })
	mu.Lock()
	n := len(sent)
	mu.Unlock()
	if n != 2 {
		t.Errorf("replayed %d messages, want 2", n)
	}
	// Spool should be empty and no leftover replay files.
	info, err := os.Stat(spoolPath)
	if err != nil {
		t.Fatalf("stat spool: %v", err)
	}
	if info.Size() != 0 {
		t.Errorf("spool not empty after replay: %d bytes", info.Size())
	}
	leftovers, _ := filepath.Glob(spoolPath + ".replay.*")
	if len(leftovers) != 0 {
		t.Errorf("leftover replay files: %v", leftovers)
	}
}

func TestReplayerRespuelsOnFailure(t *testing.T) {
	dir := t.TempDir()
	metrics := &Metrics{}
	spoolPath := filepath.Join(dir, "spool.ndjson")
	spool, err := OpenSpool(spoolPath, metrics)
	if err != nil {
		t.Fatalf("open spool: %v", err)
	}
	defer spool.Close()

	if err := spool.Write("k1", []byte(`{"n":1}`)); err != nil {
		t.Fatal(err)
	}
	send := func(_ context.Context, _ []kafka.Message) error { return errors.New("still down") }
	r := &Replayer{spool: spool, interval: time.Hour, metrics: metrics, send: send, logf: t.Logf}
	r.replayOnce(context.Background())

	if metrics.ReplayErrors.Load() != 1 {
		t.Errorf("replay errors = %d, want 1", metrics.ReplayErrors.Load())
	}
	if metrics.Replayed.Load() != 0 {
		t.Errorf("replayed = %d, want 0", metrics.Replayed.Load())
	}
	// The record must be back in the spool so nothing is lost.
	recs, err := readSpoolFile(spoolPath)
	if err != nil {
		t.Fatalf("read spool: %v", err)
	}
	if len(recs) != 1 {
		t.Errorf("spool has %d records, want 1", len(recs))
	}
}

func TestReplayerRunTicks(t *testing.T) {
	dir := t.TempDir()
	metrics := &Metrics{}
	spool, err := OpenSpool(filepath.Join(dir, "spool.ndjson"), metrics)
	if err != nil {
		t.Fatalf("open spool: %v", err)
	}
	defer spool.Close()
	if err := spool.Write("k", []byte(`{"n":1}`)); err != nil {
		t.Fatal(err)
	}

	send := func(_ context.Context, _ []kafka.Message) error { return errors.New("down") }
	r := newReplayer(spool, 20*time.Millisecond, metrics, send, t.Logf)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	r.Start(ctx)

	waitFor(t, "replay tick", func() bool { return metrics.ReplayErrors.Load() >= 1 })
	cancel()
	r.Wait()
}

func TestReplayerRunStopsOnCancel(t *testing.T) {
	dir := t.TempDir()
	metrics := &Metrics{}
	spool, err := OpenSpool(filepath.Join(dir, "spool.ndjson"), metrics)
	if err != nil {
		t.Fatalf("open spool: %v", err)
	}
	defer spool.Close()

	r := newReplayer(spool, 10*time.Millisecond, metrics, func(context.Context, []kafka.Message) error { return nil }, t.Logf)
	ctx, cancel := context.WithCancel(context.Background())
	r.Start(ctx)
	cancel()
	r.Wait() // must return promptly
}

func TestKafkaSinkDropsWithoutFallback(t *testing.T) {
	metrics := &Metrics{}
	send := func(_ context.Context, _ []kafka.Message) error { return errors.New("down") }
	k := newKafkaSink(testKafkaCfg(), nil, send, nil, metrics, nil)
	k.start()
	defer k.Close()

	for i := 0; i < 3; i++ {
		if err := k.Write("k", []byte(`{"n":1}`)); err != nil {
			t.Fatalf("write: %v", err)
		}
	}
	waitFor(t, "records dropped", func() bool { return metrics.Dropped.Load() == 3 })
	if metrics.Spooled.Load() != 0 {
		t.Errorf("spooled = %d, want 0 (no fallback configured)", metrics.Spooled.Load())
	}
}

func TestMarshalJSONNoHTMLEscape(t *testing.T) {
	out, err := marshalJSON(map[string]string{"u": "<a>&b</a>"})
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	if got := string(out); got != `{"u":"<a>&b</a>"}` {
		t.Errorf("marshalJSON = %s", got)
	}
	if len(out) > 0 && out[len(out)-1] == '\n' {
		t.Error("marshalJSON should not include a trailing newline")
	}
}
