package main

import (
	"os"
	"testing"
)

func TestExampleConfigLoads(t *testing.T) {
	cfg, err := LoadConfig("config.example.json")
	if err != nil {
		t.Fatalf("load example config: %v", err)
	}
	if err := cfg.Validate(); err != nil {
		t.Fatalf("validate example config: %v", err)
	}
	if !cfg.Output.Kafka.Enabled {
		t.Error("example config should enable kafka")
	}
	if len(cfg.Output.Kafka.Brokers) == 0 || cfg.Output.Kafka.Topic == "" {
		t.Errorf("example config kafka not configured: %+v", cfg.Output.Kafka)
	}
	if cfg.Capture.MaxBodySize != 0 {
		t.Errorf("example config should default to unlimited body, got %d", cfg.Capture.MaxBodySize)
	}
}

func TestConfigStringDurationsAreHonored(t *testing.T) {
	dir := t.TempDir()
	path := dir + "/cfg.json"
	raw := `{
	  "target": "http://localhost:9000",
	  "timeout": "12s",
	  "output": {
	    "console": true,
	    "kafka": {
	      "brokers": ["127.0.0.1:9092"],
	      "topic": "t",
	      "batch_timeout": "250ms",
	      "write_timeout": "3s",
	      "fallback": { "replay_interval": "7s" }
	    }
	  }
	}`
	if err := os.WriteFile(path, []byte(raw), 0o644); err != nil {
		t.Fatalf("write config: %v", err)
	}

	cfg, err := LoadConfig(path)
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.Timeout != 12*1e9 {
		t.Errorf("timeout = %v, want 12s", cfg.Timeout)
	}
	if cfg.Output.Kafka.BatchTimeout != 250*1e6 {
		t.Errorf("batch_timeout = %v, want 250ms", cfg.Output.Kafka.BatchTimeout)
	}
	if cfg.Output.Kafka.WriteTimeout != 3*1e9 {
		t.Errorf("write_timeout = %v, want 3s", cfg.Output.Kafka.WriteTimeout)
	}
	if cfg.Output.Kafka.Fallback.ReplayInterval != 7*1e9 {
		t.Errorf("replay_interval = %v, want 7s", cfg.Output.Kafka.Fallback.ReplayInterval)
	}
}

func TestConfigDefaultsAndValidation(t *testing.T) {
	// Missing target must fail.
	cfg := defaultConfig()
	cfg.ApplyDefaults()
	if err := cfg.Validate(); err == nil {
		t.Error("expected error for missing target")
	}

	// A minimal valid config.
	cfg = defaultConfig()
	cfg.Target = "http://localhost:9000"
	if err := cfg.Finalize(); err != nil {
		t.Fatalf("finalize: %v", err)
	}
	if cfg.Output.Kafka.BatchTimeout == 0 || cfg.Output.Kafka.WriteTimeout == 0 {
		t.Error("kafka duration defaults not applied")
	}
	if len(cfg.Capture.RedactHeaders) == 0 {
		t.Error("default redact headers not applied")
	}

	// Bad kafka key must fail.
	cfg = defaultConfig()
	cfg.Target = "http://localhost:9000"
	cfg.Output.Kafka.Key = "bogus"
	if err := cfg.Finalize(); err == nil {
		t.Error("expected error for invalid kafka key")
	}

	// No output at all must fail.
	cfg = defaultConfig()
	cfg.Target = "http://localhost:9000"
	cfg.Output.Kafka.Enabled = false
	cfg.Output.Console = false
	cfg.Output.File = ""
	if err := cfg.Finalize(); err == nil {
		t.Error("expected error for no output configured")
	}
}
