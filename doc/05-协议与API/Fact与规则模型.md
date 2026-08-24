# Fact 与规则模型

## 构造约定

普通平台通过命名约定反射构造 Fact：

```text
platform=android + type=DeviceData
=> com.coolxer.model.fact.android.AndroidDeviceFact
```

规则脚本按 Fact 类名对应：

```text
AndroidDeviceFact -> AndroidDeviceFactRule.groovy
```

checker 配置决定字段顺序和采集策略。平台名、type、类名、配置 key 或脚本名不一致，会导致构造或规则执行失败。

当前 Server 已为 Android 显式允许 `ActivityData`、`AnrData`、`AppData`、`DeviceData`、
`EventData`、`FileData`、`InjectData`、`JavaData`、`LocationData`、`NativeData`、`NetData`、
`PrivacyData`、`RuntimeData`、`SelfAppData` 和 `UserData`。Android 的旧检测器集合可能超过
通用单节点结构限制，但仍受请求体字节数和 Fact 数量上限约束。

HarmonyOS 与 ESP32 的客户端 Fact 当前只是待接入契约：Server 尚无对应类型白名单、
Fact 类、checker、规则和 topic，不能因为客户端能生成 JSON 就视为已支持。

## Server 应用 Fact

Spring Boot Agent 使用显式命名字段，`schemaVersion=1`，并携带 `messageId`、`observedAt` 和 `sequence`。当前类型包括：

- `ApplicationData`
- `RuntimeData`
- `ApiAssetData`
- `ApiObservationData`
- `SecurityConfigData`
- `DependencyData`
- `DangerousCallData`
- `SecurityEventData`
- `AgentHealthData`
- `UserData`
- `ActionData`

Server 对类型、字段白名单、枚举、大小和嵌套结构严格校验；未知字段会被永久拒绝。Server 应用 topic 使用 `server_fact_*` 前缀，规则使用 `Server*FactRule.groovy`。

## 规则输出

规则生成 Agenda，再由 punish 配置映射动作建议。风险输出包含：

```text
detectorVersion / ruleVersion / reasonCodes /
evidenceCount / confidence
```

新规则默认 `observation=true`。风险分数按会话聚合并随时间衰减，单个客户端信号不应直接触发高风险处罚。

## 依赖漏洞数据

Server Agent 可读取本地 OSV Maven JSON 或 gzip JSON 快照。快照按修改时间热加载，请求链路不访问公网；依赖漏洞只生成风险和 Agenda，不参与 Agent 运行时阻断。全量历史重算需要下游重放既有 Dependency Checkpoint。
