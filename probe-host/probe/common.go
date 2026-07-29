package probe

import (
	"os"
	"path/filepath"
	"probe-host/log_zap"
	"strconv"
	"time"
)

/* 采集common 信息
例子：
{
    "common": [
        "2090E6F6-CE5E-484A-9FA2-77DB008208D4",
        "1684394081000",
        "1.0.0.220519",
        1,
        "测试app",
        "com.abc.test",
        "1.0.1",
        "ios",
        "apple",
        "iphone8.1",
        "ios",
        "13.1",
        "wifi",
        "192.168.12.1",
        "10.22.22.22",
        31.207207207207208,
        107.41724717894603,
        "四川省达州市通川区",
        "2023-03-29 06:00:39"
    ]
}
*/

func GetCommon() []interface{} {
	// 这些是固定值，后续从启动配置里面找
	sdkVersion := "1.231207"
	agentId := "1"
	platform := "host"

	// 注意： 长度固定为24
	common := make([]interface{}, 24, 24)
	common[0] = "userId"
	common[1] = device.guid()
	common[2] = strconv.FormatInt(time.Now().UnixNano()/int64(time.Millisecond), 10)
	common[3] = sdkVersion
	common[4] = agentId
	common[5] = processName()
	common[6] = os.Args[0]

	common[8] = platform
	common[9] = device.manufacturer()
	common[10] = device.model()
	common[11] = device.systemName()
	common[12] = device.systemVersion()
	common[13] = device.netType()
	common[14] = device.lanIp()
	common[15] = device.wanIp()

	region, err := ip2Region()
	if err != nil {
		log.E("ip2Region error: %v", err)
	} else {
		log.D("ip2Region: %v", region)
		common[16] = region.Latitude
		common[17] = region.Longitude
		common[18] = region.CountryCode
		common[19] = region.City
		common[20] = region.City
	}
	// TODO heyjd 待完善指标
	common[21] = ""
	common[22] = ""
	common[23] = time.Now().Format("2006-01-02 15:04:05")
	return common
}

func processName() string {
	executable, err := os.Executable()
	if err != nil {
		panic(err)
	}
	processName := filepath.Base(executable)
	return processName
}

type ipRegion struct {
	Latitude, Longitude float32 // 经纬度
	CountryCode         string  // 国家代码 CN 等
	Region              string
	City                string
}

// ip2Region 通过ip 获取地理位置信息
// 2024-3-3 探针无需进行ip2Region的操作，由服务端进行，因此这里留空
func ip2Region() (ipRegion, error) {
	return ipRegion{}, nil
}
