package probe

import (
	"agent-host/model"
	"regexp"
	"strings"
	"time"
)

type DeviceData struct {
	Type      string   `json:"type"`
	BuildDate string   `json:"buildDate"`
	Device    []string `json:"device"`
	Screen    []string `json:"screen"`
	CPU       []string `json:"cpu"`
	Battery   []string `json:"battery"`
	UUID      []string `json:"uuid"`
	Network   []string `json:"network"`
	Storage   []string `json:"storage"`
}
type FileData struct {
	Type      string     `json:"type"`
	BuildDate string     `json:"buildDate"`
	Exist     []int      `json:"exist"`
	Context   []string   `json:"context"`
	FileList  [][]string `json:"fileList"`
	Access    []int      `json:"access"`
	Md5       []string   `json:"md5"`
	Yara      []string   `json:"yara"`
}
type RuntimeData struct {
	Type      string   `json:"type"`
	BuildDate string   `json:"buildDate"`
	Process   []string `json:"process"`
	Env       []string `json:"env"`
	Cmd       []string `json:"cmd"`
}
type NetWorkData struct {
	Type         string   `json:"type"`
	BuildDate    string   `json:"buildDate"`
	Port         []int    `json:"port"`
	Socket       []int    `json:"socket"`
	UDS          []int    `json:"uds"`
	NetInterface []string `json:"netInterface"`
	HTTPProxy    []string `json:"httpProxy"`
	Hosts        []string `json:"hosts"`
	Certificate  []string `json:"certificate"`
}
type EventData struct {
	Type      string     `json:"type"`
	BuildDate string     `json:"buildDate"`
	SSH       [][]string `json:"sshd"`
}
type UserData struct {
	Type      string      `json:"type"`
	Data      interface{} `json:"data"`
	UserId    string      `json:"userId"`
	BuildDate string      `json:"buildDate"`
}

func GetData(config *model.ProbeCfg) []interface{} {
	now := time.Now().Format("2006-01-02 15:04:05")
	deviceConfig := config.DeviceConfig
	deviceData := &DeviceData{Type: "DeviceData", BuildDate: now,
		Device: deviceValues("device", deviceConfig.Device), Screen: deviceValues("screen", deviceConfig.Screen),
		CPU: deviceValues("cpu", deviceConfig.CPU), Battery: deviceValues("battery", deviceConfig.Battery),
		UUID: deviceValues("uuid", deviceConfig.UUID), Network: deviceValues("network", deviceConfig.Network),
		Storage: deviceValues("storage", deviceConfig.Storage),
	}
	files := config.FileConfig
	fileData := &FileData{Type: "FileData", BuildDate: now, Exist: fileProbe.exist(files.Exist), Context: fileProbe.context(files.Context), FileList: fileProbe.fileList(files.FileList), Access: fileProbe.access(files.Access), Md5: fileProbe.fileMd5(files.Md5), Yara: fileProbe.yara(files.Yara)}
	runtimeConfig := config.RuntimeConfig
	runtimeData := &RuntimeData{Type: "RuntimeData", BuildDate: now, Process: runtimeProbe.matchProcess(runtimeConfig.Process), Env: runtimeProbe.env(runtimeConfig.Env), Cmd: runtimeProbe.cmd(runtimeConfig.Cmd)}
	network := config.NetConfig
	networkData := &NetWorkData{Type: "NetData", BuildDate: now, Port: netProbe.port(network.Port), Socket: netProbe.port(network.Socket), UDS: netProbe.uds(network.UDS), NetInterface: netProbe.interfaces(network.NetInterface), HTTPProxy: netProbe.proxies(network.HTTPProxy), Hosts: netProbe.hosts(network.Hosts), Certificate: netProbe.certificate(network.Certificate)}
	var sshEvents [][]string
	if len(config.EventConfig.Sshd) > 0 {
		sshEvents = filterSSHEvents(eventProbe.sshd(), config.EventConfig.Sshd)
	}
	eventData := &EventData{Type: "EventData", BuildDate: now, SSH: sshEvents}
	return []interface{}{deviceData, fileData, runtimeData, networkData, eventData}
}

func deviceValues(group string, keys []string) []string {
	result := make([]string, len(keys))
	for index, key := range keys {
		switch group + "." + key {
		case "device.type":
			result[index] = device.typE()
		case "device.name":
			result[index] = device.name()
		case "device.systemName":
			result[index] = device.systemName()
		case "device.systemVersion":
			result[index] = device.systemVersion()
		case "device.buildId":
			result[index] = device.buildId()
		case "device.modelCode":
			result[index] = device.model()
		case "device.countryCode":
			result[index] = device.countryCode()
		case "device.language":
			result[index] = device.language()
		case "device.timeZone":
			result[index] = device.timeZone()
		case "device.currency":
			result[index] = device.currency()
		case "device.startElapsedTime":
			result[index] = device.startElapsedTime()
		case "screen.width":
			result[index] = device.width()
		case "screen.height":
			result[index] = device.height()
		case "screen.brightness":
			result[index] = device.brightness()
		case "screen.density":
			result[index] = device.density()
		case "screen.rotation":
			result[index] = device.rotation()
		case "cpu.totalCore":
			result[index] = device.totalCore()
		case "cpu.usedCore":
			result[index] = device.usedCore()
		case "cpu.type":
			result[index] = device.cpuType()
		case "cpu.abi":
			result[index] = device.abi()
		case "battery.capacity":
			result[index] = device.capacity()
		case "battery.charging":
			result[index] = device.charging()
		case "uuid.macEth0":
			result[index] = device.macEth0("")
		case "uuid.serial":
			result[index] = device.serial()
		case "uuid.bootId":
			result[index] = device.bootId()
		case "uuid.guid":
			result[index] = device.guid()
		case "network.lanIp":
			result[index] = device.lanIp()
		case "network.mask":
			result[index] = device.mask()
		case "network.broadcast":
			result[index] = device.broadcast()
		case "network.routing":
			result[index] = device.routing()
		case "network.signal":
			result[index] = device.signal()
		case "network.type":
			result[index] = device.netType()
		case "storage.totalDisk":
			result[index] = device.totalDisk()
		case "storage.usedDisk":
			result[index] = device.usedDisk()
		case "storage.totalRam":
			result[index] = device.totalRam()
		case "storage.usedRam":
			result[index] = device.usedRam()
		}
	}
	return result
}

func filterSSHEvents(events [][]string, expressions []string) [][]string {
	result := make([][]string, 0)
	for _, expression := range expressions {
		compiled, err := regexp.Compile(expression)
		if err != nil {
			continue
		}
		for _, event := range events {
			if compiled.MatchString(strings.Join(event, " ")) {
				result = append(result, event)
				if len(result) >= maxListItems {
					return result
				}
			}
		}
	}
	return result
}
