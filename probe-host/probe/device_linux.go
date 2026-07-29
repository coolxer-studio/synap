//go:build linux

package probe

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"probe-host/probe/screen"
	"probe-host/probe/uuid"
	"regexp"
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
	cmd := exec.Command("uname", "-m")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	return string(output)
}

// sshd 分析的secure日志, 目前只分析accepted的日志
func (e *EventProbe) sshd() [][]string {
	file, err := os.Open("/var/log/secure")
	if err != nil {
		return nil
	}
	defer file.Close()

	// secure log demo
	// Jan 21 10:13:18 iZ2ze72apzjtwzhwerph1iZ sshd[112283]: Accepted password for root from 120.244.24.85 port 1691 ssh2
	scanner := bufio.NewScanner(file)
	regex := regexp.MustCompile(`^.* [\w]+ sshd\[[\d]+\]: Accepted password for [\w]+ from [\d\.]+ port [\d]+ ssh2$`)

	var res [][]string
	for scanner.Scan() {
		line := scanner.Text()
		// 目前只匹配指定的accepted log
		if regex.Match([]byte(line)) {
			segs := strings.Split(line, " ")
			// segs 9 = user
			res = append(res, []string{segs[9], segs[0] + " " + segs[2] + " " + segs[3]})
		}
	}
	if err := scanner.Err(); err != nil {
		fmt.Println("Error:", err)
	}
	return res
}
