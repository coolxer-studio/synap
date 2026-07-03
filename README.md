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
- Groovy 3.0.17
- Jackson
- Lombok
- ProGuard

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

接口详情见 [doc/API.md](doc/API.md)。

主要接口：

| 方法 | 推荐路径 | 说明 |
| --- | --- | --- |
| POST | `/attach/start` | agent 启动，获取平台 checker 配置并执行启动规则 |
| POST | `/attach/upload` | agent 上报采集数据，执行对应 Fact 规则 |
| POST | `/cross/{id}` | 按 `msg_api_config.json` 配置查询外部服务 |

Controller 中还保留了数字别名：

- `/attach` 也可用 `/1`
- `/attach/start` 的方法别名是 `/1`
- `/attach/upload` 的方法别名是 `/2`
- `/cross` 也可用 `/2`

建议新调用方优先使用语义化路径。

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
spring.kafka.bootstrap-servers=192.168.1.201:9092
```

注意：当前 `application-dev.properties` 和 `application-prod.properties` 中的 `rule.engine.groovy.path` 指向 `.../rule/groovy/`，但仓库里的 Groovy 文件实际位于 `src/main/java/com/coolxer/rule/`。本地启动或部署前需要确认该路径是否符合实际目录。

## 本地运行

前置依赖：

- JDK 17
- Maven
- 可访问的 Kafka
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
- `cross` 接口找不到配置 id 时会抛出 `IllegalArgumentException`。
