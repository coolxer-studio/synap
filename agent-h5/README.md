# synap-agent-h5

浏览器风险感知探针 SDK。SDK 获取服务端策略后，按策略采集设备、运行环境和用户行为数据，并通过 `@coolxer/secure-communication-js` 编解码后批量上报。

完整的检测点、默认开关、服务端标签、`ErrorData` 字段和验收场景见 [检测点与测试用例](doc/检测点与测试用例.md)。

## 浏览器支持

- Chrome / Edge 90+
- Firefox 90+
- Safari / iOS 14+
- Android 90+ 及对应 WebView

默认构建不再支持 IE。如业务必须支持旧浏览器，应单独维护旧浏览器专用构建。

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
    collectionProfile: 'low',
    allowedScriptOrigins: ['https://cdn.example.com'],
    allowedParentOrigins: ['https://app.example.com']
  };
</script>
<script src="./probe.min.js" integrity="sha384-构建生成的摘要" crossorigin="anonymous"></script>
```

也可以显式、幂等地初始化：

```js
await window.SynapAgent.init({
  appKey: '申请得到的 APP KEY',
  baseUrl: 'https://risk.example.com',
  collectionProfile: 'minimal'
});
```

默认值是低侵入的 `low`。还支持 `balanced`、`full`、`minimal`，或以下布尔开关：

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

`npm run build:prod` 同时输出 `dist/probe.min.js.sha384`。SRI 校验实际加载脚本是否与发布产物一致；`allowedScriptOrigins` 和 `allowedParentOrigins` 分别约束 SDK 来源与 iframe 宿主来源。

`low` 默认开启低权重调试观察，包括 DevTools 快捷键、窗口尺寸边沿变化和主线程长暂停。这些信号不单独作为高风险结论；`minimal` 档位仍默认关闭。

宿主应用必须在适用法律和自身隐私政策允许的前提下启用设备指纹、地理位置和行为采集。SDK 不应接收密码、证件号、支付信息或其他明文敏感数据。

`webrtc` 开启时，SDK 会在初始化阶段用不带 STUN 服务的 WebRTC host candidate 探测内网 IPv4/IPv6，最长等待 1.5 秒并在会话内复用结果。现代浏览器通过 mDNS 隐藏地址时返回 `masked`，不支持或无候选时返回 `unavailable`，不会再用 `0.0.0.0` 冒充检测值。外网 IP 不由浏览器探测，而是由服务端根据 HTTP 连接和可信代理链覆盖。

数据用途限定为设备/环境完整性、自动化行为、会话重放和接口异常识别。传输幂等记录由服务端默认保存 14 天；Fact 和行为统计的 Kafka/下游保存周期不由 SDK 决定，部署方必须配置并在隐私政策中披露，且只保留完成风控目的所需的最短时间。

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
SynapAgent.markAction({ id: crypto.randomUUID(), name: 'login', phase: 'start' });
const metrics = SynapAgent.getMetrics();
```

`userData` 会拒绝空用户标识、超过 256 字符的标识、不可序列化数据以及超过 32 KiB 的数据，并返回 `boolean`。

`markAction` 只接受一次性动作 ID、动作名称和 `start | success | failure` 阶段。SDK 仅生成该动作窗口内的行为计数，不接收表单值。

### 辅助全局 API

以下非查询入口继续提供全局调用方式：

```js
initWithKey(appKey);                 // 返回 Promise
window.userData(userId, data);      // 返回 boolean
```

设备、评级和标签查询只提供 `SynapAgent.getDeviceInfo/getRatingInfo/getLabelInfo()` Promise API。

## 运行特性

- 策略缓存按 SDK 版本和 app key 隔离，损坏后自动重新获取。
- 同一时间只发送一个批次；每条消息拥有稳定 `messageId`，只有服务端明确 ACK 后才提交。
- 失败和未确认消息恢复到队首并指数退避；in-flight 批次也会参与页面生命周期持久化。
- 初始化幂等；`destroy()` 会移除 timer、事件监听器和调试检测资源。
- Canvas、WebGL、字体等静态设备信息每个页面会话只计算并上报一次；运行环境和位置仅在变化时上报。
- 原始鼠标轨迹不会上传，SDK 按动画帧采样并每 10 秒生成速度方差、停顿、直线比例、时间熵及点击/输入节奏统计；不采集输入值。
- `getMetrics()` 额外提供批次数、重试、ACK 失败、服务端重复、永久拒绝、in-flight 数量及 `networkInfo` 内网探测状态。

调试行为默认开启并仅作为低权重观察信号。代码格式化通过 `RuntimeData.custom.format` 上报，命中后服务端生成 `H5观察-代码格式化` LOW 标签和 `CODE_FORMATTING_DETECTED` reason code。`ErrorData` 收集脱敏后的错误模板、来源路径模板、行列、指纹和 5 秒窗口计数，不上传完整堆栈或原始 `console.error` 参数。

## 数据协议

第一版只使用原有 H5 codec 和五个固定接口。upload 的 `meta.protocolVersion` 固定为 `1`，每条消息携带稳定的 `messageId/sequence/observedAt`；只有服务端 ACK 的消息才会从队列删除。网络语义为“至少一次传输 + messageId 幂等消费”。

appKey 存在于浏览器代码中，原 H5 codec 不能证明请求来自真实探针，也不能替代 HTTPS。生产环境必须同时使用 HTTPS、精确 Origin 白名单、可信代理配置和服务端风险信号。
