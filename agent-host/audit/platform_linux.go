//go:build linux

package audit

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"syscall"
	"time"
)

func platformName() string { return "linux" }

func platformBootID() string {
	data, _ := os.ReadFile("/proc/sys/kernel/random/boot_id")
	return strings.TrimSpace(string(data))
}

func builtinCriticalFiles() []string {
	return []string{"/etc/passwd", "/etc/group", "/etc/shadow", "/etc/sudoers", "/etc/ssh/sshd_config", "/etc/hosts", "/etc/resolv.conf", "/etc/crontab"}
}

func platformEvents(current *state, bootID string, now time.Time, baseline bool) ([]Event, error) {
	var events []Event
	var available bool
	for _, path := range []string{"/var/log/audit/audit.log", "/var/log/auth.log", "/var/log/secure", "/var/log/syslog", "/var/log/messages"} {
		if _, err := os.Stat(path); err != nil {
			continue
		}
		lines, err := incrementalLines(path, current, baseline)
		if err != nil {
			continue
		}
		available = true
		for index, line := range lines {
			if event, ok := parseLinuxAuditLine(line, bootID, path, now, index); ok {
				events = append(events, event)
			}
		}
	}
	events = append(events, diffLinuxUSB(current, bootID, now, baseline)...)
	events = append(events, diffLinuxTasks(current, bootID, now, baseline)...)
	events = append(events, diffLinuxAccounts(current, bootID, now, baseline)...)
	journalEvents, journalAvailable := readJournalEvents(current, bootID, now, baseline)
	events = append(events, journalEvents...)
	available = available || journalAvailable
	if !available && !baseline {
		if _, emitted := current.Cursors["capability:linux-audit"]; emitted {
			return events, nil
		}
		current.Cursors["capability:linux-audit"] = cursor{ID: "unavailable"}
		e := NewEvent(bootID, "linux_audit", "missing:auditd-auth", "capability", "degraded", "unavailable", now)
		e.Detail = JSONDetail(map[string]string{"platform": "linux", "missingSource": "auditd,journald/auth logs", "permission": "root or audit group required", "affectedCategories": "session,account,process,network"})
		events = append(events, e)
	}
	return events, nil
}

func readJournalEvents(current *state, bootID string, now time.Time, baseline bool) ([]Event, bool) {
	if _, err := os.Stat("/run/systemd/journal"); err != nil {
		return nil, false
	}
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	position := current.Cursors["journalctl"]
	if baseline || position.ID == "" {
		output, err := exec.CommandContext(ctx, "/usr/bin/journalctl", "--no-pager", "-n", "0", "--show-cursor").Output()
		if err != nil {
			return nil, false
		}
		for _, line := range strings.Split(string(output), "\n") {
			if strings.HasPrefix(line, "-- cursor: ") {
				position.ID = strings.TrimPrefix(line, "-- cursor: ")
			}
		}
		current.Cursors["journalctl"] = position
		return nil, position.ID != ""
	}
	args := []string{"--no-pager", "--output=json", "-n", "1000", "-t", "sshd", "-t", "systemd-logind", "-t", "cron", "-t", "CRON", "-t", "systemd-udevd"}
	args = append(args, "--after-cursor="+position.ID)
	output, err := exec.CommandContext(ctx, "/usr/bin/journalctl", args...).Output()
	if err != nil && len(output) == 0 {
		return nil, false
	}
	var events []Event
	scanner := bufio.NewScanner(strings.NewReader(string(output)))
	scanner.Buffer(make([]byte, 64<<10), 1<<20)
	for scanner.Scan() {
		var record map[string]any
		if json.Unmarshal(scanner.Bytes(), &record) != nil {
			continue
		}
		if value, ok := record["__CURSOR"].(string); ok {
			position.ID = value
		}
		message, _ := record["MESSAGE"].(string)
		identifier, _ := record["SYSLOG_IDENTIFIER"].(string)
		rawID := position.ID
		if event, ok := parseLinuxAuditLine(message, bootID, "journald:"+identifier, now, 0); ok {
			event.EventID = stableKey(bootID, "journald", rawID)
			events = append(events, event)
			continue
		}
		lower := strings.ToLower(message)
		if identifier == "systemd-udevd" && (strings.Contains(lower, "usb") || strings.Contains(lower, "device")) {
			action := "attached"
			if strings.Contains(lower, "remove") {
				action = "detached"
			}
			e := NewEvent(bootID, "journald:udev", rawID, "peripheral", action, "observed", now)
			e.Detail = JSONDetail(map[string]string{"record": message})
			events = append(events, e)
		}
	}
	current.Cursors["journalctl"] = position
	return events, true
}

func incrementalLines(path string, current *state, baseline bool) ([]string, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	info, err := file.Stat()
	if err != nil {
		return nil, err
	}
	id := ""
	if stat, ok := info.Sys().(*syscall.Stat_t); ok {
		id = fmt.Sprintf("%d:%d", stat.Dev, stat.Ino)
	}
	position := current.Cursors[path]
	if baseline {
		position.Offset = info.Size()
	}
	if position.ID != "" && id != "" && position.ID != id {
		position.Offset = 0
	}
	if position.Offset > info.Size() {
		position.Offset = 0
	}
	if _, err := file.Seek(position.Offset, io.SeekStart); err != nil {
		return nil, err
	}
	var lines []string
	scanner := bufio.NewScanner(file)
	buffer := make([]byte, 64<<10)
	scanner.Buffer(buffer, 1<<20)
	for scanner.Scan() {
		lines = append(lines, scanner.Text())
	}
	offset, _ := file.Seek(0, io.SeekCurrent)
	current.Cursors[path] = cursor{Offset: offset, ID: id}
	return lines, scanner.Err()
}

var (
	loginAccepted = regexp.MustCompile(`(?i)Accepted (?:password|publickey) for (\S+) from (\S+)`)
	loginFailed   = regexp.MustCompile(`(?i)Failed (?:password|publickey) for (?:invalid user )?(\S+) from (\S+)`)
	sessionClosed = regexp.MustCompile(`(?i)(?:session closed for user|Disconnected from user)\s+(\S+)`)
	auditID       = regexp.MustCompile(`msg=audit\(([^)]+)\)`)
	auditAccount  = regexp.MustCompile(`(?i)type=(ADD_USER|DEL_USER|USER_MGMT|GRP_MGMT|ROLE_ASSIGN|USER_CHAUTHTOK)`)
)

func parseLinuxAuditLine(line, bootID, source string, now time.Time, index int) (Event, bool) {
	rawID := fmt.Sprintf("%s:%d:%s", source, index, stableKey(line))
	if match := auditID.FindStringSubmatch(line); len(match) == 2 {
		rawID = match[1]
	}
	if match := loginAccepted.FindStringSubmatch(line); len(match) == 3 {
		e := NewEvent(bootID, source, rawID, "session", "login", "success", now)
		e.Actor, e.Subject, e.RemoteEndpoint = match[1], match[1], match[2]
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	if match := loginFailed.FindStringSubmatch(line); len(match) == 3 {
		e := NewEvent(bootID, source, rawID, "session", "login", "failure", now)
		e.Actor, e.Subject, e.RemoteEndpoint = match[1], match[1], match[2]
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	if match := sessionClosed.FindStringSubmatch(line); len(match) == 2 {
		e := NewEvent(bootID, source, rawID, "session", "logout", "success", now)
		e.Actor, e.Subject = match[1], match[1]
		return e, true
	}
	if match := auditAccount.FindStringSubmatch(line); len(match) == 2 {
		action := map[string]string{"ADD_USER": "created", "DEL_USER": "deleted", "USER_MGMT": "modified", "GRP_MGMT": "permission_changed", "ROLE_ASSIGN": "permission_changed", "USER_CHAUTHTOK": "permission_changed"}[strings.ToUpper(match[1])]
		e := NewEvent(bootID, source, rawID, "account", action, auditResult(line), now)
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	if strings.Contains(line, "type=CONFIG_CHANGE") {
		e := NewEvent(bootID, source, rawID, "capability", "policy_changed", auditResult(line), now)
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	if strings.Contains(line, "type=EXECVE") {
		e := NewEvent(bootID, source, rawID, "process", "started", auditResult(line), now)
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	if strings.Contains(line, "synap_network") && (strings.Contains(line, "type=SYSCALL") || strings.Contains(line, "type=SOCKADDR")) {
		e := NewEvent(bootID, source, rawID, "network", "established", auditResult(line), now)
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	if strings.Contains(line, "type=PATH") && strings.Contains(line, "name=") {
		action := "modified"
		if strings.Contains(line, "nametype=CREATE") {
			action = "created"
		}
		if strings.Contains(line, "nametype=DELETE") {
			action = "deleted"
		}
		e := NewEvent(bootID, source, rawID, "config_file", action, auditResult(line), now)
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	if strings.Contains(line, " CRON[") || strings.Contains(line, "systemd") && strings.Contains(line, ".timer") {
		e := NewEvent(bootID, source, rawID, "scheduled_task", "executed", "success", now)
		e.Detail = JSONDetail(map[string]string{"record": redact(line)})
		return e, true
	}
	return Event{}, false
}

func auditResult(line string) string {
	if strings.Contains(line, "res=failed") || strings.Contains(line, "success=no") {
		return "failure"
	}
	return "success"
}

func diffLinuxUSB(current *state, bootID string, now time.Time, baseline bool) []Event {
	next := map[string]string{}
	entries, _ := filepath.Glob("/sys/bus/usb/devices/*")
	for _, path := range entries {
		vendor, _ := os.ReadFile(filepath.Join(path, "idVendor"))
		product, _ := os.ReadFile(filepath.Join(path, "idProduct"))
		if len(vendor) == 0 {
			continue
		}
		key := filepath.Base(path)
		next[key] = strings.TrimSpace(string(vendor)) + ":" + strings.TrimSpace(string(product))
	}
	if baseline {
		current.Peripherals = next
		return nil
	}
	events := diffMapEvents(current.Peripherals, next, bootID, now, "peripheral", "attached", "detached", "sysfs_usb")
	current.Peripherals = next
	return events
}

func diffLinuxTasks(current *state, bootID string, now time.Time, baseline bool) []Event {
	next := map[string]string{}
	for _, pattern := range []string{"/etc/cron.d/*", "/var/spool/cron/*", "/var/spool/cron/crontabs/*", "/etc/systemd/system/*.timer", "/usr/lib/systemd/system/*.timer"} {
		paths, _ := filepath.Glob(pattern)
		for _, path := range paths {
			if info, err := os.Stat(path); err == nil {
				next[path] = fmt.Sprintf("%d:%d", info.Size(), info.ModTime().UnixNano())
			}
		}
	}
	if baseline {
		current.Tasks = next
		return nil
	}
	var events []Event
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
		e := NewEvent(bootID, "task_snapshot", stableKey(key, action, before, after), "scheduled_task", action, "observed", now)
		e.Target = key
		events = append(events, e)
	}
	current.Tasks = next
	return events
}

func diffLinuxAccounts(current *state, bootID string, now time.Time, baseline bool) []Event {
	next := map[string]string{}
	if data, err := os.ReadFile("/etc/passwd"); err == nil {
		for _, line := range strings.Split(string(data), "\n") {
			fields := strings.Split(line, ":")
			if len(fields) >= 4 {
				next["user:"+fields[0]] = fields[2] + ":" + fields[3] + ":" + fields[6]
			}
		}
	}
	if data, err := os.ReadFile("/etc/group"); err == nil {
		for _, line := range strings.Split(string(data), "\n") {
			fields := strings.Split(line, ":")
			if len(fields) >= 4 {
				next["group:"+fields[0]] = fields[2] + ":" + fields[3]
			}
		}
	}
	if baseline {
		current.Accounts = next
		return nil
	}
	var events []Event
	for key, value := range next {
		before, ok := current.Accounts[key]
		if !ok {
			e := NewEvent(bootID, "account_snapshot", stableKey(key, value, "created"), "account", "created", "observed", now)
			e.Subject = key
			events = append(events, e)
		} else if before != value {
			e := NewEvent(bootID, "account_snapshot", stableKey(key, before, value), "account", "permission_changed", "observed", now)
			e.Subject = key
			events = append(events, e)
		}
	}
	for key, value := range current.Accounts {
		if _, ok := next[key]; !ok {
			e := NewEvent(bootID, "account_snapshot", stableKey(key, value, "deleted"), "account", "deleted", "observed", now)
			e.Subject = key
			events = append(events, e)
		}
	}
	current.Accounts = next
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
