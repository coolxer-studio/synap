package screen

import "testing"

func TestScreenCollectorsDoNotPanic(t *testing.T) {
	_ = Rotation()
	_ = Density()
	_ = Brightness()
	_ = Width()
	_ = Height()
}
