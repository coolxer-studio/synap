//go:build darwin

package probe

import (
	"context"
	"os/exec"
	"agent-host/probe/screen"
	"strings"
	"time"
)

func (d *DeviceInfo) serial() string {
	cmd := exec.Command("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return ioregValue(string(output), "IOPlatformSerialNumber")
}

func (d *DeviceInfo) manufacturer() string {
	cmd := exec.Command("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return ioregValue(string(output), "manufacturer")
}

func (d *DeviceInfo) bootId() string {
	cmd := exec.Command("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return ioregValue(string(output), "IOPlatformUUID")
}

func (d *DeviceInfo) modelName() string {
	output, err := exec.Command("sysctl", "-n", "hw.model").Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

func (d *DeviceInfo) defaultRoute() string {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	output, err := exec.CommandContext(ctx, "route", "-n", "get", "default").Output()
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(output), "\n") {
		fields := strings.Fields(line)
		if len(fields) == 2 && fields[0] == "gateway:" {
			return fields[1]
		}
	}
	return ""
}

// ---- screen ----
// ---- macbook screen use command system_profiler SPDisplaysDataType
func (d *DeviceInfo) rotation() string {
	return screen.Rotation()
}

func (d *DeviceInfo) density() string {
	return screen.Density()
}

func (d *DeviceInfo) brightness() string {
	return screen.Brightness()
}

func (d *DeviceInfo) width() string {
	return screen.Width()
}

func (d *DeviceInfo) height() string {
	return screen.Height()
}

// ---- screen  end  ----

func (d *DeviceInfo) abi() string {
	cmd := exec.Command("uname", "-m")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

// 获取sshd 日志
func (e *EventProbe) sshd() [][]string {
	return nil
}

func ioregValue(output, key string) string {
	for _, line := range strings.Split(output, "\n") {
		if !strings.Contains(line, `"`+key+`"`) {
			continue
		}
		parts := strings.Split(line, "=")
		if len(parts) == 2 {
			return strings.Trim(strings.TrimSpace(parts[1]), `"`)
		}
	}
	return ""
}
