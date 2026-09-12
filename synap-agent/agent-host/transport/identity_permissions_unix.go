//go:build !windows

package transport

import "os"

func hardenIdentityDirectory(path string) error { return os.Chmod(path, 0o700) }

func hardenIdentityFile(path string) error { return os.Chmod(path, 0o600) }
