# synap-agent

Synap 客户端安全探针与数据仿真工具集。

## 模块

| 模块 | 用途 | 主要产物 |
| --- | --- | --- |
| `agent-h5` | 浏览器/WebView 安全探针 | `dist/probe.min.js` |
| `agent-miniprogram` | 微信小程序保留源码（协议 v1 首发禁用） | 不生成生产产物 |
| `agent-host` | Linux、Windows、macOS 主机安全与审计探针 | `x-probe` / `x-probe.exe` |
| `agent-springboot` | 服务端 Spring Boot API 资产与运行时安全探针 | Starter / shaded Java Agent |
| `agent-emulator` | Android、iOS、H5、WeChat、Host 报文仿真 | Go 可执行文件 |

H5、Host、Emulator、Android 和 iOS 统一接入安全通信协议 v1：TLS 1.2+ 之上使用
`P-256 + HKDF-SHA256 + AES-256-GCM`，业务 method、逻辑 path、受保护 header
和正文均位于密文内。Agent 保留“至少一次传输 + messageId 幂等消费”语义；每次
传输使用新的 `seq/requestId`。微信小程序在首发版本中明确禁用，不连接新版服务端。

## H5 SDK

```bash
cd agent-h5
npm ci
npm run ci
```

```js
await window.SynapAgent.init({
  baseUrl: 'https://risk.example.com',
  appId: 'synap-h5',
  serverTrustAnchors: {
    'server-key-2026': '<P-256 SPKI Base64URL>'
  },
  collectionProfile: 'low'
});
```

公共 API 为 `SynapAgent.init/destroy/getDeviceInfo/getRatingInfo/getLabelInfo/userData/markAction/getMetrics`。同步全局查询函数已移除。详见 `agent-h5/README.md` 与 `agent-h5/doc/接入-Web.md`。

## 微信小程序

安全通信协议 v1 首发不支持微信小程序。其构建命令和 `SynapAgent.init()` 都会在
任何网络请求前明确失败，不生成能够连接生产服务端的旧协议包。详见
`agent-miniprogram/README.md`。

## 数据仿真器

```bash
cd agent-emulator
go test ./...
go build -o agent-emulator
./agent-emulator --host localhost --device_count 1 --print_log=true
```

仿真器会更新 guid、startId、clientTime；WeChat 样例还会生成 v1 meta、batchId、messageId、sequence 和 observedAt。请求头格式统一为：

```text
{guid}-{platform}-{appId}-{sdkVersion}
```

## 协作关系

```text
agent-h5 / agent-host / agent-springboot / agent-emulator / Android / iOS
  -> /sc/v1/handshake 建立安全会话
  -> start 获取 checker 配置
  -> 采集并写入有界队列
  -> /sc/v1/message 加密 upload + ACK
  -> synap-server 幂等构建 Fact
  -> Groovy 观察规则
  -> Kafka Checkpoint
```

`synap-server` 的 Controller 逻辑路由仅允许 `/attach/start`、`/attach/upload` 和
`/cross/{id}`。外部只能访问 `/sc/v1/**`；直接明文访问 Controller、旧
`/sc/h5/**` 或旧 Agent 请求都会被拒绝。
