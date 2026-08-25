# synap-server 接口文档

本文档基于当前项目代码整理。

H5 各 Fact 的检测用途、规则阈值、风险 reason code 和测试要求见 [H5 检测规则与验收说明](../03-Agent接入/H5检测规则.md)。

Host 探针使用相同的 24 项 common 与数组 Fact 协议，但不使用 H5 meta/ACK。当前推荐接口为 `/attach/start`、`/attach/upload`、`/cross/info`、`/cross/rating`、`/cross/label_start`；旧 `/1/1`、`/1/2` 和数字查询 `1/6/5` 继续兼容。Host 客户端只在 HTTP 成功且业务 `status=1` 后确认本地落盘记录。

## 基础信息

- 默认服务地址：`http://localhost:11099`
- 请求格式：`Content-Type: application/json`
- 主要 Header：`code`
- 业务响应字段：`status`，`1` 表示成功，`0` 表示失败

H5 的 start、upload 和 cross 请求/响应均使用原 appKey codec。本文 JSON 只展示 codec 处理前的业务对象，网络上传输的是编解码后的报文。

## Header: code

所有主要接口都读取 `code` 请求头。

代码解析逻辑位于 `Header`：

```text
<guid-part-1>-<guid-part-2>-<guid-part-3>-<guid-part-4>-<guid-part-5>-<platform>-<appId>-<sdkVersion>
```

解析结果：

| 字段 | 来源 |
| --- | --- |
| `guid` | 前 5 段重新用 `-` 拼接 |
| `platform` | 第 6 段 |
| `appId` | 第 7 段 |
| `sdkVersion` | 第 8 段 |

示例：

```http
code: g1-g2-g3-g4-g5-android-100-1.0.0
```

## 通用请求体

`RequestVO`：

```json
{
  "common": [],
  "data": []
}
```

`common` 是数组，按固定顺序映射到 `Common` 对象。

| 索引 | 字段 |
| --- | --- |
| 0 | `userId` |
| 1 | `guid` |
| 2 | `startId` |
| 3 | `sdkVersion` |
| 4 | `appId` |
| 5 | `appName` |
| 6 | `appPackage` |
| 7 | `appVersion` |
| 8 | `platform` |
| 9 | `manufacturer` |
| 10 | `model` |
| 11 | `system` |
| 12 | `systemVersion` |
| 13 | `netType` |
| 14 | `lanIp` |
| 15 | `wanIp` |
| 16 | `latitude` |
| 17 | `longitude` |
| 18 | `country` |
| 19 | `province` |
| 20 | `city` |
| 21 | `county` |
| 22 | `thoroughfare` |
| 23 | `clientTime` |

H5 的 `lanIp` 来自浏览器可选的 WebRTC host candidate，可能为 `masked`、`unavailable` 或 `disabled`。H5 等平台的 `wanIp` 客户端值不可信，服务端会使用 TCP 来源地址覆盖；仅当直接来源属于配置的可信代理 CIDR 时才解析转发头。Host 探针例外：它可通过配置的第三方服务查询公网 IP，服务端在该值通过公网 IPv4/IPv6 校验时保留它，同时仍将真实连接来源单独记录为 `requestIp`。

示例：

```json
{
  "common": [
    "u001",
    "g1-g2-g3-g4-g5",
    "10001",
    "1.0.0",
    "100",
    "DemoApp",
    "com.demo.app",
    "1.0.0",
    "android",
    "Google",
    "Pixel 7",
    "Android",
    "14",
    "wifi",
    "192.168.1.2",
    "203.0.113.10",
    "31.2304",
    "121.4737",
    "CN",
    "Shanghai",
    "Shanghai",
    "Pudong",
    "Demo Road",
    "2026-07-03 10:00:00"
  ],
  "data": []
}
```

支持的 `platform` 值由配置和 Fact 包决定：

- `android`
- `ios`
- `h5`
- `wechat`
- `host`

## 通用响应体

`ResponseVO`：

```json
{
  "status": 1,
  "config": {},
  "agendas": [],
  "punishes": []
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `status` | number | `1` 成功，`0` 失败 |
| `config` | object/null | 启动接口返回平台 checker 配置；upload 通常为 `null` |
| `agendas` | array | 规则命中的议题数组 |
| `punishes` | array | 匹配到的客户端动作数组 |

`agenda` 数组格式：

```json
["tag", "source", "level"]
```

`punish` 数组格式：

```json
[1, "title", "message", "target"]
```

## POST /attach/start

各平台 agent 的启动逻辑路由。服务端从 `/sc/v1/message` 解密后转发该请求，按
`common.platform` 加载 checker 配置、构造对应 StartFact、执行启动规则，并将结果
重新加密返回。

推荐路径：

```http
POST /attach/start
```

受保护的内部 Header（位于密文内）：

```http
code: g1-g2-g3-g4-g5-h5-1-1.3.0
```

请求 Body：

```json
{
  "common": [
    "u001",
    "g1-g2-g3-g4-g5",
    "10001",
    "1.3.0",
    "1",
    "DemoApp",
    "app.example.com",
    "1.0.0",
    "h5",
    "Browser",
    "Desktop",
    "macOS",
    "15",
    "wifi",
    "192.168.1.2",
    "203.0.113.10",
    "31.2304",
    "121.4737",
    "CN",
    "Shanghai",
    "Shanghai",
    "Pudong",
    "Demo Road",
    "2026-07-03 10:00:00"
  ],
  "meta": {
    "protocolVersion": 1,
    "appKeyId": "8f5c2a4d9b7e1f603ac8d042b97e6c15",
    "detectorVersion": "1.3.0",
    "messageId": "start-msg-019",
    "batchId": "start-batch-019",
    "sessionId": "10001",
    "attempt": 1,
    "createdAt": 1785885600000,
    "observedAt": 1785885600000,
    "sequence": 1
  },
  "data": []
}
```

StartData 是每个 `startId` 会话的第一条逻辑消息，固定使用 `sequence=1`。重试时除
`attempt` 递增外，其余追踪字段必须保持不变；服务端只执行一次规则和 Kafka 发布，
并原样重放首次响应。兼容期内完全不带追踪字段的旧客户端仍可启动，但只要出现任一
追踪字段就必须提交完整元组。可通过
`synap.start-tracking.required-platforms=h5,wechat,ios,android,host` 分平台启用强制校验。

成功响应示例：

```json
{
  "status": 1,
  "config": {
    "startConfig": [1, 60.0, 120.0, 10.0, 864000.0],
    "deviceConfig": {}
  },
  "agendas": [],
  "punishes": []
}
```

失败响应示例：

```json
{
  "status": 0,
  "config": null,
  "agendas": null,
  "punishes": null
}
```

常见失败原因：

- `meta.protocolVersion` 不是 `1`，或缺少 `appKeyId/detectorVersion`。
- `common.platform` 不是 `h5`，或者 header/common 不一致。
- Groovy 规则路径配置不正确，导致启动规则加载失败。

## POST /attach/upload

H5、微信和 Host 数据上报接口。固定使用 `protocolVersion=1`，服务端先执行消息幂等和
sequence 观察，再构造 Fact、执行规则并写入 Kafka。

推荐路径：

```http
POST /attach/upload
```

请求 Header：

```http
code: g1-g2-g3-g4-g5-h5-1-1.3.0
```

请求 Body 示例：

下面是 H5 `UserData` 示例。其他 Fact 需要按 `checker_config/h5.json` 补齐字段，并保证数组长度与配置项一致。

```json
{
  "common": [
    "u001",
    "g1-g2-g3-g4-g5",
    "10001",
    "1.3.0",
    "1",
    "DemoApp",
    "app.example.com",
    "1.0.0",
    "h5",
    "Browser",
    "Desktop",
    "macOS",
    "15",
    "wifi",
    "192.168.1.2",
    "203.0.113.10",
    "31.2304",
    "121.4737",
    "CN",
    "Shanghai",
    "Shanghai",
    "Pudong",
    "Demo Road",
    "2026-07-03 10:00:00"
  ],
  "meta": {
    "protocolVersion": 1,
    "batchId": "batch-019",
    "sessionId": "10001",
    "attempt": 1,
    "createdAt": 1785885600000,
    "appKeyId": "8f5c2a4d9b7e1f603ac8d042b97e6c15",
    "detectorVersion": "1.3.0"
  },
  "data": [
    {
      "type": "UserData",
      "buildDate": "2026-07-03 10:00:00",
      "messageId": "msg-019",
      "sequence": 1,
      "observedAt": 1785885600000,
      "userId": "user-hash",
      "data": {"scene": "login"}
    }
  ]
}
```

说明：

- `type` 必填，用于推导 Fact 类名。
- H5、微信和 Host 的 `meta`、`messageId`、`sequence` 和 `observedAt` 必填；缺失时服务端拒绝。
- Host 采用一消息一批次，StartData 固定 sequence 1，第一条普通消息使用 sequence 2。
- 缺少 batch meta 的旧 Host 会收到 HTTP 426，必须协调升级服务端和全部 Host 客户端。
- `messageId` 在持久化和重试过程中必须保持不变；服务端去重键由 appId、startId 和 messageId 组成。
- 其他字段名要与对应平台 checker 配置中的 `<type>Config` 子字段匹配。
- 数组值顺序必须与 checker 配置中的字段顺序一致。
- 如果 Fact 构造失败，当前代码会记录错误并跳过该条数据。

H5 接受的 `type` 包括 `DeviceData/SelfAppData/RuntimeData/LocationData/IntegrityData/ResourceIntegrityData/BehaviorData/ActionData/SessionData/NetworkData/ErrorData/EventData/DebugData/DevToolData/UserData`。其中 `ErrorData` 只包含脱敏错误模板、路径模板、行列、摘要和 5 秒窗口计数；`RuntimeData.custom.format=1` 会生成低权重 `H5观察-代码格式化` 标签。完整字段和阈值见 [H5 检测规则与验收说明](../03-Agent接入/H5检测规则.md)。

Host 接受 `DeviceData/FileData/RuntimeData/NetData/EventData/UserData/AuditData`。AuditData
Fact 内的 `batchId` 是业务审计批次，`meta.batchId` 是传输批次；服务端同时执行 messageId
和审计批次幂等，并始终以传输 batchId 返回 ACK。

成功响应示例：

```json
{
  "status": 1,
  "config": null,
  "agendas": [
    ["小众机型", "Pixel 7", "low"]
  ],
  "punishes": [],
  "ack": {
    "batchId": "batch-019",
    "acceptedMessageIds": ["msg-019"],
    "duplicateMessageIds": [],
    "rejectedItems": []
  }
}
```

客户端只能删除 `acceptedMessageIds` 和 `duplicateMessageIds` 中的消息；`rejectedItems.retryable=true` 或未出现在 ACK 中的消息需要重试。H5 Agenda 数组第 4 位为同批命中次数。

服务端副作用：

- 每个成功执行规则的 Fact 会生成 `Checkpoint`。
- `Checkpoint` 会按 Fact 类型发送到对应 Kafka topic。
- Kafka key 使用 `common.guid`。

## H5 查询接口

跨服务查询请求和响应均使用原 H5 codec。第一版只暴露以下三个 H5 查询地址：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/cross/info` | 设备信息查询 |
| POST | `/cross/rating` | 风险评级查询 |
| POST | `/cross/label_start` | 当次启动标签查询 |

配置示例：

```json
{
  "id": "info",
  "api_name": "设备信息查询",
  "api_url": "http://192.168.1.201:11003/device/info/{guid}"
}
```

请求示例：

```http
POST /cross/info
code: g1-g2-g3-g4-g5-h5-1-1.3.0
Content-Type: application/json
```

```json
{
  "common": [
    "u001",
    "g1-g2-g3-g4-g5",
    "10001",
    "1.3.0",
    "1",
    "DemoApp",
    "app.example.com",
    "1.0.0",
    "h5",
    "Browser",
    "Desktop",
    "macOS",
    "15",
    "wifi",
    "192.168.1.2",
    "203.0.113.10",
    "31.2304",
    "121.4737",
    "CN",
    "Shanghai",
    "Shanghai",
    "Pudong",
    "Demo Road",
    "2026-07-03 10:00:00"
  ],
  "meta": {
    "protocolVersion": 1,
    "appKeyId": "8f5c2a4d9b7e1f603ac8d042b97e6c15",
    "detectorVersion": "1.3.0"
  },
  "data": []
}
```

响应：

```text
下游服务返回的原始字符串
```

异常情况：

- 找不到 `id` 对应配置时，当前代码会抛出 `IllegalArgumentException`。
- 下游服务不可达时，会由 `RestTemplate` 抛出调用异常。

## 配置文件热加载

服务启动时会加载并监听：

| 配置 | 路径配置项 | 说明 |
| --- | --- | --- |
| checker | `checker.config.path` | 各平台采集配置目录 |
| punish | `punish.config.path` | agenda 命中后的动作规则 |
| msg API | `msg.api.config.path` | H5 `info/rating/label_start` 查询的外部服务配置 |

配置变更后，监听线程会自动重新加载对应文件。

## 规则与动作说明

Groovy 规则输入变量：

```groovy
fact
```

规则需要返回：

```groovy
ArrayList<Agenda>
```

示例：

```groovy
ArrayList<Agenda> agendaList = new ArrayList<>()
AndroidDeviceFact androidDeviceFact = (AndroidDeviceFact) fact
if (androidDeviceFact.getDevice().getModelCode().contains("Pixel")) {
    agendaList.add(new Agenda("小众机型", androidDeviceFact.getDevice().getModelCode(), AgendaLevelEnum.LOW))
}
return agendaList
```

`punish_config.json` 会根据 `Agenda.tag` 和 `Agenda.source` 匹配动作：

```json
{
  "tag": "root",
  "sourceRegex": ".*",
  "action": {
    "type": 1,
    "title": "运行环境异常",
    "message": "运行在root/越狱设备上"
  }
}
```

## 微信小程序

安全协议 v1 首发不支持微信小程序。服务端不开放历史小程序别名，构建系统也不会
生成可连接新版生产服务端的小程序旧协议包。
