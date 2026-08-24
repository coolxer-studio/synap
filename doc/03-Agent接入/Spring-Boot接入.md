# Spring Boot 接入

Spring Boot Agent 面向 Java 17、Spring Boot 3.2–3.x。完整部署由 Starter 与 Java Agent 组成：Starter 发现 API 和安全上下文，Java Agent 观测危险 Sink。

## 构建

```bash
cd synap-agent/agent-springboot
mvn verify
```

Java Agent 产物：

```text
agent-springboot-javaagent/target/
  agent-springboot-javaagent-1.0.0-SNAPSHOT-agent.jar
```

## 应用依赖

```xml
<dependency>
  <groupId>com.coolxer.synap</groupId>
  <artifactId>agent-springboot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 配置示例

```yaml
synap:
  agent:
    enabled: true
    server-url: https://risk.example.com
    app-id: 10001
    app-name: order-service
    app-version: 2026.08.12
    app-key-id: server-order-prod
    server-key-id: server-key-2026
    trust-anchor: ${SYNAP_SC_TRUST_ANCHOR}
    data-dir: /var/lib/synap/order-service
    mode: observe
    api:
      enabled: true
      observation-window: 60
      max-assets: 5000
      body-summary-allowlist: []
      trusted-proxies: []
    spool:
      max-items: 10000
      max-bytes: 104857600
```

```bash
java -javaagent:/opt/synap/agent-springboot-javaagent.jar -jar app.jar
```

未加载 Java Agent 时，Starter 仍提供 API 资产、运行时信息和业务埋点，并报告 `degraded-no-javaagent`。仅加载 Java Agent 时能力标记为 `javaagent-only`。缺少 app key ID、Server key ID 或信任锚时不会连接，也不会回退明文。

当前配置校验接受 HTTP 和 HTTPS Server URL，远程 HTTP 不再被 Agent 主动拒绝。生产
仍应使用 HTTPS；协议 v1 的应用层加密不替代 TLS 证书验证和网络元数据保护。Server
默认仍要求 TLS，HTTP 联调还需要显式的非生产 Server 配置。

## 容器接入

```dockerfile
COPY agent-springboot-javaagent.jar /opt/synap/agent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/synap/agent.jar"
VOLUME ["/var/lib/synap"]
```

Kubernetes 可通过 Init Container 下发 JAR，通过 Secret 注入安全配置，并将 `synap.agent.data-dir` 挂载到 `emptyDir` 或 PVC。需要跨重启保持身份和待发送记录时应使用持久存储。

## 观察和保护模式

默认 `observe` 不改变业务结果。`protect` 只允许本地内置版本化规则：

- `COMMAND_INJECTION_V1`
- `SSRF_PRIVATE_TARGET_V1`
- `PATH_TRAVERSAL_V1`

SQL、JNDI、反序列化和 SpEL 在首版只告警。上下文缺失、异常、超时或过载均 fail-open。紧急回滚时先切回 `observe`；必要时移除 `-javaagent`，Starter 将继续降级运行。

## 隐私和可靠性

默认上传路由模板、方法、状态码族、延迟桶、大小和安全头结果，不上传 Cookie、Authorization、Session ID、原始 query、正文或完整堆栈。spool 默认上限为 10,000 条、100 MiB、7 天；只有 accepted/duplicate ACK 后才删除记录。
