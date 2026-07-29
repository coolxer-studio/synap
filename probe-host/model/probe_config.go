package model

type ProbeCfg struct {
	StartConfig   []float64     `json:"startConfig"` //是否执行采集、采集频率、采集窗口、上送频率、上送窗口
	DeviceConfig  DeviceConfig  `json:"deviceConfig"`
	FileConfig    FileConfig    `json:"fileConfig"`
	RuntimeConfig RuntimeConfig `json:"runtimeConfig"`
	NetConfig     NetConfig     `json:"netConfig"`
	EventConfig   EventConfig   `json:"eventConfig"`
}

type DeviceConfig struct {
	Device  []string `json:"device"`
	Screen  []string `json:"screen"`
	CPU     []string `json:"cpu"`
	Battery []string `json:"battery"`
	UUID    []string `json:"uuid"`
	Network []string `json:"network"`
	Storage []string `json:"storage"`
}

type FileConfig struct {
	Exist    []string `json:"exist"`
	Context  []string `json:"context"`
	FileList []string `json:"fileList"`
	Md5      []string `json:"md5"`
	Access   []string `json:"access"`
}

type RuntimeConfig struct {
	Process []string `json:"process"`
	Env     []string `json:"env"`
	Cmd     []string `json:"cmd"`
}

type NetConfig struct {
	Port        []string `json:"port"`
	Certificate []string `json:"certificate"`
}

type EventConfig struct {
	Sshd []string `json:"sshd"`
}
