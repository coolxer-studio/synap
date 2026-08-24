# Server API

## 外部入口

生产 Agent 只访问：

```http
POST /sc/v1/handshake
POST /sc/v1/handshake/finish
POST /sc/v1/message
```

所有业务请求由安全层解密后转发。下列是逻辑路径，不是公网明文 API：

| 方法 | 逻辑路径 | 说明 |
| --- | --- | --- |
| `POST` | `/attach/start` | 获取平台 checker 并执行启动规则 |
| `POST` | `/attach/upload` | 上报 Fact、执行规则并返回 ACK |
| `POST` | `/cross/info` | 查询设备或资产信息 |
| `POST` | `/cross/rating` | 查询风险评级 |
| `POST` | `/cross/label_start` | 查询当次启动标签 |

旧 `/1/1`、`/1/2` 和数字查询路径只用于历史兼容背景。新 Agent 不应调用这些路径；直接明文调用逻辑 Controller 应被拒绝。

## 逻辑请求模型

解密后的请求主体使用紧凑数组协议：

```json
{
  "common": [],
  "data": []
}
```

`common` 的 24 个固定位置依次为：

```text
userId, guid, startId, sdkVersion, appId, appName, appPackage,
appVersion, platform, manufacturer, model, system, systemVersion,
netType, lanIp, wanIp, latitude, longitude, country, province, city,
county, thoroughfare, clientTime
```

受保护 `code` 的逻辑格式为：

```text
<guid-5段>-<platform>-<appId>-<sdkVersion>
```

Server 会校验 code、header/common 一致性、平台、Fact 数量、字符串长度和嵌套深度。

## 逻辑响应

```json
{
  "status": 1,
  "config": {},
  "agendas": [],
  "punishes": []
}
```

`status=1` 表示业务成功，`0` 表示失败。start 通常返回 checker 配置；upload 通常返回 ACK、Agenda 和动作建议。客户端不能只凭 HTTP 2xx 删除消息，还需检查业务状态与 ACK。

## 来源地址

H5 等浏览器型平台提交的 `wanIp` 不可信，Server 使用连接来源覆盖。Host 与 Android
可以声明独立查询到的公网 IP，但只有通过公网 IPv4/IPv6 校验后才保留；私网、回环、
链路本地、组播等地址会回退为连接来源。真实连接来源始终独立写入 `requestIp`。

只有直接来源属于可信代理 CIDR 时，Server 才解析转发头，并从代理链右侧向左剥离可信节点。
