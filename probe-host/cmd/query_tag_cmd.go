package cmd

import (
	"fmt"
	"github.com/spf13/cobra"
	log "probe-host/log_zap"
	"probe-host/transport"
)

// 查询标签信息子命令
var queryTagsCmd = &cobra.Command{
	Use:   "label",
	Short: "User can query the label information of the current device",
	Long:  `User can query the label information of the current device.`,
	Run:   labelCmd,
}

func labelCmd(cmd *cobra.Command, args []string) {
	resp, err := transport.Label()
	if err != nil {
		log.E("query label failed, err is %v", err)
		fmt.Print("查询失败, 请稍后再试!")
		return
	}
	fmt.Printf("%s\n", resp)
}
