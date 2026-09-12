package storage

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"agent-host/model"
	"github.com/google/uuid"
)

var ErrInvalidTrackingRecord = errors.New("invalid tracked queue record")

const (
	trackingLockTimeout     = 10 * time.Second
	trackingLockStaleAfter  = time.Minute
	maxTrackedEnvelopeBytes = 256 << 10
)

type trackingState struct {
	Next    map[string]int64 `json:"next"`
	Updated map[string]int64 `json:"updated"`
}

func (s *Spool) EnqueueFact(session model.Session, fact interface{}) error {
	return s.withTrackingLock(func() error {
		s.mu.Lock()
		defer s.mu.Unlock()
		if len(session.Common) != 24 || session.Common[2] == "" {
			return fmt.Errorf("%w: session common is incomplete", ErrInvalidTrackingRecord)
		}
		message, err := factMap(fact)
		if err != nil {
			return err
		}
		now := time.Now().UnixMilli()
		sequence, state, err := s.allocateSequenceLocked(session.Common[2])
		if err != nil {
			return err
		}
		message["messageId"] = "msg-" + uuid.NewString()
		message["observedAt"] = now
		message["sequence"] = sequence
		record := model.QueueRecord{
			Code: session.Code,
			Envelope: model.Envelope{
				Common: append([]string(nil), session.Common...),
				Meta: &model.RequestMeta{
					ProtocolVersion: 1,
					AppKeyID:        sessionAppKeyID(session),
					DetectorVersion: session.Common[3],
					BatchID:         "batch-" + uuid.NewString(),
					SessionID:       session.Common[2],
					CreatedAt:       now,
				},
				Data: []interface{}{message},
			},
			CreatedAt: now,
		}
		if err := validateEnvelopeSize(record.Envelope); err != nil {
			return err
		}
		state.Next[session.Common[2]] = sequence + 1
		state.Updated[session.Common[2]] = now
		if err := s.writeTrackingStateLocked(state); err != nil {
			return err
		}
		// Advancing the counter before writing the record favors a visible gap
		// over ever reusing a sequence after a crash or disk error.
		return s.writeNewRecordLocked(record)
	})
}

// PrepareUpload migrates a legacy one-fact record when necessary, increments
// the stable batch attempt, and persists the exact envelope before transport.
func (s *Spool) PrepareUpload(item *Item) (model.QueueRecord, error) {
	if item == nil || filepath.Dir(item.Path) != s.queueDir {
		return model.QueueRecord{}, fmt.Errorf("%w: invalid queue item", ErrInvalidTrackingRecord)
	}
	var prepared model.QueueRecord
	err := s.withTrackingLock(func() error {
		s.mu.Lock()
		defer s.mu.Unlock()
		data, err := os.ReadFile(item.Path)
		if err != nil {
			return err
		}
		if err := json.Unmarshal(data, &prepared); err != nil {
			return fmt.Errorf("%w: decode record: %v", ErrInvalidTrackingRecord, err)
		}
		if len(prepared.Envelope.Common) != 24 || len(prepared.Envelope.Data) != 1 {
			return fmt.Errorf("%w: host record must contain one fact and 24 common fields", ErrInvalidTrackingRecord)
		}
		message, ok := prepared.Envelope.Data[0].(map[string]interface{})
		if !ok {
			return fmt.Errorf("%w: fact must be an object", ErrInvalidTrackingRecord)
		}
		hasMessageID := nonEmptyString(message["messageId"])
		hasObservedAt := positiveInt64(message["observedAt"])
		hasSequence := positiveInt64(message["sequence"])
		hasAnyTracking := message["messageId"] != nil || message["observedAt"] != nil || message["sequence"] != nil
		if prepared.Envelope.Meta == nil && !hasAnyTracking {
			if err := s.migrateLegacyLocked(&prepared, message); err != nil {
				return err
			}
		} else if prepared.Envelope.Meta == nil || !hasMessageID || !hasObservedAt || !hasSequence {
			return fmt.Errorf("%w: partial message tracking metadata", ErrInvalidTrackingRecord)
		}
		if err := validateBatchMeta(prepared.Envelope.Meta, prepared.Envelope.Common); err != nil {
			return err
		}
		if err := validateEnvelopeSize(prepared.Envelope); err != nil {
			return err
		}
		prepared.Envelope.Meta.Attempt++
		encoded, err := json.Marshal(prepared)
		if err != nil {
			return err
		}
		if err := atomicWrite(item.Path, encoded, 0o600); err != nil {
			return err
		}
		item.Record = prepared
		return nil
	})
	return prepared, err
}

func (s *Spool) migrateLegacyLocked(record *model.QueueRecord, message map[string]interface{}) error {
	sessionID := record.Envelope.Common[2]
	sequence, state, err := s.allocateSequenceLocked(sessionID)
	if err != nil {
		return err
	}
	observedAt := record.CreatedAt
	if observedAt <= 0 {
		observedAt = time.Now().UnixMilli()
	}
	message["messageId"] = "msg-" + uuid.NewString()
	message["observedAt"] = observedAt
	message["sequence"] = sequence
	record.Envelope.Meta = &model.RequestMeta{
		ProtocolVersion: 1,
		AppKeyID:        s.appKeyIDForSessionLocked(record.Envelope.Common),
		DetectorVersion: record.Envelope.Common[3],
		BatchID:         "batch-" + uuid.NewString(),
		SessionID:       sessionID,
		CreatedAt:       observedAt,
	}
	state.Next[sessionID] = sequence + 1
	state.Updated[sessionID] = time.Now().UnixMilli()
	return s.writeTrackingStateLocked(state)
}

func sessionAppKeyID(session model.Session) string {
	if session.StartMeta.AppKeyID != "" {
		return session.StartMeta.AppKeyID
	}
	if len(session.Common) > 4 {
		return session.Common[4]
	}
	return ""
}

func (s *Spool) appKeyIDForSessionLocked(common []string) string {
	if len(common) < 5 {
		return ""
	}
	data, err := os.ReadFile(filepath.Join(s.root, "session.json"))
	if err == nil {
		var session model.Session
		if json.Unmarshal(data, &session) == nil && len(session.Common) > 2 &&
			session.Common[2] == common[2] && session.StartMeta.AppKeyID != "" {
			return session.StartMeta.AppKeyID
		}
	}
	return common[4]
}

func (s *Spool) allocateSequenceLocked(sessionID string) (int64, trackingState, error) {
	if sessionID == "" {
		return 0, trackingState{}, fmt.Errorf("%w: sessionId is empty", ErrInvalidTrackingRecord)
	}
	state, err := s.readTrackingStateLocked()
	if err != nil {
		return 0, trackingState{}, err
	}
	next := state.Next[sessionID]
	if next >= 2 {
		return next, state, nil
	}
	next = 2
	for _, dir := range []string{s.queueDir, s.deadDir} {
		files, listErr := jsonFiles(dir)
		if listErr != nil {
			return 0, trackingState{}, listErr
		}
		for _, file := range files {
			data, readErr := os.ReadFile(filepath.Join(dir, file.Name()))
			if readErr != nil {
				continue
			}
			var queued model.QueueRecord
			if json.Unmarshal(data, &queued) != nil || len(queued.Envelope.Common) < 3 || queued.Envelope.Common[2] != sessionID || len(queued.Envelope.Data) != 1 {
				continue
			}
			message, ok := queued.Envelope.Data[0].(map[string]interface{})
			if !ok {
				continue
			}
			if sequence, ok := int64Value(message["sequence"]); ok && sequence >= next {
				next = sequence + 1
			}
		}
	}
	return next, state, nil
}

func (s *Spool) readTrackingStateLocked() (trackingState, error) {
	state := trackingState{Next: map[string]int64{}, Updated: map[string]int64{}}
	data, err := os.ReadFile(filepath.Join(s.root, "tracking-state.json"))
	if os.IsNotExist(err) {
		return state, nil
	}
	if err != nil {
		return state, err
	}
	if err := json.Unmarshal(data, &state); err != nil {
		return state, err
	}
	if state.Next == nil {
		state.Next = map[string]int64{}
	}
	if state.Updated == nil {
		state.Updated = map[string]int64{}
	}
	return state, nil
}

func (s *Spool) writeTrackingStateLocked(state trackingState) error {
	expiredBefore := time.Now().Add(-maxAge).UnixMilli()
	for sessionID, updatedAt := range state.Updated {
		if updatedAt > 0 && updatedAt < expiredBefore {
			delete(state.Updated, sessionID)
			delete(state.Next, sessionID)
		}
	}
	data, err := json.Marshal(state)
	if err != nil {
		return err
	}
	return atomicWrite(filepath.Join(s.root, "tracking-state.json"), data, 0o600)
}

func (s *Spool) writeNewRecordLocked(record model.QueueRecord) error {
	data, err := json.Marshal(record)
	if err != nil {
		return err
	}
	if int64(len(data)) > s.maxBytes {
		return errors.New("record exceeds spool byte limit")
	}
	name := fmt.Sprintf("%020d-%s.json", time.Now().UnixNano(), randomID())
	if err := atomicWrite(filepath.Join(s.queueDir, name), data, 0o600); err != nil {
		return err
	}
	return s.pruneLocked(time.Now())
}

func factMap(fact interface{}) (map[string]interface{}, error) {
	data, err := json.Marshal(fact)
	if err != nil {
		return nil, err
	}
	var message map[string]interface{}
	if err := json.Unmarshal(data, &message); err != nil || message == nil {
		return nil, fmt.Errorf("%w: fact must encode as an object", ErrInvalidTrackingRecord)
	}
	if !nonEmptyString(message["type"]) {
		return nil, fmt.Errorf("%w: fact type is required", ErrInvalidTrackingRecord)
	}
	return message, nil
}

func validateBatchMeta(meta *model.RequestMeta, common []string) error {
	if meta == nil || meta.ProtocolVersion != 1 || meta.AppKeyID == "" || meta.DetectorVersion == "" ||
		meta.BatchID == "" || meta.SessionID != common[2] || meta.CreatedAt <= 0 || meta.Attempt < 0 {
		return fmt.Errorf("%w: invalid batch metadata", ErrInvalidTrackingRecord)
	}
	return nil
}

func validateEnvelopeSize(envelope model.Envelope) error {
	data, err := json.Marshal(envelope)
	if err != nil {
		return err
	}
	if len(data) > maxTrackedEnvelopeBytes {
		return fmt.Errorf("%w: tracked envelope exceeds 256 KiB", ErrInvalidTrackingRecord)
	}
	return nil
}

func nonEmptyString(value interface{}) bool {
	text, ok := value.(string)
	return ok && text != ""
}

func positiveInt64(value interface{}) bool {
	number, ok := int64Value(value)
	return ok && number > 0
}

func int64Value(value interface{}) (int64, bool) {
	switch typed := value.(type) {
	case float64:
		return int64(typed), typed > 0 && typed == float64(int64(typed))
	case int64:
		return typed, typed > 0
	case int:
		return int64(typed), typed > 0
	case json.Number:
		parsed, err := typed.Int64()
		return parsed, err == nil && parsed > 0
	case string:
		parsed, err := strconv.ParseInt(typed, 10, 64)
		return parsed, err == nil && parsed > 0
	default:
		return 0, false
	}
}

func (s *Spool) withTrackingLock(action func() error) error {
	path := filepath.Join(s.root, "tracking.lock")
	deadline := time.Now().Add(trackingLockTimeout)
	for {
		file, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
		if err == nil {
			_, _ = file.WriteString(strconv.Itoa(os.Getpid()))
			_ = file.Close()
			defer os.Remove(path)
			return action()
		}
		if !os.IsExist(err) {
			return err
		}
		if info, statErr := os.Stat(path); statErr == nil && time.Since(info.ModTime()) > trackingLockStaleAfter {
			_ = os.Remove(path)
			continue
		}
		if time.Now().After(deadline) {
			return errors.New("timed out acquiring tracking lock")
		}
		time.Sleep(10 * time.Millisecond)
	}
}
