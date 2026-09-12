# synap-agent-electron

`synap-agent-electron` 将 Synap H5 安全探针接入 Electron 28+。主进程只提供经过
净化的桌面运行时和安全配置，preload 通过固定的窄 IPC 桥传递数据，指定主窗口的
renderer 继续使用 H5 安全通信协议、IndexedDB 安装身份、队列和 Fact。

Electron 在 Server 侧仍标识为 `h5`，不会引入新的平台、checker 或 Kafka Topic。

## 安装与构建

```bash
cd synap-agent/agent-electron
npm ci
npm run ci
```

包提供三个显式入口：`synap-agent-electron/main`、`/preload` 和 `/renderer`。不提供
容易在错误进程中加载的根入口。

## 主进程

自定义协议必须在 `app.ready` 前注册为 standard、secure，并启用 Fetch 与 CORS。
协议内容处理器仍由宿主应用自己实现，SDK 不抢占全局协议注册。

```js
const { app, BrowserWindow, protocol } = require('electron');
const { registerElectronAgent } = require('synap-agent-electron/main');

protocol.registerSchemesAsPrivileged([{
  scheme: 'my-desktop',
  privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true }
}]);

app.whenReady().then(() => {
  const window = new BrowserWindow({
    webPreferences: {
      preload: require.resolve('synap-agent-electron/preload'),
      nodeIntegration: false,
      contextIsolation: true,
      sandbox: true,
      webSecurity: true
    }
  });
  registerElectronAgent(window, { allowedOrigins: ['my-desktop://app'] });
  window.loadURL('my-desktop://app/index.html');
});
```

若应用已有 preload，应由应用的构建工具把 `import 'synap-agent-electron/preload'` 打入
同一个单文件 preload bundle；sandboxed preload 不应在运行时加载任意本地模块。

## Renderer

```js
import { initElectronAgent, destroyElectronAgent } from 'synap-agent-electron/renderer';

await initElectronAgent({
  baseUrl: 'https://synap.example.com',
  appId: 'desktop-app',
  serverTrustAnchors: {
    'server-key-2026': '<P-256 SPKI Base64URL>'
  },
  collectionProfile: 'low'
});

window.addEventListener('beforeunload', () => {
  destroyElectronAgent({ flush: true }).catch(() => {});
});
```

生产打包应用只接受 HTTPS Synap Server；未打包开发应用可使用 localhost、127.0.0.1
或 `::1` 的 HTTP Server。`file://` 和普通 HTTP renderer 会在任何网络请求前失败。

## Server 配置

- 将 `my-desktop://app` 加入 `SYNAP_CORS_ALLOWED_ORIGINS` 和对应 appId 的 Origin 白名单。
- 将公开 appId 加入 `SYNAP_SC_ALLOWED_APP_IDS`。
- 反向代理只暴露 `/sc/v1/**`，信任锚必须与 Server 身份公钥一致。
- Server 仍按 H5 接收 `DeviceData.capabilities.electron`、`IntegrityData` 和 `EventData`。

## 数据与限制

首版只支持一个显式注册的主窗口。采集应用/运行时版本、OS/架构、安全 webPreferences、
规范化 Origin/路径模板以及渲染进程生命周期事件；不采集文件路径、启动参数、环境变量、
用户名、主机名、MAC、进程列表、文件、端口、输入内容或 Cookie。生命周期事件在内存中
最多保留 32 条，renderer 写入 H5 持久队列后才通过 ACK 删除。

可运行示例位于 `demo/`：

```bash
npm run build
npx electron demo/main.cjs
```
