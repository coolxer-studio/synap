package probe

import (
	"testing"
)

func TestEventProbe_sshd(t *testing.T) {
	tests := []struct {
		name string
		want [][]string
	}{
		{
			name: "test_01",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			e := &EventProbe{}
			got := e.sshd()
			t.Logf("sshd() = %v, want %v", got, tt.want)

		})
	}
}
