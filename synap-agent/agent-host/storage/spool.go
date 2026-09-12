package storage

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"agent-host/model"
)

const maxAge = 7 * 24 * time.Hour

type Spool struct {
	root     string
	queueDir string
	deadDir  string
	maxBytes int64
	maxItems int
	mu       sync.Mutex
}

type Item struct {
	Path   string
	Record model.QueueRecord
}

func (s *Spool) Root() string { return s.root }

func New(root string, maxBytes int64, maxItems int) (*Spool, error) {
	if maxBytes <= 0 || maxItems <= 0 {
		return nil, errors.New("invalid spool limits")
	}
	s := &Spool{root: root, queueDir: filepath.Join(root, "spool"), deadDir: filepath.Join(root, "dead-letter"), maxBytes: maxBytes, maxItems: maxItems}
	for _, dir := range []string{root, s.queueDir, s.deadDir} {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			return nil, err
		}
		_ = os.Chmod(dir, 0o700)
	}
	if err := s.pruneLocked(time.Now()); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Spool) Enqueue(record model.QueueRecord) error {
	s.mu.Lock()
	defer s.mu.Unlock()
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

func (s *Spool) Next() (*Item, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	files, err := jsonFiles(s.queueDir)
	if err != nil || len(files) == 0 {
		return nil, err
	}
	path := filepath.Join(s.queueDir, files[0].Name())
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var record model.QueueRecord
	if err := json.Unmarshal(data, &record); err != nil {
		_ = os.Rename(path, filepath.Join(s.deadDir, files[0].Name()))
		return nil, fmt.Errorf("invalid spool record: %w", err)
	}
	return &Item{Path: path, Record: record}, nil
}

func (s *Spool) Ack(item *Item) error {
	if item == nil || filepath.Dir(item.Path) != s.queueDir {
		return errors.New("invalid spool item")
	}
	return os.Remove(item.Path)
}

func (s *Spool) Reject(item *Item) error {
	if item == nil || filepath.Dir(item.Path) != s.queueDir {
		return errors.New("invalid spool item")
	}
	if err := os.Rename(item.Path, filepath.Join(s.deadDir, filepath.Base(item.Path))); err != nil {
		return err
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return pruneDir(s.deadDir, s.maxBytes, s.maxItems, time.Now())
}

func (s *Spool) SaveSession(session model.Session) error {
	data, err := json.Marshal(session)
	if err != nil {
		return err
	}
	return atomicWrite(filepath.Join(s.root, "session.json"), data, 0o600)
}

func (s *Spool) LoadSession() (model.Session, error) {
	data, err := os.ReadFile(filepath.Join(s.root, "session.json"))
	if err != nil {
		return model.Session{}, err
	}
	var session model.Session
	err = json.Unmarshal(data, &session)
	return session, err
}

func (s *Spool) pruneLocked(now time.Time) error {
	if err := pruneDir(s.queueDir, s.maxBytes, s.maxItems, now); err != nil {
		return err
	}
	return pruneDir(s.deadDir, s.maxBytes, s.maxItems, now)
}

func pruneDir(dir string, maxBytes int64, maxItems int, now time.Time) error {
	files, err := jsonFiles(dir)
	if err != nil {
		return err
	}
	var total int64
	for _, file := range files {
		total += file.Size()
	}
	for len(files) > 0 && (len(files) > maxItems || total > maxBytes || now.Sub(files[0].ModTime()) > maxAge) {
		total -= files[0].Size()
		if err := os.Remove(filepath.Join(dir, files[0].Name())); err != nil && !os.IsNotExist(err) {
			return err
		}
		files = files[1:]
	}
	return nil
}

func jsonFiles(dir string) ([]os.FileInfo, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	files := make([]os.FileInfo, 0, len(entries))
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".json") {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			return nil, err
		}
		files = append(files, info)
	}
	sort.Slice(files, func(i, j int) bool { return files[i].Name() < files[j].Name() })
	return files, nil
}

func atomicWrite(path string, data []byte, mode os.FileMode) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), ".tmp-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)
	if err := tmp.Chmod(mode); err != nil {
		_ = tmp.Close()
		return err
	}
	if _, err := tmp.Write(data); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, path)
}

func randomID() string {
	value := make([]byte, 6)
	if _, err := rand.Read(value); err != nil {
		return fmt.Sprintf("%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(value)
}
