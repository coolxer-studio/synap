# agent-qt

`agent-qt` 是可直接链接到 Qt 桌面应用的 Synap 轻量安全探针。它使用服务端已有的
`host` 平台契约，支持 Qt 5.15/6.x、Windows、macOS 和 Linux。

首版只采集当前应用需要的低敏感信息：设备/系统、当前进程、基础网络、磁盘和屏幕
参数。不会扫描文件、全局进程、端口、hosts、证书、环境变量内容、MAC、硬件序列号，
也不会跟踪 UI 行为或自动弹窗。完整主机审计继续使用 `agent-host`。

## 构建

依赖 CMake 3.16+、C++17、Qt Core/Network/Gui 和 OpenSSL 1.1.1+ 或 3.x。

```bash
cmake -S . -B build -DSYNAP_AGENT_QT_BUILD_TESTS=ON
cmake --build build --config Release
ctest --test-dir build --output-on-failure -C Release
cmake --install build --prefix /path/to/prefix
```

安装后在宿主 CMake 工程中使用：

```cmake
find_package(SynapAgentQt 1 REQUIRED)
target_link_libraries(my-desktop-app PRIVATE Synap::AgentQt)
```

qmake 工程可以直接编译 SDK 源码：

```qmake
include(/path/to/agent-qt/agent-qt.pri)
```

qmake 需要能够通过编译器默认搜索路径找到 OpenSSL；非标准安装位置请在宿主 `.pro`
中补充 `INCLUDEPATH` 和 `LIBS`。

## 初始化

```cpp
#include <synap/qt/Agent.h>

auto *agent = new Synap::Qt::Agent(qApp);
Synap::Qt::AgentConfig config;
config.baseUrl = QUrl("https://risk.example.com");
config.businessAppId = 7;                 // common/code 中的数字 appId
config.secureAppId = "desktop-product";  // 安全握手公开标识
config.serverKeyId = "server-key-2026";
config.trustAnchorBase64Url = "<P-256 SPKI Base64URL>";
config.appName = QCoreApplication::applicationName();
config.appPackage = "com.example.desktop";
config.appVersion = QCoreApplication::applicationVersion();
config.enrollmentTokenProvider = [] {
    return qEnvironmentVariable("SYNAP_SC_ENROLLMENT_TOKEN");
};

QString error;
if (!agent->configure(config, &error)) {
    qWarning() << error;
    return;
}
connect(agent, &Synap::Qt::Agent::reminderReceived,
        [](const QString &title, const QString &message) {
    // 由宿主决定 Widgets/QML 的展示方式。
});
agent->start();
```

`start()`、`stop()` 可以重复调用且不会阻塞 UI。查询接口返回 requestId，结果通过
`queryFinished(kind, requestId, payload)` 异步返回：

```cpp
const QString requestId = agent->requestRating();
agent->submitUserData("user-42", {{"plan", "pro"}});
```

QML 项目不需要额外插件。由 C++ 创建并配置 `Agent`，再通过 context property 或
单例对象暴露；`state`、`queueDepth`、start/stop、查询及 UserData API 均可直接使用。

## 安全与可靠性

- 生产只接受 HTTPS，TLS 最低 1.2。localhost HTTP 必须显式设置
  `allowInsecureLocalhost=true`，不能用于生产。
- 应用层使用协议 v1：P-256、HKDF-SHA256、AES-256-GCM；Server SPKI 必须匹配固定
  key ID 和信任锚，任何失败都不会回退明文。
- 注册令牌只在第一次握手时调用 provider，不落盘、不写日志。
- 安装身份与队列默认位于 `QStandardPaths::AppLocalDataLocation/synap-agent-qt/<appId>`。
  Unix 权限为目录 0700、文件 0600；Windows 使用当前用户专属 DACL。
- 默认队列为 10 MiB / 1000 条 / 7 天。只有 accepted 或 duplicate ACK 才删除；
  临时失败指数退避，永久拒绝进入 `dead-letter`。

更多部署、QML、隐私和排障说明见 [doc/接入-Qt.md](doc/接入-Qt.md)。

