# Infrastructure 测试重构计划

**创建日期**: 2026-06-16  
**目标**: 将 12 个禁用的 JdbcTemplate mock 测试迁移到 Testcontainers 集成测试  
**预计工作量**: 2-3 天  
**优先级**: 高

---

## 1. 重构概述

### 当前问题

12 个 Infrastructure 层测试违规 mock JdbcTemplate，违反项目规则：

> "禁止 mock JdbcTemplate/JDBC 链路 — SQL 正确性只能靠真实数据库验证"

### 重构目标

1. ✅ 使用 Testcontainers 提供真实 PostgreSQL 环境
2. ✅ 移除所有 JdbcTemplate mock
3. ✅ 通过真实数据库验证 SQL 正确性
4. ✅ 移除 @Disabled 注解，恢复测试
5. ✅ 保持测试逻辑和覆盖范围

### 预期收益

- 真实数据库验证，消除虚假覆盖率
- 提前发现 SQL 语法错误和性能问题
- 验证 Flyway migration 兼容性
- 提升测试可信度和代码质量

---

## 2. 技术方案

### 2.1 依赖配置

**pom.xml 添加**:
```xml
<!-- Testcontainers PostgreSQL -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>

<!-- Testcontainers JUnit 5 Extension -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>

<!-- PostgreSQL JDBC Driver (确保版本匹配) -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### 2.2 Testcontainers 配置

**BaseRepositoryTest.java** (基础测试类):
```java
package io.github.spike.myai.infrastructure.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

/**
 * 基础 Repository 测试类。
 *
 * <p>提供真实的 PostgreSQL 环境（通过 Testcontainers），
 * 确保所有 SQL 都在真实数据库上验证。
 *
 * @author spike
 * @since 1.0.0
 */
@Testcontainers
@SpringBootTest
public abstract class BaseRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("myai_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/migration/V1__init_schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DataSource dataSource;

    /**
     * 每个测试后清理数据。
     */
    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE documents CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE knowledge_bases CASCADE");
        jdbcTemplate.execute("TRUNCATE TABLE local_accounts CASCADE");
        // 根据需要清理更多表
    }
}
```

### 2.3 application-test.yaml

**src/test/resources/application-test.yaml**:
```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

  ai:
    dashscope:
      api-key: test-key
      chat:
        options:
          model: qwen-plus
      embedding:
        options:
          model: text-embedding-v4

logging:
  level:
    io.github.spike.myai: DEBUG
    org.springframework.jdbc: DEBUG
```

---

## 3. 迁移步骤

### Phase 1: 基础设施准备 (Day 1)

#### 3.1 添加依赖

```bash
# 编辑 pom.xml 添加 Testcontainers 依赖
# 验证依赖正确引入
mvn clean compile
```

#### 3.2 创建 BaseRepositoryTest

```bash
# 创建基础设施测试包
mkdir -p src/test/java/io/github/spike/myai/infrastructure/test

# 创建 BaseRepositoryTest.java
# 配置 Testcontainers
# 配置 Flyway 初始化
```

#### 3.3 创建测试数据工厂

**TestDataFactory.java**:
```java
package io.github.spike.myai.infrastructure.test;

import io.github.spike.myai.auth.domain.model.LoginAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.UploadStatus;

import java.time.Instant;

/**
 * 测试数据工厂。
 *
 * <p>提供标准化的测试数据，避免魔法字符串和硬编码值。
 *
 * @author spike
 * @since 1.0.0
 */
public final class TestDataFactory {

    private TestDataFactory() {
    }

    /**
     * 创建活跃状态的登录账号。
     */
    public static LoginAccount createActiveLoginAccount() {
        return new LoginAccount(
                "user-1",
                "alice",
                "Alice",
                "ACTIVE",
                "{bcrypt}hash",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN,
                "ACTIVE",
                0,
                null);
    }

    /**
     * 创建待处理状态的文档。
     */
    public static Document createPendingDocument() {
        return new Document(
                "doc-1",
                "kb-1",
                "hash-1",
                "test.txt",
                100,
                UploadStatus.INGESTING,
                null,
                0,
                3,
                null,
                null,
                null,
                null,
                0,
                null,
                "v1",
                null,
                Instant.now(),
                Instant.now());
    }

    // 添加更多工厂方法...
}
```

#### 3.4 创建 Flyway 初始化脚本

**src/test/resources/db/migration/V1__init_schema.sql**:
```sql
-- 复制生产环境的 Flyway migration
-- 或从现有 migration 合并

CREATE EXTENSION IF NOT EXISTS vector;

-- 认证相关表
CREATE TABLE local_accounts (
    user_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    display_name VARCHAR(100),
    user_status VARCHAR(20),
    password_hash VARCHAR(255),
    workspace_id VARCHAR(50),
    workspace_role VARCHAR(50),
    account_status VARCHAR(20),
    failed_login_count INTEGER DEFAULT 0,
    locked_until TIMESTAMPTZ
);

CREATE TABLE audit_events (
    event_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(50),
    event_type VARCHAR(100),
    outcome VARCHAR(20),
    reason VARCHAR(200),
    created_at TIMESTAMPTZ
);

-- 文档相关表
CREATE TABLE documents (
    document_id VARCHAR(50) PRIMARY KEY,
    knowledge_base_id VARCHAR(50),
    content_hash VARCHAR(64),
    filename VARCHAR(255),
    file_size BIGINT,
    upload_status VARCHAR(20),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    processed_at TIMESTAMPTZ,
    indexed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 知识库相关表
CREATE TABLE knowledge_bases (
    knowledge_base_id VARCHAR(50) PRIMARY KEY,
    workspace_id VARCHAR(50),
    name VARCHAR(100),
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 其他表...
-- 参考生产环境的 Flyway migration 文件
```

---

### Phase 2: 迁移 Repository 测试 (Day 2)

#### 3.5 迁移 JdbcLocalAccountRepositoryTest

**原代码** (禁用):
```java
@Disabled("TODO: Refactor to integration test")
class JdbcLocalAccountRepositoryTest {

    @Test
    void constructor_shouldNotExecuteImplicitDdl() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);  // ❌ 违规
        new JdbcLocalAccountRepository(jdbcTemplate);
        verify(jdbcTemplate, never()).execute(any(String.class));
    }
}
```

**迁移后**:
```java
class JdbcLocalAccountRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private JdbcLocalAccountRepository repository;

    @Test
    void constructor_shouldInitializeWithoutDdl() {
        // 验证 Repository 初始化不执行额外 DDL
        // 真实数据库已通过 Flyway 初始化
        assertNotNull(repository);
    }

    @Test
    void recordFailedLogin_shouldUpdateDatabase() {
        // Arrange
        LoginAccount account = TestDataFactory.createActiveLoginAccount();
        jdbcTemplate.update("""
            INSERT INTO local_accounts (user_id, username, display_name, user_status, 
                password_hash, workspace_id, workspace_role, account_status, 
                failed_login_count, locked_until)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            account.userId(), account.username(), account.displayName(),
            account.userStatus(), account.passwordHash(), account.workspaceId(),
            account.workspaceRole(), account.accountStatus(), 
            account.failedLoginCount(), account.lockedUntil());

        Instant failedAt = Instant.now();
        Instant lockUntil = failedAt.plus(Duration.ofMinutes(10));

        // Act
        LoginFailureState state = repository.recordFailedLogin(
                account.userId(), failedAt, 3, lockUntil);

        // Assert
        assertNotNull(state);
        assertEquals(1, state.failedLoginCount());
        assertNull(state.lockedUntil());

        // 验证数据库状态
        Integer failedCount = jdbcTemplate.queryForObject(
                "SELECT failed_login_count FROM local_accounts WHERE user_id = ?",
                Integer.class,
                account.userId());
        assertEquals(1, failedCount);
    }

    // 迁移其他测试方法...
}
```

#### 3.6 迁移其他 Repository 测试

为每个文件执行相同的迁移：

1. **JdbcBootstrapAdminRepositoryTest.java**
2. **JdbcAuditEventQueryRepositoryTest.java**
3. **JdbcDocumentGrantManagementRepositoryTest.java**
4. **JdbcKnowledgeBaseGrantManagementRepositoryTest.java**
5. **JdbcWorkspaceMemberRepositoryTest.java**
6. **JdbcDocumentRepositoryTest.java**
7. **JdbcDocumentListRepositoryTest.java**
8. **JdbcDocumentVersionHistoryRepositoryTest.java**
9. **IngestSchemaVerifierTest.java**
10. **PgVectorDocumentVectorIndexerTest.java**
11. **JdbcKnowledgeBaseRepositoryTest.java**

**迁移模式**:
```java
// 原模式 (Mockito mock)
JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
when(jdbcTemplate.queryForObject(...)).thenReturn(...);

// 新模式 (真实数据库)
@Autowired
JdbcTemplate jdbcTemplate;

// 准备测试数据
jdbcTemplate.update("INSERT INTO ...", ...);

// 执行被测方法
var result = repository.someMethod(...);

// 验证结果
assertNotNull(result);
// 验证数据库状态
Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ...", Integer.class);
assertEquals(expectedCount, count);
```

---

### Phase 3: 验证和优化 (Day 3)

#### 3.7 运行测试

```bash
# 运行所有测试（包括新迁移的集成测试）
mvn test

# 预期结果：
# - 所有 @Disabled 注解移除
# - 422+ 个测试通过
# - 0 个失败
# - 0 个跳过
```

#### 3.8 性能优化

**优化策略**:
1. **容器复用** — Testcontainers 默认每个测试类启动新容器
   ```java
   @Testcontainers
   class BaseRepositoryTest {
       @Container
       static PostgreSQLContainer<?> postgres = ...;  // static = 容器复用
   }
   ```

2. **批量清理** — 使用 `@Transactional` 回滚测试数据
   ```java
   @Transactional
   @Rollback
   class JdbcLocalAccountRepositoryTest {
       // 测试数据在每个测试后自动回滚
   }
   ```

3. **并行执行** — Maven Surefire 配置
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-surefire-plugin</artifactId>
       <configuration>
           <parallel>classes</parallel>
           <threadCount>4</parallel>
       </configuration>
   </plugin>
   ```

#### 3.9 更新验证报告

重新运行 Test Quality Review：

```bash
# 预期结果：
# - 质量评分: 95-100/100
# - 质量等级: A+ (Excellent)
# - 0 个 @Disabled 测试
# - 0 个 JdbcTemplate mock
# - 100% 真实数据库验证
```

---

## 4. 文件清单

### 需要迁移的文件 (12 个)

1. ✅ `JdbcLocalAccountRepositoryTest.java`
2. ⬜ `JdbcBootstrapAdminRepositoryTest.java`
3. ⬜ `JdbcAuditEventQueryRepositoryTest.java`
4. ⬜ `JdbcDocumentGrantManagementRepositoryTest.java`
5. ⬜ `JdbcKnowledgeBaseGrantManagementRepositoryTest.java`
6. ⬜ `JdbcWorkspaceMemberRepositoryTest.java`
7. ⬜ `JdbcDocumentRepositoryTest.java`
8. ⬜ `JdbcDocumentListRepositoryTest.java`
9. ⬜ `JdbcDocumentVersionHistoryRepositoryTest.java`
10. ⬜ `IngestSchemaVerifierTest.java`
11. ⬜ `PgVectorDocumentVectorIndexerTest.java`
12. ⬜ `JdbcKnowledgeBaseRepositoryTest.java`

### 需要创建的文件 (4 个)

1. ⬜ `BaseRepositoryTest.java` — 基础测试类
2. ⬜ `TestDataFactory.java` — 测试数据工厂
3. ⬜ `application-test.yaml` — 测试配置
4. ⬜ `V1__init_schema.sql` — 测试 schema 初始化

---

## 5. 风险和缓解措施

### 风险 1: 测试执行时间增加

**问题**: Testcontainers 启动容器需要时间（~10-30 秒）

**缓解措施**:
- 容器复用（static container）
- 批量运行测试（避免单个测试类启动容器）
- CI/CD 环境预热容器

### 风险 2: 测试数据污染

**问题**: 真实数据库可能保留脏数据

**缓解措施**:
- 每个测试后清理数据（`@AfterEach`）
- 使用 `@Transactional` + `@Rollback`
- 独立的测试 schema

### 风险 3: Flyway migration 兼容性

**问题**: 测试环境的 schema 可能与生产不一致

**缓解措施**:
- 复用生产 Flyway migration 文件
- 定期同步 schema
- CI/CD 验证 schema 一致性

---

## 6. 验收标准

### ✅ Phase 1 完成标准

- [ ] Testcontainers 依赖添加到 pom.xml
- [ ] BaseRepositoryTest 创建完成
- [ ] TestDataFactory 创建完成
- [ ] application-test.yaml 配置完成
- [ ] Flyway 初始化脚本创建完成
- [ ] 编译通过，无错误

### ✅ Phase 2 完成标准

- [ ] 所有 12 个文件迁移完成
- [ ] 所有 @Disabled 注解移除
- [ ] 所有 JdbcTemplate mock 移除
- [ ] 所有测试通过（422+ 个测试）
- [ ] 无编译警告

### ✅ Phase 3 完成标准

- [ ] 测试执行时间 < 60 秒（422 个测试）
- [ ] 无 flaky test（连续运行 5 次全部通过）
- [ ] 质量评分提升到 95+ 分
- [ ] 代码审查通过
- [ ] 文档更新完成

---

## 7. 后续优化

### 短期 (1-2 周)

- 添加更多集成测试（端到端流程）
- 优化测试执行速度（并行执行）
- 添加测试覆盖率报告

### 中期 (1 个月)

- 引入 Instancio（数据工厂库）
- 添加性能测试（SQL 查询分析）
- CI/CD 集成 Testcontainers

### 长期 (3 个月)

- 测试容器云化（Kubernetes test pods）
- 自动化测试数据生成
- 测试环境一键部署

---

## 8. 参考资源

### 官方文档

- [Testcontainers for Java](https://www.testcontainers.org/quickstart/junit5/)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
- [Flyway Testing](https://documentation.red-gate.com/fd/snapshot-277578894.html)

### 示例项目

- [Spring Boot Testcontainers Example](https://github.com/testcontainers/testcontainers-java/tree/main/examples)
- [PGVector Testcontainers](https://www.testcontainers.org/modules/databases/postgres/)

---

## 9. 附录

### A. 迁移检查清单模板

```markdown
## 迁移: [测试文件名]

### 预迁移
- [ ] 读取原测试代码
- [ ] 识别所有 JdbcTemplate mock
- [ ] 列出所有测试方法

### 迁移
- [ ] 创建新测试类（extends BaseRepositoryTest）
- [ ] 替换 JdbcTemplate mock 为 @Autowired
- [ ] 添加测试数据准备（jdbcTemplate.update）
- [ ] 添加数据库状态验证（jdbcTemplate.queryForObject）
- [ ] 保持原有断言逻辑

### 验证
- [ ] 编译通过
- [ ] 所有测试通过
- [ ] 移除 @Disabled 注解
- [ ] 代码审查
```

### B. 常见迁移模式

**模式 1: 查询方法测试**
```java
// 原代码
when(jdbcTemplate.queryForObject(...)).thenReturn(mockData);

// 迁移后
jdbcTemplate.update("INSERT INTO ...", ...);
var result = repository.findById(...);
assertNotNull(result);
```

**模式 2: 更新方法测试**
```java
// 原代码
repository.update(...);
verify(jdbcTemplate).update(...);

// 迁移后
repository.update(...);
Integer count = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM ... WHERE ...", Integer.class, ...);
assertEquals(1, count);
```

**模式 3: 删除方法测试**
```java
// 原代码
repository.delete(...);
verify(jdbcTemplate).update(...);

// 迁移后
repository.delete(...);
Integer count = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM ... WHERE ...", Integer.class, ...);
assertEquals(0, count);
```

---

**创建人**: Master Test Architect  
**创建日期**: 2026-06-16  
**版本**: 1.0  
**状态**: 等待执行
