package storage

import (
	"errors"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/shirou/gopsutil/process"
	"agent-host/model"
)

var ErrAlreadyRunning = errors.New("probe is already running")

type ProcessLock struct{ path string }

func AcquireProcessLock(dataDir string) (*ProcessLock, error) {
	path := filepath.Join(dataDir, "probe.lock")
	for attempt := 0; attempt < 2; attempt++ {
		file, err := os.OpenFile(path, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
		if err == nil {
			_, writeErr := file.WriteString(strconv.Itoa(os.Getpid()))
			closeErr := file.Close()
			if writeErr != nil {
				_ = os.Remove(path)
				return nil, writeErr
			}
			if closeErr != nil {
				_ = os.Remove(path)
				return nil, closeErr
			}
			return &ProcessLock{path: path}, nil
		}
		if !os.IsExist(err) {
			return nil, err
		}
		data, readErr := os.ReadFile(path)
		pid, parseErr := strconv.Atoi(strings.TrimSpace(string(data)))
		alive, aliveErr := process.PidExists(int32(pid))
		if readErr == nil && parseErr == nil && aliveErr == nil && alive {
			return nil, ErrAlreadyRunning
		}
		if removeErr := os.Remove(path); removeErr != nil && !os.IsNotExist(removeErr) {
			return nil, ErrAlreadyRunning
		}
	}
	return nil, ErrAlreadyRunning
}

func (l *ProcessLock) Release() error {
	if l == nil {
		return nil
	}
	data, err := os.ReadFile(l.path)
	if err == nil && strings.TrimSpace(string(data)) != strconv.Itoa(os.Getpid()) {
		return ErrAlreadyRunning
	}
	if err := os.Remove(l.path); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func SessionActive(dataDir string, session model.Session) bool {
	data, err := os.ReadFile(filepath.Join(dataDir, "probe.lock"))
	if err != nil || strings.TrimSpace(string(data)) != strconv.Itoa(session.PID) {
		return false
	}
	alive, err := process.PidExists(int32(session.PID))
	return err == nil && alive
}
