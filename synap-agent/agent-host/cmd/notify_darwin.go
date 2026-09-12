//go:build darwin

package cmd

import (
	"context"
	"os"
	"os/exec"
	"strconv"
	"time"
)

func desktopNotify(title, message string) {
	if os.Getenv("SSH_TTY") != "" {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	script := "display notification " + strconv.Quote(message) + " with title " + strconv.Quote(title)
	_ = exec.CommandContext(ctx, "osascript", "-e", script).Run()
}
