package main

import "sync/atomic"

// Metrics holds lightweight counters for the probe. All fields are safe for
// concurrent use.
type Metrics struct {
	Captured      atomic.Int64 // transactions captured and formatted
	Sent          atomic.Int64 // records acknowledged by Kafka
	SendErrors    atomic.Int64 // failed produce attempts
	Spooled       atomic.Int64 // records written to the fallback file
	Dropped       atomic.Int64 // records lost (no fallback, or fallback error)
	Replayed      atomic.Int64 // records replayed from the fallback file
	ReplayErrors  atomic.Int64 // failed replay attempts
	QueueOverflow atomic.Int64 // records spilled because the queue was full
}

// Snapshot is a JSON-friendly copy of the counters.
type Snapshot struct {
	Captured      int64 `json:"captured"`
	Sent          int64 `json:"sent"`
	SendErrors    int64 `json:"send_errors"`
	Spooled       int64 `json:"spooled"`
	Dropped       int64 `json:"dropped"`
	Replayed      int64 `json:"replayed"`
	ReplayErrors  int64 `json:"replay_errors"`
	QueueOverflow int64 `json:"queue_overflow"`
}

func (m *Metrics) Snapshot() Snapshot {
	return Snapshot{
		Captured:      m.Captured.Load(),
		Sent:          m.Sent.Load(),
		SendErrors:    m.SendErrors.Load(),
		Spooled:       m.Spooled.Load(),
		Dropped:       m.Dropped.Load(),
		Replayed:      m.Replayed.Load(),
		ReplayErrors:  m.ReplayErrors.Load(),
		QueueOverflow: m.QueueOverflow.Load(),
	}
}
