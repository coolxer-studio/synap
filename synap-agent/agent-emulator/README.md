# synap-agent 消息仿真器

## 构建与测试

```bash
go test ./...
go build -o agent-emulator
```

## 发送

```bash
./agent-emulator --host 192.168.1.201 --device_count 1 --print_log=true
```

发送前会更新 guid、毫秒级 startId 和客户端时间。WeChat start/upload 会同步生成第一版 meta、batchId、messageId、sequence、observedAt；请求头按 `{guid}-{platform}-{appId}-{sdkVersion}` 构造。

## 生成加密样例

无参数运行会读取 `data` 并写入 `data_encryp`：

```bash
./agent-emulator
```

只刷新微信小程序加密样例：

```bash
./agent-emulator --encrypt_platform wechat
```
