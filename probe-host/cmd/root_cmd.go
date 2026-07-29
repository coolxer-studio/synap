package cmd

// root cmd for probe
import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/spf13/cobra"
	"net/http"
	"os"
	"os/signal"
	log "probe-host/log_zap"
	"probe-host/model"
	"probe-host/transport"
	"syscall"
)

// 进程名称
var processName = "x-probe"

var rootCmd = &cobra.Command{
	Use:   "probe",
	Short: "Probe is a probe tool for collecting device information.",
	Long: `A probe tool for collecting device information，
                it's can run on any device, including windows、macOS、linux-like OS'.`,
	Run:     rootCmdRun,
	Version: "1.0.0",
}

// startKey 启动探针所需要的key，后续用于认证、加密等
// fixme 这里后续打包的时候封装到对应的sdk中
var startKey string

func init() {
	//rootCmd.
	//	Flags().
	//	StringVarP(&startKey, "key", "k", "", "probe key")

	log.Init()
}

func Execute() {
	rootCmd.AddCommand(
		addUserDataCmd,
		queryTagsCmd,
		ratingCmd,
		deviceCmd,
	)

	if err := rootCmd.Execute(); err != nil {
		fmt.Println(err)
		os.Exit(1)
	}
}

// rootCmdRun is the main entry point for the probe command
func rootCmdRun(cmd *cobra.Command, args []string) {
	p, err := hasStarted()
	if err != nil {
		fmt.Println("启动失败, 请重试...")
		return
	}
	if p != nil {
		fmt.Printf("探针程序已启动，进程ID: %d\n", p.Pid)
		return
	}
	fmt.Println("开始启动...")
	startKernel()
}

func startKernel() {
	resp, err := transport.Start()
	if err != nil {
		panic(err)
	}
	startResp := model.Resp{}
	err = json.Unmarshal([]byte(resp), &startResp)
	if err != nil {
		log.E("JSON decoding error: %v", err)
		return
	}
	log.I("start probe config: %v", startResp.Config)

	ctx, cancel := context.WithCancel(context.Background())
	go func(ctx context.Context) {
		err := StartProbe(ctx, &startResp.Config)
		if err != nil {
			panic("start probe error")
		}

		log.I("is init probe:true!")
		fmt.Println("is init probe:true!")
	}(ctx)

	// 启动http 服务, 主要通过该进程上传自定义数据
	http.HandleFunc("/probe", uploadCustomData)
	server := &http.Server{Addr: "localhost:8080", Handler: nil}
	go func(ctx context.Context) {
		err := server.ListenAndServe()
		if err != nil {
			if !errors.Is(err, http.ErrServerClosed) {
				fmt.Printf("probe http server err is %v\n", err)
			}
		}
	}(ctx)

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	cancel()

	err = server.Shutdown(context.Background())
	if err != nil {
		fmt.Println("shutdown http server error")
	}
	fmt.Println("shutdown server")
	log.E("shutdown server")

	log.I("server exiting...")
	fmt.Println("server exiting...")
}
