package probe

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"agent-host/model"
	"github.com/google/uuid"
)

const SDKVersion = "2.0.0"

type SessionOptions struct {
	AppID      int
	AppName    string
	AppPackage string
	AppVersion string
	UserID     string
	WANIP      string
}

func NewSession(options SessionOptions, now time.Time) model.Session {
	guid := GetGuid()
	startID := now.UnixMilli()
	common := make([]string, 24)
	common[0] = options.UserID
	common[1] = guid
	common[2] = strconv.FormatInt(startID, 10)
	common[3] = SDKVersion
	common[4] = strconv.Itoa(options.AppID)
	common[5] = options.AppName
	common[6] = options.AppPackage
	common[7] = options.AppVersion
	common[8] = "host"
	common[9] = device.manufacturer()
	common[10] = device.model()
	common[11] = device.systemName()
	common[12] = device.systemVersion()
	common[13] = device.netType()
	common[14] = device.lanIp()
	common[15] = options.WANIP
	common[16] = "0"
	common[17] = "0"
	common[23] = now.Format("2006-01-02 15:04:05")
	return model.Session{
		Code:   fmt.Sprintf("%s-host-%d-%s", guid, options.AppID, SDKVersion),
		Common: common, StartID: startID, PID: os.Getpid(), StartedAt: now.UnixMilli(),
		StartMeta: model.RequestMeta{
			ProtocolVersion: 1, AppKeyID: strconv.Itoa(options.AppID), DetectorVersion: SDKVersion,
			MessageID: "start-msg-" + uuid.NewString(), BatchID: "start-batch-" + uuid.NewString(),
			SessionID: strconv.FormatInt(startID, 10), CreatedAt: now.UnixMilli(),
			ObservedAt: now.UnixMilli(), Sequence: 1,
		},
	}
}

func GetCommon() []string {
	return NewSession(SessionOptions{AppID: 1, AppName: "x-probe", AppPackage: "agent-host", AppVersion: SDKVersion}, time.Now()).Common
}

func processName() string {
	executable, err := os.Executable()
	if err != nil {
		return "x-probe"
	}
	return filepath.Base(executable)
}
