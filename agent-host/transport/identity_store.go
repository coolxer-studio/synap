package transport

import (
	"os"
	"path/filepath"

	sc "github.com/coolxer/secure-communication-go/v2"
)

// protectedIdentityStore ensures the installation private key is created only
// after the containing directory has been restricted to the current account.
type protectedIdentityStore struct{ path string }

func (s protectedIdentityStore) LoadOrCreate(appID string) (sc.InstallationIdentity, error) {
	directory := filepath.Dir(s.path)
	if err := os.MkdirAll(directory, 0o700); err != nil {
		return nil, err
	}
	if err := hardenIdentityDirectory(directory); err != nil {
		return nil, err
	}
	identity, err := (sc.FileIdentityStore{Path: s.path}).LoadOrCreate(appID)
	if err != nil {
		return nil, err
	}
	if err := hardenIdentityFile(s.path); err != nil {
		return nil, err
	}
	return identity, nil
}
