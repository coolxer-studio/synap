package storage

import (
	"os"
	"path/filepath"
	"agent-host/model"
	"strconv"
	"testing"
)

func TestSessionActiveRequiresMatchingProcessLock(t *testing.T) {
	dir := t.TempDir()
	session := model.Session{PID: os.Getpid()}
	if SessionActive(dir, session) {
		t.Fatal("session without a lock must be inactive")
	}
	if err := os.WriteFile(filepath.Join(dir, "probe.lock"), []byte(strconv.Itoa(os.Getpid())), 0o600); err != nil {
		t.Fatal(err)
	}
	if !SessionActive(dir, session) {
		t.Fatal("matching live session must be active")
	}
	if SessionActive(dir, model.Session{PID: os.Getpid() + 1}) {
		t.Fatal("mismatched session must be inactive")
	}
}
