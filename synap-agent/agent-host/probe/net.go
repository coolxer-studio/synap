package probe

import (
	"net"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"time"
)

var netProbe = &NetProbe{}

type NetProbe struct{}

func (n *NetProbe) port(ports []string) []int {
	result := make([]int, len(ports))
	for index, port := range ports {
		connection, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", port), 400*time.Millisecond)
		if err == nil {
			result[index] = 1
			_ = connection.Close()
		}
	}
	return result
}

func (n *NetProbe) uds(entries []string) []int {
	result := make([]int, len(entries))
	for index, entry := range entries {
		if _, err := os.Stat(filepath.Clean(entry)); err == nil {
			result[index] = 1
		}
	}
	return result
}

func (n *NetProbe) interfaces(expressions []string) []string {
	return matchValues(expressions, func() []string {
		interfaces, _ := net.Interfaces()
		values := make([]string, 0, len(interfaces))
		for _, item := range interfaces {
			values = append(values, item.Name)
		}
		return values
	}())
}

func (n *NetProbe) proxies(expressions []string) []string {
	values := []string{os.Getenv("HTTP_PROXY"), os.Getenv("HTTPS_PROXY"), os.Getenv("ALL_PROXY"), os.Getenv("NO_PROXY")}
	return matchValues(expressions, values)
}

func (n *NetProbe) hosts(expressions []string) []string {
	path := "/etc/hosts"
	if runtime.GOOS == "windows" {
		path = filepath.Join(os.Getenv("SystemRoot"), "System32", "drivers", "etc", "hosts")
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	lines := strings.Split(truncate(string(data)), "\n")
	return matchValues(expressions, lines)
}

func (n *NetProbe) certificate(expressions []string) []string {
	return make([]string, len(expressions))
}

func matchValues(expressions, values []string) []string {
	result := make([]string, len(expressions))
	for index, expression := range expressions {
		compiled, err := regexp.Compile(expression)
		if err != nil {
			continue
		}
		for _, value := range values {
			if value != "" && compiled.MatchString(value) {
				result[index] = truncate(value)
				break
			}
		}
	}
	return result
}
