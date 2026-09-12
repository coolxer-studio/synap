//go:build darwin

package screen

import (
	"context"
	"os/exec"
	"regexp"
	"strings"
	"time"
)

func displayProfile() string {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	output, err := exec.CommandContext(ctx, "system_profiler", "SPDisplaysDataType").Output()
	if err != nil {
		return ""
	}
	return string(output)
}

func Rotation() string { return "" }
func Density() string {
	if strings.Contains(displayProfile(), "Retina") {
		return "144"
	}
	if displayProfile() != "" {
		return "72"
	}
	return ""
}
func Brightness() string { return "" }

func resolution() (string, string) {
	expression := regexp.MustCompile(`Resolution:\s+(\d+)\s+x\s+(\d+)`)
	matches := expression.FindStringSubmatch(displayProfile())
	if len(matches) != 3 {
		return "", ""
	}
	return matches[1], matches[2]
}
func Width() string  { width, _ := resolution(); return width }
func Height() string { _, height := resolution(); return height }
