package appconfig

import (
	"errors"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

const (
	DefaultSpoolMaxBytes int64 = 100 << 20
	DefaultSpoolMaxItems       = 10_000
)

type Options struct {
	ServerBaseURL string
	PublicIPURL   string
	AppID         int
	AppName       string
	AppPackage    string
	AppVersion    string
	UserID        string
	DataDir       string
	LogLevel      string
	SpoolMaxBytes int64
	SpoolMaxItems int
}

func Defaults(embeddedServer string) Options {
	base := env("SYNAP_HOST_SERVER", strings.TrimRight(embeddedServer, "/"))
	if base == "" {
		base = "http://localhost:11099"
	}
	return Options{
		ServerBaseURL: base,
		PublicIPURL:   env("SYNAP_HOST_PUBLIC_IP_URL", "https://api64.ipify.org?format=json"),
		AppID:         envInt("SYNAP_HOST_APP_ID", 1),
		AppName:       env("SYNAP_HOST_APP_NAME", "x-probe"),
		AppPackage:    env("SYNAP_HOST_APP_PACKAGE", "agent-host"),
		AppVersion:    env("SYNAP_HOST_APP_VERSION", "2.0.0"),
		UserID:        env("SYNAP_HOST_USER_ID", ""),
		DataDir:       env("SYNAP_HOST_DATA_DIR", defaultDataDir()),
		LogLevel:      env("SYNAP_HOST_LOG_LEVEL", "info"),
		SpoolMaxBytes: envInt64("SYNAP_HOST_SPOOL_MAX_BYTES", DefaultSpoolMaxBytes),
		SpoolMaxItems: envInt("SYNAP_HOST_SPOOL_MAX_ITEMS", DefaultSpoolMaxItems),
	}
}

func (o *Options) Validate() error {
	parsed, err := url.Parse(strings.TrimSpace(o.ServerBaseURL))
	if err != nil || parsed.Scheme == "" || parsed.Host == "" || parsed.User != nil || parsed.RawQuery != "" || parsed.Fragment != "" {
		return errors.New("server must be an http(s) origin without credentials, query, or fragment")
	}
	if parsed.Scheme != "http" && parsed.Scheme != "https" {
		return errors.New("server must use http or https")
	}
	if parsed.Path != "" && parsed.Path != "/" {
		return errors.New("server must not contain a path")
	}
	publicIPURL, err := url.Parse(strings.TrimSpace(o.PublicIPURL))
	if err != nil || publicIPURL.Scheme == "" || publicIPURL.Host == "" || publicIPURL.User != nil || publicIPURL.Fragment != "" {
		return errors.New("public-ip-url must be an http(s) URL without credentials or fragment")
	}
	if publicIPURL.Scheme != "http" && publicIPURL.Scheme != "https" {
		return errors.New("public-ip-url must use http or https")
	}
	if o.AppID <= 0 || o.AppName == "" || o.AppPackage == "" || o.AppVersion == "" {
		return errors.New("app-id, app-name, app-package and app-version are required")
	}
	if o.DataDir == "" || o.SpoolMaxBytes <= 0 || o.SpoolMaxItems <= 0 {
		return errors.New("data-dir and positive spool limits are required")
	}
	o.ServerBaseURL = strings.TrimRight(o.ServerBaseURL, "/")
	o.PublicIPURL = strings.TrimSpace(o.PublicIPURL)
	return nil
}

func defaultDataDir() string {
	base, err := os.UserConfigDir()
	if err != nil || base == "" {
		return filepath.Join(".", ".synap-host")
	}
	return filepath.Join(base, "synap-host")
}

func env(name, fallback string) string {
	if value, ok := os.LookupEnv(name); ok && strings.TrimSpace(value) != "" {
		return strings.TrimSpace(value)
	}
	return fallback
}

func envInt(name string, fallback int) int {
	value, err := strconv.Atoi(env(name, ""))
	if err != nil {
		return fallback
	}
	return value
}

func envInt64(name string, fallback int64) int64 {
	value, err := strconv.ParseInt(env(name, ""), 10, 64)
	if err != nil {
		return fallback
	}
	return value
}
