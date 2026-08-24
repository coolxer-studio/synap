# Qt 桌面探针

Qt 5.15/6.x 的 Windows、macOS、Linux 桌面应用使用 `synap-agent/agent-qt`。该 SDK
在业务进程内运行，服务端平台仍为 `host`，无需增加 Qt Fact 或 Controller 路由。

它提供设备、当前应用运行时、基础网络和显式 UserData 采集，不提供文件扫描、全局
进程/端口扫描或系统审计；这些能力使用独立的 `agent-host`。

构建、CMake/qmake 配置、C++/QML API、安全身份、队列和排障细节见
`synap-agent/agent-qt/README.md` 与 `synap-agent/agent-qt/doc/接入-Qt.md`。

生产接入必须配置 HTTPS Origin、数字业务 appId、安全通信 appId、Server key ID、
P-256 SPKI 信任锚和短时单次注册令牌。SDK 不允许生产 HTTP，也不会在安全通信失败后
回退明文。

