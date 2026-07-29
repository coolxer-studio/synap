package cmd

import (
	"context"
	"fmt"
	"probe-host/model"
	"strings"
	"testing"
	"time"

	"github.com/shirou/gopsutil/process"
)

func TestStartProbe(t *testing.T) {
	type args struct {
		C *model.ProbeCfg
	}
	tests := []struct {
		name    string
		args    args
		wantErr bool
	}{
		{
			name: "test01",
			args: args{
				C: &model.ProbeCfg{
					StartConfig: []float64{1, 1, 60, 2, 60},
				},
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := StartProbe(context.Background(), tt.args.C)
			if err != nil {
				t.Errorf("StartProbe() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			time.Sleep(60 * time.Second)
		})
	}
}

func TestStopXProbe(t *testing.T) {
	processes, err := process.Processes()
	if err != nil {
		fmt.Printf("err is %v", err)
		return
	}
	for _, p := range processes {
		name, _ := p.Name()
		craeteTime,_ := p.CreateTime()
		fmt.Printf("process name: %s, create time:%d \n", name, craeteTime)
		if strings.Contains(name, processName) {
			fmt.Printf("kill process, pid: %d, processName:%v", p.Pid, name)
			err := p.Kill()
			if err != nil {
				fmt.Printf("kill err is %v", err)
			}
		}
	}
}