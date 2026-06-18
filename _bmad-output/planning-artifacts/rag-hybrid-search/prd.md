---
title: RAG 检索链路优化 — Hybrid Search + Query Classification + Eval 体系
status: final
created: 2026-06-16
updated: 2026-06-16
---

# RAG 检索链路优化 PRD

## 0. 文档用途

本文档定义 my-AI RAG 检索链路优化的功能需求与验收标准。

**受众**：后端开发（spike）、代码审查者。

**范围**：覆盖 5 个已锁定决策点（D3/D4/D5/D6/D17）的实施需求，不涉及 docling-upgrade（D11/D22）的解析管道变更。

**前置文档**：
- 决策 Register：`_bmad-output/planning-artifacts/research/decision-register.md`（D3—D17 已锁定）
- 论文：`docs/reference/RAG/`（EMNLP 2024, "Searching for Best Practices in RAG"）
- 现有架构：`docs/architecture/overview.md`

---

## 1. 愿景

### 问题陈述

当前 RAG 检索链路存在三个结构性缺陷：

1. **单一检索路径**：仅 `COSINE_DISTANCE` 向量检索（`PgVectorChunkRetrievalAdapter`），对稀有术语和精确关键词匹配无覆盖。用户搜索 "PGVector HNSW 索引配置" 时，Dense 向量可能返回语义相关但不含精确术语的 chunk。

2. **无查询路由**：所有用户输入（包括"你好""今天天气"）都走完整 RAG pipeline——向量检索 → 拼接 context → LLM 生成。闲聊查询触发无意义的向量检索，浪费延迟预算且可能引入无关 chunk 干扰 LLM。

3. **无量化评估基线**：每次修改检索参数或策略后，无法判断效果变好还是变坏。没有 Recall@5、MRR 等指标，参数调优靠直觉。

### 目标

实施 Hybrid Search（Dense + BM25 + RRF 融合）+ Query Classification（规则路由）+ 轻量 Eval 体系，将 RAG 检索从"单路盲检"升级为"多路融合 + 智能路由 + 数据驱动调优"。

### 论文依据

EMNLP 2024（Fudan University, Table 1 消融实验）：

| 模块 | 效果增量 | 延迟增量 | 本项目决策 |
|------|----------|----------|-----------|
| Hybrid Search（Dense + BM25） | avg score +12%（0.383→0.429） | +0.01s（1.44→1.45s） | ✅ 实施（D3） |
| Query Classification | avg score +5%（0.422→0.443），延迟 -30% | -5.0s（16.58→11.58s） | ✅ 规则版（D5） |
| Reranking（monoT5） | avg score +3%（0.430→0.443） | +1.4s（10.31→11.71s） | ❌ 仅留接口（D4） |
| Summarization（Recomp） | avg score +1.1%（0.441→0.446） | +0.73s | ❌ 维持截断（D6） |

---

## 2. 目标用户

### 2.1 Jobs To Be Done

| # | JTBD | 对应决策 |
|---|------|---------|
| JTBD-1 | 当用户输入精确技术术语时，系统应同时利用语义和关键词两路检索，提高召回率 | D3 |
| JTBD-2 | 当用户输入闲聊内容时，系统应跳过检索直接回答，减少无意义延迟 | D5 |
| JTBD-3 | 当开发者调优检索参数时，应有量化指标（Recall@5、MRR）判断效果变化 | D17 |
| JTBD-4 | 当检索参数（候选放大倍率、最小候选数）需要调整时，应通过配置文件修改而非改代码 | D6 |
| JTBD-5 | 当未来需要引入重排序能力时，架构应预留可插拔接口 | D4 |

### 2.2 关键用户旅程

**UJ-1：精确术语问答**
> **角色**：spike（后端开发者），正在排查一个 Flyway 迁移问题，需要从项目知识库中找到精确的配置步骤。

1. spike 输入 "Spring Boot 如何配置 Flyway 数据库迁移"
2. 系统通过 QueryClassifier 判定为 `PROCEDURAL`（→ FR-4/FR-5/FR-6）
3. Hybrid Search 双路检索（→ FR-9）：Dense 路径匹配语义相关的 Spring Boot 配置类 chunk，BM25 路径精确匹配含 "Flyway" 关键词的 chunk
4. RRF 融合后 top-K chunks 同时覆盖语义相关性和关键词精确性（→ FR-9）
5. LLM 基于高质量 chunks 生成包含具体步骤的回答

**UJ-2：闲聊拦截**
> **角色**：小明（新用户），第一次打开系统，随手输入了一句问候，期望得到快速响应。

1. 小明输入 "你好"
2. 系统通过 QueryClassifier 判定为 `CHITCHAT`（→ FR-4/FR-5/FR-6）
3. 跳过检索，直接调用 LLM 生成友好回复（→ FR-6）
4. 响应延迟从 ~2s（检索 + 生成）降至 ~0.5s（仅生成）`[估算，待实测校准]`

**UJ-3：效果回归检测**
> **角色**：spike（后端开发者），刚修改了 RRF 融合参数，需要量化验证改动是否有效。

1. spike 修改 RRF 融合参数后（→ FR-9）
2. 运行 `EvalRunner`（→ FR-11），30 秒内获得 Recall@5 和 MRR 指标
3. 与基线对比：Recall@5 从 0.72 升至 0.78，确认改动有效
4. 提交代码，eval 结果记录在 commit message 中

---

## 3. 术语表

| 术语 | 定义 |
|------|------|
| **Dense 检索** | 基于向量嵌入的语义相似度检索（pgvector COSINE_DISTANCE） |
| **Sparse 检索** | 基于关键词的全文检索（PostgreSQL tsvector + ts_rank） |
| **BM25** | 经典稀疏检索算法，用于关键词相关性评分（本项目用 PostgreSQL ts_rank 近似实现，详见 FR-8） |
| **RRF** | Reciprocal Rank Fusion，多路检索结果融合算法，公式：score = Σ 1/(k + rank_i)，k=60 |
| **QueryType** | 查询分类结果枚举：FACTOID / PROCEDURAL / COMPARATIVE / CHITCHAT / GENERAL |
| **Recall@K** | 在 top-K 检索结果中，命中相关文档的比例 |
| **MRR** | Mean Reciprocal Rank，第一个相关结果排名的倒数的均值 |
| **Faithfulness** | LLM 生成的答案是否仅基于检索到的参考片段（LLM judge 评估） |
| **Answer Relevancy** | LLM 生成的答案是否回答了用户的原始问题（LLM judge 评估） |
| **tsvector** | PostgreSQL 内置的全文检索数据类型，存储文档的词项（token）集合 |
| **GIN 索引** | Generalized Inverted Index，PostgreSQL 的倒排索引类型，支持 tsvector 高效检索 |
| **`'simple'` 配置** | PostgreSQL 内置的文本搜索配置，按非字母数字字符分割 token，中文逐字拆分 |

---

## 4. 功能需求

### 功能组索引

| 功能组 | 决策 | FR 编号 | 估算 |
|--------|------|---------|------|
| 4.1 基础准备 | D3/D4/D6 | FR-1, FR-2, FR-3 | ~半天 |
| 4.2 查询分类 | D5 | FR-4, FR-5, FR-6 | ~1 天 |
| 4.3 混合检索 | D3 | FR-7, FR-8, FR-9, FR-10 | ~2 天 |
| 4.4 评估体系 | D17 | FR-11, FR-12, FR-13 | ~1.5 天 |

**依赖图**：

```
4.1 基础准备（无依赖）
  ↓
4.2 查询分类（依赖 FR-1 score 字段）
  ↓
4.3 混合检索（依赖 FR-1 score + FR-6 应用层集成点）
  ↓
4.4 评估体系（依赖 FR-9 Hybrid Search 上线）
```

---

### 4.1 基础准备

#### FR-1：RetrievedChunk 添加 score 字段

**当前状态**：`RetrievedChunk` record 无 score 字段，检索结果丢失置信度信息。

**需求**：
- `RetrievedChunk` record 新增 `double score` 字段
- Dense 检索路径：score = 1 - cosine_distance（Spring AI 返回的相似度）`[ASSUMPTION: Spring AI similaritySearch 返回值可直接用于 cosine similarity 计算，见 A-1]`
- BM25 检索路径：score = ts_rank 值
- RRF 融合路径：score = RRF 融合分
- 保持向后兼容：新增简化构造器 `RetrievedChunk(documentId, kbId, chunkIndex, content, score)`

**验收标准**：
- [ ] `RetrievedChunk` record 包含 `double score` 字段
- [ ] `PgVectorChunkRetrievalAdapter.toRetrievedChunk()` 正确映射相似度到 score
- [ ] 所有现有测试通过（score 默认值 0.0 不影响现有逻辑）

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java`（修改）
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`（修改）
- 对应测试文件（修改）

---

#### FR-2：RerankingPort 接口钩子

**需求**：
- 在 domain 层新增 `RerankingPort` 接口：`List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN)`
- infrastructure 层新增 `NoOpRerankingAdapter`：透传输入，零开销
- 集成点：`AskQuestionApplicationService` 中检索后、context 拼接前

**验收标准**：
- [ ] `RerankingPort` 接口定义在 `qa/domain/port/` 包下
- [ ] `NoOpRerankingAdapter` 实现透传，返回输入列表的前 topN 条
- [ ] `AskQuestionApplicationService` 在检索后调用 rerank（默认 NoOp）
- [ ] 现有行为不变（NoOp 透传）

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/domain/port/RerankingPort.java`（新增）
- `src/main/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapter.java`（新增）
- `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`（修改）

---

#### FR-3：检索参数配置外部化

**当前状态**：`AskQuestionApplicationService` 中 `MIN_RETRIEVAL_CANDIDATES = 20` 和 `RETRIEVAL_CANDIDATE_MULTIPLIER = 4` 为硬编码常量。

**需求**：
- 通过 `@ConfigurationProperties` 或 `@Value` 从 `application.yaml` 读取
- 配置键：`app.qa.retrieval.min-candidates`（默认 20）、`app.qa.retrieval.candidate-multiplier`（默认 4）
- 移除硬编码常量

**验收标准**：
- [ ] 配置项在 `application.yaml` 中可设置
- [ ] 默认值与当前硬编码值一致（20 和 4）
- [ ] 修改配置后无需重新编译即可生效（重启生效）

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`（修改）
- `src/main/resources/application.yaml`（修改）

---

### 4.2 查询分类

#### FR-4：QueryClassifierPort 接口

**需求**：
- domain 层新增 `QueryClassifierPort` 接口：`QueryType classify(String question)`
- 新增 `QueryType` 枚举（5 值）：`FACTOID` / `PROCEDURAL` / `COMPARATIVE` / `CHITCHAT` / `GENERAL`

**验收标准**：
- [ ] `QueryClassifierPort` 定义在 `qa/domain/port/` 包下
- [ ] `QueryType` 定义在 `qa/domain/model/` 包下
- [ ] 接口和枚举零框架注解

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/domain/port/QueryClassifierPort.java`（新增）
- `src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java`（新增）

---

#### FR-5：RuleBasedQueryClassifier 实现

**需求**：
- infrastructure 层实现 `RuleBasedQueryClassifier`
- 分类规则（5 条核心规则，上限 10 条）：

| 优先级 | QueryType | 匹配规则 | 示例 |
|--------|-----------|---------|------|
| 1（最高） | CHITCHAT | 问候/感谢/闲聊关键词（"你好""谢谢""天气"） | "你好"、"今天天气怎么样" |
| 2 | PROCEDURAL | 疑问词 + 操作动词（"怎么""如何""配置""实现"） | "如何配置 Flyway" |
| 3 | FACTOID | 疑问词 + 定义/概念（"什么是""定义""介绍"） | "什么是向量数据库" |
| 4 | COMPARATIVE | 比较关键词（"对比""区别""vs""哪个好"） | "Spring AI 和 LangChain 的区别" |
| 5（默认） | GENERAL | 以上均不匹配时的兜底 | "文档管理" |

- 纯 Java String/Regex 实现，零外部依赖
- 冲突解决：Chitchat 最高优先级（直接拦截），其余按规则顺序匹配，首个命中即返回

**验收标准**：
- [ ] 每种 QueryType 至少 3 个测试用例
- [ ] CHITCHAT 优先级高于所有其他类型
- [ ] 混合意图查询按优先级匹配（PROCEDURAL > COMPARATIVE > FACTOID > GENERAL），有对应测试用例
- [ ] 空字符串/null 返回 GENERAL
- [ ] 纯 Java，无 Spring 注解（`@Component` 由配置类注册）

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifier.java`（新增）
- 对应测试文件（新增）

---

#### FR-6：AskQuestionApplicationService 集成查询分类

**需求**：
- 注入 `QueryClassifierPort`
- 在检索前调用 `classify(question)`
- CHITCHAT：跳过检索，直接调用 `AnswerGenerationPort.generateAnswer(question)` 返回
- 其他 QueryType：走现有检索流程（Phase 3 后走 Hybrid Search）

**验收标准**：
- [ ] CHITCHAT 输入不触发 `ChunkRetrievalPort` 调用（可 mock 验证）
- [ ] 非 CHITCHAT 输入行为不变
- [ ] 返回结果中 references 为空列表（CHITCHAT 无检索引用）

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`（修改）
- 对应测试文件（修改）

---

### 4.3 混合检索

#### FR-7：Flyway V9 tsvector 迁移

**需求**：
- 新增 `V9__hybrid_search_tsvector.sql`
- 为 `vector_store.content` 添加 `content_tsv tsvector` 生成列（`GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED`）
- 创建 GIN 索引 `idx_vector_store_fts ON vector_store USING GIN (content_tsv)`
- 使用 `'simple'` 文本搜索配置（中文逐字拆分，英文按词拆分）`[ASSUMPTION: 'simple' 配置对英文技术术语分词质量足够，见 A-2]`

**验收标准**：
- [ ] migration 执行后，现有数据的 `content_tsv` 自动回填
- [ ] GIN 索引创建成功（可通过 `\d vector_store` 验证）
- [ ] 中文内容 `to_tsvector('simple', content)` 返回逐字 token
- [ ] 英文内容返回按词 token

**影响文件**：
- `src/main/resources/db/migration/V9__hybrid_search_tsvector.sql`（新增）

---

#### FR-8：SparseRetrievalAdapter BM25 检索

**需求**：
- 新增 `SparseRetrievalAdapter`，实现 BM25 近似全文检索
- 使用 `JdbcTemplate` 直接执行 SQL（不通过 Spring AI VectorStore）
- 查询语句：`SELECT id, content, metadata, ts_rank(content_tsv, query) AS rank FROM vector_store, plainto_tsquery('simple', ?) query WHERE content_tsv @@ query [AND scope_filter] ORDER BY rank DESC LIMIT ?`
- scope 过滤逻辑复用 `PgVectorChunkRetrievalAdapter` 的 `buildScopeFilter` 逻辑（提取为共享工具类或策略）
- score 映射：`RetrievedChunk.score = ts_rank` 值

**验收标准**：
- [ ] 包含 "Flyway" 关键词的查询能检索到含 "Flyway" 的 chunk
- [ ] scope 过滤正确（只检索 askable 版本的 chunk）
- [ ] 空查询返回空列表
- [ ] score 字段正确填充 ts_rank 值

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/SparseRetrievalAdapter.java`（新增）
- 对应测试文件（新增）

---

#### FR-9：HybridChunkRetrievalAdapter RRF 融合

**需求**：
- 新增 `HybridChunkRetrievalAdapter`，实现 `ChunkRetrievalPort.hybridSearch()` 方法
- 组合 Dense（`PgVectorChunkRetrievalAdapter`）+ Sparse（`SparseRetrievalAdapter`）
- RRF 融合算法：`score = Σ 1/(k + rank_i)`，k=60，等权重（dense 0.5 / sparse 0.5）
- 同一 chunk 在两路都命中时，RRF 分数叠加
- 返回结果按 RRF 分数降序排列

**验收标准**：
- [ ] 同一查询，Hybrid 结果的 top-5 与纯 Dense 结果有差异（证明 BM25 路径生效）
- [ ] RRF 分数计算正确（手工验证 1-2 个 case）
- [ ] 仅 Dense 命中的 chunk 正确计分
- [ ] 仅 Sparse 命中的 chunk 正确计分
- [ ] 两路都命中的 chunk 分数叠加

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/domain/port/ChunkRetrievalPort.java`（修改 — 新增 hybridSearch 方法）
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapter.java`（新增）
- 对应测试文件（新增）

---

#### FR-10：AskQuestionApplicationService 切换到 Hybrid Search

**需求**：
- 注入 `HybridChunkRetrievalAdapter`（或通过 `ChunkRetrievalPort` 多实现选择）
- 将 `similaritySearch()` 调用替换为 `hybridSearch()`
- 其余流程不变（candidate amplification → limit topK → prompt → LLM）

**验收标准**：
- [ ] 普通问答请求走 Hybrid Search 路径
- [ ] CHITCHAT 请求仍跳过检索
- [ ] 返回结果包含 RRF 融合后的 score

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`（修改）

---

### 4.4 评估体系

#### FR-11：EvalRunner 检索质量评估（三模式对比 + 分层报告）

**需求**：
- 新增 4 个 test-only 组件（`src/test/java/` 下），职责单一、可独立测试：
  1. `RetrievalEvalDatasetLoader`：加载、校验、解析 JSON 测试数据集
  2. `RetrievalEvalExecutor`：封装检索调用逻辑，支持批量执行，隔离评测逻辑与业务检索接口
  3. `EvalMetricsCalculator`：纯工具类，无状态、无 Spring 依赖，所有指标计算均为 static 纯函数
  4. `EvalReportGenerator`：结构化组装评测结果，统一输出 JSON 报告

- **指标体系**：
  - `Recall@5`：top-5 中命中 strong 相关文档的比例
  - `MRR`：第一个 strong 相关结果排名的倒数的均值
  - `HitRate@5`：top-5 中至少命中 1 条 strong 相关文档的查询占比
  - `NDCG@K`：预留扩展接口（`ndcgAtK()` 方法签名 + `UnsupportedOperationException`），本期不实现

- **数据集格式**：
  - 每条样本扩展字段：`query_type`（QueryType 枚举值）、`relevance_levels`（documentId → strong/weak 映射）
  - 基础指标仅统计 `strong` 强相关文档，`weak` 预留用于 NDCG 加权
  - 缺失必填字段时抛出语义明确的 `IllegalArgumentException`，禁止静默失败

- **三模式对比**：
  - 纯向量检索模式：注入 `PgVectorChunkRetrievalAdapter`
  - 纯关键词检索模式：注入 `SparseRetrievalAdapter`
  - 混合检索模式（默认）：注入 `HybridChunkRetrievalAdapter`
  - 三种模式通过 Java 21 虚拟线程并行执行（`Executors.newVirtualThreadPerTaskExecutor()`），共享同一份数据集

- **三级报告结构**：
  1. 整体汇总层：Recall@5、MRR、HitRate@5、总查询数、单条平均检索耗时
  2. 分类型统计层：按 `query_type` 分组统计各类型的三项指标均值
  3. 单条详情层：查询内容、query_type、检索返回 ID 列表、标注相关 ID 列表、命中标记、单条指标得分

- **边界情况**：相关文档列表为空 → Recall/HitRate 默认 1.0；无命中 → MRR 返回 0.0；严格避免除零

- **实现分阶段交付**：
  - 3.1a：数据模型 + DatasetLoader + 校验 + 单元测试
  - 3.1b：MetricsCalculator（含 NDCG 扩展接口）+ Executor + 单模式可跑
  - 3.1c：三模式对比 + ReportGenerator 三级报告 + EvalRunnerTest 集成

**验收标准**：
- [ ] 20 条 QA pairs 覆盖 5 种 QueryType（每种至少 3 条），含 query_type 和 relevance_levels 字段
- [ ] Recall@5、MRR、HitRate@5 计算正确（手工验证 + 单元测试覆盖边界场景）
- [ ] 三模式对比可通过 `EvalRunnerTest` 一键触发，报告含三模式独立汇总 + 对比
- [ ] 单模式 ≤ 5 秒，三模式对比 ≤ 15 秒（20 条样本）
- [ ] 输出 JSON 可读，三级结构（汇总 / 分类型 / 单条详情）
- [ ] 全程不调用大模型、无外部网络请求
- [ ] 所有组件仅在 test 作用域生效，不侵入生产代码
- [ ] `mvn test -Dtest=EvalRunnerTest` 可触发完整评测

**影响文件**：
- `src/test/java/io/github/spike/myai/qa/infrastructure/eval/RetrievalEvalDatasetLoader.java`（新增）
- `src/test/java/io/github/spike/myai/qa/infrastructure/eval/RetrievalEvalExecutor.java`（新增）
- `src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalMetricsCalculator.java`（新增）
- `src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalReportGenerator.java`（新增）
- `src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalRunnerTest.java`（新增）
- `src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalMetricsCalculatorTest.java`（新增）
- `src/test/resources/eval/retrieval-qa-pairs.json`（新增 — 20 条 QA pairs）

---

#### FR-12：EvalRunner 生成质量评估（Layer 2）

**需求**：
- Layer 2 指标：`Faithfulness`（答案是否仅基于检索文档）和 `Answer Relevancy`（是否回答原问题）
- 测试数据：10 个手写 QA pairs，包含 question + 标准答案 + 相关 documentId 列表
- Judge 模型：DashScope qwen-max（通过 `AnswerGenerationPort` 或独立 ChatModel 调用）
- Judge prompt：结构化评分模板（1-5 分制），输出 JSON 格式的评分和理由
- 输出：JSON 报告（Faithfulness 均分、Answer Relevancy 均分、每条 query 的评分详情）

**验收标准**：
- [ ] 10 条 QA pairs 覆盖至少 3 种 QueryType
- [ ] Faithfulness 评分与人工判断一致率 > 80%（抽样 5 条验证）
- [ ] 执行时间 < 5 分钟
- [ ] 输出 JSON 可读，包含 judge 的评分理由

**影响文件**：
- `src/main/java/io/github/spike/myai/qa/infrastructure/eval/EvalRunner.java`（扩展 Layer 2）
- `src/test/resources/eval/generation-qa-pairs.json`（新增 — 10 条 QA pairs）
- 对应测试文件（新增/修改）

---

#### FR-13：EvalRunner CLI 入口

**需求**：
- 独立 main 方法或 SpringBootTest 触发 eval
- 支持运行 Layer 1 only 或 Layer 1 + Layer 2
- 输出 JSON 报告到 `target/eval-report-{timestamp}.json`

**验收标准**：
- [ ] `mvn test -Dtest=EvalRunnerTest` 可触发评估
- [ ] 输出 JSON 文件路径打印到 stdout
- [ ] Layer 1 和 Layer 2 可独立运行

**影响文件**：
- `src/test/java/io/github/spike/myai/qa/infrastructure/eval/EvalRunnerTest.java`（新增）

---

## 5. 非目标（Non-Goals）

以下内容**明确不在本次实施范围内**：

| # | 非目标 | 理由 |
|---|--------|------|
| NG-1 | HyDE（Hypothetical Document Embedding） | 论文数据 +0.014 avg score 换延迟暴增 8 倍，不符合"it just works"定位（D3） |
| NG-2 | 实际 Reranking 实现（monoT5/TILDEv2/LLM-based） | 论文数据显示边际收益最低（+3%），且需 Python 推理服务（D4） |
| NG-3 | QueryType 驱动的 Dense/Sparse 权重调整 | 需要 eval 基线数据后再调优，当前阶段使用等权重 RRF |
| NG-4 | ES/Jieba/zhparser 等外部依赖引入 | 先用 PG 内置 `'simple'` 配置，eval 数据驱动升级决策 |
| NG-5 | 合成 QA 对生成 | D17 Layer 3 可选扩展，当前用手写 QA pairs |
| NG-6 | NDCG@K 完整实现 | 本期预留扩展接口，核心逻辑延后到 Phase 2，当前用 Recall@5 + MRR + HitRate@5 |
| NG-7 | 对抗 QA 对（刻意设计会出错的查询） | D17 Layer 3 可选扩展 |
| NG-8 | Streaming 输出 | 当前 `AnswerGenerationPort` 为同步调用，streaming 是独立优化项 |
| NG-9 | Prompt 模板优化 | D14 已有独立决策，不在本 PRD 范围内 |

---

## 6. MVP 范围

### 6.1 MVP 范围内

1. FR-1：score 字段 — 所有检索路径必须透出置信度
2. FR-2：RerankingPort 钩子 — 架构扩展点就位
3. FR-3：配置外部化 — 检索参数可配置
4. FR-4：QueryClassifierPort 接口 — 分类能力抽象
5. FR-5：RuleBasedQueryClassifier — 5 条规则覆盖核心场景
6. FR-6：CHITCHAT 拦截集成 — 立竿见影的延迟优化
7. FR-7：V9 tsvector 迁移 — Hybrid Search 基础设施
8. FR-8：SparseRetrievalAdapter — BM25 检索能力
9. FR-9：HybridChunkRetrievalAdapter RRF 融合 — 多路检索核心
10. FR-10：应用层切换到 Hybrid Search — 端到端链路打通
11. FR-11：检索质量评估 — Recall@5 + MRR + HitRate@5，三模式对比，分层报告

### 6.2 MVP 范围外（Phase 2 迭代）

1. FR-12：Layer 2 生成质量评估 — Faithfulness + Answer Relevancy（依赖 Layer 1 基线稳定后）
2. FR-13：EvalRunner CLI 入口 — 与 FR-12 一起交付
3. QueryType 驱动的权重调优 — 依赖 eval 基线数据
4. zhparser 升级 — 依赖 eval 数据证明 `'simple'` 不足

---

## 7. 成功指标

| 编号 | 指标 | 目标值 | 验证方法 |
|------|------|--------|---------|
| SM-1 | Hybrid Search 检索质量提升 | Recall@5 ≥ 0.70（vs 纯 Dense 基线） | FR-11 EvalRunner |
| SM-1a | 检索命中覆盖率 | HitRate@5 ≥ 0.85（≥85% 的查询至少命中 1 条） | FR-11 EvalRunner |
| SM-2 | CHITCHAT 拦截延迟降低 | 闲聊响应延迟降低 ≥ 50% | 手动计时对比 |
| SM-3 | 代码净增可控 | 净增 ≤ 800 LOC（含测试） | `git diff --stat` |
| SM-4 | 现有功能零回归 | 所有现有 QA 测试通过 | `mvn test -Dtest="*Qa*Test"` |

**反指标（Counter-metric）**：
- 如果 Hybrid Search 的 Recall@5 低于纯 Dense 基线，说明 BM25 路径引入了噪音 → 回退到纯 Dense + 排查 Sparse 路径

---

## 8. 开放问题

| # | 问题 | 影响范围 | 负责人 | 状态 |
|---|------|---------|--------|------|
| OQ-1 | `ChunkRetrievalPort` 新增 `hybridSearch()` 方法还是新建独立端口 `HybridRetrievalPort`？ | FR-9 接口设计 | spike | 待 architecture 阶段确定 `[倾向: 在现有端口新增方法，理由: ChunkRetrievalPort 已有 overloaded 的 similaritySearch，新增 hybridSearch 保持同一语义边界；新建独立端口仅在 Spring AI 接口约束阻止扩展时作为备选]` |

---

## 9. 假设索引

| 编号 | 假设 | 来源 | 如果错误的影响 |
|------|------|------|---------------|
| A-1 | Spring AI 的 `VectorStore.similaritySearch()` 返回的 score 可用于 cosine similarity 计算 | 代码调研 | 需要自定义 SQL 查询获取原始距离值 |
| A-2 | PostgreSQL `'simple'` 配置对英文技术术语的分词质量足够 | D3 决策讨论 | 需要引入 `zhparser` 或应用层分词 |
| A-3 | 现有 20 个手写 QA pairs 能覆盖 5 种 QueryType 的核心场景 | D17 决策 | 需要增加 QA pairs 数量 |

---

## 横切关注点 NFR

### NFR-1：检索延迟增量

Hybrid Search 的端到端延迟增量（相比纯 Dense）不超过 **200ms**。

- Dense 路径：现有代码，无变化
- Sparse 路径：单次 SQL 查询，GIN 索引命中后 < 50ms（个人知识库几百份文档）
- RRF 融合：纯内存计算，< 1ms
- 双路并行执行：总延迟 = max(Dense, Sparse) + RRF ≈ Dense 延迟 + 50ms

### NFR-2：零新外部依赖

- 不引入新的 Maven 依赖
- 不引入新的 Docker 容器
- BM25 检索使用 PostgreSQL 原生 tsvector + GIN 索引

### NFR-3：六边形架构合规

- `QueryClassifierPort`、`RerankingPort` 定义在 domain 层
- 实现类在 infrastructure 层
- domain 层零框架注解
- adapter 之间不互相引用

---

## 风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| R1: Spring AI VectorStore 不暴露原始 cosine distance | 中 | FR-1 score 字段需要自定义 SQL | 备选方案：直接 JdbcTemplate 查询 `1 - (embedding <=> query_vector)` |
| R2: `'simple'` 分词对中文精确术语查询效果差 | 中 | 影响 BM25 路径 precision | D17 eval 数据驱动；升级路径为 `zhparser`（已规划） |
| R3: scope 过滤逻辑在 Sparse 路径重复实现 | 低 | 代码重复，维护负担 | 提取 `ScopeFilterBuilder` 工具类，Dense/Sparse 共用 |
| R4: Flyway V9 与 docling-upgrade 的 migration 冲突 | 无 | — | docling-upgrade 不涉及 DB schema 变更（architecture.md 确认） |
| R5: eval QA pairs 质量不足导致基线无意义 | 低 | eval 结果不可信 | QA pairs 由 spike 基于真实使用场景手写，非自动生成 |

---

## 级联变更范围

| 文件/模块 | 变更类型 | 关联 FR |
|----------|---------|---------|
| `RetrievedChunk.java` | 修改 — 新增 score 字段 | FR-1 |
| `ChunkRetrievalPort.java` | 修改 — 新增 hybridSearch 方法 | FR-9 |
| `AskQuestionApplicationService.java` | 修改 — 集成 QueryClassifier + HybridSearch + Reranker | FR-2, FR-6, FR-10 |
| `PgVectorChunkRetrievalAdapter.java` | 修改 — score 映射 | FR-1 |
| `application.yaml` | 修改 — 新增检索配置项 | FR-3 |
| `V9__hybrid_search_tsvector.sql` | 新增 | FR-7 |
| `QueryClassifierPort.java` | 新增 | FR-4 |
| `QueryType.java` | 新增 | FR-4 |
| `RerankingPort.java` | 新增 | FR-2 |
| `RuleBasedQueryClassifier.java` | 新增 | FR-5 |
| `NoOpRerankingAdapter.java` | 新增 | FR-2 |
| `SparseRetrievalAdapter.java` | 新增 | FR-8 |
| `HybridChunkRetrievalAdapter.java` | 新增 | FR-9 |
| `RetrievalEvalDatasetLoader.java` | 新增（test） | FR-11 |
| `RetrievalEvalExecutor.java` | 新增（test） | FR-11 |
| `EvalMetricsCalculator.java` | 新增（test） | FR-11 |
| `EvalReportGenerator.java` | 新增（test） | FR-11 |
| `EvalRunnerTest.java` | 新增（test） | FR-11 |
| `EvalMetricsCalculatorTest.java` | 新增（test） | FR-11 |

**总计**：5 修改 + 14 新增 = 19 文件（其中 8 个 test-only）

---

## 与 docling-upgrade 的并行策略

| 维度 | RAG 优化（本 PRD） | docling-upgrade |
|------|-------------------|----------------|
| 分支 | `feature/rag-hybrid-search` | 另一分支 |
| DB 变更 | V9 tsvector 迁移 | 无 |
| 改动区域 | `qa/` 子域（检索 + 问答） | `ingest/` 子域（解析 + 分块） |
| 共享文件 | `AskQuestionApplicationService.java` | `ProcessDocumentApplicationService.java` |
| Merge 冲突风险 | 无 — 两个子域无交集 | 同左 |

**唯一潜在交叉点**：D22 Docling HybridChunker 改变 chunk 的 content 格式后，Dense 向量和 BM25 索引都需要 re-index。但这是数据层面的操作（重新入库），不是代码冲突。

---

## 推出计划

| Phase | 范围 | 预估工时 | 依赖 |
|-------|------|---------|------|
| Phase 1：基础准备 | FR-1, FR-2, FR-3 | ~半天 | 无 |
| Phase 2：查询分类 | FR-4, FR-5, FR-6 | ~1 天 | Phase 1（FR-1 score） |
| Phase 3：混合检索 | FR-7, FR-8, FR-9, FR-10 | ~2 天 | Phase 1 + Phase 2 |
| Phase 4：评估体系 | FR-11（三模式对比 + 分层报告）, FR-12, FR-13 | ~2 天 | Phase 3 |
| **总计** | 13 个 FR | **~5.5 天** | — |
