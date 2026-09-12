package probe

import "testing"

func TestDeviceInfo_width(t *testing.T) {
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
			if got := d.width(); got != tt.want {
				t.Errorf("width() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestDeviceInfo_height(t *testing.T) {
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
			if got := d.height(); got != tt.want {
				t.Errorf("height() = %v, want %v", got, tt.want)
			}
		})
	}
}
