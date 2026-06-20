---
baseline_commit: 6bd4a1d
---

# Story 2.4: HybridChunkRetrievalAdapter RRF 融合与应用层切换

Status: done

## Story

作为用户，
我希望系统同时利用语义和关键词两路检索并融合结果，
以便精确术语和语义相关的内容都能被召回，检索质量提升（Recall@5 ≥ 0.70）。

## Acceptance Criteria

1. **Given** Dense（`PgVectorChunkRetrievalAdapter`）和 Sparse（`SparseRetrievalAdapter`）两路检索已就位
   **When** 新增 `HybridChunkRetrievalAdapter`（`qa/infrastructure/retrieval/` 包下）
   **Then** 实现 `ChunkRetrievalPort` 接口的 `similaritySearch` 方法（不修改端口接口，AD-1）

2. **Given** `HybridChunkRetrievalAdapter` 是 Hybrid 检索路径的编排器
   **When** 执行 `similaritySearch()`
   **Then** 内部编排：① 并行调 Dense + Sparse → ② RRF 融合 → ③ 返回 `List<RetrievedChunk>`

3. **Given** RRF 融合算法
   **When** 计算每个 chunk 的 RRF 分数
   **Then** 公式：`score = Σ weight_i / (k + rank_i)`，`k=60`（RRF_K），`DENSE_WEIGHT=0.5`，`SPARSE_WEIGHT=0.5`
   **And** 常量定义为 `private static final`（AD-9），不放配置文件
   **And** 同一 chunk 双路命中时 RRF 分数叠加
   **And** 结果按 RRF 分降序排列

4. **Given** Dense/Sparse 并行执行（NFR-1：延迟增量 ≤200ms，AD-3）
   **When** 调用两路检索
   **Then** 使用 `CompletableFuture.supplyAsync()` 并行执行
   **And** 总延迟 = max(Dense延迟, Sparse延迟) + RRF计算（<1ms）

5. **Given** 降级策略（AD-3）
   **When** 单路检索失败
   **Then** 降级到另一路结果（`exceptionally()` 返回空 list，日志 WARN）
   **And** 两路都失败 → 返回空 list
   **And** adapter 层不抛异常给调用方

6. **Given** 类标注 `@Primary`
   **When** Spring 容器注入 `ChunkRetrievalPort`
   **Then** `HybridChunkRetrievalAdapter` 替代 `PgVectorChunkRetrievalAdapter` 作为默认实现
   **And** 现有 `PgVectorChunkRetrievalAdapter` 和 `SparseRetrievalAdapter` 保留 `@Component`（EvalRunner 需要注入具体类）

7. **Given** `AskQuestionApplicationService` 不需要任何代码改动（AD-2：RRF 对调用方透明）
   **When** Hybrid 适配器注册为 @Primary
   **Then** ApplicationService 代码不变，只换注入的实现者
   **And** CHITCHAT 请求仍跳过检索（Epic 1 Story 1.6 行为不变）

8. **Given** 测试覆盖
   **When** 编写 `HybridChunkRetrievalAdapterTest`
   **Then** 覆盖以下场景：
   - 双路命中时 RRF 分数叠加正确（手工验证 1-2 个 case）
   - 仅 Dense 命中时返回 Dense-only 结果（score 不变）
   - 仅 Sparse 命中时返回 Sparse-only 结果（score 不变）
   - 两路都返回空时结果为空列表
   - Dense 失败时降级到 Sparse-only 结果
   - Sparse 失败时降级到 Dense-only 结果
   - 两路都失败时返回空列表
   - 空查询快速返回空列表
   - 结果按 RRF 分降序排列
   - 同一 chunk 双路命中时分数正确叠加

9. **Given** 空查询防御
   **When** `question` 为 null 或 blank
   **Then** 返回空列表 `List.of()`（不触发并行检索，不抛异常）

10. **Given** 不修改 `ChunkRetrievalPort` 接口（AD-1）
    **When** 实现完成
    **Then** `ChunkRetrievalPort.java` 无任何变更
    **And** 现有 `PgVectorChunkRetrievalAdapterTest` 和 `SparseRetrievalAdapterTest` 所有测试不受影响

## Tasks / Subtasks

- [x] Task 1: 新增 `HybridChunkRetrievalAdapter` 主类（AC: #1, #2, #3, #4, #5, #6, #9）
  - [x] 1.1 在 `qa/infrastructure/retrieval/` 包下创建 `HybridChunkRetrievalAdapter.java`
  - [x] 1.2 类标注 `@Primary` + `@Component`，注入 `PgVectorChunkRetrievalAdapter` 和 `SparseRetrievalAdapter`
  - [x] 1.3 定义 RRF 常量：`RRF_K = 60`、`DENSE_WEIGHT = 0.5`、`SPARSE_WEIGHT = 0.5`（private static final）
  - [x] 1.4 实现 `ChunkRetrievalPort` 的两个 `similaritySearch` 方法重载
  - [x] 1.5 空查询快速返回 `List.of()`（null/blank 防御，与 Dense/Sparse 行为一致）
  - [x] 1.6 `similaritySearch(question, topK, scope)` 内部编排：并行调 Dense + Sparse → RRF 融合 → 截取 topK → 返回

- [x] Task 2: 实现并行执行与降级策略（AC: #4, #5）
  - [x] 2.1 使用 `CompletableFuture.supplyAsync(() -> denseAdapter.similaritySearch(...))` 启动 Dense 并行
  - [x] 2.2 使用 `CompletableFuture.supplyAsync(() -> sparseAdapter.similaritySearch(...))` 启动 Sparse 并行
  - [x] 2.3 使用 `CompletableFuture.allOf(denseFuture, sparseFuture).join()` 等待两路完成
  - [x] 2.4 Dense/Sparse 各自 `.exceptionally(ex -> { log.warn(...); return List.of(); })` 降级处理
  - [x] 2.5 两路都返回空 → 最终返回空 list

- [x] Task 3: 实现 RRF 融合算法（AC: #3）
  - [x] 3.1 收集 Dense 结果（按原序 rank=1,2,...）和 Sparse 结果（按原序 rank=1,2,...）
  - [x] 3.2 遍历 Dense 结果：每个 chunk 的 RRF 分 += `DENSE_WEIGHT / (RRF_K + rank)`
  - [x] 3.3 遍历 Sparse 结果：每个 chunk 的 RRF 分 += `SPARSE_WEIGHT / (RRF_K + rank)`
  - [x] 3.4 使用 `LinkedHashMap<String, RetrievedChunk>` 按 chunk 的 `documentId + chunkIndex` 作为 composite key 合并双路结果
  - [x] 3.5 合并时：首次出现的 chunk 保留原始字段（content 等），仅累加 RRF 分数到 score 字段
  - [x] 3.6 同一 chunk 双路命中时更新 score 为叠加后的 RRF 分
  - [x] 3.7 按 RRF 分降序排列（`Comparator.comparingDouble(RetrievedChunk::score).reversed()`）
  - [x] 3.8 截取前 topK 条返回

- [x] Task 4: 新增 `HybridChunkRetrievalAdapterTest`（AC: #8）
  - [x] 4.1 创建 `HybridChunkRetrievalAdapterTest.java`（与被测类同包 package-private）
  - [x] 4.2 Mock `PgVectorChunkRetrievalAdapter` 和 `SparseRetrievalAdapter`
  - [x] 4.3 测试双路命中 RRF 分数叠加（核心 case：验证公式 `1/(60+rank)` 的权重计算）
  - [x] 4.4 测试仅 Dense 命中（Sparse 返回空）
  - [x] 4.5 测试仅 Sparse 命中（Dense 返回空）
  - [x] 4.6 测试两路都返回空
  - [x] 4.7 测试 Dense 失败降级（mock 抛异常）
  - [x] 4.8 测试 Sparse 失败降级
  - [x] 4.9 测试两路都失败 → 空列表
  - [x] 4.10 测试空查询快速返回（null + blank）
  - [x] 4.11 测试结果降序排列
  - [x] 4.12 测试同一 chunk 双路命中分数叠加

- [x] Task 5: 集成验证（AC: #7, #10）
  - [x] 5.1 运行 `mvn test "-Dtest=HybridChunkRetrievalAdapterTest"` — 15 个测试全部通过
  - [x] 5.2 运行 `mvn test "-Dtest=AskQuestionApplicationServiceTest"` — 11 个现有测试全部通过（ApplicationService 无代码改动）
  - [x] 5.3 运行 `mvn test "-Dtest=PgVectorChunkRetrievalAdapterTest"` — 8 个现有测试全部通过
  - [x] 5.4 运行 `mvn test "-Dtest=SparseRetrievalAdapterTest"` — 13 个现有测试全部通过
  - [x] 5.5 确认 `ChunkRetrievalPort.java` 无任何变更（git status 确认）
  - [x] 5.6 确认 `AskQuestionApplicationService.java` 无任何变更（git status 确认）

## Dev Notes

### 前置故事上下文

- **Story 1.1-1.6（done）**：Epic 1 全部完成 — `RetrievedChunk.score`、`RerankingPort`、`QaRetrievalProperties`、`QueryType`、`RuleBasedQueryClassifier`、CHITCHAT 拦截均已就位
- **Story 2.1（done）**：Flyway V9 迁移已执行 — `content_tsv tsvector` 列和 `idx_vector_store_fts` GIN 索引已创建
- **Story 2.2（done）**：`ScopeFilterBuilder` 已提取 — `toFilterExpression()`（Dense 路径）和 `toSqlCondition()`（Sparse 路径）两个静态方法可用，`SqlScopeCondition` record 已定义
- **Story 2.3（done）**：`SparseRetrievalAdapter` 已实现 — BM25 检索路径就位，实现 `ChunkRetrievalPort`，使用 `JdbcTemplate` 直接 SQL + `ts_rank` 评分

本 Story 是 Epic 2 的最后一个 Story，也是 RAG Hybrid Search 的核心 — RRF 融合。它是整个 Epic 的目标产出物。完成后系统将默认使用 Hybrid Search，Dense-only 和 Sparse-only 路径保留作为 EvalRunner 对比用。

### 当前 ChunkRetrievalPort 接口（精确签名）

**文件：** `src/main/java/io/github/spike/myai/qa/domain/port/ChunkRetrievalPort.java`

```java
public interface ChunkRetrievalPort {
    List<RetrievedChunk> similaritySearch(String question, int topK);
    List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope);
}
```

**关键约束（AD-1）：** 不修改此接口。`HybridChunkRetrievalAdapter` 作为新的实现者，通过 `@Primary` 注解替代 `PgVectorChunkRetrievalAdapter` 成为默认注入。

### 当前 RetrievedChunk 构造器

**文件：** `src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java`

```java
// 全字段构造器
new RetrievedChunk(documentId, kbId, chunkIndex, content,
    sourceVersionNumber, sourceFilename, sourceUpdatedAt, score);

// 简化构造器（score 默认 0.0，版本元数据为 null）
new RetrievedChunk(documentId, kbId, chunkIndex, content);
```

**RRF 融合时注意：** 同一 chunk 双路命中时需要合并。由于 `RetrievedChunk` 是 record（不可变），合并策略是创建新 RetrievedChunk 或在 RRF 阶段只追踪 score。推荐方案：用 `Map<String, Double>` 追踪 RRF 累积分，最终从某一路结果中取 content 字段并覆写 score。

### PgVectorChunkRetrievalAdapter 当前签名（Dense 路径）

**文件：** `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`

```java
@Component
public class PgVectorChunkRetrievalAdapter implements ChunkRetrievalPort {
    // 构造器：注入 VectorStore
    public PgVectorChunkRetrievalAdapter(VectorStore vectorStore) { ... }

    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK) { ... }

    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK,
            List<AskableDocumentVersion> scope) { ... }
}
```

**注入方式：** `HybridChunkRetrievalAdapter` 通过构造器注入 `PgVectorChunkRetrievalAdapter`（具体类，非端口接口），因为同层 adapter 组合。

### SparseRetrievalAdapter 当前签名（Sparse 路径）

**文件：** `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapter.java`

```java
@Component
public class SparseRetrievalAdapter implements ChunkRetrievalPort {
    // 构造器：注入 JdbcTemplate
    public SparseRetrievalAdapter(JdbcTemplate jdbcTemplate) { ... }

    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK) { ... }

    @Override
    public List<RetrievedChunk> similaritySearch(String question, int topK,
            List<AskableDocumentVersion> scope) { ... }
}
```

**注入方式：** `HybridChunkRetrievalAdapter` 通过构造器注入 `SparseRetrievalAdapter`（具体类）。

### AskQuestionApplicationService 当前行为（不需要修改）

**文件：** `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`

**关键行为（均不改动）：**
- 注入 `ChunkRetrievalPort chunkRetrievalPort`（Spring 将自动注入 `@Primary` 的 Hybrid 实现）
- CHITCHAT 查询跳过检索（`queryType == QueryType.CHITCHAT` → 直接调 LLM）
- 非 CHITCHAT 查询：计算 `retrievalTopK` → 调 `chunkRetrievalPort.similaritySearch(question, retrievalTopK, scope)` → 截取 topK → 重排序 → 生成回答
- `@Primary` 切换对 ApplicationService 完全透明（AD-2）

### RRF 算法精确实现指南

**Reciprocal Rank Fusion (RRF)** 是一种标准的多路检索融合算法，论文参考：Cormack et al., 2009。

**公式：**
```
score(d) = Σ_i weight_i / (k + rank_i(d))
```

其中：
- `d` = 某个 chunk（通过 documentId + chunkIndex 唯一标识）
- `i` = 检索路径索引（Dense=0, Sparse=1）
- `weight_i` = 路径权重（DENSE_WEIGHT=0.5, SPARSE_WEIGHT=0.5）
- `k` = 平滑常数（RRF_K=60，标准值）
- `rank_i(d)` = chunk d 在第 i 路结果中的排名（1-based）

**常量定义（AD-9，不可协商）：**
```java
private static final int RRF_K = 60;
private static final double DENSE_WEIGHT = 0.5;
private static final double SPARSE_WEIGHT = 0.5;
```

**排名规则：** 各路结果按 adapter 返回顺序排名（rank=1,2,3,...），即 Dense 路按 cosine similarity 降序排名，Sparse 路按 ts_rank 降序排名。adapter 返回的 list 顺序即为排名顺序。

**计算示例：**
```
假设 Dense 返回 [chunkA(score=0.9), chunkB(score=0.7), chunkC(score=0.5)]
假设 Sparse 返回 [chunkB(rank_score=2.1), chunkA(rank_score=1.5), chunkD(rank_score=0.8)]

chunkA: RRF = 0.5/(60+1) + 0.5/(60+2) = 0.008197 + 0.008065 = 0.016262
chunkB: RRF = 0.5/(60+2) + 0.5/(60+1) = 0.008065 + 0.008197 = 0.016262
chunkC: RRF = 0.5/(60+3)              = 0.007937
chunkD: RRF =              0.5/(60+3)  = 0.007937

结果：[chunkA(0.016262), chunkB(0.016262), chunkC(0.007937), chunkD(0.007937)]
```

**注意：** RRF 分数量级很小（~0.01），这是正常的。RRF 分只用于排序，不做绝对值比较。

### CompletableFuture 并行执行实现指南

**文件内结构：**

```java
@Override
public List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope) {
    if (question == null || question.isBlank()) {
        return List.of();
    }

    int effectiveTopK = Math.max(1, topK);

    // 并行启动两路检索
    CompletableFuture<List<RetrievedChunk>> denseFuture = CompletableFuture
            .supplyAsync(() -> denseAdapter.similaritySearch(question, effectiveTopK, scope))
            .exceptionally(ex -> {
                log.warn("Dense retrieval failed, degrading to sparse-only", ex);
                return List.of();
            });

    CompletableFuture<List<RetrievedChunk>> sparseFuture = CompletableFuture
            .supplyAsync(() -> sparseAdapter.similaritySearch(question, effectiveTopK, scope))
            .exceptionally(ex -> {
                log.warn("Sparse retrieval failed, degrading to dense-only", ex);
                return List.of();
            });

    // 等待两路完成
    CompletableFuture.allOf(denseFuture, sparseFuture).join();

    List<RetrievedChunk> denseResults = denseFuture.join();
    List<RetrievedChunk> sparseResults = sparseFuture.join();

    // RRF 融合
    return fuseByRrf(denseResults, sparseResults, effectiveTopK);
}
```

**Java 21 Virtual Threads：** `CompletableFuture.supplyAsync()` 默认使用 `ForkJoinPool.commonPool()`。在 Spring Boot 3.5.8 + Java 21 中，如果启用了 `spring.threads.virtual.enabled=true`，则会使用 Virtual Threads。但即使未启用，Dense/Sparse 两路并行也只需要 2 个线程，commonPool 足够。

**禁止：** 在 adapter 层抛出 `RuntimeException` 给调用方。adapter 内部消化异常，保证端口契约不被破坏。

### RRF 融合实现细节

**Composite Key 定义：** 同一 chunk 在 Dense 和 Sparse 路径中返回时，需要通过唯一标识匹配。推荐使用 `documentId + "#" + chunkIndex` 作为 key。

```java
private static String chunkKey(RetrievedChunk chunk) {
    return chunk.documentId() + "#" + chunk.chunkIndex();
}
```

**融合算法（核心方法）：**

```java
private List<RetrievedChunk> fuseByRrf(
        List<RetrievedChunk> denseResults,
        List<RetrievedChunk> sparseResults,
        int topK) {

    // 1. 累积 RRF 分数
    Map<String, Double> rrfScores = new LinkedHashMap<>();
    Map<String, RetrievedChunk> representativeChunks = new LinkedHashMap<>();

    for (int rank = 0; rank < denseResults.size(); rank++) {
        RetrievedChunk chunk = denseResults.get(rank);
        String key = chunkKey(chunk);
        rrfScores.merge(key, DENSE_WEIGHT / (RRF_K + rank + 1), Double::sum);
        representativeChunks.putIfAbsent(key, chunk);
    }

    for (int rank = 0; rank < sparseResults.size(); rank++) {
        RetrievedChunk chunk = sparseResults.get(rank);
        String key = chunkKey(chunk);
        rrfScores.merge(key, SPARSE_WEIGHT / (RRF_K + rank + 1), Double::sum);
        representativeChunks.putIfAbsent(key, chunk);
    }

    // 2. 构建最终结果（覆写 score 为 RRF 分数）
    return representativeChunks.entrySet().stream()
            .sorted((a, b) -> Double.compare(
                    rrfScores.get(b.getKey()), rrfScores.get(a.getKey())))
            .limit(topK)
            .map(entry -> {
                RetrievedChunk original = entry.getValue();
                double rrfScore = rrfScores.get(entry.getKey());
                return new RetrievedChunk(
                        original.documentId(),
                        original.kbId(),
                        original.chunkIndex(),
                        original.content(),
                        original.sourceVersionNumber(),
                        original.sourceFilename(),
                        original.sourceUpdatedAt(),
                        rrfScore);
            })
            .toList();
}
```

**关键点：**
- `rank + 1`：RRF 的 rank 是 1-based（第一名为 rank=1）
- `putIfAbsent`：首次出现的 chunk 保留原始 content 等字段
- `merge`：累加 RRF 分数
- `RetrievedChunk` 是 record（不可变），所以需要用构造器创建新的来设置 RRF score

### @Primary 注解与 Bean 替代机制

**Spring 的 `@Primary` 行为：** 当存在多个 `ChunkRetrievalPort` 实现时（`PgVectorChunkRetrievalAdapter`、`SparseRetrievalAdapter`、`HybridChunkRetrievalAdapter`），`@Primary` 标注的 bean 作为默认注入目标。

**当前状态：**
- `PgVectorChunkRetrievalAdapter` 标注 `@Component` — 不改
- `SparseRetrievalAdapter` 标注 `@Component` — 不改
- `HybridChunkRetrievalAdapter` 标注 `@Primary` + `@Component` — 新增

**结果：** `AskQuestionApplicationService` 的 `ChunkRetrievalPort chunkRetrievalPort` 字段自动注入 `HybridChunkRetrievalAdapter`，ApplicationService 代码零改动。

**EvalRunner 对比需求：** Story 3.1 的 EvalRunner 需要注入具体 adapter 类做 Dense vs Hybrid 对比，因此 `PgVectorChunkRetrievalAdapter` 和 `SparseRetrievalAdapter` 必须保留 `@Component`。

### 架构约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| AD-1：不改 ChunkRetrievalPort 接口 | ✅ | 新增实现者，不改端口 |
| AD-2：RRF 对调用方透明 | ✅ | ApplicationService 零改动 |
| AD-3：Dense/Sparse 并行 + 降级 | ✅ | CompletableFuture 并行 + exceptionally 降级 |
| AD-9：RRF 常量 private static final | ✅ | 不放配置文件 |
| AD-10：score 语义由调用链决定 | ✅ | score = RRF 融合分，不引入 RetrievalMethod 枚举 |
| NFR-1：延迟增量 ≤ 200ms | ✅ | 并行执行，增量 ≈ max(Dense, Sparse) - min(Dense, Sparse) + RRF(<1ms) |
| NFR-2：零新外部依赖 | ✅ | CompletableFuture + Map + Stream，纯 JDK |
| NFR-3：六边形合规 | ✅ | adapter 在 infrastructure 层，实现 domain port |
| adapter 间禁止互相引用 | ✅ | HybridChunkRetrievalAdapter 内部组合（同层 adapter，架构文档明确允许） |

### Project Structure Notes

**新增文件：**
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapter.java`（`@Primary` + `@Component` class）
- `src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapterTest.java`

**修改文件：** 无（AD-2：ApplicationService 不改，AD-1：端口不改）

**不修改文件：**
- `ChunkRetrievalPort.java`（端口接口不变）
- `AskQuestionApplicationService.java`（应用层不变，@Primary 自动切换注入）
- `PgVectorChunkRetrievalAdapter.java`（Dense 路径不变）
- `SparseRetrievalAdapter.java`（Sparse 路径不变）
- `ScopeFilterBuilder.java`（共享工具不变）

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 2.4] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-1,AD-2,AD-3] — 不改端口 + RRF 透明 + 并行策略
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#RRF 算法常量] — 常量定义规则
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#CompletableFuture 异常处理] — 降级策略
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-9] — Hybrid Search RRF 融合需求
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-10] — 应用层切换到 Hybrid Search
- [Source: src/main/java/io/github/spike/myai/qa/domain/port/ChunkRetrievalPort.java] — 端口接口定义
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java] — Dense 路径实现
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapter.java] — Sparse 路径实现
- [Source: src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java] — 应用层服务（不改）
- [Source: src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java] — 现有 10 个测试
- [Source: docs/project-context.md#CompletableFuture] — CompletableFuture 必须有 .exceptionally()
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#实施顺序] — Phase 3 Step 10-11（FR-9, FR-10）

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- 两个测试初版失败：(1) `AskableDocumentVersion` 构造器校验 `sourceUpdatedAt` 不允许 null，修正为传入合法 Instant；(2) `similaritySearch(question, topK)` 重载内部委托到 3 参数版本（scope=null），测试的 verify 需匹配 3 参数签名而非 2 参数签名。修正后 15/15 通过。

### Completion Notes List

- 创建 `HybridChunkRetrievalAdapter`（`@Primary` + `@Component`），实现 `ChunkRetrievalPort` 接口的两个 `similaritySearch` 方法重载
- 注入 `PgVectorChunkRetrievalAdapter`（Dense）和 `SparseRetrievalAdapter`（Sparse），同层 adapter 组合
- RRF 融合算法：`score = Σ weight/(k+rank)`，k=60，等权重 0.5/0.5，private static final 常量（AD-9）
- `CompletableFuture.supplyAsync()` 并行执行 Dense + Sparse 两路检索，`allOf().join()` 等待完成
- `exceptionally()` 降级策略：单路失败降级到另一路结果，日志 WARN，不抛异常；两路都失败返回空 list
- composite key `documentId + "#" + chunkIndex` 匹配双路同一 chunk，RRF 分数叠加
- `@Primary` 替代 `PgVectorChunkRetrievalAdapter` 作为默认 `ChunkRetrievalPort` 实现
- `AskQuestionApplicationService` 零改动（AD-2），CHITCHAT 仍跳过检索
- `HybridChunkRetrievalAdapterTest` 15 个测试全部通过：双路命中叠加、单路降级、双路失败、空查询（null/blank）、降序排列、topK 截断、scope 传递、topK 下限保护、委托验证
- `AskQuestionApplicationServiceTest` 11 个现有测试全部通过（无回归）
- `PgVectorChunkRetrievalAdapterTest` 8 个现有测试全部通过（无回归）
- `SparseRetrievalAdapterTest` 13 个现有测试全部通过（无回归）
- `ChunkRetrievalPort.java` 无任何变更（AD-1 约束满足）
- `AskQuestionApplicationService.java` 无任何变更（AD-2 约束满足）

### File List

- src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapter.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapterTest.java (新增)

## Change Log

- feat(qa): Story 2.4 — HybridChunkRetrievalAdapter RRF 融合与应用层切换（2026-06-18）
- Code Review (2026-06-18): 15+32 tests pass, no blockers. Review notes: L1 redundant join(), L2 misleading test comment, L3 weak sort assertion, L4 import style, M1 ForkJoinPool under high concurrency — all deferred as future optimizations.
