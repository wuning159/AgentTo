# RAG V1 覆盖率发布门禁收口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用真实行为测试覆盖 RAG 公共服务核心分支，并把 JaCoCo 门禁提升到原计划承诺的标准。

**Architecture:** 先以 `mvn clean verify` 生成的 `jacoco.xml` 为唯一覆盖率证据，按核心业务包的未覆盖行为补测试。DTO、JPA 访问器和框架装配只能在发布门禁文档中逐项说明后排除；不得用排除核心业务分支或降低阈值让构建假通过。核心包达标后，再按包处理全仓缺口并提高 Maven 门禁。

**Tech Stack:** Java 17、JUnit 5、AssertJ、Mockito、Spring Boot 4.1、Testcontainers、JaCoCo 0.8.13、Maven。

## Global Constraints

- 每个生产行为的修改必须先写失败测试并观察到预期 RED；只补测试时也要先运行目标测试确认它能覆盖目标分支。
- 核心包是 `routing`、`evidence`、`citation`、`query`；最终要求行/分支覆盖均为 1.00。
- 全仓最终要求行覆盖不低于 0.95、分支覆盖不低于 0.90；纯 DTO、JPA 访问器、Spring 配置装配的排除理由必须写入发布门禁文档。
- `mvn.cmd clean verify` 必须在 Docker Desktop 可用时执行，且 MySQL/Elasticsearch Testcontainers 不得被跳过。
- 不修改生产业务逻辑来适配覆盖率，除非测试先证明存在业务缺陷。

---

## 文件结构

- `rag-service/backend/src/test/java/com/agentto/rag/citation/CitationValidatorTest.java`：补齐引用归一化、空值和非法引用的所有判断分支。
- `rag-service/backend/src/test/java/com/agentto/rag/evidence/EvidenceGateTest.java`：补齐 rerank/rrf 分数选择与拒答边界。
- `rag-service/backend/src/test/java/com/agentto/rag/query/*Test.java`：覆盖命令/响应不变式、编排 Trace、Controller 失败映射及 Trace 查询边界。
- `rag-service/backend/src/test/java/com/agentto/rag/routing/*Test.java`：覆盖两阶段路由拒绝分支、ES 适配器 HTTP/健康检查/配置边界。
- `rag-service/backend/pom.xml`：仅在测试达标后提升 JaCoCo 规则。
- `docs/RAG回归评测与发布门禁-V1.md`：记录基线、排除项、最终门禁和验证结果。

### Task 1: 固化覆盖率基线与核心不变式

**Files:**
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/query/RagQueryServiceTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/query/RagQueryControllerTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/query/QueryFlowTraceServiceTest.java`

**Interfaces:**
- Consumes: `RagQueryCommand(Long, String, int)`、`RagQueryResponse`、`RagQueryController.query(...)`、`QueryFlowTraceService.list(...)`。
- Produces: 对空调用方、空查询、非正 limit、空列表防御性拷贝、分页边界和控制器生成不可用分支的可执行测试证据。

- [ ] **Step 1: 为 `RagQueryCommand` 的 null 调用方写失败测试**

```java
@Test
void rejectsNullClientAppId() {
    assertThatThrownBy(() -> new RagQueryCommand(null, "预算审批", 8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("clientAppId 不能为空");
}
```

- [ ] **Step 2: 单独运行该测试并确认 RED 或覆盖缺口被定位**

Run: `mvn.cmd -Dtest=RagQueryServiceTest#rejectsNullClientAppId test`

Expected: 测试在添加前不存在；新增后必须通过并在 JaCoCo 中执行紧凑构造器的 null 分支。

- [ ] **Step 3: 为空查询和非法 limit 各写一个独立测试**

```java
assertThatThrownBy(() -> new RagQueryCommand(1L, " ", 8))
        .hasMessage("查询不能为空");
assertThatThrownBy(() -> new RagQueryCommand(1L, "预算审批", 0))
        .hasMessage("finalLimit 必须大于 0");
```

- [ ] **Step 4: 运行聚焦测试并确认 GREEN**

Run: `mvn.cmd -Dtest=RagQueryServiceTest test`

Expected: 0 failures，0 errors。

### Task 2: 补齐证据门和引用真实性的核心分支

**Files:**
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/evidence/EvidenceGateTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/citation/CitationValidatorTest.java`

**Interfaces:**
- Consumes: `EvidenceGate.assess(...)` 与 `CitationValidator.validate(...)`。
- Produces: 分数降级、候选缺失、空 quote、未知 chunkId、规范化匹配和不匹配的测试覆盖。

- [ ] **Step 1: 为只有 `rrfScore` 的候选写失败测试**

```java
@Test
void fallsBackToRrfScoreWhenRerankScoreIsAbsent() {
    EvidenceAssessment assessment = gate.assess(List.of(candidate(null, 0.60)));
    assertThat(assessment.decision()).isEqualTo(EvidenceDecision.INSUFFICIENT_EVIDENCE);
}
```

- [ ] **Step 2: 运行聚焦测试并确认目标分支被执行**

Run: `mvn.cmd -Dtest=EvidenceGateTest#fallsBackToRrfScoreWhenRerankScoreIsAbsent test`

Expected: 断言反映 `scoreOf` 的 rrf 降级规则；失败时先修正测试数据而不是修改生产门限。

- [ ] **Step 3: 为引用校验的空 quote、未知 chunk 和 NFKC/空白归一化分别增加独立测试**

```java
assertThat(validator.validate(List.of(new Citation("chunk-1", "")), evidence).valid()).isFalse();
assertThat(validator.validate(List.of(new Citation("unknown", "真实片段")), evidence).valid()).isFalse();
assertThat(validator.validate(List.of(new Citation("chunk-1", "预算 审批")), evidence).valid()).isTrue();
```

- [ ] **Step 4: 运行两个测试类并确认 GREEN**

Run: `mvn.cmd -Dtest=EvidenceGateTest,CitationValidatorTest test`

Expected: 0 failures，0 errors。

### Task 3: 补齐查询编排、Trace 与 HTTP 映射分支

**Files:**
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/query/RagQueryServiceTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/query/QueryFlowTraceServiceTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/query/RagQueryControllerTest.java`

**Interfaces:**
- Consumes: 一次/二次检索 `RagQueryService.query(...)`、`QueryFlowTraceService.list/detail(...)`、公共端点 `POST /api/v1/rag/query`。
- Produces: 无有效改写、二次检索、无效引用、生成不可用、Trace 截断和空页边界的全分支测试。

- [ ] **Step 1: 为缺失的编排失败分支写失败测试**

```java
@Test
void returnsInsufficientEvidenceWhenRewriteDoesNotChangeQuery() {
    // router 已路由；第一次证据不足；rewriter 返回与原 query 相同的文本
    RagQueryResponse response = service.query(new RagQueryCommand(1L, "预算审批", 8));
    assertThat(response.decision()).isEqualTo(RagQueryDecision.INSUFFICIENT_EVIDENCE);
    assertThat(response.attempts()).hasSize(1);
}
```

- [ ] **Step 2: 运行目标测试并确认 RED/覆盖目标明确**

Run: `mvn.cmd -Dtest=RagQueryServiceTest#returnsInsufficientEvidenceWhenRewriteDoesNotChangeQuery test`

Expected: 新测试在实现前失败；若已有行为正确，先确认 JaCoCo 目标分支从未执行再保留该测试。

- [ ] **Step 3: 以相同方式增加 Trace 列表空结果和 Controller 的 503 映射测试**

```java
assertThat(traceService.list(1, 20)).isEmpty();
mockMvc.perform(post("/api/v1/rag/query").contentType(APPLICATION_JSON)
        .content("{\"query\":\"预算审批\"}"))
        .andExpect(status().isServiceUnavailable());
```

- [ ] **Step 4: 运行查询包测试并确认 GREEN**

Run: `mvn.cmd -Dtest=RagQueryServiceTest,QueryFlowTraceServiceTest,RagQueryControllerTest test`

Expected: 0 failures，0 errors。

### Task 4: 补齐两阶段路由和 Elasticsearch 适配器边界

**Files:**
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/routing/KnowledgeBaseRouterTest.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/routing/ElasticsearchKnowledgeBaseProfileIndexTest.java`

**Interfaces:**
- Consumes: `KnowledgeBaseRouter.route(...)`、`ElasticsearchKnowledgeBaseProfileIndex.search(...)`、`healthy()`。
- Produces: 无 ACL 候选、验证分数临界值、HTTP 非 2xx、无响应体、错误 URL 与健康检查异常的测试证据。

- [ ] **Step 1: 为验证分数恰低于阈值写失败测试**

```java
@Test
void rejectsKnowledgeBaseBelowVerificationThreshold() {
    KnowledgeBaseRoute route = router.route(1L, "预算审批");
    assertThat(route.decision()).isEqualTo(RoutingDecision.NO_RELEVANT_KNOWLEDGE_BASE);
}
```

- [ ] **Step 2: 运行目标测试并确认路由拒绝分支被执行**

Run: `mvn.cmd -Dtest=KnowledgeBaseRouterTest#rejectsKnowledgeBaseBelowVerificationThreshold test`

Expected: 0 failures，且 JaCoCo 覆盖 `verifyKnowledgeBase` 的低分分支。

- [ ] **Step 3: 为 ES 适配器的 4xx/5xx、空响应和 health 网络异常各增加独立测试**

```java
assertThatThrownBy(() -> index.search("预算审批", 3))
        .isInstanceOf(IllegalStateException.class);
assertThat(index.healthy()).isFalse();
```

- [ ] **Step 4: 运行路由包测试并确认 GREEN**

Run: `mvn.cmd -Dtest=KnowledgeBaseRouterTest,ElasticsearchKnowledgeBaseProfileIndexTest test`

Expected: 0 failures，0 errors。

### Task 5: 提升门禁并验证发布证据

**Files:**
- Modify: `rag-service/backend/pom.xml`
- Modify: `docs/RAG回归评测与发布门禁-V1.md`

**Interfaces:**
- Consumes: 任务 1–4 的 JaCoCo 报告。
- Produces: 核心包行/分支 1.00、全仓行 0.95/分支 0.90 的 Maven `verify` 强制门禁和排除理由。

- [ ] **Step 1: 在不改 `pom.xml` 的情况下运行完整报告并记录覆盖数**

Run: `mvn.cmd clean verify`

Expected: `target/site/jacoco/jacoco.xml` 显示核心包和全仓覆盖率达到目标；未达到时回到对应任务补测试，不提升阈值。

- [ ] **Step 2: 只在报告达标后写出会失败的严格门禁配置**

```xml
<limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>1.00</minimum></limit>
<limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>1.00</minimum></limit>
```

- [ ] **Step 3: 增加全仓行/分支 0.95/0.90 门禁与明确 excludes**

```xml
<limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.95</minimum></limit>
<limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.90</minimum></limit>
```

- [ ] **Step 4: 运行 `mvn.cmd clean verify` 并确认 GREEN**

Expected: 0 failures，0 errors，JaCoCo 报告显示所有严格门禁通过。

- [ ] **Step 5: 更新门禁文档和提交**

```powershell
git add rag-service/backend/pom.xml rag-service/backend/src/test/java docs/RAG回归评测与发布门禁-V1.md
git commit -m "test(rag): 收紧 V1 覆盖率发布门禁"
```

## 自检

- 核心包四个目录均有对应任务，未通过排除绕过业务分支。
- 每个任务均从具体测试、聚焦命令和预期结果开始。
- 全仓目标只允许在完整报告达标后写入 `pom.xml`。
