//go:build linux

package screen

import (
	"context"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
)

func xrandr() string {
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	output, err := exec.CommandContext(ctx, "xrandr", "--current").Output()
	if err != nil {
		return ""
	}
	return string(output)
}

func Rotation() string {
	for _, line := range strings.Split(xrandr(), "\n") {
		if strings.Contains(line, " connected") {
			for _, value := range []string{"normal", "left", "right", "inverted"} {
				if strings.Contains(line, " "+value+" ") {
					return value
				}
			}
		}
	}
	return ""
}

func Density() string { return "" }

func Brightness() string {
	devices, _ := filepath.Glob("/sys/class/backlight/*")
	if len(devices) == 0 {
		return ""
	}
	current, err1 := os.ReadFile(filepath.Join(devices[0], "brightness"))
	maximum, err2 := os.ReadFile(filepath.Join(devices[0], "max_brightness"))
	if err1 != nil || err2 != nil {
		return ""
	}
	c, err1 := strconv.Atoi(strings.TrimSpace(string(current)))
	m, err2 := strconv.Atoi(strings.TrimSpace(string(maximum)))
	if err1 != nil || err2 != nil || m <= 0 {
		return ""
	}
	return strconv.Itoa(c * 100 / m)
}

func resolution() (string, string) {
	expression := regexp.MustCompile(`(?m)^\s*(\d+)x(\d+)\s+[^\n]*\*`)
	matches := expression.FindStringSubmatch(xrandr())
	if len(matches) != 3 {
		return "", ""
	}
	return matches[1], matches[2]
}

func Width() string  { width, _ := resolution(); return width }
func Height() string { _, height := resolution(); return height }
