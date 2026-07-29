package cmd

import (
	"fmt"
	log "probe-host/log_zap"
	"probe-host/transport"

	"github.com/spf13/cobra"
)

var ratingCmd = &cobra.Command{
	Use:   "rating",
	Short: "User can query the score level of the current device",
	Long:  `User can query the score level of the current device.`,
	Run:   queryLabelCmd,
}

func queryLabelCmd(cmd *cobra.Command, args []string) {

	resp, err := transport.Rating()
	if err != nil {
		log.E("query label failed, err is %v", err)
		fmt.Print("查询失败, 请稍后再试!")
		return
	}
	fmt.Printf("%s\n", resp)
}
