package cmd

import (
	"fmt"
	log "probe-host/log_zap"
	"probe-host/transport"

	"github.com/spf13/cobra"
)

var deviceCmd = &cobra.Command{
	Use:   "device",
	Short: "User can query device info of the current device",
	Long:  `User can query device info of the current device.`,
	Run:   queryDeviceCmd,
}

func queryDeviceCmd(cmd *cobra.Command, args []string) {
	resp, err := transport.Device()
	if err != nil {
		log.E("query label failed, err is %v", err)
		fmt.Print("查询失败, 请稍后再试!")
		return
	}
	fmt.Printf("%s\n", resp)
}
