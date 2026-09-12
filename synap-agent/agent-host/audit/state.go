package audit

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"time"
)

type cursor struct {
	Offset int64  `json:"offset"`
	ID     string `json:"id,omitempty"`
}

type fileState struct {
	Size    int64  `json:"size"`
	ModNano int64  `json:"modNano"`
	Mode    uint32 `json:"mode"`
	Kind    string `json:"kind"`
}

type processState struct {
	PID      int32  `json:"pid"`
	PPID     int32  `json:"ppid"`
	User     string `json:"user"`
	Exe      string `json:"exe"`
	Name     string `json:"name"`
	Cmdline  string `json:"cmdline"`
	CreateMS int64  `json:"createMs"`
}

type state struct {
	Version       int                     `json:"version"`
	Initialized   bool                    `json:"initialized"`
	CleanShutdown bool                    `json:"cleanShutdown"`
	BootID        string                  `json:"bootId"`
	LastSavedAt   string                  `json:"lastSavedAt"`
	Cursors       map[string]cursor       `json:"cursors"`
	Files         map[string]fileState    `json:"files"`
	Processes     map[string]processState `json:"processes"`
	Connections   map[string]string       `json:"connections"`
	Peripherals   map[string]string       `json:"peripherals"`
	Accounts      map[string]string       `json:"accounts"`
	Tasks         map[string]string       `json:"tasks"`
}

type stateStore struct {
	dir  string
	path string
}

func newStateStore(dataDir string) (*stateStore, error) {
	dir := filepath.Join(dataDir, "audit-state")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil, err
	}
	_ = os.Chmod(dir, 0o700)
	return &stateStore{dir: dir, path: filepath.Join(dir, "state.json")}, nil
}

func emptyState() state {
	return state{Version: 1, Cursors: map[string]cursor{}, Files: map[string]fileState{},
		Processes: map[string]processState{}, Connections: map[string]string{}, Peripherals: map[string]string{},
		Accounts: map[string]string{}, Tasks: map[string]string{}}
}

func (s *stateStore) load() (state, error) {
	result := emptyState()
	data, err := os.ReadFile(s.path)
	if errors.Is(err, os.ErrNotExist) {
		return result, nil
	}
	if err != nil {
		return result, err
	}
	if err := json.Unmarshal(data, &result); err != nil {
		return emptyState(), err
	}
	if result.Cursors == nil {
		result.Cursors = map[string]cursor{}
	}
	if result.Files == nil {
		result.Files = map[string]fileState{}
	}
	if result.Processes == nil {
		result.Processes = map[string]processState{}
	}
	if result.Connections == nil {
		result.Connections = map[string]string{}
	}
	if result.Peripherals == nil {
		result.Peripherals = map[string]string{}
	}
	if result.Accounts == nil {
		result.Accounts = map[string]string{}
	}
	if result.Tasks == nil {
		result.Tasks = map[string]string{}
	}
	return result, nil
}

func (s *stateStore) save(value state) error {
	value.Version = 1
	value.LastSavedAt = time.Now().UTC().Format(time.RFC3339Nano)
	data, err := json.Marshal(value)
	if err != nil {
		return err
	}
	tmp, err := os.CreateTemp(s.dir, ".state-*")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	defer os.Remove(tmpName)
	if err := tmp.Chmod(0o600); err != nil {
		_ = tmp.Close()
		return err
	}
	if _, err := tmp.Write(data); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Sync(); err != nil {
		_ = tmp.Close()
		return err
	}
	if err := tmp.Close(); err != nil {
		return err
	}
	return os.Rename(tmpName, s.path)
}
