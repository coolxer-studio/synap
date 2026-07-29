package screen

import (
	"fmt"
	"os/exec"
	"strings"
	"testing"
)

func TestScreen(t *testing.T) {

	t.Logf("brightness: %s", brightness())
}

func brightness() string {
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
			fmt.Println(line)
			parts := strings.Fields(line)
			fmt.Println(parts)
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
