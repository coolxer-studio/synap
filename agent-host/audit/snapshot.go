package audit

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	gnet "github.com/shirou/gopsutil/net"
	"github.com/shirou/gopsutil/process"

	"agent-host/model"
)

const maxSnapshotItems = 4096

func (m *Monitor) collectFiles(now time.Time, baseline bool, events *[]Event) error {
	current := map[string]fileState{}
	targets := make([]fileTarget, 0)
	critical := m.policy.CriticalFiles
	if m.policy.CriticalFilesMode != "replace" {
		critical = append(builtinCriticalFiles(), critical...)
	}
	for _, path := range critical {
		if path != "" {
			targets = append(targets, fileTarget{path: path, kind: "config_file"})
		}
	}
	for _, directory := range m.policy.BusinessDirectories {
		if directory.Path != "" {
			targets = append(targets, fileTarget{path: directory.Path, kind: "business_file", directory: &directory})
		}
	}
	seen := map[string]struct{}{}
	for _, target := range targets {
		if len(current) >= maxSnapshotItems {
			break
		}
		if err := scanTarget(target, current, seen); err != nil && !errors.Is(err, os.ErrNotExist) {
			return err
		}
	}
	if !baseline {
		paths := make([]string, 0, len(current)+len(m.state.Files))
		for path := range current {
			paths = append(paths, path)
		}
		for path := range m.state.Files {
			if _, ok := current[path]; !ok {
				paths = append(paths, path)
			}
		}
		sort.Strings(paths)
		for _, path := range paths {
			before, existed := m.state.Files[path]
			after, exists := current[path]
			if existed && exists && before.Size == after.Size && before.ModNano == after.ModNano && before.Mode == after.Mode {
				continue
			}
			action := "modified"
			kind := after.Kind
			if !existed {
				action = "created"
			}
			if !exists {
				action, kind = "deleted", before.Kind
			}
			e := NewEvent(m.bootID, "file_snapshot", stableKey(path, action, before.ModNano, after.ModNano, after.Size), kind, action, "observed", now)
			e.Actor, e.Target = "unknown", path
			detail := map[string]any{"path": path, "size": after.Size, "mode": fmt.Sprintf("%#o", after.Mode), "modTime": time.Unix(0, after.ModNano).UTC().Format(time.RFC3339Nano)}
			if action != "deleted" {
				algorithm, digest, reason := digestFile(path, after.Size, m.policy.DigestMaxBytes)
				e.DigestAlgorithm, e.Digest = algorithm, digest
				if reason != "" {
					detail["digestSkipped"] = reason
				}
			}
			e.Detail = JSONDetail(detail)
			*events = append(*events, e)
		}
	}
	m.state.Files = current
	return nil
}

type fileTarget struct {
	path, kind string
	directory  *model.AuditDirectory
}

func scanTarget(target fileTarget, result map[string]fileState, seen map[string]struct{}) error {
	info, err := os.Lstat(target.path)
	if err != nil {
		return err
	}
	if info.Mode()&os.ModeSymlink != 0 {
		if target.directory != nil && !target.directory.FollowSymlinks {
			return nil
		}
		info, err = os.Stat(target.path)
		if err != nil {
			return err
		}
	}
	if !info.IsDir() {
		result[target.path] = fileState{Size: info.Size(), ModNano: info.ModTime().UnixNano(), Mode: uint32(info.Mode()), Kind: target.kind}
		return nil
	}
	if target.directory == nil {
		return nil
	}
	root := filepath.Clean(target.path)
	visited := map[string]struct{}{}
	var walk func(string, string, int) error
	walk = func(path, relative string, depth int) error {
		if len(result) >= maxSnapshotItems || depth > 64 {
			return nil
		}
		if relative != "" && excludedPath(relative, target.directory.Exclude) {
			return nil
		}
		info, err := os.Lstat(path)
		if err != nil {
			return nil
		}
		if info.Mode()&os.ModeSymlink != 0 {
			if !target.directory.FollowSymlinks {
				return nil
			}
			info, err = os.Stat(path)
			if err != nil {
				return nil
			}
		}
		if info.IsDir() {
			if relative != "" && !target.directory.Recursive {
				return nil
			}
			realPath, err := filepath.EvalSymlinks(path)
			if err == nil {
				if _, ok := visited[realPath]; ok {
					return nil
				}
				visited[realPath] = struct{}{}
			}
			entries, err := os.ReadDir(path)
			if err != nil {
				return nil
			}
			for _, entry := range entries {
				childRel := entry.Name()
				if relative != "" {
					childRel = filepath.Join(relative, entry.Name())
				}
				if err := walk(filepath.Join(path, entry.Name()), childRel, depth+1); err != nil {
					return err
				}
			}
			return nil
		}
		if _, ok := seen[path]; ok {
			return nil
		}
		seen[path] = struct{}{}
		result[path] = fileState{Size: info.Size(), ModNano: info.ModTime().UnixNano(), Mode: uint32(info.Mode()), Kind: target.kind}
		return nil
	}
	return walk(root, "", 0)
}

func excludedPath(path string, patterns []string) bool {
	for _, pattern := range patterns {
		if matched, _ := filepath.Match(pattern, path); matched {
			return true
		}
		if matched, _ := filepath.Match(pattern, filepath.Base(path)); matched {
			return true
		}
	}
	return false
}

func digestFile(path string, size, limit int64) (string, string, string) {
	if size > limit {
		return "", "", "size_limit"
	}
	file, err := os.Open(path)
	if err != nil {
		return "", "", "unreadable"
	}
	defer file.Close()
	hash := sha256.New()
	written, err := io.Copy(hash, io.LimitReader(file, limit+1))
	if err != nil {
		return "", "", "read_error"
	}
	if written > limit {
		return "", "", "size_limit"
	}
	return "SHA-256", hex.EncodeToString(hash.Sum(nil)), ""
}

func (m *Monitor) collectProcesses(now time.Time, baseline bool, events *[]Event) error {
	items, err := process.Processes()
	if err != nil {
		return err
	}
	current := map[string]processState{}
	for _, item := range items {
		if len(current) >= maxSnapshotItems {
			break
		}
		created, _ := item.CreateTime()
		name, _ := item.Name()
		exe, _ := item.Exe()
		cmdline, _ := item.Cmdline()
		if m.processExcluded(name, exe, cmdline) {
			continue
		}
		ppid, _ := item.Ppid()
		username, _ := item.Username()
		value := processState{PID: item.Pid, PPID: ppid, User: username, Exe: exe, Name: name, Cmdline: redact(cmdline), CreateMS: created}
		key := fmt.Sprintf("%d:%d", item.Pid, created)
		current[key] = value
		if !baseline {
			if _, ok := m.state.Processes[key]; !ok {
				*events = append(*events, m.processEvent(value, "started", now))
			}
		}
	}
	if !baseline {
		for key, value := range m.state.Processes {
			if _, ok := current[key]; !ok {
				*events = append(*events, m.processEvent(value, "terminated", now))
			}
		}
		if m.lastProc.IsZero() || now.Sub(m.lastProc) >= time.Duration(m.policy.ProcessSnapshotSeconds)*time.Second {
			for _, item := range items {
				created, _ := item.CreateTime()
				value, ok := current[fmt.Sprintf("%d:%d", item.Pid, created)]
				if !ok {
					continue
				}
				e := m.processEvent(value, "running", now)
				status, _ := item.Status()
				cpu, _ := item.CPUPercent()
				memory, _ := item.MemoryInfo()
				e.Detail = JSONDetail(map[string]any{"user": value.User, "parentPid": value.PPID, "executable": value.Exe, "status": status, "cpuPercent": cpu, "rss": memoryValue(memory), "commandLine": value.Cmdline})
				*events = append(*events, e)
			}
			m.lastProc = now
		}
	}
	m.state.Processes = current
	return nil
}

func memoryValue(value *process.MemoryInfoStat) uint64 {
	if value == nil {
		return 0
	}
	return value.RSS
}

func (m *Monitor) processEvent(value processState, action string, now time.Time) Event {
	e := NewEvent(m.bootID, "process_snapshot", stableKey(value.PID, value.CreateMS, action), "process", action, "observed", now)
	e.Actor, e.Subject, e.Target, e.PID, e.Process = value.User, value.User, value.Exe, strconv.Itoa(int(value.PID)), value.Name
	e.Detail = JSONDetail(map[string]any{"user": value.User, "parentPid": value.PPID, "executable": value.Exe, "commandLine": value.Cmdline, "createTime": value.CreateMS})
	return e
}

func (m *Monitor) processExcluded(values ...string) bool {
	joined := strings.Join(values, " ")
	for _, expression := range m.excluded {
		if expression.MatchString(joined) {
			return true
		}
	}
	return false
}

func (m *Monitor) collectConnections(now time.Time, baseline bool, events *[]Event) error {
	connections, err := gnet.Connections("all")
	if err != nil {
		return err
	}
	current := map[string]string{}
	for _, connection := range connections {
		if m.connectionProcessExcluded(connection.Pid) {
			continue
		}
		protocol := connectionProtocol(connection.Type)
		local := endpoint(connection.Laddr.IP, connection.Laddr.Port)
		remote := endpoint(connection.Raddr.IP, connection.Raddr.Port)
		if m.connectionExcluded(connection.Laddr.IP, connection.Laddr.Port, connection.Raddr.IP, connection.Raddr.Port) {
			continue
		}
		key := stableKey(protocol, local, remote, connection.Pid, connection.Status)
		current[key] = strings.Join([]string{protocol, local, remote, strconv.Itoa(int(connection.Pid)), connection.Status}, "\x00")
		if !baseline {
			if _, ok := m.state.Connections[key]; !ok {
				*events = append(*events, connectionEvent(m.bootID, key, current[key], "established", now))
			}
		}
	}
	if !baseline {
		for key, value := range m.state.Connections {
			if _, ok := current[key]; !ok {
				*events = append(*events, connectionEvent(m.bootID, key, value, "terminated", now))
			}
		}
	}
	m.state.Connections = current
	return nil
}

func (m *Monitor) connectionProcessExcluded(pid int32) bool {
	for _, value := range m.state.Processes {
		if value.PID == pid {
			return m.processExcluded(value.Name, value.Exe, value.Cmdline)
		}
	}
	return false
}

func connectionEvent(bootID, key, value, action string, now time.Time) Event {
	parts := strings.Split(value, "\x00")
	e := NewEvent(bootID, "connection_snapshot", stableKey(key, action), "network", action, "observed", now)
	if len(parts) == 5 {
		e.Protocol, e.LocalEndpoint, e.RemoteEndpoint, e.PID = parts[0], parts[1], parts[2], parts[3]
		e.Detail = JSONDetail(map[string]string{"state": parts[4]})
	}
	return e
}

func connectionProtocol(value uint32) string {
	if value == 2 {
		return "udp"
	}
	return "tcp"
}
func endpoint(ip string, port uint32) string {
	if ip == "" && port == 0 {
		return ""
	}
	return net.JoinHostPort(ip, strconv.Itoa(int(port)))
}

func (m *Monitor) connectionExcluded(localIP string, localPort uint32, remoteIP string, remotePort uint32) bool {
	for _, port := range m.policy.ExcludePorts {
		if uint32(port) == localPort || uint32(port) == remotePort {
			return true
		}
	}
	for _, expression := range m.policy.ExcludeCIDRs {
		_, network, err := net.ParseCIDR(expression)
		if err != nil {
			continue
		}
		if (net.ParseIP(localIP) != nil && network.Contains(net.ParseIP(localIP))) || (net.ParseIP(remoteIP) != nil && network.Contains(net.ParseIP(remoteIP))) {
			return true
		}
	}
	return false
}
