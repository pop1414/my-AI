---
baseline_commit: da35d37
---

# Story 2.2: ScopeFilterBuilder 共享工具类

Status: review

## Story

作为开发者，
我希望 Dense 和 Sparse 两路检索共享 scope 过滤逻辑，
以便避免代码重复，D20 Group Model 迁移时集中维护。

## Acceptance Criteria

1. **Given** Dense 路径（`PgVectorChunkRetrievalAdapter`）已有 scope 过滤逻辑（`buildScopeFilter` + `buildVersionFilter` 两个 private static 方法，lines 141-176）
   **When** 提取 `ScopeFilterBuilder`（`qa/infrastructure/retrieval/` 包下，package-private class）
   **Then** `ScopeFilterBuilder` 不是 Spring Bean（无 `@Component`），是纯工具类，静态方法

2. **Given** Dense 路径需要 Spring AI `Filter.Expression` 输出
   **When** 调用 `ScopeFilterBuilder.toFilterExpression(List<AskableDocumentVersion> scope)`
   **Then** 返回 `Filter.Expression`（scope 为空/null 时返回 null）
   **And** 生成的过滤表达式结构与当前 `buildScopeFilter()` 完全一致：`OR( AND(documentId=A, GROUP(version-conditions-A)), AND(documentId=B, GROUP(version-conditions-B)), ... )`
   **And** version-conditions 保持原有逻辑：`OR(documentVersionNumber=N, splitVersion="version-N-v1", [splitVersion="v1" if N==1])`

3. **Given** Sparse 路径需要 SQL WHERE 子句输出（Story 2.3 需要）
   **When** 调用 `ScopeFilterBuilder.toSqlCondition(List<AskableDocumentVersion> scope)`
   **Then** 返回 `SqlScopeCondition` record
   **And** `SqlScopeCondition` record 包含 `String whereClause` 和 `List<Object> params`
   **And** scope 为空/null 时返回 `SqlScopeCondition("", List.of())`
   **And** 生成的 SQL 等价于 Filter.Expression 的语义：`(vs_document_id = ? AND (vs_version_number = ? OR vs_split_version = ?)) OR ...`

4. **Given** `SqlScopeCondition` 是一个新的 record
   **When** 定义
   **Then** 放在 `qa/infrastructure/retrieval/` 包下，package-private
   **And** record compact constructor 做防御性校验（whereClause 和 params 不能为 null）
   **And** params 使用 `Collections.unmodifiableList(new ArrayList<>(params))` 防御性拷贝

5. **Given** `PgVectorChunkRetrievalAdapter` 中 `buildScopeFilter` 和 `buildVersionFilter` 是 private static 方法
   **When** 重构为使用 `ScopeFilterBuilder`
   **Then** 删除 `buildScopeFilter` 和 `buildVersionFilter` 两个 private static 方法
   **And** `similaritySearch(String question, int topK, List<AskableDocumentVersion> scope)` 方法改为调用 `ScopeFilterBuilder.toFilterExpression(scope)`
   **And** 其他代码（`toRetrievedChunk`、metadata 常量、helper 方法）保持不变
   **And** metadata 常量（`METADATA_DOCUMENT_ID` 等）仍保留在 `PgVectorChunkRetrievalAdapter` 中（因为 `toRetrievedChunk` 仍然使用它们）
   **And** `ScopeFilterBuilder` 内部定义自己的 metadata key 常量（或使用字符串字面量），不引用 Adapter 的常量

6. **Given** 重构必须不破坏现有行为
   **When** 运行 `PgVectorChunkRetrievalAdapterTest`
   **Then** 所有 8 个现有测试全部通过（不修改任何现有测试）
   **And** 特别是 `similaritySearch_shouldPushAskableVersionScopeToVectorFilter` 和 `similaritySearch_shouldBuildPgVectorCompatibleFilterExpression` 两个 scope 相关测试

7. **Given** 新增 `ScopeFilterBuilderTest`
   **When** 编写测试
   **Then** 覆盖 `toFilterExpression`：
     - 空/null scope 返回 null
     - 单个文档 scope 生成正确的 Filter.Expression（包含 documentId、documentVersionNumber、splitVersion）
     - 多个文档 scope 生成 OR 组合
     - version=1 的文档包含 legacy splitVersion="v1" 条件
     - version>1 的文档不包含 splitVersion="v1" 条件
   **And** 覆盖 `toSqlCondition`：
     - 空/null scope 返回空 whereClause 和空 params
     - 单个文档 scope 生成正确的 SQL 片段和参数列表
     - 多个文档 scope 生成 OR 组合的 SQL
     - version=1 的文档包含 legacy splitVersion 条件
     - params 顺序与 `?` 占位符顺序一致
   **And** 验证 `PgVectorFilterExpressionConverter` 能转换 `toFilterExpression` 的输出（兼容性测试，复用现有测试模式）

## Tasks / Subtasks

- [x] Task 1: 新增 `SqlScopeCondition` record（AC: #4）
  - [x] 1.1 在 `qa/infrastructure/retrieval/` 包下创建 `SqlScopeCondition.java`
  - [x] 1.2 定义 record `SqlScopeCondition(String whereClause, List<Object> params)`
  - [x] 1.3 compact constructor 校验 whereClause 和 params 不为 null
  - [x] 1.4 params 防御性拷贝

- [x] Task 2: 新增 `ScopeFilterBuilder` 工具类（AC: #1, #2, #3）
  - [x] 2.1 在 `qa/infrastructure/retrieval/` 包下创建 `ScopeFilterBuilder.java`
  - [x] 2.2 实现 `static Filter.Expression toFilterExpression(List<AskableDocumentVersion> scope)` — 从 `PgVectorChunkRetrievalAdapter.buildScopeFilter()` + `buildVersionFilter()` 提取
  - [x] 2.3 实现 `static SqlScopeCondition toSqlCondition(List<AskableDocumentVersion> scope)` — 等价语义的 SQL 版本
  - [x] 2.4 类和方法均为 package-private（无 public 修饰符）
  - [x] 2.5 无 `@Component` 或其他 Spring 注解

- [x] Task 3: 重构 `PgVectorChunkRetrievalAdapter`（AC: #5, #6）
  - [x] 3.1 删除 `buildScopeFilter` 和 `buildVersionFilter` 两个 private static 方法
  - [x] 3.2 `similaritySearch` 方法改为调用 `ScopeFilterBuilder.toFilterExpression(scope)`
  - [x] 3.3 保留 metadata 常量（`toRetrievedChunk` 仍在使用）
  - [x] 3.4 运行 `mvn test "-Dtest=PgVectorChunkRetrievalAdapterTest"` — 8 个测试全部通过

- [x] Task 4: 新增 `ScopeFilterBuilderTest`（AC: #7）
  - [x] 4.1 创建 `ScopeFilterBuilderTest.java`
  - [x] 4.2 覆盖 `toFilterExpression` 的 5 个场景
  - [x] 4.3 覆盖 `toSqlCondition` 的 5 个场景
  - [x] 4.4 PgVector 转换器兼容性测试
  - [x] 4.5 运行 `mvn test "-Dtest=ScopeFilterBuilderTest"` — 全部通过

- [x] Task 5: 全量验证（AC: #6）
  - [x] 5.1 运行 `mvn test "-Dtest=PgVectorChunkRetrievalAdapterTest,ScopeFilterBuilderTest"` — 21 个测试全部通过
  - [x] 5.2 确认不修改任何现有测试文件

## Dev Notes

### 前置故事上下文

- Story 1.1-1.6（done）：Epic 1 全部完成 — RetrievedChunk.score、RerankingPort、QaRetrievalProperties、QueryType、RuleBasedQueryClassifier、CHITCHAT 拦截均已就位
- Story 2.1（done）：Flyway V9 迁移已执行 — `content_tsv tsvector` 列和 `idx_vector_store_fts` GIN 索引已创建

本 Story 是 Scope 过滤逻辑的重构提取，不改变任何外部行为。它是 Story 2.3（SparseRetrievalAdapter）的前置依赖。

### 当前 scope 过滤逻辑（精确代码位置）

**文件：** `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`

**`buildScopeFilter` 方法（lines 141-157）：**
```java
private static Filter.Expression buildScopeFilter(List<AskableDocumentVersion> scope) {
    if (scope == null || scope.isEmpty()) {
        return null;
    }
    FilterExpressionBuilder builder = new FilterExpressionBuilder();
    List<FilterExpressionBuilder.Op> documentVersionFilters = new ArrayList<>();
    for (AskableDocumentVersion item : scope) {
        documentVersionFilters.add(builder.and(
                builder.eq(METADATA_DOCUMENT_ID, item.documentId()),
                builder.group(buildVersionFilter(builder, item.askableVersionNumber()))));
    }
    FilterExpressionBuilder.Op result = documentVersionFilters.getFirst();
    for (int i = 1; i < documentVersionFilters.size(); i++) {
        result = builder.or(result, documentVersionFilters.get(i));
    }
    return result.build();
}
```

**`buildVersionFilter` 方法（lines 166-176）：**
```java
private static FilterExpressionBuilder.Op buildVersionFilter(
        FilterExpressionBuilder builder,
        int askableVersionNumber) {
    FilterExpressionBuilder.Op versionFilter = builder.or(
            builder.eq(METADATA_DOCUMENT_VERSION_NUMBER, askableVersionNumber),
            builder.eq(METADATA_SPLIT_VERSION, "version-" + askableVersionNumber + "-v1"));
    if (askableVersionNumber == INITIAL_DOCUMENT_VERSION_NUMBER) {
        versionFilter = builder.or(versionFilter, builder.eq(METADATA_SPLIT_VERSION, LEGACY_INITIAL_SPLIT_VERSION));
    }
    return versionFilter;
}
```

**依赖的常量（lines 47-63）：**
```java
private static final String METADATA_DOCUMENT_ID = "documentId";
private static final String METADATA_DOCUMENT_VERSION_NUMBER = "documentVersionNumber";
private static final String METADATA_SPLIT_VERSION = "splitVersion";
private static final int INITIAL_DOCUMENT_VERSION_NUMBER = 1;
private static final String LEGACY_INITIAL_SPLIT_VERSION = "v1";
```

### 重构前后行为对比

| 行为 | 重构前 | 重构后 |
|------|--------|--------|
| scope = null | `buildScopeFilter` 返回 null → 不设置 filterExpression | `ScopeFilterBuilder.toFilterExpression(null)` 返回 null → 不变 |
| scope = empty list | 返回 null | 返回 null |
| scope = [docA v2] | `documentId = 'docA' AND (documentVersionNumber = 2 OR splitVersion = 'version-2-v1')` | 相同 |
| scope = [docA v1] | 上面 + `OR splitVersion = 'v1'` | 相同 |
| scope = [docA v2, docB v4] | `(...docA...) OR (...docB...)` | 相同 |

### toSqlCondition SQL 生成规则

`toSqlCondition` 需要生成等价的 SQL WHERE 子句供 `SparseRetrievalAdapter` 使用（Story 2.3）。SQL 列名对应 vector_store 表的 metadata JSON 字段。

**注意：** Sparse 路径使用 JdbcTemplate 直接 SQL 查询 `vector_store` 表。metadata 列类型是 `json`，在 PostgreSQL 中用 `metadata->>'key'` 提取字段值。

```sql
-- 单文档 scope 示例
WHERE (
    metadata->>'documentId' = ?
    AND (
        (metadata->>'documentVersionNumber')::int = ?
        OR metadata->>'splitVersion' = ?
        -- 如果 version=1，额外 OR metadata->>'splitVersion' = 'v1'
    )
)

-- 多文档 scope 示例
WHERE (
    (metadata->>'documentId' = ? AND (...version conditions...))
    OR
    (metadata->>'documentId' = ? AND (...version conditions...))
)
```

**参数列表顺序：** documentId, versionNumber, splitVersion(s) 按文档顺序排列。

### Spring AI FilterExpressionBuilder API 参考

来自 `org.springframework.ai.vectorstore.filter.FilterExpressionBuilder`：

```java
FilterExpressionBuilder builder = new FilterExpressionBuilder();
FilterExpressionBuilder.Op op = builder.eq("key", value);       // key = value
FilterExpressionBuilder.Op combined = builder.and(op1, op2);    // op1 AND op2
FilterExpressionBuilder.Op alternatives = builder.or(op1, op2); // op1 OR op2
FilterExpressionBuilder.Op grouped = builder.group(op);          // (op)
Filter.Expression expression = op.build();                        // 最终表达式
```

### 架构约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| AD-4：ScopeFilterBuilder 在 infrastructure/retrieval/ | ✅ | package-private class |
| NFR-3：六边形合规 | ✅ | ScopeFilterBuilder 依赖 Spring AI 类型（Filter.Expression），必须在 infrastructure 层 |
| AD-1：不改 ChunkRetrievalPort 接口 | ✅ | 纯重构，不涉及端口接口变更 |
| NFR-2：零新外部依赖 | ✅ | 不引入新 Maven 依赖 |
| 数据对象使用 record | ✅ | SqlScopeCondition 使用 Java record |
| 防御性拷贝 | ✅ | SqlScopeCondition params 使用 unmodifiableList |

### SQL 列名与 metadata 键映射

| metadata 键 | SQL 表达式 | 用途 |
|-------------|-----------|------|
| `documentId` | `metadata->>'documentId'` | 文档 ID 过滤 |
| `documentVersionNumber` | `(metadata->>'documentVersionNumber')::int` | 版本号匹配（cast 为 int） |
| `splitVersion` | `metadata->>'splitVersion'` | 分块版本标识匹配 |

**注意：** `documentVersionNumber` 在 JSON 中存储为字符串，SQL 中需要 `::int` cast 才能做数值比较。`splitVersion` 是纯字符串比较。

### ScopeFilterBuilder 设计要点

1. **纯静态工具类** — 无状态，无 `@Component`，所有方法 `static`
2. **两套输出格式** — `toFilterExpression` 返回 Spring AI 类型（Dense 路径），`toSqlCondition` 返回 SQL 片段（Sparse 路径）
3. **共享常量** — metadata key 字符串在 ScopeFilterBuilder 内部定义（与 Adapter 的常量独立，避免耦合）
4. **legacy splitVersion 兼容** — version=1 时必须包含 `splitVersion = "v1"` 条件，这是历史向量的兼容逻辑

### Project Structure Notes

**新增文件：**
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/ScopeFilterBuilder.java`（package-private class）
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SqlScopeCondition.java`（package-private record）
- `src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/ScopeFilterBuilderTest.java`

**修改文件：**
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`（删除 2 个 private static 方法，改调 ScopeFilterBuilder）

**不修改文件：**
- `ChunkRetrievalPort.java`（端口接口不变）
- `AskQuestionApplicationService.java`（应用层不变）
- 所有现有测试文件

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 2.2] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-4] — ScopeFilterBuilder 架构决策
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-8] — SparseRetrievalAdapter 需求（隐含 ScopeFilterBuilder）
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java#L141-176] — 当前 buildScopeFilter + buildVersionFilter 实现
- [Source: src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapterTest.java#L183-234] — 现有 scope 过滤测试
- [Source: src/main/java/io/github/spike/myai/qa/domain/model/AskableDocumentVersion.java] — 输入类型定义
- [Source: docs/project-context.md#数据对象规范] — record + 防御性拷贝规则

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- 无重大调试问题。两个测试断言需要修正：(1) Filter.Expression 的 `toString()` 格式中 `v1` 值的引号风格与预期不同；(2) SQL `toSqlCondition` 使用参数化查询（`?` 占位符），实际值在 `params` 列表中而非 WHERE 子句字符串中。修正后 13 个新测试全部通过。

### Completion Notes List

- 创建 `SqlScopeCondition` record（package-private），含防御性校验和 params 不可变拷贝
- 创建 `ScopeFilterBuilder` 工具类（package-private），提供 `toFilterExpression` 和 `toSqlCondition` 两个静态方法
- `toFilterExpression` 完整提取自 `PgVectorChunkRetrievalAdapter.buildScopeFilter()` + `buildVersionFilter()`，行为完全一致
- `toSqlCondition` 为等价语义的 SQL 版本，使用 `metadata->>'key'` 提取 JSON 字段，`::int` cast 处理版本号比较
- 重构 `PgVectorChunkRetrievalAdapter`：删除 2 个 private static 方法和 2 个不再使用的常量，改调 `ScopeFilterBuilder`
- 移除不再需要的 `ArrayList` 和 `FilterExpressionBuilder` import
- PgVectorChunkRetrievalAdapterTest 8 个现有测试全部通过（未修改任何现有测试）
- ScopeFilterBuilderTest 13 个新测试全部通过（7 toFilterExpression + 6 toSqlCondition）

### File List

- src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SqlScopeCondition.java (新增)
- src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/ScopeFilterBuilder.java (新增)
- src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java (修改)
- src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/ScopeFilterBuilderTest.java (新增)

### Review Findings

- [x] [Review][Decision] toSqlCondition WHERE 片段缺少外层括号 — 已修复: ScopeFilterBuilder.toSqlCondition 始终包裹 `(...)`
- [x] [Review][Patch] toFilterExpression 测试未验证 legacy "v1" 独立值 — 已修复: 使用 `contains("Value[value=v1]")` 独立验证
- [x] [Review][Patch] 缺少 @author/@since Javadoc 标签 — 已修复: ScopeFilterBuilder 和 SqlScopeCondition 均已添加
- [x] [Review][Patch] 缺少 SqlScopeCondition 不可变性测试 — 已修复: 新增 `sqlScopeCondition_shouldReturnUnmodifiableParams`
- [x] [Review][Patch] 缺少混合版本 toFilterExpression 测试 — 已修复: 新增 `toFilterExpression_shouldHandleMixedVersions`
- [x] [Review][Defer] metadata 常量重复定义 — AC#5 明确要求独立定义，非缺陷
- [x] [Review][Defer] scope 列表含 null 元素时的 NPE — 与原始行为一致
- [x] [Review][Defer] `::int` 强制转换失败风险 — 数据完整性保障层面
- [x] [Review][Defer] SqlScopeCondition 当前无生产调用者 — Story 2.3 前置依赖

## Change Log

- refactor(qa): Story 2.2 — ScopeFilterBuilder 共享工具类，提取 scope 过滤逻辑为 Dense/Sparse 共用（2026-06-17）
