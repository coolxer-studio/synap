//go:build windows

package cmd

import (
	"context"
	"os"
	"os/exec"
	"time"
)

func desktopNotify(title, message string) {
	if os.Getenv("SESSIONNAME") == "" || os.Getenv("SESSIONNAME") == "Services" {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	_ = exec.CommandContext(ctx, "msg.exe", "*", title+": "+message).Run()
}
