package probe

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestResolvePublicIPJSONAndPlainText(t *testing.T) {
	for _, body := range []string{`{"ip":"8.8.8.8"}`, "1.1.1.1\n"} {
		server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
			_, _ = response.Write([]byte(body))
		}))
		got := ResolvePublicIP(context.Background(), server.URL)
		server.Close()
		if got == "" {
			t.Fatalf("body %q was not parsed", body)
		}
	}
}

func TestResolvePublicIPRejectsPrivateAndFailureResponses(t *testing.T) {
	for _, body := range []string{`{"ip":"127.0.0.1"}`, `{"ip":"192.168.1.2"}`, `{"ip":"100.64.1.2"}`} {
		server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
			_, _ = response.Write([]byte(body))
		}))
		got := ResolvePublicIP(context.Background(), server.URL)
		server.Close()
		if got != "" {
			t.Fatalf("accepted non-public IP %q", got)
		}
	}
}
