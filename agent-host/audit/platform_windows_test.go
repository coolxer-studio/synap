//go:build windows

package audit

import (
	"testing"
	"time"
)

func TestWindowsEventXMLFixtures(t *testing.T) {
	fixture := []byte(`<Events><Event xmlns="http://schemas.microsoft.com/win/2004/08/events/event"><System><EventID>4625</EventID><EventRecordID>77</EventRecordID><TimeCreated SystemTime="2026-08-05T10:00:00.0000000Z"/></System><EventData><Data Name="TargetUserName">alice</Data><Data Name="ProcessId">0x2a</Data><Data Name="SourceIp">2001:db8::1</Data><Data Name="SourcePort">55000</Data></EventData></Event></Events>`)
	values := parseWindowsEventXML(fixture)
	if len(values) != 1 {
		t.Fatalf("events=%d", len(values))
	}
	event, ok := mapWindowsEvent(values[0], "Security", "boot", time.Unix(1, 0))
	if !ok || event.Category != "session" || event.Action != "login" || event.Result != "failure" || event.PID != "42" || event.RemoteEndpoint != "[2001:db8::1]:55000" {
		t.Fatalf("event=%+v ok=%v", event, ok)
	}
	if normalizeWindowsProtocol("6") != "tcp" || normalizeWindowsProtocol("17") != "udp" {
		t.Fatal("protocol mapping")
	}
}
