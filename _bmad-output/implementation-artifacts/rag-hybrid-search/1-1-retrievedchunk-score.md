---
baseline_commit: f25f043ec03aef1b3beba8880a985ea3df765941
---

# Story 1.1: RetrievedChunk 添加 score 字段

Status: done

## Story

作为开发者，
我希望检索结果包含置信度评分，
以便后续多路检索融合有统一的评分基础。

## Acceptance Criteria

1. **Given** `RetrievedChunk` record 当前只有 documentId、kbId、chunkIndex、content、sourceVersionNumber、sourceFilename、sourceUpdatedAt 7 个字段
   **When** 添加 `double score` 字段
   **Then** record 声明新增 `double score` 作为**最后一个字段**（保持简化构造器兼容）

2. **Given** 现有简化构造器 `RetrievedChunk(documentId, kbId, chunkIndex, content)`
   **When** 调用简化构造器
   **Then** score 默认值为 `0.0`（不是 -1、NaN 或 null）

3. **Given** Spring AI 1.1.2 的 `PgVectorStore` 已在 `Document.getScore()` 中计算 `1.0 - cosine_distance`
   **When** `PgVectorChunkRetrievalAdapter.toRetrievedChunk()` 映射向量检索结果
   **Then** 读取 `chunkDocument.getScore()` 并传入 `RetrievedChunk`
   **And** 当 `getScore()` 返回 null 时，score 默认为 `0.0`

4. **Given** 本 Story 完成后所有后续检索路径（Sparse BM25、RRF 融合）都需要填充 score
   **When** 定义 score 字段
   **Then** 不引入 `RetrievalMethod` 枚举 — score 语义由调用链决定（AD-10）

5. **Given** 本改动涉及 domain model
   **When** 完成实现
   **Then** 所有现有测试通过（score 新增字段不影响现有行为）

## Tasks / Subtasks

- [x] Task 1: 修改 RetrievedChunk record（AC: #1, #2, #4）
  - [x] 1.1 在 record 声明中添加 `double score` 字段（最后位置）
  - [x] 1.2 修改全参构造器：7 参 → 8 参，末尾加 `double score`
  - [x] 1.3 修改简化构造器 `RetrievedChunk(documentId, kbId, chunkIndex, content)`：内部调用 `this(..., 0.0)` 确保 score 默认 0.0
  - [x] 1.4 更新 Javadoc：`@param score` 说明语义（Dense=cosine similarity, Sparse=ts_rank, RRF=fusion score）

- [x] Task 2: 修改 PgVectorChunkRetrievalAdapter.toRetrievedChunk()（AC: #3）
  - [x] 2.1 在 `toRetrievedChunk()` 方法中读取 `chunkDocument.getScore()`
  - [x] 2.2 null 安全处理：`getScore() != null ? getScore() : 0.0`
  - [x] 2.3 将 score 值传入 `new RetrievedChunk(...)` 构造器

- [x] Task 3: 新增 RetrievedChunkTest 单元测试（AC: #1, #2, #5）
  - [x] 3.1 测试简化构造器 score 默认值为 0.0
  - [x] 3.2 测试全参构造器正确设置 score 值
  - [x] 3.3 测试 score 为 0.0 时不影响其他字段

- [x] Task 4: 更新 PgVectorChunkRetrievalAdapterTest（AC: #3, #5）
  - [x] 4.1 在现有 `similaritySearch_shouldMapRetrievedChunks` 测试中验证 score 字段映射
  - [x] 4.2 新增测试：Mock Document.getScore() 返回 null 时，score 应为 0.0
  - [x] 4.3 新增测试：Mock Document.getScore() 返回具体值时，score 正确传递

- [x] Task 5: 验证现有测试不回归（AC: #5）
  - [x] 5.1 运行 `mvn test "-Dtest=AskQuestionApplicationServiceTest"` 确认通过（6 tests, 0 failures）
  - [x] 5.2 运行 `mvn test "-Dtest=PgVectorChunkRetrievalAdapterTest"` 确认通过（6 tests, 0 failures）
  - [x] 5.3 确认 AskQuestionApplicationServiceTest 中所有现有 `RetrievedChunk` 构造调用不受影响

## Dev Notes

### 当前 RetrievedChunk record 状态

```java
// 文件: src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java
// 42 行，7 个字段，2 个构造器
public record RetrievedChunk(
        String documentId,
        String kbId,
        int chunkIndex,
        String content,
        Integer sourceVersionNumber,
        String sourceFilename,
        Instant sourceUpdatedAt) {

    // 简化构造器：score 默认 0.0
    public RetrievedChunk(String documentId, String kbId, int chunkIndex, String content) {
        this(documentId, kbId, chunkIndex, content, null, null, null);
    }
}
```

**改动要点：**
- `double score` 字段添加在 `sourceUpdatedAt` **之后**（record 末尾），保持现有字段顺序不变
- 简化构造器内部改为 `this(documentId, kbId, chunkIndex, content, null, null, null, 0.0)`
- 现有 4 参数构造器的调用方（AskQuestionApplicationServiceTest 等）无需任何修改

### 当前 toRetrievedChunk() 状态

```java
// 文件: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java
// ~第 184 行，toRetrievedChunk 方法
private RetrievedChunk toRetrievedChunk(org.springframework.ai.document.Document chunkDocument) {
    // ... 解析 metadata ...
    return new RetrievedChunk(
            documentId, kbId, chunkIndex, content,
            sourceVersionNumber, sourceFilename, sourceUpdatedAt);
    // ⚠️ 没有读取 chunkDocument.getScore() — 本 Story 要修复
}
```

**改动要点：**
- 在 `return new RetrievedChunk(...)` 前，提取 score：`double score = chunkDocument.getScore() != null ? chunkDocument.getScore() : 0.0;`
- 将 `score` 作为第 8 个参数传入构造器

### Spring AI 1.1.2 score 机制（关键上下文）

**PgVectorStore 源码**（`spring-ai-pgvector-store-1.1.2`）：

```java
// PgVectorStore 内部 DocumentRowMapper.mapRow()
return Document.builder()
    .id(id).text(content).metadata(metadata)
    .score(1.0 - distance)  // ← cosine distance → similarity
    .build();
```

| 概念 | 说明 |
|------|------|
| `Document.getScore()` | 返回 `1.0 - cosine_distance`，即 cosine similarity |
| 值域 | [-1.0, 1.0]，1.0 = 完全匹配，0.0 = 正交 |
| null 情况 | `getScore()` 声明为 `@Nullable Double`，理论上调 similaritySearch 后不为 null，但需防御 |
| metadata 中的 `"distance"` 键 | 存储原始 cosine distance，**不使用** — 直接用 `getScore()` 更安全 |

**验证假设 A-1 已确认：** Spring AI 1.1.2 的 `Document.getScore()` 返回的值可直接用于 cosine similarity 计算，不需要自定义 SQL 查询。

### 架构约束检查

| 约束 | 状态 |
|------|------|
| domain 层零框架注解 | ✅ RetrievedChunk 是纯 Java record，改后仍无框架依赖 |
| 不引入 RetrievalMethod 枚举 | ✅ score 语义由调用链决定（AD-10） |
| score 默认值 0.0 | ✅ 简化构造器 `this(...)` 传 0.0 |
| 不修改 ChunkRetrievalPort 接口 | ✅ 只改 domain model + adapter 内部映射 |
| record 防御性拷贝 | ✅ score 是 `double` 基本类型，无此问题 |

### 现有代码调用方影响分析

| 调用方 | 构造器使用 | 影响 |
|--------|-----------|------|
| `PgVectorChunkRetrievalAdapter.toRetrievedChunk()` | 7 参构造器 | **需修改** — 新增 score 参数 |
| `AskQuestionApplicationServiceTest` | 7 参构造器 | **需修改** — 在构造调用末尾加 `0.0` 或显式 score 值 |
| 其他测试中的 `new RetrievedChunk(...)` | 7 参或 4 参 | 4 参无影响；7 参需加 score 参数 |

**注意：** 由于 Java record 的全参构造器是编译器生成的，添加新字段后原 7 参调用会编译失败。所有使用 7 参构造器的地方必须改为 8 参。4 参简化构造器内部已更新，调用方无需改动。

### 测试规范

- JUnit 5 + Mockito，纯单元测试（不启动 Spring 上下文）
- 测试类 package-private，与被测类同 package
- `@Test` + `@DisplayName("中文业务描述")`
- 方法命名 `method_shouldExpectedBehavior_whenCondition`
- 每个测试方法只断言一个行为

### Project Structure Notes

- `RetrievedChunk.java` 路径：`src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java`
- `PgVectorChunkRetrievalAdapter.java` 路径：`src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`
- 新增测试 `RetrievedChunkTest.java` 路径：`src/test/java/io/github/spike/myai/qa/domain/model/RetrievedChunkTest.java`
- 修改测试 `PgVectorChunkRetrievalAdapterTest.java` 路径：`src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapterTest.java`
- 修改测试 `AskQuestionApplicationServiceTest.java` 路径：`src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java`

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 1.1] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#score 字段处理] — score 语义表
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-10] — 不引入 RetrievalMethod 枚举
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-1] — 功能需求
- [Source: docs/project-context.md#Java — Record 与数据对象] — record 编码规范
- [Source: spring-ai-pgvector-store-1.1.2 PgVectorStore] — `score = 1.0 - distance`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- RetrievedChunk record 新增 `double score` 字段（末尾位置），简化构造器默认 0.0
- PgVectorChunkRetrievalAdapter.toRetrievedChunk() 读取 `Document.getScore()` 并 null 安全处理
- AskQuestionApplicationServiceTest 所有 7 参构造器调用已更新为 8 参（末尾加 `0.0`）
- 新增 RetrievedChunkTest（3 个测试）覆盖简化构造器默认值、全参构造器、字段独立性
- PgVectorChunkRetrievalAdapterTest 新增 2 个 score 测试 + 现有测试增加 score 断言
- 全部 15 个测试通过（AskQuestion: 6, Adapter: 6, RetrievedChunk: 3）

### File List

- src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java (modified)
- src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java (modified)
- src/test/java/io/github/spike/myai/qa/domain/model/RetrievedChunkTest.java (new)
- src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapterTest.java (modified)
- src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java (modified)

## Change Log

- feat(qa): RetrievedChunk 添加 score 字段，PgVectorChunkRetrievalAdapter 读取 Document.getScore()（2026-06-17）

### Review Findings (2026-06-17)

#### decision_needed

- [x] [Review][Decision] **D1: score 字段无值域校验** → 选 B：仅拒绝 NaN/Infinity（`Double.isFinite()`），不限制值域范围
- [x] [Review][Decision] **D2: score 是否应传递到 AskReferenceResult 返回前端** → 选 A：不传递，score 仅内部检索管线使用

#### patch

- [x] [Review][Patch] **P1: adapter 中 NaN/Infinity score 未防御** [PgVectorChunkRetrievalAdapter.java:201-202]
- [x] [Review][Patch] **P2: record_shouldPreserveOtherFields 包含 8 个断言违反单断言原则** [RetrievedChunkTest.java:34]
- [x] [Review][Patch] **P3: Javadoc 缺少 @author spike + @since 1.0.0** [RetrievedChunk.java]
- [x] [Review][Patch] **P4: 缺少 score 边界值测试（-1.0 / 1.0 / NaN / Infinity）** [RetrievedChunkTest.java, PgVectorChunkRetrievalAdapterTest.java]
- [x] [Review][Patch] **P5: assertEquals(0.0, ..., 0.0001) 对精确值 0.0 使用了不必要的 delta** [PgVectorChunkRetrievalAdapterTest.java:108]
- [x] [Review][Patch] **P6: TestCompile.java 死代码文件（无 @Test，无断言）** [src/test/java/test/TestCompile.java]
- [x] [Review][Patch] **P7: assertEquals(null, xxx) 应使用 assertNull() 以获得更清晰的失败信息** [RetrievedChunkTest.java:40-42]

#### defer

- [x] [Review][Defer] **W1: record 添加 score 字段改变 equals/hashCode** [RetrievedChunk.java] — 添加字段到 record 必然改变自动生成的 equals/hashCode，如外部用 Set/Map 存储 RetrievedChunk 会产生行为差异，属预存风险非当前改动引入
- [x] [Review][Defer] **W2: score 0.0 默认值混淆"未计算"与"零置信度"语义** [RetrievedChunk.java:42] — Spec 明确要求默认值 0.0，如改需 spec 更新
