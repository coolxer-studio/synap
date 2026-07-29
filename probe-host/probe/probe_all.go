package probe

import "probe-host/model"

// 这里定义所有的data 数据的结构体

type DeviceData struct {
	Type    string   `json:"type"`
	Device  []string `json:"device"`
	Screen  []string `json:"screen"`
	CPU     []string `json:"cpu"`
	Battery []string `json:"battery"`
	UUID    []string `json:"uuid"`
	Network []string `json:"network"`
	Storage []string `json:"storage"`
}

type FileData struct {
	Type     string     `json:"type"`
	Exist    []int      `json:"exist"`
	Context  []string   `json:"context"`
	FileList [][]string `json:"fileList"`
	Access   []string   `json:"access"`
	Md5      []string   `json:"md5"`
	Yara     []string   `json:"yara"`
}

type RuntimeData struct {
	Type    string   `json:"type"`
	Process []string `json:"process"`
	Env     []string `json:"env"`
	Cmd     []string `json:"cmd"`
}

type NetWorkData struct {
	Type        string   `json:"type"`
	Port        []int    `json:"port"`
	Certificate []string `json:"certificate"`
}

type EventData struct {
	Type string     `json:"type"`
	SSH  [][]string `json:"sshd"`
}

// UserData 当用户使用命令自定义上传时使用的
type UserData struct {
	Type      string      `json:"type"`
	Data      interface{} `json:"data"`
	UserId    string      `json:"userId"`
	BuildDate string      `json:"buildDate"`
}

// GetData 每次探测获取所有数据的入口
func GetData(c *model.ProbeCfg) []interface{} {

	// 获取设备信息
	deviceData := &DeviceData{
		Type: "DeviceData",
		Device: []string{
			device.typE(),
			device.name(),
			device.systemName(),
			device.systemVersion(),
			device.buildId(),
			device.model(),
			device.countryCode(),
			device.language(),
			device.timeZone(),
			device.currency(),
			device.startElapsedTime(),
		},
		Screen: []string{
			device.width(),
			device.height(),
			device.brightness(),
			device.density(),
			device.rotation(),
		},
		CPU: []string{
			device.totalCore(),
			device.usedCore(),
			device.cpuType(),
			device.abi(),
		},
		Battery: []string{
			device.capacity(),
			device.charging(),
		},
		UUID: []string{
			device.macEth0(""),
			device.serial(),
			device.bootId(),
			device.guid(),
		},
		Network: []string{
			device.lanIp(),
			device.mask(),
			device.broadcast(),
			device.routing(),
			device.signal(),
			device.netType(),
		},
		Storage: []string{
			device.totalDisk(),
			device.usedDisk(),
			device.totalRam(),
			device.usedRam(),
		},
	}

	fc := c.FileConfig
	fileData := &FileData{
		Type:     "FileData",
		Exist:    fileProbe.exist(fc.Exist),
		Context:  fileProbe.context(fc.Context),
		FileList: fileProbe.fileList(fc.FileList),
		Md5:      fileProbe.fileMd5(fc.Md5),
		Yara:     []string{},
		Access:   fileProbe.access(fc.Access),
	}

	rc := c.RuntimeConfig
	runtimeData := &RuntimeData{
		Type:    "RuntimeData",
		Process: runtimeProbe.matchProcess(rc.Process),
		Env:     runtimeProbe.env(rc.Env),
		Cmd:     runtimeProbe.cmd(rc.Cmd),
	}

	nc := c.NetConfig
	netWorkData := &NetWorkData{
		Type:        "NetData",
		Port:        netProbe.port(nc.Port),
		Certificate: netProbe.certificate(nc.Certificate),
	}

	eventData := &EventData{
		Type: "EventData",
		SSH:  eventProbe.sshd(),
	}

	var res []interface{}
	res = append(res, deviceData, fileData, runtimeData, netWorkData, eventData)
	return res
}

func GetGuid() string {
	return device.guid()
}
