//go:build linux

package uuid

import (
	"os/exec"
	"strings"
)

type DeviceInfo struct{}

func (d *DeviceInfo) Serial() string {
	cmd := exec.Command("bash", "-c", "sudo dmidecode -t 1 | grep 'Serial Number'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	serialNumber := strings.TrimSpace(strings.Split(string(output), ":")[1])
	return serialNumber
}

func (d *DeviceInfo) Manufacturer() string {
	cmd := exec.Command("bash", "-c", "sudo dmidecode -t 1 | grep 'Manufacturer'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	serialNumber := strings.TrimSpace(strings.Split(string(output), ":")[1])
	return serialNumber
}

func (d *DeviceInfo) BootId() string {
	cmd := exec.Command("bash", "-c", "sudo dmidecode -t 1 | grep 'UUID'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	serialNumber := strings.TrimSpace(strings.Split(string(output), ":")[1])
	return serialNumber
}
