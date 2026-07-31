# RAG Observability and DOCX Chunking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve the RAG administrator experience, expose deterministic ingestion progress, and replace one-paragraph-per-chunk DOCX processing with heading-aware paragraph composition.

**Architecture:** Keep the existing Spring Boot APIs and Vue 3 application boundaries. Add small, testable frontend presentation helpers, preserve the existing ingestion polling API, and implement DOCX structure extraction plus deterministic paragraph aggregation in the backend. PDF and Excel behavior remain unchanged.

**Tech Stack:** Java 21, Spring Boot 4.1, Apache POI 5.4, JUnit 5, Vue 3, TypeScript 6, Element Plus, Vitest.

## Global Constraints

- Development administrator credentials are `admin / admin123`.
- Backend remains on port `18473`; frontend remains on port `5174`.
- Retrieval defaults remain keyword 20, vector 20, fusion 30, rerank 15, final 8.
- DOCX target length remains 500 characters, maximum 800, overlap budget 80.
- PDF and Excel parsing behavior must not change.
- No LLM, embedding model, or semantic similarity is used for chunk boundary decisions.

---

### Task 1: Development administrator credentials

**Files:**
- Modify: `rag-service/backend/src/main/resources/application.yml`
- Modify: `rag-service/frontend/src/views/LoginView.vue`
- Modify: `rag-service/README.md`
- Modify: `rag-service/cleanup-test-data.ps1`

**Interfaces:**
- Consumes: existing `RAG_ADMIN_USERNAME` and `RAG_ADMIN_PASSWORD` environment overrides.
- Produces: local defaults `admin` and `admin123` without changing the authentication API.

- [ ] Change backend fallback username and password to `admin` and `admin123`.
- [ ] Change the prefilled login username to `admin`; keep the password field empty.
- [ ] Update developer-facing documentation and cleanup script default username.
- [ ] Restart the backend and verify `POST /api/auth/login` succeeds with the new credentials.

### Task 2: Retrieval presentation helpers

**Files:**
- Create: `rag-service/frontend/src/retrieval/presentation.ts`
- Create: `rag-service/frontend/src/retrieval/presentation.test.ts`
- Modify: `rag-service/frontend/src/views/TracesView.vue`
- Modify: `rag-service/frontend/src/views/RetrievalView.vue`
- Modify: `rag-service/frontend/src/styles.css`

**Interfaces:**
- Consumes: `TraceCandidate`, `RetrievalCandidate`, `RetrievalTimings`.
- Produces: `finalTraceCandidates(candidates)`, `rerankLabel(candidate)`, Chinese parameter descriptors, and source-location text.

- [ ] Write failing Vitest cases proving final Trace candidates exclude rows without `finalRank`, sorting uses final rank, and candidates without a rerank rank are labelled `未进入精排`.
- [ ] Run `npm test -- presentation.test.ts` and confirm the new tests fail because helpers do not exist.
- [ ] Implement the minimal presentation helpers and rerun the focused tests.
- [ ] Change Trace details to default to final results and provide an “全部候选” tab.
- [ ] Collapse retrieval limits under “高级检索参数”, use Chinese names, and retain the same numeric values.
- [ ] Keep retrieval results on the current page and make the hit text and source location visually primary.
- [ ] Run the complete frontend test and build commands.

### Task 3: Deterministic ingestion progress model

**Files:**
- Create: `rag-service/frontend/src/ingestion/progress.ts`
- Create: `rag-service/frontend/src/ingestion/progress.test.ts`
- Modify: `rag-service/frontend/src/views/DocumentsView.vue`
- Modify: `rag-service/frontend/src/styles.css`

**Interfaces:**
- Consumes: `IngestionJob` returned by `GET /api/admin/ingestion/jobs/{jobId}`.
- Produces: seven ordered progress nodes with `pending`, `active`, `success`, or `failed` state.

- [ ] Write failing Vitest cases for queued, active, succeeded, and failed jobs.
- [ ] Run the focused test and confirm failure because the progress mapper does not exist.
- [ ] Implement the seven-stage mapper for `STORE`, `PARSE`, `CLEAN`, `CHUNK`, `EMBED`, `INDEX`, and `COMPLETE`.
- [ ] Replace the completed-only timeline with a fixed linear progress display driven by the existing 1.5-second polling loop.
- [ ] Display stage detail, elapsed time, item count, and terminal error without introducing a new backend endpoint.
- [ ] Run focused and complete frontend verification.

### Task 4: DOCX structure extraction

**Files:**
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/parser/DocxDocumentParser.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/ingestion/parser/DocumentParserTest.java`

**Interfaces:**
- Consumes: a DOCX body containing paragraphs and tables.
- Produces: `ParsedBlock` values in original body order with heading path, paragraph range, table number, and row range metadata.

- [ ] Write a failing parser test with a paragraph, table, and following paragraph proving body order is preserved.
- [ ] Write a failing parser test proving heading levels build a stable section path.
- [ ] Run the focused Maven test and confirm both failures match the missing behavior.
- [ ] Iterate Apache POI body elements in source order and maintain a heading-level stack.
- [ ] Emit paragraph and table-row blocks with source metadata.
- [ ] Rerun parser tests and existing backend tests.

### Task 5: Heading-aware paragraph composition

**Files:**
- Modify: `rag-service/backend/src/main/java/com/agentto/rag/ingestion/chunk/StructureAwareChunker.java`
- Modify: `rag-service/backend/src/test/java/com/agentto/rag/ingestion/chunk/StructureAwareChunkerTest.java`

**Interfaces:**
- Consumes: ordered `ParsedBlock` values from all parsers.
- Produces: `RagChunk` values; only DOCX paragraph blocks are composed, while PDF pages, Excel rows, and table rows keep their existing boundaries.

- [ ] Write a failing test proving short DOCX paragraphs in the same section combine near the target size.
- [ ] Write a failing test proving paragraphs never combine across section paths.
- [ ] Write a failing test proving an oversized paragraph splits at sentence and secondary punctuation boundaries and never exceeds the maximum.
- [ ] Write regression tests proving PDF page and Excel row blocks do not combine.
- [ ] Run the focused test and verify the new cases fail for the intended reasons.
- [ ] Implement paragraph composition and hierarchical large-paragraph splitting with complete-sentence overlap.
- [ ] Run focused and complete backend tests.

### Task 6: End-to-end verification

**Files:**
- Verify only; no new production files required.

**Interfaces:**
- Consumes: packaged backend and built frontend.
- Produces: running development system verified through real HTTP requests and browser interaction.

- [ ] Run `D:\DevTools\Maven\bin\mvn.cmd test` in `rag-service/backend`.
- [ ] Run `npm test` and `npm run build` in `rag-service/frontend`.
- [ ] Restart the backend and frontend with the project start scripts.
- [ ] Verify health, login, dashboard, document list, retrieval search, trace list/detail, and ingestion job endpoints return non-500 responses.
- [ ] Verify the login page uses `admin`, Trace defaults to final results, advanced retrieval controls are collapsed, and the ingestion drawer shows seven nodes.
