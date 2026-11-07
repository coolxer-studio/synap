package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

// captureSink records every write in memory for assertions.
type captureSink struct {
	mu      sync.Mutex
	records []capturedRecord
}

type capturedRecord struct {
	key   string
	value []byte
}

func (s *captureSink) Write(key string, value []byte) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.records = append(s.records, capturedRecord{key: key, value: append([]byte(nil), value...)})
	return nil
}

func (s *captureSink) Close() error { return nil }

func (s *captureSink) transactions(t *testing.T) []Transaction {
	t.Helper()
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]Transaction, 0, len(s.records))
	for _, r := range s.records {
		var tx Transaction
		if err := json.Unmarshal(r.value, &tx); err != nil {
			t.Fatalf("decode record %q: %v", r.value, err)
		}
		out = append(out, tx)
	}
	return out
}

func (s *captureSink) last(t *testing.T) Transaction {
	t.Helper()
	txs := s.transactions(t)
	if len(txs) == 0 {
		t.Fatal("no transactions captured")
	}
	return txs[len(txs)-1]
}

func newTestGateway(t *testing.T, target string, mutate func(*Config)) (*Gateway, *captureSink, *Metrics) {
	t.Helper()
	cfg := &Config{
		Listen:  ":0",
		Target:  target,
		Capture: CaptureConfig{Headers: true, Body: true},
		Output:  OutputConfig{Console: true}, // satisfies validation; tests use their own sink
	}
	if mutate != nil {
		mutate(cfg)
	}
	if err := cfg.Finalize(); err != nil {
		t.Fatalf("finalize: %v", err)
	}
	sink := &captureSink{}
	metrics := &Metrics{}
	gw, err := NewGateway(cfg, sink, metrics, nil)
	if err != nil {
		t.Fatalf("new gateway: %v", err)
	}
	return gw, sink, metrics
}

func TestGatewayForwardsAndCaptures(t *testing.T) {
	var (
		gotPath, gotQuery, gotBody, gotApp string
	)
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		gotPath = r.URL.Path
		gotQuery = r.URL.RawQuery
		gotBody = string(b)
		gotApp = r.Header.Get("X-Forwarded-App")
		w.Header().Set("X-Backend", "yes")
		w.WriteHeader(http.StatusCreated)
		fmt.Fprintf(w, "echo:%s", b)
	}))
	defer backend.Close()

	gw, sink, metrics := newTestGateway(t, backend.URL+"/api", func(cfg *Config) {
		cfg.StripPrefix = []string{"/gateway"}
		cfg.AddHeaders = map[string]string{"X-Forwarded-App": "gw"}
	})
	proxySrv := httptest.NewServer(gw)
	defer proxySrv.Close()

	req, _ := http.NewRequest(http.MethodPost, proxySrv.URL+"/gateway/users?page=2", strings.NewReader("ping"))
	req.Header.Set("X-Test", "yes")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)

	if resp.StatusCode != http.StatusCreated || string(body) != "echo:ping" {
		t.Errorf("forward result: status=%d body=%q", resp.StatusCode, body)
	}
	if gotPath != "/api/users" || gotQuery != "page=2" || gotBody != "ping" || gotApp != "gw" {
		t.Errorf("upstream got path=%q query=%q body=%q app=%q", gotPath, gotQuery, gotBody, gotApp)
	}

	tx := sink.last(t)
	if tx.Type != recordType || tx.SchemaVersion != recordSchemaVersion {
		t.Errorf("unexpected record type/schema: %+v", tx)
	}
	if tx.ID == "" {
		t.Error("missing record id")
	}
	if tx.Request.Method != http.MethodPost || tx.Request.Path != "/gateway/users" ||
		tx.Request.Query != "page=2" || tx.Request.Body != "ping" {
		t.Errorf("unexpected request record: %+v", tx.Request)
	}
	if tx.Request.Headers.Get("X-Test") != "yes" {
		t.Errorf("request headers missing X-Test: %+v", tx.Request.Headers)
	}
	if tx.Request.ClientIP != "127.0.0.1" {
		t.Errorf("client_ip = %q, want 127.0.0.1", tx.Request.ClientIP)
	}
	if tx.Response.Status != http.StatusCreated || tx.Response.Body != "echo:ping" {
		t.Errorf("unexpected response record: %+v", tx.Response)
	}
	if tx.Response.Headers.Get("X-Backend") != "yes" {
		t.Errorf("response headers missing X-Backend: %+v", tx.Response.Headers)
	}
	if tx.Upstream != backend.URL+"/api" {
		t.Errorf("upstream = %q", tx.Upstream)
	}
	if metrics.Captured.Load() != 1 {
		t.Errorf("captured = %d, want 1", metrics.Captured.Load())
	}

	// Message key defaults to the request id.
	sink.mu.Lock()
	key := sink.records[0].key
	sink.mu.Unlock()
	if key != tx.ID {
		t.Errorf("kafka key = %q, want record id %q", key, tx.ID)
	}
}

func TestGatewayRedactsHeaders(t *testing.T) {
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer backend.Close()

	gw, sink, _ := newTestGateway(t, backend.URL, nil)
	proxySrv := httptest.NewServer(gw)
	defer proxySrv.Close()

	req, _ := http.NewRequest(http.MethodGet, proxySrv.URL+"/", nil)
	req.Header.Set("Authorization", "Bearer super-secret")
	if _, err := http.DefaultClient.Do(req); err != nil {
		t.Fatalf("request: %v", err)
	}

	tx := sink.last(t)
	if got := tx.Request.Headers.Get("Authorization"); got != "[REDACTED]" {
		t.Errorf("Authorization = %q, want [REDACTED]", got)
	}
}

func TestGatewayUpstreamDownReturns502(t *testing.T) {
	gw, sink, _ := newTestGateway(t, "http://127.0.0.1:1", func(cfg *Config) {
		cfg.Timeout = 2 * 1e9
	})
	proxySrv := httptest.NewServer(gw)
	defer proxySrv.Close()

	resp, err := http.Get(proxySrv.URL + "/")
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusBadGateway {
		t.Errorf("status = %d, want 502", resp.StatusCode)
	}

	tx := sink.last(t)
	if tx.Response.Status != http.StatusBadGateway || tx.Error == "" {
		t.Errorf("expected 502 record with error, got %+v", tx)
	}
}

// TestGatewayUpgradePreservesTunnel reproduces the default-config 502: body
// capture must not strip the io.ReadWriteCloser contract from a 101 response.
func TestGatewayUpgradePreservesTunnel(t *testing.T) {
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.EqualFold(r.Header.Get("Upgrade"), "echo") {
			http.Error(w, "missing upgrade header", http.StatusBadRequest)
			return
		}
		conn, brw, err := http.NewResponseController(w).Hijack()
		if err != nil {
			t.Errorf("backend hijack: %v", err)
			return
		}
		defer conn.Close()
		if _, err := brw.WriteString("HTTP/1.1 101 Switching Protocols\r\nUpgrade: echo\r\nConnection: Upgrade\r\n\r\n"); err != nil {
			t.Errorf("backend write 101: %v", err)
			return
		}
		if err := brw.Flush(); err != nil {
			t.Errorf("backend flush 101: %v", err)
			return
		}
		buf := make([]byte, 64)
		for {
			n, err := brw.Read(buf)
			if n > 0 {
				if _, werr := brw.Write(buf[:n]); werr != nil {
					return
				}
				if werr := brw.Flush(); werr != nil {
					return
				}
			}
			if err != nil {
				return
			}
		}
	}))
	defer backend.Close()

	gw, sink, _ := newTestGateway(t, backend.URL, nil)
	proxySrv := httptest.NewServer(gw)
	defer proxySrv.Close()

	addr := strings.TrimPrefix(proxySrv.URL, "http://")
	conn, err := net.DialTimeout("tcp", addr, 2*time.Second)
	if err != nil {
		t.Fatalf("dial gateway: %v", err)
	}
	brw := bufio.NewReadWriter(bufio.NewReader(conn), bufio.NewWriter(conn))
	req := "GET /ws HTTP/1.1\r\nHost: " + addr + "\r\nConnection: Upgrade\r\nUpgrade: echo\r\n\r\n"
	if _, err := brw.WriteString(req); err != nil {
		t.Fatalf("write upgrade request: %v", err)
	}
	if err := brw.Flush(); err != nil {
		t.Fatalf("flush upgrade request: %v", err)
	}
	statusLine, err := brw.ReadString('\n')
	if err != nil {
		t.Fatalf("read status line: %v", err)
	}
	if !strings.Contains(statusLine, "101") {
		t.Fatalf("status line = %q, want 101 Switching Protocols", statusLine)
	}
	for {
		line, err := brw.ReadString('\n')
		if err != nil {
			t.Fatalf("read response header: %v", err)
		}
		if line == "\r\n" {
			break
		}
	}

	msg := []byte("hello-tunnel")
	if _, err := brw.Write(msg); err != nil {
		t.Fatalf("write tunnel payload: %v", err)
	}
	if err := brw.Flush(); err != nil {
		t.Fatalf("flush tunnel payload: %v", err)
	}
	echoed := make([]byte, len(msg))
	if _, err := io.ReadFull(brw, echoed); err != nil {
		t.Fatalf("read echoed payload: %v", err)
	}
	if string(echoed) != string(msg) {
		t.Errorf("echoed payload = %q, want %q", echoed, msg)
	}
	_ = conn.Close()

	var tx Transaction
	deadline := time.Now().Add(2 * time.Second)
	for {
		txs := sink.transactions(t)
		if len(txs) > 0 {
			tx = txs[len(txs)-1]
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("no transaction captured for upgraded connection")
		}
		time.Sleep(10 * time.Millisecond)
	}

	if tx.Response.Status != http.StatusSwitchingProtocols {
		t.Errorf("recorded status = %d, want 101", tx.Response.Status)
	}
	if tx.Response.Upgrade != "echo" {
		t.Errorf("recorded upgrade = %q, want echo", tx.Response.Upgrade)
	}
	if tx.Response.Body != "" {
		t.Errorf("upgrade body should not be captured, got %q", tx.Response.Body)
	}
	if tx.Response.BytesIn != int64(len(msg)) {
		t.Errorf("bytes_in = %d, want %d", tx.Response.BytesIn, len(msg))
	}
	if tx.Response.BytesOut != int64(len(msg)) {
		t.Errorf("bytes_out = %d, want %d", tx.Response.BytesOut, len(msg))
	}
}

func TestGatewayFullBodyNotTruncatedByDefault(t *testing.T) {
	big := strings.Repeat("A", 64*1024)
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.Copy(io.Discard, r.Body)
		w.Header().Set("Content-Type", "text/plain")
		io.WriteString(w, big)
	}))
	defer backend.Close()

	gw, sink, _ := newTestGateway(t, backend.URL, nil)
	proxySrv := httptest.NewServer(gw)
	defer proxySrv.Close()

	resp, err := http.Post(proxySrv.URL+"/upload", "text/plain", strings.NewReader(big))
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	defer resp.Body.Close()
	got, _ := io.ReadAll(resp.Body)
	if len(got) != len(big) {
		t.Errorf("forwarded body size = %d, want %d", len(got), len(big))
	}

	tx := sink.last(t)
	if tx.Request.Body != big || tx.Request.BodyTruncated {
		t.Errorf("request body not preserved: len=%d truncated=%v", len(tx.Request.Body), tx.Request.BodyTruncated)
	}
	if tx.Response.Body != big || tx.Response.BodyTruncated {
		t.Errorf("response body not preserved: len=%d truncated=%v", len(tx.Response.Body), tx.Response.BodyTruncated)
	}
}

func TestGatewayMaxBodySizeStillTruncatesWhenSet(t *testing.T) {
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.Copy(io.Discard, r.Body)
		io.WriteString(w, strings.Repeat("B", 100))
	}))
	defer backend.Close()

	gw, sink, _ := newTestGateway(t, backend.URL, func(cfg *Config) {
		cfg.Capture.MaxBodySize = 16
	})
	proxySrv := httptest.NewServer(gw)
	defer proxySrv.Close()

	resp, err := http.Get(proxySrv.URL + "/")
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	got, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if len(got) != 100 {
		t.Errorf("client body size = %d, want 100", len(got))
	}

	tx := sink.last(t)
	if len(tx.Response.Body) != 16 || !tx.Response.BodyTruncated {
		t.Errorf("expected truncated 16-byte log body, got len=%d truncated=%v", len(tx.Response.Body), tx.Response.BodyTruncated)
	}
}

func TestGatewayCaptureDisabled(t *testing.T) {
	backend := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, "secret")
	}))
	defer backend.Close()

	gw, sink, _ := newTestGateway(t, backend.URL, func(cfg *Config) {
		cfg.Capture.Body = false
		cfg.Capture.Headers = false
	})
	proxySrv := httptest.NewServer(gw)
	defer proxySrv.Close()

	if _, err := http.Get(proxySrv.URL + "/"); err != nil {
		t.Fatalf("request: %v", err)
	}

	tx := sink.last(t)
	if tx.Request.Body != "" || tx.Response.Body != "" {
		t.Errorf("bodies should not be captured: %+v", tx)
	}
	if tx.Request.Headers != nil || tx.Response.Headers != nil {
		t.Errorf("headers should not be captured: %+v", tx)
	}
}

func TestJoinPath(t *testing.T) {
	cases := []struct {
		parts []string
		want  string
	}{
		{[]string{"", "", "/"}, "/"},
		{[]string{"", "", "/foo"}, "/foo"},
		{[]string{"/api", "", "/users"}, "/api/users"},
		{[]string{"/api/", "/users"}, "/api/users"},
		{[]string{"/api", "v1", "/users"}, "/api/v1/users"},
		{[]string{"", "/v1", "/users"}, "/v1/users"},
	}
	for _, c := range cases {
		if got := joinPath(c.parts...); got != c.want {
			t.Errorf("joinPath(%v) = %q, want %q", c.parts, got, c.want)
		}
	}
}

func TestKeyFor(t *testing.T) {
	tx := &Transaction{ID: "abc"}
	tx.Request.ClientIP = "10.0.0.1"
	if got := keyFor("id", tx); got != "abc" {
		t.Errorf("keyFor(id) = %q", got)
	}
	if got := keyFor("client_ip", tx); got != "10.0.0.1" {
		t.Errorf("keyFor(client_ip) = %q", got)
	}
	if got := keyFor("none", tx); got != "" {
		t.Errorf("keyFor(none) = %q", got)
	}
}
