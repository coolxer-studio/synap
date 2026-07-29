package probe

import (
	"github.com/shirou/gopsutil/process"
	"os"
	"os/exec"
	"regexp"
	"strings"
)

var runtimeProbe = &RuntimeProbe{}

type RuntimeProbe struct{}

func (r *RuntimeProbe) env(envList []string) []string {
	var res []string

	for _, env := range envList {
		res = append(res, os.Getenv(env))
	}
	return res
}

func (r *RuntimeProbe) cmd(cmds []string) []string {
	var res []string

	for _, cmd := range cmds {
		segs := strings.Split(cmd, " ")
		if len(segs) <= 0 {
			res = append(res, "")
			continue
		}
		var output string
		stdout, err := exec.Command(segs[0], segs[1:]...).Output()
		if err == nil {
			output = string(stdout)
		}
		res = append(res, output)
	}
	return res
}

// matchProcess match process name by regex
func (r *RuntimeProbe) matchProcess(regexes []string) []string {
	var rgx []*regexp.Regexp
	for _, regex := range regexes {
		r, err := regexp.Compile(regex)
		if err != nil {
			continue
		}
		rgx = append(rgx, r)
	}

	// list all process name, match regexes
	var res []string
	processes, err := process.Processes()
	if err != nil {
		return res
	}
	for _, p := range processes {
		name, _ := p.Name()
		for _, r := range rgx {
			if r.MatchString(name) {
				res = append(res, name)
				break
			}
		}
	}
	return res
}
