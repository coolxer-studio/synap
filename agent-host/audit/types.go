package audit

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

const (
	MaxDetailBytes = 4 << 10
	EventColumns   = 18
)

var Columns = []string{
	"eventId", "eventTime", "category", "action", "result", "actor", "subject", "target",
	"pid", "process", "localEndpoint", "remoteEndpoint", "protocol", "digestAlgorithm", "digest",
	"source", "bootId", "detail",
}

var categories = map[string]struct{}{
	"device": {}, "session": {}, "account": {}, "config_file": {}, "peripheral": {},
	"network": {}, "process": {}, "business_file": {}, "scheduled_task": {}, "capability": {},
}

type Event struct {
	EventID, EventTime, Category, Action, Result, Actor, Subject, Target string
	PID, Process, LocalEndpoint, RemoteEndpoint, Protocol                string
	DigestAlgorithm, Digest, Source, BootID, Detail                      string
}

func NewEvent(bootID, source, rawID, category, action, result string, at time.Time) Event {
	if _, ok := categories[category]; !ok {
		category = "capability"
	}
	if result == "" {
		result = "observed"
	}
	seed := strings.Join([]string{bootID, source, rawID, category, action}, "\x00")
	sum := sha256.Sum256([]byte(seed))
	return Event{
		EventID: hex.EncodeToString(sum[:]), EventTime: at.UTC().Format(time.RFC3339Nano),
		Category: category, Action: action, Result: result, Source: source, BootID: bootID,
	}
}

func (e Event) Row() []string {
	return []string{e.EventID, e.EventTime, e.Category, e.Action, e.Result, e.Actor, e.Subject, e.Target,
		e.PID, e.Process, e.LocalEndpoint, e.RemoteEndpoint, e.Protocol, e.DigestAlgorithm, e.Digest,
		e.Source, e.BootID, truncateUTF8(e.Detail, MaxDetailBytes)}
}

func JSONDetail(value any) string {
	encoded, err := json.Marshal(value)
	if err != nil {
		return `{}`
	}
	return truncateUTF8(redact(string(encoded)), MaxDetailBytes)
}

func Int(value int64) string { return strconv.FormatInt(value, 10) }

func truncateUTF8(value string, max int) string {
	if len(value) <= max {
		return value
	}
	value = value[:max]
	for !utf8.ValidString(value) && len(value) > 0 {
		value = value[:len(value)-1]
	}
	return value
}

func stableKey(parts ...any) string {
	var b strings.Builder
	for _, part := range parts {
		fmt.Fprint(&b, part, "\x00")
	}
	sum := sha256.Sum256([]byte(b.String()))
	return hex.EncodeToString(sum[:])
}
