//go:build darwin

package audit

import (
	"testing"
	"time"
)

func TestParseDarwinUnifiedLogFixtures(t *testing.T) {
	fixtures := []struct{ line, category, action, result string }{
		{"sshd: authentication failed for user alice", "session", "login", "failure"},
		{"loginwindow: session opened user=bob", "session", "login", "success"},
		{"opendirectoryd: create user=carol", "account", "created", "success"},
		{"cron: CMD user=root /usr/local/bin/job", "scheduled_task", "executed", "success"},
	}
	for _, fixture := range fixtures {
		event, ok := parseDarwinLogLine(fixture.line, "boot", time.Unix(1, 0))
		if !ok || event.Category != fixture.category || event.Action != fixture.action || event.Result != fixture.result {
			t.Fatalf("line=%q event=%+v ok=%v", fixture.line, event, ok)
		}
	}
}
