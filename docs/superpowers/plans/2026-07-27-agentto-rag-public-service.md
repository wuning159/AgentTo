# AgentTo 公共 RAG 服务增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有独立 RAG 服务上增加可供外部项目调用的动态多知识库检索、证据阈值与拒答、引用真实性校验、查询改写与一次二次检索，以及可作为发布门禁的故障分类和回归评测。

**Architecture:** 保留当前 `HybridRetrievalService`、RRF、TEI Embedding/Rerank 和 Trace，新增调用方与知识库领域模型、存储无关的过滤检索契约，以及位于检索之上的 `RagQueryService` 编排层。知识库路由分为“知识库画像召回 Top 10”和“真实内容验证后 Top 3”两阶段；没有合格知识库时返回 `NO_RELEVANT_KNOWLEDGE_BASE`，禁止强制选择和自动联网兜底。

**Tech Stack:** Java 21、Spring Boot 4.1.0、Spring AI 2.0.0、Spring MVC、Spring Data JPA、Flyway、MySQL/H2、现有 Elasticsearch 适配器、TEI、JUnit 5、AssertJ、Mockito、JaCoCo、Testcontainers。

## Global Constraints

- 严格执行 Red → Green → Refactor；每项生产代码前必须先观察到预期失败。
- 新增和修改的核心领域、路由、证据、引用、改写、编排代码必须达到 100% 行覆盖和 100% 分支覆盖。
- 全仓最终目标为行覆盖不低于 95%、分支覆盖不低于 90%；DTO、JPA 纯访问器、Spring 配置装配代码可列明理由后排除。
- 每个调用方只能访问自己的私有知识库和显式授权的共享知识库。
- 第一阶段画像候选最多 10 个，第二阶段最多选择 3 个知识库。
- 无合格知识库时返回 `NO_RELEVANT_KNOWLEDGE_BASE`，不得让 LLM 强选知识库。
- 查询改写最多触发一次二次检索，禁止无界循环。
- 引用只能引用本次检索实际返回的 `chunkId`，引用文本必须能在对应切片中规范化匹配。
- 不自动联网搜索，不把无证据的模型回答作为成功结果。
- 本计划不更换向量数据库；先冻结存储 SPI 和契约测试，现有 Elasticsearch 是首个实现，Qdrant/Milvus 选型与迁移另立计划。
- Spring AI 查询改写和答案生成只依赖通用 `ChatModel`；模型供应商 starter 与密钥属于部署决策，不在领域代码中硬编码。
- 本计划只覆盖后端公共能力和验证，不包含管理前端页面。
- 不记录明文 API Key、Authorization、Cookie 或模型密钥；数据库只保存不可逆哈希和安全前缀。
- Windows/PowerShell 命令为默认执行形式。

## Success Criteria

1. 外部调用方通过 Bearer API Key 调用 `POST /api/v1/rag/query`。
2. 请求只在调用方可访问的 20–200 个候选知识库中路由，第一阶段 Top 10、第二阶段 Top 3。
3. 没有相关知识库时返回明确拒绝，不执行答案生成。
4. 证据不足时返回 `INSUFFICIENT_EVIDENCE`；充分时才允许生成答案。
5. 所有引用都能映射到真实 `chunkId`，伪造或错误引用使结果降级为拒答。
6. 首次证据不足时最多改写一次并二次检索，Trace 能展示两次查询和触发原因。
7. 回归评测至少覆盖路由错误、召回缺失、精排错误、证据不足、引用错误、模型/索引故障。
8. 相关单测、契约测试、MySQL/Elasticsearch 集成测试和 Maven `verify` 全部通过。

## Dependency and Parallelization Map

### 串行关键路径

`Task 0 → Task 1 → Task 2 → Task 4 → Task 5 → Task 6 → Task 8 → Task 10 → Task 12 → Task 14`

- Task 1–2 冻结数据库主键、调用方身份和 ACL。
- Task 4 冻结存储过滤接口，Task 5–6 才能安全实现两阶段路由。
- Task 8 冻结证据判定结果，Task 10 才能实现一次二次检索状态机。
- Task 12 冻结公共 API，Task 14 才进行完整发布验收。

### 可并行组

- **并行组 A（Task 2 完成后）：** Task 3 调用方认证、Task 4 检索 SPI 可以并行；两者不得修改同一个 Flyway 文件。
- **并行组 B（Task 4 完成后）：** Task 7 证据门、Task 9 引用校验、Task 11 评测数据加载器可以并行；三者不得修改 `RagQueryService`。
- **并行组 C（Task 10 完成后）：** Task 11 指标计算与 Task 13 Trace/故障分类可以并行。

### 禁止并行

- Task 5 和 Task 6 都会修改路由包及检索调用链，必须串行。
- Task 8 和 Task 10 都会修改查询编排结果，必须串行。
- Flyway 迁移必须按 V4、V5、V6 顺序创建和验证。
- 全量 Maven 验证与 Testcontainers 集成测试共享构建目录和端口资源，保持串行。

## Planned File Structure

```text
src/main/java/com/agentto/rag/
├── client/                 # 外部调用方、API Key、调用方上下文
├── knowledgebase/          # 知识库、共享授权、画像和管理服务
├── routing/                # 两阶段知识库路由
├── evidence/               # 证据阈值、拒答决策
├── citation/               # 引用模型和真实性校验
├── rewrite/                # Spring AI 查询改写适配器
├── query/                  # 公共 RAG 查询编排和 API
├── evaluation/             # 故障分类、数据集、指标和发布门禁
├── index/                  # 扩展现有存储无关过滤契约
└── retrieval/              # 保留现有混合检索，接入 SearchScope
```

---

### Task 0: 恢复 Git 基线并锁定现有测试

**Files:**
- Verify: `D:/projects/AgentTo/.git`
- Verify: `rag-service/backend/pom.xml`
- Verify: `rag-service/backend/src/test/java`
- Create: `docs/RAG开发测试基线-2026-07-27.md`

**Interfaces:**
- Consumes: 当前 AgentTo 文件目录。
- Produces: 可创建分支、提交和回滚的有效 Git 仓库；现有测试基线报告。

> 当前 `D:\projects\AgentTo\.git` 不能被 Git 识别。此任务不写业务代码，但它是后续双工具协作和频繁提交的硬前置。

- [ ] **Step 1: 确认 `.git` 是损坏目录、误创建目录还是缺失仓库**

Run:

```powershell
git -C D:\projects\AgentTo rev-parse --show-toplevel
Get-ChildItem -Force D:\projects\AgentTo\.git
```

Expected: 第一条当前失败；第二条提供恢复依据。不要在未确认来源前执行 `git init`。

- [ ] **Step 2: 从原远端或可信备份恢复仓库元数据**

如果存在远端，重新克隆到新目录并逐项迁移未跟踪文件；如果从未建立 Git，得到用户确认后再初始化。禁止覆盖现有项目目录。

- [ ] **Step 3: 建立执行分支**

```powershell
git switch -c codex/rag-public-service
git status --short --branch
```

Expected: 当前分支为 `codex/rag-public-service`，已有用户文件保持不变。

- [ ] **Step 4: 运行现有后端测试基线**

```powershell
Set-Location D:\projects\AgentTo\rag-service\backend
.\mvnw.cmd test
```

若无 Maven Wrapper：

```powershell
mvn test
```

Expected: 记录 tests、failures、errors、skipped 的实际数字；失败时先修复基线或登记为既有失败，不得进入功能实现。

- [ ] **Step 5: 把实际测试数字、环境和既有失败写入基线文档并提交**

```powershell
git add docs
git commit -m "chore: establish rag service development baseline"
```

Expected: 不包含业务代码。

---

### Task 1: 加入覆盖率报告与分阶段门禁

**Files:**
- Modify: `rag-service/backend/pom.xml`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/quality/CoveragePolicyTest.java`

**Interfaces:**
- Consumes: Maven Surefire 测试结果。
- Produces: `target/site/jacoco/jacoco.xml`；核心新包 100% 覆盖的最终门禁。

- [ ] **Step 1: 写失败的覆盖策略测试**

```java
class CoveragePolicyTest {
    @Test
    void corePackagesAreDeclaredForCoverageEnforcement() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom)
                .contains("jacoco-maven-plugin")
                .contains("com/agentto/rag/routing")
                .contains("com/agentto/rag/evidence")
                .contains("com/agentto/rag/citation")
                .contains("com/agentto/rag/query");
    }
}
```

- [ ] **Step 2: 运行测试并确认 RED**

```powershell
mvn -Dtest=CoveragePolicyTest test
```

Expected: FAIL，原因是 `pom.xml` 尚未声明 JaCoCo 和核心包。

- [ ] **Step 3: 在 `pom.xml` 加入 JaCoCo report 和 check**

核心配置：

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.13</version>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report-and-check</id>
            <phase>verify</phase>
            <goals><goal>report</goal><goal>check</goal></goals>
        </execution>
    </executions>
</plugin>
```

同时在 `pom.xml` 声明核心包清单，令策略测试可以转绿：

```xml
<properties>
    <coverage.core.includes>
        com/agentto/rag/routing/**,
        com/agentto/rag/evidence/**,
        com/agentto/rag/citation/**,
        com/agentto/rag/query/**
    </coverage.core.includes>
</properties>
```

先生成报告，不立即对历史包执行 100% 门禁；在 Task 14 中对新增核心包开启 100% 行/分支检查并对全仓设置 95%/90%。

- [ ] **Step 4: 运行测试并确认 GREEN**

```powershell
mvn -Dtest=CoveragePolicyTest test
mvn test
```

Expected: 两条命令通过。

- [ ] **Step 5: 提交**

```powershell
git add rag-service/backend/pom.xml rag-service/backend/src/test/java/com/agentto/rag/quality/CoveragePolicyTest.java
git commit -m "test: add rag coverage reporting"
```

---

### Task 2: 建立知识库、共享授权和调用方领域模型

**Files:**
- Create: `rag-service/backend/src/main/resources/db/migration/V4__create_knowledge_base_and_client_tables.sql`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBase.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseVisibility.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseGrant.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseGrantRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseAccessService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/JpaKnowledgeBaseAccessService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseAdminService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseAdminController.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/knowledgebase/KnowledgeBaseNotWritableException.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApplication.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApplicationRepository.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/RagDocument.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/RagChunkEntity.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/database/FlywayMigrationTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/knowledgebase/KnowledgeBaseAccessTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/knowledgebase/KnowledgeBaseAdminServiceTest.java`

**Interfaces:**
- Consumes: 现有 `rag_document`、`rag_chunk` 和管理员用户。
- Produces: `KnowledgeBaseAccessService.accessibleKnowledgeBaseIds(Long clientAppId)`。

- [ ] **Step 1: 扩展 Flyway 测试并确认 RED**

```java
@Test
void createsKnowledgeBaseClientAndGrantSchema() {
    assertTableExists("RAG_KNOWLEDGE_BASE");
    assertTableExists("RAG_CLIENT_APPLICATION");
    assertTableExists("RAG_KNOWLEDGE_BASE_GRANT");
    assertColumnExists("RAG_DOCUMENT", "KNOWLEDGE_BASE_ID");
    assertColumnExists("RAG_CHUNK", "KNOWLEDGE_BASE_ID");
}
```

```powershell
mvn -Dtest=FlywayMigrationTest test
```

Expected: FAIL，缺少 V4 表和字段。

- [ ] **Step 2: 创建 V4 迁移**

迁移必须：

1. 创建 `rag_client_application(id, app_uid, name, status, created_at, updated_at)`。
2. 创建 `rag_knowledge_base(id, kb_uid, name, description, visibility, owner_app_id, status, profile_version, created_at, updated_at)`。
3. 创建 `rag_knowledge_base_grant(knowledge_base_id, client_app_id, permission, created_at)`，联合唯一。
4. 插入 `legacy-default` 调用方和 `legacy-default-kb`。
5. 为现有文档和切片回填默认知识库。
6. 最后把 `knowledge_base_id` 改为非空并建立外键和索引。

- [ ] **Step 3: 创建最小领域实体和访问服务测试**

```java
@Test
void returnsOwnedPrivateAndGrantedSharedKnowledgeBasesOnly() {
    Set<Long> accessible = service.accessibleKnowledgeBaseIds(10L);
    assertThat(accessible).containsExactlyInAnyOrder(101L, 102L);
    assertThat(accessible).doesNotContain(103L);
}
```

- [ ] **Step 4: 运行测试并确认 RED**

```powershell
mvn -Dtest=KnowledgeBaseAccessTest test
```

Expected: FAIL，`KnowledgeBaseAccessService` 不存在。

- [ ] **Step 5: 实现访问规则**

```java
public interface KnowledgeBaseAccessService {
    Set<Long> accessibleKnowledgeBaseIds(Long clientAppId);
    void requireReadable(Long clientAppId, Long knowledgeBaseId);
    void requireManageable(Long clientAppId, Long knowledgeBaseId);
}
```

规则：

- 所有者可管理和读取自己的私有/共享知识库。
- 非所有者只能读取显式授权且 `visibility=SHARED` 的知识库。
- `DISABLED` 知识库永远不可路由。

- [ ] **Step 6: 用管理服务实现知识库创建、画像更新和共享授权**

管理端第一版提供：

```text
POST /api/admin/knowledge-bases
PUT  /api/admin/knowledge-bases/{kbUid}/profile
POST /api/admin/knowledge-bases/{kbUid}/grants
DELETE /api/admin/knowledge-bases/{kbUid}/grants/{appUid}
```

`KnowledgeBaseAdminService` 必须验证 owner app 存在、共享授权不重复、私有知识库不能保留对外 grant。画像字段变化时原子递增 `profileVersion`，供 Task 6 重建画像索引。

- [ ] **Step 7: 运行聚焦测试和迁移测试**

```powershell
mvn -Dtest=KnowledgeBaseAccessTest,KnowledgeBaseAdminServiceTest,FlywayMigrationTest test
```

Expected: PASS。

- [ ] **Step 8: 提交**

```powershell
git add rag-service/backend/src/main rag-service/backend/src/test
git commit -m "feat: add knowledge base ownership and sharing"
```

---

### Task 3: 增加外部调用方 API Key 认证

**Files:**
- Create: `rag-service/backend/src/main/resources/db/migration/V5__add_client_api_keys.sql`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApiKey.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApiKeyRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/CallerPrincipal.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/CallerRequestContext.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApiProperties.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/InvalidClientCredentialException.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApiKeyAuthenticator.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApiKeyInterceptor.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApiWebConfiguration.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApplicationAdminService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/ClientApplicationAdminController.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/client/CreatedClientApiKey.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/client/ClientApiKeyAuthenticatorTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/client/ClientApiKeyInterceptorTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/client/ClientApplicationAdminServiceTest.java`

**Interfaces:**
- Produces: `CallerPrincipal(Long appId, String appUid, String appName)`。
- Security rule: 只存 `HMAC-SHA256(serverPepper, rawKey)`，日志只允许记录 `keyPrefix`。

- [ ] **Step 1: 写认证 RED 测试**

```java
@Test
void authenticatesActiveKeyWithoutPersistingRawSecret() {
    CallerPrincipal principal = authenticator.authenticate("rag_live_example_secret");
    assertThat(principal.appUid()).isEqualTo("app-a");
    verify(repository).findActiveByKeyHash(expectedHash);
}

@Test
void rejectsRevokedKey() {
    assertThatThrownBy(() -> authenticator.authenticate("revoked"))
            .isInstanceOf(InvalidClientCredentialException.class);
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=ClientApiKeyAuthenticatorTest test
```

Expected: FAIL，认证类型不存在。

- [ ] **Step 3: 实现 V5 和认证器**

表字段：`id`、`client_app_id`、`key_prefix`、`key_hash`、`status`、`expires_at`、`last_used_at`、`created_at`。

```java
public CallerPrincipal authenticate(String rawKey) {
    String hash = hmacSha256(properties.pepper(), rawKey);
    ClientApiKey key = repository.findActiveByKeyHash(hash)
            .orElseThrow(InvalidClientCredentialException::new);
    key.requireUsable(Instant.now());
    return new CallerPrincipal(key.clientAppId(), key.appUid(), key.appName());
}
```

- [ ] **Step 4: 写拦截器测试并实现**

只拦截 `/api/v1/**`；缺少或错误 Bearer Token 返回 HTTP 401；管理端 `/api/admin/**` 继续使用现有管理员 Session。

- [ ] **Step 5: 先写 RED 测试，再实现调用方和 Key 的管理端生命周期**

管理端提供：

```text
POST /api/admin/clients
POST /api/admin/clients/{appUid}/keys
POST /api/admin/clients/{appUid}/keys/{keyPrefix}/revoke
```

创建 Key 时只在响应中返回一次完整密钥：

```java
public record CreatedClientApiKey(String appUid, String keyPrefix, String rawKey) {}
```

持久化实体不得包含 `rawKey` 字段；控制器、异常和日志不得输出完整 Key。
原始 Key 使用 `SecureRandom` 生成至少 32 字节随机量；pepper 只从 `RAG_CLIENT_KEY_PEPPER` 环境变量读取，不允许提供可用于生产的默认值。

- [ ] **Step 6: 运行聚焦测试**

```powershell
mvn -Dtest=ClientApiKeyAuthenticatorTest,ClientApiKeyInterceptorTest,ClientApplicationAdminServiceTest,AuthServiceTest test
```

Expected: PASS，且现有管理员认证无回归。

- [ ] **Step 7: 提交**

```powershell
git add rag-service/backend/src/main rag-service/backend/src/test
git commit -m "feat: authenticate rag client applications"
```

---

### Task 4: 冻结存储无关的知识库过滤检索契约

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/index/SearchScope.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/index/ChunkIndex.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/index/IndexedChunk.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/index/IndexSearchHit.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/index/ElasticsearchChunkIndex.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/index/DisabledChunkIndex.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/index/ChunkIndexContractTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/index/ElasticsearchChunkIndexTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/HybridRetrievalServiceTest.java`

**Interfaces:**
- Produces:

```java
public record SearchScope(Set<Long> knowledgeBaseIds) {
    public SearchScope {
        knowledgeBaseIds = Set.copyOf(knowledgeBaseIds);
        if (knowledgeBaseIds.isEmpty()) throw new IllegalArgumentException("knowledgeBaseIds must not be empty");
    }
}

List<IndexSearchHit> keywordSearch(String query, SearchScope scope, int limit);
List<IndexSearchHit> vectorSearch(float[] queryVector, SearchScope scope, int limit);
```

- [ ] **Step 1: 写契约 RED 测试**

```java
@Test
void neverReturnsChunksOutsideSearchScope() {
    index.replaceVersionChunks(1L, List.of(chunk(101L), chunk(202L)));
    SearchScope scope = new SearchScope(Set.of(101L));
    assertThat(index.keywordSearch("预算", scope, 10))
            .extracting(IndexSearchHit::knowledgeBaseId)
            .containsOnly(101L);
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=ElasticsearchChunkIndexTest test
```

Expected: 编译失败，缺少 `SearchScope` 和 `knowledgeBaseId`。

- [ ] **Step 3: 最小修改记录和接口**

为 `IndexedChunk`、`IndexSearchHit` 增加 `Long knowledgeBaseId`。旧的无 scope 方法只允许测试迁移期使用，并标记 `@Deprecated`；生产调用必须提供 scope。

- [ ] **Step 4: Elasticsearch 映射和查询加入过滤**

映射增加：

```json
"knowledge_base_id": { "type": "long" }
```

关键词和向量查询都必须生成 `terms` 过滤：

```json
{ "terms": { "knowledge_base_id": [101, 102] } }
```

- [ ] **Step 5: 运行索引、入库和检索测试**

```powershell
mvn -Dtest=ElasticsearchChunkIndexTest,IngestionOrchestratorTest,HybridRetrievalServiceTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add rag-service/backend/src/main/java/com/agentto/rag/index rag-service/backend/src/test
git commit -m "refactor: add storage-neutral knowledge base search scope"
```

---

### Task 5: 让文档入库完整携带知识库身份

**Files:**
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentService.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentAdminController.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionOrchestrator.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/RagChunkEntity.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/document/DocumentServiceTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/ingestion/IngestionOrchestratorTest.java`

**Interfaces:**
- Consumes: `KnowledgeBaseAdminService.requireActive(...)`；第一版文档入库仍由现有管理端执行。
- Produces: 每个 `rag_document`、`rag_chunk`、`IndexedChunk` 都有同一个非空 `knowledgeBaseId`。

- [ ] **Step 1: 写无效知识库上传和身份传播 RED 测试**

```java
@Test
void uploadRejectsUnknownOrDisabledKnowledgeBase() {
    assertThatThrownBy(() -> service.upload(file, 999L, adminId))
            .isInstanceOf(KnowledgeBaseNotWritableException.class);
}

@Test
void ingestionCopiesKnowledgeBaseIdIntoEveryIndexedChunk() {
    orchestrator.run(jobId);
    assertThat(index.chunks()).extracting(IndexedChunk::knowledgeBaseId).containsOnly(101L);
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=DocumentServiceTest,IngestionOrchestratorTest test
```

Expected: FAIL，上传和切片尚未接收知识库 ID。

- [ ] **Step 3: 最小实现**

将上传签名调整为：

```java
UploadResult upload(MultipartFile file, Long knowledgeBaseId, Long operatorId)
```

控制器请求必须提供 `knowledgeBaseId`。入库从文档读取该 ID，写入 `RagChunkEntity` 和 `IndexedChunk`，不允许从自由文本 metadata 推断。

外部调用方在 V1 只使用公共查询 API；知识库、授权和文档由管理端维护。若后续需要调用方自助上传，应独立增加上传授权、配额和审计计划，不能复用查询 Key 默认获得写权限。

- [ ] **Step 4: 运行相关测试**

```powershell
mvn -Dtest=DocumentServiceTest,IngestionOrchestratorTest,ElasticsearchChunkIndexTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add rag-service/backend/src/main rag-service/backend/src/test
git commit -m "feat: bind documents and chunks to knowledge bases"
```

---

### Task 6: 实现两阶段动态多知识库路由

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/routing/KnowledgeBaseProfileIndex.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/routing/KnowledgeBaseProfileCandidate.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/routing/ElasticsearchKnowledgeBaseProfileIndex.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/routing/KnowledgeBaseRoute.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/routing/RoutingDecision.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/routing/KnowledgeBaseRouter.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/routing/RoutingProperties.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/routing/KnowledgeBaseRouterTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/routing/ElasticsearchKnowledgeBaseProfileIndexTest.java`
- Modify: `rag-service/backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `accessibleKnowledgeBaseIds(clientAppId)`、`EmbeddingService`、`ChunkIndex`。
- Produces:

```java
public record KnowledgeBaseRoute(
        RoutingDecision decision,
        List<Long> profileShortlist,
        List<Long> selectedKnowledgeBaseIds,
        Map<Long, Double> verificationScores) {}

public enum RoutingDecision { ROUTED, NO_RELEVANT_KNOWLEDGE_BASE }
```

- [ ] **Step 1: 写第一阶段 Top 10 和 ACL RED 测试**

```java
@Test
void profileRoutingSearchesOnlyAccessibleKnowledgeBasesAndCapsAtTen() {
    KnowledgeBaseRoute route = router.route(10L, "报销发票怎么审批");
    assertThat(route.profileShortlist()).hasSizeLessThanOrEqualTo(10);
    assertThat(route.profileShortlist()).doesNotContain(unauthorizedKbId);
}
```

- [ ] **Step 2: 写第二阶段 Top 3 和拒绝 RED 测试**

```java
@Test
void contentVerificationSelectsAtMostThreeKnowledgeBases() {
    KnowledgeBaseRoute route = router.route(10L, "预算审批规则");
    assertThat(route.selectedKnowledgeBaseIds()).hasSizeBetween(1, 3);
}

@Test
void returnsNoRelevantKnowledgeBaseInsteadOfForcingAChoice() {
    KnowledgeBaseRoute route = router.route(10L, "完全无关的问题");
    assertThat(route.decision()).isEqualTo(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE);
    assertThat(route.selectedKnowledgeBaseIds()).isEmpty();
}
```

- [ ] **Step 3: 运行并确认 RED**

```powershell
mvn -Dtest=KnowledgeBaseRouterTest test
```

Expected: FAIL，路由类型不存在。

- [ ] **Step 4: 实现第一阶段画像召回**

画像文本由名称、描述、标签和示例问题组成。接口固定为：

```java
List<KnowledgeBaseProfileCandidate> search(
        float[] queryVector, Set<Long> accessibleKnowledgeBaseIds, int limit);
```

`limit` 固定读取 `rag.routing.profile-limit=10`。画像索引必须过滤可访问 ID。

- [ ] **Step 5: 实现第二阶段真实内容验证**

对 Top 10 每个知识库进行小规模受限向量检索，取该知识库前两条内容分数的加权平均：

```text
verificationScore = top1 * 0.7 + top2 * 0.3
```

仅保留 `verificationScore >= rag.routing.verification-threshold`，按分数降序选择 Top 3；没有合格项返回 `NO_RELEVANT_KNOWLEDGE_BASE`。

- [ ] **Step 6: 添加默认配置**

```yaml
rag:
  routing:
    profile-limit: 10
    selected-limit: 3
    verification-per-kb-limit: 2
    verification-threshold: 0.55
```

阈值是初始基线，Task 11 用评测集校准，不在生产中静默自动修改。

- [ ] **Step 7: 运行路由测试**

```powershell
mvn -Dtest=KnowledgeBaseRouterTest,ElasticsearchKnowledgeBaseProfileIndexTest test
```

Expected: PASS。

- [ ] **Step 8: 提交**

```powershell
git add rag-service/backend/src/main/java/com/agentto/rag/routing rag-service/backend/src/test/java/com/agentto/rag/routing rag-service/backend/src/main/resources/application.yml
git commit -m "feat: route queries across dynamic knowledge bases"
```

---

### Task 7: 实现证据阈值和明确拒答

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evidence/EvidenceDecision.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evidence/EvidenceAssessment.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evidence/EvidencePolicyProperties.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evidence/EvidenceGate.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/evidence/EvidenceGateTest.java`
- Modify: `rag-service/backend/src/main/resources/application.yml`

**Interfaces:**
- Produces:

```java
public enum EvidenceDecision {
    SUFFICIENT,
    INSUFFICIENT_EVIDENCE,
    NO_RELEVANT_KNOWLEDGE_BASE
}

public record EvidenceAssessment(
        EvidenceDecision decision,
        double topScore,
        int qualifyingEvidenceCount,
        String reason) {}
```

- [ ] **Step 1: 写边界 RED 测试**

覆盖：

- 空候选 → `INSUFFICIENT_EVIDENCE`
- Top 1 低于阈值 → 拒答
- Top 1 合格但合格证据数不足 → 拒答
- 分数恰好等于阈值 → 通过
- 路由已拒绝 → `NO_RELEVANT_KNOWLEDGE_BASE`

```java
@ParameterizedTest
@CsvSource({"0.54,1,INSUFFICIENT_EVIDENCE", "0.55,2,SUFFICIENT"})
void appliesThresholdAndMinimumEvidenceCount(
        double topScore, int count, EvidenceDecision expected) {
    assertThat(gate.assess(route, candidates(topScore, count)).decision()).isEqualTo(expected);
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=EvidenceGateTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现纯函数证据门**

初始配置：

```yaml
rag:
  evidence:
    minimum-score: 0.55
    minimum-count: 2
```

优先使用 rerank score；没有 rerank 时使用归一化后的向量/RRF 分数，并在 reason 标记降级。

- [ ] **Step 4: 运行并确认 GREEN**

```powershell
mvn -Dtest=EvidenceGateTest test
```

Expected: PASS，100% 行和分支覆盖。

- [ ] **Step 5: 提交**

```powershell
git add rag-service/backend/src/main/java/com/agentto/rag/evidence rag-service/backend/src/test/java/com/agentto/rag/evidence rag-service/backend/src/main/resources/application.yml
git commit -m "feat: reject rag queries with insufficient evidence"
```

---

### Task 8: 建立答案生成边界和引用真实性校验

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/citation/Citation.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/citation/GeneratedAnswer.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/citation/CitationValidationResult.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/citation/CitationValidator.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/AnswerGenerator.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/SpringAiAnswerGenerator.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/DisabledAnswerGenerator.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/citation/CitationValidatorTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/query/SpringAiAnswerGeneratorTest.java`

**Interfaces:**
- Produces:

```java
public record Citation(String chunkId, String quote) {}
public record GeneratedAnswer(String text, List<Citation> citations) {}

public interface AnswerGenerator {
    GeneratedAnswer generate(String query, List<RetrievalCandidate> evidence);
}
```

- [ ] **Step 1: 写引用真实性 RED 测试**

```java
@Test
void rejectsCitationToChunkThatWasNotRetrieved() {
    CitationValidationResult result = validator.validate(
            answer(citation("invented", "不存在")),
            List.of(candidate("real-1", "真实制度内容")));
    assertThat(result.valid()).isFalse();
    assertThat(result.invalidChunkIds()).containsExactly("invented");
}

@Test
void rejectsQuoteThatDoesNotExistInTheSourceChunk() {
    assertThat(validator.validate(
            answer(citation("real-1", "伪造原文")),
            List.of(candidate("real-1", "真实制度内容"))).valid()).isFalse();
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=CitationValidatorTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现确定性校验**

校验顺序：

1. `chunkId` 必须属于本次证据集合。
2. quote 经过 Unicode NFKC、空白折叠后必须是 source content 的子串。
3. 同一个引用不得出现空 quote。
4. 答案非空时至少有一个有效引用。

该校验只证明“引用来源真实”，不声称自动证明答案中每个自然语言结论都被支持。

- [ ] **Step 4: 先写 RED 测试，再实现 Spring AI 结构化答案生成**

`SpringAiAnswerGenerator` 只接收通过 EvidenceGate 的候选，要求模型输出 `GeneratedAnswer`。Prompt 明确：

- 只能基于给定 evidence。
- 每个主要结论必须引用 chunkId。
- quote 必须逐字来自对应切片。
- 证据不支持时返回空答案。

使用 `@ConditionalOnBean(ChatModel.class)` 装配；没有 `ChatModel` 时装配 `DisabledAnswerGenerator`，由编排层返回 `GENERATION_UNAVAILABLE`，不得伪造回答。

- [ ] **Step 5: 运行并确认 GREEN**

```powershell
mvn -Dtest=CitationValidatorTest,SpringAiAnswerGeneratorTest test
```

Expected: PASS，100% 行和分支覆盖。

- [ ] **Step 6: 提交**

```powershell
git add rag-service/backend/src/main/java/com/agentto/rag/citation rag-service/backend/src/main/java/com/agentto/rag/query/AnswerGenerator.java rag-service/backend/src/test
git commit -m "feat: validate rag citations against retrieved chunks"
```

---

### Task 9: 使用 Spring AI 2.0 实现查询改写适配器

**Files:**
- Modify: `rag-service/backend/pom.xml`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/rewrite/QueryRewriter.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/rewrite/SpringAiQueryRewriter.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/rewrite/DisabledQueryRewriter.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/rewrite/SpringAiQueryRewriterTest.java`

**Interfaces:**
- Produces:

```java
public interface QueryRewriter {
    Optional<String> rewrite(String originalQuery, String failureReason);
}
```

- [ ] **Step 1: 写改写行为 RED 测试**

覆盖：

- 返回去除多余空白后的新查询。
- 模型返回原查询时返回 `Optional.empty()`。
- 模型异常时降级为 `Optional.empty()`。
- 改写结果为空或超过 1000 字符时拒绝。

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=SpringAiQueryRewriterTest test
```

Expected: FAIL。

- [ ] **Step 3: 加入 Spring AI RAG 依赖**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-rag</artifactId>
</dependency>
```

- [ ] **Step 4: 实现 Spring AI 适配器**

使用 `RewriteQueryTransformer`，Prompt 必须要求：

- 保留原意和实体。
- 补足省略的检索关键词。
- 不回答问题。
- 只返回一个改写查询。

`QueryRewriter` 隔离 Spring AI 具体 API，使编排层单测无需真实模型。

- [ ] **Step 5: 运行聚焦测试**

```powershell
mvn -Dtest=SpringAiQueryRewriterTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add rag-service/backend/pom.xml rag-service/backend/src/main/java/com/agentto/rag/rewrite rag-service/backend/src/test/java/com/agentto/rag/rewrite
git commit -m "feat: rewrite low-evidence rag queries once"
```

---

### Task 10: 实现一次二次检索的公共查询编排

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/RagQueryCommand.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/RagQueryDecision.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/RagQueryResponse.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/QueryAttempt.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/RagQueryService.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/query/RagQueryServiceTest.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalRequest.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/HybridRetrievalService.java`

**Interfaces:**
- Consumes: Router、`HybridRetrievalService`、EvidenceGate、QueryRewriter、AnswerGenerator、CitationValidator。
- Produces:

```java
public record RagQueryCommand(Long clientAppId, String query, int finalLimit) {}

public enum RagQueryDecision {
    ANSWERED,
    NO_RELEVANT_KNOWLEDGE_BASE,
    INSUFFICIENT_EVIDENCE,
    INVALID_CITATION,
    GENERATION_UNAVAILABLE
}

public record RagQueryResponse(
        RagQueryDecision decision,
        String answer,
        List<Citation> citations,
        List<QueryAttempt> attempts,
        String traceUid) {}
```

- [ ] **Step 1: 写主路径 RED 测试**

```java
@Test
void routesRetrievesGeneratesAndValidatesAnAnswer() {
    RagQueryResponse response = service.query(new RagQueryCommand(10L, "预算如何审批", 8));
    assertThat(response.decision()).isEqualTo(RagQueryDecision.ANSWERED);
    assertThat(response.attempts()).hasSize(1);
}
```

- [ ] **Step 2: 写拒答和一次重试 RED 测试**

覆盖：

- 路由无结果：不调用 retrieval、rewriter、generator。
- 首次证据不足且改写有效：只重试一次。
- 二次仍不足：返回 `INSUFFICIENT_EVIDENCE`。
- 改写器异常：不重试，返回首次拒答。
- 引用无效：返回 `INVALID_CITATION`，不泄露未验证答案。
- 未配置 `ChatModel`：返回 `GENERATION_UNAVAILABLE`，不把检索片段拼成伪答案。

- [ ] **Step 3: 运行并确认 RED**

```powershell
mvn -Dtest=RagQueryServiceTest test
```

Expected: FAIL，编排类型不存在。

- [ ] **Step 4: 实现有限状态机**

```text
ROUTE
  ├─ no route → NO_RELEVANT_KNOWLEDGE_BASE
  └─ routed → RETRIEVE_1 → ASSESS_1
       ├─ sufficient → GENERATE → VALIDATE
       └─ insufficient → REWRITE
            ├─ no rewrite → INSUFFICIENT_EVIDENCE
            └─ rewritten → RETRIEVE_2 → ASSESS_2
                 ├─ insufficient → INSUFFICIENT_EVIDENCE
                 └─ sufficient → GENERATE → VALIDATE
```

`RetrievalRequest` 增加 `SearchScope` 和 `attemptNo`；`HybridRetrievalService` 的关键词与向量检索都使用同一个 scope。

- [ ] **Step 5: 运行当前包测试**

```powershell
mvn -Dtest=RagQueryServiceTest,HybridRetrievalServiceTest,EvidenceGateTest,CitationValidatorTest test
```

Expected: PASS。

- [ ] **Step 6: 重构**

只在测试保持绿色时提取：

- `executeAttempt(query, scope, attemptNo)`
- `refusal(decision, attempts, traceUid)`
- `generateAndValidate(...)`

- [ ] **Step 7: 再次运行并提交**

```powershell
mvn -Dtest=RagQueryServiceTest,HybridRetrievalServiceTest test
git add rag-service/backend/src/main rag-service/backend/src/test
git commit -m "feat: orchestrate routed rag with one retry"
```

---

### Task 11: 建立故障分类、回归数据集和指标

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evaluation/RagFailureCode.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evaluation/EvaluationCase.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evaluation/EvaluationDatasetLoader.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evaluation/EvaluationMetrics.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evaluation/EvaluationMetricsCalculator.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/evaluation/RagEvaluationRunner.java`
- Create: `rag-service/backend/src/test/resources/rag-eval/baseline.jsonl`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/evaluation/EvaluationDatasetLoaderTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/evaluation/EvaluationMetricsTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/evaluation/RagRegressionTest.java`

**Interfaces:**
- Produces失败分类：

```java
public enum RagFailureCode {
    NO_ROUTE_FALSE_NEGATIVE,
    ROUTE_FALSE_POSITIVE,
    RETRIEVAL_MISS,
    RERANK_MISS,
    FALSE_ACCEPT,
    FALSE_REFUSAL,
    INVALID_CITATION,
    MODEL_FAILURE,
    INDEX_FAILURE
}
```

- [ ] **Step 1: 写数据集加载 RED 测试**

JSONL 每行固定结构：

```json
{"id":"route-finance-001","clientAppId":10,"query":"预算怎么审批","expectedKbIds":[101],"expectedChunkIds":["finance-approval-1"],"expectedDecision":"ANSWERED"}
```

测试拒绝重复 ID、空 query、空 expectedDecision 和未知枚举。

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=EvaluationDatasetLoaderTest test
```

Expected: FAIL。

- [ ] **Step 3: 实现加载器和至少 30 条首批基线**

数据分布：

- 10 条正确路由/多知识库问题。
- 5 条无相关知识库。
- 5 条证据不足。
- 5 条引用真实性。
- 5 条查询改写后命中。

数据必须使用测试知识库和虚构内容，不包含生产数据或敏感信息。

- [ ] **Step 4: 写指标 RED 测试**

```java
@Test
void calculatesRouteRecallAtThreeAndRefusalPrecision() {
    EvaluationMetrics metrics = calculator.calculate(results);
    assertThat(metrics.routeRecallAt3()).isEqualTo(1.0);
    assertThat(metrics.citationValidity()).isEqualTo(1.0);
}
```

- [ ] **Step 5: 实现指标**

必须包含：

- Route Recall@3
- Retrieval Hit@10
- MRR
- Refusal Precision / Recall
- Citation Validity
- Rewrite Recovery Rate
- P50 / P95 latency
- 每种 `RagFailureCode` 数量

- [ ] **Step 6: 实现回归门禁**

首版发布阈值：

```text
Route Recall@3       >= 0.95
Retrieval Hit@10     >= 0.90
Refusal Precision    >= 0.95
Refusal Recall       >= 0.90
Citation Validity    == 1.00
```

延迟只报告不作为首版硬门禁，待真实向量库和模型环境稳定后再冻结。

- [ ] **Step 7: 运行评测测试**

```powershell
mvn -Dtest=EvaluationDatasetLoaderTest,EvaluationMetricsTest,RagRegressionTest test
```

Expected: PASS。

- [ ] **Step 8: 提交**

```powershell
git add rag-service/backend/src/main/java/com/agentto/rag/evaluation rag-service/backend/src/test
git commit -m "test: add rag failure taxonomy and regression gates"
```

---

### Task 12: 暴露稳定的公共 RAG API

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/RagQueryController.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/RagQueryApiRequest.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/query/RagQueryApiResponse.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/query/RagQueryControllerTest.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/common/api/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `POST /api/v1/rag/query`

请求：

```json
{"query":"预算如何审批","finalLimit":8}
```

成功或证据类业务拒答均返回 HTTP 200，使用业务 `decision` 区分；认证失败 401，权限失败 403，非法参数 400，索引不可用或 `GENERATION_UNAVAILABLE` 返回 503。

- [ ] **Step 1: 写 MockMvc RED 测试**

覆盖：

- 有效 API Key + ANSWERED。
- 无 Key → 401。
- 无路由 → 200 + `NO_RELEVANT_KNOWLEDGE_BASE`。
- 证据不足 → 200 + `INSUFFICIENT_EVIDENCE`。
- 非法 finalLimit → 400。
- 内部响应不暴露 rerank 原始服务错误、密钥或堆栈。

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=RagQueryControllerTest test
```

Expected: FAIL，Controller 不存在。

- [ ] **Step 3: 实现薄 Controller**

Controller 只负责：

1. 从 `CallerRequestContext` 取得 `clientAppId`。
2. 校验 `query` 和 `finalLimit`。
3. 调用 `RagQueryService`。
4. 映射稳定 DTO。

不得在 Controller 中执行路由、阈值判断或改写。

- [ ] **Step 4: 运行 API 和认证测试**

```powershell
mvn -Dtest=RagQueryControllerTest,ClientApiKeyInterceptorTest test
```

Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git add rag-service/backend/src/main/java/com/agentto/rag/query rag-service/backend/src/main/java/com/agentto/rag/common/api/GlobalExceptionHandler.java rag-service/backend/src/test
git commit -m "feat: expose public rag query api"
```

---

### Task 13: 扩展 Trace、阶段进度和可诊断故障

**Files:**
- Create: `rag-service/backend/src/main/resources/db/migration/V6__extend_query_trace_for_routing_and_retries.sql`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalStage.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryTraceEntity.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryTraceDetail.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryTraceService.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/database/FlywayMigrationTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/QueryTraceServiceTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/RetrievalJobServiceTest.java`

**Interfaces:**
- Produces Trace 字段：`clientAppId`、`originalQuery`、`effectiveQuery`、`attemptCount`、`profileShortlistJson`、`selectedKbIdsJson`、`evidenceDecision`、`failureCode`、`citationValid`。

- [ ] **Step 1: 写迁移和 Trace RED 测试**

```java
@Test
void traceShowsRoutingEvidenceRewriteAndCitationDecision() {
    QueryTraceDetail detail = service.detail(traceUid);
    assertThat(detail.attempts()).hasSize(2);
    assertThat(detail.selectedKnowledgeBaseIds()).containsExactly(101L);
    assertThat(detail.evidenceDecision()).isEqualTo("SUFFICIENT");
    assertThat(detail.citationValid()).isTrue();
}
```

- [ ] **Step 2: 运行并确认 RED**

```powershell
mvn -Dtest=FlywayMigrationTest,QueryTraceServiceTest test
```

Expected: FAIL。

- [ ] **Step 3: 创建 V6 并扩展阶段**

新增阶段：

```java
ROUTE_PROFILE,
ROUTE_VERIFY,
EVIDENCE_GATE,
QUERY_REWRITE,
CITATION_VALIDATE
```

每个阶段记录状态、耗时、输入数量、输出数量和降级原因，不保存 API Key 或完整 Authorization。

- [ ] **Step 4: 更新 Trace 持久化和详情**

查询失败也必须尽最大可能写入 Trace；数据库不可用时保留原异常为主异常，不用 Trace 异常覆盖。

- [ ] **Step 5: 运行 Trace 和 Job 测试**

```powershell
mvn -Dtest=FlywayMigrationTest,QueryTraceServiceTest,RetrievalJobServiceTest,RagQueryServiceTest test
```

Expected: PASS。

- [ ] **Step 6: 提交**

```powershell
git add rag-service/backend/src/main rag-service/backend/src/test
git commit -m "feat: trace rag routing evidence and retry decisions"
```

---

### Task 14: 真实集成、覆盖率和发布门禁

**Files:**
- Modify: `rag-service/backend/pom.xml`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/integration/RagPublicApiIntegrationTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/integration/ElasticsearchScopeIntegrationTest.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/integration/MySqlMigrationIntegrationTest.java`
- Create: `rag-service/backend/src/test/resources/application-integration.yml`
- Create: `docs/RAG公共服务API-V1.md`
- Create: `docs/RAG回归评测与发布门禁-V1.md`

**Interfaces:**
- Consumes: 所有前置任务。
- Produces: 可重复的 `mvn clean verify` 发布证据和公共 API 文档。

- [ ] **Step 1: 加入 Testcontainers 依赖**

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>elasticsearch</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 写完整链路 RED 测试**

测试准备：

1. 创建两个调用方。
2. 创建私有、共享、未授权知识库。
3. 写入可区分的测试切片。
4. 使用调用方 A 的 Key 查询。
5. 断言只路由到可访问知识库。
6. 断言引用映射真实切片。
7. 断言 Trace 包含路由、证据和引用阶段。

- [ ] **Step 3: 运行并确认 RED**

```powershell
mvn -Dtest=RagPublicApiIntegrationTest test
```

Expected: 在集成装配完成前 FAIL；失败原因必须是缺少真实装配，而不是 Docker/端口错误。

- [ ] **Step 4: 完成最小集成配置**

使用 Testcontainers 动态注入 MySQL 和 Elasticsearch 地址。Embedding、Rerank、AnswerGenerator 使用确定性的本地测试替身；另设可选 profile 对接真实 TEI，不将外部模型波动作为默认 CI 条件。

- [ ] **Step 5: 开启最终 JaCoCo 门禁**

对新增核心包设置：

```text
LINE   COVEREDRATIO = 1.00
BRANCH COVEREDRATIO = 1.00
```

全仓设置：

```text
LINE   COVEREDRATIO >= 0.95
BRANCH COVEREDRATIO >= 0.90
```

如果全仓历史覆盖率未达标，不降低目标；创建独立补测任务并禁止声称“全覆盖完成”。纯 DTO/JPA 访问器排除项必须在文档逐项列出理由。

- [ ] **Step 6: 运行聚焦集成测试**

```powershell
mvn -Dtest=MySqlMigrationIntegrationTest,ElasticsearchScopeIntegrationTest,RagPublicApiIntegrationTest test
```

Expected: PASS，0 failures，0 errors。

- [ ] **Step 7: 运行完整发布验证**

```powershell
mvn clean verify
```

Expected:

- 全部单元和集成测试通过。
- JaCoCo 门禁通过。
- `RagRegressionTest` 指标达到阈值。
- 无测试打印敏感头、API Key 或数据库密码。

- [ ] **Step 8: 编写公共 API 和评测文档**

`RAG公共服务API-V1.md` 必须包含认证、请求/响应、四种业务 decision、HTTP 状态和脱敏示例。

`RAG回归评测与发布门禁-V1.md` 必须包含数据集格式、指标公式、阈值、运行命令和失败分类。

- [ ] **Step 9: 最终提交**

```powershell
git add rag-service/backend docs
git commit -m "test: verify public rag service end to end"
```

---

## Milestones and Stage Gates

| 里程碑 | 包含任务 | 预计有效工作日 | 必须通过的门禁 |
|---|---:|---:|---|
| M0 基线可控 | 0–1 | 1–2 | Git 正常、现有测试基线、JaCoCo 报告 |
| M1 多租户知识库基础 | 2–5 | 5–7 | ACL、API Key、过滤契约、入库身份 |
| M2 动态路由与拒答 | 6–7 | 4–6 | Top 10/Top 3、无路由拒绝、证据门 |
| M3 引用与二次检索 | 8–10 | 5–7 | 引用真实性、最多一次改写、状态机全分支 |
| M4 评测与公共 API | 11–13 | 4–6 | 回归指标、稳定 API、完整 Trace |
| M5 生产验收 | 14 | 3–5 | Testcontainers、覆盖率、`mvn clean verify` |

**总估算：** 22–33 个有效工作日。该估算不包含管理前端、不包含 Qdrant/Milvus 迁移、不包含生产账号与网络审批等待。

## Execution Rules

1. 每次只领取一个 Task；Task 内严格按 checkbox 顺序执行。
2. 每个 RED 必须记录实际失败原因；编译配置或环境错误不算有效 RED。
3. 每个 Task 独立提交，禁止跨 Task 混合提交。
4. Codex 负责计划、接口契约、代码审查和最终验收；Cursor 未来只领取文件范围明确的子任务。
5. 并行执行前在共享任务账本登记 owner、branch、files、status；同一文件同时只能有一个 owner。
6. 任何阈值调整必须由回归数据支持，并在提交中附指标变化，不得凭感觉修改。
7. 任何新增向量数据库依赖必须另立 ADR 和迁移计划，不在本计划中顺手引入。

## Self-Review Result

- Spec coverage: 四项能力、公共服务、私有/共享 ACL、两阶段 Top 3 路由、无结果拒答均有对应任务。
- Placeholder scan: 无 TBD、TODO 或“后续自行处理”式步骤。
- Type consistency: `clientAppId`、`knowledgeBaseId`、`SearchScope`、`KnowledgeBaseRoute`、`EvidenceAssessment`、`RagQueryResponse` 在生产者和消费者任务间一致。
- Scope boundary: 管理前端和向量数据库迁移明确排除，避免与核心服务同时扩张。
