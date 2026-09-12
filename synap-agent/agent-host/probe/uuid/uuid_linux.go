//go:build linux

package uuid

import (
	"os"
	"strings"
)

type DeviceInfo struct{}

func read(path string) string {
	value, err := os.ReadFile(path)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(value))
}

func (d *DeviceInfo) Serial() string       { return read("/sys/class/dmi/id/product_serial") }
func (d *DeviceInfo) Manufacturer() string { return read("/sys/class/dmi/id/sys_vendor") }
func (d *DeviceInfo) BootId() string       { return read("/proc/sys/kernel/random/boot_id") }
