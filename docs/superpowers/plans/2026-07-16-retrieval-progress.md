# 召回实验室检索进度 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为召回实验室增加真实的异步检索阶段进度，展示每个阶段的状态、数量、耗时和降级原因。

**Architecture:** 保留现有同步检索接口，在 `HybridRetrievalService` 中加入可选的进度报告接口。管理后台通过异步任务服务执行同一套检索逻辑，前端每 500 毫秒轮询任务快照，完成后继续使用原有结果表格和 Trace。

**Tech Stack:** Java 21、Spring Boot、Spring MVC、JUnit 5、Vue 3、TypeScript、Element Plus、Vitest。

## Global Constraints

- 页面只展示后端真实阶段，不模拟百分比。
- 阶段顺序固定为关键词召回、查询向量生成、向量召回、结果融合、精排、完成。
- Embedding 和 Rerank 失败属于可降级异常，其他检索异常终止任务。
- 原有 `POST /api/admin/retrieval/search` 行为不变。
- 临时任务完成或失败后保留 30 分钟，最多保留 500 条。
- `E:\AgentTo` 当前不是 Git 仓库，不初始化仓库、不创建 worktree，也不执行提交步骤。

---

### Task 1: 检索阶段事件

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalStage.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalStageStatus.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalProgressReporter.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/HybridRetrievalService.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/HybridRetrievalServiceTest.java`

**Interfaces:**
- `RetrievalProgressReporter.running(RetrievalStage stage)`
- `RetrievalProgressReporter.completed(RetrievalStage stage, long elapsedMs, Integer itemCount)`
- `RetrievalProgressReporter.degraded(RetrievalStage stage, long elapsedMs, String message)`
- `RetrievalProgressReporter.skipped(RetrievalStage stage, String message)`
- `RetrievalProgressReporter.failed(RetrievalStage stage, String message)`
- `HybridRetrievalService.search(RetrievalRequest request, RetrievalProgressReporter reporter)`

- [ ] **Step 1: 写失败测试**

在 `HybridRetrievalServiceTest` 中增加一个记录事件的 reporter，断言正常检索按以下顺序结束：

```java
assertThat(events).containsExactly(
    "RUNNING:KEYWORD", "COMPLETED:KEYWORD:2",
    "RUNNING:EMBEDDING", "COMPLETED:EMBEDDING",
    "RUNNING:VECTOR", "COMPLETED:VECTOR:2",
    "RUNNING:FUSION", "COMPLETED:FUSION:3",
    "RUNNING:RERANK", "COMPLETED:RERANK:2",
    "RUNNING:COMPLETE", "COMPLETED:COMPLETE:2"
);
```

再增加 Embedding 与 Rerank 异常测试，分别断言 `DEGRADED:EMBEDDING`、`SKIPPED:VECTOR` 和 `DEGRADED:RERANK`。

- [ ] **Step 2: 验证测试因缺少接口而失败**

Run:

```powershell
$env:JAVA_HOME='D:\DevTools\Java\jdk-21.0.11+10'
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Dtest=HybridRetrievalServiceTest test
```

Expected: 编译失败，提示 `RetrievalProgressReporter` 或带 reporter 的 `search` 方法不存在。

- [ ] **Step 3: 实现最小阶段报告能力**

`RetrievalStage` 固定包含：

```java
KEYWORD, EMBEDDING, VECTOR, FUSION, RERANK, COMPLETE
```

原 `search(request)` 委托给 `search(request, RetrievalProgressReporter.noop())`。在每个实际调用前上报 `running`，结束后上报耗时和数量；Embedding 与 Rerank 的现有 fallback 分支分别上报降级和跳过。

- [ ] **Step 4: 运行聚焦测试直到通过**

Run: 与 Step 2 相同。

Expected: `HybridRetrievalServiceTest` 全部通过。

---

### Task 2: 异步检索任务和接口

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalStageSnapshot.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalJobSnapshot.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalJobCreated.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalJobState.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalJobService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalJobConfiguration.java`
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalAdminController.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/RetrievalJobServiceTest.java`

**Interfaces:**
- `String RetrievalJobService.create(RetrievalRequest request)`
- `RetrievalJobSnapshot RetrievalJobService.get(String jobUid)`
- `POST /api/admin/retrieval/jobs -> ApiResponse<RetrievalJobCreated>`
- `GET /api/admin/retrieval/jobs/{jobUid} -> ApiResponse<RetrievalJobSnapshot>`

- [ ] **Step 1: 写失败测试**

使用可控 `Executor` 保存待执行命令，断言：

```java
String jobUid = service.create(request);
assertThat(service.get(jobUid).status()).isEqualTo(RetrievalJobStatus.QUEUED);
executor.runNext();
assertThat(service.get(jobUid).status()).isEqualTo(RetrievalJobStatus.COMPLETED);
assertThat(service.get(jobUid).result()).isNotNull();
```

再覆盖任务失败、阶段快照和未知任务编号。

- [ ] **Step 2: 验证测试因类不存在而失败**

Run:

```powershell
$env:JAVA_HOME='D:\DevTools\Java\jdk-21.0.11+10'
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Dtest=RetrievalJobServiceTest test
```

Expected: 编译失败，提示 `RetrievalJobService` 不存在。

- [ ] **Step 3: 实现线程安全任务状态**

`RetrievalJobState` 实现 `RetrievalProgressReporter`，内部使用 `EnumMap<RetrievalStage, RetrievalStageSnapshot>`，所有写入和 `snapshot()` 加锁。阶段快照包含：

```java
RetrievalStage stage,
RetrievalStageStatus status,
Long elapsedMs,
Integer itemCount,
String message
```

任务快照包含 `jobUid`、总体状态、当前阶段、阶段列表、结果、错误、创建和完成时间。

- [ ] **Step 4: 实现异步执行、清理和 HTTP 接口**

`RetrievalJobService` 使用注入的 `Executor` 执行任务；创建时立即返回 UUID。每次创建和查询时清理超过 30 分钟的终态任务，并在超过 500 条时优先删除最早的终态任务。

生产环境使用名为 `retrievalJobExecutor` 的固定线程池，核心线程数 2，最大线程数 4，队列 100。

- [ ] **Step 5: 运行聚焦测试直到通过**

Run: 与 Step 2 相同。

Expected: `RetrievalJobServiceTest` 全部通过。

---

### Task 3: 前端状态映射和轮询

**Files:**
- Create: `rag-service/frontend/src/retrieval/progress.ts`
- Create: `rag-service/frontend/src/retrieval/progress.test.ts`
- Create: `rag-service/frontend/src/retrieval/jobPolling.ts`
- Create: `rag-service/frontend/src/retrieval/jobPolling.test.ts`
- Modify: `rag-service/frontend/src/types/rag.ts`
- Modify: `rag-service/frontend/src/api/rag.ts`

**Interfaces:**
- `RETRIEVAL_STAGES` 固定六个节点及中文名称。
- `formatStageDetail(snapshot): string`
- `startRetrievalJobPolling(load, onUpdate, onFailure, intervalMs?): () => void`
- `createRetrievalJob(query, limits): Promise<RetrievalJobCreated>`
- `retrievalJob(jobUid): Promise<RetrievalJobSnapshot>`

- [ ] **Step 1: 写状态映射失败测试**

断言 86 毫秒显示为 `20 条 / 86 ms`，2820 毫秒显示为 `15 条 / 2.82 s`，Embedding 不显示数量，降级和跳过保留说明。

- [ ] **Step 2: 写轮询失败测试**

使用 Vitest fake timers 断言轮询会立即执行、每 500 毫秒继续执行、完成后停止、连续失败 3 次后调用失败回调、主动 stop 后不再请求。

- [ ] **Step 3: 运行测试并确认因模块不存在而失败**

Run:

```powershell
npm.cmd test -- src/retrieval/progress.test.ts src/retrieval/jobPolling.test.ts
```

Expected: FAIL，提示模块不存在。

- [ ] **Step 4: 实现类型、格式化和轮询工具**

轮询器每次收到 `COMPLETED` 或 `FAILED` 总体状态后不再安排定时器；请求异常累计到 3 次后停止并回调。任何一次成功请求都把连续错误计数清零。

- [ ] **Step 5: 运行聚焦测试直到通过**

Run: 与 Step 3 相同。

Expected: 两个测试文件全部通过。

---

### Task 4: 页面接入和完整验证

**Files:**
- Modify: `rag-service/frontend/src/views/RetrievalView.vue`
- Modify: `rag-service/frontend/src/styles.css`

**Interfaces:**
- Consumes: Task 2 的异步任务接口。
- Consumes: Task 3 的状态映射和轮询停止函数。
- Produces: 查询区域下方的六阶段真实进度条及最终结果展示。

- [ ] **Step 1: 接入创建任务和轮询**

`run()` 清理旧轮询和结果，创建任务后启动轮询。收到完成快照时写入 `result`，收到失败时显示错误。`onBeforeUnmount` 中停止轮询。

- [ ] **Step 2: 增加线性阶段组件结构和样式**

在查询控制台和结果区之间显示六个等宽节点，连接线位于节点圆点中心；运行中使用绿色脉冲，完成为绿色，降级和跳过为黄色，失败为红色。窄屏时允许横向滚动，不改变当前桌面端主布局。

- [ ] **Step 3: 运行完整单元测试**

Run:

```powershell
npm.cmd test
$env:JAVA_HOME='D:\DevTools\Java\jdk-21.0.11+10'
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' test
```

Expected: 前后端全部测试通过，0 failures、0 errors。

- [ ] **Step 4: 运行生产构建**

Run:

```powershell
npm.cmd run build
$env:JAVA_HOME='D:\DevTools\Java\jdk-21.0.11+10'
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -DskipTests package
```

Expected: 两个命令 exit code 0。

- [ ] **Step 5: 重启后端并做真实检索验证**

验证创建任务立即返回，至少观察到一个非终态快照，最终状态为 `COMPLETED`，六个阶段均有实际状态，最终结果和 Trace 可以打开。浏览器确认执行期间不再只有按钮转圈。

