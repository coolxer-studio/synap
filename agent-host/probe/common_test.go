package probe

import (
	"testing"
	"time"
)

func TestSessionCommonAndHeaderAreStable(t *testing.T) {
	ConfigureDataDir(t.TempDir())
	now := time.UnixMilli(1_700_000_000_000)
	session := NewSession(SessionOptions{AppID: 7, AppName: "app", AppPackage: "pkg", AppVersion: "1", WANIP: "8.8.8.8"}, now)
	if len(session.Common) != 24 {
		t.Fatalf("common len=%d", len(session.Common))
	}
	if session.Common[2] != "1700000000000" || session.StartID != 1_700_000_000_000 {
		t.Fatalf("start id mismatch")
	}
	if session.Code != session.Common[1]+"-host-7-"+SDKVersion {
		t.Fatalf("code=%s", session.Code)
	}
	if session.Common[15] != "8.8.8.8" {
		t.Fatalf("wan ip=%q", session.Common[15])
	}
	if session.StartMeta.MessageID == "" || session.StartMeta.BatchID == "" ||
		session.StartMeta.ProtocolVersion != 1 || session.StartMeta.AppKeyID != "7" ||
		session.StartMeta.DetectorVersion != SDKVersion ||
		session.StartMeta.SessionID != session.Common[2] || session.StartMeta.Sequence != 1 ||
		session.StartMeta.CreatedAt != now.UnixMilli() || session.StartMeta.ObservedAt != now.UnixMilli() {
		t.Fatalf("invalid start tracking metadata: %+v", session.StartMeta)
	}
}
