//go:build windows

package screen

import (
	"fmt"
	"syscall"
)

// Rotation returns the screen rotation.
// Getting this value via WinAPI is complex in Go and would require the 'unsafe' package, so it's not implemented.
func Rotation() string {
	return ""
}

// Density returns the screen density in DPI (dots per inch).
func Density() string {
	var (
		user32        = syscall.MustLoadDLL("user32.dll")
		getDC         = user32.MustFindProc("GetDC")
		releaseDC     = user32.MustFindProc("ReleaseDC")
		gdi32         = syscall.MustLoadDLL("gdi32.dll")
		getDeviceCaps = gdi32.MustFindProc("GetDeviceCaps")
	)
	const LOGPIXELSX = 88 // Horizontal DPI

	hdc, _, _ := getDC.Call(0)
	if hdc == 0 {
		return ""
	}
	defer releaseDC.Call(0, hdc)

	dpi, _, _ := getDeviceCaps.Call(hdc, uintptr(LOGPIXELSX))
	return fmt.Sprintf("%d", dpi)
}

// Brightness returns the screen brightness.
func Brightness() string {
	return ""
}

// Width returns the screen width in pixels.
func Width() string {
	var (
		user32           = syscall.MustLoadDLL("user32.dll")
		getSystemMetrics = user32.MustFindProc("GetSystemMetrics")
	)
	const SM_CXSCREEN = 0
	width, _, _ := getSystemMetrics.Call(SM_CXSCREEN)
	return fmt.Sprintf("%d", width)
}

// Height returns the screen height in pixels.
func Height() string {
	var (
		user32           = syscall.MustLoadDLL("user32.dll")
		getSystemMetrics = user32.MustFindProc("GetSystemMetrics")
	)
	const SM_CYSCREEN = 1
	height, _, _ := getSystemMetrics.Call(SM_CYSCREEN)
	return fmt.Sprintf("%d", height)
}
