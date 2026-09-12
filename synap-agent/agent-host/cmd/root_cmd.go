package cmd

import (
	"context"
	"errors"
	"fmt"
	"math/rand"
	"os"
	"os/signal"
	"syscall"
	"time"

	"agent-host/appconfig"
	log "agent-host/log_zap"
	"agent-host/model"
	"agent-host/probe"
	"agent-host/storage"
	"agent-host/transport"
	"github.com/spf13/cobra"
)

var options = appconfig.Defaults(transport.EmbeddedHost())

var rootCmd = &cobra.Command{
	Use: "x-probe", Short: "Synap agent-host security probe", Version: probe.SDKVersion,
	SilenceUsage: true, SilenceErrors: true, RunE: runRoot,
}

func init() {
	flags := rootCmd.PersistentFlags()
	flags.StringVar(&options.ServerBaseURL, "server", options.ServerBaseURL, "synap-server origin")
	flags.StringVar(&options.PublicIPURL, "public-ip-url", options.PublicIPURL, "public IP lookup endpoint")
	flags.IntVar(&options.AppID, "app-id", options.AppID, "application id")
	flags.StringVar(&options.AppName, "app-name", options.AppName, "application name")
	flags.StringVar(&options.AppPackage, "app-package", options.AppPackage, "application package")
	flags.StringVar(&options.AppVersion, "app-version", options.AppVersion, "application version")
	flags.StringVar(&options.UserID, "user-id", options.UserID, "default user id")
	flags.StringVar(&options.DataDir, "data-dir", options.DataDir, "state, queue and log directory")
	flags.StringVar(&options.LogLevel, "log-level", options.LogLevel, "debug, info, warn or error")
	flags.Int64Var(&options.SpoolMaxBytes, "spool-max-bytes", options.SpoolMaxBytes, "maximum queued bytes")
	flags.IntVar(&options.SpoolMaxItems, "spool-max-items", options.SpoolMaxItems, "maximum queued records")
}

func Execute() error {
	rootCmd.AddCommand(addUserDataCmd, queryTagsCmd, ratingCmd, deviceCmd)
	return rootCmd.Execute()
}

func prepare() (*storage.Spool, error) {
	if err := options.Validate(); err != nil {
		return nil, err
	}
	probe.ConfigureDataDir(options.DataDir)
	if err := log.Init(options.DataDir, options.LogLevel); err != nil {
		return nil, err
	}
	return storage.New(options.DataDir, options.SpoolMaxBytes, options.SpoolMaxItems)
}

func runRoot(_ *cobra.Command, _ []string) error {
	spool, err := prepare()
	if err != nil {
		return err
	}
	defer log.Close()
	processLock, err := storage.AcquireProcessLock(options.DataDir)
	if err != nil {
		if errors.Is(err, storage.ErrAlreadyRunning) {
			return err
		}
		return fmt.Errorf("acquire process lock: %w", err)
	}
	defer processLock.Release()
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	publicIP := probe.ResolvePublicIP(ctx, options.PublicIPURL)
	if publicIP == "" {
		log.W("public IP lookup unavailable; server source address will be used")
	}
	session := probe.NewSession(probe.SessionOptions{AppID: options.AppID, AppName: options.AppName, AppPackage: options.AppPackage, AppVersion: options.AppVersion, UserID: options.UserID, WANIP: publicIP}, time.Now())
	transportConfig := transport.RuntimeConfig(options.ServerBaseURL, options.DataDir)
	if transportConfig.AppID != "" {
		session.StartMeta.AppKeyID = transportConfig.AppID
	}
	if err := spool.SaveSession(session); err != nil {
		return fmt.Errorf("save session: %w", err)
	}
	client := transport.New(transportConfig)
	config, err := waitForStart(ctx, client, session)
	if err != nil {
		return err
	}
	fmt.Printf("探针已启动，进程ID: %d\n", os.Getpid())
	return StartProbe(ctx, &config, session, client, spool)
}

func waitForStart(ctx context.Context, client *transport.Client, session model.Session) (model.ProbeCfg, error) {
	delay := time.Second
	for {
		response, err := client.Start(&session)
		if err == nil {
			notify(response)
			return response.Config, nil
		}
		if !transport.IsRetryable(err) {
			return model.ProbeCfg{}, err
		}
		log.W("start request failed; retrying: %v", err)
		jitter := time.Duration(rand.Int63n(int64(delay/2 + 1)))
		select {
		case <-ctx.Done():
			return model.ProbeCfg{}, ctx.Err()
		case <-time.After(delay + jitter):
		}
		if delay < time.Minute {
			delay *= 2
		}
		if delay > time.Minute {
			delay = time.Minute
		}
	}
}
