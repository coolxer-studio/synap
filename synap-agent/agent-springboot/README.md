# Synap Server / Spring Boot Agent

`agent-springboot` 是 Synap 面向 Java 17、Spring Boot 3.2–3.x 的运行时安全探针。完整部署由 Starter 与 `-javaagent` 组成：Starter 发现 Spring MVC/WebFlux API 与安全上下文，Java Agent 监测危险 Sink，二者通过 bootstrap 可见的 JDK-only bridge 汇入唯一 Runtime，并复用 Synap 安全通信协议和风险平台。

## 模块

| 模块 | 职责 |
| --- | --- |
| `agent-springboot-api` | `SynapAgent` 稳定业务埋点 API，不依赖 Spring |
| `agent-springboot-bridge` | 跨 ClassLoader 请求上下文、早期事件与 Sink 决策桥 |
| `agent-springboot-core` | 标准 Java SDK 协议 v1 传输、Fact、脱敏、ACK/sequence 和有界落盘队列 |
| `agent-springboot-starter` | 自动配置、MVC/WebFlux 路由资产、运行观测与安全配置 |
| `agent-springboot-javaagent` | `premain/agentmain`、Byte Buddy Sink 插桩与 shaded JAR |
| `agent-springboot-testkit` | canary 敏感数据断言等测试支持 |
| `agent-springboot-demo` | MVC、WebFlux 依赖、Security、JDBC/命令演示应用 |

## 构建

```bash
cd synap-agent/agent-springboot
mvn verify
```

Java Agent 产物位于：

```text
agent-springboot-javaagent/target/agent-springboot-javaagent-1.0.0-SNAPSHOT-agent.jar
```

## Spring Boot 接入

在应用中加入 Starter：

```xml
<dependency>
  <groupId>com.coolxer.synap</groupId>
  <artifactId>agent-springboot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

生产配置示例：

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

启动时同时加载 Java Agent：

```bash
java -javaagent:/opt/synap/agent-springboot-javaagent.jar -jar app.jar
```

未加载 Java Agent 时 Starter 仍提供 API 资产、Spring/Java 运行时信息和业务埋点，并上报 `degraded-no-javaagent`。仅加载 Java Agent 时可使用 `SYNAP_AGENT_*` 与 `SYNAP_SC_*` 环境变量启动，能力标记为 `javaagent-only`。普通配置如服务地址使用 `SYNAP_AGENT_SERVER_URL`，安全通信凭据继续使用 `SYNAP_SC_APP_ID`、`SYNAP_SC_SERVER_KEY_ID` 和 `SYNAP_SC_TRUST_ANCHOR`。缺少 app key ID、server key ID 或信任锚时不会建立连接，也不会回退到明文。

安全传输使用 `secure-communication-java:1.1.0-SNAPSHOT`，设备类型固定为 `SERVER`。
新身份保存在 `data-dir/secure-communication/java-sdk-identity.json`；旧版 `identity.json`
不会迁移或删除，升级后首次连接需要新的单次 `SERVER` 注册令牌。

## 容器接入

Docker 镜像中复制 Agent，并为 spool 提供持久目录：

```dockerfile
COPY agent-springboot-javaagent.jar /opt/synap/agent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/opt/synap/agent.jar"
VOLUME ["/var/lib/synap"]
```

Kubernetes 可通过 Init Container 下发 JAR，使用 Secret 注入 `SYNAP_SC_APP_ID`、`SYNAP_SC_SERVER_KEY_ID`、`SYNAP_SC_TRUST_ANCHOR`，并把 `emptyDir` 或 PVC 挂载到 `synap.agent.data-dir`。不要把信任锚或注册凭据写入镜像。

## 采集与隐私边界

默认只上传路由模板、HTTP 方法、状态码族、延迟桶、请求/响应大小、Content-Type/鉴权与安全头结果；不上传 Cookie、Authorization、Session ID、原始 query、请求/响应正文或完整堆栈。路径参数不会进入 `assetId`，动态请求统一聚合为 `/dynamic/unmapped`。

危险 Sink 事件只包含目标 SHA-256、应用栈指纹、关联结果和有限上下文。客户端 IP 使用每日 HMAC；敏感键会在业务 API 和 HTTP 输入关联前拒绝。`body-summary-allowlist` 是显式授权入口，首版实现不读取 body 原文；后续字段结构采集也必须保持强制敏感键拒绝。

## 危险 Sink 与保护模式

首版 Hook 覆盖 JDBC、JDK/Apache/OkHttp/Spring 出站 HTTP、常用文件 API、`Runtime.exec`/`ProcessBuilder`、JNDI、Java 反序列化和 SpEL。它关联“当前请求输入与 Sink 参数的规范化匹配”，不做跨变量完整污点传播。

默认 `observe` 不改变业务结果。`protect` 只允许本地内置版本化规则：

- `COMMAND_INJECTION_V1`
- `SSRF_PRIVATE_TARGET_V1`
- `PATH_TRAVERSAL_V1`

```yaml
synap:
  agent:
    mode: protect
    protection:
      enabled-rule-ids:
        - COMMAND_INJECTION_V1
```

SQL、JNDI、反序列化与 SpEL 首版只告警。上下文缺失、规则异常、超时或探针过载全部 fail-open。回滚时先改回 `mode: observe`；紧急情况下移除 `JAVA_TOOL_OPTIONS` 中的 `-javaagent`，Starter 会继续以降级模式运行。

## 业务 API

```java
SynapAgent.setUser(opaqueUserId, attributes);
SynapAgent.markAction(new ActionEvent(id, name, ActionEvent.Phase.SUCCESS, attributes));
SynapAgent.reportSecurityEvent(event);
SynapAgent.flush();
AgentHealth health = SynapAgent.health();
```

标识长度、属性数量/大小和敏感键会被强制校验；禁止提交密码、令牌、Cookie、支付卡或身份证明原值。

## 传输与可靠性

客户端使用 P-256、HKDF-SHA256、AES-256-GCM 访问 `/sc/v1/handshake`、`/sc/v1/handshake/finish` 与 `/sc/v1/message`，密文中的逻辑路径仍为 `/attach/start` 和 `/attach/upload`。Start 固定 sequence 1，普通 Fact 从 2 开始；重试只增加 attempt。只有 ACK 明确 accepted/duplicate 后才删除，永久拒绝进入 dead-letter。

spool 默认 10,000 条、100 MiB、7 天，单 envelope 不超过 256 KiB；Unix 目录/文件权限分别为 `0700`/`0600`。API 路由超过 5,000 后进入 overflow 聚合并上报健康事件。

## 当前边界

首版不支持 Spring Boot 2、Java 8/11、Spring Boot 4、GraalVM Native Image、完整污点传播、BOLA/业务越权判断、敏感响应识别或完整 OpenAPI Schema 推断。WebFlux 请求上下文写入 Reactor Context；脱离 Reactor 链且未传播上下文的自建线程会 fail-open 并只保留聚合观测。
