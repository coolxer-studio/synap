package main

import (
	"io"
	"os"
	"sync"
)

// Sink receives fully formatted, ready-to-ship records. Write must be safe for
// concurrent use and should not block the proxy for long.
type Sink interface {
	// Write emits one record. key may be empty; value is compact JSON.
	Write(key string, value []byte) error
	// Close flushes and releases resources.
	Close() error
}

// nopSink discards everything.
type nopSink struct{}

func (nopSink) Write(string, []byte) error { return nil }
func (nopSink) Close() error               { return nil }

// lineSink writes JSON lines to an io.Writer (stdout or a file).
type lineSink struct {
	mu sync.Mutex
	w  io.Writer
}

func newLineSink(w io.Writer) *lineSink { return &lineSink{w: w} }

func (s *lineSink) Write(_ string, value []byte) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if _, err := s.w.Write(value); err != nil {
		return err
	}
	_, err := s.w.Write([]byte{'\n'})
	return err
}

func (s *lineSink) Close() error {
	if c, ok := s.w.(io.Closer); ok {
		return c.Close()
	}
	return nil
}

// fileSink appends JSON lines to a file opened in append mode.
func newFileSink(path string) (*lineSink, error) {
	f, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	if err != nil {
		return nil, err
	}
	return newLineSink(f), nil
}

// multiSink fans a record out to several sinks. It keeps going after an error
// and returns the first one encountered.
type multiSink struct {
	sinks []Sink
}

func newMultiSink(sinks ...Sink) *multiSink {
	filtered := make([]Sink, 0, len(sinks))
	for _, s := range sinks {
		if s != nil {
			filtered = append(filtered, s)
		}
	}
	return &multiSink{sinks: filtered}
}

func (m *multiSink) Write(key string, value []byte) error {
	var firstErr error
	for _, s := range m.sinks {
		if err := s.Write(key, value); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}

func (m *multiSink) Close() error {
	var firstErr error
	for _, s := range m.sinks {
		if err := s.Close(); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}
