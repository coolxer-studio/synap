package probe

import (
	"net"
	"testing"
)

func TestBroadcastCalculationShape(t *testing.T) {
	ip := net.ParseIP("192.168.10.12").To4()
	mask := net.CIDRMask(24, 32)
	result := make(net.IP, 4)
	for index := range result {
		result[index] = ip[index] | ^mask[index]
	}
	if result.String() != "192.168.10.255" {
		t.Fatalf("broadcast=%s", result)
	}
}

func TestGuidPersists(t *testing.T) {
	ConfigureDataDir(t.TempDir())
	first, second := GetGuid(), GetGuid()
	if first == "" || first != second {
		t.Fatalf("guid not stable: %q %q", first, second)
	}
}
