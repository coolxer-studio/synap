package storage

import (
	"os"
	"path/filepath"
	"agent-host/model"
	"sync"
	"testing"
	"time"
)

func TestSpoolPersistsAndAcknowledges(t *testing.T) {
	dir := t.TempDir()
	spool, err := New(dir, 1<<20, 10)
	if err != nil {
		t.Fatal(err)
	}
	record := model.QueueRecord{Code: "a-b-c-d-e-host-1-2.0.0", Envelope: model.Envelope{Common: make([]string, 24)}, CreatedAt: 1}
	if err := spool.Enqueue(record); err != nil {
		t.Fatal(err)
	}
	reopened, err := New(dir, 1<<20, 10)
	if err != nil {
		t.Fatal(err)
	}
	item, err := reopened.Next()
	if err != nil || item == nil {
		t.Fatalf("next: %v", err)
	}
	if item.Record.Code != record.Code {
		t.Fatalf("code = %q", item.Record.Code)
	}
	if err := reopened.Ack(item); err != nil {
		t.Fatal(err)
	}
	item, err = reopened.Next()
	if err != nil || item != nil {
		t.Fatalf("expected empty spool, item=%v err=%v", item, err)
	}
}

func TestSpoolRejectsAndPrunesExpiredRecords(t *testing.T) {
	dir := t.TempDir()
	spool, err := New(dir, 1<<20, 10)
	if err != nil {
		t.Fatal(err)
	}
	if err := spool.Enqueue(model.QueueRecord{Code: "rejected", Envelope: model.Envelope{Common: make([]string, 24)}}); err != nil {
		t.Fatal(err)
	}
	item, err := spool.Next()
	if err != nil || item == nil {
		t.Fatalf("item=%v err=%v", item, err)
	}
	if err := spool.Reject(item); err != nil {
		t.Fatal(err)
	}
	dead, err := jsonFiles(filepath.Join(dir, "dead-letter"))
	if err != nil || len(dead) != 1 {
		t.Fatalf("dead=%d err=%v", len(dead), err)
	}

	if err := spool.Enqueue(model.QueueRecord{Code: "expired", Envelope: model.Envelope{Common: make([]string, 24)}}); err != nil {
		t.Fatal(err)
	}
	queued, _ := jsonFiles(filepath.Join(dir, "spool"))
	old := time.Now().Add(-8 * 24 * time.Hour)
	if err := os.Chtimes(filepath.Join(dir, "spool", queued[0].Name()), old, old); err != nil {
		t.Fatal(err)
	}
	if _, err := New(dir, 1<<20, 10); err != nil {
		t.Fatal(err)
	}
	queued, _ = jsonFiles(filepath.Join(dir, "spool"))
	if len(queued) != 0 {
		t.Fatalf("expired queue len=%d", len(queued))
	}
}

func TestConcurrentEnqueueAndPrivatePermissions(t *testing.T) {
	dir := t.TempDir()
	spool, err := New(dir, 1<<20, 100)
	if err != nil {
		t.Fatal(err)
	}
	var group sync.WaitGroup
	for index := 0; index < 20; index++ {
		group.Add(1)
		go func(value int) {
			defer group.Done()
			if err := spool.Enqueue(model.QueueRecord{Code: string(rune('a' + value)), Envelope: model.Envelope{Common: make([]string, 24)}}); err != nil {
				t.Errorf("enqueue: %v", err)
			}
		}(index)
	}
	group.Wait()
	files, err := jsonFiles(filepath.Join(dir, "spool"))
	if err != nil || len(files) != 20 {
		t.Fatalf("files=%d err=%v", len(files), err)
	}
	if mode := files[0].Mode().Perm(); mode != 0o600 {
		t.Fatalf("file mode=%o", mode)
	}
	info, err := os.Stat(filepath.Join(dir, "spool"))
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o700 {
		t.Fatalf("directory mode=%o", info.Mode().Perm())
	}
}

func TestSpoolEvictsOldest(t *testing.T) {
	spool, err := New(t.TempDir(), 1<<20, 2)
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 3; i++ {
		if err := spool.Enqueue(model.QueueRecord{Code: string(rune('a' + i)), Envelope: model.Envelope{Common: make([]string, 24)}}); err != nil {
			t.Fatal(err)
		}
	}
	item, err := spool.Next()
	if err != nil {
		t.Fatal(err)
	}
	if item.Record.Code != "b" {
		t.Fatalf("oldest retained code = %q", item.Record.Code)
	}
}
