# synap-agent-h5

Synap H5 Agent 通过 `@coolxer/secure-communication-js@2.0.0` 接入安全通信协议
v1。外层只访问 `/sc/v1/handshake`、`/sc/v1/handshake/finish` 和
`/sc/v1/message`；业务 method、逻辑 path、Content-Type、`code` 和正文位于
AES-256-GCM 密文内，传输 requestId 位于经过 AAD 认证的外层信封中。

## 接入

在加载 `probe.min.js` 前提供公开配置：

```html
<script>
window.SynapAgentConfig = {
  baseUrl: 'https://synap.example.com',
  appId: 'synap-h5',
  serverTrustAnchors: {
    'server-key-2026': '<P-256 SPKI Base64URL>'
  }
};
</script>
<script src="/probe.min.js" integrity="sha384-..." crossorigin="anonymous"></script>
```

```js
await SynapAgent.init(window.SynapAgentConfig);
SynapAgent.userData('opaque-user-id', { scene: 'checkout' });
await SynapAgent.flush();
const rating = await SynapAgent.getRatingInfo();
await SynapAgent.destroy({ flush: true });
```

浏览器端 `appId` 是公开标识，不是秘密。安全通信支持 HTTP 和 HTTPS；生产环境仍建议使用 HTTPS，并结合服务端公钥固定、Origin 白名单和 IndexedDB 中不可导出的 WebCrypto P-256 安装密钥。
2.0 使用 `coolxer-secure-communication-v2` 身份库及
`installation-v2:<appId>` 记录，不读取、覆盖或删除 1.x 身份。升级后首次请求会为
当前 Origin 和 appId 生成新安装身份，并按需自动完成会话初始化。

## 重试语义

安全 SDK 不自动重试业务 POST。Agent 队列重试时保留原业务
`messageId/batchId/sequence`，但安全客户端为每次传输生成新的 `seq/requestId`
并重新加密。会话过期时先重新握手。

## 构建与验证

```bash
npm ci
npm run ci
```

生产产物位于 `dist/probe.min.js`，相邻 `.sha384` 文件记录 SRI。构建配置必须
注入实际 `appId` 和服务端公开信任锚；包内不得出现注册令牌、私钥或旧协议 SDK。
