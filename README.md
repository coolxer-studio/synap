# synap-server

synap-server 是 synap 的请求响应服务，负责接收 agent 数据、下发采集配置和控制动作，并将规则命中结果写入 Kafka。

## 项目定位

- `start`：agent 启动时获取当前平台的 checker 配置，并执行启动规则。
- `upload`：agent 上报采集数据，服务端构造 Fact、执行 Groovy 规则、生成 agenda 和 punish。
- `cross`：按配置代理查询外部服务，例如设备信息、风险标签、风险指数等。
- 配置热加载：checker、punish、msg API 配置文件变更后会自动重新加载。

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Spring Web
- Spring Kafka
- Spring Data Redis（生产幂等存储）
- Groovy 3.0.17
- Jackson
- Lombok

## 目录结构

```text
src/main/java/com/coolxer
├── Application.java              # 启动类
├── config                        # Kafka topic、跨域、配置监听、JSON 工具配置
├── controller                    # HTTP 接口入口
├── model                         # VO、Fact、Agenda、Punish、Checkpoint 等模型
├── rule                          # Groovy 规则脚本
├── service                       # 服务接口
├── service/impl                  # 规则执行、配置加载、跨服务调用实现
└── utils                         # Jackson、反射、字符串转换工具

src/main/resources
├── checker_config                # 各平台 checker 配置
├── punish_config                 # agenda -> punish 动作配置
├── msg_api_config                # /cross/{id} 外部 API 配置
└── application-*.properties      # 环境配置
```

## 核心流程

```text
Agent
  -> AttachController
  -> Origin / Fetch Metadata / 结构校验
  -> H5 codec 解码 + 第一版协议校验
  -> sequence 缺口 / 倒序观察
  -> messageId 幂等检查
  -> FactBuilder / StartFact
  -> RuleEngineImpl
  -> Groovy Rule
  -> Agenda
  -> PunishConfig
  -> ResponseVO + Kafka Checkpoint
```

核心代码入口：

- 启动类：`src/main/java/com/coolxer/Application.java`
- 数据接口：`src/main/java/com/coolxer/controller/AttachController.java`
- 跨服务接口：`src/main/java/com/coolxer/controller/CrossController.java`
- Fact 构造：`src/main/java/com/coolxer/model/fact/FactBuilder.java`
- Fact 字段填充：`src/main/java/com/coolxer/model/fact/AbstractFact.java`
- 规则执行：`src/main/java/com/coolxer/service/impl/RuleEngineImpl.java`
- Kafka topic：`src/main/java/com/coolxer/config/TopicDefine.java`

## 数据模型约定

上报数据采用“配置定义字段顺序，agent 传数组值”的紧凑格式。

`common` 公共字段顺序定义在 `CheckerConfig` 中，例如 `userId`、`guid`、`startId`、`sdkVersion`、`appId`、`platform` 等。

每个平台的 checker 配置位于：

- `src/main/resources/checker_config/android.json`
- `src/main/resources/checker_config/ios.json`
- `src/main/resources/checker_config/h5.json`
- `src/main/resources/checker_config/wechat.json`
- `src/main/resources/checker_config/host.json`

H5 的 `selfAppAllowlist` 按 appId 配置允许域名；未配置时仅允许 `common.appPackage` 对应的当前站点域名，不再使用硬编码测试域名。

upload 数据中的 Fact 类型通过命名约定反射构造：

```text
platform=android + type=DeviceData
=> com.coolxer.model.fact.android.AndroidDeviceFact

platform=ios + type=RuntimeData
=> com.coolxer.model.fact.ios.IosRuntimeFact
```

对应规则脚本也按 Fact 类名命名：

```text
AndroidDeviceFact -> AndroidDeviceFactRule.groovy
IosRuntimeFact   -> IosRuntimeFactRule.groovy
```

## 接口文档

接口详情见 [doc/API.md](doc/API.md)，H5 Fact、观察规则、`ErrorData`、代码格式化标签和服务端验收场景见 [H5 检测规则与验收说明](doc/H5检测规则.md)。

对外 H5 第一版固定使用以下接口：

| 方法 | 推荐路径 | 说明 |
| --- | --- | --- |
| POST | `/sc/h5/attach/start` | agent 启动，获取 H5 checker 配置并执行启动规则 |
| POST | `/sc/h5/attach/upload` | agent 上报采集数据，执行对应 Fact 规则并返回 ACK |
| POST | `/sc/h5/cross/info` | 设备信息查询 |
| POST | `/sc/h5/cross/rating` | 风险评级查询 |
| POST | `/sc/h5/cross/label_start` | 当次启动标签查询 |

Android、iOS、Wechat 和 Host 仍使用各自现有路由，不受 H5 第一版契约影响。

## 配置说明

默认 profile：

```properties
spring.profiles.active=dev
```

开发环境关键配置：

```properties
server.port=11099
checker.config.path=src/main/resources/checker_config/
punish.config.path=src/main/resources/punish_config/punish_config.json
msg.api.config.path=src/main/resources/msg_api_config/msg_api_config.json
spring.kafka.bootstrap-servers=127.0.0.1:9094
```

外网 IP 以服务端收到的连接地址为准。只有请求的直接来源匹配 `synap.network.trusted-proxies` 时，才会解析标准 `Forwarded`、`X-Forwarded-For` 或 `X-Real-IP`，并从代理链右侧向左剥离可信节点。例如：

```properties
synap.network.trusted-proxies=10.20.0.0/16,2001:db8:100::/48
```

生产环境通过 `SYNAP_TRUSTED_PROXIES` 配置实际网关 CIDR；服务直接暴露时保持为空。不要为省事配置 `0.0.0.0/0`，否则客户端可伪造外网 IP。解析结果会覆盖客户端提交的 `common.wanIp`，并写入 Fact 的 `requestIp`。

生产环境必须显式配置浏览器 Origin，空值会拒绝跨域请求：

```properties
SYNAP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
# 按 appKey 的 SHA-256 前 32 个十六进制字符配置
SYNAP_APP_ORIGINS=8f5c2a4d9b7e1f603ac8d042b97e6c15=https://app.example.com|https://m.example.com
```

可信网关还可以注入 `X-Synap-Network-ASN`、`X-Synap-Network-Category`、`X-Synap-Tls-Fingerprint` 和 `X-Synap-Ip-Reputation`。这些头只有在直接来源命中 `SYNAP_TRUSTED_PROXIES` 时才会被接受；浏览器直连提交的同名头会被忽略。

请求会同时校验 `Origin/Referer/Sec-Fetch-*`、`header/common` 一致性、Fact 数量、字符串长度和嵌套深度。H5 仅接受 `protocolVersion=1` 的批次契约；生产使用 Redis 保存 messageId 幂等、sequence 和风险聚合状态。

风险输出统一包含 `detectorVersion/ruleVersion/reasonCodes/evidenceCount/confidence`，新规则默认 `observation=true`。分数按会话聚合并时间衰减；单个客户端信号不直接触发高风险处罚。

## 本地运行

前置依赖：

- JDK 17
- Maven
- 可访问的 Kafka
- 生产环境可访问的 Redis；开发 profile 默认使用有界内存去重存储
- 可访问的外部服务地址，见 `msg_api_config.json`

启动：

```bash
mvn spring-boot:run
```

默认端口：`11099`。

运行测试：

```bash
mvn test
```

## 新增 Fact / 规则的步骤

1. 在对应平台的 `checker_config/<platform>.json` 中增加 `<type>Config`。
2. 在 `model/fact/<platform>/` 下新增 `<Platform><Type>Fact.java`。
3. 如需自动字段填充，在字段上添加 `@ValueSet`，并确保字段名与配置 key 对应。
4. 在 `rule/` 下新增 `<Platform><Type>FactRule.groovy`，返回 `ArrayList<Agenda>`。
5. 在 `TopicDefine` 中新增对应 Kafka topic 常量和 `NewTopic` Bean。
6. 更新接口文档和必要测试。

## 已知注意点

- `FactBuilder` 依赖平台名和 `type` 的命名约定；命名不一致会导致 Fact 构造失败并跳过该条数据。
- `start` 接口中规则执行失败时，当前代码可能在读取 `checkpoint` 时触发空指针；`upload` 接口已经做了空值判断。
- `punish` 使用 `HashSet` 去重，返回顺序当前不保证稳定，代码中也保留了按优先级排序的 TODO。
- H5 使用“至少一次传输 + messageId 幂等消费”；Redis `done` 记录默认保存 14 天，处理中租约为 5 分钟。
- appKey 存在于浏览器代码中，H5 codec 不构成真实探针或硬件可信证明，生产必须使用 HTTPS。
- `cross` 接口找不到配置 id 时会抛出 `IllegalArgumentException`。
