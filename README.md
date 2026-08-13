# synap-server

微信小程序 v1 ACK、幂等与观察规则见 [doc/Wechat检测规则.md](doc/Wechat检测规则.md)。

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
  -> 平台结构校验（H5 额外校验 Origin / Fetch Metadata，WeChat 校验 appId）
  -> appKey codec 解码 + H5/WeChat 第一版协议校验
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

所有 Agent 的外层 HTTP 入口统一为 `/sc/v1/handshake`、
`/sc/v1/handshake/finish` 和 `/sc/v1/message`。下表是解密后允许转发的逻辑路由，
不能以明文直接访问：

| 方法 | 推荐路径 | 说明 |
| --- | --- | --- |
| POST | `/attach/start` | agent 启动，获取 H5 checker 配置并执行启动规则 |
| POST | `/attach/upload` | agent 上报采集数据，执行对应 Fact 规则并返回 ACK |
| POST | `/cross/info` | 设备信息查询 |
| POST | `/cross/rating` | 风险评级查询 |
| POST | `/cross/label_start` | 当次启动标签查询 |

H5、Host、Android 和 iOS 统一使用协议 v1；微信小程序本版本不支持，不能连接
新版生产服务端。

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

Host 探针可在 common 中提交经第三方服务查询的公网 IP；服务端仅在它是合法公网 IPv4/IPv6 时保留，否则使用连接来源地址。实际 TCP/可信代理来源仍独立记录在 Fact 的 `requestIp` 中。其他平台的外网 IP 继续以服务端收到的连接地址为准。只有请求的直接来源匹配 `synap.network.trusted-proxies` 时，才会解析标准 `Forwarded`、`X-Forwarded-For` 或 `X-Real-IP`，并从代理链右侧向左剥离可信节点。例如：

```properties
synap.network.trusted-proxies=10.20.0.0/16,2001:db8:100::/48
```

生产环境通过 `SYNAP_TRUSTED_PROXIES` 配置实际网关 CIDR；服务直接暴露时保持为空。不要为省事配置 `0.0.0.0/0`，否则客户端可伪造来源 IP。代理链解析结果写入 Fact 的 `requestIp`；除通过公网地址校验的 Host 声明值外，服务端会用该来源地址覆盖 `common.wanIp`。

生产环境必须显式配置浏览器 Origin，空值会拒绝跨域请求：

```properties
SYNAP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com
```

安全通信协议 v1 默认关闭；启用时必须同时提供 Redis、允许的公开 `appId`、服务端
P-256 身份密钥、会话记录加密密钥和管理接口令牌：

```properties
SYNAP_SC_ENABLED=true
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

风险输出统一包含 `detectorVersion/ruleVersion/reasonCodes/evidenceCount/confidence`，新规则默认 `observation=true`。分数按会话聚合并时间衰减；单个客户端信号不直接触发高风险处罚。

## 本地运行

前置依赖：

- JDK 17
- Maven
- 可访问的 Kafka
- 生产环境可访问的 Redis；开发 profile 默认使用有界内存去重存储
- 可访问的外部服务地址，见 `msg_api_config.json`

开发 profile 会可选加载 `.local/sc-dev/application-local.properties`。需要启用
安全通信时，在该 Git 已忽略的文件中保存本地密钥；密钥必须在服务
重启后保持不变，否则 Redis 中已有的安全会话将无法解密。本地密钥文件
应设置为仅当前用户可读。

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
- 所有 Agent 使用“至少一次传输 + messageId 幂等消费”；Redis `done` 记录默认保存 14 天，处理中租约为 5 分钟。安全 SDK 不自动重试业务 POST。
- H5 的 `appId` 是公开标识；身份保证依赖 HTTPS、服务端公钥固定、Origin 白名单和不可导出的 WebCrypto 安装密钥，强度低于原生端的一次性令牌注册。
- `cross` 接口找不到配置 id 时会抛出 `IllegalArgumentException`。
