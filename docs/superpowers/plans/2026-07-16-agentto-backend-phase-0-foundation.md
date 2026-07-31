# AgentTo Backend Phase 0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Do not begin the next task until the current task has passed its stated checks.

**Goal:** Correct the existing `agentto-backend` foundation so it runs on the approved Spring stack, loads development configuration from Nacos, connects to real MySQL/Redis/MinIO services, and has repeatable tests and startup checks without H2 or local middleware containers.

**Architecture:** Keep `agentto-backend` as a modular monolith. Repository files hold only stable defaults and Nacos bootstrap parameters; environment-specific addresses, accounts, passwords, model keys, and feature settings are loaded from Nacos. Pure domain tests run without infrastructure, while integration tests use isolated resources on the real development middleware.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring AI 2.0.0, Spring Cloud Alibaba 2025.1.0.0, Nacos Client 3.1.1, MySQL 8.x, Redis 7.x, MinIO Java SDK 8.5.17, Flyway, JUnit 5, AssertJ, Maven at `D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd`.

## Global Constraints

- Modify only `E:\AgentTo\backend`, the related backend documentation, and the AgentTo deployment description required to keep ports/configuration consistent.
- Do not add Agent business behavior, authentication, file-task APIs, RAG integration, or final workflow rules in this phase.
- Do not use H2, an in-memory database, or locally started Docker middleware.
- Do not put real secrets in `application*.yml`, tests, scripts, command examples, logs, or Git-tracked files.
- Local development starts only the backend process; MySQL, Redis, and MinIO use the existing development services.
- Local Nacos namespace is `agentto-local`, group is `AGENTTO`, and data ID is `agentto-backend.yml`.
- Production Nacos is not configured in this phase. Keep its address and namespace as startup inputs.
- Backend HTTP port is `18472`. RAG backend remains on `18473`; reserve `18474` for the future workflow service.
- The OpenAI-compatible Spring AI adapter is only a protocol adapter for DeepSeek in this project; no OpenAI provider key is configured.
- All new production code must include complete Chinese class and method comments explaining responsibility, boundary, and failure behavior.
- Current `E:\AgentTo\.git` is incomplete and cannot be used as a repository. Do not initialize, replace, or delete it automatically. Commit steps remain blocked until the user decides how Git should be repaired.

## Test Layers

| Layer | Naming | External dependency | Normal command |
|---|---|---|---|
| Unit test | `*Test` | None | `mvn test` |
| Integration test | `*IT` | 本机 Nacos + 阿里云 MySQL/Redis/MinIO 隔离测试资源 | `mvn verify -Pintegration` |
| Startup acceptance | PowerShell checks | Running backend and real middleware | `check-local.ps1` |

Integration resources must be isolated as follows:

- MySQL database: `agentto_test`;
- Redis prefix: `agentto:test:`; use a dedicated database number only when the server policy allows it;
- MinIO bucket: `agentto-test`;
- MinIO object prefix: a unique test run ID;
- Nacos data ID: `agentto-backend-test.yml`, group `AGENTTO`, namespace `agentto-local`.

Tests create only their own records and objects and remove them in teardown. Flyway migration tables remain because they belong to the isolated test database.

---

### Task 1: Freeze the current baseline and separate test layers

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/test/java/com/agentto/platform/AgentToApplicationTest.java`
- Modify: `backend/src/test/java/com/agentto/platform/database/FlywayMigrationTest.java`
- Delete: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/test/resources/application-integration-test.yml`
- Create: `backend/src/test/java/com/agentto/platform/support/IntegrationTest.java`

**Expected interfaces:**
- `@IntegrationTest` is a local composed annotation that activates `integration-test` and marks tests requiring real services.
- Surefire executes `*Test`; Failsafe executes `*IT` only through the `integration` Maven profile.

- [x] Run the current test suite with `D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd test` and save the result in the implementation notes. Do not treat existing H2 success as final acceptance.
- [x] Add Maven Surefire/Failsafe separation and rename the application-context and Flyway tests to `AgentToApplicationIT` and `FlywayMigrationIT`.
- [x] Delete the H2 test profile and make `application-integration-test.yml` import the isolated Nacos test data ID.
- [x] Add `@IntegrationTest` with `@SpringBootTest`, `@ActiveProfiles("integration-test")`, and clear documentation that it uses real development resources.
- [x] Add one pure unit test proving `InMemoryWorkflowGateway` still works without starting Spring.
- [x] Run `mvn test`; expect only unit tests to run and pass without connecting to middleware.
- [x] Run `mvn verify -Pintegration` before the real configuration exists; expect a clear failure identifying the missing Nacos configuration rather than silently falling back to H2.

### Task 2: Align the Spring dependency baseline

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/test/java/com/agentto/platform/build/DependencyBaselineTest.java`

**Dependency changes:**
- Spring Boot parent `4.0.6`;
- Spring AI BOM `2.0.0`;
- Spring Cloud Alibaba BOM `2025.1.0.0`;
- `spring-cloud-starter-alibaba-nacos-config`;
- `spring-boot-starter-data-redis`;
- `io.minio:minio:8.5.17`;
- retain MVC, validation, JPA, Actuator, Flyway, MySQL, and test starters;
- remove H2 completely.

- [x] Write `DependencyBaselineTest` that reads package implementation versions for Boot and Spring AI and asserts the expected major/minor baseline. Keep exact transitive-version checks in Maven Enforcer/dependency management rather than brittle string assertions.
- [x] Run the test and confirm it fails against the existing Boot 4.1 build.
- [x] Add Spring Cloud Alibaba dependency management and the required starters.
- [x] Remove the H2 dependency.
- [x] Add Maven Enforcer rules for Java 21 and dependency convergence where compatible; document any explicitly excluded known convergence conflict.
- [x] Run `mvn dependency:tree` and verify Nacos Client resolves to `3.1.1` and there is no H2 artifact.
- [x] Run `mvn test`; expect the dependency baseline and existing unit tests to pass.

### Task 3: Replace direct environment configuration with Nacos import

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Delete: `backend/src/main/resources/application-local.yml`
- Modify: `backend/src/main/resources/application-dev.yml`
- Modify: `backend/src/main/resources/application-prod.yml`
- Create: `backend/config/nacos/agentto-backend.example.yml`
- Create: `backend/config/nacos/agentto-backend-test.example.yml`
- Create: `backend/src/main/java/com/agentto/platform/config/AgentToInfrastructureProperties.java`
- Test: `backend/src/test/java/com/agentto/platform/config/AgentToInfrastructurePropertiesTest.java`

**Repository configuration shape:**

```yaml
server:
  port: 18472

spring:
  application:
    name: agentto-backend
  config:
    import:
      - "nacos:${AGENTTO_NACOS_DATA_ID:agentto-backend.yml}?group=${AGENTTO_NACOS_GROUP:AGENTTO}&refreshEnabled=true"
  cloud:
    nacos:
      server-addr: ${AGENTTO_NACOS_SERVER:127.0.0.1:8848}
      username: ${AGENTTO_NACOS_USERNAME:}
      password: ${AGENTTO_NACOS_PASSWORD:}
      config:
        namespace: ${AGENTTO_NACOS_NAMESPACE:agentto-local}
```

The exact property shape must be checked against the resolved Spring Cloud Alibaba 2025.1.0.0 metadata during implementation. Do not add `bootstrap.yml`.

- [x] Write unit tests for validation of storage bucket names, Redis prefixes, service URLs, and required model provider references.
- [x] Move datasource, Redis, MinIO, DeepSeek, RAG URL, workflow adapter, and feature settings into the Nacos example file using obvious placeholders.
- [x] Keep only stable application name, port, actuator defaults, graceful shutdown, and Nacos bootstrap inputs in repository configuration.
- [x] Configure DeepSeek through Spring AI's OpenAI-compatible protocol properties without adding an OpenAI API key or OpenAI model name.
- [x] Set the default workflow adapter name consistently to `in-memory` in all environments.
- [x] Enable Nacos health information explicitly because the selected Spring Cloud Alibaba line does not expose it by default.
- [x] Run `mvn test`; expect property validation tests to pass.
- [x] Manually review `rg -n "password|api-key|secret|sk-" backend` and confirm only placeholders/property names appear.

### Task 4: Prove Flyway and JPA against real MySQL

**Files:**
- Modify: `backend/src/test/java/com/agentto/platform/database/FlywayMigrationIT.java`
- Create: `backend/src/test/java/com/agentto/platform/database/DatabaseIsolationIT.java`
- Review: `backend/src/main/resources/db/migration/V1__create_minimum_task_tables.sql`

**Expected behavior:**
- Tests use the `agentto_test` database supplied through Nacos.
- Flyway is the only schema creator; Hibernate uses `ddl-auto=validate`.
- The application never creates or alters tables through JPA at startup.

- [x] Rewrite `FlywayMigrationIT` to query MySQL `information_schema.tables` for the current database and assert all five baseline tables exist.
- [x] Add assertions for primary keys, required version/hash fields, indexes, and charset/collation where applicable.
- [x] Add `DatabaseIsolationIT` that fails if the configured database name is not exactly `agentto_test`.
- [x] Review V1 SQL for MySQL syntax and correct only defects required for repeatable migration; do not add future business tables in this phase.
- [x] Publish `agentto-backend-test.yml` to local Nacos using values supplied by the user or existing development configuration. Never copy secrets back into the repository.
- [x] Run `mvn verify -Pintegration -Dit.test=DatabaseIsolationIT,FlywayMigrationIT` and expect both tests to pass.
- [x] Run the same command again and confirm Flyway reports no pending migration and the schema remains valid.

### Task 5: Add real Redis and MinIO infrastructure checks

**Files:**
- Create: `backend/src/main/java/com/agentto/platform/infrastructure/storage/ObjectStorageGateway.java`
- Create: `backend/src/main/java/com/agentto/platform/infrastructure/storage/MinioObjectStorageGateway.java`
- Create: `backend/src/main/java/com/agentto/platform/infrastructure/storage/MinioConfiguration.java`
- Create: `backend/src/main/java/com/agentto/platform/infrastructure/storage/MinioHealthIndicator.java`
- Test: `backend/src/test/java/com/agentto/platform/infrastructure/redis/RedisConnectivityIT.java`
- Test: `backend/src/test/java/com/agentto/platform/infrastructure/storage/MinioObjectStorageGatewayIT.java`

**Minimal storage port:**

```java
public interface ObjectStorageGateway {
    void put(String objectKey, InputStream content, long size, String contentType);
    InputStream get(String objectKey);
    boolean exists(String objectKey);
    void delete(String objectKey);
}
```

This interface is infrastructure foundation only. It does not introduce file-task business behavior.

- [x] Write `RedisConnectivityIT` that checks the required `agentto:test:` prefix, writes a random key with a short TTL, reads it, and deletes it.
- [x] Write `MinioObjectStorageGatewayIT` that writes a small random object under a unique prefix, reads and hashes it, verifies existence, and removes it in teardown.
- [x] Add a safety assertion that integration tests can only use bucket `agentto-test`.
- [x] Implement the minimal storage gateway with Chinese comments and no business-specific naming.
- [x] Add `MinioHealthIndicator` that checks bucket access without listing or logging file names.
- [x] Use Spring Boot Redis auto-configuration and Actuator health rather than creating a second Redis client stack.
- [x] Run `mvn verify -Pintegration -Dit.test=RedisConnectivityIT,MinioObjectStorageGatewayIT`; expect both tests to pass and leave no test objects or Redis keys.

### Task 6: Make health status useful and safe

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/agentto/platform/health/AgentToReadinessHealthIndicator.java`
- Test: `backend/src/test/java/com/agentto/platform/health/AgentToReadinessHealthIndicatorTest.java`
- Test: `backend/src/test/java/com/agentto/platform/health/ActuatorHealthIT.java`

**Health groups:**
- Liveness: JVM/application process only;
- Readiness: MySQL, Redis, MinIO, and Nacos configuration availability;
- DeepSeek and RAG are not startup blockers in this phase and must not make readiness `DOWN`.

- [x] Write unit tests for readiness aggregation, including one dependency down and optional external AI service unavailable.
- [x] Configure `/actuator/health/liveness` and `/actuator/health/readiness` groups.
- [x] Keep detailed health output hidden by default; local development may expose details only through an explicit Nacos setting.
- [x] Do not return addresses, usernames, object names, passwords, API keys, or raw exception messages from health endpoints.
- [x] Add `ActuatorHealthIT` and verify MySQL, Redis, MinIO, and Nacos appear in readiness.
- [x] Run `mvn verify -Pintegration -Dit.test=ActuatorHealthIT` and expect HTTP 200 with overall `UP` in a correctly configured environment.

### Task 7: Standardize local start, status, stop, and cleanup

**Files:**
- Modify: `backend/start-local.ps1`
- Create: `backend/check-local.ps1`
- Create: `backend/stop-local.ps1`
- Create: `backend/clear-dev-data.ps1`
- Create: `backend/.env.local.example`
- Modify: `backend/README.md`
- Modify: `deploy/docker-compose.yml`

**Startup behavior:**
- Use `D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd` by default;
- require only Nacos bootstrap variables in `.env.local`;
- run preflight checks before starting Spring;
- refuse to start if port `18472` is already occupied by an unrelated process;
- do not start MySQL, Redis, MinIO, Elasticsearch, TEI, RAG, or frontend;
- write PID and startup log paths under an ignored runtime directory;
- avoid opening visible PowerShell windows.

- [x] Update `start-local.ps1` to validate Java 21, Maven, Nacos TCP connectivity, required environment variables, and port availability before one backend start.
- [x] Add `check-local.ps1` to report process, port, liveness, readiness, and the names—not values—of required Nacos configuration items.
- [x] Add `stop-local.ps1` that stops only the PID recorded for this backend and verifies the process command line before terminating it.
- [x] Add `clear-dev-data.ps1` with explicit environment guardrails. It must reject production namespace/database/bucket values, show the target resources, require an execution flag, and clean only AgentTo development/test data.
- [x] Change Compose backend mappings and health checks from 8080 to 18472, but document that Compose is for later integrated deployment and is not used by the local development workflow.
- [x] Update README to remove Maven Wrapper/H2/local middleware instructions and document the real local startup path.
- [x] Run a PowerShell syntax check for all four scripts.
- [x] Run `check-local.ps1` before starting; expect a clear `STOPPED` result rather than an exception.

### Task 8: Final foundation verification

**Files:**
- Create: `backend/docs/phase-0-verification.md`
- Review: all files changed by Tasks 1–7

- [x] Ensure the backend is stopped before verification and no stale PID is recorded.
- [x] Run `D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd clean test`; expect all pure unit tests to pass without contacting middleware.
- [x] Run `D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd clean verify -Pintegration`; expect all real middleware integration tests to pass.
- [x] Run `start-local.ps1` once. Do not repeatedly restart it between individual checks.
- [x] Run `check-local.ps1`; expect port `18472`, liveness `UP`, readiness `UP`, and real MySQL/Redis/MinIO/Nacos status.
- [x] Query the five baseline tables and confirm the application is using the intended development database, not `agentto_test`.
- [x] Search the repository for H2 references, port 8080 backend references, plaintext keys, and stale Boot 4.1 statements in backend documentation. Resolve every relevant hit.
- [x] Record exact commands, timestamps, test counts, dependency versions, Nacos namespace/data ID names, and health results in `backend/docs/phase-0-verification.md`; do not record secret values.
- [x] Leave the backend running only when handing it to the user for manual confirmation. After the user confirms, stop it with `stop-local.ps1`.
- [ ] Do not claim Phase 0 complete until both automated checks and the user's manual confirmation pass.

## Phase 0 Exit Review

Before proceeding to account and file-task development, confirm all answers are “yes”:

- [x] Is the approved Spring Boot 4.0.x / Spring AI 2.0 / SCA 2025.1 stack actually resolved by Maven?
- [x] Has H2 been completely removed?
- [x] Can unit tests run without middleware and integration tests use only isolated real resources?
- [x] Does local startup depend only on the backend process plus already-running external services?
- [x] Is Nacos the source of environment-specific configuration and secrets?
- [x] Are MySQL, Redis, MinIO, and Nacos represented in readiness checks?
- [x] Can a failed preflight be understood without digging through a long Spring startup log?
- [x] Can development/test data be cleared without risking unrelated or production data?
- [x] Are all new production classes and non-obvious decisions documented in Chinese?
- [ ] Has the user manually confirmed the running backend once?

## Deferred Work

The following work is intentionally deferred to later plans:

- user login, organization, roles, and permission model;
- file upload and version business APIs;
- RAG service-to-service contract;
- DeepSeek request execution and model switching;
- Prompt/version/Agent management;
- ContextPackage, Agent stages, SSE, and run visualizations;
- temporary workflow V0 and HopFresh BPM migration;
- message queue, Tool/MCP, OCR, and enterprise WeChat integration.

## Official References

- Spring AI 2.0 requirements: <https://docs.spring.io/spring-ai/reference/getting-started.html>
- Spring Cloud Alibaba 2025.1.0.0 release: <https://github.com/alibaba/spring-cloud-alibaba/releases>
- Nacos configuration import: <https://sca.aliyun.com/docs/2025.x/user-guide/nacos/quick-start/>
- Nacos advanced configuration and health behavior: <https://sca.aliyun.com/docs/2025.x/user-guide/nacos/advanced-guide/>
