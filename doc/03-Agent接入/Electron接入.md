# Electron 接入

Electron Agent 位于 `synap-agent/agent-electron`，支持 Electron 28+ 的 Windows、macOS
与 Linux 应用。它使用指定主窗口、隔离 preload 和 H5 renderer 探针组成一个会话，
Server 侧平台仍为 `h5`。

## 安全边界

- 页面必须使用 HTTPS 或 standard + secure 的自定义协议，不支持 `file://`。
- BrowserWindow 必须关闭 Node integration，开启 context isolation、sandbox 和 webSecurity。
- 主进程必须为指定窗口配置精确 `allowedOrigins`；IPC 同时校验 sender、主 frame 和当前 Origin。
- preload 只暴露上下文读取、事件订阅和 ACK，不暴露任意 IPC 或 Node/Electron API。

完整代码、构建命令、Server Origin 配置和隐私范围见
`synap-agent/agent-electron/README.md`。生产环境使用 HTTPS Server；只有未打包开发应用可连接
loopback HTTP。

## 生命周期

1. 在 `app.ready` 前注册安全自定义协议。
2. 创建 BrowserWindow 并调用 `registerElectronAgent`。
3. renderer 调用 `initElectronAgent`；成功后再使用查询和业务标记 API。
4. 页面退出时调用 `destroyElectronAgent({ flush: true })`，窗口销毁时主进程 registration 自动释放。

首版只注册一个主窗口。其他窗口不会创建会话；若需要窗口级风险视图，应由业务显式选择
另一个窗口作为主窗口，而不是重复初始化多个探针。
