# Synap workspace

工作区包含：

- `zenvis-business-service-spring-boot-starter`：独立的 Zenvis 心跳与事件上报 Starter。
- `synap-server`：使用该 Starter 的 Synap 业务服务。
- `synap-agent`：Synap Agent 工程。

构建 Starter 和 `synap-server`：

```bash
JAVA_HOME=/path/to/jdk-17 mvn clean install
```

只运行 Starter 测试：

```bash
mvn -pl zenvis-business-service-spring-boot-starter test
```
