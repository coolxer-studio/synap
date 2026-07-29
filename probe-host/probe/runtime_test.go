package probe

import (
	"testing"
)

func TestRuntimeProbe_cmd(t *testing.T) {
	type args struct {
		cmds []string
	}
	tests := []struct {
		name string
		args args
		want []interface{}
	}{
		{
			name: "which go",
			args: args{
				cmds: []string{"ifconfig", "which go", "go version"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := &RuntimeProbe{}
			t.Logf("res is %v", r.cmd(tt.args.cmds))
		})
	}
}

func TestRuntimeProbe_env(t *testing.T) {
	type args struct {
		envList []string
	}
	tests := []struct {
		name string
		args args
		want []interface{}
	}{
		{
			name: "01",
			args: args{
				envList: []string{"PATH", "GOROOT", "GOPATH"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := &RuntimeProbe{}
			t.Logf("res is %v", r.env(tt.args.envList))
		})
	}
}

func TestRuntimeProbe_matchProcess(t *testing.T) {
	type args struct {
		regexes []string
	}
	tests := []struct {
		name string
		args args
		want []interface{}
	}{
		{
			name: "01",
			args: args{regexes: []string{"ptpcamerad"}},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			r := &RuntimeProbe{}
			t.Logf("res is %v", r.matchProcess(tt.args.regexes))
		})
	}
}
