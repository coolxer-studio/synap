# 微信小程序检测与上报规则

## 协议

微信小程序沿用现有网关路径和 appKey codec：

- `/risk/start`
- `/risk/upload`
- `/device_index/info`
- `/device_index/rating`
- `/device_index/label_start`

`common.platform=wechat`。start/cross 强制 `meta.protocolVersion=1/appKeyId/detectorVersion`；upload 还强制 `batchId/sessionId/attempt/createdAt`，每条 Fact 强制 `messageId/sequence/observedAt`。

服务端返回包含 `acceptedMessageIds/duplicateMessageIds/rejectedItems` 的 ACK。幂等键为 `synap:wechat:message:{appId}:{startId}:{messageId}`，done TTL 为 14 天；sequence 状态按 `wechat:appId:startId:guid` 隔离。网络层仍是至少一次投递，规则与 Kafka 通过 messageId 实现幂等边界。

## 校验

- common 必须恰好 24 项，header 的 guid/platform/appId/sdkVersion 必须一致。
- Fact 类型必须在 WeChat allowlist 内，请求体、Fact 数量、字符串和嵌套深度受限。
- `SelfAppData.data.appId` 必须与 `common.appPackage` 一致。
- 生产环境按 `appKeyId` 配置小程序 appId 白名单：

```properties
synap.security.wechat-appids=app-key-id-1=wx123|wx456;app-key-id-2=wx789
synap.security.require-wechat-appid=true
```

微信请求不套用浏览器 Origin 和 Fetch Metadata 规则；外网 IP 由受信代理链解析并覆盖客户端值。

## 观察规则

| 标签 | 等级 | 触发条件 |
| --- | --- | --- |
| 微信观察-开发者工具 | LOW | brand/platform 或 Runtime 显示 devtools |
| 微信观察-代码格式化 | LOW | `codeFormatting=1`；reason code 为 `CODE_FORMATTING_DETECTED` |
| 微信观察-enableDebug | LOW | enableDebug=true |
| 微信观察-非Release环境 | LOW | envVersion 非 release |
| 微信观察-自动化启动场景 | LOW | scene=1030 |
| 微信观察-多信号运行环境异常 | MEDIUM | 至少两个独立运行环境信号 |
| 微信观察-多信号完整性异常 | MEDIUM | evidence>=2 且加权分>=4 |
| 微信观察-重复异常 | LOW | 同错误指纹 count>=5 |
| 微信观察-自动化行为 | MEDIUM | 样本>=20 且至少两类异常统计 |
| 微信观察-客户端时钟回拨 | MEDIUM | monotonic/wall clock 检测到回拨 |
| 微信观察-会话状态频繁变化 | LOW | 前后台/网络切换或内存告警聚集 |
| 微信观察-网络失败突增 | LOW | 请求>=20 且失败率>=50% |
| 微信观察-位置速度异常 | LOW | speed>100 m/s |
| 微信观察-调试长暂停 | LOW | 前台主线程长暂停 |

微信小程序不参与安全协议 v1 首发；保留的规则只用于后续迁移测试，不能连接新版生产入口。
