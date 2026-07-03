# synap-server 接口文档

本文档基于当前项目代码整理。

## 基础信息

- 默认服务地址：`http://localhost:11099`
- 请求格式：`Content-Type: application/json`
- 主要 Header：`code`
- 业务响应字段：`status`，`1` 表示成功，`0` 表示失败

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

agent 启动接口。服务端根据 `common.platform` 加载平台 checker 配置，构造对应平台的 `StartFact`，执行启动规则，然后返回配置、agenda、punish。

推荐路径：

```http
POST /attach/start
```

代码中存在的别名组合：

- `/attach/start`
- `/attach/1`
- `/1/start`
- `/1/1`

请求 Header：

```http
code: g1-g2-g3-g4-g5-android-100-1.0.0
```

请求 Body：

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

- `common.platform` 不在已加载平台配置中。
- Groovy 规则路径配置不正确，导致启动规则加载失败。

## POST /attach/upload

agent 数据上报接口。服务端遍历 `data` 数组，为每条数据构造对应 Fact，执行同名 Groovy 规则，将 checkpoint 写入 Kafka，并返回 agenda 和 punish。

推荐路径：

```http
POST /attach/upload
```

代码中存在的别名组合：

- `/attach/upload`
- `/attach/2`
- `/1/upload`
- `/1/2`

请求 Header：

```http
code: g1-g2-g3-g4-g5-android-100-1.0.0
```

请求 Body 示例：

下面示例用于说明结构。真实 `DeviceData` 请求需要按 `checker_config/android.json` 中的 `deviceConfig` 补齐对应字段，并保证每个数组长度与配置项一致。

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
  "data": [
    {
      "type": "DeviceData",
      "buildDate": "2026-07-03 10:00:00",
      "device": [
        "phone",
        "Pixel 7",
        "Android",
        "14",
        "build-id",
        "Pixel 7",
        "CN",
        "zh",
        "Asia/Shanghai",
        "CNY",
        "2026-07-03 09:58:00",
        false
      ],
      "screen": [1080, 2400, 0.8, 2.75, 0],
      "cpu": [8, 2, "arm64", "arm64-v8a"]
    }
  ]
}
```

说明：

- `type` 必填，用于推导 Fact 类名。
- 其他字段名要与对应平台 checker 配置中的 `<type>Config` 子字段匹配。
- 数组值顺序必须与 checker 配置中的字段顺序一致。
- 如果 Fact 构造失败，当前代码会记录错误并跳过该条数据。

成功响应示例：

```json
{
  "status": 1,
  "config": null,
  "agendas": [
    ["小众机型", "Pixel 7", "low"]
  ],
  "punishes": []
}
```

服务端副作用：

- 每个成功执行规则的 Fact 会生成 `Checkpoint`。
- `Checkpoint` 会按 Fact 类型发送到对应 Kafka topic。
- Kafka key 使用 `common.guid`。

## POST /cross/{id}

跨服务查询接口。服务端根据 `id` 从 `msg_api_config.json` 查找 API 配置，将 URL 中的 `{field}` 占位符替换为 `Header` 或 `Common` 中的字段值，然后发起 GET 请求并返回下游响应字符串。

推荐路径：

```http
POST /cross/{id}
```

代码中存在的别名：

- `/cross/{id}`
- `/2/{id}`

配置示例：

```json
{
  "id": "info",
  "api_name": "设备信息查询",
  "api_url": "http://192.168.1.201:11003/device/info/{guid}"
}
```

当前配置中的常见 id：

| id | 说明 |
| --- | --- |
| `info` | 设备信息查询 |
| `app` | 设备安装应用信息查询 |
| `device_id` | 设备指纹查询 |
| `label_start` | 当次启动标签查询 |
| `rating` | 风险指数查询 |
| `label-today` | 今日标签查询示例 |

请求示例：

```http
POST /cross/info
code: g1-g2-g3-g4-g5-android-100-1.0.0
Content-Type: application/json
```

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
| msg API | `msg.api.config.path` | `/cross/{id}` 外部服务配置 |

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
