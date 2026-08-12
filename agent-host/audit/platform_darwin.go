//go:build darwin

package audit

import (
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"time"
)

func platformName() string { return "darwin" }

func platformBootID() string {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	out, _ := exec.CommandContext(ctx, "/usr/sbin/sysctl", "-n", "kern.boottime").Output()
	sum := sha256.Sum256(out)
	if len(out) == 0 {
		return ""
	}
	return hex.EncodeToString(sum[:16])
}

func builtinCriticalFiles() []string {
	return []string{"/etc/passwd", "/etc/group", "/etc/hosts", "/etc/resolv.conf", "/etc/ssh/sshd_config", "/etc/sudoers", "/etc/pam.d/authorization", "/Library/Preferences/com.apple.loginwindow.plist"}
}

func platformEvents(current *state, bootID string, now time.Time, baseline bool) ([]Event, error) {
	var events []Event
	openBSMEvents, openBSMAvailable := readOpenBSM(current, bootID, now, baseline)
	events = append(events, openBSMEvents...)
	if !openBSMAvailable {
		events = append(events, readESLogger(current, bootID, now, baseline)...)
	}
	if !baseline {
		ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
		output, err := exec.CommandContext(ctx, "/usr/bin/log", "show", "--last", "3s", "--style", "compact", "--predicate", `process == "sshd" OR process == "loginwindow" OR process == "opendirectoryd" OR process == "securityd" OR process == "cron"`).Output()
		cancel()
		if err == nil {
			scanner := bufio.NewScanner(strings.NewReader(string(output)))
			scanner.Buffer(make([]byte, 64<<10), 1<<20)
			for scanner.Scan() {
				if event, ok := parseDarwinLogLine(scanner.Text(), bootID, now); ok {
					if _, seen := current.Cursors["darwin:"+event.EventID]; !seen {
						current.Cursors["darwin:"+event.EventID] = cursor{ID: event.EventID}
						events = append(events, event)
					}
				}
			}
		} else {
			if _, emitted := current.Cursors["capability:darwin-unified-log"]; !emitted {
				current.Cursors["capability:darwin-unified-log"] = cursor{ID: "unavailable"}
				e := NewEvent(bootID, "unified_log", "missing:unified_log", "capability", "degraded", "unavailable", now)
				e.Detail = JSONDetail(map[string]string{"platform": "darwin", "missingSource": "unified log/OpenBSM", "permission": "root/full disk access may be required", "affectedCategories": "session,account,scheduled_task"})
				events = append(events, e)
			}
		}
	}
	events = append(events, diffDarwinUSB(current, bootID, now, baseline)...)
	events = append(events, diffDarwinTasks(current, bootID, now, baseline)...)
	pruneEventCursors(current.Cursors, "darwin:", 2000)
	return events, nil
}

func readOpenBSM(current *state, bootID string, now time.Time, baseline bool) ([]Event, bool) {
	if _, err := os.Stat("/var/audit/current"); err != nil {
		return nil, false
	}
	position := current.Cursors["openbsm"]
	if baseline || position.ID == "" {
		current.Cursors["openbsm"] = cursor{ID: now.Format(time.RFC3339Nano)}
		return nil, true
	}
	since, err := time.Parse(time.RFC3339Nano, position.ID)
	if err != nil {
		since = now.Add(-3 * time.Second)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	reduce := exec.CommandContext(ctx, "/usr/sbin/auditreduce", "-a", since.Local().Format("20060102150405"), "/var/audit/current")
	praudit := exec.CommandContext(ctx, "/usr/sbin/praudit", "-l")
	pipe, err := reduce.StdoutPipe()
	if err != nil {
		return nil, false
	}
	praudit.Stdin = pipe
	var output bytes.Buffer
	praudit.Stdout = &output
	if err := praudit.Start(); err != nil {
		return nil, false
	}
	if err := reduce.Start(); err != nil {
		_ = praudit.Process.Kill()
		return nil, false
	}
	_ = reduce.Wait()
	_ = praudit.Wait()
	current.Cursors["openbsm"] = cursor{ID: now.Format(time.RFC3339Nano)}
	var events []Event
	scanner := bufio.NewScanner(strings.NewReader(output.String()))
	scanner.Buffer(make([]byte, 64<<10), 1<<20)
	for scanner.Scan() {
		if len(events) >= 1000 {
			break
		}
		if event, ok := parseOpenBSMLine(scanner.Text(), bootID, now); ok {
			events = append(events, event)
		}
	}
	return events, true
}

func parseOpenBSMLine(line, bootID string, now time.Time) (Event, bool) {
	lower := strings.ToLower(line)
	category, action, result := "", "", "success"
	if strings.Contains(lower, "failure") {
		result = "failure"
	}
	switch {
	case strings.Contains(lower, "aue_login") || strings.Contains(lower, "aue_ssauthorize"):
		category, action = "session", "login"
	case strings.Contains(lower, "aue_logout"):
		category, action = "session", "logout"
	case strings.Contains(lower, "aue_add_user"):
		category, action = "account", "created"
	case strings.Contains(lower, "aue_remove_user"):
		category, action = "account", "deleted"
	case strings.Contains(lower, "aue_modify_group") || strings.Contains(lower, "aue_setegid"):
		category, action = "account", "permission_changed"
	case strings.Contains(lower, "aue_exec"):
		category, action = "process", "started"
	case strings.Contains(lower, "aue_create") || strings.Contains(lower, "aue_open_rw"):
		category, action = "business_file", "created"
	case strings.Contains(lower, "aue_rename") || strings.Contains(lower, "aue_write"):
		category, action = "business_file", "modified"
	case strings.Contains(lower, "aue_unlink") || strings.Contains(lower, "aue_delete"):
		category, action = "business_file", "deleted"
	default:
		return Event{}, false
	}
	e := NewEvent(bootID, "openbsm", stableKey(line), category, action, result, now)
	e.Detail = JSONDetail(map[string]string{"record": redact(line)})
	return e, true
}

func readESLogger(current *state, bootID string, now time.Time, baseline bool) []Event {
	if baseline {
		return nil
	}
	if _, err := os.Stat("/usr/bin/eslogger"); err != nil {
		if _, emitted := current.Cursors["capability:darwin-eslogger"]; emitted {
			return nil
		}
		current.Cursors["capability:darwin-eslogger"] = cursor{ID: "unavailable"}
		e := NewEvent(bootID, "eslogger", "missing:eslogger", "capability", "degraded", "unavailable", now)
		e.Detail = JSONDetail(map[string]string{"platform": "darwin", "missingSource": "OpenBSM/eslogger", "affectedCategories": "process,session,account,business_file"})
		return []Event{e}
	}
	ctx, cancel := context.WithTimeout(context.Background(), 1500*time.Millisecond)
	defer cancel()
	output, _ := exec.CommandContext(ctx, "/usr/bin/eslogger", "exec", "exit", "open", "close", "rename", "unlink", "authentication", "login", "logout").Output()
	var events []Event
	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	scanner.Buffer(make([]byte, 64<<10), 1<<20)
	for scanner.Scan() {
		line := scanner.Text()
		lower := strings.ToLower(line)
		category, action := "", ""
		switch {
		case strings.Contains(lower, `"event_type":"exec"`):
			category, action = "process", "started"
		case strings.Contains(lower, `"event_type":"exit"`):
			category, action = "process", "terminated"
		case strings.Contains(lower, `"event_type":"authentication"`) || strings.Contains(lower, `"event_type":"login"`):
			category, action = "session", "login"
		case strings.Contains(lower, `"event_type":"logout"`):
			category, action = "session", "logout"
		case strings.Contains(lower, `"event_type":"unlink"`):
			category, action = "business_file", "deleted"
		case strings.Contains(lower, `"event_type":"rename"`) || strings.Contains(lower, `"event_type":"open"`):
			category, action = "business_file", "modified"
		}
		if category != "" {
			e := NewEvent(bootID, "eslogger", stableKey(line), category, action, "observed", now)
			e.Detail = JSONDetail(map[string]string{"record": redact(line)})
			events = append(events, e)
		}
	}
	return events
}

var darwinUser = regexp.MustCompile(`(?i)(?:user|account)[ =:\"]+([a-z0-9._-]+)`)

func parseDarwinLogLine(line, bootID string, now time.Time) (Event, bool) {
	lower := strings.ToLower(line)
	category, action, result := "", "", "success"
	switch {
	case strings.Contains(lower, "authentication") && (strings.Contains(lower, "fail") || strings.Contains(lower, "denied")):
		category, action, result = "session", "login", "failure"
	case strings.Contains(lower, "authenticated") || strings.Contains(lower, "session opened"):
		category, action = "session", "login"
	case strings.Contains(lower, "logout") || strings.Contains(lower, "session closed"):
		category, action = "session", "logout"
	case strings.Contains(lower, "create") && strings.Contains(lower, "user"):
		category, action = "account", "created"
	case strings.Contains(lower, "delete") && strings.Contains(lower, "user"):
		category, action = "account", "deleted"
	case strings.Contains(lower, "group") || strings.Contains(lower, "privilege"):
		category, action = "account", "permission_changed"
	case strings.Contains(lower, "cron") && (strings.Contains(lower, "cmd") || strings.Contains(lower, "start")):
		category, action = "scheduled_task", "executed"
	default:
		return Event{}, false
	}
	e := NewEvent(bootID, "unified_log", stableKey(line), category, action, result, now)
	if match := darwinUser.FindStringSubmatch(line); len(match) == 2 {
		e.Actor, e.Subject = match[1], match[1]
	}
	e.Detail = JSONDetail(map[string]string{"record": redact(line)})
	return e, true
}

func diffDarwinUSB(current *state, bootID string, now time.Time, baseline bool) []Event {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	output, _ := exec.CommandContext(ctx, "/usr/sbin/ioreg", "-p", "IOUSB", "-l", "-w", "0").Output()
	next := map[string]string{}
	for _, line := range strings.Split(string(output), "\n") {
		line = strings.TrimSpace(line)
		if strings.Contains(line, "+-o ") {
			key := strings.TrimSpace(strings.SplitN(strings.TrimPrefix(line, "+-o "), "<", 2)[0])
			if key != "" {
				next[key] = stableKey(line)
			}
		}
	}
	if baseline {
		current.Peripherals = next
		return nil
	}
	events := diffMapEvents(current.Peripherals, next, bootID, now, "peripheral", "attached", "detached", "iokit")
	current.Peripherals = next
	return events
}

func diffDarwinTasks(current *state, bootID string, now time.Time, baseline bool) []Event {
	next := map[string]string{}
	patterns := []string{"/Library/LaunchDaemons/*.plist", "/Library/LaunchAgents/*.plist", "/System/Library/LaunchDaemons/*.plist", "/System/Library/LaunchAgents/*.plist", "/var/at/tabs/*"}
	for _, pattern := range patterns {
		paths, _ := filepath.Glob(pattern)
		for _, path := range paths {
			if info, err := os.Stat(path); err == nil {
				next[path] = stableKey(info.Size(), info.ModTime().UnixNano())
			}
		}
	}
	if baseline {
		current.Tasks = next
		return nil
	}
	keys := make([]string, 0, len(next)+len(current.Tasks))
	for key := range next {
		keys = append(keys, key)
	}
	for key := range current.Tasks {
		if _, ok := next[key]; !ok {
			keys = append(keys, key)
		}
	}
	sort.Strings(keys)
	var events []Event
	for _, key := range keys {
		before, had := current.Tasks[key]
		after, has := next[key]
		if had && has && before == after {
			continue
		}
		action := "modified"
		if !had {
			action = "created"
		}
		if !has {
			action = "deleted"
		}
		e := NewEvent(bootID, "launchd_snapshot", stableKey(key, action, before, after), "scheduled_task", action, "observed", now)
		e.Target = key
		events = append(events, e)
	}
	current.Tasks = next
	return events
}

func diffMapEvents(before, after map[string]string, bootID string, now time.Time, category, added, removed, source string) []Event {
	var result []Event
	for key, value := range after {
		if _, ok := before[key]; !ok {
			e := NewEvent(bootID, source, stableKey(key, value, added), category, added, "observed", now)
			e.Target = key
			e.Detail = JSONDetail(map[string]string{"identity": value})
			result = append(result, e)
		}
	}
	for key, value := range before {
		if _, ok := after[key]; !ok {
			e := NewEvent(bootID, source, stableKey(key, value, removed), category, removed, "observed", now)
			e.Target = key
			e.Detail = JSONDetail(map[string]string{"identity": value})
			result = append(result, e)
		}
	}
	return result
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
