# Web 接入

## SDK 说明

`probe.min.js` 是浏览器风险感知探针。支持 Chrome/Edge 90+、Firefox 90+、Safari/iOS 14+ 和 Android 90+ WebView，不支持 IE。

## 集成步骤

将 SDK 文件复制到业务站点，在脚本加载前设置 app key 和服务地址：

```html
<script>
  window.SynapAgentConfig = {
    appKey: '申请得到的 APP KEY',
    baseUrl: 'https://risk.example.com',
    collectionProfile: 'full'
  };
</script>
<script src="./js/probe.min.js"></script>
```

SDK 仍会自动初始化。生产页面使用 HTTPS 时，`baseUrl` 也必须是 HTTPS，否则初始化会拒绝执行。

如需由应用控制启动时机，可调用幂等初始化接口：

```js
await window.SynapAgent.init({
  appKey: '申请得到的 APP KEY',
  baseUrl: 'https://risk.example.com',
  collectionProfile: 'minimal'
});
```

## 采集控制与隐私

`collectionProfile` 可以传 `full`、`minimal`，也可以按能力配置：

```js
{
  canvas: true,
  webgl: true,
  fonts: true,
  webrtc: true,
  location: false,
  userBehavior: true,
  debug: true,
  integrity: true,
  session: true,
  network: false
}
```

宿主应用负责取得适用的授权并在隐私政策中说明数据用途和保存周期。不得通过 `userData` 上传密码、证件号、支付信息等明文敏感数据。

`webrtc: true` 会执行一次有超时限制的内网地址探测，不访问第三方 STUN 服务。浏览器启用 mDNS 隐藏时结果为 `masked`，无法探测时为 `unavailable`；可通过 `SynapAgent.getMetrics().networkInfo` 查看状态。外网 IP 由服务端从可信 HTTP 代理链识别，客户端不会自行请求第三方“查 IP”接口。

SDK 仅将行为数据用于自动化、会话重放和异常交互识别，上传的是时间窗口统计而非输入内容或完整轨迹。服务端传输幂等记录默认保存 14 天；业务 Fact 的实际保存周期由部署方配置并披露。

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
```

返回 `boolean`。空标识、超过 256 字符的标识、不可序列化数据或超过 32 KiB 的数据返回 `false`。

兼容接口 `window.userData(userId, data)` 行为相同。

### 设备、评级和标签查询

```js
const device = await SynapAgent.getDeviceInfo();
const rating = await SynapAgent.getRatingInfo();
const labels = await SynapAgent.getLabelInfo();
```

返回服务端解密后的 JSON 对象。网络失败、超时、非 2xx、错误密文或错误 JSON 都会拒绝 Promise，并带有错误类型。

以下旧同步接口暂时保留，但会阻塞主线程并输出废弃提示：

```js
window.deviceInfo();
window.ratingInfo();
window.labelInfo();
```

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
2. Network 面板中 start 和 upload 请求成功。
3. `SynapAgent.getMetrics().status` 为 `running`。
4. 管理平台能够查询到对应 app key 的运行数据。

## 页面生命周期

- SDK 页面会话内只计算一次静态设备指纹。
- 页面隐藏时暂停周期采集，并把待发送消息写入 sessionStorage。
- 下次加载会恢复待发送和正在发送的消息；消息 ID 在恢复和重试时保持不变。
- 服务端明确 ACK 后才删除消息；错误密文、空响应、业务失败和 batchId 不匹配都会重试。
- 策略缓存按 app key 和 SDK 版本隔离，损坏时自动重新获取。
