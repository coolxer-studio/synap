package cmd

import (
	"github.com/spf13/cobra"
	"agent-host/transport"
)

var ratingCmd = &cobra.Command{Use: "rating", Short: "Query the current device risk rating", RunE: func(_ *cobra.Command, _ []string) error { return query(transport.RatingPath, false) }}
