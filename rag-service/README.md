# AgentTo 独立 RAG 服务

这个目录是一套独立运行的 RAG 技术服务，供 AgentTo 通过 HTTP 调用，同时提供技术管理员后台。

## 目录

- `backend`：Spring Boot 4.1、Spring AI 2.0 后端，默认端口 `18473`
- `frontend`：Vue 3、TypeScript、Element Plus 管理端，默认端口 `5174`
- `start-backend.ps1`：读取 `E:\AgentTo\backend\.env.local` 后启动后端
- `start-frontend.ps1`：启动前端开发服务器
- `cleanup-test-data.ps1`：输入确认词和管理员密码后，清理 RAG 联调数据

## 本地启动

先确认远程 MySQL、MinIO、Elasticsearch、TEI Embedding 和 TEI Rerank 已启动，再分别执行：

```powershell
powershell -ExecutionPolicy Bypass -File E:\AgentTo\rag-service\start-backend.ps1
powershell -ExecutionPolicy Bypass -File E:\AgentTo\rag-service\start-frontend.ps1
```

浏览器访问 `http://127.0.0.1:5174`。开发环境默认管理员账号为 `admin`，默认密码为 `admin123`；生产环境应通过后端环境变量覆盖。

## 验证命令

后端使用本机 Maven：

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd test
```

前端：

```powershell
npm.cmd test
npm.cmd run build
```

H2 只存在于后端自动化测试进程。正常启动和实际联调使用阿里云 MySQL，不会启动本地数据库或 Docker 中间件。

## 清理联调数据

后端运行时执行：

```powershell
powershell -ExecutionPolicy Bypass -File E:\AgentTo\rag-service\cleanup-test-data.ps1
```

脚本只清理 RAG 文档、版本、切片、入库任务、检索 Trace、RAG 专用 Elasticsearch 索引内容和 MinIO Bucket 文件。Flyway 表结构、管理员账号和 AgentTo 主系统数据会保留。
