package audit

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"

	"agent-host/model"
)

func TestFixedColumnsBatchingAndRedaction(t *testing.T) {
	if err := ValidateConfig(model.AuditConfig{Events: append([]string(nil), Columns...)}); err != nil {
		t.Fatal(err)
	}
	if err := ValidateConfig(model.AuditConfig{Events: []string{"eventId"}}); err == nil {
		t.Fatal("short config must be rejected")
	}

	var batches []model.AuditData
	monitor, err := New(t.TempDir(), model.AuditPolicy{Enabled: true, BatchMaxEvents: 2, BatchMaxBytes: defaultBatchBytes}, func(data model.AuditData) error {
		batches = append(batches, data)
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
	events := make([]Event, 0, 5)
	for index := 0; index < 5; index++ {
		event := NewEvent("boot", "fixture", stableKey(index), "session", "login", "failure", time.Unix(int64(index+1), 0))
		event.Detail = JSONDetail(map[string]string{"password": "do-not-upload", "safe": "ok"})
		events = append(events, event)
	}
	if err := monitor.emit(events); err != nil {
		t.Fatal(err)
	}
	if len(batches) != 3 {
		t.Fatalf("batch count=%d", len(batches))
	}
	seen := map[string]bool{}
	for _, batch := range batches {
		if seen[batch.BatchID] || batch.BatchID == "" {
			t.Fatalf("invalid batch id %q", batch.BatchID)
		}
		seen[batch.BatchID] = true
		if len(batch.Events) > 2 {
			t.Fatalf("batch events=%d", len(batch.Events))
		}
		for _, row := range batch.Events {
			if len(row) != EventColumns {
				t.Fatalf("event columns=%d", len(row))
			}
			if strings.Contains(row[17], "do-not-upload") {
				t.Fatalf("detail was not redacted: %s", row[17])
			}
		}
	}
}

func TestStateIsPrivateAndRoundTripsCleanShutdown(t *testing.T) {
	store, err := newStateStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	value := emptyState()
	value.Initialized = true
	value.CleanShutdown = true
	value.BootID = "boot-fixture"
	if err := store.save(value); err != nil {
		t.Fatal(err)
	}
	loaded, err := store.load()
	if err != nil {
		t.Fatal(err)
	}
	if !loaded.Initialized || !loaded.CleanShutdown || loaded.BootID != "boot-fixture" {
		t.Fatalf("loaded=%+v", loaded)
	}
	if runtime.GOOS != "windows" {
		dirInfo, _ := os.Stat(store.dir)
		fileInfo, _ := os.Stat(store.path)
		if dirInfo.Mode().Perm() != 0o700 {
			t.Fatalf("dir mode=%o", dirInfo.Mode().Perm())
		}
		if fileInfo.Mode().Perm() != 0o600 {
			t.Fatalf("file mode=%o", fileInfo.Mode().Perm())
		}
	}
}

func TestDigestLimitAndFullSHA256(t *testing.T) {
	path := filepath.Join(t.TempDir(), "business.txt")
	if err := os.WriteFile(path, []byte("audit fixture"), 0o600); err != nil {
		t.Fatal(err)
	}
	algorithm, digest, reason := digestFile(path, 13, 64)
	if algorithm != "SHA-256" || digest != "79d98e1383bcb88c8f980fac03687fb5793cd631025e1f009dbd94592cf350f9" || reason != "" {
		t.Fatalf("algorithm=%s digest=%s reason=%s", algorithm, digest, reason)
	}
	if _, _, reason := digestFile(path, 13, 4); reason != "size_limit" {
		t.Fatalf("reason=%s", reason)
	}
}
