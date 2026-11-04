package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"
)

func main() {
	var (
		configPath   = flag.String("config", "", "path to a JSON config file")
		listen       = flag.String("listen", "", "listen address, e.g. :8080")
		target       = flag.String("target", "", "upstream base URL, e.g. http://localhost:9000")
		timeout      = flag.Duration("timeout", 0, "upstream response-header timeout, e.g. 30s")
		console      = flag.Bool("console", false, "also print records to stdout")
		logFile      = flag.String("log-file", "", "also append records to this local file")
		captureBody  = flag.Bool("capture-body", true, "capture request/response bodies")
		maxBody      = flag.Int64("max-body", 0, "max body bytes captured per message; 0 = unlimited")
		kafkaEnabled = flag.Bool("kafka-enabled", true, "enable the Kafka sink")
		kafkaBrokers = flag.String("kafka-brokers", "", "comma-separated Kafka brokers, e.g. 127.0.0.1:9092")
		kafkaTopic   = flag.String("kafka-topic", "", "Kafka topic for transaction records")
	)
	flag.Parse()

	cfg, err := LoadConfig(*configPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	// Flags override the config file only when explicitly set.
	if *listen != "" {
		cfg.Listen = *listen
	}
	if *target != "" {
		cfg.Target = *target
	}
	if *timeout > 0 {
		cfg.Timeout = *timeout
	}
	if flagPassed("console") {
		cfg.Output.Console = *console
	}
	if *logFile != "" {
		cfg.Output.File = *logFile
	}
	if flagPassed("capture-body") {
		cfg.Capture.Body = *captureBody
	}
	if flagPassed("max-body") {
		cfg.Capture.MaxBodySize = *maxBody
	}
	if flagPassed("kafka-enabled") {
		cfg.Output.Kafka.Enabled = *kafkaEnabled
	}
	if *kafkaBrokers != "" {
		cfg.Output.Kafka.Brokers = splitAndTrim(*kafkaBrokers)
	}
	if *kafkaTopic != "" {
		cfg.Output.Kafka.Topic = *kafkaTopic
	}
	if err := cfg.Finalize(); err != nil {
		log.Fatalf("invalid config: %v", err)
	}

	logf := func(format string, args ...any) { log.Printf(format, args...) }
	metrics := &Metrics{}

	// Build the sink chain.
	var sinks []Sink
	if cfg.Output.Console {
		sinks = append(sinks, newLineSink(os.Stdout))
	}
	if cfg.Output.File != "" {
		fs, err := newFileSink(cfg.Output.File)
		if err != nil {
			log.Fatalf("open log file: %v", err)
		}
		sinks = append(sinks, fs)
	}

	var (
		spool        *Spool
		replayer     *Replayer
		replayCT     context.Context
		cancelReplay context.CancelFunc
	)

	if cfg.Output.Kafka.Enabled {
		if cfg.Output.Kafka.Fallback.Enabled {
			if spool, err = OpenSpool(cfg.Output.Kafka.Fallback.File, metrics); err != nil {
				log.Fatalf("open fallback spool: %v", err)
			}
		}

		ks, err := NewKafkaSink(cfg, spool, metrics, logf)
		if err != nil {
			log.Fatalf("init kafka sink: %v", err)
		}
		sinks = append(sinks, ks)

		if spool != nil && cfg.Output.Kafka.Fallback.Replay {
			replayer = newReplayer(spool, cfg.Output.Kafka.Fallback.ReplayInterval, metrics, ks.send, logf)
			replayCT, cancelReplay = context.WithCancel(context.Background())
			replayer.Start(replayCT)
			logf("spool replay enabled (interval=%s, file=%s)", cfg.Output.Kafka.Fallback.ReplayInterval, cfg.Output.Kafka.Fallback.File)
		}
	}

	if len(sinks) == 0 {
		sinks = append(sinks, nopSink{})
	}
	sink := newMultiSink(sinks...)

	gw, err := NewGateway(cfg, sink, metrics, logf)
	if err != nil {
		log.Fatalf("create gateway: %v", err)
	}

	srv := &http.Server{
		Addr:              cfg.Listen,
		Handler:           gw,
		ReadHeaderTimeout: 30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	go func() {
		scheme := "http"
		if cfg.TLS != nil && cfg.TLS.Cert != "" && cfg.TLS.Key != "" {
			scheme = "https"
		}
		log.Printf("agent gateway listening on %s://%s -> %s (capture body=%v, max body=%s)",
			scheme, cfg.Listen, cfg.Target, cfg.Capture.Body, bodyLimitText(cfg.Capture.MaxBodySize))
		log.Printf("output: kafka=%v brokers=%v topic=%q spool=%q console=%v file=%q",
			cfg.Output.Kafka.Enabled, cfg.Output.Kafka.Brokers, cfg.Output.Kafka.Topic,
			spoolFile(cfg), cfg.Output.Console, cfg.Output.File)

		var serveErr error
		if scheme == "https" {
			serveErr = srv.ListenAndServeTLS(cfg.TLS.Cert, cfg.TLS.Key)
		} else {
			serveErr = srv.ListenAndServe()
		}
		if serveErr != nil && serveErr != http.ErrServerClosed {
			log.Fatalf("server: %v", serveErr)
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop
	log.Println("shutting down...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("shutdown: %v", err)
	}

	// Order matters: stop the replayer first so it cannot touch the spool while
	// we are tearing it down; then flush the sink (which may still spool failed
	// records); finally close the spool.
	if cancelReplay != nil {
		cancelReplay()
	}
	if replayer != nil {
		replayer.Wait()
	}
	if err := sink.Close(); err != nil {
		log.Printf("close sink: %v", err)
	}
	if spool != nil {
		if err := spool.Close(); err != nil {
			log.Printf("close spool: %v", err)
		}
	}
	log.Println("agent gateway stopped")
}

// flagPassed reports whether the named flag was set on the command line.
func flagPassed(name string) bool {
	passed := false
	flag.Visit(func(f *flag.Flag) {
		if f.Name == name {
			passed = true
		}
	})
	return passed
}

func splitAndTrim(s string) []string {
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}

func bodyLimitText(n int64) string {
	if n <= 0 {
		return "unlimited"
	}
	return fmt.Sprintf("%d bytes", n)
}
