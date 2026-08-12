package model

type ProbeCfg struct {
	PolicyVersion string        `json:"policyVersion,omitempty"`
	StartConfig   []float64     `json:"startConfig"` //是否执行采集、采集频率、采集窗口、上送频率、上送窗口
	DeviceConfig  DeviceConfig  `json:"deviceConfig"`
	FileConfig    FileConfig    `json:"fileConfig"`
	RuntimeConfig RuntimeConfig `json:"runtimeConfig"`
	NetConfig     NetConfig     `json:"netConfig"`
	EventConfig   EventConfig   `json:"eventConfig"`
	AuditConfig   AuditConfig   `json:"auditConfig"`
	AuditPolicy   AuditPolicy   `json:"auditPolicy"`
}

// AuditConfig describes the fixed positional AuditData payload. The server
// publishes the same column list so older Host fact mapping remains untouched.
type AuditConfig struct {
	Events []string `json:"events"`
}

type AuditPolicy struct {
	Enabled                bool             `json:"enabled"`
	Categories             map[string]bool  `json:"categories"`
	PollIntervalSeconds    int              `json:"pollIntervalSeconds"`
	ProcessSnapshotSeconds int              `json:"processSnapshotSeconds"`
	DigestMaxBytes         int64            `json:"digestMaxBytes"`
	BatchMaxEvents         int              `json:"batchMaxEvents"`
	BatchMaxBytes          int              `json:"batchMaxBytes"`
	CriticalFiles          []string         `json:"criticalFiles"`
	CriticalFilesMode      string           `json:"criticalFilesMode"`
	BusinessDirectories    []AuditDirectory `json:"businessDirectories"`
	ExcludeProcesses       []string         `json:"excludeProcesses"`
	ExcludeCIDRs           []string         `json:"excludeCidrs"`
	ExcludePorts           []int            `json:"excludePorts"`
}

type AuditDirectory struct {
	Path           string   `json:"path"`
	Recursive      bool     `json:"recursive"`
	Exclude        []string `json:"exclude"`
	FollowSymlinks bool     `json:"followSymlinks"`
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
	Yara     []string `json:"yara"`
}

type RuntimeConfig struct {
	Process []string `json:"process"`
	Env     []string `json:"env"`
	Cmd     []string `json:"cmd"`
}

type NetConfig struct {
	Port         []string `json:"port"`
	Socket       []string `json:"socket"`
	UDS          []string `json:"uds"`
	NetInterface []string `json:"netInterface"`
	HTTPProxy    []string `json:"httpProxy"`
	Hosts        []string `json:"hosts"`
	Certificate  []string `json:"certificate"`
}

type EventConfig struct {
	Sshd []string `json:"sshd"`
}
