package log

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

var (
	Log  = zap.NewNop()
	lock sync.Mutex
)

func Init(dataDir, level string) error {
	lock.Lock()
	defer lock.Unlock()
	logDir := filepath.Join(dataDir, "logs")
	if err := os.MkdirAll(logDir, 0o700); err != nil {
		return err
	}
	logFile := filepath.Join(logDir, "probe.log")
	rotate(logFile, 10<<20)
	parsedLevel := zapcore.InfoLevel
	if err := parsedLevel.Set(strings.ToLower(level)); err != nil {
		return fmt.Errorf("invalid log level: %w", err)
	}
	cfg := zap.Config{
		Level:    zap.NewAtomicLevelAt(parsedLevel),
		Encoding: "json",
		EncoderConfig: zapcore.EncoderConfig{
			MessageKey: "message", LevelKey: "level", TimeKey: "time", CallerKey: "caller",
			EncodeTime: zapcore.RFC3339TimeEncoder, EncodeCaller: zapcore.ShortCallerEncoder,
			EncodeLevel: zapcore.LowercaseLevelEncoder,
		},
		OutputPaths: []string{logFile}, ErrorOutputPaths: []string{logFile},
	}
	logger, err := cfg.Build(zap.AddCallerSkip(1))
	if err != nil {
		return err
	}
	_ = Log.Sync()
	Log = logger
	return nil
}

func rotate(path string, max int64) {
	info, err := os.Stat(path)
	if err != nil || info.Size() < max {
		return
	}
	_ = os.Remove(path + ".1")
	_ = os.Rename(path, path+".1")
}

func Close()                                     { _ = Log.Sync() }
func W(template string, args ...interface{})     { Log.Warn(format(template, args...)) }
func I(template string, args ...interface{})     { Log.Info(format(template, args...)) }
func D(template string, args ...interface{})     { Log.Debug(format(template, args...)) }
func E(template string, args ...interface{})     { Log.Error(format(template, args...)) }
func Panic(template string, args ...interface{}) { Log.Panic(format(template, args...)) }

func format(template string, args ...interface{}) string {
	if len(args) == 0 {
		return template
	}
	return fmt.Sprintf(template, args...)
}
