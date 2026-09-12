package cmd

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"agent-host/model"
	"agent-host/transport"
)

func TestParseSchedule(t *testing.T) {
	value, err := parseSchedule([]float64{1, 10, 30, 5, 60})
	if err != nil {
		t.Fatal(err)
	}
	if value.collectEvery.Seconds() != 10 || value.uploadFor.Seconds() != 60 {
		t.Fatalf("unexpected schedule: %+v", value)
	}
	for _, invalid := range [][]float64{{1}, {1, 0, 1, 1, 1}, {0, 1, 1, 1, 1}} {
		if _, err := parseSchedule(invalid); err == nil {
			t.Fatalf("expected error for %v", invalid)
		}
	}
}

func TestMissingEmbeddedTrustFailsBeforeNetwork(t *testing.T) {
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		requests.Add(1)
		http.Error(response, "unexpected request", http.StatusInternalServerError)
	}))
	defer server.Close()

	common := make([]string, 24)
	session := model.Session{
		Code: "a-b-c-d-e-host-1-2.0.0", Common: common,
		StartID: 1_700_000_000_000,
	}
	client := transport.New(transport.Config{
		BaseURL: server.URL, DataDir: t.TempDir(),
	})
	if _, err := client.Start(&session); err == nil || transport.IsRetryable(err) {
		t.Fatalf("missing build trust must be a permanent configuration error: %v", err)
	}
	if requests.Load() != 0 {
		t.Fatalf("invalid security configuration made %d network requests", requests.Load())
	}
}

func TestWaitForStartStopsOnPermanentSecurityConfigurationError(t *testing.T) {
	client := transport.New(transport.Config{
		BaseURL: "http://127.0.0.1:11099", DataDir: t.TempDir(),
	})
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	started := time.Now()
	_, err := waitForStart(ctx, client, model.Session{
		Code: "a-b-c-d-e-host-1-2.0.0", Common: make([]string, 24),
	})
	if err == nil || transport.IsRetryable(err) {
		t.Fatalf("expected permanent configuration error, got %v", err)
	}
	if elapsed := time.Since(started); elapsed > 200*time.Millisecond {
		t.Fatalf("permanent error was retried for %v", elapsed)
	}
}

func TestClassifyUploadAckRequiresExactSingleMessageCoverage(t *testing.T) {
	record := model.QueueRecord{Envelope: model.Envelope{
		Meta: &model.RequestMeta{BatchID: "batch-1"},
		Data: []interface{}{map[string]interface{}{"messageId": "message-1"}},
	}}
	tests := []struct {
		name    string
		ack     *model.UploadAck
		outcome ackOutcome
		invalid bool
	}{
		{"accepted", &model.UploadAck{BatchID: "batch-1", AcceptedMessageIDs: []string{"message-1"}}, ackAccepted, false},
		{"duplicate", &model.UploadAck{BatchID: "batch-1", DuplicateMessageIDs: []string{"message-1"}}, ackDuplicate, false},
		{"retryable", &model.UploadAck{BatchID: "batch-1", RejectedItems: []model.RejectedItem{{MessageID: "message-1", Retryable: true}}}, ackRetryable, false},
		{"permanent", &model.UploadAck{BatchID: "batch-1", RejectedItems: []model.RejectedItem{{MessageID: "message-1", Retryable: false}}}, ackPermanent, false},
		{"missing", &model.UploadAck{BatchID: "batch-1"}, ackRetryable, true},
		{"wrong-batch", &model.UploadAck{BatchID: "batch-2", AcceptedMessageIDs: []string{"message-1"}}, ackRetryable, true},
		{"conflicting", &model.UploadAck{BatchID: "batch-1", AcceptedMessageIDs: []string{"message-1"}, DuplicateMessageIDs: []string{"message-1"}}, ackRetryable, true},
	}
	for _, value := range tests {
		t.Run(value.name, func(t *testing.T) {
			outcome, err := classifyUploadAck(record, model.Resp{Status: 1, Ack: value.ack})
			if (err != nil) != value.invalid || (!value.invalid && outcome != value.outcome) {
				t.Fatalf("outcome=%v err=%v", outcome, err)
			}
		})
	}
}
