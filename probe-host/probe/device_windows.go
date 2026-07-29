//go:build windows

package probe

import (
	"os/exec"
	"probe-host/probe/screen"
	"strings"
)

func (d *DeviceInfo) serial() string {
	cmd := exec.Command("vmic", "bios get serialnumber")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	serialNumber := strings.TrimSpace(strings.Split(string(output), ":")[1])
	return serialNumber
}

func (d *DeviceInfo) manufacturer() string {
	return ""
}

func (d *DeviceInfo) bootId() string {
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
	return ""
}

func (e *EventProbe) sshd() [][]string {
	return [][]string{{"root", "2023-12-25 12:33:00"}, {"test", "2023-12-25 12:38:00"}}
}
