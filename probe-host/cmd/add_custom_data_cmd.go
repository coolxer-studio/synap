package cmd

import (
	"encoding/json"
	"fmt"
	"github.com/go-resty/resty/v2"
	log "probe-host/log_zap"

	"github.com/spf13/cobra"
)

var argsDataMissingError = "args: -d, --userData 'json string' is missing"
var argsUserIdMissingError = "args: -i, --userId is missing"

var addUserDataCmd = &cobra.Command{
	Use:   "data",
	Short: "User can add custom data by probe tool",
	Long: `A probe tool for collecting device information，
               args: -uId, --userId add custom userId. 
               args: -uData, --userData add custom data by probe tool`,
	Run: addCustomDataCmd,
}

var (
	customData string
	userId     string
)

func addCustomDataCmd(cmd *cobra.Command, args []string) {
	if customData == "" {
		fmt.Println(argsDataMissingError)
		return
	}
	if userId == "" {
		fmt.Println(argsUserIdMissingError)
		return
	}

	p, err := hasStarted()
	if err != nil {
		log.E("check process has started failed, err is %v", err)
		fmt.Println("上传失败，请重试！")
		return
	}
	if p == nil {
		fmt.Printf("上传进程未启动，请首先启动上传进程后，再上传自定义数据\n")
		return
	}

	err = putCustomDataToQueue(userId, customData)
	if err != nil {
		fmt.Printf("err is %v\n", err)
		fmt.Println("上传失败，请重试！")
		return
	}
	// fmt.Println("添加成功！")
}

func init() {
	addUserDataCmd.
		Flags().
		StringVarP(&userId, "userId", "i", "", "add custom userId by probe tool")
	addUserDataCmd.
		Flags().
		StringVarP(&customData, "userData", "d", "", "add custom data by probe tool")

	err := addUserDataCmd.MarkFlagRequired("userData")
	if err != nil {
		fmt.Printf("addUserDataCmd mark flag required failed, err is %v \n", err)
	}
	err = addUserDataCmd.MarkFlagRequired("userId")
	if err != nil {
		fmt.Printf("addUserDataCmd mark flag required failed, err is %v \n", err)
	}
}

// putCustomDataToQueue 将用户数据上传到队列中
func putCustomDataToQueue(userId, customData string) error {
	var data interface{}
	err := json.Unmarshal([]byte(customData), &data)
	if err != nil {
		log.E("JSON decoding error: %v", err)
		return err
	}
	dataMap := map[string]interface{}{
		"userId": userId,
		"data":   data,
	}

	// 写入到文件中
	resp, err := resty.New().R().
		SetHeader("Content-Type", "application/json").
		SetBody(dataMap).
		Post("http://localhost:8080/probe")
	if err != nil {
		return err
	}
	fmt.Printf("resp is %s\n", string(resp.Body()))
	return nil
}
