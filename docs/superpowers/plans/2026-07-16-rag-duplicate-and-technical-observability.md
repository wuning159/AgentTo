# RAG Duplicate Governance and Technical Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 阻止完全相同文件重复入库，保证最终检索结果内容唯一，并让技术管理员能够查看文档入库和检索执行的真实步骤、参数、公式、样本与完整报告。

**Architecture:** 上传阶段用文件 SHA-256 做幂等判断；检索阶段在 RRF 之后、Rerank 之前用规范化内容哈希合并重复候选，同时把完整候选和去重关系写入 Trace。技术执行数据使用现有入库阶段表和检索 Trace 表持久化，前端复用一套“时间线 + 技术检查器 + 完整报告”组件。

**Tech Stack:** Java 25、Spring Boot 4.1、Spring Data JPA、Flyway、MySQL、Elasticsearch、JUnit 5、AssertJ、Vue 3、TypeScript 6、Element Plus、Vitest。

## Global Constraints

- 后端端口保持 `18473`，前端端口保持 `5174`。
- 实际联调继续使用阿里云 MySQL、MinIO、Elasticsearch 和 TEI 服务；自动化测试使用 H2 和内存替身。
- Maven 必须使用 `D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd`。
- 不优化 PDF 分块，不增加语义近似去重，不自动删除历史重复数据。
- 页面不能返回或展示密码、Token、API Key、Authorization 头和完整向量。
- 当前 `E:\AgentTo` 不是 Git 仓库，实施过程中不执行提交命令，也不擅自初始化 Git。

---

## File Structure

### 后端

- `document/DocumentService.java`：上传 SHA-256 幂等判断。
- `document/DocumentVersionRepository.java`：按 SHA-256 查询已有版本。
- `document/UploadResult.java`：同时表达新上传和重复命中。
- `retrieval/ContentFingerprint.java`：规范化正文并计算内容哈希。
- `retrieval/ContentDeduplicator.java`：在 RRF 后选择代表候选并记录重复关系。
- `retrieval/DedupeResult.java`、`retrieval/DedupeStatus.java`：去重结果契约。
- `retrieval/HybridRetrievalService.java`：接入内容去重和执行报告。
- `observability/TechnicalStageDetail.java`、`observability/ExecutionEvent.java`、`observability/ExecutionReport.java`：入库和检索共用的技术观测模型。
- `ingestion/IngestionTechnicalDetailFactory.java`：生成解析、清洗、分块、向量化和索引详情。
- `retrieval/RetrievalExecutionReportBuilder.java`：生成检索阶段报告。
- `db/migration/V3__add_duplicate_and_observability_fields.sql`：增加持久化字段和查询索引。

### 前端

- `components/observability/ExecutionSummary.vue`：任务摘要。
- `components/observability/ExecutionTimeline.vue`：完整阶段时间线。
- `components/observability/TechnicalInspector.vue`：说明、参数、公式、样本和脱敏原始数据。
- `components/observability/ExecutionReport.vue`：可折叠完整报告。
- `components/observability/ExecutionWorkbench.vue`：组合上述组件。
- `observability/presentation.ts`：状态、耗时、数量、RRF 贡献和安全 JSON 的纯函数。
- `views/TracesView.vue`：使用执行工作台展示检索 Trace。
- `views/DocumentsView.vue`、`views/DocumentDetailView.vue`：使用执行工作台展示入库过程。

---

### Task 1: 完全相同文件上传幂等处理

**Files:**
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentVersionRepository.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/UploadResult.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentService.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentAdminController.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/document/DocumentServiceTest.java`
- Modify: `rag-service/frontend/src/types/rag.ts`
- Modify: `rag-service/frontend/src/views/DocumentsView.vue`
- Create: `rag-service/frontend/src/documents/uploadResult.ts`
- Create: `rag-service/frontend/src/documents/uploadResult.test.ts`

**Interfaces:**
- Produces: `Optional<RagDocumentVersion> findFirstBySha256OrderByCreatedAtDesc(String sha256)`。
- Produces: `UploadResult(Long documentId, Long versionId, Long jobId, String objectKey, boolean duplicate, String message)`。
- Produces: `uploadAction(result: UploadResult): 'OPEN_EXISTING' | 'WATCH_JOB'`。

- [ ] **Step 1: 增加后端重复上传失败测试**

在 `DocumentServiceTest` 增加：

```java
@Test
void exactDuplicateReturnsExistingVersionWithoutWritingStorageOrCreatingJob() {
    byte[] bytes = "same-docx-content".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile first = new MockMultipartFile("file", "制度.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
    MockMultipartFile second = new MockMultipartFile("file", "制度副本.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);

    UploadResult created = documentService.upload(first, "公司制度", adminId);
    UploadResult duplicate = documentService.upload(second, "其他分类", adminId);

    assertThat(duplicate.duplicate()).isTrue();
    assertThat(duplicate.documentId()).isEqualTo(created.documentId());
    assertThat(duplicate.versionId()).isEqualTo(created.versionId());
    assertThat(duplicate.jobId()).isNull();
    assertThat(storage.size()).isOne();
    assertThat(documentRepository.count()).isOne();
    assertThat(versionRepository.count()).isOne();
    assertThat(jobRepository.count()).isOne();
}
```

- [ ] **Step 2: 运行测试并确认当前实现失败**

Run:

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml -Dtest=DocumentServiceTest test
```

Expected: 新测试因第二次上传仍创建文档、版本、对象和任务而失败。

- [ ] **Step 3: 实现 SHA-256 查询和幂等返回**

`DocumentVersionRepository` 增加：

```java
Optional<RagDocumentVersion> findFirstBySha256OrderByCreatedAtDesc(String sha256);
```

`UploadResult` 调整为：

```java
public record UploadResult(Long documentId, Long versionId, Long jobId, String objectKey,
        boolean duplicate, String message) {
    public static UploadResult created(Long documentId, Long versionId, Long jobId, String objectKey) {
        return new UploadResult(documentId, versionId, jobId, objectKey, false, "文件已进入处理队列");
    }

    public static UploadResult duplicate(RagDocumentVersion version) {
        return new UploadResult(version.getDocumentId(), version.getId(), null, version.getObjectKey(), true,
                "该文件已经入库，已为你打开已有文档");
    }
}
```

`DocumentService.upload()` 在 `storage.put()` 之前增加：

```java
String sha256 = sha256(bytes);
Optional<RagDocumentVersion> existing = versionRepository.findFirstBySha256OrderByCreatedAtDesc(sha256);
if (existing.isPresent()) {
    return UploadResult.duplicate(existing.get());
}
```

新建成功时改用 `UploadResult.created(...)`。`DocumentAdminController` 只在 `jobId != null` 时调用 `ingestionLauncher.launch()`。

- [ ] **Step 4: 运行后端测试确认通过**

Run: 与 Step 2 相同。

Expected: `DocumentServiceTest` 全部通过，重复上传不会写第二份对象。

- [ ] **Step 5: 增加前端上传结果纯函数测试**

`uploadResult.test.ts`：

```ts
import { describe, expect, it } from 'vitest'
import { uploadAction } from './uploadResult'

describe('uploadAction', () => {
  it('opens existing document for an exact duplicate', () => {
    expect(uploadAction({ documentId: 1, versionId: 2, jobId: null, objectKey: 'x', duplicate: true,
      message: 'already exists' })).toBe('OPEN_EXISTING')
  })

  it('watches ingestion for a new upload', () => {
    expect(uploadAction({ documentId: 1, versionId: 2, jobId: 3, objectKey: 'x', duplicate: false,
      message: 'queued' })).toBe('WATCH_JOB')
  })
})
```

- [ ] **Step 6: 实现前端重复提示和跳转**

`uploadResult.ts`：

```ts
import type { UploadResult } from '@/types/rag'

export function uploadAction(result: UploadResult): 'OPEN_EXISTING' | 'WATCH_JOB' {
  return result.duplicate || result.jobId == null ? 'OPEN_EXISTING' : 'WATCH_JOB'
}
```

`DocumentsView.submit()` 在上传成功后分支处理：

```ts
if (uploadAction(result) === 'OPEN_EXISTING') {
  ElMessage.warning(result.message)
  await router.push(`/documents/${result.documentId}`)
  return
}
jobDrawer.value = true
await watchJob(result.jobId)
ElMessage.success(result.message)
```

- [ ] **Step 7: 验证前端测试和构建**

Run:

```powershell
npm.cmd test --prefix E:\AgentTo\rag-service\frontend
npm.cmd run build --prefix E:\AgentTo\rag-service\frontend
```

Expected: Vitest 全部通过，TypeScript 和 Vite 构建成功。

---

### Task 2: RRF 后的内容去重

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/ContentFingerprint.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/ContentDeduplicator.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/DedupeResult.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/DedupeStatus.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalCandidate.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/HybridRetrievalService.java`
- Create: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/ContentDeduplicatorTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/HybridRetrievalServiceTest.java`

**Interfaces:**
- Produces: `String ContentFingerprint.sha256(String content)`。
- Produces: `DedupeResult ContentDeduplicator.dedupe(List<RetrievalCandidate> ranked, int limit)`。
- Produces: `DedupeResult(List<RetrievalCandidate> selected, List<RetrievalCandidate> traceCandidates, int duplicateCount)`。

- [ ] **Step 1: 为规范化哈希和代表候选选择编写失败测试**

```java
@Test
void mergesContentThatOnlyDiffersByUnicodeWidthAndWhitespace() {
    RetrievalCandidate first = RetrievalCandidate.keyword("chunk-a", "HITL 是什么", 1, 1)
            .withRrf(0.030, 1);
    RetrievalCandidate duplicate = RetrievalCandidate.keyword("chunk-b", "ＨＩＴＬ\n  是什么", 1, 2)
            .withRrf(0.029, 2);
    RetrievalCandidate different = RetrievalCandidate.keyword("chunk-c", "RACI 是什么", 1, 3)
            .withRrf(0.028, 3);

    DedupeResult result = new ContentDeduplicator().dedupe(List.of(first, duplicate, different), 8);

    assertThat(result.selected()).extracting(RetrievalCandidate::chunkId)
            .containsExactly("chunk-a", "chunk-c");
    assertThat(result.duplicateCount()).isEqualTo(1);
    assertThat(result.traceCandidates()).filteredOn(c -> c.chunkId().equals("chunk-b"))
            .first().extracting(RetrievalCandidate::duplicateOfChunkId).isEqualTo("chunk-a");
}
```

- [ ] **Step 2: 运行测试并确认类尚不存在**

Run:

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml -Dtest=ContentDeduplicatorTest test
```

Expected: 编译失败，提示 `ContentDeduplicator` 和去重字段不存在。

- [ ] **Step 3: 实现确定性内容哈希**

```java
public final class ContentFingerprint {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ContentFingerprint() {}

    public static String sha256(String content) {
        String normalized = WHITESPACE.matcher(Normalizer.normalize(
                content == null ? "" : content, Normalizer.Form.NFKC)).replaceAll(" ").trim();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
```

`RetrievalCandidate` 增加 `contentHash`、`dedupeStatus`、`duplicateOfChunkId` 三个末尾字段，并增加 `withDedupe(...)`。所有现有工厂方法的默认值为 `null`、`PENDING`、`null`。

- [ ] **Step 4: 实现内容去重器**

```java
public DedupeResult dedupe(List<RetrievalCandidate> ranked, int limit) {
    Map<String, RetrievalCandidate> representativeByHash = new LinkedHashMap<>();
    List<RetrievalCandidate> trace = new ArrayList<>();
    List<RetrievalCandidate> selected = new ArrayList<>();
    int duplicateCount = 0;
    for (RetrievalCandidate candidate : ranked) {
        String hash = ContentFingerprint.sha256(candidate.content());
        RetrievalCandidate representative = representativeByHash.get(hash);
        if (representative == null) {
            RetrievalCandidate kept = candidate.withDedupe(hash, DedupeStatus.KEPT, null);
            representativeByHash.put(hash, kept);
            trace.add(kept);
            if (selected.size() < Math.max(limit, 0)) selected.add(kept);
        } else {
            trace.add(candidate.withDedupe(hash, DedupeStatus.DUPLICATE, representative.chunkId()));
            duplicateCount++;
        }
    }
    return new DedupeResult(List.copyOf(selected), List.copyOf(trace), duplicateCount);
}
```

- [ ] **Step 5: 在混合检索中接入 RRF 后去重**

把融合调用调整为先计算全部并集候选，再截取唯一内容：

```java
List<RetrievalCandidate> fusedAll = fusion.fuse(keyword, vector, keyword.size() + vector.size());
DedupeResult dedupe = contentDeduplicator.dedupe(fusedAll, request.fusionLimit());
List<RetrievalCandidate> fused = dedupe.selected();
```

Rerank 只接收 `fused`。写 Trace 时，把 Rerank 和 final 标注按 `chunkId` 合并回 `dedupe.traceCandidates()`；重复候选保留 RRF 分数和重复关系，但不产生 Rerank 排名和最终排名。

- [ ] **Step 6: 增加检索服务回归测试**

测试关键词和向量分别返回同内容的两个不同 `chunkId`，断言：

```java
assertThat(response.candidates()).extracting(RetrievalCandidate::contentHash).doesNotHaveDuplicates();
assertThat(recordingTraceRecorder.candidates()).hasSizeGreaterThan(response.candidates().size());
assertThat(recordingTraceRecorder.candidates()).anyMatch(
        candidate -> candidate.dedupeStatus() == DedupeStatus.DUPLICATE);
```

- [ ] **Step 7: 运行检索单元测试**

Run:

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml -Dtest=ContentDeduplicatorTest,RrfFusionTest,HybridRetrievalServiceTest test
```

Expected: 三组测试全部通过，RRF 原有排序测试不回退。

---

### Task 3: 持久化去重关系和技术执行详情

**Files:**
- Create: `rag-service/backend/src/main/resources/db/migration/V3__add_duplicate_and_observability_fields.sql`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/observability/TechnicalStageDetail.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/observability/ExecutionEvent.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/observability/ExecutionReport.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionStage.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionStageView.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryTraceEntity.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryCandidateEntity.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/database/FlywayMigrationTest.java`

**Interfaces:**
- Produces: `TechnicalStageDetail(String summary, Integer inputCount, Integer outputCount, Map<String,Object> parameters, Map<String,Object> metrics, List<Map<String,Object>> samples, Map<String,Object> raw)`。
- Produces: `ExecutionReport(List<ExecutionEvent> events)`。

- [ ] **Step 1: 扩展迁移测试并确认 V3 尚未存在**

在 `FlywayMigrationTest` 验证以下列存在：

```java
assertColumnExists("RAG_INGESTION_STAGE", "TECHNICAL_DETAIL_JSON");
assertColumnExists("RAG_QUERY_TRACE", "RANK_CONSTANT");
assertColumnExists("RAG_QUERY_TRACE", "DEDUPLICATED_COUNT");
assertColumnExists("RAG_QUERY_TRACE", "EXECUTION_REPORT_JSON");
assertColumnExists("RAG_QUERY_CANDIDATE", "CONTENT_HASH");
assertColumnExists("RAG_QUERY_CANDIDATE", "DEDUPE_STATUS");
assertColumnExists("RAG_QUERY_CANDIDATE", "DUPLICATE_OF_CHUNK_UID");
```

并在测试类中增加确定性的查询方法：

```java
private void assertColumnExists(String table, String column) {
    Integer count = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.columns where upper(table_name) = ? and upper(column_name) = ?",
            Integer.class, table, column);
    assertThat(count).isEqualTo(1);
}
```

- [ ] **Step 2: 新增 Flyway V3**

```sql
alter table rag_ingestion_stage
    add column technical_detail_json longtext null after detail_message;

alter table rag_query_trace
    add column rank_constant int not null default 60 after final_limit,
    add column deduplicated_count int not null default 0 after result_count,
    add column execution_report_json longtext null after deduplicated_count;

alter table rag_query_candidate
    add column content_hash varchar(64) null after chunk_uid,
    add column dedupe_status varchar(16) not null default 'KEPT' after content_hash,
    add column duplicate_of_chunk_uid varchar(64) null after dedupe_status;

create index idx_rag_version_sha256 on rag_document_version(sha256);
create index idx_rag_candidate_trace_hash on rag_query_candidate(trace_id, content_hash);
```

- [ ] **Step 3: 增加共用技术观测记录**

创建不可变 record，并在紧凑构造器中把 `null` 集合转为空集合、把样本文本截断到 1000 字符。`raw` 只允许基本类型、集合和 Map，不接受请求头、凭据或完整向量。

- [ ] **Step 4: 映射实体字段**

`IngestionStage` 增加 `technicalDetailJson`；`QueryTraceEntity` 增加 `rankConstant`、`deduplicatedCount` 和 `executionReportJson`；`QueryCandidateEntity` 从 `RetrievalCandidate` 保存三个去重字段。

`IngestionStageView` 返回 `TechnicalStageDetail technicalDetail`，由 `IngestionQueryService` 使用 `ObjectMapper` 反序列化；空值返回 `null`，损坏的历史 JSON 返回只包含“技术详情无法解析”的安全摘要，不能让整个接口返回 500。

- [ ] **Step 5: 运行迁移和实体相关测试**

Run:

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml -Dtest=FlywayMigrationTest,DocumentServiceTest,QueryTraceServiceTest test
```

Expected: H2 从 V1 到 V3 迁移成功，历史默认值可读取。

---

### Task 4: 记录文档入库每个阶段的真实参数和样本

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionTechnicalDetailFactory.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionOrchestrator.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionConfiguration.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/ingestion/IngestionOrchestratorTest.java`

**Interfaces:**
- Consumes: `TechnicalStageDetail` from Task 3。
- Produces: `IngestionTechnicalDetailFactory.parse(...)`、`clean(...)`、`chunk(...)`、`embed(...)`、`index(...)`。

- [ ] **Step 1: 写入库详情失败测试**

完成一份 DOCX 入库后断言：

```java
IngestionStageView chunk = queryService.job(jobId).stages().stream()
        .filter(stage -> stage.stage().equals("CHUNK")).findFirst().orElseThrow();
assertThat(chunk.technicalDetail().parameters())
        .containsEntry("targetChars", 500)
        .containsEntry("maxChars", 800)
        .containsEntry("overlapChars", 80);
assertThat(chunk.technicalDetail().inputCount()).isPositive();
assertThat(chunk.technicalDetail().outputCount()).isPositive();
```

- [ ] **Step 2: 运行测试确认技术详情为空**

Run:

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml -Dtest=IngestionOrchestratorTest test
```

Expected: 断言失败，因为现有阶段只保存一句 `detailMessage`。

- [ ] **Step 3: 实现阶段详情工厂**

每个方法只接收已经产生的执行数据，不在工厂内重新解析文件或调用外部服务。样本规则固定为最多 3 条，每条正文最多 500 字符；Embedding 样本只保留维度和前 8 维。

分块详情参数必须直接来自 `ChunkingProperties`：

```java
Map.of(
    "strategy", "STRUCTURE_AWARE_PARAGRAPH",
    "targetChars", properties.targetChars(),
    "maxChars", properties.maxChars(),
    "overlapChars", properties.overlapChars(),
    "oversizedSplitOrder", List.of("SENTENCE", "CLAUSE", "HARD_LIMIT")
)
```

- [ ] **Step 4: 在 Orchestrator 中保存阶段详情**

为清洗阶段同时保留清洗前块数、清洗后块数和空块删除数；为向量化阶段保存批量大小 16、实际向量数和实际维度；为索引阶段保存 `chunkIndex.indexVersion()` 和写入数量。

所有详情先由 `ObjectMapper.writeValueAsString()` 序列化，再传给新的 `IngestionStage.success(..., technicalDetailJson, ...)`。失败阶段保存错误类型和安全错误说明，不保存堆栈。

- [ ] **Step 5: 运行入库相关测试**

Run:

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml -Dtest=IngestionOrchestratorTest,StructureAwareChunkerTest,DocumentParserTest test
```

Expected: 入库、DOCX 分块和解析测试全部通过。

---

### Task 5: 保存检索执行报告、RRF 参数和去重事件

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalExecutionReportBuilder.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalStage.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/HybridRetrievalService.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/TraceRecorder.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryTraceService.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryTraceDetail.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/QueryTraceCandidate.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/HybridRetrievalServiceTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/QueryTraceServiceTest.java`

**Interfaces:**
- Produces: `TraceRecorder.record(request, candidates, timings, fallbackReason, rankConstant, duplicateCount, report)`。
- Produces: `QueryTraceDetail` 中的 `limits`、`rankConstant`、`deduplicatedCount`、`executionReport`。

- [ ] **Step 1: 增加 Trace 完整报告失败测试**

```java
QueryTraceDetail detail = service.detail(traceUid);
assertThat(detail.rankConstant()).isEqualTo(60);
assertThat(detail.executionReport().events()).extracting(ExecutionEvent::stage)
        .containsExactly("PREPROCESS", "KEYWORD", "EMBEDDING", "VECTOR", "FUSION", "DEDUPE", "RERANK", "COMPLETE");
assertThat(detail.executionReport().events()).filteredOn(event -> event.stage().equals("FUSION"))
        .first().extracting(event -> event.detail().parameters().get("rankConstant")).isEqualTo(60);
```

- [ ] **Step 2: 扩展阶段枚举和实时进度顺序**

`RetrievalStage` 顺序改为：

```java
PREPROCESS, KEYWORD, EMBEDDING, VECTOR, FUSION, DEDUPE, RERANK, COMPLETE
```

预处理记录查询长度和限制参数，不保存额外的完整查询副本；DEDUPE 记录输入数量、唯一内容数量、合并数量和规范化算法标识。

- [ ] **Step 3: 实现执行报告构建器**

构建器为每个阶段只追加一次最终事件，事件包含状态、开始时间、结束时间、耗时和 `TechnicalStageDetail`。发生降级时状态使用 `DEGRADED`，跳过时使用 `SKIPPED`，不可恢复错误使用 `FAILED`。

- [ ] **Step 4: 在 HybridRetrievalService 中同步上报和记录**

每个真实步骤开始时继续调用 `RetrievalProgressReporter.running()`；结束时同时更新 reporter 和 report builder。RRF 详情保存：

```java
Map.of("rankConstant", 60, "keywordLimit", request.keywordLimit(),
       "vectorLimit", request.vectorLimit(), "fusionLimit", request.fusionLimit())
```

样本从候选中最多取 3 条，保存 `chunkId`、两路排名、两路原始分数、RRF 分数和 RRF 排名。

- [ ] **Step 5: 持久化并返回完整报告**

`QueryTraceEntity.create()` 接收并保存 `rankConstant`、`duplicateCount` 和报告 JSON。`QueryTraceService.detail()` 反序列化报告；历史 Trace 没有 JSON 时，使用现有 timings 和候选数据生成只读的兼容报告，并标记 `historicalSnapshot=false`。

- [ ] **Step 6: 运行检索任务、Trace 和降级测试**

Run:

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml -Dtest=HybridRetrievalServiceTest,RetrievalJobServiceTest,QueryTraceServiceTest test
```

Expected: 正常、Embedding 降级、Rerank 降级和 Trace 兼容读取全部通过。

---

### Task 6: 建立前端技术观测类型和纯函数

**Files:**
- Modify: `rag-service/frontend/src/types/rag.ts`
- Create: `rag-service/frontend/src/observability/presentation.ts`
- Create: `rag-service/frontend/src/observability/presentation.test.ts`

**Interfaces:**
- Produces: `TechnicalStageDetail`、`ExecutionEvent`、`ExecutionReport`、`ExecutionStageViewModel`。
- Produces: `rrfContribution(rank, rankConstant)`、`stageViewModel(event)`、`safeJson(value)`。

- [ ] **Step 1: 写 RRF 和状态展示失败测试**

```ts
it('calculates the displayed RRF contribution from the saved rank constant', () => {
  expect(rrfContribution(5, 60)).toBeCloseTo(1 / 65, 8)
})

it('does not turn a degraded event into a successful event', () => {
  const degraded: ExecutionEvent = {
    stage: 'RERANK', status: 'DEGRADED', startedAt: '2026-07-16T01:00:00Z',
    finishedAt: '2026-07-16T01:00:01Z', elapsedMs: 1000,
    detail: { summary: '精排不可用', inputCount: 15, outputCount: 8,
      parameters: {}, metrics: {}, samples: [], raw: {} },
  }
  expect(stageViewModel(degraded).tone).toBe('warning')
})

it('redacts credential-shaped keys from raw data', () => {
  expect(safeJson({ token: 'secret', dimensions: 1024 })).toContain('***')
})
```

- [ ] **Step 2: 实现纯函数**

```ts
export function rrfContribution(rank: number | null, k: number): number {
  return rank == null ? 0 : 1 / (k + rank)
}

const SECRET_KEY = /password|token|secret|authorization|api[-_]?key/i

export function sanitize(value: unknown): unknown {
  if (Array.isArray(value)) return value.slice(0, 50).map(sanitize)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .map(([key, item]) => [key, SECRET_KEY.test(key) ? '***' : sanitize(item)]))
  }
  return value
}
```

- [ ] **Step 3: 运行前端纯函数测试**

Run:

```powershell
npm.cmd test --prefix E:\AgentTo\rag-service\frontend -- presentation.test.ts
```

Expected: 新增测试全部通过。

---

### Task 7: 实现共用执行工作台组件

**Files:**
- Create: `rag-service/frontend/src/components/observability/ExecutionSummary.vue`
- Create: `rag-service/frontend/src/components/observability/ExecutionTimeline.vue`
- Create: `rag-service/frontend/src/components/observability/TechnicalInspector.vue`
- Create: `rag-service/frontend/src/components/observability/ExecutionReport.vue`
- Create: `rag-service/frontend/src/components/observability/ExecutionWorkbench.vue`
- Modify: `rag-service/frontend/src/styles.css`

**Interfaces:**
- Consumes: `ExecutionReport`、当前选中阶段和可选候选。
- Produces: `select-stage` 和 `select-candidate` 事件。

- [ ] **Step 1: 先建立组件数据边界**

`ExecutionWorkbench.vue` props 固定为：

```ts
const props = defineProps<{
  title: string
  subtitle: string
  runId: string
  totalMs: number | null
  resultCount: number | null
  report: ExecutionReport
  candidates?: TraceCandidate[]
  rankConstant?: number
}>()
```

内部状态只保存 `selectedStage`、`selectedCandidateId` 和 `reportExpanded`，不复制后端报告数据。

- [ ] **Step 2: 实现顶部摘要和时间线**

时间线严格按 `report.events` 顺序渲染，节点状态来自后端。节点显示阶段名称、耗时、输出数量和降级标签；点击节点发出 `select-stage`。

- [ ] **Step 3: 实现技术检查器四个页签**

- “处理说明”渲染 `detail.summary`；
- “参数与公式”渲染参数表，并在 FUSION 阶段显示 RRF 公式和选中候选贡献；
- “输入输出样本”渲染最多 3 条样本；
- “原始响应”使用 `safeJson()` 后以可复制代码块展示。

未知字段按键值表显示，保证后端以后增加指标时页面不会崩溃。

- [ ] **Step 4: 实现完整执行报告**

默认折叠，只显示事件总数、是否降级和总耗时。展开后使用时间、事件、执行记录、输入输出和结果五列；失败和降级事件保持醒目，但不使用遮挡正文的大面积红色。

- [ ] **Step 5: 构建并处理 TypeScript 错误**

Run:

```powershell
npm.cmd run build --prefix E:\AgentTo\rag-service\frontend
```

Expected: `vue-tsc --noEmit` 和 Vite 构建成功。

---

### Task 8: 接入检索 Trace 和文档入库页面

**Files:**
- Modify: `rag-service/frontend/src/views/TracesView.vue`
- Modify: `rag-service/frontend/src/views/DocumentsView.vue`
- Modify: `rag-service/frontend/src/views/DocumentDetailView.vue`
- Modify: `rag-service/frontend/src/api/rag.ts`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionJobRepository.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionQueryService.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentAdminController.java`

**Interfaces:**
- Produces: `GET /api/admin/versions/{versionId}/ingestion` 返回该版本最近一次入库任务。

- [ ] **Step 1: 增加按版本查询最近入库任务接口**

`IngestionJobRepository`：

```java
Optional<IngestionJob> findFirstByVersionIdOrderByCreatedAtDesc(Long versionId);
```

`IngestionQueryService` 复用现有 `job(Long jobId)` 映射，找不到时返回 `JOB_NOT_FOUND` 404。Controller 增加 `/versions/{versionId}/ingestion`。

- [ ] **Step 2: 升级 Trace 抽屉**

`TracesView.vue` 保留“最终结果 / 全部候选”能力，把顶部耗时卡和候选分数表放入 `ExecutionWorkbench` 的候选区域。默认选中 COMPLETE；用户点击候选时，FUSION 页签显示该候选的 RRF 计算，DEDUPE 页签显示它是否被合并。

- [ ] **Step 3: 升级上传后的入库抽屉**

任务执行中继续每 1.5 秒轮询，时间线使用实时阶段；已完成阶段可以点击并查看已保存详情。未完成阶段只显示“正在执行”，不能伪造参数和样本。

- [ ] **Step 4: 在文档详情页增加处理过程入口**

选择版本后调用 `GET /versions/{versionId}/ingestion`，打开同一个 `ExecutionWorkbench`。没有入库任务的历史版本显示明确空状态，不返回 500。

- [ ] **Step 5: 运行前端测试和构建**

Run:

```powershell
npm.cmd test --prefix E:\AgentTo\rag-service\frontend
npm.cmd run build --prefix E:\AgentTo\rag-service\frontend
```

Expected: 全部前端测试通过，生产构建成功。

---

### Task 9: 全量自动化与真实环境验收

**Files:**
- Modify only if a failing test identifies a real defect in files listed above.

**Interfaces:**
- Consumes: Tasks 1-8 的接口和页面。
- Produces: 可由用户直接检查的运行中前后端服务。

- [ ] **Step 1: 运行后端全量测试**

```powershell
D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd -f E:\AgentTo\rag-service\backend\pom.xml test
```

Expected: Maven `BUILD SUCCESS`，无失败和错误测试。

- [ ] **Step 2: 运行前端全量测试和构建**

```powershell
npm.cmd test --prefix E:\AgentTo\rag-service\frontend
npm.cmd run build --prefix E:\AgentTo\rag-service\frontend
```

Expected: Vitest 全部通过，Vite 构建成功。

- [ ] **Step 3: 在阿里云中间件上验证重复上传**

使用同一份 DOCX 连续上传两次，记录上传前后的文档、版本、入库任务、MinIO 对象和 Elasticsearch 分块数量。第二次返回已有文档，四类数量均不增加。

- [ ] **Step 4: 验证历史重复内容检索**

使用“`HITL 是什么`”执行检索，确认最终结果的 `contentHash` 无重复；完整候选仍能看到被合并候选、代表候选和合并数量。

- [ ] **Step 5: 验证执行工作台**

分别打开一条完成的入库任务和一条检索 Trace，确认：

- 时间线状态、耗时和数量与接口一致；
- RRF `k=60`、排名贡献和总分计算一致；
- 分块参数显示 `500 / 800 / 80`；
- 降级状态在摘要、时间线、检查器和报告中一致；
- 完整报告可以展开；
- 页面没有接口 500，没有泄露凭据和完整向量。

- [ ] **Step 6: 保持前后端运行供用户确认**

启动后确认 `http://127.0.0.1:5174` 和 `http://127.0.0.1:18473` 可访问。用户确认前不关闭服务；用户确认完成后再按要求停止。
