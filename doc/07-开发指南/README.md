# 开发指南

本章为需要修改 Synap 自身或扩展规则的开发人员提供入口。开始前先阅读目标子项目的 README；`synap-build` 目录还必须遵循其 `AGENTS.md`。

## 工作区

```text
synap/
├── synap-server/    Java 17 / Spring Boot 接收与规则服务
├── synap-agent/     多平台 Agent 与仿真器
├── synap-build/     Go 实现的模板驱动工程修改服务
└── doc/             集中文档站
```

- [子项目开发](子项目开发.md)
- [扩展 Fact 与规则](扩展Fact与规则.md)
- [文档维护](文档维护.md)

> 根仓库当前将三个子项目作为独立维护边界处理。提交前在实际修改的仓库中确认状态，不要把子项目构建产物或本地数据带入根仓库提交。
