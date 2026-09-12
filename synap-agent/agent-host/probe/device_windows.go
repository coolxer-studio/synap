//go:build windows

package probe

import (
	"os/exec"
	"agent-host/probe/screen"
	"runtime"
	"strings"
)

func (d *DeviceInfo) serial() string {
	return powershellValue("(Get-CimInstance Win32_BIOS).SerialNumber")
}

func (d *DeviceInfo) manufacturer() string {
	return powershellValue("(Get-CimInstance Win32_ComputerSystem).Manufacturer")
}

func (d *DeviceInfo) bootId() string {
	return powershellValue("(Get-CimInstance Win32_OperatingSystem).LastBootUpTime.ToFileTimeUtc()")
}

func (d *DeviceInfo) modelName() string {
	return powershellValue("(Get-CimInstance Win32_ComputerSystem).Model")
}

func (d *DeviceInfo) defaultRoute() string {
	output, err := exec.Command("route.exe", "PRINT", "-4", "0.0.0.0").Output()
	if err != nil {
		return ""
	}
	for _, line := range strings.Split(string(output), "\n") {
		fields := strings.Fields(line)
		if len(fields) >= 3 && fields[0] == "0.0.0.0" && fields[1] == "0.0.0.0" {
			return fields[2]
		}
	}
	return ""
}

// screen info begin --------
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

// screen info end --------

func (d *DeviceInfo) abi() string {
	return runtime.GOARCH
}

func (e *EventProbe) sshd() [][]string {
	return nil
}

func powershellValue(script string) string {
	output, err := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}
