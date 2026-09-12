package storage

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"testing"
	"time"

	"agent-host/model"
)

func trackingSession(startID string) model.Session {
	common := make([]string, 24)
	common[2] = startID
	common[3] = "2.0.0"
	common[4] = "1"
	common[8] = "host"
	return model.Session{Code: "a-b-c-d-e-host-1-2.0.0", Common: common,
		StartMeta: model.RequestMeta{AppKeyID: "synap-host-dev"}}
}

func TestEnqueueFactAssignsStableSequentialTracking(t *testing.T) {
	spool, err := New(t.TempDir(), 1<<20, 20)
	if err != nil {
		t.Fatal(err)
	}
	session := trackingSession("1700000000000")
	for _, factType := range []string{"DeviceData", "FileData", "RuntimeData", "NetData", "EventData", "UserData", "AuditData"} {
		fact := map[string]interface{}{"type": factType, "buildDate": "2026-08-12 10:00:00"}
		if factType == "AuditData" {
			fact["batchId"] = "business-audit-batch"
		}
		if err := spool.EnqueueFact(session, fact); err != nil {
			t.Fatal(err)
		}
	}
	records := readQueuedRecords(t, spool)
	if len(records) != 7 {
		t.Fatalf("records=%d", len(records))
	}
	for index, record := range records {
		message := record.Envelope.Data[0].(map[string]interface{})
		if sequence, _ := int64Value(message["sequence"]); sequence != int64(index+2) {
			t.Fatalf("sequence[%d]=%v", index, message["sequence"])
		}
		if !nonEmptyString(message["messageId"]) || !positiveInt64(message["observedAt"]) {
			t.Fatalf("missing message tracking: %#v", message)
		}
		meta := record.Envelope.Meta
		if meta == nil || meta.ProtocolVersion != 1 || meta.AppKeyID != "synap-host-dev" || meta.DetectorVersion != "2.0.0" ||
			meta.SessionID != session.Common[2] || meta.BatchID == "" || meta.Attempt != 0 {
			t.Fatalf("invalid batch metadata: %+v", meta)
		}
		if message["type"] == "AuditData" && message["batchId"] != "business-audit-batch" {
			t.Fatal("AuditData business batchId was overwritten")
		}
	}
}

func TestConcurrentSpoolInstancesAllocateUniqueSequences(t *testing.T) {
	root := t.TempDir()
	left, _ := New(root, 1<<20, 100)
	right, _ := New(root, 1<<20, 100)
	session := trackingSession("1700000000001")
	var group sync.WaitGroup
	for index := 0; index < 20; index++ {
		group.Add(1)
		go func(value int) {
			defer group.Done()
			spool := left
			if value%2 == 1 {
				spool = right
			}
			if err := spool.EnqueueFact(session, map[string]interface{}{"type": "UserData", "value": value}); err != nil {
				t.Errorf("enqueue %d: %v", value, err)
			}
		}(index)
	}
	group.Wait()
	records := readQueuedRecords(t, left)
	sequences := make([]int, 0, len(records))
	for _, record := range records {
		value, _ := int64Value(record.Envelope.Data[0].(map[string]interface{})["sequence"])
		sequences = append(sequences, int(value))
	}
	sort.Ints(sequences)
	for index, sequence := range sequences {
		if sequence != index+2 {
			t.Fatalf("sequences=%v", sequences)
		}
	}
}

func TestPrepareUploadPersistsAttemptsAndMigratesLegacy(t *testing.T) {
	root := t.TempDir()
	spool, _ := New(root, 1<<20, 20)
	session := trackingSession("1700000000002")
	if err := spool.EnqueueFact(session, map[string]interface{}{"type": "DeviceData"}); err != nil {
		t.Fatal(err)
	}
	item, _ := spool.Next()
	first, err := spool.PrepareUpload(item)
	if err != nil {
		t.Fatal(err)
	}
	second, err := spool.PrepareUpload(item)
	if err != nil {
		t.Fatal(err)
	}
	firstMessage := first.Envelope.Data[0].(map[string]interface{})
	secondMessage := second.Envelope.Data[0].(map[string]interface{})
	if first.Envelope.Meta.Attempt != 1 || second.Envelope.Meta.Attempt != 2 ||
		first.Envelope.Meta.BatchID != second.Envelope.Meta.BatchID ||
		firstMessage["messageId"] != secondMessage["messageId"] || firstMessage["sequence"] != secondMessage["sequence"] {
		t.Fatalf("retry identity changed: first=%+v second=%+v", first, second)
	}

	legacyRoot := t.TempDir()
	legacy, _ := New(legacyRoot, 1<<20, 20)
	legacyRecord := model.QueueRecord{Code: session.Code, Envelope: model.Envelope{
		Common: session.Common, Data: []interface{}{map[string]interface{}{"type": "FileData"}},
	}, CreatedAt: time.Now().Add(-time.Minute).UnixMilli()}
	if err := legacy.Enqueue(legacyRecord); err != nil {
		t.Fatal(err)
	}
	legacyItem, _ := legacy.Next()
	migrated, err := legacy.PrepareUpload(legacyItem)
	if err != nil {
		t.Fatal(err)
	}
	migratedMessage := migrated.Envelope.Data[0].(map[string]interface{})
	if migrated.Envelope.Meta == nil || migrated.Envelope.Meta.Attempt != 1 ||
		!nonEmptyString(migratedMessage["messageId"]) || !positiveInt64(migratedMessage["observedAt"]) {
		t.Fatalf("legacy record not migrated: %+v", migrated)
	}
	if sequence, _ := int64Value(migratedMessage["sequence"]); sequence != 2 {
		t.Fatalf("legacy sequence=%d", sequence)
	}
}

func TestPrepareUploadRejectsPartialTracking(t *testing.T) {
	spool, _ := New(t.TempDir(), 1<<20, 20)
	session := trackingSession("1700000000003")
	record := model.QueueRecord{Code: session.Code, Envelope: model.Envelope{
		Common: session.Common,
		Data:   []interface{}{map[string]interface{}{"type": "DeviceData", "messageId": "partial"}},
	}, CreatedAt: time.Now().UnixMilli()}
	if err := spool.Enqueue(record); err != nil {
		t.Fatal(err)
	}
	item, _ := spool.Next()
	if _, err := spool.PrepareUpload(item); !errors.Is(err, ErrInvalidTrackingRecord) {
		t.Fatalf("expected invalid tracking error, got %v", err)
	}
}

func readQueuedRecords(t *testing.T, spool *Spool) []model.QueueRecord {
	t.Helper()
	files, err := jsonFiles(spool.queueDir)
	if err != nil {
		t.Fatal(err)
	}
	records := make([]model.QueueRecord, 0, len(files))
	for _, file := range files {
		data, err := os.ReadFile(filepath.Join(spool.queueDir, file.Name()))
		if err != nil {
			t.Fatal(err)
		}
		var record model.QueueRecord
		if err := json.Unmarshal(data, &record); err != nil {
			t.Fatal(err)
		}
		records = append(records, record)
	}
	return records
}
