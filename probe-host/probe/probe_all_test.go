package probe

import (
	"probe-host/model"
	"testing"
)

func TestGetData(t *testing.T) {
	type args struct {
		c model.ProbeCfg
	}
	tests := []struct {
		name string
		args args
		want []interface{}
	}{
		{
			name: "probe_once",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := GetData(&tt.args.c)
			t.Logf("GetData() = %+v, want %v", got, tt.want)
		})
	}
}
