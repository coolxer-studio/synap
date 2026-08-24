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
