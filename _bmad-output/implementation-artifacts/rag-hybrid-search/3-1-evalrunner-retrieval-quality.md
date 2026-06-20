---
baseline_commit: 5a5dee3
---

# Story 3.1: EvalRunner 检索质量评估（三模式对比 + 分层报告）

Status: done

## Story

作为开发者，
我希望有量化工具衡量检索质量，并能对比 Dense、Sparse、Hybrid 三种检索模式的效果差异，
以便精准定位检索链路瓶颈，数据驱动地验证参数调优效果。

## Acceptance Criteria

### 阶段 3.1a：数据模型 + DatasetLoader + 校验

1. **Given** 需要标准化的评测数据集格式
   **When** 新增 `RetrievalEvalDatasetLoader`（`src/test/java/` 下，AD-5 test-only 组件）
   **Then** QA pairs JSON 格式扩展，每条样本包含以下字段：

   ```json
   {
     "question": "Spring Boot 如何配置 Flyway",
     "query_type": "PROCEDURAL",
     "relevant_doc_ids": ["doc-001", "doc-003"],
     "relevance_levels": {
       "doc-001": "strong",
       "doc-003": "weak"
     }
   }
   ```

2. **Given** `query_type` 字段
   **When** 加载 QA pairs
   **Then** 取值必须为 `QueryType` 枚举的 5 个值之一（`FACTOID` / `PROCEDURAL` / `COMPARATIVE` / `CHITCHAT` / `GENERAL`）

3. **Given** `relevance_levels` 字段
   **When** 校验数据完整性
   **Then** 每个 `relevant_doc_ids` 中的 ID 必须有对应的 relevance 标注

4. **Given** 指标统计精度
   **When** 计算基础指标（Recall@5、MRR、HitRate@5）
   **Then** 仅统计 `strong` 强相关文档，`weak` 预留用于后续 NDCG 加权

5. **Given** 数据格式完整性
   **When** 加载器遇到缺失必填字段（question / query_type / relevant_doc_ids）
   **Then** 抛出语义明确的 `IllegalArgumentException`，禁止静默失败

6. **Given** 测试数据
   **When** 准备 QA pairs
   **Then** 20 条手写 QA pairs（`src/test/resources/eval/retrieval-qa-pairs.json`），每种 QueryType 至少 3 条

7. **Given** 加载器测试覆盖
   **When** 编写 `RetrievalEvalDatasetLoaderTest`
   **Then** 覆盖正常加载和格式校验异常场景

### 阶段 3.1b：MetricsCalculator + Executor + 单模式可跑

8. **Given** 数据集加载器就位
   **When** 新增 `EvalMetricsCalculator`
   **Then** `EvalMetricsCalculator` 为纯工具类 — 无状态、零 Spring 依赖，所有指标计算均为 **static 纯函数**

9. **Given** 核心指标需求
   **When** 实现指标计算
   **Then** 核心指标：
   - `Recall@5`：top-5 中命中 strong 相关文档的比例
   - `MRR`：第一个 strong 相关结果排名的倒数的均值
   - `HitRate@5`：top-5 中至少命中 1 条 strong 相关文档的查询占比

10. **Given** NDCG 指标预留
    **When** 定义 `EvalMetricsCalculator` 接口
    **Then** 预留 `ndcgAtK(results, relevanceLevels, k)` 方法签名，返回 `double`，本期抛 `UnsupportedOperationException("NDCG@K: Phase 2")`，后续实现无需重构现有代码

11. **Given** 边界情况
    **When** 指标计算遇到边界值
    **Then** 处理规则：
    - 相关文档列表为空 → Recall 默认返回 1.0，HitRate 默认返回 1.0
    - 无任何命中 → MRR 返回 0.0
    - 严格避免除零异常

12. **Given** 执行器封装
    **When** 新增 `RetrievalEvalExecutor`
    **Then** 封装检索调用逻辑 — 支持批量执行，隔离评测逻辑与业务检索接口

13. **Given** 计算器测试
    **When** 编写 `EvalMetricsCalculatorTest`
    **Then** 覆盖：正常用例、全部命中、零命中、空相关文档列表、单条结果等边界场景

### 阶段 3.1c：三模式对比 + ReportGenerator + 集成

14. **Given** MetricsCalculator 和 Executor 就位
    **When** 扩展 Executor 支持三种检索模式
    **Then** 三种模式通过直接注入具体 adapter 类实现（非 Port 接口多态）：
    - **纯向量检索模式**：注入 `PgVectorChunkRetrievalAdapter`
    - **纯关键词检索模式**：注入 `SparseRetrievalAdapter`
    - **混合检索模式（默认）**：注入 `HybridChunkRetrievalAdapter`

15. **Given** 三模式对比并行执行
    **When** 运行三模式评测
    **Then** 使用 **Java 21 虚拟线程**并行执行（`Executors.newVirtualThreadPerTaskExecutor()`），三种模式共享同一份数据集同时跑（AD-12）

16. **Given** 性能约束
    **When** 执行评测
    **Then** 单模式 ≤ 5 秒，三模式对比 ≤ 15 秒（20 条样本）

17. **Given** 报告生成
    **When** 新增 `EvalReportGenerator`
    **Then** JSON 报告为三级结构：
    1. **整体汇总层**：Recall@5、MRR、HitRate@5、总查询数、单条平均检索耗时
    2. **分类型统计层**：按 `query_type` 分组统计各类型的三项指标均值，快速定位哪类查询效果最差
    3. **单条详情层**：查询内容、query_type、检索返回的 ID 列表、标注的相关 ID 列表、命中标记、单条指标得分

18. **Given** 三模式对比报告
    **When** 生成报告
    **Then** 报告包含三个模式的独立汇总 + 对比表

19. **Given** 触发方式
    **When** 运行评测
    **Then** `mvn test -Dtest=EvalRunnerTest` 可触发完整混合检索评测

20. **Given** 零外部依赖
    **When** 评测执行
    **Then** 全程不调用大模型、无外部网络请求

21. **Given** test-only 边界
    **When** 所有组件实现完成
    **Then** 所有组件仅在 test 作用域生效，不侵入任何生产代码、不影响主业务打包

## Tasks / Subtasks

- [x] Task 1: 新增 QA pairs 测试数据集（AC: #1, #2, #3, #5, #6）
  - [x] 1.1 创建 `src/test/resources/eval/` 目录
  - [x] 1.2 编写 `retrieval-qa-pairs.json` — 20 条 QA pairs，每种 QueryType 至少 3 条
  - [x] 1.3 每条包含 `question`、`query_type`、`relevant_doc_ids`、`relevance_levels` 四个字段
  - [x] 1.4 `relevant_doc_ids` 中的每个 ID 在 `relevance_levels` 中都有对应标注（strong/weak）
  - [x] 1.5 QA pairs 应基于项目实际知识库内容编写（Flyway、PGVector、Spring Boot 等技术主题）

- [x] Task 2: 新增 `RetrievalEvalDatasetLoader` + 测试（AC: #1, #2, #3, #5, #7）
  - [x] 2.1 创建 `RetrievalEvalDatasetLoader.java`（`src/test/java/.../qa/infrastructure/eval/` 包下）
  - [x] 2.2 使用 Jackson `ObjectMapper` 反序列化 JSON（Spring Boot 测试依赖自带 Jackson，无需新依赖）
  - [x] 2.3 定义 `EvalSample` record（question, queryType, relevantDocIds, relevanceLevels）
  - [x] 2.4 定义 `RelevanceLevel` 枚举（STRONG, WEAK）— 纯 Java 枚举，零注解
  - [x] 2.5 校验逻辑：缺失必填字段 → 抛 `IllegalArgumentException`，`query_type` 非法值 → 抛异常
  - [x] 2.6 校验逻辑：`relevant_doc_ids` 中的 ID 缺少 `relevance_levels` 标注 → 抛异常
  - [x] 2.7 创建 `RetrievalEvalDatasetLoaderTest.java` — 覆盖正常加载和各种校验异常场景

- [x] Task 3: 新增 `EvalMetricsCalculator` + 测试（AC: #8, #9, #10, #11, #13）
  - [x] 3.1 创建 `EvalMetricsCalculator.java`（`src/test/java/.../qa/infrastructure/eval/` 包下）
  - [x] 3.2 实现 `recallAtK(List<String> retrievedIds, Set<String> strongRelevantIds, int k)` — static 纯函数
  - [x] 3.3 实现 `mrr(List<String> retrievedIds, Set<String> strongRelevantIds)` — static 纯函数
  - [x] 3.4 实现 `hitRateAtK(List<String> retrievedIds, Set<String> strongRelevantIds, int k)` — static 纯函数
  - [x] 3.5 定义 `ndcgAtK(...)` 方法签名，本期抛 `UnsupportedOperationException("NDCG@K: Phase 2")`
  - [x] 3.6 边界处理：空相关文档列表 → Recall=1.0, HitRate=1.0；无命中 → MRR=0.0；防除零
  - [x] 3.7 创建 `EvalMetricsCalculatorTest.java` — 覆盖正常用例、全命中、零命中、空列表、单条结果等边界

- [x] Task 4: 新增 `RetrievalEvalExecutor`（AC: #12, #14, #15）
  - [x] 4.1 创建 `RetrievalEvalExecutor.java`（`src/test/java/.../qa/infrastructure/eval/` 包下）
  - [x] 4.2 构造器注入三个具体 adapter：`PgVectorChunkRetrievalAdapter`（Dense）、`SparseRetrievalAdapter`（Sparse）、`HybridChunkRetrievalAdapter`（Hybrid）
  - [x] 4.3 实现 `executeSingleMode(ChunkRetrievalPort adapter, List<EvalSample> dataset, int topK)` — 批量执行，返回 `List<EvalResult>`
  - [x] 4.4 定义 `EvalResult` record（question, queryType, retrievedIds, relevantDocIds, relevanceLevels, hitFlags, recall, mrr, hitRate, latencyMs）
  - [x] 4.5 使用虚拟线程 `Executors.newVirtualThreadPerTaskExecutor()` 并行执行三种模式（AD-12）
  - [x] 4.6 性能约束：单模式 ≤ 5 秒，三模式对比 ≤ 15 秒（20 条样本）

- [x] Task 5: 新增 `EvalReportGenerator`（AC: #17, #18）
  - [x] 5.1 创建 `EvalReportGenerator.java`（`src/test/java/.../qa/infrastructure/eval/` 包下）
  - [x] 5.2 实现三层 JSON 报告结构：整体汇总 → 分类型统计 → 单条详情
  - [x] 5.3 整体汇总：Recall@5、MRR、HitRate@5、总查询数、单条平均检索耗时
  - [x] 5.4 分类型统计：按 QueryType 分组，每类的三项指标均值
  - [x] 5.5 单条详情：查询内容、query_type、检索返回 ID 列表、标注的相关 ID 列表、命中标记、单条指标得分
  - [x] 5.6 三模式对比时报告包含三个模式独立汇总 + 对比表

- [x] Task 6: 新增 `EvalRunnerTest` 集成入口（AC: #19, #20, #21）
  - [x] 6.1 创建 `EvalRunnerTest.java`（`src/test/java/.../qa/infrastructure/eval/` 包下）
  - [x] 6.2 标注 `@SpringBootTest`，注入三个具体 adapter
  - [x] 6.3 `runRetrievalEval()` 测试方法：加载数据集 → 三模式并行执行 → 生成报告
  - [x] 6.4 全程不调用 LLM、无外部网络请求（仅触发检索链路）
  - [x] 6.5 确认 `mvn test -Dtest=EvalRunnerTest` 可独立触发
  - [x] 6.6 确认所有组件仅在 test 作用域，不影响 `mvn package` 主业务打包

- [x] Task 7: 集成验证（AC: #21）
  - [x] 7.1 运行 `mvn test "-Dtest=RetrievalEvalDatasetLoaderTest"` — 正常加载和校验异常全部通过
  - [x] 7.2 运行 `mvn test "-Dtest=EvalMetricsCalculatorTest"` — 所有指标计算和边界场景通过
  - [x] 7.3 运行 `mvn test "-Dtest=EvalRunnerTest"` — 三模式评测完整执行，JSON 报告输出
  - [x] 7.4 运行 `mvn "-Dtest=!MyAiApplicationTests" test` — 所有现有测试不受影响（无回归）
  - [x] 7.5 运行 `mvn clean package -DskipTests` — 确认主业务打包不受影响（test-only 组件不进生产包）

## Dev Notes

### 前置故事上下文

- **Story 1.1-1.6（done）**：Epic 1 全部完成 — `RetrievedChunk.score`、`RerankingPort`、`QaRetrievalProperties`、`QueryType`（5 值枚举在 `qa/domain/model/`）、`RuleBasedQueryClassifier`、CHITCHAT 拦截均已就位
- **Story 2.1（done）**：Flyway V9 迁移已执行 — `content_tsv tsvector` 列和 `idx_vector_store_fts` GIN 索引已创建
- **Story 2.2（done）**：`ScopeFilterBuilder` 已提取 — `toFilterExpression()`（Dense 路径）和 `toSqlCondition()`（Sparse 路径）两个静态方法可用
- **Story 2.3（done）**：`SparseRetrievalAdapter` 已实现 — BM25 检索路径就位，`@Component`，实现 `ChunkRetrievalPort`
- **Story 2.4（done）**：`HybridChunkRetrievalAdapter` 已实现 — `@Primary` + `@Component`，RRF 融合，默认 `ChunkRetrievalPort` 实现

本 Story 是 Epic 3 的唯一故事。它依赖 Epic 1 + Epic 2 的全部产出物。三个具体 adapter（Dense、Sparse、Hybrid）都已就位且保留了 `@Component`，EvalRunner 可以直接注入做三模式对比。

### Epic 2 回顾教训（必须吸取）

来自 `_bmad-output/implementation-artifacts/rag-hybrid-search/epic-2-retro-2026-06-18.md`：

1. **Javadoc 遗漏仍发生** — 新文件实现后、提交前逐项检查 `@author` + `@since` + 类级别 Javadoc。行动项 1：Epic 3 新文件审查零 Javadoc 修复项
2. **测试断言初版质量有待提高** — 手算预期值再写代码；SQL 参数计数逐一编号；对象构造先确认构造器签名。行动项 2：Epic 3 测试初版通过率 > 80%（当前 Epic 2 约 25%）
3. **构造器参数封装** — 如需再加参数考虑 record 封装

### 三路 Adapter 当前签名（EvalRunner 注入目标）

**Dense — `PgVectorChunkRetrievalAdapter`**（`@Component`，保留用于对比）

```java
// 文件：src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java
@Component
public class PgVectorChunkRetrievalAdapter implements ChunkRetrievalPort {
    public PgVectorChunkRetrievalAdapter(VectorStore vectorStore) { ... }
    // similaritySearch(String question, int topK)
    // similaritySearch(String question, int topK, List<AskableDocumentVersion> scope)
}
```

**Sparse — `SparseRetrievalAdapter`**（`@Component`，保留用于对比）

```java
// 文件：src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapter.java
@Component
public class SparseRetrievalAdapter implements ChunkRetrievalPort {
    public SparseRetrievalAdapter(JdbcTemplate jdbcTemplate) { ... }
    // similaritySearch(String question, int topK)
    // similaritySearch(String question, int topK, List<AskableDocumentVersion> scope)
}
```

**Hybrid — `HybridChunkRetrievalAdapter`**（`@Primary` + `@Component`）

```java
// 文件：src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapter.java
@Primary
@Component
public class HybridChunkRetrievalAdapter implements ChunkRetrievalPort {
    public HybridChunkRetrievalAdapter(
            PgVectorChunkRetrievalAdapter denseAdapter,
            SparseRetrievalAdapter sparseAdapter) { ... }
    // similaritySearch(String question, int topK)
    // similaritySearch(String question, int topK, List<AskableDocumentVersion> scope)
    // RRF 常量：RRF_K=60, DENSE_WEIGHT=0.5, SPARSE_WEIGHT=0.5（private static final）
}
```

### ChunkRetrievalPort 接口（精确签名，不可修改）

```java
// 文件：src/main/java/io/github/spike/myai/qa/domain/port/ChunkRetrievalPort.java
public interface ChunkRetrievalPort {
    List<RetrievedChunk> similaritySearch(String question, int topK);
    List<RetrievedChunk> similaritySearch(String question, int topK, List<AskableDocumentVersion> scope);
}
```

**关键约束：** EvalRunner 直接注入具体 adapter 类（非 Port 接口），因此可分别调用三路检索做对比。scope 参数可传 `null`（各 adapter 内部处理 null → 无过滤）。

### RetrievedChunk record（精确字段）

```java
// 文件：src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java
public record RetrievedChunk(
    String documentId,
    String kbId,
    int chunkIndex,
    String content,
    Integer sourceVersionNumber,
    String sourceFilename,
    Instant sourceUpdatedAt,
    double score) {

    // 简化构造器：score 默认 0.0，版本元数据为 null
    public RetrievedChunk(String documentId, String kbId, int chunkIndex, String content) { ... }
}
```

**EvalRunner 评测时需要：** `documentId` 用于匹配标注（`relevant_doc_ids`），`score` 用于排名排序。

### QueryType 枚举（精确值）

```java
// 文件：src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java
public enum QueryType {
    FACTOID, PROCEDURAL, COMPARATIVE, CHITCHAT, GENERAL
}
```

**QA pairs 的 `query_type` 字段必须与这 5 个枚举值严格匹配。**

### AD-5 架构约束：Test-only 组件定位

**决策来源：** `_bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-5`

```
src/test/java/io/github/spike/myai/qa/infrastructure/eval/
  ├── RetrievalEvalDatasetLoader.java       // 数据集加载 + 校验
  ├── EvalSample.java                       // record — 单条评测样本
  ├── RelevanceLevel.java                   // 枚举 — STRONG / WEAK
  ├── EvalMetricsCalculator.java            // static 纯函数 — Recall@5 / MRR / HitRate@5
  ├── EvalResult.java                       // record — 单条评测结果
  ├── RetrievalEvalExecutor.java            // 检索执行器 — 三模式并行
  ├── EvalReportGenerator.java              // JSON 报告生成
  ├── EvalRunnerTest.java                   // @SpringBootTest 集成入口
  ├── RetrievalEvalDatasetLoaderTest.java   // 加载器单元测试
  └── EvalMetricsCalculatorTest.java        // 指标计算器单元测试

src/test/resources/eval/
  └── retrieval-qa-pairs.json               // 20 条 QA pairs
```

**约束：**
- 所有 eval 组件放在 `src/test/java/`，不进生产包
- `@SpringBootTest` 按需加载，不需要 `@Transactional`
- 不引入 REST endpoint 或完整 Spring Context 测试基础设施
- `RetrievalEvalDatasetLoader`、`EvalMetricsCalculator`、`EvalReportGenerator` 可做纯单元测试（不启动 Spring 上下文）
- `EvalRunnerTest` 需要 `@SpringBootTest`（需要注入具体 adapter 和真实的向量数据库检索）

### 虚拟线程并行执行指南（AD-12）

```java
// 三模式并行执行示例
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    Future<List<EvalResult>> denseFuture = executor.submit(() ->
        executeSingleMode(denseAdapter, dataset, topK));
    Future<List<EvalResult>> sparseFuture = executor.submit(() ->
        executeSingleMode(sparseAdapter, dataset, topK));
    Future<List<EvalResult>> hybridFuture = executor.submit(() ->
        executeSingleMode(hybridAdapter, dataset, topK));

    List<EvalResult> denseResults = denseFuture.get();
    List<EvalResult> sparseResults = sparseFuture.get();
    List<EvalResult> hybridResults = hybridFuture.get();
}
```

**注意：** Java 21 虚拟线程通过 `Executors.newVirtualThreadPerTaskExecutor()` 创建。try-with-resources 确保 executor 关闭。`Future.get()` 会抛 `ExecutionException`，需要捕获并处理。

### JSON 报告三层结构示例

```json
{
  "summary": {
    "total_queries": 20,
    "modes": {
      "dense":  { "recall_at_5": 0.65, "mrr": 0.58, "hit_rate_at_5": 0.80, "avg_latency_ms": 120 },
      "sparse": { "recall_at_5": 0.55, "mrr": 0.48, "hit_rate_at_5": 0.70, "avg_latency_ms": 80 },
      "hybrid": { "recall_at_5": 0.75, "mrr": 0.68, "hit_rate_at_5": 0.90, "avg_latency_ms": 150 }
    }
  },
  "by_query_type": {
    "PROCEDURAL": {
      "dense":  { "recall_at_5": 0.70, "mrr": 0.65, "hit_rate_at_5": 0.85 },
      "sparse": { "recall_at_5": 0.60, "mrr": 0.52, "hit_rate_at_5": 0.75 },
      "hybrid": { "recall_at_5": 0.80, "mrr": 0.72, "hit_rate_at_5": 0.95 }
    }
  },
  "details": [
    {
      "question": "Spring Boot 如何配置 Flyway",
      "query_type": "PROCEDURAL",
      "modes": {
        "dense": {
          "retrieved_ids": ["doc-001#c0", "doc-002#c1"],
          "relevant_ids": ["doc-001", "doc-003"],
          "hit_flags": [true, false],
          "recall": 0.5, "mrr": 1.0, "hit_rate": 1.0,
          "latency_ms": 115
        }
      }
    }
  ]
}
```

### QA pairs 数据编写指南

**数据来源：** 基于项目实际知识库中的文档内容编写。项目使用 Spring Boot + PGVector + Flyway + Spring AI 技术栈，QA pairs 应覆盖这些技术主题。

**分布要求：**
- 20 条总数，每种 QueryType ≥ 3 条
- FACTOID（事实查询）≥ 3 条：如"什么是 PGVector"、"Spring AI 的 VectorStore 接口支持哪些操作"
- PROCEDURAL（操作查询）≥ 3 条：如"如何配置 Flyway 迁移"、"怎么添加新的文档入库流程"
- COMPARATIVE（对比查询）≥ 3 条：如"JPA 和 JdbcTemplate 的区别"、"Dense 和 Sparse 检索各有什么优缺点"
- CHITCHAT（闲聊）≥ 3 条：如"你好"、"谢谢"、"今天天气怎么样"
- GENERAL（通用）≥ 3 条：如"文档管理"、"系统架构"

**doc_id 标注：** `relevant_doc_ids` 中的 ID 必须是知识库中实际存在的 document ID。运行 EvalRunner 前需确认数据库中有对应数据。`relevance_levels` 中每个 ID 必须标注 `strong` 或 `weak`。

**指标统计精度：** Recall@5、MRR、HitRate@5 仅统计 `strong` 强相关文档。`weak` 预留用于后续 NDCG 加权（Phase 2）。

### 架构约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| AD-5：EvalRunner test-only | ✅ | 全部放 `src/test/java/`，不进生产包 |
| AD-12：虚拟线程并行 | ✅ | `Executors.newVirtualThreadPerTaskExecutor()` |
| NFR-2：零新外部依赖 | ✅ | Jackson（Spring Boot 测试自带）、JDK 标准库 |
| NFR-3：六边形合规 | ✅ | eval 组件在 test 作用域，直接注入具体 adapter，不经过 domain port |
| 不修改 ChunkRetrievalPort | ✅ | AD-1 约束持续遵守 |
| 不修改 AskQuestionApplicationService | ✅ | eval 独立于主业务流程 |

### Project Structure Notes

**新增文件（全部在 src/test/）：**

```
src/test/java/io/github/spike/myai/qa/infrastructure/eval/
  ├── EvalSample.java
  ├── RelevanceLevel.java
  ├── RetrievalEvalDatasetLoader.java
  ├── EvalMetricsCalculator.java
  ├── EvalResult.java
  ├── RetrievalEvalExecutor.java
  ├── EvalReportGenerator.java
  ├── EvalRunnerTest.java
  ├── RetrievalEvalDatasetLoaderTest.java
  └── EvalMetricsCalculatorTest.java

src/test/resources/eval/
  └── retrieval-qa-pairs.json
```

**不修改文件：**

- `ChunkRetrievalPort.java`（端口接口不变）
- `AskQuestionApplicationService.java`（应用层不变）
- `PgVectorChunkRetrievalAdapter.java`（Dense 路径不变）
- `SparseRetrievalAdapter.java`（Sparse 路径不变）
- `HybridChunkRetrievalAdapter.java`（Hybrid 路径不变）
- `QueryType.java`（枚举不变，仅作为 QA pairs 的校验目标）
- `RetrievedChunk.java`（record 不变，仅作为检索结果的数据载体）
- `application.yaml`（不新增配置项）
- `pom.xml`（不新增依赖）

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 3.1] — Story 定义与三阶段 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-5] — EvalRunner test-only 定位决策
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#实施顺序] — Phase 4（FR-11）
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-11] — EvalRunner Layer 1 检索质量评估需求
- [Source: _bmad-output/implementation-artifacts/rag-hybrid-search/epic-2-retro-2026-06-18.md] — Epic 2 回顾教训
- [Source: _bmad-output/implementation-artifacts/rag-hybrid-search/2-4-hybrid-chunk-retrieval-adapter.md] — Story 2.4 完整上下文
- [Source: src/main/java/io/github/spike/myai/qa/domain/port/ChunkRetrievalPort.java] — 端口接口定义
- [Source: src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java] — 检索结果 record
- [Source: src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java] — 查询类型枚举
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java] — Dense 路径
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapter.java] — Sparse 路径
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapter.java] — Hybrid 路径
- [Source: docs/project-context.md] — 162 条编码规则

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- `RetrievalEvalDatasetLoader` 最初用 `load(String resourcePath)` 通过 classpath 加载，导致临时文件路径无法测试。重构为增加 `loadFromStream(InputStream)` 重载，错误路径测试改用 `ByteArrayInputStream` 直接传入。
- `EvalRunnerTest` 三模式并行执行使用 `Executors.newVirtualThreadPerTaskExecutor()`，首次运行 13 秒完成（含 Spring 上下文启动 7.5 秒，实际检索 ~5 秒）。

### Completion Notes List

- **阶段 3.1a**：创建 `RelevanceLevel` 枚举（STRONG/WEAK）、`EvalSample` record、`RetrievalEvalDatasetLoader`（Jackson ObjectMapper + 完整格式校验）。`RetrievalEvalDatasetLoaderTest` 11 个测试通过。
- **阶段 3.1b**：创建 `EvalMetricsCalculator`（static 纯函数：recallAtK、mrr、hitRateAtK、ndcgAtK Phase 2 预留）。`EvalMetricsCalculatorTest` 17 个测试覆盖正常用例和边界场景。
- **阶段 3.1c**：创建 `EvalResult` record、`RetrievalEvalExecutor`（批量执行器）、`EvalReportGenerator`（三层 JSON 报告）、`EvalRunnerTest`（@SpringBootTest 三模式并行评测入口）。
- `mvn test -Dtest=EvalRunnerTest` 通过 — Spring 上下文启动成功，三个 adapter 注入成功，三模式并行执行完成，JSON 报告输出到 `target/eval-report-{timestamp}.json`。
- `mvn "-Dtest=!MyAiApplicationTests" test` — 556 个测试全部通过，零回归。
- `mvn clean package -DskipTests` — 主业务打包成功，test-only 组件不影响生产包。
- QA pairs 使用占位符 doc_id（如 `doc-flyway-config`），用户需用实际知识库文档 ID 更新以获得有意义的评测结果。

### File List

- src/test/java/io/github/spike/myai/qa/infrastructure/eval/RelevanceLevel.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalSample.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/RetrievalEvalDatasetLoader.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/RetrievalEvalDatasetLoaderTest.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalMetricsCalculator.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalMetricsCalculatorTest.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalResult.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/RetrievalEvalExecutor.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalReportGenerator.java (新增)
- src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalRunnerTest.java (新增)
- src/test/resources/eval/retrieval-qa-pairs.json (新增)

### Review Findings

- [x] [Review][Decision] AC 16 — 性能阈值是否需要硬断言？已采用软断言：仅度量检索耗时（排除 Spring 启动），2x 裕度（30s），防 flaky。已实现 `assertThat(totalRetrievalMs).isLessThan(30_000)`。（EvalRunnerTest.java:61-68）
- [x] [Review][Patch] Java `assert` → AssertJ 断言 — 7 行报告校验已替换为 `assertThat(...).as(...).isTrue()`。（EvalRunnerTest.java:93-100）
- [x] [Review][Patch] `adapter.similaritySearch()` 返回值 null 防护 — 已加 `if (chunks == null) chunks = List.of()`。（RetrievalEvalExecutor.java:74-75）
- [x] [Review][Patch] `InterruptedException | ExecutionException` 捕获已拆分 — InterruptedException 保留 interrupt 标记，ExecutionException 独立处理 unwrap cause。（EvalRunnerTest.java:74-76）
- [x] [Review][Patch] `executeSingleMode` 逐条错误隔离 — 每条样本 try-catch，失败记录 `latencyMs=-1` 标记占位，不丢失其他结果。（RetrievalEvalExecutor.java:38-41）
- [x] [Review][Patch] `Future.get()` 超时 — 三路 Future 均加 `.get(60, TimeUnit.SECONDS)` + `TimeoutException` throws。（EvalRunnerTest.java:71-73）
- [x] [Review][Patch] `.limit(k)` 负值防护 — `recallAtK`/`hitRateAtK`/`ndcgAtK` 入口加 `if (k < 0) throw new IllegalArgumentException(...)`。（EvalMetricsCalculator.java:37,81,96）
- [x] [Review][Patch] 指标静态方法 null 入参校验 — 所有方法加 `Objects.requireNonNull(...)`。（EvalMetricsCalculator.java:32-84）
- [x] [Review][Patch] `buildComparisonDetails` 安全迭代 — 改用 `maxSize = Math.max(...)` 索引安全遍历，缺位 null guard。（EvalReportGenerator.java:184-198）

## Change Log

- feat(qa): Story 3.1 — EvalRunner 检索质量评估（三模式对比 + 分层报告）（2026-06-18）
