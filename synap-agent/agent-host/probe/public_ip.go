package probe

import (
	"context"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"strings"
	"time"
)

const maxPublicIPResponseBytes = 4 << 10

// ResolvePublicIP queries the configured external service once per new probe
// session. Failure is deliberately non-fatal so collection can still start.
func ResolvePublicIP(parent context.Context, endpoint string) string {
	ctx, cancel := context.WithTimeout(parent, 4*time.Second)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return ""
	}
	request.Header.Set("Accept", "application/json, text/plain")
	request.Header.Set("User-Agent", "synap-host/2.0.0")
	client := &http.Client{Timeout: 4 * time.Second}
	response, err := client.Do(request)
	if err != nil {
		return ""
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return ""
	}
	body, err := io.ReadAll(io.LimitReader(response.Body, maxPublicIPResponseBytes+1))
	if err != nil || len(body) > maxPublicIPResponseBytes {
		return ""
	}
	value := strings.TrimSpace(string(body))
	var payload struct {
		IP      string `json:"ip"`
		Query   string `json:"query"`
		Address string `json:"address"`
	}
	if json.Unmarshal(body, &payload) == nil {
		for _, candidate := range []string{payload.IP, payload.Query, payload.Address} {
			if isPublicIP(candidate) {
				return net.ParseIP(strings.TrimSpace(candidate)).String()
			}
		}
	}
	if isPublicIP(value) {
		return net.ParseIP(value).String()
	}
	return ""
}

func isPublicIP(value string) bool {
	ip := net.ParseIP(strings.TrimSpace(value))
	if ip == nil || !ip.IsGlobalUnicast() || ip.IsPrivate() || ip.IsLoopback() || ip.IsLinkLocalUnicast() {
		return false
	}
	_, carrierNAT, _ := net.ParseCIDR("100.64.0.0/10")
	return !carrierNAT.Contains(ip)
}
