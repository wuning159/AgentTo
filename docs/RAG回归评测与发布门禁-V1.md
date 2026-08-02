# RAG 回归评测与发布门禁 V1

## 1. 评测数据集

默认数据集位于 `rag-service/backend/src/test/resources/rag-eval/baseline.jsonl`，共 30 条用例：

- 15 条期望正常作答；
- 5 条期望 `NO_RELEVANT_KNOWLEDGE_BASE`；
- 5 条期望 `INSUFFICIENT_EVIDENCE`；
- 5 条期望 `INVALID_CITATION`。

每行是一个 JSON 对象，至少包含调用方、查询、期望决策、期望知识库/切片和是否需要改写等字段。数据集只使用测试 ID 和脱敏文本，不保存真实密钥。

## 2. 故障分类

评测按用例独立累计故障，单条用例可以命中多个分类：

| 分类 | 含义 |
|---|---|
| `NO_ROUTE_FALSE_NEGATIVE` | 有相关知识库却路由拒绝 |
| `ROUTE_FALSE_POSITIVE` | 无相关知识库却路由到知识库 |
| `RETRIEVAL_MISS` | 期望切片未被最终引用命中 |
| `RERANK_MISS` | 命中切片排名超过 Top 10 |
| `FALSE_ACCEPT` | 预期拒答却给出答案 |
| `FALSE_REFUSAL` | 预期作答却拒答 |
| `INVALID_CITATION` | 生成引用未通过真实性校验 |
| `MODEL_FAILURE` | ChatModel 或答案生成不可用 |
| `INDEX_FAILURE` | 执行期检索/索引基础设施异常 |

## 3. 指标和门禁

评测实现位于 `com.agentto.rag.evaluation`：

| 指标 | 定义 | V1 门禁 |
|---|---|---:|
| Route Recall@3 | 期望有知识库的用例中成功路由比例 | ≥ 0.95 |
| Retrieval Hit@10 | 期望有文档且作答的用例中引用命中比例 | ≥ 0.90 |
| MRR | 首个命中期望切片的引用排名倒数均值 | 报告项 |
| Refusal Precision | 实际拒答中符合预期的比例 | ≥ 0.95 |
| Refusal Recall | 预期拒答中正确拒答的比例 | ≥ 0.90 |
| Citation Validity | 作答用例中引用全部有效的比例 | = 1.00 |
| Rewrite Recovery Rate | 需要改写的用例最终作答比例 | 报告项 |
| P50/P95 | 全链路延迟百分位数 | 报告项 |

无样本的比例指标按 `1.0` 处理；延迟在 V1 只报告，不作为硬门禁。

## 4. 运行命令

```powershell
cd rag-service/backend

# 普通测试
mvn.cmd test

# 完整发布验证：单元、MySQL/Elasticsearch Testcontainers、JaCoCo
mvn.cmd clean verify
```

完整验证要求 Docker Desktop daemon 可用。Embedding、Rerank 和答案生成在集成测试中使用确定性替身；真实 TEI/模型联调属于部署验证，不作为默认 CI 的随机依赖。

## 5. 当前验收结果（2026-08-02）

- 后端 `mvn.cmd verify`：204 个测试，0 failures，0 errors，BUILD SUCCESS。
- 前端 `pnpm.cmd test`：8 个测试文件、24 个测试通过。
- 前端 `pnpm.cmd run build`：生产构建通过；存在单个大于 500KB 的 bundle 警告，不影响构建结果。
- Docker 验收依赖：MySQL、Redis、Elasticsearch、MinIO、TEI Embedding、TEI Rerank 均已启动并通过健康检查。
- 本地后端健康检查返回 HTTP 200；`verify-api.ps1` 验证 18 个接口，无任何 HTTP 500。真实检索在未完成文档入库时按预期返回 503，知识库创建测试因当前联调数据约束返回 400，均未出现未分类 500。

## 6. 覆盖率说明

当前 Maven JaCoCo 配置的实际硬门禁为：核心包指令/行覆盖率 0.80，全仓指令覆盖率 0.60。Task 14 原始计划要求核心行/分支 1.00、全仓行 0.95/分支 0.90；当前配置尚未达到该目标，因此本次验收结论是“功能和集成验证通过，覆盖率目标仍需单独收口”，不能据此声称达到原计划的全覆盖发布标准。

后续应补齐核心包未覆盖分支和 DTO/持久化访问器排除说明，再提高 `pom.xml` 中的 JaCoCo 门禁；提高门禁前必须先补测试，避免只修改阈值制造假通过。
