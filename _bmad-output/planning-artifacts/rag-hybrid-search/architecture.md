---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
workflowType: 'architecture'
lastStep: 8
status: 'complete'
completedAt: '2026-06-16'
inputDocuments:
  - "_bmad-output/planning-artifacts/rag-hybrid-search/prd.md"
  - "_bmad-output/planning-artifacts/rag-hybrid-search/.decision-log.md"
  - "_bmad-output/planning-artifacts/rag-hybrid-search/review-rubric.md"
  - "_bmad-output/planning-artifacts/research/decision-register.md"
  - "docs/project-context.md"
  - "docs/architecture/overview.md"
  - "docs/architecture/domain/qa.md"
workflowType: 'architecture'
project_name: 'my-AI'
user_name: 'spike'
date: '2026-06-16'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**

13 个 FR 分为 4 个功能组，覆盖 RAG 检索链路的三个结构性缺陷：

| 功能组 | FR 编号 | 架构含义 |
|--------|---------|---------|
| 4.1 基础准备 | FR-1, FR-2, FR-3 | domain model 扩展（score 字段）+ 新端口（RerankingPort）+ 配置外部化 |
| 4.2 查询分类 | FR-4, FR-5, FR-6 | 新端口（QueryClassifierPort）+ 规则引擎 + 应用层集成 |
| 4.3 混合检索 | FR-7, FR-8, FR-9, FR-10 | DB schema 变更（V9 tsvector）+ 新适配器（Sparse + Hybrid）+ 端口扩展 |
| 4.4 评估体系 | FR-11, FR-12, FR-13 | 新独立组件（EvalRunner）+ QA 数据集 |

MVP 范围：FR-1 到 FR-11（FR-12/13 推迟到 Phase 2）。

**Non-Functional Requirements:**

| NFR | 约束 | 影响 |
|-----|------|------|
| NFR-1 检索延迟 | Hybrid Search 增量 ≤ 200ms | Dense/Sparse 必须并行执行，RRF 纯内存计算 |
| NFR-2 零新依赖 | 不引入新 Maven 依赖和 Docker 容器 | BM25 必须用 PG 原生 tsvector + GIN |
| NFR-3 六边形合规 | domain 层零框架注解，adapter 不互相引用 | QueryClassifierPort/RerankingPort 定义在 domain 层 |

**Scale & Complexity:**

- 主要技术领域：后端 API（Spring Boot + PGVector）
- 复杂度：中等（个人知识库 Portfolio 项目）
- 预估架构组件：5 个新组件 + 4 个修改组件 = 9 个架构构件
- 估计净增代码：≤ 800 LOC（含测试）

### Technical Constraints & Dependencies

- **数据库**：PostgreSQL 16 + PGVector 已就位，BM25 用 PG 原生 tsvector + GIN 索引，零新基础设施
- **迁移纪律**：Flyway V9 不可修改已执行的 V1-V8，PGVector 维度硬编码
- **数据访问**：JdbcTemplate 直接 SQL，Sparse 路径不经过 Spring AI VectorStore
- **假设 A-1**：Spring AI `VectorStore.similaritySearch()` 的 score 可用于 cosine similarity 计算——需验证，备选方案是自定义 SQL 查询
- **假设 A-2**：`'simple'` 文本搜索配置对英文技术术语分词质量足够，中文逐字拆分被 Dense 路径稀释
- **并行策略**：与 docling-upgrade 无 DB schema 冲突（V9 是本项目独立迁移）
- **5 个决策已锁定**：D3（Hybrid Search）、D4（Reranking 仅留接口）、D5（规则分类器）、D6（维持截断+配置化）、D17（轻量 Eval 体系）
- **OQ-1 待定**：`ChunkRetrievalPort` 新增方法 vs 新建独立端口 — 本架构核心决策点

### Cross-Cutting Concerns Identified

1. **score 字段贯穿**：RetrievedChunk.score 在 Dense/Sparse/RRF 三路都需要正确填充，是所有后续功能的基础
2. **Scope 过滤逻辑复用**：Dense 和 Sparse 路径都需要 scope 过滤（只检索 askable 版本的 chunk），必须提取共享工具类避免重复（R3 风险）
3. **Eval 体系依赖链**：FR-11 需要 FR-9（Hybrid Search）上线后才能验证，Layer 1 eval 是参数调优的数据基础
4. **QueryType 驱动的检索策略**：D5 分类结果影响 D3 的 Dense/Sparse 权重配比，需在应用层而非适配器层做路由
5. **配置外部化**：检索参数（min-candidates、candidate-multiplier）需通过 `@ConfigurationProperties` 暴露，为后续 QueryType 驱动权重调整预留扩展点

## Starter Template Evaluation

**不适用 — Brownfield 项目。**

my-AI 技术栈已完全确立（Java 21 + Spring Boot 3.5.8 + PostgreSQL 16 + PGVector + React 19），架构为 DDD-Lite 六边形。本次 RAG Hybrid Search 是在现有 `qa/` 子域上做功能增强，不需要（也不应该）引入 starter template。

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**

- AD-1: ChunkRetrievalPort 接口扩展 → 不改端口接口，新增实现者（Option C）
- AD-2: RRF 融合位置 → HybridChunkRetrievalAdapter 内部透明完成

**Important Decisions (Shape Architecture):**

- AD-3: Dense/Sparse 并行执行 → CompletableFuture 并行 + 降级
- AD-4: Scope 过滤复用 → ScopeFilterBuilder 工具类
- AD-5: EvalRunner 定位 → Test-only 组件

**Deferred Decisions (Post-MVP):**

- AD-6: QueryType 驱动权重 → 依赖 D17 eval 基线数据
- AD-7: zhparser 中文分词 → 依赖 eval 数据证明 'simple' 不足

### AD-1: ChunkRetrievalPort 接口扩展 (OQ-1 Resolution)

**决策：不扩展端口接口，新增 HybridChunkRetrievalAdapter 作为 ChunkRetrievalPort 的新实现者。**

**选项对比：**

| 选项 | 方案 | 选择 |
|------|------|------|
| A | 在 ChunkRetrievalPort 新增 hybridSearch() 方法 | ❌ 接口变胖 |
| B | 新建独立端口 HybridRetrievalPort | ❌ 语义分裂 |
| C | 不改端口接口，新增实现者 | ✅ 选定 |

**理由：**

1. 端口契约是"检索相关 chunk"，不是"用哪种算法检索"。Dense vs Hybrid 是 infrastructure 层关注点
2. Hybrid 是论文验证的严格上位替代（avg score +12%，延迟几乎不变），生产环境无理由保留 Dense-only 作为运行时选项
3. 零接口变更、零调用方变更 — AskQuestionApplicationService 代码不改，只换注入的实现者
4. EvalRunner 需要对比 Dense vs Hybrid 时，注入具体 adapter 类（不走端口），test 环境天然支持

**实现切换机制：** @Primary 注解（Hybrid 默认）或 @ConditionalOnProperty（配置切换）

**与 D3 联动：** PgVectorChunkRetrievalAdapter（纯 Dense）保留，HybridChunkRetrievalAdapter（Dense + Sparse + RRF）为默认实现。

### AD-2: RRF 融合位置

**决策：在 HybridChunkRetrievalAdapter 内部完成 RRF 融合，对调用方完全透明。**

**理由：**

1. RRF 是检索策略的内部实现细节，调用方只要"给我 topK 相关 chunk"
2. 放在 ApplicationService 层会让它从"问答编排"膨胀为"检索策略编排"
3. 与 AD-1 一致 — HybridChunkRetrievalAdapter 实现 ChunkRetrievalPort.similaritySearch()，内部编排 Dense + Sparse + RRF

**结构：**

```
HybridChunkRetrievalAdapter implements ChunkRetrievalPort
  ├── PgVectorChunkRetrievalAdapter (Dense - 注入)
  ├── SparseRetrievalAdapter (Sparse - 注入)
  └── similaritySearch() 内部：
        1. 并行调 Dense + Sparse (见 AD-3)
        2. RRF 融合 (k=60, 等权重)
        3. 返回 List<RetrievedChunk> (score = RRF 分数)
```

### AD-3: Dense/Sparse 并行执行

**决策：CompletableFuture.supplyAsync() 并行执行，总延迟 = max(Dense, Sparse) + RRF (≈ 50ms 增量)。**

**理由：**

1. NFR-1 硬约束：Hybrid Search 增量 ≤ 200ms。顺序执行约 150ms 增量（接近上限），并行约 50ms 增量（余量充足）
2. Java 21 Virtual Threads — supplyAsync() 默认在 Virtual Thread 池执行，零线程池配置
3. 并行逻辑封装在 adapter 内部，对端口和 ApplicationService 完全透明

**降级策略：** Dense 失败 → 降级到 Sparse-only 结果，Sparse 失败 → 降级到 Dense-only 结果。通过 CompletableFuture.exceptionally() 处理。

### AD-4: Scope 过滤逻辑复用

**决策：提取 ScopeFilterBuilder 工具类到 infrastructure/retrieval/ 包，供 Dense 和 Sparse 路径共用。**

**理由：**

1. 两处逻辑输入完全相同（List<AskableDocumentVersion>），仅输出格式不同
2. D20 Group Model 迁移后 scope 逻辑会变，集中维护避免遗漏
3. 项目规则禁止重复代码，共享关注点应提取为工具类

**结构：**

```java
class ScopeFilterBuilder {
    Filter.Expression toFilterExpression(List<AskableDocumentVersion> scope);  // Dense 路径
    SqlScopeCondition toSqlCondition(List<AskableDocumentVersion> scope);       // Sparse 路径
}
record SqlScopeCondition(String whereClause, List<Object> params) {}
```

### AD-5: EvalRunner 组件定位

**决策：Test-only 组件，放在 src/test/java/，通过 @SpringBootTest 按需加载。**

**理由：**

1. PRD 明确："独立组件，不在主 QA 流程中"、"mvn test -Dtest=EvalRunnerTest 可触发评估"
2. 需要注入具体 adapter 做 Dense vs Hybrid 对比 — test 环境天然支持
3. QA pairs 数据在 src/test/resources/eval/ — 确认是 test 域
4. 不需要 @Transactional、REST endpoint 或完整 Spring Context

**结构：**

```
src/test/java/.../qa/infrastructure/eval/
  ├── EvalRunnerTest.java        // @SpringBootTest
  └── EvalMetrics.java           // Recall@5, MRR 计算
src/test/resources/eval/
  ├── retrieval-qa-pairs.json    // 20 条 QA pairs (Layer 1)
  └── generation-qa-pairs.json   // 10 条 QA pairs (Layer 2, Phase 2)
```

### Implementation Sequence

**Phase 1 — 基础准备（FR-1, FR-2, FR-3）：**

1. RetrievedChunk 添加 score 字段
2. RerankingPort + NoOpRerankingAdapter
3. 检索参数配置外部化

**Phase 2 — 查询分类（FR-4, FR-5, FR-6）：**

4. QueryClassifierPort + QueryType
5. RuleBasedQueryClassifier
6. AskQuestionApplicationService 集成查询分类

**Phase 3 — 混合检索（FR-7, FR-8, FR-9, FR-10）：**

7. Flyway V9 tsvector 迁移
8. ScopeFilterBuilder 工具类
9. SparseRetrievalAdapter
10. HybridChunkRetrievalAdapter (RRF 融合)
11. @Primary 切换到 Hybrid 实现

**Phase 4 — 评估体系（FR-11）：**

12. EvalRunner + QA pairs 数据集

### Cross-Component Dependencies

- AD-1 → AD-2 → AD-3 是线性依赖链：端口决定 → 融合位置决定 → 并行策略决定
- AD-4 被 AD-2 依赖（HybridChunkRetrievalAdapter 内部的 Sparse 路径需要 ScopeFilterBuilder）
- AD-5 独立于其他决策，但依赖 AD-1 的"注入具体 adapter"能力
- FR-1 score 字段是 Phase 2/3/4 的前置条件（所有检索路径都需要填充 score）

## Implementation Patterns & Consistency Rules

### Scope

通用编码模式已在 `docs/project-context.md`（162 条规则）中定义。本节仅覆盖 RAG Hybrid Search 特有的实现一致性规则，防止 AI agent 在共享决策点做出不同选择。

### Critical Conflict Points Identified

7 个领域存在 agent 分歧风险。

### score 字段处理（FR-1）

**规则：** `RetrievedChunk.score` 的含义随检索路径变化，但类型始终是 `double`。

| 检索路径 | score 含义 | 计算方式 |
|----------|-----------|---------|
| Dense | cosine similarity | `1.0 - cosine_distance` |
| Sparse | BM25 rank | `ts_rank(content_tsv, query)` |
| RRF 融合 | RRF 分数 | `Σ 1/(k + rank_i)`，k=60 |

**默认值：** 现有简化构造器 `RetrievedChunk(documentId, kbId, chunkIndex, content)` 的 score 默认为 `0.0`（不是 -1 或 NaN），保持向后兼容。

**禁止：** 在 domain model 中放检索路径标识（如 `enum RetrievalMethod`）。score 的语义由调用链决定，不是 RetrievedChunk 的职责。

### RRF 算法常量

**规则：** RRF 常量集中定义为 `HybridChunkRetrievalAdapter` 的 `private static final` 常量，不放配置文件。

```java
private static final int RRF_K = 60;
private static final double DENSE_WEIGHT = 0.5;
private static final double SPARSE_WEIGHT = 0.5;
```

**理由：** 这些是算法参数，不是运维参数。等 D17 eval 数据驱动调优时再决定是否外部化。PRD 明确说"不做超参调优——等 D17 eval 上线后用数据驱动调整"。

**禁止：** 在 `application.yaml` 中暴露 RRF_K 和权重。Phase 2 有数据支撑后再配置化。

### CompletableFuture 异常处理

**规则：** Dense/Sparse 单路失败时降级到另一路结果，日志级别 WARN，不抛异常。

```java
CompletableFuture<List<RetrievedChunk>> denseFuture =
    CompletableFuture.supplyAsync(() -> denseAdapter.similaritySearch(...))
                     .exceptionally(ex -> {
                         log.warn("Dense retrieval failed, degrading to sparse-only", ex);
                         return List.of();
                     });
```

**降级边界：** 两路都失败（都返回空 list）→ 最终返回空 list → ApplicationService 层的空结果兜底逻辑生效（已有：返回 fallback answer）。

**禁止：** 在 adapter 层抛出 `RuntimeException` 给调用方。adapter 内部消化异常，保证端口契约不被破坏。

### QueryType 枚举（FR-4）

**规则：** 枚举值严格使用 PRD 定义的 5 个值，命名不变。

```java
public enum QueryType {
    FACTOID, PROCEDURAL, COMPARATIVE, CHITCHAT, GENERAL
}
```

**禁止：** 使用 CHAT、CASUAL、QUESTION 等替代名称。PRD 的 5 个值已在论文分类体系中有对应关系，不可随意重命名。

**包位置：** `qa/domain/model/QueryType.java`（domain 层，零框架注解）。

### 配置属性命名

**规则：** 检索配置属性前缀 `app.qa.retrieval.*`，与现有 `app.*` 配置体系对齐。

```yaml
app:
  qa:
    retrieval:
      min-candidates: 20      # FR-3
      candidate-multiplier: 4  # FR-3
```

**配置类：** `QaRetrievalProperties`，带 `@ConfigurationProperties(prefix = "app.qa.retrieval")` + `@Validated`。

**禁止：** 使用 `myai.*` 或 `my-ai.*` 前缀（与现有配置风格不一致）。

### ScopeFilterBuilder 访问级别（AD-4）

**规则：** `ScopeFilterBuilder` 是 package-private class，放在 `qa/infrastructure/retrieval/` 包下。

**理由：** 它只被同包的 `PgVectorChunkRetrievalAdapter` 和 `SparseRetrievalAdapter` 使用，不需要对外暴露。

**禁止：** 放在 domain 层（它依赖 Spring AI 的 `Filter.Expression` 类型）。

### Flyway V9 命名（FR-7）

**规则：** tsvector 列名和索引名严格使用 PRD 定义。

```sql
-- V9__hybrid_search_tsvector.sql
ALTER TABLE vector_store
    ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

CREATE INDEX idx_vector_store_fts
    ON vector_store USING GIN (content_tsv);
```

| 对象 | 名称 | 禁止的替代名 |
|------|------|-------------|
| 列名 | `content_tsv` | `content_tsvector`, `tsv_content` |
| 索引名 | `idx_vector_store_fts` | `idx_vector_store_content_tsv`, `idx_fts` |
| 配置 | `'simple'` | `'english'`, `'chinese'` |

**理由：** Flyway 迁移不可修改已执行版本。命名必须一次到位。

### Enforcement Guidelines

**所有 AI Agent 实现 RAG Hybrid Search 时 MUST：**

1. 不修改 `ChunkRetrievalPort` 接口签名（AD-1）
2. RRF 融合逻辑只在 `HybridChunkRetrievalAdapter` 内部（AD-2）
3. Dense/Sparse 必须并行执行（AD-3）
4. Scope 过滤通过 `ScopeFilterBuilder` 复用（AD-4）
5. `RetrievedChunk.score` 默认值 0.0，不引入 NaN 或负数
6. RRF 常量作为类内 `private static final`，不放配置文件
7. Flyway V9 对象命名与 PRD 严格一致
8. QueryType 枚举值与 PRD 一致，不多不少

**验证方式：** ArchUnit 规则已覆盖 domain→infrastructure 依赖检查（D19）。新增的实现一致性规则通过 code review 验证。

## Project Structure & Boundaries

### RAG Hybrid Search — 变更文件清单

本节仅列出 RAG Hybrid Search 新增和修改的文件。完整项目结构见 `docs/guides/source-tree.md`。

基础路径：`src/main/java/io/github/spike/myai/`

#### domain 层（零框架注解）

```
qa/domain/
  model/
    RetrievedChunk.java          [修改] 新增 double score 字段 + 简化构造器
    QueryType.java               [新增] 枚举：FACTOID/PROCEDURAL/COMPARATIVE/CHITCHAT/GENERAL
  port/
    ChunkRetrievalPort.java      [不变] 接口签名不改（AD-1）
    RerankingPort.java           [新增] List<RetrievedChunk> rerank(candidates, question, topN)
    QueryClassifierPort.java     [新增] QueryType classify(String question)
```

#### application 层

```
qa/application/
  service/
    AskQuestionApplicationService.java  [修改] 集成 QueryClassifier + Reranker + 配置外部化
```

#### infrastructure 层

```
qa/infrastructure/
  retrieval/
    PgVectorChunkRetrievalAdapter.java  [修改] score 映射 + 使用 ScopeFilterBuilder
    SparseRetrievalAdapter.java         [新增] BM25 检索（JdbcTemplate + tsvector）
    HybridChunkRetrievalAdapter.java    [新增] @Primary，Dense + Sparse + RRF 融合
    ScopeFilterBuilder.java             [新增] package-private，Dense/Sparse 共用
    SqlScopeCondition.java              [新增] record，SQL WHERE 片段 + 参数列表
  reranking/
    NoOpRerankingAdapter.java           [新增] 透传实现，零开销
  classifier/
    RuleBasedQueryClassifier.java       [新增] 规则引擎，纯 Java String/Regex
  config/
    QaRetrievalProperties.java          [新增] @ConfigurationProperties(prefix = "app.qa.retrieval")
```

#### 数据库迁移

```
src/main/resources/
  db/migration/
    V9__hybrid_search_tsvector.sql      [新增] content_tsv tsvector + GIN 索引
  application.yaml                      [修改] 新增 app.qa.retrieval.* 配置项
```

#### 测试

```
src/test/java/io/github/spike/myai/qa/
  infrastructure/
    retrieval/
      SparseRetrievalAdapterTest.java           [新增]
      HybridChunkRetrievalAdapterTest.java      [新增]
      ScopeFilterBuilderTest.java               [新增]
    reranking/
      NoOpRerankingAdapterTest.java             [新增]
    classifier/
      RuleBasedQueryClassifierTest.java         [新增]
    eval/
      EvalRunnerTest.java                       [新增] @SpringBootTest
      EvalMetrics.java                          [新增] Recall@5, MRR 计算
  application/
    service/
      AskQuestionApplicationServiceTest.java    [修改] 新增 QueryClassifier/Reranker 测试
  domain/
    model/
      RetrievedChunkTest.java                   [修改] 新增 score 字段测试

src/test/resources/eval/
  retrieval-qa-pairs.json                       [新增] 20 条 QA pairs
```

### Architectural Boundaries

**六边形分层边界：**

```
interfaces (不改动)
  │
  ▼
application (AskQuestionApplicationService)
  │ 依赖：ChunkRetrievalPort, RerankingPort, QueryClassifierPort, AnswerGenerationPort
  │ 不依赖：任何 infrastructure 类
  ▼
domain (RetrievedChunk, QueryType, Port 接口)
  │ 零框架注解
  │ 不依赖：Spring, JdbcTemplate, 任何 infrastructure 类
  ▼
infrastructure (Adapters)
  ├── retrieval/  ← Dense + Sparse + Hybrid + ScopeFilterBuilder
  ├── reranking/  ← NoOpRerankingAdapter
  ├── classifier/ ← RuleBasedQueryClassifier
  └── config/     ← QaRetrievalProperties
```

**关键约束：**

- `SparseRetrievalAdapter` 使用 `JdbcTemplate` 直接 SQL，不经过 Spring AI `VectorStore`
- `HybridChunkRetrievalAdapter` 注入 `PgVectorChunkRetrievalAdapter` 和 `SparseRetrievalAdapter`（adapter 内部组合，不跨层）
- `ScopeFilterBuilder` 依赖 Spring AI `Filter.Expression`（Dense 路径），因此必须在 infrastructure 层，不能放 domain
- `RuleBasedQueryClassifier` 零外部依赖，纯 Java，但仍放 infrastructure 层（实现端口）
- `QaRetrievalProperties` 带 `@ConfigurationProperties` + `@Validated`，放在 infrastructure/config/

### Requirements to Structure Mapping

| FR | 主要变更文件 | 测试文件 |
|----|-------------|---------|
| FR-1 | RetrievedChunk.java, PgVectorChunkRetrievalAdapter.java | RetrievedChunkTest.java |
| FR-2 | RerankingPort.java, NoOpRerankingAdapter.java, AskQuestionApplicationService.java | NoOpRerankingAdapterTest.java |
| FR-3 | QaRetrievalProperties.java, application.yaml, AskQuestionApplicationService.java | AskQuestionApplicationServiceTest.java |
| FR-4 | QueryClassifierPort.java, QueryType.java | RuleBasedQueryClassifierTest.java |
| FR-5 | RuleBasedQueryClassifier.java | RuleBasedQueryClassifierTest.java |
| FR-6 | AskQuestionApplicationService.java | AskQuestionApplicationServiceTest.java |
| FR-7 | V9__hybrid_search_tsvector.sql | （手动验证：\d vector_store） |
| FR-8 | SparseRetrievalAdapter.java, ScopeFilterBuilder.java | SparseRetrievalAdapterTest.java, ScopeFilterBuilderTest.java |
| FR-9 | HybridChunkRetrievalAdapter.java, ChunkRetrievalPort.java（不变） | HybridChunkRetrievalAdapterTest.java |
| FR-10 | AskQuestionApplicationService.java | AskQuestionApplicationServiceTest.java |
| FR-11 | EvalRunnerTest.java, EvalMetrics.java, retrieval-qa-pairs.json | EvalRunnerTest.java |

### Data Flow

```
用户提问
  │
  ▼
QaController → AskQuestionCommand
  │
  ▼
AskQuestionApplicationService
  ├── 1. QueryClassifierPort.classify(question) → QueryType
  │      └── RuleBasedQueryClassifier (规则匹配)
  ├── 2. [CHITCHAT?] → 跳到步骤 5
  ├── 3. ChunkRetrievalPort.similaritySearch(question, topK, scope)
  │      └── HybridChunkRetrievalAdapter (@Primary)
  │            ├── CompletableFuture: PgVectorChunkRetrievalAdapter.similaritySearch()  [Dense]
  │            ├── CompletableFuture: SparseRetrievalAdapter.similaritySearch()         [Sparse]
  │            └── RRF fusion → List<RetrievedChunk>
  ├── 4. RerankingPort.rerank(candidates, question, topN)
  │      └── NoOpRerankingAdapter (透传)
  ├── 5. AnswerGenerationPort.generateAnswer(prompt)
  │      └── ChatModelAnswerGenerationAdapter (LLM)
  └── 6. 返回 AskQuestionResult
```

### Integration Points

**内部通信：** 全部通过 domain port 接口，adapter 之间不互相引用。唯一例外：`HybridChunkRetrievalAdapter` 内部注入 `PgVectorChunkRetrievalAdapter` 和 `SparseRetrievalAdapter`（同层 adapter 组合，不跨层）。

**外部集成：** 无新外部服务。复用已有的 PostgreSQL（tsvector + GIN）、DashScope（LLM）、Spring AI VectorStore（Dense 检索）。

## Architecture Validation Results

### Coherence Validation ✅

**决策兼容性：**

- AD-1（不改端口）+ AD-2（RRF 在 adapter 内）+ AD-3（并行）完全一致 — 都指向同一个设计：`HybridChunkRetrievalAdapter` 是唯一新增的端口实现
- AD-4（ScopeFilterBuilder）与 AD-2 兼容 — Sparse 路径的 scope 过滤通过工具类实现，不破坏 adapter 边界
- AD-5（test-only eval）与 AD-1 兼容 — EvalRunner 注入具体 adapter 类，不依赖端口切换
- 无矛盾决策

**模式一致性：**

- 命名规范与 `project-context.md`（162 条规则）一致：record 数据对象、`Jdbc*` 前缀、`*Port` 端口命名
- RRF 常量 `private static final` 不暴露 — 符合"算法参数不外部化"模式
- 配置前缀 `app.qa.retrieval.*` — 与现有 `app.*` 体系一致

**结构对齐：**

- 所有新文件落在正确的六边形层：port → domain，adapter → infrastructure，config → infrastructure
- `ScopeFilterBuilder` 在 infrastructure 层（依赖 Spring AI 类型）— 正确
- `QueryType` 在 domain 层 — 零框架注解 — 正确

### Requirements Coverage Validation ✅

**FR 覆盖：** 11 个 MVP FR 全部有对应的架构支持和文件映射。

| FR | 架构支持 | 验证 |
|----|---------|------|
| FR-1 score 字段 | AD-1 + 实现模式 score 规则 | ✅ |
| FR-2 RerankingPort | 新端口 + NoOp 实现 | ✅ |
| FR-3 配置外部化 | QaRetrievalProperties | ✅ |
| FR-4 QueryClassifierPort | 新端口 + QueryType 枚举 | ✅ |
| FR-5 规则分类器 | RuleBasedQueryClassifier | ✅ |
| FR-6 集成分类 | ApplicationService 编排 | ✅ |
| FR-7 V9 迁移 | Flyway migration + 命名锁定 | ✅ |
| FR-8 SparseRetrievalAdapter | 新 adapter + ScopeFilterBuilder | ✅ |
| FR-9 Hybrid RRF | AD-1 + AD-2 + AD-3 | ✅ |
| FR-10 切换到 Hybrid | @Primary 注解 | ✅ |
| FR-11 EvalRunner | AD-5 test-only | ✅ |

**NFR 覆盖：**

| NFR | 架构支持 | 验证 |
|-----|---------|------|
| NFR-1 延迟 ≤ 200ms | AD-3 CompletableFuture 并行 | ✅ |
| NFR-2 零新依赖 | PG 原生 tsvector + GIN | ✅ |
| NFR-3 六边形合规 | port 在 domain，adapter 在 infrastructure | ✅ |

### Implementation Readiness Validation ✅

**决策完整性：** 5 个关键决策全部有选项对比、选择、理由。

**结构完整性：** 文件清单精确到包路径和变更类型（新增/修改/不变）。

**模式完整性：** 7 个实现模式有具体代码示例和禁止项。

### Gap Analysis Results

**Critical Gaps：** 无。

**Important Gaps（2 项）：**

| # | Gap | 影响 | 建议 |
|---|-----|------|------|
| G-1 | SparseRetrievalAdapter 实现 ChunkRetrievalPort 的 similaritySearch 语义 | BM25 检索不是"相似度搜索"，但 AD-1 要求它实现同一端口 | 方法名保持 similaritySearch（端口契约），内部实现用 ts_rank — 语义由 adapter 解释 |
| G-2 | @Primary vs @ConditionalOnProperty 的最终选择 | PRD 未明确是否需要配置切换 | MVP 用 @Primary（Hybrid 默认），不加 @ConditionalOnProperty — 保持简单，需要时再加 |

**Nice-to-Have Gaps：** 无。

### Architecture Completeness Checklist

**Requirements Analysis**

- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed
- [x] Technical constraints identified
- [x] Cross-cutting concerns mapped

**Architectural Decisions**

- [x] Critical decisions documented with versions
- [x] Technology stack fully specified
- [x] Integration patterns defined
- [x] Performance considerations addressed

**Implementation Patterns**

- [x] Naming conventions established
- [x] Structure patterns defined
- [x] Communication patterns specified
- [x] Process patterns documented

**Project Structure**

- [x] Complete directory structure defined
- [x] Component boundaries established
- [x] Integration points mapped
- [x] Requirements to structure mapping complete

### Architecture Readiness Assessment

**Overall Status：READY FOR IMPLEMENTATION**

**Confidence Level：** high

**Key Strengths：**

1. 5 个决策全部基于论文数据 + 代码调研，无拍脑袋
2. AD-1（不改端口）大幅降低了改动面和回归风险
3. 实现模式有具体代码示例，AI agent 可直接参照
4. 162 条 project-context 规则 + 7 条 feature-specific 规则，双层约束

**Areas for Future Enhancement：**

1. AD-6 QueryType 驱动权重（需 eval 基线数据）
2. AD-7 zhparser 中文分词（需 eval 数据证明 'simple' 不足）
3. G-2 @ConditionalOnProperty（需要运行时切换时再加）

### Implementation Handoff

**AI Agent Guidelines：**

- Follow all architectural decisions exactly as documented
- Use implementation patterns consistently across all components
- Respect project structure and boundaries
- Refer to this document for all architectural questions

**First Implementation Priority：**

Phase 1 — 基础准备：FR-1（RetrievedChunk score 字段）→ FR-2（RerankingPort）→ FR-3（配置外部化）
