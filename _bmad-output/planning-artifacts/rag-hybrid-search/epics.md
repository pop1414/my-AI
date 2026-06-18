---
stepsCompleted: ["step-01", "step-02", "step-03", "step-04"]
inputDocuments:
  - "_bmad-output/planning-artifacts/rag-hybrid-search/prd.md"
  - "_bmad-output/planning-artifacts/rag-hybrid-search/architecture.md"
---

# my-AI RAG Hybrid Search - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for my-AI RAG Hybrid Search, decomposing the requirements from the PRD and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

FR-1: RetrievedChunk record 新增 `double score` 字段，Dense 路径 score = 1 - cosine_distance，BM25 路径 score = ts_rank 值，RRF 路径 score = RRF 融合分，保持向后兼容（默认值 0.0）
FR-2: domain 层新增 RerankingPort 接口（`List<RetrievedChunk> rerank(candidates, question, topN)`），infrastructure 层 NoOpRerankingAdapter 透传实现，集成到 AskQuestionApplicationService 检索后、context 拼接前
FR-3: 检索参数配置外部化 — `MIN_RETRIEVAL_CANDIDATES` 和 `RETRIEVAL_CANDIDATE_MULTIPLIER` 从硬编码常量改为 `@ConfigurationProperties`，配置键 `app.qa.retrieval.min-candidates`（默认 20）和 `app.qa.retrieval.candidate-multiplier`（默认 4）
FR-4: domain 层新增 QueryClassifierPort 接口（`QueryType classify(String question)`），新增 QueryType 枚举（5 值：FACTOID/PROCEDURAL/COMPARATIVE/CHITCHAT/GENERAL），零框架注解
FR-5: infrastructure 层 RuleBasedQueryClassifier 实现，5 条优先级规则（CHITCHAT > PROCEDURAL > FACTOID > COMPARATIVE > GENERAL），纯 Java String/Regex，零外部依赖
FR-6: AskQuestionApplicationService 集成查询分类 — CHITCHAT 跳过检索直接调用 LLM，其他 QueryType 走现有检索流程，CHITCHAT 返回结果 references 为空列表
FR-7: Flyway V9 tsvector 迁移 — `content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED` + GIN 索引 `idx_vector_store_fts`，现有数据自动回填
FR-8: SparseRetrievalAdapter 实现 BM25 近似全文检索，使用 JdbcTemplate 直接 SQL + ts_rank 评分，复用 ScopeFilterBuilder 过滤逻辑，score = ts_rank 值
FR-9: HybridChunkRetrievalAdapter 实现 RRF 融合 — 组合 Dense + Sparse，RRF 公式 `Σ 1/(k + rank_i)`（k=60，等权重 0.5/0.5），同 chunk 双路命中时分数叠加，结果按 RRF 分降序
FR-10: AskQuestionApplicationService 切换到 Hybrid Search — 注入 HybridChunkRetrievalAdapter（@Primary），将 similaritySearch() 调用替换为走 Hybrid 路径，CHITCHAT 仍跳过检索
FR-11: EvalRunner Layer 1 检索质量评估 — Recall@5 + MRR 指标，20 条手写 QA pairs（每种 QueryType 至少 3 条），JSON 格式测试数据，直接调用 ChunkRetrievalPort，输出 JSON 报告
FR-12: EvalRunner Layer 2 生成质量评估 — Faithfulness + Answer Relevancy 指标，10 条手写 QA pairs，DashScope qwen-max 作为 Judge 模型，1-5 分结构化评分（MVP 范围外，Phase 2）
FR-13: EvalRunner CLI 入口 — 独立 main 方法或 SpringBootTest 触发，支持 Layer 1 only 或 Layer 1+2，输出 JSON 到 `target/eval-report-{timestamp}.json`（MVP 范围外，Phase 2）

### NonFunctional Requirements

NFR-1: Hybrid Search 端到端延迟增量（相比纯 Dense）不超过 200ms — Dense/Sparse 必须并行执行，RRF 纯内存计算 < 1ms
NFR-2: 零新外部依赖 — 不引入新 Maven 依赖、不引入新 Docker 容器，BM25 使用 PostgreSQL 原生 tsvector + GIN 索引
NFR-3: 六边形架构合规 — QueryClassifierPort/RerankingPort 定义在 domain 层，实现类在 infrastructure 层，domain 层零框架注解，adapter 之间不互相引用（HybridChunkRetrievalAdapter 内部组合除外）

### Additional Requirements

AD-1: ChunkRetrievalPort 接口不修改 — 新增 HybridChunkRetrievalAdapter 作为 ChunkRetrievalPort 的新实现者，@Primary 注解切换，EvalRunner 对比时注入具体 adapter 类
AD-2: RRF 融合位置在 HybridChunkRetrievalAdapter 内部透明完成 — 对调用方完全隐藏，ApplicationService 代码不变只换注入的实现者
AD-3: Dense/Sparse 并行执行 — CompletableFuture.supplyAsync() 并行，降级策略：单路失败 → 降级到另一路结果，日志 WARN 不抛异常，两路都失败 → 返回空 list
AD-4: ScopeFilterBuilder 工具类 — package-private，放在 infrastructure/retrieval/ 包下，供 Dense 和 Sparse 路径共用 scope 过滤逻辑，提供 toFilterExpression()（Dense）和 toSqlCondition()（Sparse）两个方法
AD-5: EvalRunner 定位为 Test-only 组件 — 放在 src/test/java/，通过 @SpringBootTest 按需加载，QA pairs 在 src/test/resources/eval/
AD-6: QueryType 枚举值严格使用 PRD 定义的 5 个值（FACTOID/PROCEDURAL/COMPARATIVE/CHITCHAT/GENERAL），包位置 qa/domain/model/
AD-7: 配置属性前缀 `app.qa.retrieval.*`，配置类 QaRetrievalProperties 带 @ConfigurationProperties + @Validated
AD-8: Flyway V9 对象命名严格一致 — 列名 content_tsv、索引名 idx_vector_store_fts、配置 'simple'
AD-9: RRF 常量（RRF_K=60, DENSE_WEIGHT=0.5, SPARSE_WEIGHT=0.5）作为 HybridChunkRetrievalAdapter 的 private static final 常量，不放配置文件
AD-10: RetrievedChunk.score 语义由调用链决定，不引入 RetrievalMethod 枚举标识检索路径
AD-11: 代码净增 ≤ 800 LOC（含测试）
AD-12: EvalRunner 三模式对比并行执行使用 Java 21 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()`），不引入额外线程池配置

### UX Design Requirements

不适用 — 本次 RAG Hybrid Search 是纯后端检索链路优化，无前端 UI 变更。

### FR Coverage Map

| FR | Epic | 描述 |
|----|------|------|
| FR-1 | Epic 1 | RetrievedChunk score 字段 |
| FR-2 | Epic 1 | RerankingPort 接口钩子 |
| FR-3 | Epic 1 | 检索参数配置外部化 |
| FR-4 | Epic 1 | QueryClassifierPort 接口 |
| FR-5 | Epic 1 | RuleBasedQueryClassifier 实现 |
| FR-6 | Epic 1 | CHITCHAT 拦截集成 |
| FR-7 | Epic 2 | Flyway V9 tsvector 迁移 |
| FR-8 | Epic 2 | SparseRetrievalAdapter BM25 |
| FR-9 | Epic 2 | HybridChunkRetrievalAdapter RRF 融合 |
| FR-10 | Epic 2 | 应用层切换 Hybrid Search |
| FR-11 | Epic 3 | EvalRunner Layer 1 检索质量评估 |
| FR-12 | Phase 2 延后 | EvalRunner Layer 2 生成质量评估 |
| FR-13 | Phase 2 延后 | EvalRunner CLI 入口 |

## Epic List

### Epic 1: RAG 检索基础设施升级 — 扩展点与配置化

系统具备检索质量透明度（score）、可插拔重排序接口、可配置检索参数、以及查询意图分类能力 — 为后续混合检索和智能路由奠定基础。

**FRs covered:** FR-1, FR-2, FR-3, FR-4, FR-5, FR-6

### Epic 2: Hybrid Search 多路融合检索

系统同时利用语义向量和关键词两路检索，对精确技术术语的召回率显著提升（Recall@5 ≥ 0.70）。

**FRs covered:** FR-7, FR-8, FR-9, FR-10

### Epic 3: RAG 检索质量评估体系

开发者有量化工具（Recall@5、MRR）验证检索参数调优效果，从"靠直觉"升级为"数据驱动"。

**FRs covered:** FR-11（FR-12、FR-13 Phase 2 延后）

## Epic 1: RAG 检索基础设施升级 — 扩展点与配置化

系统具备检索质量透明度（score）、可插拔重排序接口、可配置检索参数、以及查询意图分类能力 — 为后续混合检索和智能路由奠定基础。

### Story 1.1: RetrievedChunk 添加 score 字段

作为开发者，
我希望检索结果包含置信度评分，
以便后续多路检索融合有统一的评分基础。

**Acceptance Criteria:**

**Given** `RetrievedChunk` record 当前只有 documentId、kbId、chunkIndex、content 字段
**When** 添加 `double score` 字段
**Then** 新增简化构造器 `RetrievedChunk(documentId, kbId, chunkIndex, content, score)`
**And** 现有无参构造器 score 默认值为 `0.0`（不是 -1 或 NaN）
**And** `PgVectorChunkRetrievalAdapter.toRetrievedChunk()` 正确映射相似度到 score（`1.0 - cosine_distance`）
**And** 所有现有测试通过
**And** 不引入 `RetrievalMethod` 枚举 — score 语义由调用链决定（AD-10）

### Story 1.2: RerankingPort 可插拔接口

作为开发者，
我希望系统预留重排序扩展点，
以便未来引入 Reranking 能力时无需修改应用层代码。

**Acceptance Criteria:**

**Given** domain 层需要一个重排序端口接口
**When** 定义 `RerankingPort` 接口（`qa/domain/port/` 包下）
**Then** 方法签名为 `List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN)`
**And** 接口零框架注解
**And** infrastructure 层新增 `NoOpRerankingAdapter`（`qa/infrastructure/reranking/` 包下），透传输入列表的前 topN 条
**And** `AskQuestionApplicationService` 在检索后、context 拼接前注入并调用 `RerankingPort`
**And** 默认注入 `NoOpRerankingAdapter`，现有行为不变
**And** `NoOpRerankingAdapterTest` 验证透传逻辑和 topN 截断

### Story 1.3: 检索参数配置外部化

作为开发者，
我希望检索参数通过配置文件管理而非硬编码，
以便无需重新编译即可调整检索行为。

**Acceptance Criteria:**

**Given** `AskQuestionApplicationService` 中 `MIN_RETRIEVAL_CANDIDATES = 20` 和 `RETRIEVAL_CANDIDATE_MULTIPLIER = 4` 为硬编码常量
**When** 新增 `QaRetrievalProperties` 配置类（`qa/infrastructure/config/` 包下）
**Then** 配置类带 `@ConfigurationProperties(prefix = "app.qa.retrieval")` + `@Validated`
**And** 包含 `minCandidates`（默认 20）和 `candidateMultiplier`（默认 4）字段
**And** `application.yaml` 新增 `app.qa.retrieval` 配置段
**And** `AskQuestionApplicationService` 注入 `QaRetrievalProperties` 替代硬编码常量
**And** 修改配置后重启生效，无需重新编译
**And** 默认值与原硬编码值一致
**And** 对应测试验证配置注入正确

### Story 1.4: 查询分类端口与 QueryType 定义

作为开发者，
我希望系统能对用户查询进行意图分类，
以便不同类型查询走不同的检索策略。

**Acceptance Criteria:**

**Given** domain 层需要查询分类能力
**When** 新增 `QueryType` 枚举（`qa/domain/model/` 包下）
**Then** 枚举值严格为 5 个：`FACTOID` / `PROCEDURAL` / `COMPARATIVE` / `CHITCHAT` / `GENERAL`（不多不少）
**And** 零框架注解
**When** 新增 `QueryClassifierPort` 接口（`qa/domain/port/` 包下）
**Then** 方法签名为 `QueryType classify(String question)`
**And** 接口零框架注解
**And** 两个文件均不依赖任何 Spring 或第三方库

### Story 1.5: 规则引擎查询分类器实现

作为开发者，
我希望基于优先级规则的查询分类器能区分闲聊、操作、事实、比较等意图，
以便系统根据查询类型选择合适的处理路径。

**Acceptance Criteria:**

**Given** `QueryClassifierPort` 接口和 `QueryType` 枚举已就位
**When** 新增 `RuleBasedQueryClassifier`（`qa/infrastructure/classifier/` 包下）
**Then** 实现 5 条优先级规则（首个命中即返回）：

| 优先级 | QueryType | 匹配规则 | 示例 |
|--------|-----------|---------|------|
| 1（最高） | CHITCHAT | 问候/感谢/闲聊关键词 | "你好"、"谢谢"、"今天天气" |
| 2 | PROCEDURAL | 疑问词 + 操作动词 | "如何配置 Flyway"、"怎么实现" |
| 3 | FACTOID | 疑问词 + 定义/概念 | "什么是向量数据库" |
| 4 | COMPARATIVE | 比较关键词 | "Spring AI 和 LangChain 区别" |
| 5（默认） | GENERAL | 以上均不匹配 | "文档管理" |

**And** 纯 Java String/Regex 实现，零外部依赖，零 Spring 注解
**And** 混合意图查询按优先级匹配（有对应测试用例）
**And** 空字符串/null 返回 GENERAL
**And** 每种 QueryType 至少 3 个测试用例
**And** CHITCHAT 优先级高于所有其他类型（有专门测试用例）

### Story 1.6: 应用层集成查询分类 — CHITCHAT 拦截

作为用户，
我希望输入闲聊内容时系统跳过检索直接回答，
以便获得更快的响应速度（延迟降低 ≥50%）。

**Acceptance Criteria:**

**Given** `QueryClassifierPort` 和 `RuleBasedQueryClassifier` 已就位
**When** `AskQuestionApplicationService` 注入 `QueryClassifierPort`
**Then** 在检索前调用 `classify(question)` 判断查询类型
**And** CHITCHAT：跳过 `ChunkRetrievalPort` 调用，直接调用 `AnswerGenerationPort.generateAnswer(question)` 返回
**And** CHITCHAT 返回结果中 `references` 为空列表
**And** 非 CHITCHAT：走现有检索流程（行为不变）
**And** CHITCHAT 输入不触发 `ChunkRetrievalPort` 调用（可 mock 验证）
**And** 对应测试覆盖 CHITCHAT 和非 CHITCHAT 两条路径

## Epic 2: Hybrid Search 多路融合检索

系统同时利用语义向量和关键词两路检索，对精确技术术语的召回率显著提升（Recall@5 ≥ 0.70）。

### Story 2.1: Flyway V9 tsvector 全文检索基础设施

作为开发者，
我希望数据库支持全文检索索引，
以便 BM25 稀疏检索路径有底层基础设施支撑。

**Acceptance Criteria:**

**Given** `vector_store` 表有 `content` 列但无全文检索支持
**When** 新增 `V9__hybrid_search_tsvector.sql`
**Then** 添加 `content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED` 生成列
**And** 创建 GIN 索引 `idx_vector_store_fts ON vector_store USING GIN (content_tsv)`
**And** 使用 `'simple'` 文本搜索配置（AD-8 命名锁定）
**And** 迁移执行后现有数据 `content_tsv` 自动回填
**And** 中文内容逐字 token，英文内容按词 token
**And** Flyway 迁移不修改已执行的 V1-V8

### Story 2.2: ScopeFilterBuilder 共享工具类

作为开发者，
我希望 Dense 和 Sparse 两路检索共享 scope 过滤逻辑，
以便避免代码重复，D20 Group Model 迁移时集中维护。

**Acceptance Criteria:**

**Given** Dense 路径（`PgVectorChunkRetrievalAdapter`）已有 scope 过滤逻辑，Sparse 路径也需要相同逻辑
**When** 提取 `ScopeFilterBuilder`（`qa/infrastructure/retrieval/` 包下，package-private class）
**Then** 提供 `toFilterExpression(List<AskableDocumentVersion> scope)` 方法（Dense 路径，返回 Spring AI `Filter.Expression`）
**And** 提供 `toSqlCondition(List<AskableDocumentVersion> scope)` 方法（Sparse 路径，返回 `SqlScopeCondition` record）
**And** `SqlScopeCondition` record 包含 `String whereClause` 和 `List<Object> params`
**And** `PgVectorChunkRetrievalAdapter` 重构为使用 `ScopeFilterBuilder`
**And** 重构后现有 Dense 检索行为不变（所有现有测试通过）
**And** `ScopeFilterBuilderTest` 覆盖两种输出格式的正确性

### Story 2.3: SparseRetrievalAdapter BM25 全文检索

作为开发者，
我希望系统具备基于关键词的稀疏检索能力，
以便精确技术术语（如 "Flyway"、"PGVector"）能被准确召回。

**Acceptance Criteria:**

**Given** Flyway V9 迁移已创建 `content_tsv` tsvector 列和 GIN 索引，`ScopeFilterBuilder` 已就位
**When** 新增 `SparseRetrievalAdapter`（`qa/infrastructure/retrieval/` 包下）
**Then** 实现 `ChunkRetrievalPort` 接口的 `similaritySearch` 方法（AD-1：不改端口接口）
**And** 使用 `JdbcTemplate` 直接执行 SQL，不经过 Spring AI `VectorStore`
**And** 查询语句：`SELECT id, content, metadata, ts_rank(content_tsv, query) AS rank FROM vector_store, plainto_tsquery('simple', ?) query WHERE content_tsv @@ query [AND scope_filter] ORDER BY rank DESC LIMIT ?`
**And** 使用 `ScopeFilterBuilder.toSqlCondition()` 复用 scope 过滤逻辑
**And** score 映射：`RetrievedChunk.score = ts_rank` 值
**And** 包含 "Flyway" 关键词的查询能检索到含 "Flyway" 的 chunk（有测试用例）
**And** scope 过滤正确（只检索 askable 版本的 chunk）
**And** 空查询返回空列表

### Story 2.4: HybridChunkRetrievalAdapter RRF 融合与应用层切换

作为用户，
我希望系统同时利用语义和关键词两路检索并融合结果，
以便精确术语和语义相关的内容都能被召回，检索质量提升（Recall@5 ≥ 0.70）。

**Acceptance Criteria:**

**Given** Dense（`PgVectorChunkRetrievalAdapter`）和 Sparse（`SparseRetrievalAdapter`）两路检索已就位
**When** 新增 `HybridChunkRetrievalAdapter`（`qa/infrastructure/retrieval/` 包下）
**Then** 实现 `ChunkRetrievalPort` 接口的 `similaritySearch` 方法（不修改端口接口，AD-1）
**And** `similaritySearch()` 内部编排：① 并行调 Dense + Sparse → ② RRF 融合 → ③ 返回 `List<RetrievedChunk>`
**And** RRF 融合算法：`score = Σ 1/(k + rank_i)`，`k=60`，`DENSE_WEIGHT=0.5`，`SPARSE_WEIGHT=0.5`（private static final 常量，AD-9）
**And** 同一 chunk 双路命中时 RRF 分数叠加，结果按 RRF 分降序
**And** Dense/Sparse 使用 `CompletableFuture.supplyAsync()` 并行执行（NFR-1：延迟增量 ≤200ms，AD-3）
**And** 降级策略：单路失败 → 降级到另一路结果（`exceptionally()` 返回空 list，日志 WARN），两路都失败 → 返回空 list
**And** 类标注 `@Primary`，替代 `PgVectorChunkRetrievalAdapter` 作为默认 `ChunkRetrievalPort` 实现
**And** `AskQuestionApplicationService` 无需任何代码改动（AD-2：RRF 对调用方透明）
**And** CHITCHAT 请求仍跳过检索（Epic 1 Story 1.6 行为不变）
**And** 同一查询 Hybrid 结果的 top-5 与纯 Dense 结果有差异（证明 BM25 生效）
**And** RRF 分数计算正确（手工验证 1-2 个 case）

## Epic 3: RAG 检索质量评估体系

开发者有量化工具（Recall@5、MRR、HitRate@5）验证检索参数调优效果，并支持 Dense/Sparse/Hybrid 三模式对比，从"靠直觉"升级为"数据驱动"。

### Story 3.1: EvalRunner 检索质量评估（三模式对比 + 分层报告）

作为开发者，
我希望有量化工具衡量检索质量，并能对比 Dense、Sparse、Hybrid 三种检索模式的效果差异，
以便精准定位检索链路瓶颈，数据驱动地验证参数调优效果。

**实现分阶段交付（3.1a → 3.1b → 3.1c），每阶段有独立可验证交付物。**

#### 阶段 3.1a：数据模型 + DatasetLoader + 校验

**Acceptance Criteria:**

**Given** 需要标准化的评测数据集格式
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

**And** `query_type` 取值必须为 `QueryType` 枚举的 5 个值之一
**And** `relevance_levels` 中每个 `relevant_doc_ids` 中的 ID 必须有对应的 relevance 标注
**And** 基础指标（Recall@5、MRR、HitRate@5）仅统计 `strong` 强相关文档，`weak` 预留用于后续 NDCG 加权
**And** 加载器包含格式校验 — 缺失必填字段（question / query_type / relevant_doc_ids）时抛出语义明确的 `IllegalArgumentException`，禁止静默失败
**And** 测试数据：20 条手写 QA pairs（`src/test/resources/eval/retrieval-qa-pairs.json`），每种 QueryType 至少 3 条
**And** `RetrievalEvalDatasetLoaderTest` 覆盖正常加载和格式校验异常场景

#### 阶段 3.1b：MetricsCalculator + Executor + 单模式可跑

**Acceptance Criteria:**

**Given** 数据集加载器就位
**When** 新增 `EvalMetricsCalculator` 和 `RetrievalEvalExecutor`
**Then** `EvalMetricsCalculator` 为纯工具类 — 无状态、零 Spring 依赖，所有指标计算均为 **static 纯函数**
**And** 核心指标：
- `Recall@5`：top-5 中命中 strong 相关文档的比例
- `MRR`：第一个 strong 相关结果排名的倒数的均值
- `HitRate@5`：top-5 中至少命中 1 条 strong 相关文档的查询占比

**And** 预留 NDCG@K 扩展接口 — `EvalMetricsCalculator` 中定义 `ndcgAtK(results, relevanceLevels, k)` 方法签名，返回 `double`，本期抛 `UnsupportedOperationException("NDCG@K: Phase 2")`，后续实现无需重构现有代码
**And** 边界情况处理：
- 相关文档列表为空 → Recall 默认返回 1.0，HitRate 默认返回 1.0
- 无任何命中 → MRR 返回 0.0
- 严格避免除零异常

**And** `RetrievalEvalExecutor` 封装检索调用逻辑 — 支持批量执行，隔离评测逻辑与业务检索接口
**And** `EvalMetricsCalculatorTest` 单元测试覆盖：正常用例、全部命中、零命中、空相关文档列表、单条结果等边界场景

#### 阶段 3.1c：三模式对比 + ReportGenerator + 集成

**Acceptance Criteria:**

**Given** MetricsCalculator 和 Executor 就位
**When** 扩展 Executor 支持三种检索模式
**Then** 三种模式通过直接注入具体 adapter 类实现（非 Port 接口多态）：
- **纯向量检索模式**：注入 `PgVectorChunkRetrievalAdapter`
- **纯关键词检索模式**：注入 `SparseRetrievalAdapter`
- **混合检索模式（默认）**：注入 `HybridChunkRetrievalAdapter`

**And** 三模式对比使用 **Java 21 虚拟线程**并行执行（`Executors.newVirtualThreadPerTaskExecutor()`），三种模式共享同一份数据集同时跑（AD-12）
**And** 性能约束：单模式 ≤ 5 秒，三模式对比 ≤ 15 秒（20 条样本）

**When** 新增 `EvalReportGenerator` 结构化组装评测结果
**Then** JSON 报告为三级结构：
1. **整体汇总层**：Recall@5、MRR、HitRate@5、总查询数、单条平均检索耗时
2. **分类型统计层**：按 `query_type` 分组统计各类型的三项指标均值，快速定位哪类查询效果最差
3. **单条详情层**：查询内容、query_type、检索返回的 ID 列表、标注的相关 ID 列表、命中标记、单条指标得分

**And** 三模式对比时，报告包含三个模式的独立汇总 + 对比表
**And** `mvn test -Dtest=EvalRunnerTest` 可触发完整混合检索评测（保留原有触发方式）
**And** 全程不调用大模型、无外部网络请求
**And** 所有组件仅在 test 作用域生效，不侵入任何生产代码、不影响主业务打包
