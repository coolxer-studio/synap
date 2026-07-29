# 主机探针
## 调试开发
go run .  

## 编译产物
目前该项目采用makefile 方式构建，请确保您的机器：

- 可以执行makefile 文件。
- 已安装golang ，并且已设置Path 路径

请使用命令：

```
make build
```

根据提示选择目标操作系统（linux、windows、darwin）、目标架构平台（可选项为：amd64、arm64）