package probe

import (
	"testing"
)

func Test_ip2Region(t *testing.T) {
	tests := []struct {
		name    string
		want    ipRegion
		wantErr bool
	}{
		{
			name: "01",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ip2Region()
			t.Log("got:", got)
			if err != nil {
				t.Errorf("ip2Region() error = %v, wantErr %v", err, tt.wantErr)
			}

		})
	}
}
