//go:build linux

package cmd

import (
	"context"
	"os"
	"os/exec"
	"time"
)

func desktopNotify(title, message string) {
	if os.Getenv("DISPLAY") == "" && os.Getenv("WAYLAND_DISPLAY") == "" {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	_ = exec.CommandContext(ctx, "notify-send", "--", title, message).Run()
}
