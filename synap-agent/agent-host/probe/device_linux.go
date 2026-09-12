//go:build linux

package probe

import (
	"bufio"
	"encoding/hex"
	"fmt"
	"os"
	"agent-host/probe/screen"
	"agent-host/probe/uuid"
	"regexp"
	"runtime"
	"strings"
)

func (d *DeviceInfo) serial() string {
	uuidDevice := &uuid.DeviceInfo{}
	return uuidDevice.Serial()
}

func (d *DeviceInfo) manufacturer() string {
	uuidDevice := &uuid.DeviceInfo{}
	return uuidDevice.Manufacturer()
}

func (d *DeviceInfo) bootId() string {
	uuidDevice := &uuid.DeviceInfo{}
	return uuidDevice.BootId()
}

func (d *DeviceInfo) modelName() string {
	value, err := os.ReadFile("/sys/class/dmi/id/product_name")
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(value))
}

func (d *DeviceInfo) defaultRoute() string {
	file, err := os.Open("/proc/net/route")
	if err != nil {
		return ""
	}
	defer file.Close()
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		fields := strings.Fields(scanner.Text())
		if len(fields) < 3 || fields[1] != "00000000" {
			continue
		}
		value, err := hex.DecodeString(fields[2])
		if err == nil && len(value) == 4 {
			return fmt.Sprintf("%d.%d.%d.%d", value[3], value[2], value[1], value[0])
		}
	}
	return ""
}

// ---- screen ----
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

func (d *DeviceInfo) abi() string {
	return runtime.GOARCH
}

// sshd 分析的secure日志, 目前只分析accepted的日志
func (e *EventProbe) sshd() [][]string {
	for _, path := range []string{"/var/log/auth.log", "/var/log/secure"} {
		file, err := os.Open(path)
		if err != nil {
			continue
		}
		defer file.Close()
		scanner := bufio.NewScanner(file)
		expression := regexp.MustCompile(`Accepted (?:password|publickey) for ([^ ]+) from ([^ ]+)`)
		var result [][]string
		for scanner.Scan() {
			matches := expression.FindStringSubmatch(scanner.Text())
			if len(matches) == 3 {
				result = append(result, []string{matches[1], matches[2]})
			}
			if len(result) >= 256 {
				break
			}
		}
		return result
	}
	return nil
}
