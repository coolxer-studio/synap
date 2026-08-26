# Synap Build API

运行中的服务在 `/openapi.yaml` 提供 OpenAPI 3.1 文档。除健康检查、OpenAPI 和静态页面外，所有 `/api/v1/*` 都要求 Bearer Token。

## 端点

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/healthz` | 进程存活检查 |
| `GET` | `/readyz` | Git、数据目录、模板和执行器就绪检查 |
| `GET` | `/api/v1/templates` | 模板和动态字段元数据 |
| `GET` | `/api/v1/tasks` | 搜索、筛选和游标分页历史任务 |
| `POST` | `/api/v1/tasks` | 提交任务；已有活动任务时返回 409 |
| `GET` | `/api/v1/tasks/latest` | 获取最新任务；无任务时返回 204 |
| `GET` | `/api/v1/tasks/{id}` | 查询任务详情 |
| `DELETE` | `/api/v1/tasks/{id}` | 停止当前活动任务 |
| `GET` | `/api/v1/tasks/{id}/output/stream` | 原始脱敏输出 SSE |
| `GET` | `/api/v1/tasks/{id}/events/stream` | 可读事件 SSE |
| `GET` | `/api/v1/tasks/{id}/artifact` | 下载成功任务 ZIP |

## 示例

```bash
curl -H "Authorization: Bearer $SYNAP_BUILD_TOKEN" \
  http://127.0.0.1:11100/api/v1/templates

curl -H "Authorization: Bearer $SYNAP_BUILD_TOKEN" \
  'http://127.0.0.1:11100/api/v1/tasks?status=failed&limit=20'

curl -N -H "Authorization: Bearer $SYNAP_BUILD_TOKEN" \
  'http://127.0.0.1:11100/api/v1/tasks/TASK_ID/events/stream?offset=0'

curl -OJ -H "Authorization: Bearer $SYNAP_BUILD_TOKEN" \
  http://127.0.0.1:11100/api/v1/tasks/TASK_ID/artifact
```

## SSE 与恢复

两个 SSE 接口都使用 UTF-8 文件字节 offset，而不是事件数量。客户端保存最后的 offset，重连时通过 `?offset=` 请求重放。可读事件中相同 `OutputEvent.id` 的后续记录用于更新已有卡片。

## 状态与错误

任务状态枚举：`queued`、`preparing`、`running`、`packaging`、`succeeded`、`failed`、`canceled`。

- `400`：请求或模板参数不合法。
- `401`：Bearer Token 无效。
- `404`：任务不存在。
- `409`：已有活动任务、停止终态任务或交付包尚不可用。

下载成功响应包含 `X-Synap-Build-SHA256`，客户端应校验后再解压审查。
