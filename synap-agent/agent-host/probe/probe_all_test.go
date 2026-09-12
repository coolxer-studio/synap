package probe

import (
	"agent-host/model"
	"testing"
)

func TestGetDataPreservesCheckerArrayLengths(t *testing.T) {
	config := model.ProbeCfg{
		DeviceConfig:  model.DeviceConfig{Device: []string{"unknown", "alsoUnknown"}, Screen: []string{"unknown"}, CPU: []string{"unknown"}, Battery: []string{"unknown"}, UUID: []string{"unknown"}, Network: []string{"unknown"}, Storage: []string{"unknown"}},
		FileConfig:    model.FileConfig{Exist: []string{"/not-present"}, Context: []string{"/not-present"}, FileList: []string{"invalid"}, Md5: []string{"/not-present"}, Access: []string{"/not-present"}, Yara: []string{"disabled"}},
		RuntimeConfig: model.RuntimeConfig{Process: []string{"["}, Env: []string{"SYNAP_TEST_MISSING"}, Cmd: []string{"echo ok | sh"}},
		NetConfig:     model.NetConfig{Port: []string{"invalid"}, Socket: []string{"invalid"}, UDS: []string{"/not-present"}, NetInterface: []string{"["}, HTTPProxy: []string{"["}, Hosts: []string{"["}, Certificate: []string{"disabled"}},
	}
	facts := GetData(&config)
	if len(facts) != 5 {
		t.Fatalf("facts len=%d", len(facts))
	}
	device := facts[0].(*DeviceData)
	assertLengths(t, []int{len(device.Device), len(device.Screen), len(device.CPU), len(device.Battery), len(device.UUID), len(device.Network), len(device.Storage)}, []int{2, 1, 1, 1, 1, 1, 1})
	file := facts[1].(*FileData)
	assertLengths(t, []int{len(file.Exist), len(file.Context), len(file.FileList), len(file.Md5), len(file.Access), len(file.Yara)}, []int{1, 1, 1, 1, 1, 1})
	runtime := facts[2].(*RuntimeData)
	assertLengths(t, []int{len(runtime.Process), len(runtime.Env), len(runtime.Cmd)}, []int{1, 1, 1})
	network := facts[3].(*NetWorkData)
	assertLengths(t, []int{len(network.Port), len(network.Socket), len(network.UDS), len(network.NetInterface), len(network.HTTPProxy), len(network.Hosts), len(network.Certificate)}, []int{1, 1, 1, 1, 1, 1, 1})
}

func TestFilterSSHEventsUsesConfiguredPatterns(t *testing.T) {
	events := [][]string{{"root", "192.0.2.1"}, {"alice", "192.0.2.2"}}
	result := filterSSHEvents(events, []string{"^root "})
	if len(result) != 1 || result[0][0] != "root" {
		t.Fatalf("result=%v", result)
	}
}

func assertLengths(t *testing.T, got, want []int) {
	t.Helper()
	for index := range want {
		if got[index] != want[index] {
			t.Fatalf("index %d length=%d want=%d", index, got[index], want[index])
		}
	}
}
