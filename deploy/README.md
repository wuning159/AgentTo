# AgentTo 部署说明

当前 Compose 默认启动 MySQL、Redis、Elasticsearch、MinIO、TEI Embedding 和 TEI Rerank。开发阶段的前端和后端在开发机运行，不部署到服务器。公司现有工作流也不放进这套 Compose，后续通过适配接口接入。

## 启动前准备

1. 将 `deploy/.env.example` 复制为 `deploy/.env`，填写数据库、Redis、Elasticsearch、MinIO 和 DeepSeek 密钥。
2. 确认 `MODEL_ROOT` 下已有 `bge-large-zh-v1.5` 和 `bge-reranker-large`。
3. 服务器上的 `.env` 权限设为 `600`，不要提交到 Git，也不要放进文档。

## 启动与检查

在 `deploy` 目录执行：

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
```

上面的命令只启动中间件。以后需要在服务器上启动后端时，再先准备后端 JAR，然后显式启用 `app` profile：

```bash
docker compose --profile app up -d --build
```

## 数据边界

- MySQL 保存正式业务数据；
- MinIO 保存原文件和历史版本，不能随意清理；
- Elasticsearch 只保存可重建索引；
- Redis 只保存缓存、锁和短期状态；
- 模型目录作为只读目录挂载给 TEI，不复制进容器。

服务器安全组和端口开放范围需要单独管理。MySQL、Redis、Elasticsearch 和 MinIO 即使配置了密码，也不建议长期向整个公网开放。
