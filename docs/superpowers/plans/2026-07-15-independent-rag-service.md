# Independent RAG Service Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a separately runnable RAG administration system under `E:\AgentTo\rag-service` that supports technical-admin login, Word/PDF/Excel ingestion, visible chunks, hybrid retrieval, reranking, and query traces.

**Architecture:** A modular Spring Boot 4.1 application owns RAG metadata and orchestration while MinIO, Elasticsearch, TEI Embedding, and TEI Rerank remain replaceable adapters. A Vue 3 administration frontend reuses the proven workbench, document-detail, timeline, chunk-table, retrieval-lab, and candidate-pool interactions from `E:\RagAiDemo` without migrating FAQ, group, Q&A, or assistant features.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, Spring Data JPA, Flyway, MySQL, MinIO Java SDK, Apache POI, PDFBox, Elasticsearch REST API, TEI HTTP APIs, Vue 3, TypeScript, Vite, Pinia, Vue Router, Axios, Element Plus.

## Global Constraints

- Create all new code under `E:\AgentTo\rag-service`; do not modify `E:\RagAiDemo`.
- Reuse compatible parser, TEI, MinIO, hybrid retrieval, RRF, rerank, trace, and administration UI logic from `E:\RagAiDemo`.
- Do not migrate FAQ templates, FAQ row chunking, groups, end-user Q&A, or assistant chat.
- Use Spring Boot `4.1.0`, Spring AI `2.0.0`, and Java `21`.
- Backend port is `10002`; frontend port is `5174`.
- Formal metadata belongs in MySQL, original files in MinIO, and rebuildable chunks/vectors in Elasticsearch.
- Never log secrets or complete document bodies.
- Current workspace root is not a Git repository, so this plan does not execute commit steps.

---

### Task 1: Backend skeleton, configuration, and migration

**Files:**
- Create: `rag-service/backend/pom.xml`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/RagServiceApplication.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/common/api/ApiResponse.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/common/api/GlobalExceptionHandler.java`
- Create: `rag-service/backend/src/main/resources/application.yml`
- Create: `rag-service/backend/src/main/resources/application-local.yml`
- Create: `rag-service/backend/src/main/resources/db/migration/V1__create_rag_tables.sql`
- Create: `rag-service/backend/src/test/resources/application-test.yml`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/database/FlywayMigrationTest.java`

**Interfaces:**
- Produces: Boot application context, `{code,message,data,traceId}` response envelope, and tables for admins, sessions, documents, versions, jobs, stages, chunks, query traces, and candidates.

- [ ] Write `FlywayMigrationTest` using Testcontainers MySQL and assert that every `rag_*` table exists.
- [ ] Run `mvnw.cmd -q -Dtest=FlywayMigrationTest test` and confirm it fails before the migration exists.
- [ ] Add the Boot 4.1/Spring AI 2.0 Maven build, configuration records, and full V1 migration.
- [ ] Run the migration test and the application context test; expect both to pass.

### Task 2: Technical-admin authentication

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AdminUser.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AdminSession.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AdminUserRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AdminSessionRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AuthService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AuthController.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AuthInterceptor.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/auth/AuthWebConfiguration.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/auth/AuthServiceTest.java`

**Interfaces:**
- Produces: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`; bearer tokens whose hashes and expiry are persisted.

- [ ] Write tests for valid login, invalid password, expired session, current user, and logout.
- [ ] Run `mvnw.cmd -q -Dtest=AuthServiceTest test`; expect missing auth types.
- [ ] Adapt password hashing and admin bootstrap behavior from `RagAiDemo`, replacing refresh-token/JWT complexity with one opaque server-side session token.
- [ ] Add the interceptor and verify unauthenticated `/api/admin/**` returns HTTP 401.
- [ ] Run `AuthServiceTest`; expect all cases to pass.

### Task 3: Object storage, file records, and upload

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/storage/ObjectStorageService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/storage/MinioObjectStorageService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/document/RagDocument.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/document/RagDocumentVersion.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentVersionRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/document/DocumentAdminController.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/document/DocumentServiceTest.java`

**Interfaces:**
- Produces: `POST /api/admin/documents`, `GET /api/admin/documents`, `GET /api/admin/documents/{id}`, SHA-256 metadata, immutable object keys, and a queued ingestion job identifier.

- [ ] Write tests proving supported extensions upload, hashes are stable, duplicate version objects are not overwritten, and unsupported files are rejected.
- [ ] Run the tests and confirm failure because the document service is absent.
- [ ] Migrate the MinIO storage abstraction from `RagAiDemo`, adapt it to Spring configuration properties, and implement document/version persistence.
- [ ] Add multipart upload and list/detail endpoints with size and type validation.
- [ ] Run `DocumentServiceTest`; expect all cases to pass.

### Task 4: Parsing and structure-aware chunking

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/parser/DocumentParser.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/parser/DocumentParserFactory.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/parser/DocxDocumentParser.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/parser/PdfDocumentParser.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/parser/ExcelDocumentParser.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/chunk/ParsedBlock.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/chunk/RagChunk.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/chunk/StructureAwareChunker.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/ingestion/parser/DocumentParserTest.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/ingestion/chunk/StructureAwareChunkerTest.java`

**Interfaces:**
- Produces: `List<ParsedBlock> parse(InputStream, String filename)` and `List<RagChunk> chunk(List<ParsedBlock>)`; each chunk carries section, page, sheet, row range, and ordinal metadata.

- [ ] Add small DOCX, PDF, and XLSX fixtures and write assertions for extracted text and source location.
- [ ] Write chunk tests for paragraph boundaries, target length, maximum length, overlap, stable ordering, and no blank chunks.
- [ ] Run both test classes; expect missing parser and chunker types.
- [ ] Migrate the `RagAiDemo` POI/PDFBox parsers and cleanup logic, excluding FAQ parsers.
- [ ] Adapt the existing structure-aware transformer to `ParsedBlock`, preserving source location and configured `targetChars=500`, `maxChars=800`, `overlapChars=80`.
- [ ] Run both tests; expect all cases to pass.

### Task 5: TEI and Elasticsearch adapters

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/embedding/EmbeddingService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/embedding/TeiEmbeddingClient.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RerankService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/TeiRerankClient.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/index/ChunkIndex.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/index/ElasticsearchChunkIndex.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/embedding/TeiEmbeddingClientTest.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/TeiRerankClientTest.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/index/ElasticsearchChunkIndexTest.java`

**Interfaces:**
- Produces: `embed(List<String>)`, `rerank(String,List<String>)`, `ensureIndex()`, `replaceVersionChunks(...)`, `keywordSearch(...)`, and `vectorSearch(...)`.

- [ ] Write MockWebServer tests for valid TEI responses, timeouts, malformed responses, and dimension validation.
- [ ] Write Elasticsearch adapter tests that assert generated mapping and request JSON contains the 1024-dimension dense vector, IK text fields, filters, and KNN query.
- [ ] Run the tests; expect missing adapter classes.
- [ ] Migrate the two TEI clients and Elasticsearch request logic from `RagAiDemo`, using Spring `RestClient` and configuration properties.
- [ ] Add bulk index replacement and health checks.
- [ ] Run the three test classes; expect all cases to pass.

### Task 6: Asynchronous ingestion and visible stages

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionJob.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionStage.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionJobRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionStageRepository.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionOrchestrator.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionWorker.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/IngestionAdminController.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/ingestion/IngestionOrchestratorTest.java`

**Interfaces:**
- Produces: queued-to-running-to-succeeded/failed state transitions and `GET /api/admin/ingestion/jobs/{id}` with ordered stage events.

- [ ] Write a test with fake storage, parser, embedding, and index adapters; assert stages are `STORE`, `PARSE`, `CLEAN`, `CHUNK`, `EMBED`, `INDEX`, `COMPLETE` and failure records the exact stage.
- [ ] Run the test; expect missing ingestion types.
- [ ] Adapt the asynchronous processor and stage-event idea from `RagAiDemo`, using a bounded Spring task executor and idempotent version processing.
- [ ] Persist chunks and stage timing, then expose status and retry endpoints.
- [ ] Run `IngestionOrchestratorTest`; expect success and failure cases to pass.

### Task 7: Hybrid retrieval, rerank fallback, and traces

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalRequest.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalCandidate.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RrfFusion.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/HybridRetrievalService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/trace/QueryTrace.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/trace/QueryCandidate.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/trace/QueryTraceService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/retrieval/RetrievalAdminController.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/RrfFusionTest.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/retrieval/HybridRetrievalServiceTest.java`

**Interfaces:**
- Produces: `POST /api/admin/retrieval/search`, `GET /api/admin/traces`, and `GET /api/admin/traces/{id}` with keyword rank/score, vector rank/score, RRF score/rank, rerank score/rank, final rank, elapsed time, and fallback reason.

- [ ] Write deterministic RRF tests using `score += 1/(60+rank)` and stable chunk-id tie breaking.
- [ ] Write service tests for full hybrid retrieval, rerank failure fallback, embedding failure keyword-only fallback, and trace persistence.
- [ ] Run both tests; expect missing retrieval types.
- [ ] Adapt the proven hybrid/RRF/rerank flow from `RagAiDemo`, keeping candidate-stage scores instead of flattening early.
- [ ] Persist the query trace and every candidate before returning results.
- [ ] Run both tests; expect all cases to pass.

### Task 8: Dashboard and dependency health

**Files:**
- Create: `rag-service/backend/src/main/java/com/agentto/rag/admin/DashboardController.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/admin/DashboardService.java`
- Create: `rag-service/backend/src/main/java/com/agentto/rag/admin/DependencyHealthService.java`
- Test: `rag-service/backend/src/test/java/com/agentto/rag/admin/DashboardServiceTest.java`

**Interfaces:**
- Produces: `GET /api/admin/dashboard` and `GET /api/admin/health/dependencies` for MySQL, MinIO, Elasticsearch, Embedding, and Rerank.

- [ ] Write tests for document/chunk/job metrics and mixed healthy/unhealthy dependencies.
- [ ] Run the test; expect missing dashboard types.
- [ ] Implement repository aggregates and bounded-time dependency probes.
- [ ] Run `DashboardServiceTest`; expect all cases to pass.

### Task 9: Vue administration shell and login

**Files:**
- Create: `rag-service/frontend/package.json`
- Create: `rag-service/frontend/src/main.ts`
- Create: `rag-service/frontend/src/App.vue`
- Create: `rag-service/frontend/src/router/index.ts`
- Create: `rag-service/frontend/src/stores/auth.ts`
- Create: `rag-service/frontend/src/api/http.ts`
- Create: `rag-service/frontend/src/api/rag.ts`
- Create: `rag-service/frontend/src/layouts/AdminLayout.vue`
- Create: `rag-service/frontend/src/views/LoginView.vue`
- Create: `rag-service/frontend/src/assets/main.css`
- Test: `rag-service/frontend/src/stores/auth.spec.ts`

**Interfaces:**
- Produces: protected routes, persisted bearer token, login/logout/current-user calls, shared sidebar/header, and typed RAG API client.

- [ ] Write a Vitest auth-store test for login, page reload restore, 401 clearing, and logout.
- [ ] Run `npm test -- --run`; expect failure before the frontend exists.
- [ ] Reuse the current Vue/Element Plus tooling and authentication interaction from `RagAiDemo`, removing public registration and ordinary-user navigation.
- [ ] Add the AgentTo-approved desktop visual style and protected layout.
- [ ] Run the auth test and `npm run type-check`; expect both to pass.

### Task 10: Workbench, documents, ingestion detail, and chunks

**Files:**
- Create: `rag-service/frontend/src/views/DashboardView.vue`
- Create: `rag-service/frontend/src/views/DocumentsView.vue`
- Create: `rag-service/frontend/src/views/DocumentDetailView.vue`
- Create: `rag-service/frontend/src/components/ingestion/IngestionTimeline.vue`
- Create: `rag-service/frontend/src/components/chunk/ChunkTable.vue`
- Create: `rag-service/frontend/src/components/document/UploadDialog.vue`
- Test: `rag-service/frontend/src/views/DocumentsView.spec.ts`

**Interfaces:**
- Consumes: dashboard, document, upload, detail, chunk, and ingestion endpoints from Tasks 3, 6, and 8.
- Produces: upload flow, status polling, filters, version data, stage timeline, chunk samples, and paginated full chunk table.

- [ ] Write component tests for upload progress, status polling stop conditions, timeline rendering, and chunk source location.
- [ ] Run the tests; expect missing views/components.
- [ ] Reuse the workbench and document-detail interaction from `RagAiDemo`, splitting the large existing views into the listed focused components.
- [ ] Connect real APIs and add empty, loading, failed, and retry states.
- [ ] Run the component tests and `npm run type-check`; expect all checks to pass.

### Task 11: Retrieval laboratory, candidate pool, traces, and health

**Files:**
- Create: `rag-service/frontend/src/views/RetrievalLabView.vue`
- Create: `rag-service/frontend/src/views/TraceListView.vue`
- Create: `rag-service/frontend/src/views/TraceDetailView.vue`
- Create: `rag-service/frontend/src/views/ServiceHealthView.vue`
- Create: `rag-service/frontend/src/components/retrieval/CandidateScoreTable.vue`
- Test: `rag-service/frontend/src/views/RetrievalLabView.spec.ts`

**Interfaces:**
- Consumes: retrieval, trace, and dependency-health endpoints from Tasks 7 and 8.
- Produces: adjustable retrieval parameters, stage result tabs, rank/score comparison, fallback banner, trace history, and dependency status cards.

- [ ] Write component tests for full hybrid results, rerank fallback, empty retrieval, and score/rank column rendering.
- [ ] Run the tests; expect missing retrieval views.
- [ ] Reuse the live RAG debug, candidate pool, query trace, and service-state interaction from `RagAiDemo`, dropping Q&A answer-generation metrics.
- [ ] Connect real APIs and format elapsed time, model/index versions, filters, score precision, and source location.
- [ ] Run the component tests, type check, and production build; expect all to pass.

### Task 12: Local environment, end-to-end acceptance, and handoff

**Files:**
- Create: `rag-service/backend/.env.local.example`
- Create: `rag-service/backend/start-local.ps1`
- Create: `rag-service/frontend/.env.example`
- Create: `rag-service/frontend/start-local.ps1`
- Create: `rag-service/README.md`
- Create: `rag-service/scripts/smoke-test.ps1`

**Interfaces:**
- Produces: repeatable local startup against the existing remote middleware and a smoke test covering login, upload, ingestion completion, chunks, hybrid retrieval, trace, and logout.

- [ ] Add configuration templates with names but no secret values, plus startup scripts for ports 10002 and 5174.
- [ ] Start the backend and frontend and verify both listening ports.
- [ ] Run `scripts/smoke-test.ps1` with a generated DOCX fixture; expect every endpoint assertion to pass and at least one retrieval result.
- [ ] Run all backend tests, frontend tests, type check, and production build.
- [ ] Confirm `git -C E:\RagAiDemo status --short` is identical to the pre-work snapshot and document the exact run commands in `rag-service/README.md`.

