# AgentTo 阶段 1A：账号、组织与访问控制实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不依赖正式工作流的前提下，为 AgentTo 建立可真实登录、可维护账号和部门、可校验固定系统角色、可审计的后端身份基础。

**Architecture:** 账号、部门、角色关系是正式业务事实，持久化在 MySQL；密码只保存 BCrypt 哈希。登录成功后生成高熵不透明 Token，Redis 只保存带过期时间的会话快照，数据库不保存登录会话。Spring Security 统一校验 Bearer Token 和接口权限，技术管理员接口使用方法级授权；后续企微登录只需新增身份提供方和外部身份映射，不修改业务角色模型。

**Tech Stack:** Java 21、Spring Boot 4.0.6、Spring Security、Spring Data JPA、Spring Data Redis、MySQL 8、Flyway、JUnit 5、AssertJ、MockMvc

## Global Constraints

- Spring Boot 固定为 `4.0.6`，Spring AI 固定为 `2.0.0`，本阶段不顺带升级版本。
- 本地与集成测试直接连接 Nacos 中配置的阿里云 MySQL、Redis 和 MinIO，不使用 H2。
- 正式账号、部门、角色和审计记录保存在 MySQL；Redis 只保存可失效、可重建的登录会话。
- 真实密码、数据库密码和密钥只进入 Nacos，不写入仓库、接口响应或日志。
- 固定系统角色第一批为 `TECH_ADMIN`、`EXECUTIVE`、`SECRETARY`、`BOSS`；A、C 等任务角色不放进本阶段的固定角色表。
- 技术管理员可以维护技术数据，但不能因此获得后续业务审批操作权限。
- 所有新增类和关键业务判断必须有完整、自然的中文注释，说明用途、边界和失败处理。
- 新功能严格执行测试先行：先写失败测试并确认失败原因，再写最小实现。
- 当前 `E:\AgentTo\.git` 不是有效 Git 仓库，本计划不擅自初始化或提交；每个任务仍保留独立测试检查点，后续建立正式仓库后按任务粒度提交。

---

## 文件结构

本阶段新增或修改的文件按职责划分如下：

```text
backend/
├─ pom.xml
├─ config/nacos/
│  ├─ agentto-backend.example.yml
│  └─ agentto-backend-test.example.yml
├─ src/main/java/com/agentto/platform/
│  ├─ common/web/
│  │  ├─ ApiError.java
│  │  ├─ BusinessException.java
│  │  └─ GlobalExceptionHandler.java
│  ├─ identity/
│  │  ├─ application/
│  │  │  ├─ IdentityAdminService.java
│  │  │  ├─ IdentityQueryService.java
│  │  │  └─ command/*.java
│  │  ├─ domain/
│  │  │  ├─ DepartmentEntity.java
│  │  │  ├─ UserAccountEntity.java
│  │  │  ├─ SystemRoleEntity.java
│  │  │  ├─ UserRoleEntity.java
│  │  │  └─ repository/*.java
│  │  └─ web/
│  │     ├─ DepartmentAdminController.java
│  │     ├─ UserAdminController.java
│  │     └─ dto/*.java
│  └─ security/
│     ├─ application/
│     │  ├─ AuthenticationService.java
│     │  ├─ AuthSessionStore.java
│     │  └─ CurrentUser.java
│     ├─ infrastructure/
│     │  ├─ RedisAuthSessionStore.java
│     │  └─ BCryptPasswordHasher.java
│     ├─ web/
│     │  ├─ BearerTokenAuthenticationFilter.java
│     │  ├─ AuthController.java
│     │  └─ dto/*.java
│     ├─ BootstrapAdminInitializer.java
│     ├─ SecurityConfiguration.java
│     └─ SecurityProperties.java
├─ src/main/resources/db/migration/
│  └─ V2__create_identity_and_access_tables.sql
└─ src/test/java/com/agentto/platform/
   ├─ database/FlywayMigrationIT.java
   ├─ identity/*.java
   ├─ security/*.java
   └─ maintenance/*.java
```

`identity` 只负责账号、部门和固定系统角色；`security` 只负责凭证校验、登录会话和当前用户。两者通过应用服务接口协作，不让 Controller 直接拼装 JPA 查询或 Redis key。

---

### Task 1: 建立身份表结构和安全依赖

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/db/migration/V2__create_identity_and_access_tables.sql`
- Modify: `backend/src/test/java/com/agentto/platform/database/FlywayMigrationIT.java`
- Modify: `backend/src/main/java/com/agentto/platform/maintenance/DevelopmentDataCleanupService.java`
- Modify: `backend/src/test/java/com/agentto/platform/maintenance/DevelopmentDataCleanupGuardTest.java`

**Interfaces:**
- Consumes: 阶段 0 已存在的 MySQL、Flyway 和开发数据清理边界。
- Produces: `at_department`、`at_user_account`、`at_system_role`、`at_user_role` 四张表，以及 Spring Security 密码和接口授权依赖。

- [x] **Step 1: 扩展 Flyway 集成测试并确认失败**

在 `FlywayMigrationIT` 中把必需表增加为：

```java
private static final List<String> REQUIRED_TABLES = List.of(
        "at_task",
        "at_review_round",
        "at_task_participant",
        "at_workflow_mapping",
        "at_audit_event",
        "at_department",
        "at_user_account",
        "at_system_role",
        "at_user_role");
```

新增索引断言：

```java
assertThat(indexNames).contains(
        "uk_at_department_code",
        "idx_at_department_parent",
        "uk_at_user_account_username",
        "uk_at_user_account_external_user_id",
        "idx_at_user_account_department",
        "primary");
```

运行：

```powershell
$env:JAVA_HOME='D:\DevTools\Java\jdk-21.0.11+10'
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Pintegration `
  -Dit.test=FlywayMigrationIT verify
```

Expected: FAIL，提示 `at_department` 等新表不存在。

- [x] **Step 2: 增加 Spring Security 依赖**

在 `pom.xml` 的依赖区增加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

不引入 JWT 库。本阶段使用随机不透明 Token，避免在 Token 中复制可能变化的账号和角色事实。

- [x] **Step 3: 编写 V2 身份迁移**

创建 `V2__create_identity_and_access_tables.sql`：

```sql
create table at_department (
    department_id varchar(36) not null,
    department_code varchar(64) not null,
    department_name varchar(128) not null,
    parent_department_id varchar(36),
    external_org_id varchar(128),
    enabled boolean not null default true,
    version bigint not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (department_id),
    constraint uk_at_department_code unique (department_code),
    constraint uk_at_department_external_org_id unique (external_org_id),
    constraint fk_at_department_parent foreign key (parent_department_id)
        references at_department (department_id)
);

create index idx_at_department_parent
    on at_department (parent_department_id, enabled);

create table at_user_account (
    user_id varchar(36) not null,
    username varchar(64) not null,
    display_name varchar(128) not null,
    password_hash varchar(100) not null,
    department_id varchar(36),
    external_user_id varchar(128),
    enabled boolean not null default true,
    password_changed_at timestamp(6) not null,
    version bigint not null default 0,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (user_id),
    constraint uk_at_user_account_username unique (username),
    constraint uk_at_user_account_external_user_id unique (external_user_id),
    constraint fk_at_user_account_department foreign key (department_id)
        references at_department (department_id)
);

create index idx_at_user_account_department
    on at_user_account (department_id, enabled);

create table at_system_role (
    role_code varchar(32) not null,
    role_name varchar(64) not null,
    role_description varchar(255),
    created_at timestamp(6) not null,
    primary key (role_code)
);

create table at_user_role (
    user_id varchar(36) not null,
    role_code varchar(32) not null,
    created_at timestamp(6) not null,
    primary key (user_id, role_code),
    constraint fk_at_user_role_user foreign key (user_id)
        references at_user_account (user_id),
    constraint fk_at_user_role_role foreign key (role_code)
        references at_system_role (role_code)
);

insert into at_system_role (role_code, role_name, role_description, created_at) values
    ('TECH_ADMIN', '技术管理员', '维护平台技术配置、账号和组织数据，不代替业务角色审批', current_timestamp(6)),
    ('EXECUTIVE', '高管', '可在具体文件任务中担任主导人或参与审查', current_timestamp(6)),
    ('SECRETARY', '秘书组', '负责秘书组阶段的文件复核', current_timestamp(6)),
    ('BOSS', '老板', '负责最终审批', current_timestamp(6));
```

外部组织和用户标识允许为空；MySQL 唯一索引允许存在多行 `NULL`，便于企微接入前使用平台自有账号。

- [x] **Step 4: 把新表加入开发数据清理顺序**

更新清理服务，使删除顺序遵守外键：

```java
private static final List<String> BUSINESS_TABLES_IN_DELETE_ORDER = List.of(
        "at_user_role",
        "at_audit_event",
        "at_workflow_mapping",
        "at_task_participant",
        "at_review_round",
        "at_task",
        "at_user_account",
        "at_department");
```

`at_system_role` 是代码定义的固定字典，不在普通开发数据清理中删除。

- [x] **Step 5: 运行迁移和单元测试**

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' test
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Pintegration `
  -Dit.test=FlywayMigrationIT verify
```

Expected: 单元测试全部 PASS；Flyway 集成测试 PASS，并确认 9 张基线及身份表存在。

---

### Task 2: 建立账号、部门和角色领域边界

**Files:**
- Create: `backend/src/main/java/com/agentto/platform/common/web/ApiError.java`
- Create: `backend/src/main/java/com/agentto/platform/common/web/BusinessException.java`
- Create: `backend/src/main/java/com/agentto/platform/common/web/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/agentto/platform/identity/domain/*.java`
- Create: `backend/src/main/java/com/agentto/platform/identity/domain/repository/*.java`
- Create: `backend/src/main/java/com/agentto/platform/identity/application/IdentityAdminService.java`
- Create: `backend/src/main/java/com/agentto/platform/identity/application/IdentityQueryService.java`
- Create: `backend/src/main/java/com/agentto/platform/identity/application/command/*.java`
- Test: `backend/src/test/java/com/agentto/platform/identity/IdentityAdminServiceTest.java`

**Interfaces:**
- Consumes: Task 1 的身份表。
- Produces:
  - `IdentityAdminService#createDepartment(CreateDepartmentCommand)`
  - `IdentityAdminService#createUser(CreateUserCommand)`
  - `IdentityAdminService#replaceRoles(String, Set<SystemRole>)`
  - `IdentityAdminService#setUserEnabled(String, boolean)`
  - `IdentityQueryService#findByUsername(String)`
  - `IdentityQueryService#getRequiredUser(String)`

- [ ] **Step 1: 先写账号规则单元测试**

测试至少覆盖：

```java
@Test
void normalizesUsernameAndRejectsDuplicateAccount() {
    CreateUserCommand command = new CreateUserCommand(
            "  Admin  ", "技术管理员", "plain-password", null, null,
            Set.of(SystemRole.TECH_ADMIN));

    UserAccount created = service.createUser(command);

    assertThat(created.username()).isEqualTo("admin");
    assertThatThrownBy(() -> service.createUser(command))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo("USERNAME_ALREADY_EXISTS");
}

@Test
void disablingUserMakesAccountUnavailableForAuthentication() {
    UserAccount user = createEnabledUser();

    service.setUserEnabled(user.userId(), false);

    assertThat(queryService.findEnabledByUsername(user.username())).isEmpty();
}
```

使用内存仓储测试纯规则，不启动 Spring，不连接数据库。

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' `
  -Dtest=IdentityAdminServiceTest test
```

Expected: FAIL，相关类型和服务尚不存在。

- [ ] **Step 2: 实现固定角色和领域对象**

固定角色只表达平台身份，不表达 A、C 等任务角色：

```java
public enum SystemRole {
    TECH_ADMIN,
    EXECUTIVE,
    SECRETARY,
    BOSS
}
```

`UserAccountEntity` 必须包含：`userId`、规范化 `username`、`displayName`、`passwordHash`、可空 `departmentId`、可空 `externalUserId`、`enabled`、`passwordChangedAt`、`version`、创建和更新时间。

账号规范化规则：

```java
static String normalizeUsername(String username) {
    if (username == null || username.isBlank()) {
        throw new BusinessException("USERNAME_REQUIRED", "登录名不能为空");
    }
    String normalized = username.trim().toLowerCase(Locale.ROOT);
    if (!normalized.matches("[a-z0-9._-]{3,64}")) {
        throw new BusinessException(
                "USERNAME_INVALID",
                "登录名只能包含小写字母、数字、点、下划线和连字符，长度为 3 到 64 位");
    }
    return normalized;
}
```

- [ ] **Step 3: 实现应用服务并保持事务边界**

`IdentityAdminService` 使用 `@Transactional` 完成单次账号或组织变更。创建账号时：

1. 规范化并检查用户名；
2. 校验部门存在且启用；
3. 通过 `PasswordHasher` 生成哈希；
4. 保存账号；
5. 保存固定系统角色；
6. 返回不包含密码哈希的领域结果。

定义密码边界：

```java
public interface PasswordHasher {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
```

密码规则第一版固定为 8 到 72 个字符，不能只包含空白。不要在错误信息或日志中打印原密码。

- [ ] **Step 4: 建立统一业务错误结构**

接口错误统一返回：

```java
public record ApiError(
        String code,
        String message,
        String traceId,
        List<String> details) {
}
```

`GlobalExceptionHandler` 至少处理：

- `BusinessException` → 对应 400、404 或 409；
- `MethodArgumentNotValidException` → 400，返回字段错误；
- 其他异常 → 500，只返回 `INTERNAL_ERROR`，堆栈仅写服务日志。

- [ ] **Step 5: 跑纯领域测试**

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' `
  -Dtest=IdentityAdminServiceTest test
```

Expected: PASS，测试不访问 MySQL、Redis、MinIO 或模型 API。

---

### Task 3: 实现 Redis 不透明会话和登录接口

**Files:**
- Create: `backend/src/main/java/com/agentto/platform/security/SecurityProperties.java`
- Create: `backend/src/main/java/com/agentto/platform/security/SecurityConfiguration.java`
- Create: `backend/src/main/java/com/agentto/platform/security/BootstrapAdminInitializer.java`
- Create: `backend/src/main/java/com/agentto/platform/security/application/*.java`
- Create: `backend/src/main/java/com/agentto/platform/security/infrastructure/*.java`
- Create: `backend/src/main/java/com/agentto/platform/security/web/*.java`
- Modify: `backend/config/nacos/agentto-backend.example.yml`
- Modify: `backend/config/nacos/agentto-backend-test.example.yml`
- Test: `backend/src/test/java/com/agentto/platform/security/AuthenticationServiceTest.java`
- Test: `backend/src/test/java/com/agentto/platform/security/AuthFlowIT.java`

**Interfaces:**
- Consumes: Task 2 的账号查询和密码哈希边界、阶段 0 的 Redis key 前缀。
- Produces:
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/logout`
  - `GET /api/v1/auth/me`
  - `CurrentUser currentUser()` 安全上下文
  - Redis key：`${agentto.infrastructure.redis.key-prefix}auth:session:<tokenSha256>`
  - Redis 用户会话索引：`${agentto.infrastructure.redis.key-prefix}auth:user-sessions:<userId>`

- [ ] **Step 1: 先写登录服务失败测试**

测试至少覆盖：

```java
@Test
void createsSessionWhenPasswordMatches() {
    LoginResult result = service.login("admin", "correct-password", requestMetadata);

    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.expiresAt()).isAfter(Instant.now(clock));
    assertThat(sessionStore.find(result.accessToken()))
            .get()
            .extracting(AuthSession::userId)
            .isEqualTo("user-admin");
}

@Test
void rejectsDisabledAccountWithoutCreatingSession() {
    disableAdmin();

    assertThatThrownBy(() -> service.login("admin", "correct-password", requestMetadata))
            .isInstanceOf(BusinessException.class)
            .extracting("code")
            .isEqualTo("INVALID_CREDENTIALS");
    assertThat(sessionStore.count()).isZero();
}
```

对“用户不存在、密码错误、账号停用”统一返回 `INVALID_CREDENTIALS`，避免暴露账号是否存在。

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' `
  -Dtest=AuthenticationServiceTest test
```

Expected: FAIL，认证服务尚不存在。

- [ ] **Step 2: 实现高熵不透明 Token 和 Redis 会话**

Token 生成规则：

```java
byte[] bytes = new byte[32];
secureRandom.nextBytes(bytes);
String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
```

Redis 不保存原始 Token，只使用 SHA-256 作为 key 后缀。每次创建会话时，同时把哈希后缀放入用户会话索引 Set；退出、停用账号、修改角色和重置密码时，通过该索引删除用户的全部会话，避免旧权限继续生效。会话 JSON 至少包含：

```java
public record AuthSession(
        String sessionId,
        String userId,
        String username,
        String displayName,
        Set<String> roles,
        Instant issuedAt,
        Instant expiresAt) {
}
```

会话默认 8 小时，由 Nacos 配置：

```yaml
agentto:
  security:
    session-ttl: 8h
    allowed-origins:
      - http://127.0.0.1:5173
      - http://localhost:5173
    bootstrap-admin:
      enabled: true
      username: admin
      display-name: 技术管理员
      password: CHANGE_ME_BOOTSTRAP_PASSWORD
```

启动初始化只在 `at_user_account` 为空时创建首个技术管理员；已有任何账号时不得重置密码，也不得打印原密码。

测试配置中的 `bootstrap-admin.enabled` 固定为 `false`，测试用账号由测试夹具显式创建，不能依赖开发管理员。

- [ ] **Step 3: 配置 Spring Security**

安全规则：

```java
http
    .csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                    "/api/v1/auth/login",
                    "/actuator/health",
                    "/actuator/health/**")
            .permitAll()
            .anyRequest().authenticated())
    .addFilterBefore(bearerTokenAuthenticationFilter,
            UsernamePasswordAuthenticationFilter.class);
```

缺少 Token 返回 401；Token 有效但角色不足返回 403；两者都使用统一 JSON 错误，不返回 Spring 默认 HTML。

- [ ] **Step 4: 实现登录、当前用户和退出接口**

登录请求与响应：

```java
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        CurrentUserView user) {
}
```

`logout` 删除当前 Token 对应的 Redis key；重复退出返回成功，保证幂等。

- [ ] **Step 5: 编写真实 MySQL + Redis 登录链路测试**

`AuthFlowIT` 使用测试库创建 BCrypt 管理员账号，然后依次验证：

1. 未登录访问 `/api/v1/auth/me` → 401；
2. 正确密码登录 → 200，返回 Bearer Token；
3. 携带 Token 访问 `/me` → 200；
4. 退出 → 204；
5. 旧 Token 再访问 `/me` → 401；
6. Redis 中使用测试前缀，不写入开发前缀。

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Pintegration `
  -Dit.test=AuthFlowIT verify
```

Expected: PASS，测试结束后清除本次创建的用户、角色关系和测试会话。

---

### Task 4: 实现技术管理员的部门和账号管理接口

**Files:**
- Create: `backend/src/main/java/com/agentto/platform/identity/web/DepartmentAdminController.java`
- Create: `backend/src/main/java/com/agentto/platform/identity/web/UserAdminController.java`
- Create: `backend/src/main/java/com/agentto/platform/identity/web/dto/*.java`
- Test: `backend/src/test/java/com/agentto/platform/identity/IdentityAdminApiIT.java`
- Test: `backend/src/test/java/com/agentto/platform/security/RoleAuthorizationIT.java`

**Interfaces:**
- Consumes: Task 2 的身份应用服务和 Task 3 的当前用户安全上下文。
- Produces:
  - `POST /api/v1/admin/departments`
  - `GET /api/v1/admin/departments`
  - `POST /api/v1/admin/users`
  - `GET /api/v1/admin/users`
  - `PUT /api/v1/admin/users/{userId}/roles`
  - `PUT /api/v1/admin/users/{userId}/enabled`
  - `PUT /api/v1/admin/users/{userId}/password`

- [ ] **Step 1: 先写角色授权集成测试**

```java
@Test
void executiveCannotCreatePlatformAccount() throws Exception {
    mockMvc.perform(post("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, bearer(executiveToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateUserJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
}

@Test
void technicalAdminCanCreatePlatformAccount() throws Exception {
    mockMvc.perform(post("/api/v1/admin/users")
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validCreateUserJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("executive01"));
}
```

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Pintegration `
  -Dit.test=RoleAuthorizationIT verify
```

Expected: FAIL，管理接口尚不存在。

- [ ] **Step 2: 实现部门管理接口**

创建部门请求：

```java
public record CreateDepartmentRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        String parentDepartmentId,
        String externalOrgId) {
}
```

部门编码规范化为大写字母、数字、下划线和连字符；父部门必须存在且启用；禁止把部门的父级设置为自身或自己的后代。第一版只提供创建和查询，不提供物理删除。

- [ ] **Step 3: 实现账号管理接口**

创建账号时必须提供初始密码和至少一个固定系统角色。响应不能包含 `passwordHash`、初始密码或 Redis 会话数据。

角色替换使用完整集合语义：

```java
public record ReplaceUserRolesRequest(
        @NotEmpty Set<SystemRole> roles) {
}
```

停用账号后：

1. MySQL 账号状态更新为停用；
2. 删除该用户当前所有 Redis 会话；
3. 已打开页面的下一次请求立即返回 401；
4. 保留账号、角色和审计历史，不物理删除。

角色替换和密码重置同样删除该用户全部现有会话，要求用户重新登录后获得最新角色和凭证状态。

- [ ] **Step 4: 给管理接口增加方法级授权**

```java
@PreAuthorize("hasRole('TECH_ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/users")
class UserAdminController {
    // ...
}
```

Spring Security authority 统一使用 `ROLE_` 前缀；数据库继续保存无前缀角色码，避免把框架细节写入业务表。

- [ ] **Step 5: 运行管理 API 集成测试**

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Pintegration `
  -Dit.test=IdentityAdminApiIT,RoleAuthorizationIT verify
```

Expected: PASS，覆盖创建部门、创建账号、角色不足、停用账号和密码重置。

---

### Task 5: 审计身份操作并完成阶段 1A 回归

**Files:**
- Create: `backend/src/main/java/com/agentto/platform/audit/application/AuditEventService.java`
- Create: `backend/src/main/java/com/agentto/platform/audit/domain/AuditEventEntity.java`
- Create: `backend/src/main/java/com/agentto/platform/audit/domain/AuditEventRepository.java`
- Modify: `backend/src/main/java/com/agentto/platform/identity/application/IdentityAdminService.java`
- Modify: `backend/src/main/java/com/agentto/platform/security/application/AuthenticationService.java`
- Modify: `backend/README.md`
- Modify: `backend/docs/phase-0-verification.md`
- Create: `backend/docs/phase-1a-verification.md`
- Test: `backend/src/test/java/com/agentto/platform/audit/IdentityAuditIT.java`

**Interfaces:**
- Consumes: 现有 `at_audit_event` 表和前四个任务的身份操作。
- Produces: 身份相关审计事件和阶段 1A 验证记录。

- [ ] **Step 1: 先写审计集成测试**

身份操作至少记录以下事件：

```text
AUTH_LOGIN_SUCCEEDED
AUTH_LOGIN_FAILED
AUTH_LOGOUT
DEPARTMENT_CREATED
USER_CREATED
USER_ROLES_REPLACED
USER_ENABLED_CHANGED
USER_PASSWORD_RESET
```

测试断言示例：

```java
assertThat(auditEventsFor(targetUserId))
        .extracting(AuditEventEntity::getEventType)
        .contains("USER_CREATED", "USER_ROLES_REPLACED");
```

审计 `event_data` 只记录字段名、变更前后状态和目标 ID，不记录原始密码、密码哈希或完整 Bearer Token。

Run:

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' -Pintegration `
  -Dit.test=IdentityAuditIT verify
```

Expected: FAIL，服务尚未写入身份审计事件。

- [ ] **Step 2: 实现追加式审计服务**

```java
public interface AuditEventService {
    void append(AuditEventCommand command);
}
```

审计写入失败时，账号创建、角色变更、停用和密码重置必须整体回滚；登录失败审计无法写入时记录结构化服务错误，但仍不得把密码写日志。

- [ ] **Step 3: 完成所有测试回归**

先运行单元测试：

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' clean test
```

再运行真实中间件集成测试：

```powershell
& 'D:\DevTools\Maven\apache-maven-3.9.16\bin\mvn.cmd' clean verify -Pintegration
```

Expected: 两条命令均以 exit code 0 结束；测试日志中没有 H2、明文密码、Token 或外部模型调用。

- [ ] **Step 4: 启动并做真实接口冒烟检查**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'E:\AgentTo\backend\start-local.ps1'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File 'E:\AgentTo\backend\check-local.ps1'
```

使用 Nacos 中的首个技术管理员账号验证：登录、`/me`、创建部门、创建高管账号、停用账号和退出。所有接口必须无 500，随后保留服务运行供用户确认。

- [ ] **Step 5: 补充阶段验证文档**

`phase-1a-verification.md` 记录：

- 实际 Java、Spring Boot、Spring Security、MySQL、Redis 版本；
- Flyway V2 表和索引；
- 单元测试、集成测试数量与命令；
- 登录、权限和会话失效冒烟结果；
- Nacos 新增配置项；
- 已知限制：仅平台账号、无企微登录、无任务数据权限、无文件上传；
- 下一步：阶段 1B 文件任务、参与人、不可变文件版本与任务级授权。

---

## 阶段 1A 验收清单

- [ ] 技术管理员可以使用平台账号登录、查看当前用户并退出。
- [ ] 登录会话位于 Redis，停用账号或主动退出后立即失效。
- [ ] 密码只以 BCrypt 哈希形式进入 MySQL，接口、日志和审计中没有原密码。
- [ ] 技术管理员可以创建部门、账号并分配固定系统角色。
- [ ] 非技术管理员访问账号和组织管理 API 返回 403。
- [ ] 固定系统角色与任务中的 A、C 角色严格分开。
- [ ] 企微外部用户和组织 ID 已有可空映射字段，但未提前实现企微登录。
- [ ] 身份相关变更全部写入追加式审计记录。
- [ ] 单元测试和真实 MySQL、Redis 集成测试全部通过。
- [ ] 后端启动后健康检查、登录和管理接口无 500，并保持运行供人工确认。

## 后续计划边界

阶段 1A 通过人工确认后，再单独编写和执行阶段 1B 计划，范围为：

1. 文件任务、主导人和动态参与人；
2. Word、PDF 主文件和 Excel 辅助材料；
3. MinIO 不可变版本、SHA-256 和下载校验；
4. 任务级数据权限；
5. 文件和版本审计；
6. 为阶段 2 RAG 正式解析快照提供稳定文件版本接口。
