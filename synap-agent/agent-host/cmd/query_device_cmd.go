package cmd

import (
	"context"
	"fmt"
	"time"

	"agent-host/probe"
	"agent-host/storage"
	"agent-host/transport"
	"github.com/spf13/cobra"
)

var deviceCmd = &cobra.Command{Use: "device", Short: "Query current device information", RunE: func(_ *cobra.Command, _ []string) error { return query(transport.InfoPath, false) }}

func query(path string, preferLast bool) error {
	spool, err := prepare()
	if err != nil {
		return err
	}
	session, loadErr := spool.LoadSession()
	if loadErr != nil || (!preferLast && !storage.SessionActive(options.DataDir, session)) {
		publicIP := probe.ResolvePublicIP(context.Background(), options.PublicIPURL)
		session = probe.NewSession(probe.SessionOptions{AppID: options.AppID, AppName: options.AppName, AppPackage: options.AppPackage, AppVersion: options.AppVersion, UserID: options.UserID, WANIP: publicIP}, time.Now())
	}
	response, err := transport.New(
		transport.RuntimeConfig(options.ServerBaseURL, options.DataDir)).Cross(path, session)
	if err != nil {
		return err
	}
	fmt.Println(response)
	return nil
}
