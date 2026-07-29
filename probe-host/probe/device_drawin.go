//go:build darwin

package probe

import (
	"os/exec"
	"probe-host/probe/screen"
	"strings"
)

func (d *DeviceInfo) serial() string {
	cmd := exec.Command("ioreg", "-l", " | grep 'IOPlatformSerialNumber'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	serialNumber := strings.TrimSpace(strings.Split(string(output), ":")[1])
	return serialNumber
}

func (d *DeviceInfo) manufacturer() string {
	cmd := exec.Command("bash", "-c", "ioreg -rd1 -c IOPlatformExpertDevice | grep manufacturer | awk -F '\"' '{print $2}'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

func (d *DeviceInfo) bootId() string {
	cmd := exec.Command("bash", "-c", "ioreg -rd1 -c IOPlatformExpertDevice | grep IOPlatformUUID | awk -F '\"' '{print $2}'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
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
	return [][]string{{"root", "2023-12-25 12:33:00"}, {"test", "2023-12-25 12:38:00"}}
}
