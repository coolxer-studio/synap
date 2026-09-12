# 扩展 Fact 与规则

新增普通平台 Fact 时按以下顺序工作：

1. 在 `checker_config/<platform>.json` 增加 `<type>Config`。
2. 在 `model/fact/<platform>/` 新增 `<Platform><Type>Fact.java`。
3. 如需自动填充，使用 `@ValueSet` 并保持字段名与配置 key 一致。
4. 在 rule 目录新增 `<Platform><Type>FactRule.groovy`。
5. 在 `TopicDefine` 增加 Kafka topic 常量和 `NewTopic` Bean。
6. 更新集中 API/Fact 文档和子项目就近说明。
7. 增加构造、边界、规则、幂等与 Kafka 发布测试。

## 规则要求

- reason code 稳定、可搜索、可解释。
- 输出 detectorVersion、ruleVersion、证据数量和置信度。
- 新规则默认 `observation=true`。
- 单个客户端信号不直接触发高风险动作。
- 阈值、时间窗口和聚合规则必须可复现实验。
- 规则异常不能产生伪造证据；需要明确日志与回退行为。

## 契约检查

```text
Agent 采集字段
  = checker 字段顺序/名称
  = Fact 字段与校验
  = rule 读取字段
  = Kafka 下游预期
  = 文档中的示例与说明
```

任何一处不一致都可能表现为 Fact 跳过、规则漏报、下游解析失败或无法回滚。
