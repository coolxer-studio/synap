package main

import (
	"bufio"
	"bytes"
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

type ctxKey int

const ctxKeyExchange ctxKey = iota

// exchange accumulates the state of one relayed call: the captured request and,
// as the response flows through, the response metadata and body.
type exchange struct {
	id       string
	start    time.Time
	gateway  GatewayInfo
	upstream string

	req RequestRec

	mu          sync.Mutex
	status      int
	respHeaders http.Header
	body        bytes.Buffer
	bodyTrunc   bool
	upstreamMs  float64
	errMsg      string
}

func (e *exchange) appendBody(p []byte, limit int64) {
	e.mu.Lock()
	defer e.mu.Unlock()
	if limit > 0 {
		remain := limit - int64(e.body.Len())
		if remain <= 0 {
			e.bodyTrunc = true
			return
		}
		if int64(len(p)) > remain {
			e.body.Write(p[:remain])
			e.bodyTrunc = true
			return
		}
	}
	e.body.Write(p)
}

func (e *exchange) setError(msg string) {
	e.mu.Lock()
	e.errMsg = msg
	e.mu.Unlock()
}

// build assembles the final record. Redaction is applied to headers here.
func (e *exchange) build(redactor headerRedactor, cap *CaptureConfig, bytesOut int64) Transaction {
	e.mu.Lock()
	defer e.mu.Unlock()

	req := e.req
	resp := ResponseRec{
		Status:        e.status,
		StatusText:    http.StatusText(e.status),
		Headers:       e.respHeaders,
		ContentType:   headerGet(e.respHeaders, "Content-Type"),
		ContentLength: parseContentLength(e.respHeaders),
		Body:          e.body.String(),
		BodySize:      int64(e.body.Len()),
		BodyTruncated: e.bodyTrunc,
		BytesOut:      bytesOut,
		UpstreamMs:    e.upstreamMs,
	}

	if !cap.Headers {
		req.Headers = nil
		resp.Headers = nil
	} else {
		req.Headers = redactor.apply(req.Headers)
		resp.Headers = redactor.apply(resp.Headers)
	}
	if !cap.Body {
		req.Body = ""
		resp.Body = ""
	}

	status := e.status
	if status == 0 {
		status = http.StatusOK
		resp.Status = status
		resp.StatusText = http.StatusText(status)
	}

	return Transaction{
		SchemaVersion: recordSchemaVersion,
		Type:          recordType,
		Time:          e.start.Format(time.RFC3339Nano),
		ID:            e.id,
		DurationMs:    float64(time.Since(e.start).Microseconds()) / 1000,
		Upstream:      e.upstream,
		Gateway:       e.gateway,
		Request:       req,
		Response:      resp,
		Error:         e.errMsg,
	}
}

// Gateway is an http.Handler that forwards every request to a fixed upstream,
// captures the full transaction and ships it to the configured sink(s).
type Gateway struct {
	cfg      *Config
	target   *url.URL
	proxy    *httputil.ReverseProxy
	sink     Sink
	metrics  *Metrics
	redactor headerRedactor
	logf     func(format string, args ...any)
}

// NewGateway builds a ready-to-serve gateway.
func NewGateway(cfg *Config, sink Sink, metrics *Metrics, logf func(string, ...any)) (*Gateway, error) {
	target, err := url.Parse(cfg.Target)
	if err != nil {
		return nil, fmt.Errorf("invalid target URL: %w", err)
	}
	if target.Scheme != "http" && target.Scheme != "https" {
		return nil, fmt.Errorf("target scheme must be http or https, got %q", target.Scheme)
	}
	if target.Host == "" {
		return nil, fmt.Errorf("target host is empty")
	}
	if logf == nil {
		logf = func(string, ...any) {}
	}

	g := &Gateway{
		cfg:      cfg,
		target:   target,
		sink:     sink,
		metrics:  metrics,
		redactor: newHeaderRedactor(cfg.Capture.RedactHeaders),
		logf:     logf,
	}

	transport := &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: (&net.Dialer{
			Timeout:   10 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          256,
		MaxIdleConnsPerHost:   64,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
		ResponseHeaderTimeout: cfg.Timeout,
		// Pass upstream compression through untouched so headers stay intact.
		DisableCompression: true,
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: cfg.InsecureSkipVerify,
		},
	}

	g.proxy = &httputil.ReverseProxy{
		Rewrite:        g.rewrite,
		ModifyResponse: g.modifyResponse,
		ErrorHandler:   g.errorHandler,
		Transport:      transport,
		ErrorLog:       log.New(os.Stderr, "proxy: ", log.LstdFlags),
	}
	return g, nil
}

// rewrite builds the outbound request: it rewrites the path, applies header
// rules and sets the standard forwarding headers.
func (g *Gateway) rewrite(pr *httputil.ProxyRequest) {
	pr.SetXForwarded()

	in := pr.In
	out := pr.Out

	path := in.URL.Path
	for _, prefix := range g.cfg.StripPrefix {
		if prefix != "" && strings.HasPrefix(path, prefix) {
			path = strings.TrimPrefix(path, prefix)
			if !strings.HasPrefix(path, "/") {
				path = "/" + path
			}
			break
		}
	}

	out.URL.Scheme = g.target.Scheme
	out.URL.Host = g.target.Host
	out.URL.Path = joinPath(g.target.Path, g.cfg.AddPrefix, path)
	out.URL.RawPath = ""
	out.Host = g.target.Host

	for k, v := range g.cfg.AddHeaders {
		out.Header.Set(k, v)
	}
	for _, k := range g.cfg.RemoveHeaders {
		out.Header.Del(k)
	}
	if out.Header.Get("X-Request-Id") == "" {
		if ex := exchangeFrom(in.Context()); ex != nil {
			out.Header.Set("X-Request-Id", ex.id)
		}
	}
}

// modifyResponse records response metadata and starts capturing the body.
func (g *Gateway) modifyResponse(resp *http.Response) error {
	ex := exchangeFrom(resp.Request.Context())
	if ex == nil {
		return nil
	}

	ex.mu.Lock()
	ex.status = resp.StatusCode
	ex.respHeaders = resp.Header.Clone()
	ex.upstreamMs = float64(time.Since(ex.start).Microseconds()) / 1000
	ex.mu.Unlock()

	if g.cfg.Capture.Body && resp.Body != nil {
		resp.Body = &captureReadCloser{
			rc:    resp.Body,
			ex:    ex,
			limit: g.cfg.Capture.MaxBodySize,
		}
	}
	return nil
}

// errorHandler is invoked when the upstream cannot be reached (or the request
// fails mid-flight). It records the error and returns 502.
func (g *Gateway) errorHandler(w http.ResponseWriter, r *http.Request, err error) {
	if ex := exchangeFrom(r.Context()); ex != nil {
		ex.mu.Lock()
		ex.errMsg = err.Error()
		if ex.status == 0 {
			ex.status = http.StatusBadGateway
		}
		ex.mu.Unlock()
	}
	w.WriteHeader(http.StatusBadGateway)
	_, _ = fmt.Fprintf(w, "502 Bad Gateway: %v\n", err)
}

// ServeHTTP implements http.Handler.
func (g *Gateway) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if g.serveInternal(w, r) {
		return
	}

	ex := &exchange{
		id:       newID(),
		start:    time.Now(),
		gateway:  GatewayInfo{Listen: g.cfg.Listen, Target: g.cfg.Target},
		upstream: g.target.String(),
	}
	r = r.WithContext(context.WithValue(r.Context(), ctxKeyExchange, ex))

	var reqContentLength int64
	if r.ContentLength > 0 {
		reqContentLength = r.ContentLength
	}

	reqBody, reqTrunc, err := captureRequestBody(r, g.cfg.Capture)

	ex.req = RequestRec{
		Method:        r.Method,
		Scheme:        requestScheme(r),
		Host:          r.Host,
		Path:          r.URL.Path,
		Query:         r.URL.RawQuery,
		URI:           r.URL.RequestURI(),
		Proto:         r.Proto,
		RemoteAddr:    r.RemoteAddr,
		ClientIP:      clientIP(r.RemoteAddr),
		UserAgent:     r.Header.Get("User-Agent"),
		Headers:       r.Header.Clone(),
		ContentType:   r.Header.Get("Content-Type"),
		ContentLength: reqContentLength,
		Body:          string(reqBody),
		BodySize:      int64(len(reqBody)),
		BodyTruncated: reqTrunc,
	}

	rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
	if err != nil {
		rec.status = http.StatusBadRequest
		http.Error(rec, "failed to read request body: "+err.Error(), http.StatusBadRequest)
		ex.setError(err.Error())
		ex.mu.Lock()
		ex.status = http.StatusBadRequest
		ex.mu.Unlock()
	} else {
		g.proxy.ServeHTTP(rec, r)
	}

	g.emit(ex, rec.bytes)
}

// emit formats and ships one transaction.
func (g *Gateway) emit(ex *exchange, bytesOut int64) {
	tx := ex.build(g.redactor, &g.cfg.Capture, bytesOut)
	value, err := marshalJSON(tx)
	if err != nil {
		g.logf("marshal transaction %s failed: %v", tx.ID, err)
		return
	}
	g.metrics.Captured.Add(1)
	key := keyFor(g.cfg.Output.Kafka.Key, &tx)
	if err := g.sink.Write(key, value); err != nil {
		g.logf("write transaction %s failed: %v", tx.ID, err)
	}
}

// serveInternal handles gateway-owned endpoints so they are never forwarded.
func (g *Gateway) serveInternal(w http.ResponseWriter, r *http.Request) bool {
	switch r.URL.Path {
	case "/__agent-gateway/health":
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = io.WriteString(w, `{"status":"ok"}`)
		return true
	case "/__agent-gateway/stats":
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		enc := json.NewEncoder(w)
		enc.SetEscapeHTML(false)
		_ = enc.Encode(struct {
			Metrics Snapshot `json:"metrics"`
			Kafka   struct {
				Enabled bool     `json:"enabled"`
				Brokers []string `json:"brokers"`
				Topic   string   `json:"topic"`
			} `json:"kafka"`
			SpoolFile string `json:"spool_file,omitempty"`
		}{
			Metrics: g.metrics.Snapshot(),
			Kafka: struct {
				Enabled bool     `json:"enabled"`
				Brokers []string `json:"brokers"`
				Topic   string   `json:"topic"`
			}{g.cfg.Output.Kafka.Enabled, g.cfg.Output.Kafka.Brokers, g.cfg.Output.Kafka.Topic},
			SpoolFile: spoolFile(g.cfg),
		})
		return true
	}
	return false
}

func spoolFile(cfg *Config) string {
	if cfg.Output.Kafka.Enabled && cfg.Output.Kafka.Fallback.Enabled {
		return cfg.Output.Kafka.Fallback.File
	}
	return ""
}

// captureRequestBody reads the request body into memory (fully unless a limit
// is configured) and restores it so the proxy can forward the full stream.
func captureRequestBody(r *http.Request, cfg CaptureConfig) (body []byte, truncated bool, err error) {
	if !cfg.Body || r.Body == nil {
		return nil, false, nil
	}

	if cfg.MaxBodySize > 0 {
		buf, err := io.ReadAll(io.LimitReader(r.Body, cfg.MaxBodySize))
		if err != nil {
			return buf, false, err
		}
		one := make([]byte, 1)
		n, _ := r.Body.Read(one)
		truncated = n > 0
		r.Body = readCloser{
			Reader: io.MultiReader(bytes.NewReader(buf), bytes.NewReader(one[:n]), r.Body),
			Closer: r.Body,
		}
		return buf, truncated, nil
	}

	buf, err := io.ReadAll(r.Body)
	if err != nil {
		return buf, false, err
	}
	r.Body = io.NopCloser(bytes.NewReader(buf))
	return buf, false, nil
}

type readCloser struct {
	io.Reader
	io.Closer
}

// captureReadCloser tees response bytes into the exchange as the client reads
// them, without blocking streaming responses.
type captureReadCloser struct {
	rc    io.ReadCloser
	ex    *exchange
	limit int64
}

func (c *captureReadCloser) Read(p []byte) (int, error) {
	n, err := c.rc.Read(p)
	if n > 0 {
		c.ex.appendBody(p[:n], c.limit)
	}
	return n, err
}

func (c *captureReadCloser) Close() error { return c.rc.Close() }

// statusRecorder captures the downstream status code and byte count while
// transparently passing through optional interfaces used by the reverse proxy.
type statusRecorder struct {
	http.ResponseWriter
	status int
	bytes  int64
	wrote  bool
}

func (s *statusRecorder) WriteHeader(code int) {
	if !s.wrote {
		s.status = code
		s.wrote = true
	}
	s.ResponseWriter.WriteHeader(code)
}

func (s *statusRecorder) Write(p []byte) (int, error) {
	if !s.wrote {
		s.status = http.StatusOK
		s.wrote = true
	}
	n, err := s.ResponseWriter.Write(p)
	s.bytes += int64(n)
	return n, err
}

func (s *statusRecorder) Flush() {
	if f, ok := s.ResponseWriter.(http.Flusher); ok {
		f.Flush()
	}
}

func (s *statusRecorder) Hijack() (net.Conn, *bufio.ReadWriter, error) {
	h, ok := s.ResponseWriter.(http.Hijacker)
	if !ok {
		return nil, nil, http.ErrNotSupported
	}
	return h.Hijack()
}

func (s *statusRecorder) Unwrap() http.ResponseWriter { return s.ResponseWriter }

func exchangeFrom(ctx context.Context) *exchange {
	ex, _ := ctx.Value(ctxKeyExchange).(*exchange)
	return ex
}

func requestScheme(r *http.Request) string {
	if r.TLS != nil {
		return "https"
	}
	return "http"
}

func headerGet(h http.Header, key string) string {
	if h == nil {
		return ""
	}
	return h.Get(key)
}

func parseContentLength(h http.Header) int64 {
	v := headerGet(h, "Content-Length")
	if v == "" {
		return 0
	}
	n, err := strconv.ParseInt(v, 10, 64)
	if err != nil {
		return 0
	}
	return n
}

// newID returns a short, random, URL-safe request identifier.
func newID() string {
	var b [8]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "req-unknown"
	}
	return hex.EncodeToString(b[:])
}

// joinPath joins URL path segments, collapsing duplicate slashes at the joins.
func joinPath(parts ...string) string {
	var b strings.Builder
	for _, p := range parts {
		if p == "" {
			continue
		}
		if b.Len() == 0 {
			b.WriteString(p)
			continue
		}
		cur := b.String()
		switch {
		case strings.HasSuffix(cur, "/") && strings.HasPrefix(p, "/"):
			b.WriteString(p[1:])
		case !strings.HasSuffix(cur, "/") && !strings.HasPrefix(p, "/"):
			b.WriteString("/")
			b.WriteString(p)
		default:
			b.WriteString(p)
		}
	}
	if b.Len() == 0 {
		return "/"
	}
	return b.String()
}
