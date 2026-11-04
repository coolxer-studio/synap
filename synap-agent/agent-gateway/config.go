package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"time"
)

// Config is the agent gateway configuration. It can be loaded from a JSON file
// and then overridden by command line flags.
type Config struct {
	// Listen is the address the probe listens on, e.g. ":8080".
	Listen string `json:"listen"`
	// Target is the upstream base URL all requests are forwarded to.
	Target string `json:"target"`

	// StripPrefix removes the first matching prefix from the incoming path
	// before forwarding.
	StripPrefix []string `json:"strip_prefix"`
	// AddPrefix is inserted between the target base path and the request path.
	AddPrefix string `json:"add_prefix"`

	// TimeoutStr is the JSON representation of Timeout (e.g. "30s").
	TimeoutStr string `json:"timeout"`
	// Timeout bounds how long the probe waits for upstream response headers.
	Timeout time.Duration `json:"-"`

	// AddHeaders are set (overwrite) on the forwarded request.
	AddHeaders map[string]string `json:"add_headers"`
	// RemoveHeaders are deleted from the forwarded request.
	RemoveHeaders []string `json:"remove_headers"`

	// InsecureSkipVerify disables upstream TLS certificate verification.
	InsecureSkipVerify bool `json:"insecure_skip_verify"`

	// TLS enables HTTPS on the listening side when Cert and Key are set.
	TLS *TLSConfig `json:"tls"`

	// Capture controls what request/response data is recorded.
	Capture CaptureConfig `json:"capture"`
	// Output controls where the formatted records are written.
	Output OutputConfig `json:"output"`
}

// TLSConfig holds the listening-side certificate material.
type TLSConfig struct {
	Cert string `json:"cert"`
	Key  string `json:"key"`
}

// CaptureConfig controls which parts of a transaction are captured.
type CaptureConfig struct {
	// Headers includes request/response headers in the record.
	Headers bool `json:"headers"`
	// Body enables capturing request/response bodies.
	Body bool `json:"body"`
	// MaxBodySize is the maximum number of body bytes captured per message.
	// 0 (the default) or any negative value means no limit: the full body is
	// captured so it is preserved verbatim for later analysis.
	MaxBodySize int64 `json:"max_body_size"`
	// RedactHeaders lists header names whose values are masked in the record.
	// A nil value means the safe default set is used; an empty list disables
	// redaction.
	RedactHeaders []string `json:"redact_headers"`
}

// OutputConfig controls the record sinks.
type OutputConfig struct {
	// Console mirrors records to stdout (useful for debugging).
	Console bool `json:"console"`
	// File appends a local copy of every record (optional archive).
	File string `json:"file"`
	// Kafka is the primary sink.
	Kafka KafkaConfig `json:"kafka"`
}

// KafkaConfig configures the Kafka producer.
type KafkaConfig struct {
	Enabled bool `json:"enabled"`
	// Brokers is the bootstrap broker list.
	Brokers []string `json:"brokers"`
	// Topic receives the transaction records.
	Topic string `json:"topic"`
	// ClientID identifies this probe to the cluster.
	ClientID string `json:"client_id"`
	// Key selects the message key: "id" (default), "client_ip" or "none".
	Key string `json:"key"`

	// BatchSize is the max number of records per produce request.
	BatchSize int `json:"batch_size"`
	// BatchTimeoutStr is the JSON representation of BatchTimeout.
	BatchTimeoutStr string `json:"batch_timeout"`
	// BatchTimeout flushes a partial batch after this idle time.
	BatchTimeout time.Duration `json:"-"`

	// QueueSize bounds the in-memory queue before spilling to the fallback file.
	QueueSize int `json:"queue_size"`

	// WriteTimeoutStr is the JSON representation of WriteTimeout.
	WriteTimeoutStr string `json:"write_timeout"`
	// WriteTimeout bounds a single produce attempt.
	WriteTimeout time.Duration `json:"-"`

	// MaxAttempts is the number of internal produce retries.
	MaxAttempts int `json:"max_attempts"`
	// RequiredAcks is one of "all" (default), "local" or "none".
	RequiredAcks string `json:"required_acks"`
	// Compression is one of "none" (default), "gzip", "snappy", "lz4", "zstd".
	Compression string `json:"compression"`

	TLS  KafkaTLSConfig  `json:"tls"`
	SASL KafkaSASLConfig `json:"sasl"`

	// Fallback spools records to a local file when Kafka is unavailable.
	Fallback FallbackConfig `json:"fallback"`
}

// KafkaTLSConfig configures TLS to the Kafka brokers.
type KafkaTLSConfig struct {
	Enabled            bool   `json:"enabled"`
	InsecureSkipVerify bool   `json:"insecure_skip_verify"`
	CAFile             string `json:"ca_file"`
	CertFile           string `json:"cert_file"`
	KeyFile            string `json:"key_file"`
}

// KafkaSASLConfig configures SASL authentication to the Kafka brokers.
type KafkaSASLConfig struct {
	Enabled   bool   `json:"enabled"`
	Mechanism string `json:"mechanism"` // plain | scram-sha-256 | scram-sha-512
	Username  string `json:"username"`
	Password  string `json:"password"`
}

// FallbackConfig configures the local spool used when Kafka is unreachable.
type FallbackConfig struct {
	Enabled bool `json:"enabled"`
	// File is the spool path. Records are appended as JSON lines.
	File string `json:"file"`
	// Replay periodically retries spooled records.
	Replay bool `json:"replay"`
	// ReplayIntervalStr is the JSON representation of ReplayInterval.
	ReplayIntervalStr string `json:"replay_interval"`
	// ReplayInterval is the replay tick interval.
	ReplayInterval time.Duration `json:"-"`
}

// defaultRedactHeaders is used when RedactHeaders is not configured.
var defaultRedactHeaders = []string{
	"Authorization",
	"Proxy-Authorization",
	"Cookie",
	"Set-Cookie",
}

// LoadConfig reads the optional JSON config file and applies defaults. It does
// not validate the target so that command line flags can still fill it in.
func LoadConfig(path string) (*Config, error) {
	cfg := defaultConfig()

	if path != "" {
		data, err := os.ReadFile(path)
		if err != nil {
			return nil, fmt.Errorf("read config file: %w", err)
		}
		if err := json.Unmarshal(data, cfg); err != nil {
			return nil, fmt.Errorf("parse config file: %w", err)
		}
	}

	cfg.ApplyDefaults()
	return cfg, nil
}

func defaultConfig() *Config {
	return &Config{
		Listen:     ":8080",
		TimeoutStr: "30s",
		Capture: CaptureConfig{
			Headers:     true,
			Body:        true,
			MaxBodySize: 0, // unlimited
		},
		Output: OutputConfig{
			Console: false,
			Kafka: KafkaConfig{
				Enabled:         true,
				Brokers:         []string{"127.0.0.1:9092"},
				Topic:           "agent-gateway",
				ClientID:        "agent-gateway",
				Key:             "id",
				BatchSize:       500,
				BatchTimeoutStr: "1s",
				QueueSize:       10000,
				WriteTimeoutStr: "5s",
				MaxAttempts:     3,
				RequiredAcks:    "all",
				Compression:     "none",
				Fallback: FallbackConfig{
					Enabled:           true,
					File:              "agent-gateway-spool.ndjson",
					Replay:            true,
					ReplayIntervalStr: "30s",
				},
			},
		},
	}
}

// ApplyDefaults fills in derived/default values. It is idempotent.
func (c *Config) ApplyDefaults() {
	if c.Listen == "" {
		c.Listen = ":8080"
	}

	if c.Timeout == 0 {
		if d, err := parseDuration(c.TimeoutStr); err == nil {
			c.Timeout = d
		}
		if c.Timeout == 0 {
			c.Timeout = 30 * time.Second
		}
	}
	if c.Timeout < 0 {
		c.Timeout = 0
	}

	if c.Capture.MaxBodySize < 0 {
		c.Capture.MaxBodySize = 0
	}
	if c.Capture.RedactHeaders == nil {
		c.Capture.RedactHeaders = append([]string(nil), defaultRedactHeaders...)
	}

	k := &c.Output.Kafka
	if k.Key == "" {
		k.Key = "id"
	}
	if k.BatchSize <= 0 {
		k.BatchSize = 500
	}
	if k.QueueSize <= 0 {
		k.QueueSize = 10000
	}
	if k.BatchTimeout == 0 {
		if d, err := parseDuration(k.BatchTimeoutStr); err == nil {
			k.BatchTimeout = d
		}
		if k.BatchTimeout == 0 {
			k.BatchTimeout = time.Second
		}
	}
	if k.WriteTimeout == 0 {
		if d, err := parseDuration(k.WriteTimeoutStr); err == nil {
			k.WriteTimeout = d
		}
		if k.WriteTimeout == 0 {
			k.WriteTimeout = 5 * time.Second
		}
	}
	if k.MaxAttempts <= 0 {
		k.MaxAttempts = 3
	}
	if k.Topic == "" {
		k.Topic = "agent-gateway"
	}
	if k.ClientID == "" {
		k.ClientID = "agent-gateway"
	}
	if k.RequiredAcks == "" {
		k.RequiredAcks = "all"
	}
	if k.Compression == "" {
		k.Compression = "none"
	}
	if k.Fallback.File == "" {
		k.Fallback.File = "agent-gateway-spool.ndjson"
	}
	if k.Fallback.ReplayInterval == 0 {
		if d, err := parseDuration(k.Fallback.ReplayIntervalStr); err == nil {
			k.Fallback.ReplayInterval = d
		}
		if k.Fallback.ReplayInterval == 0 {
			k.Fallback.ReplayInterval = 30 * time.Second
		}
	}
}

// Validate checks that the config is usable.
func (c *Config) Validate() error {
	if c.Target == "" {
		return errors.New("target is required (use --target or set \"target\" in the config file)")
	}
	if _, err := parseDuration(c.TimeoutStr); c.TimeoutStr != "" && err != nil {
		return fmt.Errorf("invalid timeout %q: %w", c.TimeoutStr, err)
	}

	if !c.Output.Kafka.Enabled && !c.Output.Console && c.Output.File == "" {
		return errors.New("no output configured: enable kafka, console or file")
	}

	k := &c.Output.Kafka
	if k.Enabled {
		if len(k.Brokers) == 0 {
			return errors.New("kafka.brokers is required when kafka is enabled")
		}
		if k.Topic == "" {
			return errors.New("kafka.topic is required when kafka is enabled")
		}
		switch k.Key {
		case "id", "client_ip", "none":
		default:
			return fmt.Errorf("invalid kafka.key %q (want id, client_ip or none)", k.Key)
		}
		switch k.RequiredAcks {
		case "all", "local", "none":
		default:
			return fmt.Errorf("invalid kafka.required_acks %q (want all, local or none)", k.RequiredAcks)
		}
		switch k.Compression {
		case "none", "gzip", "snappy", "lz4", "zstd":
		default:
			return fmt.Errorf("invalid kafka.compression %q", k.Compression)
		}
		if k.SASL.Enabled {
			switch k.SASL.Mechanism {
			case "plain", "scram-sha-256", "scram-sha-512":
			default:
				return fmt.Errorf("invalid kafka.sasl.mechanism %q", k.SASL.Mechanism)
			}
		}
	}
	return nil
}

// Finalize applies defaults and validates in one step.
func (c *Config) Finalize() error {
	c.ApplyDefaults()
	return c.Validate()
}

func parseDuration(s string) (time.Duration, error) {
	if s == "" {
		return 0, nil
	}
	return time.ParseDuration(s)
}
