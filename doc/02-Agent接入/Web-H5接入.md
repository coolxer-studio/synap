# Web / H5 接入

H5 Agent 面向浏览器与 WebView。它使用 HTTPS、Server 公钥固定、Origin 白名单和 IndexedDB 中不可导出的 P-256 安装密钥建立身份边界。

## 构建

```bash
cd synap-agent/agent-h5
npm ci
npm run ci
```

生产产物位于 `dist/probe.min.js`，相邻 `.sha384` 文件提供 SRI 摘要。

## 页面接入

```html
<script>
window.SynapAgentConfig = {
  baseUrl: 'https://synap.example.com',
  appId: 'synap-h5',
  serverTrustAnchors: {
    'server-key-2026': '<P-256 SPKI Base64URL>'
  },
  collectionProfile: 'low'
};
</script>
<script
  src="/probe.min.js"
  integrity="sha384-..."
  crossorigin="anonymous">
</script>
```

```js
await SynapAgent.init(window.SynapAgentConfig);
SynapAgent.userData('opaque-user-id', { scene: 'checkout' });
await SynapAgent.flush();
const rating = await SynapAgent.getRatingInfo();
await SynapAgent.destroy({ flush: true });
```

公共 API 包括 `init`、`destroy`、`flush`、`getDeviceInfo`、`getRatingInfo`、
`getLabelInfo`、`userData`、`markAction`、`recordEvent` 和 `getMetrics`。旧同步全局查询
函数已移除。`hostContext` 初始化项和 `recordEvent` 专供经过隔离的宿主适配器（当前为
Electron）使用；普通浏览器不应自行伪造宿主上下文。

## Server 配置

- 在 `SYNAP_CORS_ALLOWED_ORIGINS` 中列出实际 HTTPS Origin。
- 按 appId 配置允许的 H5 宿主 Origin。
- 将公开协议 appId 加入 `SYNAP_SC_ALLOWED_APP_IDS`。
- 配置与页面信任锚一致的 Server key ID 和公钥。
- 反向代理只暴露 `/sc/v1/**`，不暴露明文业务 Controller。

SDK 的配置校验接受 HTTP 和 HTTPS。生产仍应使用 HTTPS；HTTPS 页面访问 HTTP Server
还会受到浏览器 Mixed Content 策略限制。协议 v1 的应用层加密不能替代 TLS 的证书、
流量元数据和网络边界保护。Server 端 `SYNAP_SC_REQUIRE_TLS` 默认是 `true`；受控开发环境
使用 HTTP 时必须显式采用对应的非生产 Server 配置。

## 采集与隐私

H5 行为数据用于自动化、会话重放和异常交互识别，上传时间窗口统计，不上传输入内容或完整行为轨迹。`lanIp` 可能是 `masked`、`unavailable` 或 `disabled`；`wanIp` 以 Server 看到的连接来源为准。

业务接入必须披露实际采集范围和 Fact 保留周期。不要把密码、令牌、Cookie、支付信息或身份证明原值传入 `userData` 或动作属性。

## 验收

- 生产页面使用 HTTPS，脚本 SRI 与产物摘要一致。
- Origin 不在允许列表时握手失败；合法 Origin 可以初始化。
- key ID 或信任锚错误时失败且不回退明文。
- 刷新、后台恢复、离线重试和 destroy/flush 行为符合预期。
- 重试保留业务 messageId，但每次传输使用新的 seq/requestId。

## 请求处理链路

```text
POST /sc/v1/message
  -> SC1 握手会话、AEAD 与重放校验
  -> 解密逻辑路由 POST /attach/upload
  -> Origin / Referer / Fetch Metadata / 结构校验
  -> protocolVersion=1 与批次 meta 校验
  -> messageId 幂等 claim
  -> sequence 缺口/倒序观察
  -> FactBuilder
  -> Groovy Fact 规则
  -> 会话级风险聚合
  -> Kafka Checkpoint
  -> ACK accepted / duplicate / rejected
```

upload 必须包含：

- `meta.protocolVersion=1`；
- `meta.batchId/sessionId/attempt/createdAt/appKeyId/detectorVersion`；
- 每条 Fact 的 `messageId/sequence/observedAt/type/buildDate`；
- 24 个固定顺序的 `common` 字段，且 header 与 common 的 guid、platform、appId、sdkVersion 一致。

服务端对 H5 始终返回 ACK。已完成的 messageId 返回 `duplicateMessageIds`，不再次执行规则或发布 Kafka；处理中消息以 `retryable=true` 拒绝；格式错误以 `retryable=false` 永久拒绝。生产使用 Redis，messageId 完成记录默认保存 14 天；开发和测试使用有界、带 TTL 的内存实现。

## 接受的 H5 Fact

| Fact | 主要用途 | 默认客户端状态 |
| --- | --- | --- |
| `DeviceData` | 设备、浏览器、屏幕、能力状态和可选指纹 | 开启 |
| `SelfAppData` | 页面域名和应用域名一致性 | 开启 |
| `RuntimeData` | 代码格式化、运行类型和自动化特征 | 开启 |
| `IntegrityData` | 环境一致性和关键 API/appKey/策略篡改 | 开启 |
| `ResourceIntegrityData` | SDK/SRI/iframe/Service Worker/CSP/安全报告 | 开启 |
| `BehaviorData` | 10 秒行为窗口统计 | 开启 |
| `EventData` | 页面、点击和输入类型事件 | 开启，不含输入值 |
| `SessionData` | 生命周期、时钟和标签页并发 | 开启 |
| `ErrorData` | 脱敏错误模板和重复计数 | 开启 |
| `DebugData` | 主线程长暂停 | 开启 |
| `DevToolData` | 快捷键和窗口尺寸边沿 | 开启 |
| `ActionData` | 业务动作短窗口统计 | 宿主调用时 |
| `UserData` | 脱敏用户标识和受限业务上下文 | 宿主调用时 |
| `LocationData` | 授权后的位置变化 | 默认关闭 |
| `NetworkData` | 业务网络路径模板和失败聚合 | 默认关闭 |

`RequestValidationService` 仅接受上表类型，同时限制请求体、Fact 数量、字符串长度、Map/Collection 大小和嵌套深度。

## ErrorData 契约

客户端上传的是错误摘要，不是原始异常对象：

```json
{
  "type": "ErrorData",
  "buildDate": "2026-08-05 10:00:00",
  "messageId": "msg-error-1",
  "sequence": 12,
  "observedAt": 1785895200000,
  "from": "windowError",
  "category": "script",
  "message": "request failed: bearer [masked]",
  "templateHash": "fnv1a-12ab34cd",
  "source": "https://app.example.com/assets/app.js",
  "lineno": 42,
  "colno": 7,
  "count": 5
}
```

| 字段 | 服务端含义 |
| --- | --- |
| `from` | `windowError/unhandledRejection/resourceError/securityPolicyViolation` |
| `category` | `script/promise/resource/csp` |
| `message` | 客户端限制为 256 字符的脱敏错误模板 |
| `templateHash` | category、模板、来源和行列组合的客户端摘要 |
| `source` | 无 query/fragment 的 origin + 路径模板 |
| `lineno/colno` | 映射为 `H5ErrorFact.lineNo/colNo` |
| `count` | 同指纹 5 秒窗口内的次数，默认 1 |

`count >= 5` 时，`H5ErrorFactRule` 生成 `H5观察-重复前端异常` LOW 标签。服务端不应依赖或记录原始 query、请求体、header、cookie、表单值、完整堆栈或原始 `console.error` 参数。

## 规则矩阵

| 规则 | 条件 | Agenda | 级别 |
| --- | --- | --- | --- |
| H5Device | UA 为空或匹配 Headless/PhantomJS | `H5观察-异常UA` | MEDIUM |
| H5Device | UA 匹配 MuMu 或 Android x86 | `H5观察-模拟环境UA` | MEDIUM |
| H5Device | 上报 UA 与 HTTP User-Agent 不一致 | `H5观察-UA不一致` | LOW |
| H5SelfApp | 当前 host 不在 appId 域名白名单 | `H5观察-页面域名不一致` | MEDIUM |
| H5Runtime | `custom.format` 为 `1/true` | `H5观察-代码格式化` | LOW |
| H5Runtime | webdriver/PhantomJS/Selenium 等特征总数至少 2 | `H5观察-运行环境自动化` | MEDIUM |
| H5Runtime | 自动化特征只有 1 个 | `H5观察-单一自动化特征` | LOW |
| H5Integrity | 客户端多信号加权 score 至少 2 | `H5观察-环境完整性` | MEDIUM |
| H5ResourceIntegrity | `signals=true` 的数量至少 2 | `H5观察-资源与宿主完整性` | MEDIUM |
| H5ResourceIntegrity | `reports.count` 总和至少 5 | `H5观察-浏览器安全报告聚集` | LOW |
| H5Behavior | 至少 2 个行为异常条件成立 | `H5观察-自动化行为` | MEDIUM |
| H5Action | 动作内 eventCount 至少 2 且 untrusted 至少 2 | `H5观察-业务动作非可信事件` | LOW |
| H5Session | `clockRollback=true` | `H5观察-客户端时钟回拨` | MEDIUM |
| H5Session | `concurrentTabs > 3` | `H5观察-多标签并发` | LOW |
| H5Error | `count >= 5` | `H5观察-重复前端异常` | LOW |
| H5Event | `data.trusted=false` | `H5观察-非可信浏览器事件` | LOW |
| H5Network | requests 至少 20 且 failures 至少 50% | `H5观察-接口失败突增` | LOW |
| H5Location | `speed > 100` | `H5观察-异常移动速度` | LOW |
| H5Debug | 收到 DebugData | `H5观察-调试暂停` | LOW |
| H5DevTool | 收到 DevToolData | `H5观察-开发工具` | LOW |
| H5User | userId 为空 | `H5观察-缺失用户标识` | LOW |

行为规则的五类异常条件为：

- 至少 20 个指针样本且方向变化比为 0；
- 至少 5 次点击且平均间隔小于 80 毫秒；
- 至少 5 次输入且平均间隔小于 30 毫秒；
- 至少 20 个指针样本、直线比例大于 0.98 且时间熵小于 0.5；
- 非可信事件至少 3 个，或不可能点击至少 2 个。

必须同时满足其中至少两类才生成 MEDIUM 标签。

## 代码格式化标签

`H5RuntimeFactRule` 将 `custom.format=1`、正数或 `true` 统一解释为命中，输出：

```text
tag: H5观察-代码格式化
source: format:1
level: LOW
reasonCode: CODE_FORMATTING_DETECTED
```

该标签只是客户端运行源码形态的启发式观察。DevTools Pretty print 不一定改变运行函数源码，因此不能作为确定性测试或源码篡改证明；发布产物完整性仍以 HTTPS、发布摘要和 SRI 为主。

## 风险聚合

`RiskAssessmentService` 以 `appId:startId` 聚合当前会话证据，按 15 分钟半衰期衰减，默认状态 TTL 为 30 分钟。规则级别分别加分：HIGH 30、MEDIUM 18、LOW 8、其他 2。

额外证据包括：

- sequence 缺口或倒序；
- 客户端时间与服务端接收时间相差超过 5 分钟；
- 同账号设备、IP、ASN 变化和 15 分钟内超过 3 个并发会话；
- 可信网关注入的 VPN/Proxy/Tor/IDC、IP 信誉和 TLS/ASN 信息。

输出固定包含：

```json
{
  "detectorVersion": "h5-risk-1",
  "ruleVersion": "2026-08-05",
  "score": 18,
  "confidence": 0.18,
  "reasonCodes": ["CODE_FORMATTING_DETECTED"],
  "evidenceCount": 1,
  "observation": true,
  "trustLevel": "sc1-authenticated/P256_HKDF_SHA256_AES256_GCM"
}
```

所有新增检测处于观察模式。appKey codec 不能证明请求来自真实端点，客户端 Fact 也可能被同页脚本修改；单个客户端信号不得直接触发高风险处罚。

## 服务端测试用例

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| S-01 | 缺少 meta、messageId、sequence 或 observedAt | ACK 永久拒绝，规则和 Kafka 不执行 |
| S-02 | protocolVersion 不为 1 | 永久拒绝 |
| S-03 | 同批或跨批重复 messageId | 首次 accepted，后续 duplicate，只生成一个有效 Checkpoint |
| S-04 | Redis 状态为 processing | `retryable=true`，不重复执行规则 |
| S-05 | 规则处理失败 | 释放 claim 并返回可重试拒绝 |
| S-06 | sequence 缺口、重复和倒序 | Fact 记录 observation，风险结果包含相应 reason code |
| S-07 | Origin/Referer/Fetch Metadata 不一致 | 请求拒绝；非可信转发头不影响来源 IP |
| S-08 | common 非 24 项或 header/common 不一致 | 请求拒绝 |
| S-09 | Fact/字符串/集合/嵌套超过限制 | 请求永久拒绝 |
| S-10 | `RuntimeData.format=1/0` | 仅值 1 产生代码格式化 LOW 标签 |
| S-11 | 相同 `ErrorData.count=5` | 产生重复前端异常 LOW 标签，字段正确映射 |
| S-12 | 行为异常只有一类/至少两类 | 一类不命中，两类命中 MEDIUM |
| S-13 | 同批多个 Fact 命中相同 Agenda | Agenda 全局去重并附加 `hitCount` |
| S-14 | 可信网关与非可信直连提交网络头 | 只接受可信代理来源注入的标准化信号 |
| S-15 | 原五个 H5 endpoint | start/upload/cross codec 正常，不存在新接口或兼容分支 |

执行：

```bash
mvn test
```

截至 2026-08-05，本次优化基线为 24 项 Maven 测试通过，包含 H5 规则编译、代码格式化正反例、协议契约、请求校验、messageId 幂等和 sequence 状态测试。
