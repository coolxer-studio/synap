package log

import (
	"fmt"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
	"os"
)

var Log *zap.Logger

func Init() {
	logPath := "./logs"
	_, err := os.Stat(logPath)
	if err != nil {
		if os.IsNotExist(err) {
			err = os.Mkdir(logPath, os.ModePerm)
			if err != nil {
				panic(err)
			}
		} else {
			panic(err)
		}
	}

	logFilePath := logPath + string(os.PathSeparator) + "probe.log"
	_, err = os.Stat(logFilePath)
	if err != nil {
		if os.IsNotExist(err) {
			_, err = os.Create(logFilePath)
			if err != nil {
				panic(err)
			}
		} else {
			panic(err)
		}
	}

	cfg := &zap.Config{
		Level:    zap.NewAtomicLevelAt(zap.DebugLevel),
		Encoding: "json",
		EncoderConfig: zapcore.EncoderConfig{
			MessageKey:     "M",
			LevelKey:       "L",
			TimeKey:        "T",
			CallerKey:      "C",
			EncodeTime:     zapcore.RFC3339TimeEncoder,
			EncodeDuration: nil,
			EncodeCaller:   zapcore.ShortCallerEncoder,
			EncodeLevel:    zapcore.LowercaseLevelEncoder,
		},
		OutputPaths:      []string{logFilePath},
		ErrorOutputPaths: []string{logFilePath},
	}
	Log = zap.Must(cfg.Build(zap.AddCallerSkip(1)))
}

func W(template string, args ...interface{}) {
	if len(args) <= 0 {
		Log.Warn(template)
	} else {
		Log.Warn(fmt.Sprintf(template, args...))
	}
}

func I(template string, args ...interface{}) {
	if len(args) <= 0 {
		Log.Info(template)
	} else {
		Log.Info(fmt.Sprintf(template, args...))
	}
}

func D(template string, args ...interface{}) {
	if len(args) <= 0 {
		Log.Debug(template)
	} else {
		Log.Debug(fmt.Sprintf(template, args...))
	}
}

func E(template string, args ...interface{}) {
	if len(args) <= 0 {
		Log.Error(template)
	} else {
		Log.Error(fmt.Sprintf(template, args...))
	}
}

func Panic(template string, args ...interface{}) {
	if len(args) <= 0 {
		Log.Panic(template)
	} else {
		Log.Panic(fmt.Sprintf(template, args...))
	}
}
