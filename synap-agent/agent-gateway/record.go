package main

import (
	"bytes"
	"encoding/json"
	"net"
	"net/http"
	"strings"
)

const (
	recordSchemaVersion = "1.0"
	recordType          = "agent-gateway.transaction"
)

// Transaction is the formatted log record for one relayed API call. A single
// record joins the captured request and its response so downstream analysis
// sees a complete picture per call.
type Transaction struct {
	SchemaVersion string      `json:"schema_version"`
	Type          string      `json:"type"`
	Time          string      `json:"time"`
	ID            string      `json:"id"`
	DurationMs    float64     `json:"duration_ms"`
	Upstream      string      `json:"upstream"`
	Gateway       GatewayInfo `json:"gateway"`
	Request       RequestRec  `json:"request"`
	Response      ResponseRec `json:"response"`
	Error         string      `json:"error,omitempty"`
}

// GatewayInfo identifies the gateway instance that produced the record.
type GatewayInfo struct {
	Listen string `json:"listen"`
	Target string `json:"target"`
}

// RequestRec is the captured inbound request.
type RequestRec struct {
	Method        string      `json:"method"`
	Scheme        string      `json:"scheme"`
	Host          string      `json:"host"`
	Path          string      `json:"path"`
	Query         string      `json:"query,omitempty"`
	URI           string      `json:"uri"`
	Proto         string      `json:"proto"`
	RemoteAddr    string      `json:"remote_addr"`
	ClientIP      string      `json:"client_ip"`
	UserAgent     string      `json:"user_agent,omitempty"`
	Headers       http.Header `json:"headers,omitempty"`
	ContentType   string      `json:"content_type,omitempty"`
	ContentLength int64       `json:"content_length,omitempty"`
	Body          string      `json:"body,omitempty"`
	BodySize      int64       `json:"body_size"`
	BodyTruncated bool        `json:"body_truncated,omitempty"`
}

// ResponseRec is the captured upstream response.
type ResponseRec struct {
	Status        int         `json:"status"`
	StatusText    string      `json:"status_text,omitempty"`
	Headers       http.Header `json:"headers,omitempty"`
	ContentType   string      `json:"content_type,omitempty"`
	ContentLength int64       `json:"content_length,omitempty"`
	Body          string      `json:"body,omitempty"`
	BodySize      int64       `json:"body_size"`
	BodyTruncated bool        `json:"body_truncated,omitempty"`
	BytesOut      int64       `json:"bytes_out"`
	UpstreamMs    float64     `json:"upstream_ms"`
}

// headerRedactor masks configured header values (case-insensitive).
type headerRedactor struct {
	set map[string]struct{}
}

func newHeaderRedactor(names []string) headerRedactor {
	r := headerRedactor{set: make(map[string]struct{}, len(names))}
	for _, n := range names {
		n = strings.ToLower(strings.TrimSpace(n))
		if n != "" {
			r.set[n] = struct{}{}
		}
	}
	return r
}

// apply returns a redacted copy of h. A nil header stays nil.
func (r headerRedactor) apply(h http.Header) http.Header {
	if h == nil {
		return nil
	}
	out := make(http.Header, len(h))
	for k, v := range h {
		if _, ok := r.set[strings.ToLower(k)]; ok {
			out[k] = []string{"[REDACTED]"}
			continue
		}
		out[k] = append([]string(nil), v...)
	}
	return out
}

// marshalJSON serializes v without HTML escaping and without a trailing
// newline, so each record is a single, compact JSON line.
func marshalJSON(v any) ([]byte, error) {
	var b bytes.Buffer
	enc := json.NewEncoder(&b)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		return nil, err
	}
	return bytes.TrimRight(b.Bytes(), "\n"), nil
}

// keyFor derives the Kafka message key from the record.
func keyFor(mode string, tx *Transaction) string {
	switch mode {
	case "none":
		return ""
	case "client_ip":
		return tx.Request.ClientIP
	default: // "id"
		return tx.ID
	}
}

// clientIP extracts the host part of a remote address, tolerating inputs
// without a port.
func clientIP(remoteAddr string) string {
	if remoteAddr == "" {
		return ""
	}
	if host, _, err := net.SplitHostPort(remoteAddr); err == nil {
		return host
	}
	return remoteAddr
}
