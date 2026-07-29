package probe

import (
	"fmt"
	"github.com/distatus/battery"
	"github.com/google/uuid"
	"github.com/mitchellh/go-homedir"
	"github.com/shirou/gopsutil/cpu"
	"github.com/shirou/gopsutil/disk"
	"github.com/shirou/gopsutil/host"
	"github.com/shirou/gopsutil/mem"
	"github.com/shopspring/decimal"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	log "probe-host/log_zap"
	"runtime"
	"strings"
	"time"
)

// 采集设备信息

var device = &DeviceInfo{}

type DeviceInfo struct {
}

// ----- 设备信息 -----
func (d *DeviceInfo) typE() string {
	return ""
}

func (d *DeviceInfo) name() string {
	h, _ := host.Info()
	return h.Hostname
}

func (d *DeviceInfo) systemName() string {
	// 实现system方法
	platform, _, _, err := host.PlatformInformation()
	if err != nil {
		return runtime.GOOS
	}
	return platform
}

func (d *DeviceInfo) model() string {
	return ""
}

func (d *DeviceInfo) systemVersion() string {
	// 实现systemVersion方法
	_, _, version, err := host.PlatformInformation()
	if err != nil {
		return ""
	}
	return version
}

func (d *DeviceInfo) buildId() string {
	return ""
}

func (d *DeviceInfo) countryCode() string {
	ipRegion, err := ip2Region()
	if err != nil {
		return ""
	}
	return ipRegion.CountryCode
}

func (d *DeviceInfo) language() string {
	lang, _ := os.LookupEnv("LANG")
	return lang
}

// timeZone  return the local time zone name, e.g. CST china standard time
// also can return the offset from UTC, e.g. +0800
func (d *DeviceInfo) timeZone() string {
	zone, _ := time.Now().Zone()
	return zone
}

func (d *DeviceInfo) modelVersion() string {
	return ""
}

func (d *DeviceInfo) currency() string {
	return ""
}

func (d *DeviceInfo) startElapsedTime() string {
	uptime, err := host.Uptime()
	if err == nil {
		return fmt.Sprintf("%d", uptime)
	}
	return ""
}

// --- cpu ---
func (d *DeviceInfo) totalCore() string {
	cpuCores, err := cpu.Counts(true)
	if err != nil {
		return ""
	}
	return fmt.Sprintf("%d", cpuCores)
}

func (d *DeviceInfo) usedCore() string {
	r, err := cpu.Percent(time.Second, false)
	if err != nil {
		return ""
	}
	cpuCores, err := cpu.Counts(true)
	if err != nil {
		return ""
	}
	useCore := decimal.NewFromFloat(float64(cpuCores) * r[0] / 100)
	return fmt.Sprintf("%d", useCore.IntPart())
}

func (d *DeviceInfo) cpuType() string {
	cpuInfoList, err := cpu.Info()
	if err != nil {
		return ""
	}
	if len(cpuInfoList) > 0 {
		return cpuInfoList[0].ModelName
	}
	return ""
}

// ---- battery -----
func (d *DeviceInfo) capacity() string {
	batteries, err := battery.GetAll()
	if err != nil {
		return ""
	}
	if len(batteries) <= 0 {
		return ""
	}
	return fmt.Sprintf("%f", batteries[0].Design)
}

func (d *DeviceInfo) charging() string {
	batteries, err := battery.GetAll()
	if err != nil {
		return ""
	}
	if len(batteries) <= 0 {
		return ""
	}
	return fmt.Sprintf("%s", batteries[0].State)
}

// ------ uuid
func (d *DeviceInfo) macEth0(interfaceName string) string {
	if interfaceName == "" {
		interfaceName = "eth0" // default
	}
	interfaces, _ := net.Interfaces()
	for _, i := range interfaces {
		if i.Name == interfaceName {
			return i.HardwareAddr.String()
		}
	}
	return ""
}

// uuid格式字符串，保存在～/.probe_cache/.guid中，如果这个文件中有已经存在的guid就返回这个存在的，没有的话生成新的并且写入到这个文件
func (d *DeviceInfo) guid() string {
	homePath, err := homedir.Dir()
	if err != nil {
		fmt.Printf("Get home path failed: %v\n", err)
		return ""
	}
	dirPath := homePath + "/.probe_cache"
	filePath := filepath.Join(dirPath, ".guid")

	// 创建目录
	err = os.MkdirAll(dirPath, os.ModePerm)
	if err != nil {
		fmt.Printf("创建目录出错: %v\n", err)
		return ""
	}
	// 尝试读取文件内容
	guidBytes, err := os.ReadFile(filePath)
	if err == nil {
		guidStr := strings.TrimSpace(string(guidBytes))
		if _, err := uuid.Parse(guidStr); err == nil {
			// 如果文件中已存在合法的 GUID，直接返回
			return guidStr
		}
	}

	// 生成新的 GUID
	newGUID := uuid.New().String()

	// 保存到文件
	err = os.WriteFile(filePath, []byte(newGUID), 0644)
	if err != nil {
		return ""
	}

	return newGUID
}

// ------- network

/*
参考链接：https://juejin.cn/s/golang%20get%20broadcast%20address
*/

func (d *DeviceInfo) lanIp() string {
	// 实现lanIp方法
	netInterfaces, err := net.Interfaces()
	if err != nil {
		fmt.Println("net.Interfaces failed, err:", err.Error())
		return ""
	}

	for i := 0; i < len(netInterfaces); i++ {
		if (netInterfaces[i].Flags & net.FlagUp) != 0 {
			addrs, _ := netInterfaces[i].Addrs()

			for _, address := range addrs {
				if ipnet, ok := address.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
					if ipnet.IP.To4() != nil {
						return ipnet.IP.String()
					}
				}
			}
		}
	}
	return ""
}

// wanIp 外网ip
func (d *DeviceInfo) wanIp() string {
	//resp, err := http.Get("http://myexternalip.com/raw")
	resp, err := http.Get("https://api.ipify.org")
	if err != nil {
		return ""
	}
	defer resp.Body.Close()
	content, _ := io.ReadAll(resp.Body)
	return string(content)
}

func (d *DeviceInfo) mask() string {

	return ""
}

// broadcast 广播地址
func (d *DeviceInfo) broadcast() string {
	ifaces, err := net.Interfaces()
	if err != nil {
		log.E("net.Interfaces failed, err: %v", err.Error())
		return ""
	}

	for _, iface := range ifaces {
		addrs, err := iface.Addrs()
		if err != nil {
			fmt.Println(err)
			continue
		}

		for _, addr := range addrs {
			if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() {
				if ipnet.IP.To4() != nil {
					netmask := net.IP(make([]byte, 4))
					copy(netmask, ipnet.IP)
					for i := range netmask {
						netmask[i] = netmask[i] | ipnet.Mask[i]
					}
					// 这里截取后4个字节
					broadcastIp := net.IP(make([]byte, 4))
					netmask = netmask[len(netmask)-4:]
					copy(broadcastIp, ipnet.IP[len(ipnet.IP)-4:])
					for i := range broadcastIp {
						broadcastIp[i] = broadcastIp[i] | ^netmask[i]
					}
					return netmask.String()
				}
			}
		}
	}
	return ""
}

func (d *DeviceInfo) routing() string {
	return ""
}

func (d *DeviceInfo) signal() string {
	return ""
}

func (d *DeviceInfo) netType() string {
	// 实现netType方法
	return ""
}

// ----- storge ------
func (d *DeviceInfo) totalDisk() string {
	diskInfo, err := disk.Usage("/")
	if err != nil {
		return ""
	}
	return fmt.Sprintf("%d", diskInfo.Total)
}

func (d *DeviceInfo) usedDisk() string {
	diskInfo, err := disk.Usage("/")
	if err != nil {
		return ""
	}
	return fmt.Sprintf("%d", diskInfo.Used)
}

func (d *DeviceInfo) totalRam() string {
	memory, err := mem.VirtualMemory()
	if err != nil {
		return ""
	}
	return fmt.Sprintf("%d", memory.Total)
}

func (d *DeviceInfo) usedRam() string {
	memory, err := mem.VirtualMemory()
	if err != nil {
		return ""
	}
	return fmt.Sprintf("%d", memory.Used)
}
