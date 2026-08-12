package transport

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"testing"

	"agent-host/model"
	sc "github.com/coolxer/secure-communication-go"
)

func TestCanonicalLogicalEndpoints(t *testing.T) {
	if StartPath != "/attach/start" || UploadPath != "/attach/upload" ||
		InfoPath != "/cross/info" || RatingPath != "/cross/rating" ||
		LabelStartPath != "/cross/label_start" {
		t.Fatal("logical endpoint changed")
	}
}

func TestMissingTrustConfigurationFailsClosed(t *testing.T) {
	previousAppID, previousKeyID, previousAnchor := secureAppID, secureServerKeyID, secureTrustAnchor
	defer func() {
		secureAppID, secureServerKeyID, secureTrustAnchor = previousAppID, previousKeyID, previousAnchor
	}()
	secureAppID, secureServerKeyID, secureTrustAnchor = "", "", ""
	session := model.Session{Code: "code", Common: make([]string, 24)}
	_, err := New(Config{BaseURL: "http://localhost:11099", DataDir: t.TempDir()}).Start(&session)
	if err == nil || IsRetryable(err) {
		t.Fatalf("expected permanent fail-closed error, got %v", err)
	}
	if session.StartMeta.Attempt != 1 {
		t.Fatalf("start attempt=%d", session.StartMeta.Attempt)
	}
}

func TestRuntimeSecurityConfigurationOverridesBuildValues(t *testing.T) {
	t.Setenv("SYNAP_SC_APP_ID", "synap-host-dev")
	t.Setenv("SYNAP_SC_SERVER_KEY_ID", "server-key-dev")
	t.Setenv("SYNAP_SC_SERVER_PUBLIC_KEY", testTrustAnchor(t))
	t.Setenv("SYNAP_SC_ENROLLMENT_TOKEN", "one-time-token")

	config := RuntimeConfig("http://127.0.0.1:11099", t.TempDir())
	if config.AppID != "synap-host-dev" || config.ServerKeyID != "server-key-dev" ||
		config.EnrollmentToken != "one-time-token" || config.TrustAnchor == "" {
		t.Fatalf("unexpected runtime security config: %+v", config)
	}
}

func TestHTTPClientRequiresTLS12AndRejectsRedirects(t *testing.T) {
	client := New(Config{
		BaseURL: "https://synap.example.test", DataDir: t.TempDir(),
		AppID: "synap-host", ServerKeyID: "server-key", TrustAnchor: testTrustAnchor(t),
	})
	if client.secureInitError != nil {
		t.Fatal(client.secureInitError)
	}
	httpTransport, ok := client.http.GetClient().Transport.(*http.Transport)
	if !ok || httpTransport.TLSClientConfig == nil || httpTransport.TLSClientConfig.MinVersion != tls.VersionTLS12 {
		t.Fatal("transport must require TLS 1.2+")
	}
	if err := client.http.GetClient().CheckRedirect(nil, nil); !errors.Is(err, http.ErrUseLastResponse) {
		t.Fatalf("redirect must be rejected: %v", err)
	}
}

func TestUnknownSessionAndNetworkFailuresAreRetryable(t *testing.T) {
	client := &Client{}
	for _, failure := range []*sc.Error{
		{Code: "SC_UNKNOWN_SESSION", Status: 401},
		{Code: "SC_REQUEST_EXPIRED", Status: 408},
		{Code: "SC_NETWORK_FAILED"},
		{Code: "SC_KEY_PROVIDER_UNAVAILABLE", Status: 503},
	} {
		if classified := client.classifySecureFailure(failure); !IsRetryable(classified) {
			t.Fatalf("%s must be retryable: %v", failure.Code, classified)
		}
	}
	if classified := client.classifySecureFailure(&sc.Error{Code: "SC_HANDSHAKE_FAILED", Status: 401}); IsRetryable(classified) {
		t.Fatalf("handshake admission failure must be permanent: %v", classified)
	}
}

func TestInstallationIdentityUsesPrivatePermissions(t *testing.T) {
	path := filepath.Join(t.TempDir(), "security", "identity.json")
	if _, err := (protectedIdentityStore{path: path}).LoadOrCreate(); err != nil {
		t.Fatal(err)
	}
	directory, err := os.Stat(filepath.Dir(path))
	if err != nil {
		t.Fatal(err)
	}
	file, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if directory.Mode().Perm() != 0o700 || file.Mode().Perm() != 0o600 {
		t.Fatalf("identity permissions directory=%o file=%o",
			directory.Mode().Perm(), file.Mode().Perm())
	}
}

func testTrustAnchor(t *testing.T) string {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	return base64.RawURLEncoding.EncodeToString(encoded)
}

func TestSecureTransportErrorClassification(t *testing.T) {
	retry := retryable("network", 503, &sc.Error{Code: "SC_NETWORK_FAILED", Status: 503})
	if !IsRetryable(retry) {
		t.Fatal("network failure must be retryable")
	}
	permanentError := permanent("authentication", 401, &sc.Error{Code: "SC_HANDSHAKE_FAILED", Status: 401})
	if IsRetryable(permanentError) {
		t.Fatal("authentication failure must be permanent")
	}
	var requestError *RequestError
	if !errors.As(permanentError, &requestError) || requestError.Status != 401 {
		t.Fatalf("unexpected error: %v", permanentError)
	}
}
