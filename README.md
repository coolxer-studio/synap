# Synap Server

Synap Server 是 Synap 安全探针平台的数据接入与规则执行服务。它负责接收 Agent 数据、下发采集配置、构造 Fact、执行 Groovy 规则，并将 Checkpoint 写入 Kafka。生产部署只应公开由安全通信协议 v1 保护的数据入口。

## 阅读导航

- 首次运行：参阅[本地开发](#本地开发)。
- 生产上线：参阅[生产部署](#生产部署)。
- 增加采集模型或规则：参阅[扩展 Fact 与规则](#扩展-fact-与规则)。

## 核心能力

- `start`：Agent 启动时获取当前平台的 checker 配置并执行启动规则。
- `upload`：接收批量采集数据，构造 Fact、执行规则并生成 Agenda、Punish 和风险结果。
- `cross`：按动态配置代理 Asset、Operation、Risk 等下游查询。
- 可靠处理：提供 messageId 幂等、sequence 观察、风险聚合和 Kafka Checkpoint 投递。
- 配置热加载：运行时重新加载 checker、punish、msg API 和 Groovy 规则。

## 技术栈

| 领域 | 组件 |
| --- | --- |
| 运行时 | Java 17、Spring Boot 3.2.0、Spring Web |
| 规则与序列化 | Groovy 3.0.17、Jackson |
| 消息与状态 | Spring Kafka、Spring Data Redis |
| 工程辅助 | Maven、Lombok、Docker |

## 目录结构

```text
src/main/java/com/coolxer
├── Application.java              # 启动类
├── config                        # Kafka topic、跨域、配置监听、JSON 工具配置
├── controller                    # HTTP 接口入口
├── integration                   # Zenvis 等外部集成
├── model                         # VO、Fact、Agenda、Punish、Checkpoint 等模型
├── rule                          # Groovy 规则脚本
├── service                       # 幂等、sequence、session 与业务服务
├── service/impl                  # 规则执行、动态配置和跨服务调用实现
└── utils                         # Jackson、反射、字符串转换工具

src/main/resources
├── application.properties        # 公共配置与默认 dev profile
├── application-dev.properties    # 本地开发配置
└── application-prod.properties   # 生产配置

deploy
├── docker-compose.yml            # 生产容器编排
├── .env                          # 环境变量模板
└── open_config
    ├── checker_config            # 各平台 checker 配置
    ├── identify_config           # 生产规则脚本
    ├── punish_config             # Agenda -> Punish 映射
    └── msg_api_config            # /cross/{id} 外部 API 配置
```

## 核心流程

```text
Agent / Server SDK
  -> AttachController
  -> 平台结构校验（H5 额外校验 Origin / Fetch Metadata，WeChat 校验 appId）
  -> 安全信封解密 + 逻辑请求校验
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

各平台 checker 配置位于 `deploy/open_config/checker_config/`：

- `android.json`
- `ios.json`
- `h5.json`
- `wechat.json`
- `host.json`
- `server.json`

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

## 接口与安全边界

本仓库不再维护独立的 `doc/` 副本，完整字段与协议定义以[阅读导航](#阅读导航)中的集中式文档为准。

支持安全通信 v1 的 Agent 统一使用 `/sc/v1/handshake`、
`/sc/v1/handshake/finish` 和 `/sc/v1/message`。下表是解密后允许转发的逻辑路由，
不能以明文直接访问：

| 方法 | 推荐路径 | 说明 |
| --- | --- | --- |
| POST | `/attach/start` | Agent 启动，获取对应平台的 checker 配置并执行启动规则 |
| POST | `/attach/upload` | Agent 上报采集数据，执行对应 Fact 规则并返回 ACK |
| POST | `/cross/info` | 设备信息查询 |
| POST | `/cross/rating` | 风险评级查询 |
| POST | `/cross/label_start` | 当次启动标签查询 |

H5、Host、Android、iOS 和 Server SDK 使用协议 v1。微信小程序当前不支持该协议，不能连接新版生产服务端。

## 配置说明

### Profile 与动态配置

默认 profile：

```properties
spring.profiles.active=dev
```

开发环境关键配置：

```properties
server.port=11099
rule.engine.groovy.path=src/main/java/com/coolxer/rule/
checker.config.path=deploy/open_config/checker_config/
punish.config.path=deploy/open_config/punish_config/punish_config.json
msg.api.config.path=deploy/open_config/msg_api_config/msg_api_config.json
spring.kafka.bootstrap-servers=127.0.0.1:9094
```

开发环境直接读取源码规则目录；生产环境通过 Compose 将 `deploy/open_config/*` 只读挂载到 `/app/config/*`。规则、checker、punish 和 msg API 配置必须作为同一发布版本管理。

### 代理、来源与跨域

Host 探针可在 common 中提交经第三方服务查询的公网 IP；服务端仅在它是合法公网 IPv4/IPv6 时保留，否则使用连接来源地址。实际 TCP/可信代理来源仍独立记录在 Fact 的 `requestIp` 中。其他平台的外网 IP 继续以服务端收到的连接地址为准。只有请求的直接来源匹配 `synap.network.trusted-proxies` 时，才会解析标准 `Forwarded`、`X-Forwarded-For` 或 `X-Real-IP`，并从代理链右侧向左剥离可信节点。例如：

```properties
synap.network.trusted-proxies=10.20.0.0/16,2001:db8:100::/48
```

生产环境通过 `SYNAP_TRUSTED_PROXIES` 配置实际网关 CIDR；服务直接暴露时保持为空。不要为省事配置 `0.0.0.0/0`，否则客户端可伪造来源 IP。代理链解析结果写入 Fact 的 `requestIp`；除通过公网地址校验的 Host 声明值外，服务端会用该来源地址覆盖 `common.wanIp`。

生产环境必须显式配置浏览器 Origin，空值会拒绝跨域请求：

```properties
SYNAP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

### 安全通信与风险状态

生产 profile 的安全通信协议 v1 默认关闭；启用时必须同时提供 Redis、允许的公开 `appId`、服务端
P-256 身份密钥、会话记录加密密钥和管理接口令牌：

```properties
SYNAP_SC_ENABLED=true
SYNAP_SC_REQUIRE_TLS=true
SYNAP_SC_SERVER_KEY_ID=server-key-2026
SYNAP_SC_SERVER_PRIVATE_KEY=<PKCS8 Base64URL，由部署密钥系统注入>
SYNAP_SC_SERVER_PUBLIC_KEY=<SPKI Base64URL>
SYNAP_SC_SESSION_RECORD_KEY=<32 字节 Base64URL>
SYNAP_SC_ALLOWED_APP_IDS=synap-h5,synap-host,synap-android,synap-ios
SYNAP_SC_ENROLLMENT_ADMIN_TOKEN=<管理面长随机令牌>
```

外部数据入口只有 `/sc/v1/handshake`、`/sc/v1/handshake/finish` 和
`/sc/v1/message`。解密后只转发 `/attach/start`、`/attach/upload`、
`/cross/{id}`；直接明文访问业务 Controller 会返回 404。原生端注册令牌由受管理的
`POST /admin/sc/v1/enrollment-tokens` 签发，且只能使用一次。

可信网关还可以注入 `X-Synap-Network-ASN`、`X-Synap-Network-Category`、`X-Synap-Tls-Fingerprint` 和 `X-Synap-Ip-Reputation`。这些头只有在直接来源命中 `SYNAP_TRUSTED_PROXIES` 时才会被接受；浏览器直连提交的同名头会被忽略。

H5 握手使用 Origin 白名单准入；`Referer/Sec-Fetch-*` 仅是外围浏览器信号，不是
密码学身份凭据。解密后的业务请求继续校验受保护 `code`、`header/common` 一致性、
Fact 数量、字符串长度和嵌套深度。生产使用互相隔离的 Redis 命名空间保存安全会话、
重放窗口、安装身份、注册令牌以及原有 messageId 幂等和业务 sequence。

风险输出统一包含 `detectorVersion`、`ruleVersion`、`reasonCodes`、`evidenceCount` 和 `confidence`。新规则默认使用 `observation=true`；分数按会话聚合并随时间衰减，单个客户端信号不会直接触发高风险处罚。

## 本地开发

前置依赖：

- JDK 17 和 Maven。
- 可访问的 Kafka。
- 可访问的 Redis；dev profile 的业务幂等、sequence 和风险状态使用内存，但安全通信会话仍使用 Redis。
- 使用 `/cross/*` 时，可访问 `deploy/open_config/msg_api_config/` 中配置的下游服务。

dev profile 的安全通信配置直接维护在 `src/main/resources/application-dev.properties` 中。开发密钥必须在服务重启后保持不变，否则 Redis 中已有会话将无法解密；不要在该文件中使用生产密钥。

在仓库根目录启动：

```bash
mvn spring-boot:run
```

默认启用 `dev` profile，监听 `11099`。启动失败时优先检查 Kafka、Redis，以及 `deploy/open_config/` 下的动态配置文件。

运行测试：

```bash
mvn test
```

## 构建并推送镜像

构建机需要 JDK 17、Maven 和 Docker。构建、测试均以 JDK 17 为基线，不要切换到未经验证的更高 JDK。

在 `synap-server` 根目录执行：

```bash
PUSH_IMAGE=true && ./build.sh
```

ARM64 服务器将 `IMAGE_ARCH` 改为 `arm64`。构建脚本会生成两个带架构后缀的标签：

```text
<IMAGE_NAME>:1.0.0.alpha-amd64
<IMAGE_NAME>:<YYYYMMDD>-amd64
```

`build.sh` 当前将版本标签固定为 `1.0.0.alpha`，同时生成日期标签；传入 `IMAGE_TAG` 不会覆盖它。生产发布应在脚本中更新版本并使用不可变标签，不要依赖 `latest`。如需使用其他镜像仓库，可指定 `IMAGE_NAME` 和 `BASE_IMAGE_REPOSITORY`。

## 扩展 Fact 与规则

1. 在 `deploy/open_config/checker_config/<platform>.json` 中增加 `<type>Config`。
2. 在 `src/main/java/com/coolxer/model/fact/<platform>/` 下新增 `<Platform><Type>Fact.java`。
3. 如需自动字段填充，在字段上添加 `@ValueSet`，并确保字段名与配置 key 对应。
4. 在 `src/main/java/com/coolxer/rule/` 下新增 `<Platform><Type>FactRule.groovy`，返回 `ArrayList<Agenda>`；发布时由 `build.sh` 同步到生产规则目录。
5. 在 `src/main/java/com/coolxer/config/TopicDefine.java` 中新增对应 Kafka topic 常量和 `NewTopic` Bean。
6. 更新 Synap 主仓库的集中接口文档和必要测试。

## 开发约束与已知行为

- `FactBuilder` 依赖平台名和 `type` 的命名约定；命名不一致会导致 Fact 构造失败并跳过该条数据。
- `punish` 使用 `HashSet` 去重，返回顺序当前不保证稳定，代码中也保留了按优先级排序的 TODO。
- 支持 ACK 的 Agent 使用“至少一次传输 + messageId 幂等消费”；Redis `done` 记录默认保存 14 天，处理中租约为 5 分钟。安全 SDK 不自动重试业务 POST。
- H5 的 `appId` 是公开标识；身份保证依赖 HTTPS、服务端公钥固定、Origin 白名单和不可导出的 WebCrypto 安装密钥，强度低于原生端的一次性令牌注册。
- `cross` 接口找不到配置 id 时会抛出 `IllegalArgumentException`。
