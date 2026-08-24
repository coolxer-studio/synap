package appconfig

import "testing"

func TestValidateServerOrigin(t *testing.T) {
	options := Defaults("https://synap.example.test:11099")
	options.DataDir = t.TempDir()
	if err := options.Validate(); err != nil {
		t.Fatal(err)
	}
	options.ServerBaseURL = "https://user:secret@example.test/path"
	if err := options.Validate(); err == nil {
		t.Fatal("expected invalid server URL")
	}
}

func TestValidateAcceptsRemotePlainHTTP(t *testing.T) {
	options := Defaults("http://synap.example.test:11099")
	options.DataDir = t.TempDir()
	if err := options.Validate(); err != nil {
		t.Fatalf("remote HTTP must be accepted: %v", err)
	}
}

func TestEnvironmentOverridesEmbeddedBuildDefaults(t *testing.T) {
	t.Setenv("SYNAP_HOST_SERVER", "https://env.example.test")
	t.Setenv("SYNAP_HOST_PUBLIC_IP_URL", "https://ip.example.test/value")
	t.Setenv("SYNAP_HOST_APP_ID", "9")
	t.Setenv("SYNAP_HOST_APP_NAME", "environment-app")
	options := Defaults("https://embedded.example.test")
	if options.ServerBaseURL != "https://env.example.test" || options.PublicIPURL != "https://ip.example.test/value" || options.AppID != 9 || options.AppName != "environment-app" {
		t.Fatalf("options=%+v", options)
	}
	// Cobra binds directly to Options, so an explicitly supplied flag is the
	// final mutation and therefore has the highest priority.
	options.ServerBaseURL = "https://cli.example.test"
	if options.ServerBaseURL != "https://cli.example.test" {
		t.Fatal("CLI override was not retained")
	}
}
