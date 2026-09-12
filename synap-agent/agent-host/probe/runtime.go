package probe

import (
	"context"
	"os"
	"os/exec"
	"regexp"
	"strings"
	"time"

	"github.com/shirou/gopsutil/process"
)

var runtimeProbe = &RuntimeProbe{}

type RuntimeProbe struct{}

func (r *RuntimeProbe) env(names []string) []string {
	result := make([]string, len(names))
	for index, name := range names {
		result[index] = truncate(os.Getenv(name))
	}
	return result
}

func (r *RuntimeProbe) cmd(commands []string) []string {
	result := make([]string, len(commands))
	for index, command := range commands {
		arguments, ok := splitCommand(command)
		if !ok || len(arguments) == 0 {
			continue
		}
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		output, err := exec.CommandContext(ctx, arguments[0], arguments[1:]...).Output()
		cancel()
		if err == nil {
			result[index] = truncate(string(output))
		}
	}
	return result
}

func (r *RuntimeProbe) matchProcess(expressions []string) []string {
	result := make([]string, len(expressions))
	processes, err := process.Processes()
	if err != nil {
		return result
	}
	names := make([]string, 0, len(processes))
	for _, current := range processes {
		name, err := current.Name()
		if err == nil {
			names = append(names, name)
		}
	}
	for index, expression := range expressions {
		compiled, err := regexp.Compile(expression)
		if err != nil {
			continue
		}
		for _, name := range names {
			if compiled.MatchString(name) {
				result[index] = truncate(name)
				break
			}
		}
	}
	return result
}

func splitCommand(value string) ([]string, bool) {
	var result []string
	var current strings.Builder
	var quote rune
	escaped := false
	for _, char := range value {
		if escaped {
			current.WriteRune(char)
			escaped = false
			continue
		}
		if char == '\\' && quote != '\'' {
			escaped = true
			continue
		}
		if quote != 0 {
			if char == quote {
				quote = 0
			} else {
				current.WriteRune(char)
			}
			continue
		}
		if char == '\'' || char == '"' {
			quote = char
			continue
		}
		if char == ' ' || char == '\t' {
			if current.Len() > 0 {
				result = append(result, current.String())
				current.Reset()
			}
			continue
		}
		if strings.ContainsRune("|&;<>`$", char) {
			return nil, false
		}
		current.WriteRune(char)
	}
	if escaped || quote != 0 {
		return nil, false
	}
	if current.Len() > 0 {
		result = append(result, current.String())
	}
	return result, true
}

func truncate(value string) string {
	if len(value) > maxFieldBytes {
		return value[:maxFieldBytes]
	}
	return value
}
