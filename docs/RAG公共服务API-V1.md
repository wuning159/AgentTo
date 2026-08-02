# RAG 公共服务 API V1

## 1. 范围

本文档描述 `rag-service` 对外提供的稳定查询接口。管理端接口（`/api/admin/**`）和内部检索接口不属于本版本公共 API。

服务默认地址为 `http://127.0.0.1:18473`，公共查询端点为：

```http
POST /api/v1/rag/query
Authorization: Bearer <client-api-key>
Content-Type: application/json
```

调用方身份由 API Key 认证得到，客户端不能在请求体中指定 `clientAppId`。知识库路由和 ACL 均在服务端执行。

## 2. 请求

```json
{
  "query": "预算如何审批？",
  "finalLimit": 8
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `query` | string | 是 | 非空查询文本 |
| `finalLimit` | integer | 否 | 最终证据上限，默认 `8`，必须大于 `0` |

## 3. 成功响应

HTTP `200` 表示请求已完成。业务拒答也使用 `200`，由 `data.decision` 区分。

```json
{
  "code": "OK",
  "message": "操作成功",
  "traceId": "req-20260802-001",
  "data": {
    "decision": "ANSWERED",
    "answer": "预算审批分为部门审批和财务复核两个阶段。",
    "citations": [
      {"chunkId": "chunk-001", "quote": "预算审批分为部门审批和财务复核两个阶段"}
    ],
    "attempts": [
      {"attemptNo": 1, "query": "预算如何审批？", "evidenceDecision": "SUFFICIENT", "evidenceCount": 2, "note": null}
    ],
    "traceUid": "trace-001"
  }
}
```

`citations` 只包含本次检索实际返回且通过真实性校验的切片引用；`attempts` 展示第一次检索以及可选的一次改写重试。

## 4. 业务决策

| `decision` | 含义 | `answer` |
|---|---|---|
| `ANSWERED` | 证据充分且引用校验通过 | 有值 |
| `NO_RELEVANT_KNOWLEDGE_BASE` | 没有可访问且相关的知识库 | `null` |
| `INSUFFICIENT_EVIDENCE` | 一次检索或一次改写重试后证据仍不足 | `null` |
| `INVALID_CITATION` | 生成结果引用不存在或引用文本不真实 | `null` |
| `GENERATION_UNAVAILABLE` | 未配置可用 ChatModel | 由 HTTP 503 响应表示 |

查询改写最多发生一次；没有有效改写时不会进入无界重试。

## 5. HTTP 错误

| HTTP 状态 | `code` | 场景 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` / `BAD_REQUEST` | 查询为空、`finalLimit` 非法、JSON 或参数格式错误 |
| `401` | `UNAUTHORIZED` | 缺少、无效或已过期的 Bearer API Key |
| `404` | `NOT_FOUND` | 请求资源不存在 |
| `405` | `METHOD_NOT_ALLOWED` | HTTP 方法不支持 |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Content-Type 不支持 |
| `503` | `GENERATION_UNAVAILABLE` | ChatModel 未启用或答案生成不可用 |
| `500` | `INTERNAL_ERROR` | 未分类服务端异常，使用 `traceId` 排查 |

错误响应保持统一结构：

```json
{
  "code": "UNAUTHORIZED",
  "message": "API Key 无效或已过期",
  "data": null,
  "traceId": "req-20260802-002"
}
```

## 6. 脱敏和安全约束

- 文档示例不包含真实 API Key、Cookie、模型密钥或数据库密码。
- API Key 只以 Bearer Header 传输，数据库保存不可逆哈希和安全前缀。
- `traceId` 可用于日志关联，但不能替代认证凭证。
- 生产环境必须设置 `RAG_CLIENT_KEY_PEPPER`、管理员密码和模型/基础设施凭证。

## 7. 本地验证

```powershell
# 启动基础设施
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d mysql redis elasticsearch minio tei-embedding tei-rerank

# 后端完整验证（含 Testcontainers 和 JaCoCo 当前门禁）
cd rag-service/backend
mvn.cmd clean verify
```
