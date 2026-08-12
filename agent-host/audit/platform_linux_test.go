//go:build linux

package audit

import (
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestParseLinuxAuditFixtures(t *testing.T) {
	fixtures := []struct{ line, category, action, result string }{
		{"sshd[1]: Accepted publickey for root from 203.0.113.8 port 42 ssh2", "session", "login", "success"},
		{"sshd[1]: Failed password for invalid user demo from 203.0.113.9 port 42 ssh2", "session", "login", "failure"},
		{"type=ADD_USER msg=audit(1.1:5): pid=2 uid=0 res=success", "account", "created", "success"},
		{"type=EXECVE msg=audit(1.1:6): argc=2 a0=\"curl\" a1=\"token=secret\"", "process", "started", "success"},
		{"type=PATH msg=audit(1.1:7): name=\"/etc/hosts\" nametype=CREATE", "config_file", "created", "success"},
		{"Aug 5 host CRON[44]: (root) CMD (/usr/local/bin/job)", "scheduled_task", "executed", "success"},
	}
	for _, fixture := range fixtures {
		event, ok := parseLinuxAuditLine(fixture.line, "boot", "fixture", time.Unix(1, 0), 0)
		if !ok || event.Category != fixture.category || event.Action != fixture.action || event.Result != fixture.result {
			t.Fatalf("line=%q event=%+v ok=%v", fixture.line, event, ok)
		}
	}
}

func TestIncrementalCursorBaselineAppendAndRotation(t *testing.T) {
	path := filepath.Join(t.TempDir(), "audit.log")
	if err := os.WriteFile(path, []byte("old\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	current := emptyState()
	lines, err := incrementalLines(path, &current, true)
	if err != nil || len(lines) != 0 {
		t.Fatalf("baseline lines=%v err=%v", lines, err)
	}
	file, err := os.OpenFile(path, os.O_APPEND|os.O_WRONLY, 0)
	if err != nil {
		t.Fatal(err)
	}
	_, _ = file.WriteString("new\n")
	_ = file.Close()
	lines, err = incrementalLines(path, &current, false)
	if err != nil || len(lines) != 1 || lines[0] != "new" {
		t.Fatalf("append lines=%v err=%v", lines, err)
	}
	rotated := path + ".1"
	if err := os.Rename(path, rotated); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("rotated\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	lines, err = incrementalLines(path, &current, false)
	if err != nil || len(lines) != 1 || lines[0] != "rotated" {
		t.Fatalf("rotation lines=%v err=%v", lines, err)
	}
}
