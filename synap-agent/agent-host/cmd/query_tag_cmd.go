package cmd

import (
	"github.com/spf13/cobra"
	"agent-host/transport"
)

var queryTagsCmd = &cobra.Command{Use: "label", Short: "Query labels for the latest probe session", RunE: func(_ *cobra.Command, _ []string) error { return query(transport.LabelStartPath, true) }}
