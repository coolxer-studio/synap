package probe

import (
	"fmt"
	"net"
)

var netProbe = &NetProbe{}

type NetProbe struct{}

func (n *NetProbe) port(ports []string) []int {
	var res []int

	for _, port := range ports {
		conn, err := net.Listen("tcp", fmt.Sprintf("0.0.0.0:%s", port))
		if err != nil {
			res = append(res, 1)
			continue
		}
		defer conn.Close()
		res = append(res, 0)
	}
	return res
}

func (n *NetProbe) certificate(regexes []string) []string {
	// TODO heyjd golang 目前没有针对读取的证书的地方，所以这里暂时不做处理

	var res []string
	return res
}
