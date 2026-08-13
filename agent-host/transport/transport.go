package transport

import (
	"context"
	"crypto/ecdsa"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	log "agent-host/log_zap"
	"agent-host/model"
	sc "github.com/coolxer/secure-communication-go/v2"
	"github.com/go-resty/resty/v2"
)

// host is intentionally retained as a package variable because synap-build
// injects it with -X agent-host/transport.host=<origin>.
var host = "http://localhost:11099"
var secureAppID = ""
var secureServerKeyID = ""
var secureTrustAnchor = ""

const (
	StartPath      = "/attach/start"
	UploadPath     = "/attach/upload"
	InfoPath       = "/cross/info"
	RatingPath     = "/cross/rating"
	LabelStartPath = "/cross/label_start"
	identityV2Name = "identity-v2.json"
)

type RequestError struct {
	Retryable bool
	Status    int
	Message   string
}

func (e *RequestError) Error() string { return e.Message }

type Client struct {
	baseURL         string
	http            *resty.Client
	secure          *sc.Client
	secureInitError error
}

func EmbeddedHost() string { return host }

type Config struct {
	BaseURL         string
	DataDir         string
	AppID           string
	ServerKeyID     string
	TrustAnchor     string
	EnrollmentToken string
}

// RuntimeConfig combines build-time public trust values with deployment
// overrides. The enrollment token is read only at runtime and is never embedded.
func RuntimeConfig(baseURL, dataDir string) Config {
	trustAnchor := environmentOr(
		"SYNAP_SC_TRUST_ANCHOR",
		environmentOr("SYNAP_SC_SERVER_PUBLIC_KEY", secureTrustAnchor))
	return Config{
		BaseURL:         baseURL,
		DataDir:         dataDir,
		AppID:           environmentOr("SYNAP_SC_APP_ID", secureAppID),
		ServerKeyID:     environmentOr("SYNAP_SC_SERVER_KEY_ID", secureServerKeyID),
		TrustAnchor:     trustAnchor,
		EnrollmentToken: strings.TrimSpace(os.Getenv("SYNAP_SC_ENROLLMENT_TOKEN")),
	}
}

func New(config Config) *Client {
	client := resty.New().
		SetTimeout(10*time.Second).
		SetRetryCount(0).
		SetHeader("Content-Type", "application/json").
		SetHeader("User-Agent", "synap-host/2.0.0")
	client.GetClient().Transport = &http.Transport{
		Proxy:                 http.ProxyFromEnvironment,
		DialContext:           (&net.Dialer{Timeout: 5 * time.Second, KeepAlive: 30 * time.Second}).DialContext,
		TLSClientConfig:       &tls.Config{MinVersion: tls.VersionTLS12},
		TLSHandshakeTimeout:   5 * time.Second,
		ResponseHeaderTimeout: 10 * time.Second,
		IdleConnTimeout:       90 * time.Second,
	}
	client.GetClient().CheckRedirect = func(_ *http.Request, _ []*http.Request) error {
		return http.ErrUseLastResponse
	}
	baseURL := strings.TrimRight(strings.TrimSpace(config.BaseURL), "/")
	if configError := validateSecureConfig(config); configError != nil {
		return &Client{baseURL: baseURL, http: client, secureInitError: configError}
	}
	identityPath := secureIdentityPath(config.DataDir)
	secureClient, secureError := sc.New(sc.Config{
		BaseURL:                         baseURL,
		AppID:                           strings.TrimSpace(config.AppID),
		DeviceType:                      "HOST",
		ServerTrustAnchors:              map[string]string{strings.TrimSpace(config.ServerKeyID): strings.TrimSpace(config.TrustAnchor)},
		IdentityStore:                   protectedIdentityStore{path: identityPath},
		HTTPClient:                      client.GetClient(),
		AllowInsecureLoopbackForTesting: isLoopbackHTTP(baseURL),
	})
	if secureError == nil {
		if token := strings.TrimSpace(config.EnrollmentToken); token != "" {
			secureError = secureClient.Enroll(token)
		}
	}
	return &Client{baseURL: baseURL, http: client, secure: secureClient, secureInitError: secureError}
}

func (c *Client) Start(session *model.Session) (model.Resp, error) {
	if session == nil {
		return model.Resp{}, permanent("start session is required", 0, nil)
	}
	session.StartMeta.Attempt++
	return c.businessPost(StartPath, session.Code, model.Envelope{Common: session.Common, Meta: &session.StartMeta})
}

func (c *Client) Upload(record model.QueueRecord) (model.Resp, error) {
	return c.businessPost(UploadPath, record.Code, record.Envelope)
}

func (c *Client) Cross(path string, session model.Session) (string, error) {
	encoded, err := json.Marshal(model.Envelope{Common: session.Common})
	if err != nil {
		return "", permanent("encode cross request", 0, err)
	}
	response, err := c.securePost(path, session.Code, encoded)
	if err != nil {
		return "", err
	}
	if response.Status == http.StatusTooManyRequests || response.Status >= 500 {
		return "", retryable("cross service unavailable", response.Status, nil)
	}
	if response.Status >= 400 {
		return "", permanent("cross request rejected", response.Status, nil)
	}
	var status struct {
		Status int `json:"status"`
	}
	if err := json.Unmarshal(response.Body, &status); err != nil {
		return "", permanent("invalid cross response", response.Status, err)
	}
	if status.Status != 1 {
		return "", permanent("cross service returned status=0", response.Status, nil)
	}
	return string(response.Body), nil
}

func (c *Client) businessPost(path, code string, envelope model.Envelope) (model.Resp, error) {
	encoded, err := json.Marshal(envelope)
	if err != nil {
		return model.Resp{}, permanent("encode request", 0, err)
	}
	response, err := c.securePost(path, code, encoded)
	if err != nil {
		return model.Resp{}, err
	}
	status := response.Status
	if status == http.StatusTooManyRequests || status >= 500 {
		return model.Resp{}, retryable("server temporarily unavailable", status, nil)
	}
	if status >= 400 {
		return model.Resp{}, permanent("request rejected", status, nil)
	}
	var body model.Resp
	if err := json.Unmarshal(response.Body, &body); err != nil {
		return model.Resp{}, permanent("invalid server response", status, err)
	}
	if body.Status != 1 {
		return body, permanent("server returned status=0", status, nil)
	}
	log.D("request completed path=%s status=%d", path, status)
	return body, nil
}

func (c *Client) securePost(path, code string, body []byte) (*sc.Response, error) {
	if c.secureInitError != nil {
		return nil, permanent("secure client configuration is invalid", 0, c.secureInitError)
	}
	if c.secure == nil {
		return nil, permanent("secure client is unavailable", 0, nil)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	response, err := c.secure.Request(ctx, sc.Request{
		Method:           http.MethodPost,
		LogicalPath:      path,
		ContentType:      "application/json",
		ProtectedHeaders: map[string]string{"code": code},
		Body:             body,
	})
	if err == nil {
		return response, nil
	}
	return nil, c.classifySecureFailure(err)
}

func (c *Client) classifySecureFailure(err error) error {
	var secureError *sc.Error
	if errors.As(err, &secureError) {
		if secureError.Code == "SC_UNKNOWN_SESSION" {
			if c.secure != nil {
				c.secure.CloseSession()
			}
			return retryable("secure session expired; handshake required", secureError.HTTPStatus, err)
		}
		if secureError.Code == "SC_REQUEST_EXPIRED" {
			return retryable("secure request timestamp expired", secureError.HTTPStatus, err)
		}
		if secureError.HTTPStatus == http.StatusTooManyRequests || secureError.HTTPStatus >= 500 || secureError.Code == "SC_NETWORK_FAILED" {
			return retryable("secure transport unavailable", secureError.HTTPStatus, err)
		}
		return permanent("secure transport rejected", secureError.HTTPStatus, err)
	}
	return permanent("secure client failed", 0, err)
}

func validateSecureConfig(config Config) error {
	if strings.TrimSpace(config.DataDir) == "" {
		return errors.New("secure identity data directory is required")
	}
	if strings.TrimSpace(config.AppID) == "" || strings.TrimSpace(config.ServerKeyID) == "" || strings.TrimSpace(config.TrustAnchor) == "" {
		return errors.New("secure app ID, server key ID, and trust anchor are required")
	}
	encoded, err := base64.RawURLEncoding.DecodeString(strings.TrimSpace(config.TrustAnchor))
	if err != nil {
		return errors.New("secure trust anchor must be unpadded Base64URL")
	}
	parsed, err := x509.ParsePKIXPublicKey(encoded)
	key, ok := parsed.(*ecdsa.PublicKey)
	if err != nil || !ok || key.Curve.Params().Name != "P-256" {
		return errors.New("secure trust anchor must be a P-256 SPKI public key")
	}
	return nil
}

func environmentOr(name, fallback string) string {
	if value := strings.TrimSpace(os.Getenv(name)); value != "" {
		return value
	}
	return strings.TrimSpace(fallback)
}

func isLoopbackHTTP(value string) bool {
	parsed, err := url.Parse(value)
	if err != nil || parsed.Scheme != "http" {
		return false
	}
	ip := net.ParseIP(parsed.Hostname())
	return strings.EqualFold(parsed.Hostname(), "localhost") || (ip != nil && ip.IsLoopback())
}

func secureIdentityPath(dataDir string) string {
	return filepath.Join(dataDir, "secure-communication", identityV2Name)
}

func IsRetryable(err error) bool {
	var requestError *RequestError
	return errors.As(err, &requestError) && requestError.Retryable
}

func retryable(message string, status int, cause error) error {
	if cause != nil {
		message = fmt.Sprintf("%s: %v", message, cause)
	}
	return &RequestError{Retryable: true, Status: status, Message: message}
}

func permanent(message string, status int, cause error) error {
	if cause != nil {
		message = fmt.Sprintf("%s: %v", message, cause)
	}
	return &RequestError{Status: status, Message: message}
}
