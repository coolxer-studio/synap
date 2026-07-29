package cmd

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	log "probe-host/log_zap"
	"probe-host/model"
	"probe-host/probe"
	"probe-host/transport"
	"strings"
	"time"

	"github.com/shirou/gopsutil/process"
)

// 用于缓存采集数据
var queue = make(chan interface{}, 999)

// StartProbe 核心启动探测逻辑
// TODO heyjd 待测试
func StartProbe(ctx context.Context, c *model.ProbeCfg) error {
	if c == nil {
		return errors.New("probe config is nil")
	}

	// 是否执行采集、采集频率、采集窗口、上送频率、上送窗口
	startCfg := c.StartConfig
	if startCfg[0] != 1 {
		return errors.New("no need to start probe")
	}

	// 采集
	endProbeTimer := time.NewTimer(time.Duration(startCfg[2]) * time.Second)
	probeIntervalTicker := time.NewTicker(time.Duration(startCfg[1]) * time.Second)
	go func(ctx context.Context) {
		for {
			select {
			case <-probeIntervalTicker.C:
				data := probeOnce(c)
				if data != "" {
					queue <- data
				}
				log.I("probe once data: %v", data)
			case <-endProbeTimer.C:
				goto END
			case <-ctx.Done():
				goto END
			}
		}
	END:
		// 结束采集
		probeIntervalTicker.Stop()
		endProbeTimer.Stop()
		log.I("probe goroutine end...")
		fmt.Println("probe goroutine end...")
	}(ctx)

	// 上送
	endUploadTimer := time.NewTimer(time.Duration(startCfg[4]) * time.Second)
	uploadIntervalTicker := time.NewTicker(time.Duration(startCfg[3]) * time.Second)
	go func(ctx context.Context) {
		for {
			select {
			case <-uploadIntervalTicker.C:
				for {
					select {
					case msg := <-queue:
						uploadDataOnce(msg)
						// 只为打印日志
						b, _ := json.Marshal(msg)
						log.I("upload once data: %s", string(b))
					default:
						goto EndUpload
					}
				}
			EndUpload:
				fmt.Println("stop once")
			case <-endUploadTimer.C:
				goto END
			case <-ctx.Done():
				goto END
			}
		}
	END:
		endUploadTimer.Stop()
		uploadIntervalTicker.Stop()
		log.I("upload goroutine end...")
		fmt.Println("upload goroutine end...")
	}(ctx)

	return nil
}

// probe 核心采集方法，采集一次
func probeOnce(c *model.ProbeCfg) interface{} {
	log.I("begin probe once")
	data := probe.GetData(c)
	return data
}

func uploadDataOnce(data interface{}) {
	log.D("begin upload data :%+v", data)
	resp, err := transport.Upload(data)
	if err != nil {
		log.E("upload data error:%v", err)
		return
	}
	log.D("upload data resp:%v", resp)
}

// hasStarted 校验进程是否已经存在
func hasStarted() (*process.Process, error) {
	currentPid := os.Getpid()
	processes, err := process.Processes()
	if err != nil {
		return nil, err
	}
	for _, p := range processes {
		name, _ := p.Name()
		if p.Pid != int32(currentPid) && strings.Contains(name, processName) {
			return p, nil
		}
	}
	return nil, nil
}

func uploadCustomData(w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(r.Body)
	if err != nil {
		log.E("read body failed, err: %v", err)
		_, err := w.Write([]byte("failed"))
		if err != nil {
			log.E("write failed failed, err: %v", err)
		}
		return
	}
	var ucd userCustomData
	err = json.Unmarshal(body, &ucd)
	if err != nil {
		log.E("JSON decoding error: %v", err)
		_, err := w.Write([]byte("failed"))
		if err != nil {
			log.E("write failed failed, err: %v", err)
		}
		return
	}
	userData := &probe.UserData{
		Type:      "UserData",
		Data:      ucd.Data,
		UserId:    ucd.UserId,
		BuildDate: time.Now().Format("2006-01-02 15:04:05"),
	}

	queue <- []interface{}{userData}
	_, err = w.Write([]byte("success"))
	if err != nil {
		log.E("write failed failed, err: %v", err)
	}
}

type userCustomData struct {
	UserId string      `json:"userId"`
	Data   interface{} `json:"data"`
}
