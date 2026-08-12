# Web 接入

## SDK 说明

`probe.min.js` 是浏览器风险感知探针。支持 Chrome/Edge 90+、Firefox 90+、Safari/iOS 14+ 和 Android 90+ WebView，不支持 IE。

接入前建议同时阅读 [检测点与测试用例](检测点与测试用例.md)，确认默认采集范围、调试观察、错误脱敏、代码格式化标签和隐私边界。

## 集成步骤

将 SDK 文件复制到业务站点，在脚本加载前设置公开 appId、服务地址和服务端信任锚：

```html
<script>
  window.SynapAgentConfig = {
    appId: '申请得到的 APP ID',
    baseUrl: 'https://risk.example.com',
    serverTrustAnchors: {
      'server-key-2026': '<P-256 SPKI Base64URL>'
    },
    collectionProfile: 'low',
    allowedScriptOrigins: ['https://cdn.example.com'],
    allowedParentOrigins: ['https://app.example.com']
  };
</script>
<script src="./js/probe.min.js" integrity="sha384-构建生成的摘要" crossorigin="anonymous"></script>
```

配置完整时 SDK 会自动初始化。生产页面使用 HTTPS 时，`baseUrl` 也必须是 HTTPS，否则初始化会拒绝执行。

如需由应用控制启动时机，可调用幂等初始化接口：

```js
await window.SynapAgent.init({
  appId: '申请得到的 APP ID',
  baseUrl: 'https://risk.example.com',
  serverTrustAnchors: {
    'server-key-2026': '<P-256 SPKI Base64URL>'
  },
  collectionProfile: 'minimal'
});
```

`appId` 和 `serverTrustAnchors` 是安全协议 v1 必需的公开配置，必须在
`probe.js` 加载前设置；如果由 `synap-build` 生成探针，它们已被编译进制品。
直接从源码构建时，缺少这两项会跳过自动初始化，宿主可在配置就绪后
显式调用 `SynapAgent.init(config)`。

## 采集控制与隐私

默认 `low` 不请求权限、不启用高熵指纹。也可以传 `balanced`、`full`、`minimal`，或按能力配置：

```js
{
  canvas: false,
  webgl: false,
  fonts: false,
  webrtc: false,
  location: false,
  userBehavior: true,
  rawEvents: false,
  debug: true,
  integrity: true,
  session: true,
  network: false,
  uaHighEntropy: false,
  securityReports: true,
  resourceIntegrity: true
}
```

宿主应用负责取得适用的授权并在隐私政策中说明数据用途和保存周期。不得通过 `userData` 上传密码、证件号、支付信息等明文敏感数据。

默认 `low` 档位会开启低权重调试观察（DevTools 快捷键、窗口尺寸边沿变化和主线程长暂停），不会单凭这些信号产生高风险结论。`minimal` 档位仍关闭调试观察。

`webrtc: true` 会执行一次有超时限制的内网地址探测，不访问第三方 STUN 服务。浏览器启用 mDNS 隐藏时结果为 `masked`，无法探测时为 `unavailable`；可通过 `SynapAgent.getMetrics().networkInfo` 查看状态。外网 IP 由服务端从可信 HTTP 代理链识别，客户端不会自行请求第三方“查 IP”接口。

SDK 仅将行为数据用于自动化、会话重放和异常交互识别，上传的是时间窗口统计而非输入内容或完整轨迹。服务端传输幂等记录默认保存 14 天；业务 Fact 的实际保存周期由部署方配置并披露。

错误监听覆盖 JS 运行时异常、未处理 Promise、资源加载失败和 CSP 违规。上报的 `ErrorData` 仅包含脱敏模板、路径模板、行列、摘要和 5 秒窗口计数，不包含完整堆栈、原始 `console.error` 参数或 URL query/fragment。

运行时还会上报 `RuntimeData.custom.format`。值为 `1` 时服务端生成低权重 `H5观察-代码格式化` 标签；该启发式不能替代发布包 SHA-384 SRI，也不保证因 DevTools Pretty print 而触发。

构建会生成 `probe.min.js.sha384`。部署时将其中摘要写入 `integrity` 属性，并同步配置脚本与 iframe 宿主 Origin 白名单。

## 接口定义

### 初始化和销毁

```js
await SynapAgent.init(options);
await SynapAgent.destroy({ flush: true });
```

重复使用相同配置调用 `init` 不会重复安装定时器和监听器。`destroy` 会清理采集资源，`flush` 默认为 `true`。

### 用户数据

```js
const accepted = SynapAgent.userData('脱敏用户标识', {
  scene: 'login',
  channel: 'web'
});

const actionId = crypto.randomUUID();
SynapAgent.markAction({ id: actionId, name: 'login', phase: 'start' });
// 业务完成时调用 success 或 failure；不要传表单内容。
SynapAgent.markAction({ id: actionId, name: 'login', phase: 'success' });
```

返回 `boolean`。空标识、超过 256 字符的标识、不可序列化数据或超过 32 KiB 的数据返回 `false`。

只使用 `window.SynapAgent.userData(userId, data)`；不再暴露独立上传全局函数。

### 设备、评级和标签查询

```js
const device = await SynapAgent.getDeviceInfo();
const rating = await SynapAgent.getRatingInfo();
const labels = await SynapAgent.getLabelInfo();
```

返回服务端解密后的 JSON 对象。网络失败、超时、非 2xx、错误密文或错误 JSON 都会拒绝 Promise，并带有错误类型。

不提供同步查询接口；`window.deviceInfo/ratingInfo/labelInfo` 均未定义。

### 手动上报与运行指标

```js
await SynapAgent.flush();

const metrics = SynapAgent.getMetrics();
// metrics.queue.queued / inFlight / dropped
// metrics.requestFailures / parseFailures / ackFailures / retries
// metrics.serverDuplicates / rejectedMessages / batchesSent
// metrics.lastCollectionDurationMs / status
// metrics.networkInfo.status / addresses / mdnsMasked
```

## 验证

1. 控制台出现 `synap running:true`。
2. Network 面板中只出现 `/sc/v1/handshake`、`/sc/v1/handshake/finish` 和
   `/sc/v1/message`；`/attach/start`、`/attach/upload`、`/cross/*` 仅存在于密文内。
3. `SynapAgent.getMetrics().status` 为 `running`。
4. 管理平台能够查询到对应 app key 的运行数据。

## 页面生命周期

- SDK 页面会话内只计算一次静态设备指纹。
- 页面隐藏时暂停周期采集，并把待发送消息写入 sessionStorage。
- 下次加载会恢复待发送和正在发送的消息；消息 ID 在恢复和重试时保持不变。
- 服务端明确 ACK 后才删除消息；错误密文、空响应、业务失败和 batchId 不匹配都会重试。
- 策略缓存按 app key 和 SDK 版本隔离，损坏时自动重新获取。
