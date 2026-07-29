package probe

import (
	"testing"
)

func TestNetProbe_port(t *testing.T) {
	type args struct {
		ports []string
	}
	tests := []struct {
		name string
		args args
		want []interface{}
	}{
		{
			name: "test01",
			args: args{
				ports: []string{"33060", "9001"},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			n := &NetProbe{}
			t.Logf("res is %v", n.port(tt.args.ports))
		})
	}
}
