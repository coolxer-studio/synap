package transport

import (
	"encoding/json"
	"fmt"
	log "probe-host/log_zap"
	"probe-host/probe"
	"time"

	"github.com/go-resty/resty/v2"
)

var client = resty.New().SetTimeout(10 * time.Second)

var host = "http://39.106.54.18:11099"

var (
	startUrl  = "/1/1" // 启动接口
	uploadUrl = "/1/2" // 上传数据
	ratingUrl = "/2/6" // 查询评级指数
	labelUrl  = "/2/5" // 查询标签接口
	deviceUrl = "/2/1" // 查询设备信息
)

func httpPost(url string, data interface{}) (string, error) {
	payloadMap := map[string]interface{}{"common": probe.GetCommon(), "data": data}

	s, _ := json.Marshal(payloadMap)
	resp, err := client.R().
		SetHeader("Content-Type", "application/json").
		SetHeader("code", getHeader()).
		SetBody(payloadMap).
		Post(url)

	log.D("req path: %s, payloadMap: %s, resp is %s", url, string(s), string(resp.Body()))
	if err != nil {
		return "", err
	}
	return string(resp.Body()), nil
}

func Start() (string, error) {
	return httpPost(host+startUrl, nil)
}

func Upload(data interface{}) (string, error) {
	return httpPost(host+uploadUrl, data)
}

func Rating() (string, error) {
	return httpPost(host+ratingUrl, nil)
}

func Label() (string, error) {
	return httpPost(host+labelUrl, nil)
}

func Device() (string, error) {
	return httpPost(host+deviceUrl, nil)
}

var HEADER string

func getHeader() string {
	if HEADER == "" {
		HEADER = fmt.Sprintf("%s-%s-%s-%s",
			probe.GetGuid(), "host", "1", "1.231207")
	}
	return HEADER
}
