# synap-agent-h5

浏览器风险感知探针 SDK。SDK 获取服务端策略后，按策略采集设备、运行环境和用户行为数据，并通过 `@coolxer/secure-communication-js` 编解码后批量上报。

## 浏览器支持

- Chrome / Edge 90+
- Firefox 90+
- Safari / iOS 14+
- Android 90+ 及对应 WebView

默认构建不再支持 IE。如业务必须支持旧浏览器，应单独维护 legacy 构建。

## 安装与构建

项目统一使用 npm：

```bash
npm ci
npx playwright install chromium firefox webkit
npm run ci
```

常用命令：

```bash
npm run dev        # 监听源码并输出 dist/probe.dev.js
npm test           # 单元测试
npm run lint       # 源码语法和 CSP 静态检查
npm run build:prod # 输出 dist/probe.min.js
npm run size       # gzip 包体积预算（65 KiB）
```

安全通信依赖固定为 `vendor/coolxer-secure-communication-js-0.2.0.tgz`，因此干净安装不依赖仓库外目录。正式发布到组织私有 registry 后，可把依赖替换为精确版本 `0.2.0`；tarball 的来源提交和更新方式记录在 `vendor/README.md`。

## 接入

零配置接入仍会使用历史默认 key 和本地服务地址，主要用于开发环境：

```html
<script src="./probe.min.js"></script>
```

生产环境应在加载脚本前提供显式配置，且 HTTPS 页面必须配置 HTTPS 服务地址：

```html
<script>
  window.SynapAgentConfig = {
    appKey: '申请得到的 APP KEY',
    baseUrl: 'https://risk.example.com',
    collectionProfile: 'full'
  };
</script>
<script src="./probe.min.js"></script>
```

也可以显式、幂等地初始化：

```js
await window.SynapAgent.init({
  appKey: '申请得到的 APP KEY',
  baseUrl: 'https://risk.example.com',
  collectionProfile: 'minimal'
});
```

`collectionProfile` 支持 `full`、`minimal`，或以下布尔开关：

```js
{
  canvas: true,
  webgl: true,
  fonts: true,
  webrtc: true,
  location: true,
  userBehavior: true,
  debug: true
}
```

宿主应用必须在适用法律和自身隐私政策允许的前提下启用设备指纹、地理位置和行为采集。SDK 不应接收密码、证件号、支付信息或其他明文敏感数据。

## API

### Promise API

```js
await SynapAgent.init(options);
await SynapAgent.getDeviceInfo();
await SynapAgent.getRatingInfo();
await SynapAgent.getLabelInfo();
await SynapAgent.flush();
await SynapAgent.destroy({ flush: true });

const accepted = SynapAgent.userData('脱敏用户标识', { scene: 'login' });
const metrics = SynapAgent.getMetrics();
```

`userData` 会拒绝空用户标识、超过 256 字符的标识、不可序列化数据以及超过 32 KiB 的数据，并返回 `boolean`。

### 兼容 API

以下 API 至少保留一个兼容版本：

```js
initWithKey(appKey);                 // 返回 Promise
window.userData(userId, data);      // 返回 boolean
window.deviceInfo();                // 同步，已废弃
window.ratingInfo();                // 同步，已废弃
window.labelInfo();                 // 同步，已废弃
```

同步查询会阻塞主线程并输出废弃提示，新代码应使用 Promise API。

## 运行特性

- 策略缓存按 SDK 版本和 app key 隔离，损坏后自动重新获取。
- 同一时间只发送一个批次；失败批次恢复到队首并指数退避。
- 页面隐藏时暂停采集并持久化未发送消息，下次加载恢复。
- 初始化幂等；`destroy()` 会移除 timer、事件监听器和调试检测资源。
- Canvas、WebGL、字体等静态设备信息每个 SDK 会话只计算一次。
- `getMetrics()` 提供请求失败、解析失败、采集失败、最近采集耗时和队列丢弃数。

## 数据协议

现有服务端协议保持不变：请求体仍为 `{ common, data }`，`common` 固定为 24 个位置，采集消息继续使用 `DeviceData`、`SelfAppData`、`LocationData`、`RuntimeData`、`ErrorData`、`EventData`、`DevToolData` 和 `UserData` 等类型。所有新消息统一使用 `buildDate`。
