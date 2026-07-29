//go:build linux

package screen

import (
	"os/exec"
	"strings"
)

// Rotation returns the screen rotation.
func Rotation() string {
	cmd := exec.Command("bash", "-c", "xrandr 2>/dev/null | grep ' connected' | grep -oP '\\b(normal|left|right|inverted)\\b' | head -n 1")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

// Density returns the screen density (DPI).
func Density() string {
	cmd := exec.Command("bash", "-c", "xrdb -query 2>/dev/null | grep dpi | awk '{print $2}'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

// Brightness returns the screen brightness as a percentage.
func Brightness() string {
	// from /sys/class/backlight
	cmdStr := "device=$(ls /sys/class/backlight/ | head -n 1); if [ -n \"$device\" ]; then current=$(cat /sys/class/backlight/$device/brightness); max=$(cat /sys/class/backlight/$device/max_brightness); if [ \"$max\" -ne 0 ]; then echo $((current * 100 / max)); fi; fi"
	cmd := exec.Command("bash", "-c", cmdStr)
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

// Width returns the screen width in pixels.
func Width() string {
	cmd := exec.Command("bash", "-c", "xrandr 2>/dev/null | grep '*' | grep -oP '^\\d+x\\d+' | head -n1 | cut -d'x' -f1")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

// Height returns the screen height in pixels.
func Height() string {
	cmd := exec.Command("bash", "-c", "xrandr 2>/dev/null | grep '*' | grep -oP '^\\d+x\\d+' | head -n1 | cut -d'x' -f2")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}
