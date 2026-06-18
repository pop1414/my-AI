---
baseline_commit: 7cb84da
---

# Story 2.3: SparseRetrievalAdapter BM25 全文检索

Status: done

## Story

作为开发者，
我希望系统具备基于关键词的稀疏检索能力，
以便精确技术术语（如 "Flyway"、"PGVector"）能被准确召回。

## Acceptance Criteria

1. **Given** Flyway V9 迁移已创建 `content_tsv` tsvector 列和 GIN 索引，`ScopeFilterBuilder` 已就位
   **When** 新增 `SparseRetrievalAdapter`（`qa/infrastructure/retrieval/` 包下）
   **Then** 实现 `ChunkRetrievalPort` 接口的 `similaritySearch` 方法（AD-1：不改端口接口）

2. **Given** SparseRetrievalAdapter 是 BM25 路径的基础设施实现
   **When** 检索时
   **Then** 使用 `JdbcTemplate` 直接执行 SQL，不经过 Spring AI `VectorStore`

3. **Given** BM25 近似全文检索的 SQL 查询
   **When** 构造查询语句
   **Then** SQL 为：
   ```sql
   SELECT id, content, metadata, ts_rank(content_tsv, query) AS rank
   FROM vector_store, plainto_tsquery('simple', ?) query
   WHERE content_tsv @@ query [AND scope_filter]
   ORDER BY rank DESC LIMIT ?
   ```
   **And** scope_filter 部分通过 `ScopeFilterBuilder.toSqlCondition()` 动态拼接

4. **Given** 需要复用 scope 过滤逻辑
   **When** 调用 `ScopeFilterBuilder.toSqlCondition(scope)`
   **Then** 返回 `SqlScopeCondition`，拼接到 WHERE 子句
   **And** scope 为空/null 时不添加额外 WHERE 条件

5. **Given** score 字段语义（FR-1, AD-10）
   **When** 映射检索结果
   **Then** `RetrievedChunk.score = ts_rank` 值（直接取 SQL 查询的 `rank` 列）

6. **Given** BM25 检索的元数据映射
   **When** 从 `vector_store` 表的 `metadata` JSON 列提取字段
   **Then** 与 `PgVectorChunkRetrievalAdapter.toRetrievedChunk()` 保持一致的字段映射：
   - `documentId` ← `metadata->>'documentId'`
   - `kbId` ← `metadata->>'kbId'`
   - `chunkIndex` ← `(metadata->>'chunkIndex')::int`
   - `documentVersionNumber` ← `(metadata->>'documentVersionNumber')::int`（可空）
   - `splitVersion` ← `metadata->>'splitVersion'`（兼容解析）
   - `sourceFile` ← `metadata->>'sourceFile'`
   - `sourceUpdatedAt` ← `metadata->>'sourceUpdatedAt'`

7. **Given** 空查询防御
   **When** `question` 为 null 或 blank
   **Then** 返回空列表 `List.of()`（不抛异常，与 Dense 路径一致）

8. **Given** topK 下限保护
   **When** `topK` 为 0 或负值
   **Then** 使用 `Math.max(1, topK)` 作为 LIMIT 值

9. **Given** 测试覆盖
   **When** 编写 `SparseRetrievalAdapterTest`
   **Then** 覆盖以下场景：
   - 空查询返回空列表
   - topK 下限保护
   - 正常查询正确构造 SQL 并映射结果（mock JdbcTemplate 验证）
   - score 正确映射 ts_rank 值
   - scope 过滤正确拼接到 WHERE 子句
   - scope 为空时不拼接额外条件
   - metadata 字段缺失时的安全回退（documentId/kbId 为空则跳过该行）
   - 包含 "Flyway" 关键词的查询能检索到含 "Flyway" 的 chunk（有测试用例）

10. **Given** 不修改 `ChunkRetrievalPort` 接口（AD-1）
    **When** 实现完成
    **Then** `ChunkRetrievalPort.java` 无任何变更
    **And** 现有 `PgVectorChunkRetrievalAdapterTest` 所有测试不受影响

## Tasks / Subtasks

- [x] Task 1: 新增 `SparseRetrievalAdapter` 主类（AC: #1, #2, #3, #4, #5）
  - [x] 1.1 在 `qa/infrastructure/retrieval/` 包下创建 `SparseRetrievalAdapter.java`
  - [x] 1.2 类标注 `@Component`，注入 `JdbcTemplate`
  - [x] 1.3 实现 `ChunkRetrievalPort` 的两个 `similaritySearch` 方法重载
  - [x] 1.4 空查询快速返回 `List.of()`（null/blank 防御）
  - [x] 1.5 topK 下限保护 `Math.max(1, topK)`
  - [x] 1.6 使用 `ScopeFilterBuilder.toSqlCondition(scope)` 构造 WHERE 条件
  - [x] 1.7 拼接完整 SQL：`SELECT ... FROM vector_store, plainto_tsquery('simple', ?) query WHERE content_tsv @@ query [AND scope_filter] ORDER BY rank DESC LIMIT ?`
  - [x] 1.8 使用 `JdbcTemplate.query()` 执行 SQL，RowMapper 映射 `RetrievedChunk`
  - [x] 1.9 score 映射 `ts_rank(content_tsv, query)` 列值

- [x] Task 2: 实现 RowMapper 元数据映射逻辑（AC: #5, #6）
  - [x] 2.1 从 `metadata` JSON 字段提取 `documentId`、`kbId`、`chunkIndex` 等
  - [x] 2.2 复用 `PgVectorChunkRetrievalAdapter` 中的 `asString`、`asInt`、`resolveSourceVersionNumber`、`asInstant` 等辅助方法的相同逻辑
  - [x] 2.3 documentId/kbId 为空时跳过该行（与 Dense 路径一致的容错策略）
  - [x] 2.4 content 允许为空（归一为空字符串）

- [x] Task 3: 新增 `SparseRetrievalAdapterTest`（AC: #8, #9）
  - [x] 3.1 创建 `SparseRetrievalAdapterTest.java`（与被测类同包 package-private）
  - [x] 3.2 空查询返回空列表
  - [x] 3.3 topK 下限保护
  - [x] 3.4 正常查询 — mock JdbcTemplate 验证 SQL 和参数
  - [x] 3.5 score 正确映射 ts_rank
  - [x] 3.6 scope 过滤拼接验证
  - [x] 3.7 scope 为空时不拼接额外条件
  - [x] 3.8 metadata 缺失安全回退
  - [x] 3.9 "Flyway" 关键词检索测试用例

- [x] Task 4: 集成验证（AC: #10）
  - [x] 4.1 运行 `mvn test "-Dtest=SparseRetrievalAdapterTest"` — 9 个测试全部通过
  - [x] 4.2 运行 `mvn test "-Dtest=PgVectorChunkRetrievalAdapterTest"` — 8 个现有测试全部通过
  - [x] 4.3 运行 `mvn test "-Dtest=ScopeFilterBuilderTest"` — 15 个现有测试全部通过
  - [x] 4.4 确认 `ChunkRetrievalPort.java` 无任何变更（git status 确认）

## Dev Notes

### 前置故事上下文

- **Story 1.1-1.6（done）**：Epic 1 全部完成 — `RetrievedChunk.score`、`RerankingPort`、`QaRetrievalProperties`、`QueryType`、`RuleBasedQueryClassifier`、CHITCHAT 拦截均已就位
- **Story 2.1（done）**：Flyway V9 迁移已执行 — `content_tsv tsvector` 列和 `idx_vector_store_fts` GIN 索引已创建
- **Story 2.2（done/review）**：`ScopeFilterBuilder` 已提取 — `toFilterExpression()`（Dense 路径）和 `toSqlCondition()`（Sparse 路径）两个静态方法可用，`SqlScopeCondition` record 已定义

本 Story 是 Epic 2 的第三个 Story，是 BM25 稀疏检索路径的核心实现。它是 Story 2.4（HybridChunkRetrievalAdapter RRF 融合）的前置依赖。

### 当前 ChunkRetrievalPort 接口（精确签名）

**文件：** `src/main/java/io/github/spike/myai/qa/domain/port/ChunkRetrievalPort.java`

```java
public interface ChunkRetrievalPort {
    List<RetrievedChunk> similaritySearch(String question, int topK);
    List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope);
}
```

**关键约束（AD-1）：** 不修改此接口。SparseRetrievalAdapter 作为新的实现者，与 PgVectorChunkRetrievalAdapter 平行实现同一端口。

### 当前 RetrievedChunk 构造器

**文件：** `src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java`

```java
// 全字段构造器
new RetrievedChunk(documentId, kbId, chunkIndex, content,
    sourceVersionNumber, sourceFilename, sourceUpdatedAt, score);

// 简化构造器（score 默认 0.0，版本元数据为 null）
new RetrievedChunk(documentId, kbId, chunkIndex, content);
```

SparseRetrievalAdapter 应使用全字段构造器，正确填充 score（ts_rank）和版本元数据（从 metadata JSON 提取）。

### ScopeFilterBuilder.toSqlCondition() 使用方式

**文件：** `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/ScopeFilterBuilder.java`

```java
// 返回 SqlScopeCondition record
SqlScopeCondition condition = ScopeFilterBuilder.toSqlCondition(scope);
// condition.whereClause() — SQL 片段，始终有外层括号，scope 为空时为空字符串
// condition.params() — 参数列表，顺序与 ? 占位符一一对应
```

**拼接示例：**
```java
// scope 不为空时
String sql = "SELECT ... WHERE content_tsv @@ query " +
    "AND " + condition.whereClause() + " ORDER BY rank DESC LIMIT ?";
List<Object> params = new ArrayList<>();
params.add(question);  // plainto_tsquery 的参数
params.addAll(condition.params());  // scope 过滤的参数
params.add(topK);  // LIMIT 参数

// scope 为空时
String sql = "SELECT ... WHERE content_tsv @@ query ORDER BY rank DESC LIMIT ?";
List<Object> params = List.of(question, topK);
```

### vector_store 表当前结构

```sql
-- V1 创建的基础表
CREATE TABLE vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1024)
);

-- V9 新增的 tsvector 列和 GIN 索引
-- content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED
-- idx_vector_store_fts ON vector_store USING GIN (content_tsv)
```

**现有列：** id (UUID PK), content (text), metadata (json), embedding (vector(1024)), content_tsv (tsvector, V9 新增)
**现有索引：** idx_vector_store_embedding (HNSW, cosine), idx_vector_store_fts (GIN, V9 新增)

### PostgreSQL plainto_tsquery + ts_rank API

```sql
-- plainto_tsquery 将纯文本转为 tsquery，'simple' 配置逐字拆分
-- "Flyway migration" → 'flyway' & 'migration'
-- "PGVector 配置" → 'pgvector' & '配' & '置'

-- ts_rank 返回文档对查询的相关性分数（float4），值越大越相关
-- 范围通常 0.0 ~ 1.0，但不保证归一化

-- @@ 操作符：tsvector 匹配 tsquery
```

**参数化查询注意：** `plainto_tsquery('simple', ?)` 的 `?` 是 question 文本参数。scope 过滤的参数在 `SqlScopeCondition.params()` 中。LIMIT 也是参数化的 `?`。

### 测试策略：mock JdbcTemplate

**设计决策：** 单元测试 mock `JdbcTemplate`，验证 SQL 构造和参数传递，不要求真实数据库连接。

**mock 模式：**
```java
JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
SparseRetrievalAdapter adapter = new SparseRetrievalAdapter(jdbcTemplate);

// mock query() 返回模拟的 RowMapper 结果
when(jdbcTemplate.query(anyString(), any(RowMapper.class), any()))
    .thenAnswer(invocation -> {
        String sql = invocation.getArgument(0);
        RowMapper<RetrievedChunk> rowMapper = invocation.getArgument(1);
        // 验证 SQL 包含关键子句
        assertTrue(sql.contains("content_tsv @@ query"));
        // 返回模拟结果
        return List.of(mockChunk);
    });
```

**注意：** `JdbcTemplate.query(String sql, RowMapper<T> rm, Object... args)` 的 varargs 签名需要仔细 mock。如果使用 `NamedParameterJdbcTemplate` 则更方便，但与 `PgVectorChunkRetrievalAdapter` 的风格保持一致（项目规则：同一文件内禁止混用 `NamedParameterJdbcTemplate` 和 `?` 占位符）。

**推荐方式：** 使用 `ArgumentCaptor` 捕获 SQL 和参数，逐一验证关键子句和参数值。

### metadata JSON 字段映射（与 Dense 路径一致）

| metadata 键 | SQL 表达式 | Java 类型 | 用途 |
|-------------|-----------|----------|------|
| `documentId` | `metadata->>'documentId'` | String | 文档 ID |
| `kbId` | `metadata->>'kbId'` | String | 知识库 ID |
| `chunkIndex` | `(metadata->>'chunkIndex')::int` | int | 分块序号 |
| `documentVersionNumber` | `(metadata->>'documentVersionNumber')::int` | Integer (nullable) | 文档版本号 |
| `splitVersion` | `metadata->>'splitVersion'` | String | 分块版本标识（兼容解析） |
| `sourceFile` | `metadata->>'sourceFile'` | String | 来源文件名 |
| `sourceUpdatedAt` | `metadata->>'sourceUpdatedAt'` | String → Instant | 来源更新时间 |

**RowMapper 中可以直接从 ResultSet 获取 metadata JSON：**
```java
// PostgreSQL json 类型在 JDBC 中映射为 PGobject，可用 getString() 或 getObject()
String metadataJson = rs.getString("metadata");
// 或使用 Jackson/Gson 解析
```

**或者直接在 SQL 中提取字段：**
```sql
SELECT id, content,
       metadata->>'documentId' AS document_id,
       metadata->>'kbId' AS kb_id,
       (metadata->>'chunkIndex')::int AS chunk_index,
       (metadata->>'documentVersionNumber')::int AS version_number,
       metadata->>'splitVersion' AS split_version,
       metadata->>'sourceFile' AS source_file,
       metadata->>'sourceUpdatedAt' AS source_updated_at,
       ts_rank(content_tsv, query) AS rank
FROM vector_store, plainto_tsquery('simple', ?) query
WHERE content_tsv @@ query [AND scope_filter]
ORDER BY rank DESC LIMIT ?
```

**推荐方式：** 在 SQL 中直接提取 metadata 字段，RowMapper 直接从 ResultSet 列名读取。这比在 Java 中解析 JSON 更简洁、性能更好。

### 辅助方法复用策略

`PgVectorChunkRetrievalAdapter` 中的 `asString`、`asInt`、`asNullableInt`、`asInstant`、`resolveSourceVersionNumber`、`parseVersionNumberFromSplitVersion` 是 `private static` 方法。

**选项对比：**

| 选项 | 方案 | 优劣 |
|------|------|------|
| A | 提取为共享工具类 | ✅ DRY，但增加类数量 |
| B | 在 SparseRetrievalAdapter 中复制 | ❌ 代码重复 |
| C | SQL 中直接提取字段，减少 Java 端辅助方法 | ✅ 推荐 |

**推荐选项 C：** 在 SQL SELECT 中直接提取所有需要的 metadata 字段（`metadata->>'documentId' AS document_id` 等），RowMapper 直接使用 `rs.getString("document_id")` 等标准 JDBC 方法。这样：
- `asString` → `rs.getString()` (天然 null-safe)
- `asInt` → `rs.getInt()` + `wasNull()` 检查
- `asInstant` → `Instant.parse(rs.getString("source_updated_at"))` + try-catch
- `resolveSourceVersionNumber` → 仍需要，但可简化为从 `split_version` 列解析

**如果选择复制辅助方法：** 必须保持逻辑完全一致，特别是 `resolveSourceVersionNumber` 和 `parseVersionNumberFromSplitVersion` 的 legacy 兼容逻辑。

### 架构约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| AD-1：不改 ChunkRetrievalPort 接口 | ✅ | 新增实现者，不改端口 |
| NFR-2：零新外部依赖 | ✅ | JdbcTemplate + PostgreSQL 原生 tsvector，无新 Maven 依赖 |
| NFR-3：六边形合规 | ✅ | SparseRetrievalAdapter 在 infrastructure 层，实现 domain port |
| AD-4：ScopeFilterBuilder 复用 | ✅ | 使用 `toSqlCondition()` 方法 |
| AD-10：score 语义由调用链决定 | ✅ | score = ts_rank，不引入 RetrievalMethod 枚举 |
| 项目规则：JdbcTemplate 直接 SQL | ✅ | 不通过 Spring AI VectorStore |
| 项目规则：? 占位符 | ✅ | 与项目现有 JdbcTemplate 风格一致 |

### Project Structure Notes

**新增文件：**
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapter.java`（`@Component` class）
- `src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapterTest.java`

**修改文件：** 无

**不修改文件：**
- `ChunkRetrievalPort.java`（端口接口不变）
- `PgVectorChunkRetrievalAdapter.java`（Dense 路径不变）
- `ScopeFilterBuilder.java`（共享工具不变）
- `SqlScopeCondition.java`（record 不变）
- `AskQuestionApplicationService.java`（应用层不变，Story 2.4 才切换到 Hybrid）

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 2.3] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-8] — SparseRetrievalAdapter 需求
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-1,AD-4] — 不改端口 + ScopeFilterBuilder 复用
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#实施顺序] — Phase 3 Step 9（FR-8）
- [Source: src/main/java/io/github/spike/myai/qa/domain/port/ChunkRetrievalPort.java] — 端口接口定义
- [Source: src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java] — 领域模型
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/ScopeFilterBuilder.java] — 共享 scope 过滤工具
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SqlScopeCondition.java] — SQL 条件 record
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java] — Dense 路径参考实现
- [Source: src/main/resources/db/migration/V9__hybrid_search_tsvector.sql] — tsvector 列定义
- [Source: docs/project-context.md#JdbcTemplate 模板] — SQL 构造规则

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- 两个测试初版失败：(1) scope 过滤参数计数错误（version 2/4 各 3 个 params，非 4 个，预期 8 非 9）；(2) 空 scope 断言误匹配 SELECT 子句中的 `metadata->>'documentId'`，改为检查 WHERE 子句中的 `AND metadata->>'documentId'`。修正后 9/9 通过。

### Completion Notes List

- 创建 `SparseRetrievalAdapter`（`@Component`），实现 `ChunkRetrievalPort` 接口的两个 `similaritySearch` 方法重载
- 使用 `JdbcTemplate` 直接执行 BM25 检索 SQL，不经过 Spring AI VectorStore
- SQL 在 SELECT 中直接提取 metadata JSON 字段（`metadata->>'documentId' AS document_id` 等），RowMapper 从 ResultSet 列名读取，减少 Java 端 JSON 解析
- 复用 `ScopeFilterBuilder.toSqlCondition()` 构造 scope 过滤 SQL，`SqlScopeCondition` 拼接到 WHERE 子句
- score 直接映射 `ts_rank(content_tsv, query)` 列值
- `resolveSourceVersionNumber` 和 `parseVersionNumberFromSplitVersion` 保持与 Dense 路径一致的版本号兼容解析逻辑
- 空查询（null/blank）快速返回 `List.of()`，topK 下限保护 `Math.max(1, topK)`
- documentId/kbId 为空时跳过该行（与 Dense 路径一致的容错策略），日志 WARN
- `SparseRetrievalAdapterTest` 9 个测试全部通过：空查询、topK 保护、SQL 构造、score 映射、scope 过滤、空 scope、metadata 缺失、Flyway 关键词检索
- `PgVectorChunkRetrievalAdapterTest` 8 个现有测试全部通过（无回归）
- `ScopeFilterBuilderTest` 15 个现有测试全部通过（无回归）
- `ChunkRetrievalPort.java` 无任何变更（AD-1 约束满足）

### File List

- src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapter.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapterTest.java (新增)


## Change Log

- feat(qa): Story 2.3 — SparseRetrievalAdapter BM25 全文检索（2026-06-18）
