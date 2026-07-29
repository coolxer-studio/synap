//go:build darwin

package screen

// 该文件用于获取macbook的屏幕信息
// 使用命令 system_profiler SPDisplaysDataType 获取屏幕信息
// 使用命令 brightness -l 获取屏幕亮度
// 使用命令 system_profiler SPDisplaysDataType | grep Resolution 获取屏幕分辨率
// 使用命令 system_profiler SPDisplaysDataType | grep Resolution | awk '{print $2}' 获取屏幕宽度
// 使用命令 system_profiler SPDisplaysDataType | grep Resolution | awk '{print $4}' 获取屏幕高度

// 屏幕亮度使用第三方包 brightness（github: https://github.com/nriley/brightness） 获取
// brew install 不支持Apple Silicon, 使用社区编译的包tools/brightness.pkg

import (
	"fmt"
	"os/exec"
	"strings"
)

// Rotation returns the screen rotation.
func Rotation() string {
	// Rotation is not easily retrievable on macOS via command line.
	return ""
}

// Density returns the screen density (DPI).
func Density() string {
	cmd := exec.Command("bash", "-c", "system_profiler SPDisplaysDataType | grep Resolution")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	if strings.Contains(string(output), "Retina") {
		return "144" // Common DPI for Retina displays
	}
	return "72" // Standard DPI for non-retina displays
}

// Brightness returns the screen brightness as a percentage.
func Brightness() string {
	cmd := exec.Command("brightness", "-l")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}

	// The command `brightness -l` outputs something like:
	// display 0: main, active, awake, online, built-in, ID 0x1
	// display 0: brightness 0.500000
	// We need to parse this to get the float value.
	lines := strings.Split(string(output), "\n")
	for _, line := range lines {
		if strings.Contains(line, "brightness") {
			parts := strings.Fields(line)
			if len(parts) > 3 {
				var brightnessValue float64
				_, err = fmt.Sscanf(parts[3], "%f", &brightnessValue)
				if err == nil {
					return fmt.Sprintf("%d", int(brightnessValue*100))
				}
			}
		}
	}
	return ""
}

// Width returns the screen width in pixels.
func Width() string {
	cmd := exec.Command("bash", "-c", "system_profiler SPDisplaysDataType | grep Resolution | awk '{print $2}'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}

// Height returns the screen height in pixels.
func Height() string {
	cmd := exec.Command("bash", "-c", "system_profiler SPDisplaysDataType | grep Resolution | awk '{print $4}'")
	output, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(output))
}
