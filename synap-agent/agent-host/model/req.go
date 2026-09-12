package model

// Envelope is the legacy Host wire envelope. Common deliberately remains an
// ordered string slice because synap-server maps its 24 entries by position.
type Envelope struct {
	Common []string      `json:"common"`
	Meta   *RequestMeta  `json:"meta,omitempty"`
	Data   []interface{} `json:"data,omitempty"`
}

type RequestMeta struct {
	ProtocolVersion int    `json:"protocolVersion"`
	AppKeyID        string `json:"appKeyId"`
	DetectorVersion string `json:"detectorVersion"`
	MessageID       string `json:"messageId,omitempty"`
	BatchID         string `json:"batchId"`
	SessionID       string `json:"sessionId"`
	Attempt         int    `json:"attempt"`
	CreatedAt       int64  `json:"createdAt"`
	ObservedAt      int64  `json:"observedAt,omitempty"`
	Sequence        int64  `json:"sequence,omitempty"`
}

// QueueRecord keeps the original session identity with a batch so retries
// after a process restart cannot silently change startId or header fields.
type QueueRecord struct {
	Code      string   `json:"code"`
	Envelope  Envelope `json:"envelope"`
	CreatedAt int64    `json:"createdAt"`
}

type Session struct {
	Code      string      `json:"code"`
	Common    []string    `json:"common"`
	StartID   int64       `json:"startId"`
	PID       int         `json:"pid"`
	StartedAt int64       `json:"startedAt"`
	StartMeta RequestMeta `json:"startMeta"`
}

// AuditData is the sixth Host fact. Events are positional arrays to preserve
// the existing Host protocol rather than introducing a new acknowledgement
// envelope.
type AuditData struct {
	Type      string     `json:"type"`
	BuildDate string     `json:"buildDate"`
	BatchID   string     `json:"batchId"`
	Events    [][]string `json:"events"`
}
