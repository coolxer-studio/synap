//go:build windows

package audit

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/xml"
	"net"
	"os/exec"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

func platformName() string { return "windows" }

func platformBootID() string {
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
	defer cancel()
	out, _ := exec.CommandContext(ctx, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", `(Get-CimInstance Win32_OperatingSystem).LastBootUpTime.ToUniversalTime().ToString('o')`).Output()
	if len(out) == 0 {
		return ""
	}
	sum := sha256.Sum256(out)
	return hex.EncodeToString(sum[:16])
}

func builtinCriticalFiles() []string {
	return []string{`C:\Windows\System32\drivers\etc\hosts`, `C:\Windows\System32\config\SAM`, `C:\Windows\System32\config\SYSTEM`, `C:\Windows\System32\GroupPolicy\Machine\Registry.pol`}
}

type windowsEvent struct {
	System struct {
		EventID     int    `xml:"EventID"`
		RecordID    string `xml:"EventRecordID"`
		TimeCreated struct {
			SystemTime string `xml:"SystemTime,attr"`
		} `xml:"TimeCreated"`
	} `xml:"System"`
	Data []struct {
		Name  string `xml:"Name,attr"`
		Value string `xml:",chardata"`
	} `xml:"EventData>Data"`
}

func platformEvents(current *state, bootID string, now time.Time, baseline bool) ([]Event, error) {
	if baseline {
		return nil, nil
	}
	var result []Event
	channels := []string{"Security", "Microsoft-Windows-TaskScheduler/Operational", "Microsoft-Windows-Kernel-PnP/Configuration", "Microsoft-Windows-Sysmon/Operational"}
	for _, channel := range channels {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		query := `*[System[TimeCreated[timediff(@SystemTime) <= 5000]]]`
		output, err := exec.CommandContext(ctx, "wevtutil.exe", "qe", channel, "/rd:true", "/c:200", "/f:xml", "/q:"+query).Output()
		cancel()
		if err != nil {
			capabilityKey := "capability:windows:" + channel
			if _, emitted := current.Cursors[capabilityKey]; !emitted {
				current.Cursors[capabilityKey] = cursor{ID: "unavailable"}
				e := NewEvent(bootID, "windows_event_log", stableKey(channel, "unavailable"), "capability", "degraded", "unavailable", now)
				e.Target = channel
				e.Detail = JSONDetail(map[string]string{"platform": "windows", "missingSource": channel, "permission": "administrator and enabled audit policy required", "affectedCategories": windowsAffectedCategories(channel)})
				result = append(result, e)
			}
			continue
		}
		for _, raw := range parseWindowsEventXML(output) {
			key := "windows:" + channel + ":" + raw.System.RecordID
			if _, ok := current.Cursors[key]; ok {
				continue
			}
			current.Cursors[key] = cursor{ID: raw.System.RecordID}
			if event, ok := mapWindowsEvent(raw, channel, bootID, now); ok {
				result = append(result, event)
			}
		}
	}
	pruneEventCursors(current.Cursors, "windows:", 10000)
	return result, nil
}

func windowsAffectedCategories(channel string) string {
	switch channel {
	case "Security":
		return "device,session,account,process"
	case "Microsoft-Windows-TaskScheduler/Operational":
		return "scheduled_task"
	case "Microsoft-Windows-Kernel-PnP/Configuration":
		return "peripheral"
	case "Microsoft-Windows-Sysmon/Operational":
		return "process,network,business_file"
	default:
		return "unknown"
	}
}

func mapWindowsEvent(raw windowsEvent, channel, bootID string, now time.Time) (Event, bool) {
	category, action, result := "", "", "success"
	switch raw.System.EventID {
	case 4624:
		category, action = "session", "login"
	case 4625:
		category, action, result = "session", "login", "failure"
	case 4634, 4647:
		category, action = "session", "logout"
	case 4720:
		category, action = "account", "created"
	case 4726:
		category, action = "account", "deleted"
	case 4728, 4729, 4732, 4733, 4756, 4757:
		category, action = "account", "permission_changed"
	case 4688, 1:
		category, action = "process", "started"
	case 4689, 5:
		category, action = "process", "terminated"
	case 3:
		category, action = "network", "established"
	case 11, 15, 23, 26:
		category, action = "business_file", map[int]string{11: "created", 15: "modified", 23: "deleted", 26: "deleted"}[raw.System.EventID]
	case 106, 140:
		category, action = "scheduled_task", map[int]string{106: "created", 140: "modified"}[raw.System.EventID]
	case 100, 200:
		category, action = "scheduled_task", "executed"
	case 20001, 400, 410:
		category, action = "peripheral", "attached"
	default:
		return Event{}, false
	}
	fields := map[string]string{}
	for _, field := range raw.Data {
		fields[field.Name] = redact(field.Value)
	}
	eventTime := now
	if parsed, err := time.Parse(time.RFC3339Nano, raw.System.TimeCreated.SystemTime); err == nil {
		eventTime = parsed
	}
	e := NewEvent(bootID, "windows_event_log", channel+":"+raw.System.RecordID, category, action, result, eventTime)
	e.Actor = first(fields, "SubjectUserName", "User", "UserName")
	e.Subject = first(fields, "TargetUserName", "AccountName")
	e.Target = first(fields, "TargetFilename", "Image", "TaskName", "DeviceInstanceId")
	e.PID = normalizeWindowsPID(first(fields, "ProcessId", "ProcessID"))
	e.Process = first(fields, "NewProcessName", "Image")
	if category == "session" {
		e.RemoteEndpoint = joinWindowsEndpoint(first(fields, "IpAddress", "SourceIp"), first(fields, "IpPort", "SourcePort"))
	} else {
		e.LocalEndpoint = joinWindowsEndpoint(fields["SourceIp"], fields["SourcePort"])
		e.RemoteEndpoint = joinWindowsEndpoint(fields["DestinationIp"], fields["DestinationPort"])
	}
	e.Protocol = normalizeWindowsProtocol(fields["Protocol"])
	e.Detail = JSONDetail(fields)
	return e, true
}

func first(values map[string]string, keys ...string) string {
	for _, key := range keys {
		if values[key] != "" {
			return values[key]
		}
	}
	return ""
}
func joinWindowsEndpoint(host, port string) string {
	if host == "" {
		return ""
	}
	if port == "" {
		return host
	}
	return net.JoinHostPort(host, port)
}

var windowsEventPattern = regexp.MustCompile(`(?s)<Event(?:\s[^>]*)?>.*?</Event>`)

func parseWindowsEventXML(output []byte) []windowsEvent {
	var result []windowsEvent
	for _, value := range windowsEventPattern.FindAll(output, 200) {
		var event windowsEvent
		if xml.Unmarshal(value, &event) == nil {
			result = append(result, event)
		}
	}
	return result
}

func normalizeWindowsPID(value string) string {
	if value == "" {
		return ""
	}
	parsed, err := strconv.ParseUint(value, 0, 64)
	if err != nil {
		parsed, err = strconv.ParseUint(value, 10, 64)
	}
	if err != nil {
		return ""
	}
	return strconv.FormatUint(parsed, 10)
}

func normalizeWindowsProtocol(value string) string {
	switch strings.ToLower(value) {
	case "6", "tcp":
		return "tcp"
	case "17", "udp":
		return "udp"
	default:
		return ""
	}
}

func diffMapEvents(before, after map[string]string, bootID string, now time.Time, category, added, removed, source string) []Event {
	return nil
}
func pruneEventCursors(values map[string]cursor, prefix string, max int) {
	keys := make([]string, 0)
	for key := range values {
		if strings.HasPrefix(key, prefix) {
			keys = append(keys, key)
		}
	}
	if len(keys) <= max {
		return
	}
	sort.Strings(keys)
	for _, key := range keys[:len(keys)-max] {
		delete(values, key)
	}
}
