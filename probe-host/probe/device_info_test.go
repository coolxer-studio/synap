package probe

import (
	"context"
	"fmt"
	"github.com/distatus/battery"
	"github.com/shirou/gopsutil/cpu"
	"github.com/shirou/gopsutil/host"
	"github.com/shirou/gopsutil/net"
	"testing"
)

func TestDeviceInfo_systemName(t *testing.T) {
	platform, family, pver, _ := host.PlatformInformation()
	fmt.Printf("%s %s %s\n", platform, family, pver)

	infoSet, _ := host.Info()
	fmt.Printf("%v\n", infoSet)

	cpuInfo, _ := cpu.Counts(true)
	fmt.Printf("%v\n", cpuInfo)

	cpuPercent, _ := cpu.Percent(0, false)
	fmt.Printf("%v\n", cpuPercent)

	cpuInfoList, err := cpu.InfoWithContext(context.Background())
	fmt.Printf("%v\n", cpuInfoList)

	nets, err := net.Interfaces()
	fmt.Printf("%v\n", nets)

	batteries, err := battery.GetAll()
	if err != nil {
		fmt.Println("Could not get battery info!")
		return
	}
	for i, battery := range batteries {
		fmt.Printf("Bat%d: ", i)
		fmt.Printf("state: %s, ", battery.State.String())
		fmt.Printf("current capacity: %f mWh, ", battery.Current)
		fmt.Printf("last full capacity: %f mWh, ", battery.Full)
		fmt.Printf("design capacity: %f mWh, ", battery.Design)
		fmt.Printf("charge rate: %f mW, ", battery.ChargeRate)
		fmt.Printf("voltage: %f V, ", battery.Voltage)
		fmt.Printf("design voltage: %f V\n", battery.DesignVoltage)
	}

	cpuInfos, _ := cpu.Info()

	for _, info := range cpuInfos {
		fmt.Printf("%s", info)
	}
}

func TestDeviceInfo_wanIp(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{
			name: "01",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			d := &DeviceInfo{}
			t.Logf("wanIp() = %v, want %v", d.wanIp(), "")
		})
	}
}

func TestDeviceInfo_cpuType(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{
			name: "01",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			d := &DeviceInfo{}
			got := d.cpuType()
			t.Logf("cpuType() = %v, want %v", got, "")
		})
	}
}

func TestDeviceInfo_abi(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{
			name: "test",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			d := &DeviceInfo{}
			got := d.abi()
			t.Logf("abi() = %v, want %v", got, tt.want)
		})
	}
}

func TestDeviceInfo_broadcast(t *testing.T) {
	tests := []struct {
		name string
		want string
	}{
		{
			name: "获取广播地址",
			want: "172.17.79.255",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			d := &DeviceInfo{}
			if got := d.broadcast(); got != tt.want {
				t.Errorf("broadcast() = %v, want %v", got, tt.want)
			}
		})
	}
}
