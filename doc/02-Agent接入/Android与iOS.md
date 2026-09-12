# Android 与 iOS 接入

Android 和 iOS 端点位于 `synap-agent/agent-android` 与 `synap-agent/agent-ios`。两端都已
接入安全通信协议 v1，但当前业务可靠性完成度不同：Android 已进入 Server 的协议客户端
校验、ACK 与幂等链路；iOS 已完成安全传输和 start 跟踪，upload 仍是旧的无 ACK 处理方式。

## Android

生产初始化应显式提供 `SecureClientConfig` 和短时、单次注册令牌 provider：

```java
boolean initialized = CTProbe.initWithContext(
    context,
    secureClientConfig,
    () -> fetchOneTimeEnrollmentToken()
);
if (initialized) {
    CTProbe.getInstance().start();
}
```

Demo 支持通过 `SYNAP_SC_ENROLLMENT_TOKEN` 构建参数注入测试令牌；正式应用不应把令牌
写入源码、资源文件或长期 BuildConfig。Server 侧必须允许对应协议 appId 和
`deviceType=ANDROID`。

当前 Android 数据链路包括：

- start 固定使用业务 `sequence=1`，同一实例重试保持 start `messageId/batchId`。
- Fact 入队时补齐稳定 `messageId/observedAt/sequence`；Server 对 Android 类型白名单、
  业务元数据、messageId 幂等与 sequence 观察执行协议级校验。
- upload 按 UTF-8 请求体拆分到不超过 240 KiB；ACK `batchId` 必须与请求一致。
- accepted/duplicate 不再入队，retryable 或未确认记录放回队首，永久拒绝只写诊断日志。
- 当前队列仍是进程内队列，不保证应用被杀或设备重启后的恢复，也没有持久 dead-letter。

Android 会依次查询 `api.ipify.org` 与 `api64.ipify.org` 获取公网地址。Server 只保留合法
公网 IPv4/IPv6；私网、回环、链路本地、组播和无效值回退为可信代理解析后的连接来源，
并始终把实际连接来源另存为 `requestIp`。部署前应把第三方公网 IP 查询纳入隐私、可用性
和网络允许列表评审。

当前 Android 配置校验接受 HTTP 和 HTTPS URL。生产仍建议 HTTPS；HTTP 只改变外层传输，
不会关闭协议 v1 的应用层加密，但缺少 TLS 的证书和网络层保护。Server 默认仍要求 TLS，
HTTP 联调还需要显式的非生产 Server 配置。

权限、AAR 集成和检测点说明见 `synap-agent/agent-android/doc/接入-Android.md`。上线至少验证
首次注册、start、超过单批大小的分片、accepted/duplicate/retryable/permanent ACK、断网
重入队、公网 IP 失败回退和应用重启后的已知队列限制。

## iOS

iOS 的 `SynapSecureAdapter` 已使用协议 v1 客户端、Server SPKI 信任锚和注册令牌 provider，
逻辑 `/attach/start`、`/attach/upload` 与 `/cross/*` 均位于密文内。start 会携带稳定的
`messageId/batchId/sessionId/observedAt/sequence`，可进入 Server 的 start 幂等流程。

当前实现仍有以下限制：

- upload 请求未携带 batch `meta`，Fact 也未统一补齐业务 `messageId/observedAt/sequence`。
- Server 当前不把 `platform=ios` 纳入协议 upload ACK 客户端集合，因此返回旧业务响应，
  不能按 accepted/duplicate 精确清理单条记录。
- sender 发送前会排空进程内队列，失败恢复和跨重启持久化尚未达到 Host、Qt 或 Spring
  Boot Agent 的标准。

因此 iOS 当前应标记为有条件联调，不应宣称已经具备完整的“至少一次传输 + ACK 幂等消费”
生产语义。发布前需同时升级 iOS upload 元数据/队列处理和 Server 的 iOS 协议类型白名单、
ACK 契约测试。框架接入说明见 `synap-agent/agent-ios/doc/接入-Ios.md`。

## 共同安全要求

- 使用生产等价 TLS 证书和 P-256 SPKI 信任锚，失败时不回退明文业务 Controller。
- 区分安全协议 appId 与 `common` 中的业务数字 appId。
- 注册令牌短时、单次使用，不写入日志、镜像、持久配置或崩溃报告。
- 采集权限、字段说明、第三方网络访问、数据保留和删除策略符合应用发布要求。
- 直接明文访问 `/attach/*`、旧 `/sc/h5/**` 或旧 Agent 路径应失败。
