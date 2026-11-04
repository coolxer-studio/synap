package main

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"os"
	"sync/atomic"
	"time"

	"github.com/segmentio/kafka-go"
	"github.com/segmentio/kafka-go/sasl"
	"github.com/segmentio/kafka-go/sasl/plain"
	"github.com/segmentio/kafka-go/sasl/scram"
)

// sendFunc is the low-level batch producer. It is a field so tests can inject a
// fake without a real broker.
type sendFunc func(ctx context.Context, msgs []kafka.Message) error

// KafkaSink batches records in memory and produces them asynchronously. When
// Kafka is slow or unavailable, records spill to the fallback spool instead of
// blocking the proxy.
type KafkaSink struct {
	cfg     KafkaConfig
	writer  *kafka.Writer
	send    sendFunc
	spool   *Spool
	metrics *Metrics
	logf    func(format string, args ...any)

	queue  chan kafka.Message
	stop   chan struct{}
	done   chan struct{}
	closed atomic.Bool
}

// NewKafkaSink builds the sink and starts its background worker. spool may be
// nil to disable the fallback.
func NewKafkaSink(cfg *Config, spool *Spool, metrics *Metrics, logf func(string, ...any)) (*KafkaSink, error) {
	writer, send, err := buildWriter(&cfg.Output.Kafka)
	if err != nil {
		return nil, err
	}
	k := newKafkaSink(&cfg.Output.Kafka, writer, send, spool, metrics, logf)
	k.start()
	return k, nil
}

func newKafkaSink(cfg *KafkaConfig, writer *kafka.Writer, send sendFunc, spool *Spool, metrics *Metrics, logf func(string, ...any)) *KafkaSink {
	if logf == nil {
		logf = func(string, ...any) {}
	}
	return &KafkaSink{
		cfg:     *cfg,
		writer:  writer,
		send:    send,
		spool:   spool,
		metrics: metrics,
		logf:    logf,
		queue:   make(chan kafka.Message, cfg.QueueSize),
		stop:    make(chan struct{}),
		done:    make(chan struct{}),
	}
}

func (k *KafkaSink) start() { go k.run() }

// Write enqueues a record. It never blocks: when the queue is full the record is
// spilled to the fallback spool so nothing is lost.
func (k *KafkaSink) Write(key string, value []byte) error {
	msg := kafka.Message{Key: []byte(key), Value: value, Time: time.Now()}
	if k.closed.Load() {
		return k.spoolMessage(msg)
	}
	select {
	case k.queue <- msg:
		return nil
	default:
		k.metrics.QueueOverflow.Add(1)
		return k.spoolMessage(msg)
	}
}

// run drains the queue into Kafka in batches, falling back to the spool on
// failure.
func (k *KafkaSink) run() {
	defer close(k.done)

	timer := time.NewTimer(k.cfg.BatchTimeout)
	defer timer.Stop()

	batch := make([]kafka.Message, 0, k.cfg.BatchSize)
	flush := func() {
		if len(batch) == 0 {
			return
		}
		ctx, cancel := context.WithTimeout(context.Background(), k.cfg.WriteTimeout)
		err := k.send(ctx, batch)
		cancel()
		if err != nil {
			k.metrics.SendErrors.Add(1)
			k.logf("kafka produce of %d records failed: %v", len(batch), err)
			for _, m := range batch {
				_ = k.spoolMessage(m)
			}
		} else {
			k.metrics.Sent.Add(int64(len(batch)))
		}
		batch = batch[:0]
	}
	reset := func() {
		if !timer.Stop() {
			select {
			case <-timer.C:
			default:
			}
		}
		timer.Reset(k.cfg.BatchTimeout)
	}

	for {
		select {
		case m := <-k.queue:
			batch = append(batch, m)
			if len(batch) >= k.cfg.BatchSize {
				flush()
				reset()
			}
		case <-timer.C:
			flush()
			reset()
		case <-k.stop:
			for {
				select {
				case m := <-k.queue:
					batch = append(batch, m)
					if len(batch) >= k.cfg.BatchSize {
						flush()
					}
				default:
					flush()
					return
				}
			}
		}
	}
}

// spoolMessage persists a record to the fallback spool, or drops it when no
// fallback is configured.
func (k *KafkaSink) spoolMessage(m kafka.Message) error {
	if k.spool == nil {
		k.metrics.Dropped.Add(1)
		return nil
	}
	if err := k.spool.Write(string(m.Key), m.Value); err != nil {
		k.metrics.Dropped.Add(1)
		return err
	}
	k.metrics.Spooled.Add(1)
	return nil
}

// Close stops the worker after flushing what is queued, then closes the writer.
func (k *KafkaSink) Close() error {
	if !k.closed.CompareAndSwap(false, true) {
		return nil
	}
	close(k.stop)
	<-k.done
	if k.writer != nil {
		return k.writer.Close()
	}
	return nil
}

// buildWriter constructs the kafka-go writer and its send function.
func buildWriter(cfg *KafkaConfig) (*kafka.Writer, sendFunc, error) {
	transport := &kafka.Transport{
		ClientID:    cfg.ClientID,
		DialTimeout: 10 * time.Second,
	}

	if cfg.TLS.Enabled {
		tc, err := buildTLS(&cfg.TLS)
		if err != nil {
			return nil, nil, err
		}
		transport.TLS = tc
	}
	if cfg.SASL.Enabled {
		mech, err := buildSASL(&cfg.SASL)
		if err != nil {
			return nil, nil, err
		}
		transport.SASL = mech
	}

	w := &kafka.Writer{
		Addr:                   kafka.TCP(cfg.Brokers...),
		Topic:                  cfg.Topic,
		Balancer:               balancerFor(cfg.Key),
		RequiredAcks:           requiredAcks(cfg.RequiredAcks),
		Compression:            compression(cfg.Compression),
		MaxAttempts:            cfg.MaxAttempts,
		WriteTimeout:           cfg.WriteTimeout,
		BatchSize:              cfg.BatchSize,
		BatchTimeout:           cfg.BatchTimeout,
		Transport:              transport,
		AllowAutoTopicCreation: true,
	}
	send := func(ctx context.Context, msgs []kafka.Message) error {
		return w.WriteMessages(ctx, msgs...)
	}
	return w, send, nil
}

func balancerFor(key string) kafka.Balancer {
	if key == "none" {
		return &kafka.LeastBytes{}
	}
	// Hashing the key keeps records with the same key on one partition.
	return &kafka.Hash{}
}

func requiredAcks(s string) kafka.RequiredAcks {
	switch s {
	case "none":
		return kafka.RequireNone
	case "local":
		return kafka.RequireOne
	default:
		return kafka.RequireAll
	}
}

func compression(s string) kafka.Compression {
	switch s {
	case "gzip":
		return kafka.Gzip
	case "snappy":
		return kafka.Snappy
	case "lz4":
		return kafka.Lz4
	case "zstd":
		return kafka.Zstd
	default:
		return 0
	}
}

func buildTLS(cfg *KafkaTLSConfig) (*tls.Config, error) {
	tc := &tls.Config{InsecureSkipVerify: cfg.InsecureSkipVerify}
	if cfg.CAFile != "" {
		pem, err := os.ReadFile(cfg.CAFile)
		if err != nil {
			return nil, fmt.Errorf("read kafka ca: %w", err)
		}
		pool := x509.NewCertPool()
		if !pool.AppendCertsFromPEM(pem) {
			return nil, fmt.Errorf("kafka ca file %s has no valid certificates", cfg.CAFile)
		}
		tc.RootCAs = pool
	}
	if cfg.CertFile != "" || cfg.KeyFile != "" {
		cert, err := tls.LoadX509KeyPair(cfg.CertFile, cfg.KeyFile)
		if err != nil {
			return nil, fmt.Errorf("load kafka client cert: %w", err)
		}
		tc.Certificates = []tls.Certificate{cert}
	}
	return tc, nil
}

func buildSASL(cfg *KafkaSASLConfig) (sasl.Mechanism, error) {
	switch cfg.Mechanism {
	case "plain", "":
		return plain.Mechanism{Username: cfg.Username, Password: cfg.Password}, nil
	case "scram-sha-256":
		return scram.Mechanism(scram.SHA256, cfg.Username, cfg.Password)
	case "scram-sha-512":
		return scram.Mechanism(scram.SHA512, cfg.Username, cfg.Password)
	default:
		return nil, fmt.Errorf("unsupported sasl mechanism %q", cfg.Mechanism)
	}
}
