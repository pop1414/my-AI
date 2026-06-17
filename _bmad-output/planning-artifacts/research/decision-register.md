# Decision Register: my-AI 全模块技术架构评估

> 来源: `_bmad-output/archive/technical-my-ai-all-modules-assessment-research-2026-06-04.md`（已归档）
> 用途: 逐条讨论选择，每完成一条更新状态
> 状态: [ ] 待讨论 · [/] 讨论中 · [x] 已决定

---

## 决策点列表

### D1 — 异步处理模型

**背景**: 当前 `@Scheduled` 单线程轮询串行处理文档，`worker.enabled` 默认 false。30 份文档排队 15-30 分钟。

| 选项 | 方案 | 复杂度 | 适合 |
|------|------|--------|------|
| A | Virtual Threads + @Async | **低** — 改配置 + 注解 | 单实例，中吞吐 |
| B | 消息队列 (RabbitMQ/Kafka) | **高** — 新基础设施 | 多实例，高吞吐 |
| C | Spring @Async + 固定线程池 | **低** — 传统方案 | 对 Virtual Threads 有顾虑时 |
| D | 维持现状 (MVP 够用) | 零 | 暂无多人使用 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Virtual Threads + claim-then-submit + Semaphore 组合方案**

- `@Scheduled` 轮询保持作为任务发现机制，pollAndClaim() CAS 抢占后立即提交 Virtual Thread 执行并返回，轮询间隔变成"注水速率"而非"处理速率"
- `Semaphore(parallelism)` 控制并发槽位上限，防止 PDF/OCR 同时解析炸内存
- 可配置 `batch-size`：每轮 claim 最多 N 条
- 三个硬性条件：超时熔断（单文档 10min 超时 + 下游调用超时）、幂等性保证（文档 hash + PENDING→PROCESSING→DONE 状态机）、dead letter 模式（失败记录到 failure 表）
- `worker.enabled` 默认改为 true，配合 health check endpoint
- Java 升级到 21（改 pom.xml + CI JDK 版本）

**理由**: CAS 抢占天然支持未来多实例竞争（分布式锁），届时若表锁成瓶颈再考虑 MQ。VIP 0 virtual threads 是 JDK 自带，零外部依赖，适应 Docling OCR 耗时波动大的场景。

---

### D2 — LLM 提供商: DashScope → Ollama

**背景**: 当前 LLM/Embedding 全走 DashScope（阿里云）。项目定位为 portfolio 项目，面向个人开发者，需兼顾"一键跑通"体验和私有化可选项。

| 选项 | 方案 | 复杂度 | 适合 |
|------|------|--------|------|
| A | 全部切 Ollama (qwen2.5 / llama3.2) | **中** — 换 starter + 拉模型 + 调 prompt | 零外部依赖 |
| B | Ollama + DashScope 双轨 (本地优先，云端兜底) | **中** — 需实现 fallback 逻辑 | 过渡期稳妥 |
| C | 只换 Embedding 为本地，Chat 保留云端 | **低** — embedding 是最频繁的数据出网点 | 先解决最大合规风险 |
| D | 支持多模型可切换 (vLLM / 通义千问私有化版等) | **高** — 需抽象层 | 面向企业客户定制 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **DashScope 默认 + Ollama 可选 + FallbackChatModel 运行时降级**

两层机制，互不冲突：

**第一层 — 部署时选型（Spring profile）**：
- **默认 profile（`dashscope`）**：Chat = qwen-max，Embedding = text-embedding-v4（1024 维）。个人开发者拿 API Key 即跑，零硬件门槛
- **可选 profile（`ollama`）**：Chat = qwen2.5:7B，Embedding = bge-m3（1024 维）。有 GPU 的隐私敏感用户切换
- 切换方式：`--spring.profiles.active=ollama` + 重启

**第二层 — 运行时降级（FallbackChatModel，~50 行）**：
- 主模型（DashScope）不可用时自动降级到 Ollama
- 轻量熔断：`AtomicInteger` 计数，连续失败 3 次后直接走 Ollama，成功则复位
- `ChatModel` 接口一致，纯转发，零格式转换
- 无外部依赖（不引入 Resilience4j），JDK 自带 + SLF4J
- 降级事件通过 D15 的 Micrometer Counter 暴露

**理由**: 项目目标为 portfolio + 个人开发者社区。默认 DashScope = 一键跑通（阿里云注册即用），覆盖 80%+ 无 GPU 用户。Ollama profile 保留隐私选项。FallbackChatModel 是简历技术亮点——"自研轻量 fallback chain + circuit breaker"，半天实现，面试讲五分钟。Spring AI `ChatModel` 接口在两种后端下统一，业务代码零感知。

---

### D3 — 检索策略: 纯 Dense → Hybrid Search

**背景**: 当前仅 `COSINE_DISTANCE` 向量检索（`PgVectorChunkRetrievalAdapter`），对稀有术语和 OOV 词完全无覆盖。`ChunkRetrievalPort` 只有 `retrieveRelevantChunks()` 单一路径。以下关键选择从未经过讨论：

| # | 隐含选择 | 当前值 | 未讨论的问题 |
|---|---------|--------|-------------|
| 1 | 检索路径 | 仅 Dense (COSINE_DISTANCE) | Dense 对稀有术语/OOV 词无能为力，是否需要 sparse 互补？ |
| 2 | 融合策略 | 无（单路） | 如果双路检索，用什么融合算法？RRF？加权求和？ |
| 3 | Query 变换 | 无 | 是否需要 query rewriting/decomposition/HyDE 提升召回？ |
| 4 | 外部基础设施 | 仅 PG | 要不要引入 Elasticsearch？还是 PG 原生 ts_vector 够用？ |
| 5 | 检索 score 透出 | `RetrievedChunk` 无 score 字段 | 融合排序、eval 系统都需要 score，当前完全缺失 |

**论文数据（EMNLP 2024, Table 1 & Table 7）**:

**Table 1 — RAG 全链路消融实验**（corpus: 10M Wikipedia + 4M medical, generator: Llama2-7B-Chat fine-tuned）:

| Method | RAG Score | Avg Score | Avg F1 | Avg Latency (s) |
|--------|-----------|-----------|--------|-----------------|
| baseline (no retrieval) | — | 0.351 | 0.292 | 1.27 |
| w/o classification | 0.540 | 0.422 | 0.353 | 16.58 |
| + classification (Hybrid+HyDE base) | 0.580 | 0.443 | 0.353 | 11.71 |
| **+ Hybrid** | **0.498** | **0.429** | **0.318** | **1.45** |
| + Original (Dense only) | 0.486 | 0.383 | 0.273 | 1.44 |
| + HyDE | 0.545 | 0.398 | 0.293 | 11.58 |
| + Hybrid + HyDE | 0.580 | 0.443 | 0.353 | 11.71 |

**Table 7 — 检索方法在 TREC DL19/20 上的对比**（BM25 / Contriever / LLM-Embedder 及其组合）:

| Method | DL19 mAP | DL19 nDCG@10 | DL19 R@1k | DL19 Latency | DL20 mAP | DL20 nDCG@10 | DL20 R@1k | DL20 Latency |
|--------|----------|-------------|-----------|-------------|----------|-------------|-----------|-------------|
| BM25 (sparse only) | 30.13 | 50.58 | 75.01 | 0.07 | 28.56 | 47.96 | 78.63 | 0.29 |
| Contriever (dense only, unsupervised) | 23.99 | 44.54 | 74.59 | 3.06 | 23.98 | 42.13 | 75.39 | 0.98 |
| LLM-Embedder (dense only, supervised) | 44.66 | 70.20 | 84.48 | 2.61 | 45.60 | 68.76 | 84.41 | 0.71 |
| + Query Rewriting | 44.56 | 67.89 | 85.35 | 7.80 | 45.16 | 65.62 | 83.45 | 2.06 |
| + Query Decomposition | 41.93 | 66.10 | 82.62 | 14.98 | 43.30 | 64.95 | 84.18 | 2.01 |
| + HyDE | 50.87 | 75.44 | 88.76 | 7.21 | 50.94 | 73.94 | 88.03 | 2.14 |
| **+ Hybrid Search** | **47.14** | **72.50** | **89.08** | **3.20** | **47.72** | **69.80** | **88.04** | **0.77** |
| + HyDE + Hybrid Search | 52.13 | 73.34 | 90.42 | 11.16 | 53.13 | 72.72 | 90.67 | 2.95 |

**论文结论**:
- Hybrid (Dense + BM25) 是最优 ROI 方案——Table 1 中 Hybrid avg score 0.429 vs Dense-only 0.383（**+12%**），延迟几乎不变（1.45s vs 1.44s）
- HyDE 加成有限但代价巨大：Hybrid+HyDE 比 Hybrid 仅 +3%（0.443 vs 0.429），但延迟暴增 8 倍（11.71s vs 1.45s）
- 论文推荐两条路径：**Best Performance**（Hybrid+HyDE+monoT5+Recomp, score 0.483）和 **Balanced Efficiency**（Hybrid+TILDEv2+Recomp）。项目定位对应后者
- Dense 和 Sparse 互补：Dense 擅长语义关联（"坏人"↔"反派"），Sparse 擅长精确术语和 OOV 词匹配

| 选项 | 方案 | 复杂度 | 效果 |
|------|------|--------|------|
| A | Hybrid Search (pgvector Dense + PostgreSQL ts_vector BM25 + RRF 融合) | **中** — PG 原生能力，零新基础设施，~500 LOC | 论文验证最优 ROI |
| B | Hybrid + HyDE (论文 Best Performance 路径) | **中-高** — 每次查询多一次 LLM 调用生成 pseudo-document | 延迟 8x，不符合定位 |
| C | 维持纯 Dense + 调参（加大 top-K、调 chunk 策略） | **极低** | 不解决 OOV 和稀有术语根本问题 |
| D | 引入 Elasticsearch 做 Sparse 路 | **高** — 新基础设施，新容器，运维负担 | 文档量 >100 万时考虑 |
| E | 先不做，等 D17 eval 基线出来再决定 | 零 | 数据驱动，但论文信号已足够强 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option A — Hybrid Search (pgvector Dense + PostgreSQL ts_vector BM25 + RRF 融合)**

- **Dense 路径**：复用现有 pgvector `COSINE_DISTANCE` 向量检索，零改动
- **Sparse 路径**：新增 `content_tsv tsvector` 生成列 + GIN 索引，`plainto_tsquery` + `ts_rank` 做 BM25 近似全文检索（中文分词策略见下方补充说明）
- **融合算法**：RRF (Reciprocal Rank Fusion)，k=60，等权重。不做超参调优——等 D17 eval 上线后用数据驱动调整
- **架构**：`ChunkRetrievalPort` 新增 `hybridSearch()` 方法；`HybridChunkRetrievalAdapter` 组合 Dense + Sparse 适配器，分别调两路后在适配器内做 RRF 融合
- **score 补齐**：`RetrievedChunk` 新增 `score` 字段（当前缺失），融合排序和 D17 eval 都需要
- **预估**：~500 LOC，4 新文件 + 4 修改文件，零新 Maven 依赖，零新容器
- **双路检索在同一个 PostgreSQL 实例内完成**——"加一个 GIN 索引"不是新依赖
- **不选 HyDE**：论文数据 +0.014 avg score 换延迟暴增 8 倍（11.71s），项目定位是"clone 5 分钟跑通"的个人知识库，不符合"it just works"。可作为可选功能通过配置开关暴露（简历展示价值）

**理由**: 论文 Table 1 直接对比——Hybrid 比纯 Dense avg score 提升 12%（0.383→0.429），延迟几乎不变（1.44s→1.45s）。这是所有模块中"单位复杂度换质量提升"最高的单项决策。PostgreSQL 是项目已有基础设施，ts_vector + GIN 索引是 PG 原生能力，不存在"引入新依赖"的问题。论文 Balanced Efficiency 路径明确推荐 Hybrid 作为检索默认方案。

**与 D5 联动**: 规则分类器输出 query 类型后，可动态调整 Dense/Sparse 权重配比——`FACTOID`（语义精确匹配）偏重 Dense，`PROCEDURAL`（关键词匹配操作步骤）偏重 BM25，`CHITCHAT` 跳过检索。

**与 D17 联动**: RRF 的 k 值和 Dense/Sparse 权重配比依赖 D17 eval 系统（Recall@5、MRR）做数据驱动调优。初始用论文默认值，后续 e2e 回归测试验证。

**与 D22 联动**: Chunking 策略决定了 chunk 的文本质量（粒度、重叠、结构感知），直接影响 Dense 和 Sparse 两路的检索输入。D22 的 sentence-level 切分 + 512 token size 会在 Hybrid 检索中放大效果。

**中文分词策略（2026-06-16 补充）**：

初始使用 `to_tsvector('simple', content)` 配置——中文逐字拆分，英文按词拆分。

| 方案 | 效果 | 依赖 | 选择 |
|------|------|------|------|
| `'simple'`（PG 内置） | 中文逐字拆分，英文按词 | 零 | **先上** |
| `'english'`（PG 内置） | 中文整块输出，基本失效 | 零 | 不选 |
| `zhparser`（PG C 扩展） | 中文按词切分，最准确 | 自定义 Dockerfile + 扩展安装 | eval 数据差时升级 |
| 应用层 jieba 分词 | 接近 zhparser | Maven 依赖 + 一致性维护 | 不选 |

**选 `'simple'` 的理由**：BM25 在 RAG Hybrid Search 中是辅助路径（Dense 0.5 + Sparse 0.5 起步），中文逐字拆分的 precision 问题被 RRF 融合中的 Dense 语义路径稀释。英文关键词（Spring、Kafka、PGVector 等技术术语）是精确匹配的主要信号源，`'simple'` 处理英文完全正确。

**升级路径**：D17 eval 数据若显示含中文精确术语查询的 Recall@5 显著低于其他类型查询，引入 `zhparser` 扩展（`infra/pgvector/Dockerfile` 基于 `pgvector/pgvector:pg16` 安装 `postgresql-16-zhparser`，Flyway migration 中 `CREATE EXTENSION zhparser` + `CREATE TEXT SEARCH CONFIGURATION chinese (PARSER = zhparser)`）。eval 数据驱动，非拍脑袋。

**不选应用层 jieba 分词的理由**：新增 `jieba-analysis` Maven 依赖 + 索引时/查询时分词一致性维护成本 > 收益。且污染 Java 代码（应在数据库层解决数据库层问题）。

**参考论文**: EMNLP 2024, Table 1 & Table 7 — Hybrid Search avg score 0.429 vs Dense-only 0.383，延迟 1.45s vs 1.44s；论文 Balanced Efficiency Practice 明确推荐 Hybrid 作为检索默认方案。

---

### D4 — Reranking: 是否需要

**背景**: 当前 `PgVectorChunkRetrievalAdapter` 直接按 `COSINE_DISTANCE` 排序返回，无重排阶段。检索结果依赖单一路径的相似度打分，没有二次精排机制。以下关键选择从未经过讨论：

| # | 隐含选择 | 当前值 | 未讨论的问题 |
|---|---------|--------|-------------|
| 1 | 重排是否存在 | 无——Dense 相似度即最终排序 | 检索召回 ≠ 精确排序，是否需要第二个排序阶段？ |
| 2 | 重排方案选择 | 无 | monoT5（效果）/ TILDEv2（效率）/ LLM-based（零新依赖）/ 不做 |
| 3 | Python 推理依赖 | N/A | 所有专用模型（monoT5/TILDEv2/RankLLaMA）都需要 Python 推理服务 |
| 4 | 接口抽象 | 无 `Reranker` 概念 | 检索→排序→组装的 pipeline 缺少可插拔的 rerank 阶段 |
| 5 | Reranker 对 score 的依赖 | `RetrievedChunk` 无 score 字段 | 重排需要上游透出置信度分数（与 D3 score 字段联动） |

**论文数据（EMNLP 2024, Table 1 & Table 10）**:

**Table 1 — RAG 全链路：Reranking 模块消融**（corpus: 10M Wikipedia + 4M medical）:

| Method | RAG Score | Avg Score | Avg F1 | Avg Latency (s) |
|--------|-----------|-----------|--------|-----------------|
| w/o reranking | 0.530 | 0.430 | 0.334 | 10.31 |
| + monoT5 (T5-base, 220M) | 0.580 | 0.443 | 0.353 | 11.71 |
| + monoBERT (BERT-large, 340M) | 0.551 | 0.438 | 0.351 | 11.65 |
| + RankLLaMA (Llama-2-7B) | 0.558 | 0.431 | 0.342 | 13.51 |
| + TILDEv2 (BERT-base, 110M) | 0.536 | 0.440 | 0.355 | 11.26 |

**Table 10 — MS MARCO Passage Ranking 上的 Reranking 详细对比**（top-1000 from BM25）:

| Method | Base Model | #Params | MRR@10 | Hit Rate@10 | Latency (s) |
|--------|-----------|---------|--------|-------------|-------------|
| BM25 (no rerank) | — | — | 11.65 | 24.63 | — |
| monoT5 | T5-base | 220M | 31.78 | 54.07 | 4.5 |
| monoBERT | BERT-large | 340M | 31.69 | 53.38 | 15.8 |
| RankLLaMA | Llama-2-7B | 7B | 32.35 | 54.53 | 82.4 |
| TILDEv2 | BERT-base | 110M | 27.83 | 49.07 | **0.02** |

**论文结论**:
- Reranking 的边际收益是**所有模块中最低的**——Table 1 中 w/o reranking 0.430 vs monoT5 0.443（**+3.0%**），绝对值仅 +0.013
- D3 Hybrid Search 的 Dense+Sparse 互补已经在检索阶段覆盖了语义+词汇两个维度，Reranking 更多是锦上添花
- 论文推荐两条路径：Best Performance 用 monoT5，Balanced Efficiency 用 TILDEv2
- **所有专用模型 Reranker 都需要 Python 推理服务**——monoT5/TILDEv2 基于 HuggingFace Transformers，RankLLaMA 基于 PyTorch。项目约束"Docling 是唯一 Python 容器"
- TILDEv2 延迟最低（0.02s/query），但要求 passage 在索引时预计算 log-prob，新增文档需重新预处理，抵消效率优势

| 选项 | 方案 | 复杂度 | 效果 |
|------|------|--------|------|
| A | TILDEv2 (轻量，预计算 log-prob) | **中** — 需索引预处理 + Python 推理服务 | 好，延迟低 0.02s，但只能重排预索引 passage |
| B | LLM-based Reranker (用 LLM 重排 top-K) | **低** — 加一次 LLM 调用 | 取决于 LLM 质量，每次查询 +1~3s + API 费用 |
| C | 不做 Reranking，只留 `Reranker` 接口钩子 + `NoOpReranker` 默认实现 | **极低** — 一个接口 + 空实现，~10 LOC | 架构扩展点，零运行时开销 |
| D | 不做，完全不考虑 | 零 | 维持现状，缺少架构意识 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option C — 留 `Reranker` 接口钩子 + `NoOpReranker` 默认实现，不做实际重排**

- **接口定义**：`RerankingPort` → `List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN)`
- **默认实现**：`NoOpRerankingAdapter` 透传，零开销
- **插入点**：检索后（D3 Hybrid Search）、Context Assembly 前。`AskQuestionApplicationService` 的 pipeline：检索 → rerank（可插拔）→ 截断 top-K → prompt 拼接
- **预估**：~10 LOC（一个接口 + 一个空实现），零依赖，零配置
- **不做 LLM-based Reranker 实现**：论文 Table 1 +0.013 avg score 换每次查询 +1~3 秒延迟 + 额外 API 费用（每次 rerank 调用多消耗 ~500 tokens），用户体验和成本双输
- **不做专用模型 Reranker（monoT5/TILDEv2/RankLLaMA）**：全部需要 Python 推理服务（HuggingFace Transformers 或 PyTorch），违反"Docling 是唯一 Python 容器"约束。且 TILDEv2 要求 passage 在索引时预计算——新增文档需重新跑预处理，运维成本 > 收益

**理由**: 论文数据直接支持——Reranking 是唯一"有提升但代价不成比例"的模块。+0.013 avg score（+3%）是所有模块中最低的边际收益，而引入 Python 推理服务的架构成本（新容器、模型加载、GPU 需求）远超这个收益。D3 Hybrid Search 已经在检索阶段做了 Dense+Sparse 互补，Reranking 在此基础上的增量有限。但架构上预留接口成本极低（~10 LOC），展示"我知道这个扩展点，知道什么时候不该用"的工程判断力——面试时比"上了 Reranker"更有说服力。

**与 D3 联动**: Hybrid Search 的 Dense+Sparse 双路检索已经在排序层面做了 RRF 融合，部分替代了 Reranking 的"二次精排"作用。

**与 D17 联动**: 如果将来 D17 eval 数据显示特定 query 类型 Recall 高但 Precision 低（检索到了但没排到前面，即 D3 Hybrid 召回了正确 chunk 但排到了后面），可以考虑启用 LLM Reranker 对 top-K 重排。但必须用 D17 数据证明"不做 rerank 时 MRR 显著低于 Recall@K"，而非拍脑袋上。

**参考论文**: EMNLP 2024, Table 1 & Table 10 — Reranking 边际收益 +0.013（所有模块最低）；TILDEv2 效率最优但需预索引；论文 Balanced Efficiency Practice 推荐 TILDEv2，但 Python 推理服务约束使项目选择预留接口。

---

### D5 — Query Classification: 是否需要

**背景**: 当前 `AskQuestionApplicationService` 对所有用户输入都执行完整 RAG pipeline——向量检索 → 拼接 context → LLM 生成。无论是"Spring Boot 如何配置事务？"还是"你好""今天天气怎么样"，都统一走检索路径。以下关键选择从未经过讨论：

| # | 隐含选择 | 当前值 | 未讨论的问题 |
|---|---------|--------|-------------|
| 1 | 所有 query 都需检索？ | 是——无区分，全量走检索 | "你好"触发向量检索是浪费，且可能检索到无关 chunk 干扰 LLM |
| 2 | 分类实现方式 | 无分类逻辑 | 训练 BERT 分类器 / LLM zero-shot / 规则匹配——三者成本和效果差几个数量级 |
| 3 | 分类粒度 | N/A | 论文分 15 类 + 2 元（需要/不需要检索），项目需要多少类？ |
| 4 | 分类结果的用途 | N/A | 仅做 skip retrieval？还是驱动后续 D3 的 Dense/Sparse 权重、top-K 调整？ |
| 5 | 分类器可维护性 | N/A | 规则需要迭代，模型需要重训练——哪种适合个人知识库场景？ |

**论文数据（EMNLP 2024, Table 1 & Table 2）**:

**Table 1 — RAG 全链路：Query Classification 模块消融**（corpus: 10M Wikipedia + 4M medical）:

| Method | RAG Score | Avg Score | Avg F1 | Avg Latency (s) |
|--------|-----------|-----------|--------|-----------------|
| w/o classification | 0.540 | 0.422 | 0.353 | 16.58 |
| **+ classification** | **0.580** | **0.443** | **0.353** | **11.58** |
| Δ (classification 的贡献) | **+0.040** | **+0.021** | 0 | **-5.00 (-30%)** |

**Table 2 — 论文分类器性能**（BERT-base-multilingual-cased, 111K 样本训练）:

| Model | Accuracy | Precision | Recall | F1 |
|-------|----------|-----------|--------|-----|
| BERT-base-multilingual | 0.95 | 0.96 | 0.94 | 0.95 |

**分类数据集构成**: 64K "retrieval required" + 47K "no retrieval required"，覆盖 15 类任务（Code、Medical、Suggestion、Roleplay、Rewriting、Multi-task 等），不足类型用 GPT-4 生成

**论文结论**:
- Query Classification 是**唯一同时提升效果和降低延迟的模块**——avg score +5%（0.422→0.443），latency -30%（16.58→11.58s）
- 原理简单：非知识类 query（翻译、角色扮演、改写、闲聊）不需要检索，直接由 LLM 回答。跳过检索 = 省时间 + 避免无关 chunk 干扰
- 论文用 BERT 做分类器（95% 准确率），但论文也承认"可以基于任务类型做规则判断"（Section 3.1 明确说了分类依据是 task type）
- 论文 Best Performance 和 Balanced Efficiency 两条路径都保留 Query Classification——说明这不是"锦上添花"，而是"基础模块"

| 选项 | 方案 | 复杂度 | 适合 |
|------|------|--------|------|
| A | 训练/部署 BERT 分类器（论文方案） | **高** — 需 111K 训练数据 + Python 推理服务 + GPU | 查询量大、类型多样，论文验证 95% 准确率 |
| B | 规则分类器（关键词+疑问词匹配，~5 条核心规则） | **低** — 纯 Java String/Regex，~150 LOC，零外部依赖 | 个人知识库起步阶段，可演示，可迭代 |
| C | 不做分类，始终检索 | 零 | 暂时够用，但"你好"也走检索在演示时尴尬 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option B — 精简规则版分类器（~5 条核心规则 + QueryClassifier 接口）**

- **接口抽象**：`QueryClassifierPort` → `QueryType classify(String question)`，不绑定规则实现。将来可切换为 LLM zero-shot 或 BERT 分类器
- **QueryType 枚举**（5 类，非论文的 15 类）：
  - `FACTOID` — "什么是X"、"X的定义"（概念事实查询）
  - `PROCEDURAL` — "怎么做Y"、"如何配置Z"（操作步骤查询）
  - `COMPARATIVE` — "对比A和B"、"X和Y的区别"（比较分析查询）
  - `CHITCHAT` — "你好"、"今天天气"、"谢谢"（闲聊/社交，**最高优先级拦截**）
  - `GENERAL` — 默认兜底
- **实现**：`RuleBasedQueryClassifier`，关键词+疑问词正则匹配，纯 Java，零外部依赖
- **优先级与冲突解决**：Chitchat 最高优先（直接拦截，不走检索），Specific > General
- **分类结果驱动检索策略**（与 D3 联动）：
  - `FACTOID` → Dense 权重 0.7 / Sparse 0.3（语义精确匹配更重要）
  - `PROCEDURAL` → Dense 权重 0.3 / Sparse 0.7（关键词匹配操作步骤更有效）
  - `COMPARATIVE` → 等权重 Hybrid（需要同时匹配两个对象的语义和术语）
  - `CHITCHAT` → 跳过检索，直接 LLM 回复
  - `GENERAL` → 等权重 Hybrid（默认）
- **预估**：~150 LOC（5 条核心规则 + 接口 + 枚举 + 权重映射），3 新文件 + 2 修改文件，零新依赖
- **规则数量上限**：10 条。超过后考虑引入 LLM zero-shot 分类做一致性检查（Murat 建议——避免"规则泥潭"）

**理由**: 论文 Table 1 中 Query Classification 是唯一"鱼和熊掌兼得"的模块——效果 +5% 同时延迟 -30%。"智能路由"是面试中最容易一句话讲清楚架构品位的设计："非知识类问题直接拦截，不浪费向量检索"的可演示性远高于"多返回两个更相关文档"。规则版复杂度远低于训练 BERT 分类器（不需要 111K 训练数据、Python 推理服务、GPU），纯 Java 零新依赖，且 5 条规则覆盖个人知识库 90%+ 场景。接口化设计保证将来可切换到 LLM zero-shot 分类而业务代码零改动。

**与 D3 联动**: 分类结果动态调整 Hybrid Search 的 Dense/Sparse 权重配比——不同 query 类型对应不同检索策略。论文 Table 9 显示 Hybrid Search 的 α（Dense 权重）在 0.3 时最优，但那是 uniform 设置。D5 的分类信息让权重从"全局常量"变成"query-aware 变量"。

**与 D17 联动**: 分类准确率通过 D17 eval 系统的 query 类型分布监控。30 个手写 QA pairs 覆盖 5 种 query 类型，每次 eval 跑完后统计分类混淆——"规则把 PROCEDURAL 误判为 FACTOID 的比例"。规则迭代基于 eval 反馈而非拍脑袋。

**参考论文**: EMNLP 2024, Table 1 & Table 2 — Query Classification 是唯一同时提升效果（+5% avg score）和降低延迟（-30%）的模块；BERT-base 分类器达 95% F1；论文两条推荐路径均保留此模块。

---

### D6 — RAG Pipeline Summarization

**背景**: 当前 `AskQuestionApplicationService` 检索后直接拼接 retrieved chunks 进 prompt，无中间摘要/压缩步骤。代码中硬编码了 `RETRIEVAL_CANDIDATE_MULTIPLIER` 和 `MIN_RETRIEVAL_CANDIDATES` 常量，控制检索候选 → 截断到 top-K 的逻辑。以下关键选择从未经过讨论：

| # | 隐含选择 | 当前值 | 未讨论的问题 |
|---|---------|--------|-------------|
| 1 | 是否需要在 LLM 生成前压缩检索结果？ | 否——chunks 直接拼进 prompt | chunks 多时 prompt 过长，增加延迟和成本 |
| 2 | 压缩方式 | 截断到 top-K（隐式 summarization） | extractive vs abstractive vs 不做——论文测试了全部 |
| 3 | 当前截断逻辑是否可配置？ | 硬编码常量 | 不同场景（demo vs production）可能需要不同的检索候选数 |
| 4 | Summarization 是否引入新依赖？ | 否 | Recomp 需要额外模型，LongLLMLingua 需要 Python |
| 5 | 不做 summarization 的代价 | 无测量 | 论文数据显示不做比做错好——坏的摘要比不摘要更差 |

**论文数据（EMNLP 2024, Table 1 & Table 11）**:

**Table 1 — RAG 全链路：Summarization 模块消融**（corpus: 10M Wikipedia + 4M medical, generator: Llama2-7B-Chat fine-tuned）:

| Method | RAG Score | Avg Score | Avg F1 | Avg Latency (s) |
|--------|-----------|-----------|--------|-----------------|
| **w/o summarization** | **0.533** | **0.441** | **0.355** | **10.97** |
| + Recomp (extractive + abstractive) | 0.560 | 0.446 | 0.354 | 11.70 |
| + LongLLMLingua | 0.539 | 0.426 | 0.334 | 16.17 |
| Δ (no-sum → Recomp) | +0.027 | +0.005 | -0.001 | +0.73 (+6.7%) |
| Δ (no-sum → LongLLMLingua) | +0.006 | **-0.015** | -0.021 | +5.20 (+47%) |

**Table 11 — 不同 Summarization 方法在 NQ/TriviaQA/HotpotQA 上的详细对比**（generator: Llama3-8B-Instruct, summarization ratio=0.4）:

| Method | NQ F1 | TQA F1 | HotPotQA F1 | Avg F1 | Avg Token |
|--------|-------|--------|-------------|--------|-----------|
| **Origin Prompt (no summarization)** | 27.07 | 33.61 | 33.92 | **31.53** | 139 |
| Recomp (extractive) | 27.84 | 35.32 | 29.46 | 30.87 | **51** |
| Recomp (abstractive) | **33.68** | **35.87** | 29.01 | **32.85** | 59 |
| BM25 (extractive baseline) | 27.97 | 32.44 | 28.00 | 29.47 | 54 |
| Contriever (extractive baseline) | 23.62 | 33.79 | 23.64 | 27.02 | 56 |
| SelectiveContext (abstractive) | 25.05 | 34.25 | 34.43 | 31.24 | 67 |
| LongLLMLingua (abstractive) | 21.32 | 32.81 | 30.79 | 28.29 | 55 |

**论文结论**:
- Summarization 的收益是**所有模块中最微弱的**——Table 1 中 no-sum vs Recomp avg score 仅差 0.005（+1.1%），绝对值在误差范围内
- **LongLLMLingua 反而降分**（0.441→0.426，-3.4%）——论文明确指出它"occasionally distorts semantics and produces incoherent content"，不准确的摘要比不摘要更差
- Recomp 的真正价值是 **token 压缩**（Table 11: 139→51 tokens，-63%）而非质量提升。适用场景：generator 有 max length 硬约束时
- 论文仍然推荐 Recomp 作为 Best Performance 路径的一部分——因为全链路叠加后累积效果 0.483 vs 0.441（+9.5%），但这是和其他模块的交互效应，单模块贡献微弱
- **Table 11 中 Recomp (abstractive) 反而比 no-sum 的 Avg F1 略高**（32.85 vs 31.53），说明 extractive + abstractive 组合在特定数据集上有微弱优势

| 选项 | 方案 | 复杂度 | 适合 |
|------|------|--------|------|
| A | Recomp (抽取式 + 生成式压缩) | **中** — 需额外模型 + Python 推理服务 | 检索结果多、generator token limit 紧 |
| B | 简单截断到 top-K chunk（当前方案）+ 配置化改进 | **极低** — ~15 LOC，零依赖 | 个人知识库 chunks 少（<10），够用 |
| C | LLM 摘要（让 LLM 先总结检索结果再回答） | **低** — 多一次 LLM 调用 | 每次查询 +1~3s + API 费用，F1 无保证 |
| D | 维持硬编码现状，不做任何改动 | 零 | 功能正常，但不优雅 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option B — 维持截断策略，配置化改进**

- **当前代码已经是最佳实践**：检索 `topK * RETRIEVAL_CANDIDATE_MULTIPLIER` 候选 → 截断到 `topK` → LLM 被 system prompt 要求"仅基于给定参考片段回答"。这个 pipeline 本质上是隐式 extractive summarization——用相似度排序 + 截断替代显式的重要度打分
- **唯一改动**：将 `RETRIEVAL_CANDIDATE_MULTIPLIER` 和 `MIN_RETRIEVAL_CANDIDATES` 从硬编码常量改为 `application.yaml` 可配置参数（~15 LOC），不同 profile 可调：
  - `demo` profile：保守值（multiplier=3, min=3）
  - `production` profile：论文推荐值（multiplier=5, min=5）
- **不做 Recomp**：需要额外模型 + Python 推理服务（extractive compressor 基于 BERT，abstractive compressor 基于 seq2seq），违反"Docling 是唯一 Python 容器"约束
- **不做 LLM 摘要**：Table 1 数据显示 no-sum vs Recomp 仅差 +0.005 avg score。多一次 LLM 调用（+1~3s 延迟 + ~500 tokens 费用）换 0.005 质量提升，ROI 为负
- **不做 LongLLMLingua**：Table 1 数据明确显示反而降分（-3.4% avg score），论文指出其"distorts semantics and produces incoherent content"。明知有害还做，面试时反而尴尬

**理由**: 论文 Table 1 和 Table 11 共同指向同一个结论——Summarization 的边际质量收益是所有模块中最低的（+0.005 avg score），但其 token 压缩效果（-63%）在 generator token limit 紧张时有实用价值。当前项目 chunks 数量少（个人知识库场景 <10），token limit 不构成约束，所以 Summarization 的价值接近零。配置化改进是性价比最优的改动：~15 LOC 让参数可调，既承认论文数据，又不做过度工程。

**与 D3 联动**: D3 Hybrid Search 的 RRF 融合排序天然做了"重要度预排序"，截断 top-K 等价于 extractive summarization。Hybrid 的 Dense+Sparse 互补让排序更准确，间接提升了截断质量。

**与 D22 联动**: D22 的 chunk size 从 500 字符 → 512 tokens 后，单个 chunk 信息密度增加，同样 top-K 个 chunk 携带更多有效信息——等同于"免费"的 summarization 增益。

**参考论文**: EMNLP 2024, Table 1 & Table 11 — Summarization 边际质量收益最低（no-sum 0.441 vs Recomp 0.446, +0.005）；LongLLMLingua 降分（0.426, -3.4%）；Recomp token 压缩 -63%（139→51 tokens）。

---

### D22 — Chunking 策略 & Chunk Metadata

**背景**: 当前 `StructuredFallbackDocumentChunker` 已实现了一套 chunking pipeline：`MarkdownSegmenter`（按空行切段 → 按空格 tokenize）→ `HeadingContextExtractor`（启发式正则识别标题上下文）→ `ChunkWindowAssembler`（滑动窗口拼接）。配置为 `chunkSize=500` 字符、`overlapSize=100` 字符。Chunk 级元数据仅 `SourceHint` 中的 `heading` 一个字段。

**关键发现**（2026-06-05 调研）：Docling 不只是"更好的 Tika"——它有内置 chunker，且能力直接覆盖 D22 的全部需求：

| | HierarchicalChunker | **HybridChunker** |
|---|---|---|
| 切分方式 | 纯布局感知（按标题层级切） | 布局感知 + token 预算控制 |
| Token 限制 | 无 | 有（可设 max_tokens） |
| 合并小块 | 不支持 | merge_peers=True 合并过小 chunk |
| 元数据 | headings / page_no | headings / page_no |
| 适合 RAG | 一般 | **最优** |

HybridChunker 的输出直接命中 D22 的三个核心改动点：

| D22 改动 | HybridChunker 对应能力 |
|----------|----------------------|
| MarkdownSegmenter 识别结构边界 | 原生布局感知，不切断表格/代码块 |
| HeadingContextExtractor 增强 | `chunk.meta.headings` — 标题面包屑数组（如 `["Chapter 1", "Section 1.1"]`），非单一最近标题 |
| 参数对齐论文（512 tokens / token 度量） | `max_tokens=512` + HuggingFace tokenizer |

**架构影响 — chunking 边界重新划分**：

原来设想 Docling 只负责解析（PDF → Markdown），chunking 全在 Java 侧。现在 Docling 可以直接产出所有格式的 chunks：

```
所有格式 → Docling Serve → HybridChunker → chunks (JSON, 带结构元数据)
                                                  ↓
Java:  DoclingDocumentParser → 映射到 DocumentChunk → embedding → pgvector
```

Java 侧不再有 chunking 逻辑，只负责配置参数（通过 `HybridChunkerOptions`）和映射结果（Docling chunk JSON → `DocumentChunk` domain model）。参数调优不需要改 Python 代码。

**实现方式**：仍使用 `DoclingServeApi` 直接调用（非 `DoclingDocumentReader` 全链路替换——避免绕过 ingest pipeline），在请求中启用 chunking options，让 Docling Serve 对所有格式返回 pre-chunked 结果。不再有 rawXhtml/cleanedHtml 中间产物，Java 侧 `StructuredFallbackDocumentChunker` 完整移除。

**Metadata 两层模型**（chunking 层面确认）：

```
第 1 层 — 文档级 processingMetadata（JSON, ingest_documents 表）
    来源：Docling 文档元数据（标题、作者、页数、语言、OCR 标识、解析引擎版本）
    → 解析阶段写入，下游只读

第 2 层 — Chunk 级 metadata（pgvector chunk 表字段）
    来源：Docling HybridChunker 的 chunk.meta（headings、page_no、content type）
    → 从 Docling 响应直接映射，不需要 Java 侧启发式推断
    → 当前 SourceHint 只有 heading 字符串，扩展为结构化字段：
        - headings: List<String>       面包屑标题链（如 ["Ch1", "§1.1"]）
        - pageNumber: int              源页码（引用溯源）
        - contentType: enum            PARAGRAPH / TABLE / LIST_ITEM / CODE_BLOCK / HEADING
```

**为什么 Docling 的 metadata 比 Java 自建强**：

1. **Headings 是面包屑，不是单一标题**：`HeadingContextExtractor` 只能拿最近一个标题。Docling 给的是完整路径 `["Chapter 1", "1.1 Background", "1.1.1 Motivation"]`——检索时如果 chunk 匹配了 "Motivation" 下的内容，heading 面包屑让 LLM 知道这个 chunk 在文档结构中的精确位置
2. **pageNumber 是算出来的，不是猜的**：Docling 的 layout analysis 知道每段文本在第几页。Java 侧正则完全做不到
3. **contentType 是原生信息**：Docling 知道这段内容是表格、列表还是段落。Java 侧只能靠正则猜（"有 `|` 就是表格"），但 Markdown 表格和正文中的 `|` 字符无法区分

| 选项 | 方案 | 复杂度 | 适合 |
|------|------|--------|------|
| A | Docling HybridChunker 接管所有格式（含原生 MD/HTML/TXT） | **低** — 所有格式统一路径，Java 侧只做映射，~150 LOC | **推荐**：metadata 质量一致，移除 Java chunker |
| B | Docling HybridChunker 接管 PDF/DOCX，Java chunker 保留给 MD/HTML | **低-中** — 两套 chunker 并存，metadata 质量不一致 | 妥协方案 |
| C | 维持 Java 自建 chunker（不利用 Docling chunking） | **高** — 需要自建结构感知切分 + 元数据提取 | 放弃 Docling 的结构感知能力 |
| D | 用 `DoclingDocumentReader` 全链路替换（parse+chunk+vectorize 一步） | **极低** — 但绕过 ingest pipeline，失去版本控制和状态追踪 | Demo/原型 |

**状态**: [x] 已决定 — 2026-06-05（2026-06-05 修订：统一所有格式走 Docling HybridChunker，移除 Java chunker）

**决定**: **Option A — Docling HybridChunker 接管所有格式**

**chunking 分工（统一 Docling 后最终版）**：

| 格式 | 解析引擎 | Chunking |
|------|---------|----------|
| PDF | Docling Serve | Docling HybridChunker（server-side） |
| DOCX | Docling Serve | Docling HybridChunker（server-side） |
| PPTX | Docling Serve | Docling HybridChunker（server-side） |
| XLSX | Docling Serve | Docling HybridChunker（server-side） |
| 图片 (PNG/JPG/TIFF) | Docling Serve（OCR） | Docling HybridChunker（server-side） |
| 原生 Markdown | Docling Serve | Docling HybridChunker（server-side） |
| 原生 HTML | Docling Serve | Docling HybridChunker（server-side） |
| TXT | Docling Serve | Docling HybridChunker（server-side） |

**理由**: 统一路径消除两套 chunker 并存的 metadata 质量差异——之前 PDF chunk 有面包屑 headings、pageNumber、contentType，而 MD/HTML chunk 只有正则猜的单一 heading 字符串。现在所有格式的 ChunkMetadata 字段一致性由 Docling HybridChunker 保证。MD/HTML/TXT 的 HTTP 往返开销在本地容器场景下可忽略（毫秒级），换来的是 `MarkdownSegmenter` + `HeadingContextExtractor` + `ChunkWindowAssembler` ~260 行 Java chunker 可以完整移除。

**DoclingDocumentParser 的职责**（D11 执行范围调整）：

原计划：DoclingDocumentParser 调 DoclingServeApi → 拿 Markdown 文本 → 交给 Java chunker
调整后：DoclingDocumentParser 调 DoclingServeApi（带 chunking options）→ 拿 pre-chunked 结果 + metadata → 映射为 `List<DocumentChunk>` → embedding

Java 侧不再有 chunking 逻辑，只负责配置参数（`HybridChunkerOptions`）和映射结果（Docling chunk JSON → `DocumentChunk` domain model）。

**HybridChunker 参数**（Java 侧配置）：

```java
ConvertDocumentOptions.builder()
    .doOcr(true)
    .includeImages(true)
    .chunkerOptions(new HybridChunkerOptions(
        512,           // max_tokens — 对齐论文最优 faithfulness 97.59
        true           // merge_peers — 合并过小块，等价滑动窗口效果
    ))
    .build();
```

**ChunkMetadata 域模型扩展**（替代当前 `SourceHint`）：

```java
public record ChunkMetadata(
    List<String> headings,    // 面包屑标题链 ["Chapter 1", "Section 1.1"]
    int pageNumber,           // 源页码（0 = 未知）
    ChunkContentType contentType // PARAGRAPH | TABLE | LIST_ITEM | CODE_BLOCK | HEADING
) {}
```

**执行顺序**：
1. D11 DoclingDocumentParser 落地（改用 chunking options，接收 pre-chunked 结果）
2. `SourceHint` → `ChunkMetadata` 域模型重构（~50 LOC）
3. D17 eval 基线验证（Docling+HybridChunker 的 Recall@5/MRR，重建黄金样本基线）
4. 后续迭代：将 `headings` 和 `contentType` 字段用于 D3 Hybrid Search 的权重调整（TABLE chunk 对 PROCEDURAL query 加权，PARAGRAPH chunk 对 FACTOID query 加权）

**理由**: Docling HybridChunker 已经在 Python 侧做完了结构感知切分 + 元数据提取，而且质量比 Java 自建高一个数量级（原生 layout analysis vs 正则猜标题）。所有格式统一走 Docling 后，Java 侧 `MarkdownSegmenter` + `HeadingContextExtractor` + `ChunkWindowAssembler` 的 ~260 行 chunker 可以完整移除，`StructuredFallbackDocumentChunker` 整个类删除。取而代之的是 ~150 行的映射逻辑（Docling chunk JSON → `DocumentChunk` domain model），以及 ~30 行的 TXT 格式适配（之前需要新增的 `NativeTextDecoder TXT 分支` 取消）。核心价值的代码变少，信息量变大，且所有格式的 metadata 质量一致。

**与 D3 联动**: `ChunkMetadata.contentType` 和 `headings` 可用于 Hybrid Search 的权重调整——TABLE chunk 对 PROCEDURAL query 加权，PARAGRAPH 对 FACTOID 加权；`headings` 面包屑可用于 RRF 融合时的 boost（标题层级匹配越深，chunk 权重越高）。

**与 D6 联动**: chunk 质量提升 → top-K 截断携带的信息密度增加 → 进一步降低引入显式 summarization 的必要性。

**与 D17 联动**: D17 eval 基线在 Docling 管道上建立（Recall@5 + MRR），作为后续 D3/D5 调优的量化基准。重建黄金样本（Docling 产出 MD 替代 Tika 产出 MD）。

**参考论文**: EMNLP 2024, Appendix A.2 — 512 tokens 最优 faithfulness (97.59), sentence-level 是最优粒度, sliding window 是最优技术。Docling HybridChunker 的 merge_peers=True 等价于论文的 sliding window，max_tokens=512 对齐论文最优参数。

---

### D7 — 认证架构: Session → ?

**背景**: 当前 HttpSession 内存存储，单实例 OK，多实例不共享。企业客户需要 SSO / 多实例部署。

| 选项 | 方案 | 复杂度 | 适合 |
|------|------|--------|------|
| A | Spring Session + Redis | **低** — 加依赖+配置，零代码 | 多实例部署，最短路径 |
| B | JWT (无状态) | **中** — 前端需配合 Token 管理 | 微服务/多端/长期 |
| C | OAuth2 / OIDC (对接企业 SSO) | **高** — 需认证服务器 | 企业客户 SSO 需求 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **维持现状 HttpSession + 代码标记 Phase 2**

- 当前单实例 HttpSession 内存存储满足所有场景——portfolio 项目定位不需要多实例
- 面试官 clone 后 `mvnw spring-boot:run` 单实例即用，无需额外基础设施
- 代码中标记 `@TODO Phase 2`：当多实例成为真实需求时，走 Spring Session + Redis（一个 Maven 依赖 + 一个 Redis 服务 + 零代码改动）
- **不选 JWT**：前端 Token 管理 + 刷新逻辑 + 安全存储是实打实的工作量，ROI 为负
- **不选 OAuth2/OIDC**：企业 SSO 场景，portfolio 项目完全用不上

**理由**: 不解决不存在的问题。提前抽象是分心——单实例还没跑通就做多实例架构，违反了"Boring technology wins"原则。真需要时，Spring Session + Redis 半天搞定。

---

### D8 — API 输入校验

**背景**: 当前无 Bean Validation，恶意/异常输入直接穿透到服务层。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | Spring Bean Validation (`@Valid` + `@NotBlank` 等) | **极低** — 加注解 |
| B | 手动校验 (在 Controller/Service 中) | 中 — 重复代码 |
| C | 不做 (信任前端) | 零 — 风险大 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option A — Bean Validation + `@ControllerAdvice` 统一错误格式**

- Controller 入参加 `@Valid` + `@NotBlank`、`@Size`、`@NotNull` 等 JSR-380 注解
- `@ControllerAdvice` 拦截 `MethodArgumentNotValidException`，返回结构化 `{ "field": "...", "message": "..." }` 而非 Spring 默认 500 页面（~15 LOC）
- Spring Boot 自带，零新依赖。预估半天加固完所有 Controller 入参

**理由**: Bean Validation 是 Spring Boot 标准做法，半天搞定。输入校验是系统边界最基础的安全防护——不能信任前端。统一错误格式让调用方能解析校验失败原因。

---

### D9 — API 文档

**背景**: 无 OpenAPI/Swagger。前后端对接靠人工沟通。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | SpringDoc OpenAPI (自动生成) | **低** — 加依赖 |
| B | 手写 API 文档 | 高 — 维护成本 |
| C | 不做 (当前规模不需要) | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option A — SpringDoc OpenAPI，按 profile 控制开关**

- 依赖：`springdoc-openapi-starter-webmvc-ui`（`pom.xml` 加一个 dependency），零配置自动生成 OpenAPI 3.0 spec + Swagger UI
- Swagger UI 路径：`/swagger-ui.html`（默认）
- **Profile 控制**：
  - `demo` / `dev` profile：默认开启
  - `production` profile：默认关闭（安全考量，不对外暴露 API 文档）
- 自定义信息：`springdoc.info.title`、`description`、`version` 从 `application.yml` 读取
- Controller 现有 `@RequestMapping` 等注解自动被 SpringDoc 识别，无需额外注解

**理由**: SpringDoc 是 Spring Boot 生态事实标准，一个 Maven 依赖即可。可交互的 API 文档是项目"专业感"的最低门槛——无论个人 portfolio 展示还是企业交付。production 关闭避免暴露内部接口。

---

### D10 — 部署容器化

**背景**: 应用无 Dockerfile，docker-compose 只有 PG+RustFS。"面试官 clone 5 分钟跑通"无法实现。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 多阶段 Dockerfile + docker-compose 全服务编排（含应用镜像） | **中** — 2 天 |
| B | Cloud Native Buildpacks (免 Dockerfile) | **低** — Spring Boot 原生支持 |
| C | 不做容器化，保持 `mvnw spring-boot:run` | 零 — 但不满足私有化定位 |
| D | **双轨策略**（docker-compose 管基础设施，应用 java -jar） | **低-中** — 1-2 天 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option D — 双轨策略：docker-compose 编排基础设施 + java -jar 运行应用**

- **基础设施容器化**（`docker-compose up -d`）：PG + RustFS + Docling（3 个服务，含 health check 依赖链）
- **应用不打包 Docker 镜像**：`mvnw spring-boot:run`（开发）/ `java -jar` + systemd（生产）。企业客户保留 JVM 运维习惯（GC 调优、监控 agent）
- `.env.example` 含所有必需环境变量 + 中文注释
- Spring profile 区分场景：`demo`（local 存储 + 无 OCR）vs `production`（S3/RustFS + 完整 Docling）
- CI 验证 job：`docker compose up -d && sleep 30 && curl --fail http://localhost:8080/actuator/health`
- 备选：如需 OCI 镜像分发，用 **Jib**（分层缓存更智能，无需 Docker daemon，不写 Dockerfile）

**理由**: 一键启动（`docker compose up -d` + `mvnw spring-boot:run`）满足"面试官 clone 5 分钟跑通"的准入门槛。应用不入镜像保留企业客户 JVM 运维灵活性，同时避免 Dockerfile 维护成本。Boring technology wins.

**与 D11 联动**: Docling（Python）作为基础设施服务进 docker-compose，不违反本决策。

---

### D11 — 文档解析: Docling 替代 Tika

**背景**: 当前 Tika 2.9.2，复杂 PDF 解析质量一般。经 Winston 架构评审（2026-06-05），确认 Arconia `DoclingDocumentReader` 同时覆盖解析 + chunking（HybridChunker 结构感知 + token 感知），PDF/DOCX/PPTX/XLSX/图片全部覆盖，质量比 Tika→XHTML→清洗→MD 四跳管道高一个数量级。

**状态**: [x] 已决定 — 2026-06-05（2026-06-05 修订：Tika 从"保留"升级为"完全移除"）

**决定**: **Docling 替代 Tika 成为所有复杂格式的唯一解析 + chunking 路径。Tika 及其依赖从项目中完全移除。**

**解析路由（简化后）**：

| 格式 | 解析引擎 | Chunking |
|------|---------|----------|
| PDF | Docling Serve | Docling HybridChunker（server-side） |
| DOCX | Docling Serve | Docling HybridChunker（server-side） |
| PPTX | Docling Serve | Docling HybridChunker（server-side） |
| XLSX | Docling Serve | Docling HybridChunker（server-side） |
| 图片 (PNG/JPG/TIFF) | Docling Serve（OCR） | Docling HybridChunker（server-side） |
| Markdown | Java NativeTextDecoder | Java StructuredFallbackDocumentChunker |
| HTML | Java NativeTextDecoder | Java StructuredFallbackDocumentChunker |
| TXT | Java NativeTextDecoder（新增 ~5 LOC） | Java StructuredFallbackDocumentChunker |
| CSV/EPUB/RTF 等 | 不支持（上传时拒绝） | — |

**实现路径**：Arconia `DoclingServeApi`（直接调用，非 `DoclingDocumentReader` 全链路替换——避免绕过 ingest pipeline）。

**移除的组件**：
- `tika-core`、`tika-parsers-standard-package` Maven 依赖
- `TikaDocumentTextParser`（~235 行）、`TikaParseContextFactory`、`NoOpEmbeddedDocumentExtractor`
- `DocumentParseResult` 中 `rawXhtml`、`cleanedHtml` 字段（Docling 直接产出 MD，无 XHTML 中间产物）
- `DocumentParserRouter` 中 `TIKA` 枚举值

**保留的组件**：
- `DocumentTextParser` 端口 — `DoclingDocumentParser` 作为新实现
- `DocumentChunker` 端口 — 仅用于 MD/HTML/TXT 路径
- `TextCleaningService` — MD/HTML 路径仍需要清洗
- `StructuredFallbackDocumentChunker` + `MarkdownSegmenter` + `HeadingContextExtractor` + `ChunkWindowAssembler` — 仅服务 MD/HTML/TXT

**执行范围**（修订后）：
1. `docker-compose.yml` 加 `docling-serve` 服务（含 health check 依赖链）
2. 新建 `DoclingDocumentParser`，注入 Arconia `DoclingServeApi`，带 `HybridChunkerOptions(max_tokens=512, merge_peers=true)`
3. 重构 `DocumentParserRouter`：删除 `TIKA` 路由，新增 `DOCLING` 和 `REJECT` 路由
4. `DocumentParseResult` 简化（去 rawXhtml/cleanedHtml）
5. 双轨验证 1-2 天后删除 Tika + 所有相关文件 + pom.xml 依赖
6. 重建黄金样本基线（Docling 输出 vs 旧 Tika 输出对比）
7. 净增代码 < 150 行（映射逻辑），净删代码 ~500 行（Tika + XHTML 清洗链路）

**风险与缓解**：
- **Docling Serve 不可用 = PDF/DOCX 解析完全阻塞**：D10 `docker-compose.yml` 中 Docling 必须配 health check + 依赖链；Actuator health endpoint 暴露 Docling 连通性；`DoclingDocumentParser` 启动时验证连通性（fail-fast）
- **黄金样本回归闭环断裂**：Docling 产出 MD 和 Tika 产出 MD 质量不在一个世界，旧黄金样本不适用——需重建 Docling 基准
- **Arconia 版本稳定性**（BOM 0.27.1，未到 1.0）：仅在一处使用（`DoclingDocumentParser`），API 变动影响范围可控

**理由**: 文档解析质量直接决定下游全链路质量。Docling 覆盖面覆盖个人知识库 95%+ 场景，Tika 剩余覆盖的三个格式（CSV/EPUB/RTF）可安全拒绝。用更少的代码得到更好的质量——净删 ~350 行代码的同时解析质量跃升一个数量级。

---

### D12 — 向量残留清理

**背景**: 已删除/旧版本文档的向量不被自动清除。依赖 FilterExpression 过滤而非物理删除。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | Phase 2 加后台清理任务 | **低** — 定时 SQL |
| B | 在删除操作时同步清理向量 (触发器) | 中 — 实时但影响删除性能 |
| C | 不做 (FilterExpression 已防住，脏数据只是存储问题) | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option C — 先不做，代码标记 TODO Phase 2**

- 当前 FilterExpression 已隔离脏数据——旧版本文档的旧向量不会被查出来，无功能影响
- 唯一代价：pgvector 存储空间浪费。个人知识库几百份文档，向量占用可忽略
- 代码中标记 `@TODO Phase 2`：文档量上去了，一个 `@Scheduled` 定时 SQL（按 `document_id` + `version` 清理旧向量）10 分钟的事

**理由**: FilterExpression 已防住脏读，存储浪费在当前规模下不是问题。不需要在 Phase 1 花时间做清理——先把增量的路走通，再回头清存量的垃圾。

---

### D13 — Embedding 维度硬编码

**背景**: 当前硬编码 1024 维 (text-embedding-v4)。切换 Ollama 模型时维度可能不同，需全量向量重建。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 改为配置项，选同维度 Ollama 模型 (如 bge-m3: 1024) | **低** — 改动小 |
| B | 支持不同维度，建立迁移脚本 | 中 — 需灰度方案 |
| C | 不做 (先选好模型再定) | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option A — 配置项 + 双模型同维度对齐（1024） + 启动 fail-fast 校验**

- `application.yml` 加 `app.embedding.dimension: 1024`，消除硬编码
- 两个 profile 共用 1024 维：
  - **默认 dashscope profile**：`text-embedding-v4`（1024 维）
  - **可选 ollama profile**：`bge-m3`（1024 维）
- 两个模型维度恰好一致 = pgvector schema 在两个 profile 下都不需要改，零迁移成本
- 启动时校验：`EmbeddingModel.dimensions()` == 配置值，不匹配直接 fail-fast（维度不匹配静默污染检索质量是最难排查的生产事故）
- 校验时机：`ApplicationRunner` + `@Order(LOWEST_PRECEDENCE - 100)`，必须在 pgvector 自动建表之前执行
- **注意**：维度相同 ≠ 向量可互换。切换 profile 后新旧向量不在同一语义空间，需全量 re-index
- 不建通用迁移脚本（B）——不知道第二个模型长什么样就提前抽象，多半猜错

**理由**: 两个 Embedding 模型维度恰好一致（1024），这是选型时有意对齐的结果。配置化 ~20 行代码。将来切不同维度模型是独立的迁移任务（全量 re-index），不是配置项能解决的。

---

### D14 — Prompt 模板管理

**背景**: Prompt 模板硬编码在 `AskQuestionApplicationService` 中。换模型需要调整 prompt，每次改 Java 代码。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 外部化到配置文件 (`application.yaml`) | **低** — 一天 |
| B | 数据库管理 prompt (支持热更新) | 中 — 需管理界面 |
| C | 保持硬编码 | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option A — 外部化到 `classpath:/prompts/*.st` 文件 + Git 版本控制**

- 目录结构（按 profile 隔离，默认 dashscope）：
  ```
  src/main/resources/prompts/
    dashscope/                  # 默认 profile，DashScope qwen-max 适配
      chat-system.st
      rag-context.st
    ollama/                     # 可选 profile，Ollama qwen2.5 适配
      chat-system.st
      rag-context.st
  ```
- 加载方式：`@Value("classpath:/prompts/${spring.profiles.active}/*.st")` 自动按 profile 加载对应目录，不需要 if-else
- Prompt 作为代码的一部分，和代码一起版本控制（Git）
- 换模型调 prompt = 改 .st 文件 + 重启，不需要重新编译
- **不选数据库管理（B）**：prompt 不是运行时数据，DB 存 prompt 是过度工程
- **区分两类 prompt**（Winston 提醒）：模板型（system prompt、RAG 拼接）→ 外置；逻辑型（意图分类、实体抽取，输出被 Java 解析）→ 保留硬编码
- **与 D2 联动**：FallbackChatModel 运行时降级时，prompt 不切换（仍用当前 profile 的模板）。降级只换模型，不换 prompt 风格

---

### D15 — 可观测性方案

**背景**: 无结构化日志、无 Trace ID、无关键指标暴露。企业客户自己运维时无法定位问题。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 最小方案: JSON 日志 + Trace ID + Micrometer `@Timed` | **低** — 1-2 天 |
| B | ELK/Grafana 全家桶 | **高** — 基础设施重 |
| C | 先不做 (私有化前再做) | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option A (JSON 日志 + Trace ID + Micrometer @Timed)**

- Trace ID：`OncePerRequestFilter` → MDC → 响应头 `X-Trace-Id`，零外部依赖
- JSON 日志：Logback 内置 `JsonEncoder`，不引入 Logstash。MDC 自动携带 traceId/userId/documentId
- Micrometer 关键指标：`document.processing.duration` (histogram)、`document.processing.errors` (counter)、`document.queue.size` (gauge)、`downstream.calls.duration` (histogram)
- `/actuator/prometheus` + `/actuator/health`(含 db/diskSpace/doclingClient)
- 日志级别热切换 (`/actuator/loggers`)
- **核心原则**: 能力暴露 ≠ 依赖引入。JSON→stdout（jq 可查），指标→HTTP endpoint（curl 可看）
- **执行顺序**: D15 必须在 D1 之前（并发处理后没有 Trace ID 无法追踪链路；MDC 跨 Virtual Thread 传递需显式配置）

**待确认**: spike 是否认可以上方案

---

### D16 — Controller 重构范围

**背景**: `DocumentIngestController` 796 行 (臃肿)，`AcceptUploadApplicationService` 419 行 (职责过重)。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 拆分 Controller + 提取子 Service | **中** — 2-3 天，有回归风险 |
| B | 只拆 Service，Controller 先不动 | **低** — 1 天 |
| C | 先不改，等 Group Model 迁移时一起做 | 零 — 但拖得越久越难 |
| D | 不做 (功能正常，只是不优雅) | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option B — 只拆 Service，Controller 等 Group Model 迁移后动**

- **第一阶段（本次）**：拆分 `AcceptUploadApplicationService`（419 行）为多个子 Service，提取职责。纯内部重构，零 API 变动，零回归风险。预估 1 天
- **第二阶段（Group Model 后）**：`DocumentIngestController`（796 行）拆分，路由重新分配。等 D20/D21 领域模型定稿后再做，避免拆完又要改
- 子 Service 拆分方向：
  - `DocumentUploadService` — 上传 + 格式校验
  - `DocumentParseOrchestrationService` — 解析编排
  - `IngestLifecycleService` — 状态管理

**理由**: Service 层拆分是纯内部重构，不碰 Controller 路由，前端零感知。Controller 拆分涉及 API 路径重新分配——Group Model（D20/D21）上线后权限模型和路由结构都会变，现在拆 Controller 大概率要返工。先救 Service，Controller 等大图定了再说。

---

### D17 — RAG 效果评估体系

**背景**: 无量化指标。不知道当前检索命中率。每次改 RAG 参数无法判断变好还是变坏。Murat 警告：没有 eval 就上 D3/D5 是"没有血压计就开降压药"。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 建立评估数据集 (50+ 问答对) + RAGAs 四指标 + GPT-4 judge | **中** — 3-4 天 |
| B | 轻量版：20 手写 QA + Recall@5 + MRR（纯数学）+ 10 QA 的 Faithfulness（LLM judge） | **低-中** — 1-2 天 |
| C | 先用人工抽检 (每次改动手动对比 10 个问题) | **低** — 半小时/次 |
| D | 不做 (先上线再迭代) | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option B — 轻量评估体系（分层评估，不依赖 RAGAs Python 包）**

**分层评估架构**（Murat 提出）:

**Layer 1 — Retrieval Quality（检索质量，不依赖 LLM judge）**:
- 指标：`Recall@5` + `MRR`（纯数学计算，跑一次 ~30 秒）
- 数据：20 个手写 QA pairs + 标注相关文档 ID 列表
- 用途：D3 参数调优、D5 规则迭代的数据基础
- 实现：`EvalRunner.java` 直接调 `ChunkRetrievalPort`，不经过 LLM

**Layer 2 — Generation Quality（生成质量，依赖 LLM judge）**:
- 指标：`Faithfulness`（答案是否仅基于检索文档）+ `Answer Relevancy`（是否回答原问题）
- 数据：10 个手写 QA pairs（有标准答案）
- Judge：DashScope qwen-max（成本低，知道有偏差但同模型评同模型偏差可控）
- 用途：端到端回归检测——"这次改动有没有让答案变差？"

**Layer 3 — 可选扩展（将来）**:
- 合成 QA 对（100-200 条，LLM 从文档自动生成）用于大规模回归
- 对抗 QA 对（10-15 条，刻意设计会出错的查询）用于边界测试
- `NDCG@K` 排名质量指标

**数据构建**:
- 手写 QA：覆盖主要查询类型（概念定义、操作指南、对比分析、列表枚举），标注相关文档 ID + 标准答案
- 维护成本：每次跑 < 5 分钟（手动触发），不能超过这个时间否则不会跑
- 每次 RAG pipeline 改动前跑基线 + 改动后跑对比，不设绝对阈值（"不能比上次差"）

**不选 RAGAs Python 包**：避免引入 Python 依赖。Faithfulness 等指标用 Java 调 LLM 实现，逻辑不复杂（论文公式可直接翻译）。

**预估**：~300 LOC（Java eval runner） + 30 QA pairs 手工标注。代码 1 天，标注 1 天，总计 2 天。

**理由**: Murat 不可商量的底线——"没有测量工具就上 pipeline 优化，等于没有血压计就开降压药"。D3/D5 做完后没有 eval 就是盲飞。John 认可 eval 是"放大器"——本身不产出用户价值，但让其他所有模块的价值可量化。轻量版比完整版省一半时间，但给出真实信号。面试时"建立量化评估体系，用 Recall/MRR 衡量检索质量"直接展示 senior 工程素养。

**执行顺序**: D5 → D3 做完后立刻做 D17，D3 期间用手工验证过渡（Murat 接受）。D17 必须在 D3 参数调优和 D5 规则迭代之前就位。

---

### D18 — 权限过滤测试

**背景**: 无自动化测试验证"不同用户对同一问题拿到不同结果"。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 权限矩阵自动化测试 (N 用户 × M 文档) | **中** — 2 天，安全关键 |
| B | 手动测试 + 审计日志验证 | **低** — 不够可靠 |
| C | 先不做 (等 Group Model 落地后加) | 零 — 有风险 |

**状态**: [/] 讨论中 — 2026-06-05

**暂定方向**: **Option A — 权限矩阵自动化测试，4 个核心场景**

测试范围（与 D20/D21 联动，暂定）：
1. 同一问题，不同用户拿到不同结果（权限生效）
2. 用户只能看到自己 ACTIVE Group 内的文档（隔离有效）
3. DOC_ALLOW 单独授权生效
4. DOC_DENY 排除生效，Workspace Admin 免疫 DOC_DENY

**待确认**: 等 D20/D21 模型定稿后锁定具体 test case 清单。

---

### D19 — ArchUnit 架构测试

**背景**: 无架构规则验证。Hexagonal 边界可能随时间退化。

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | 加 ArchUnit 规则 (domain 不依赖 Spring, 无循环依赖等) | **低** — 半天 |
| B | 不做 (架构还小，靠 review 守住) | 零 |

**状态**: [x] 已决定 — 2026-06-05

**决定**: **Option A — ArchUnit 3 条核心规则，CI 强制执行**

三条规则（~30 LOC），守住 Hexagonal 边界：

1. **domain 包不依赖 Spring**：`classes().that().resideInAPackage("..domain..").should().onlyDependOnClassesThat().resideOutsideOfPackage("org.springframework..")`
2. **adapter 包之间不互相依赖**：`slices().matching("..adapter.(*)..").should().notDependOnEachOther()`——适配器只能通过 port 通信
3. **无循环依赖**：`slices().matching("com.example.(*)..").should().beFreeOfCycles()`——包级 DAG

- 放在 `src/test/java/` 的 `architecture` 包下，随 `mvn test` 执行
- CI pipeline 中作为独立 test phase，犯规 = 构建失败
- 超过 3 条就是过度工程——当前架构规模不需要更多

**理由**: 架构约定不能靠 code review 口头协议。三条规则半天写完，但价值持续兑现——后续 D1（异步）、D3（Hybrid Search）、D11（Docling 替换 Tika）等重构时，有人不小心把 Spring 依赖漏进 domain 层，ArchUnit 替你拦住。CI 红线比 wiki 文档管用。

---

### D20 — Group Model 迁移: KnowledgeBase 名称去留

**背景**: `KnowledgeBase` 实体将被 `Group` 替代。但"知识库"作为文档组织概念仍有价值——Group 是权限边界，"文档集合"是否需要另一个实体？

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | KnowledgeBase → Group (直接改名，Group = 权限 + 文档集合) | 简单，但概念混了 |
| B | Group (权限) + Collection (文档分组) 两个实体分离 | 概念清晰但表多 |
| C | Group 替代 KnowledgeBase，文档分组用 Tag 实现 | 最灵活 |

**状态**: [/] 讨论中 — 2026-06-05

**暂定方向**: **Group + document_groups 多对多 + 文档级 ACL 三层架构**

#### 三层权限架构

```
第一层：Group 成员（主路径，覆盖 95%）
  人 → group_member → Group → document_groups → 文档
  "你是技术部的人，技术部有哪些文档你就能看哪些"

第二层：零 Group 草稿（上传时的中间态）
  上传者 → 自己的文档（document_groups 无记录）
  "刚传上去还没分类的文档，只有自己能看"

第三层：文档级例外（按需覆盖，v1 有 API 无 UI）
  DOC_ALLOW → "给老王看一眼这份设计，他不在任何相关 Group"
  DOC_DENY  → "全组都能看除了实习生"
```

#### 可见性公式

```
用户可见文档 =
    { 文档 ∈ 用户 ACTIVE Group 的 document_groups }
  ∪ { 上传者 = 当前用户 且 零 Group }
  ∪ { DOC_ALLOW 中明确授权 }
  ∪ { Workspace Admin → 全库 }
  - { DOC_DENY 中明确排除 且 用户 ≠ Workspace Admin }
```

Workspace Admin 免疫 DOC_DENY——管理员不被他人的拒绝规则挡掉。

#### 数据模型

```
users
  + workspace_role: ENUM('ADMIN', 'MEMBER')  -- 默认 MEMBER，单 Workspace

group
  - id, name, workspace_id, created_by
  - 创建者自动成为第一个 MANAGER

group_member
  - group_id + user_id (联合主键)
  - role: MANAGER | CONTRIBUTOR | READER
  - status: PENDING | ACTIVE
  - 拒绝/移出 = DELETE（不留 INACTIVE）
  - PENDING 成员不可见 Group 文档

document_groups
  - document_id + group_id (联合主键)
  - min_role: MANAGER | CONTRIBUTOR | READER | NULL
    (v1 建列不暴露给用户，预留未来约束跨 Group 写权限)

document
  - id, uploader_id, workspace_id
  - 权限从 document_groups 继承
  - 无独立 ACL 字段（例外走 doc_allow/doc_deny 表）

doc_allow
  - document_id + user_id (联合主键)
  - v1: API 可用，无管理 UI

doc_deny
  - document_id + user_id (联合主键)
  - v1: API 可用，无管理 UI
```

#### 核心规则

| 规则 | 决定 |
|------|------|
| 主路径 | Group → document_groups → 文档 |
| 跨 Group 角色 | Highest Wins，同时作用于读和写（v1 接受） |
| minRole | document_groups 建列，v1 不暴露，预留未来约束跨 Group 写权限 |
| 草稿区 | 零 Group = 仅上传者 + Admin 可见 |
| 邀请 | MANAGER 发起 → PENDING → 接受 = ACTIVE / 拒绝 = DELETE |
| 移出 Group | DELETE 记录 |
| Workspace Admin | User.workspace_role = ADMIN，免疫 DOC_DENY，全库可见 |
| DOC_ALLOW/DOC_DENY | v1 保留 API + 数据模型，不加管理 UI |
| 检索性能 | Java 侧合并 Set → 一次 `ANY(array)` + GIN 索引 |
| 超阈值降级 | <5K `ANY(array)` / 5K-50K 候选过滤 / 50K+ 临时表 JOIN |

#### 与 RAG 链路的耦合

RAG 检索层只依赖 `Set<String> visibleDocIds` 参数，不关心其来源（旧 KB Grant / 新 Group / ACL）。权限模型可以暂定，不影响 D1-D6、D11、D17、D22 先行实施。

#### 待 review 确认

- Murat：ACL 三层架构下的测试矩阵覆盖范围
- Sally：v1 无 ACL UI 时，上传后的可见性提示 UX
- John：portfolio 演示场景优先级——检索 vs 权限管理

---

### D21 — 权限模型兼容期策略

**背景**: Group Model 上线后，旧的 KB Grant 数据需要共存。双轨运行还是直接迁移？

| 选项 | 方案 | 复杂度 |
|------|------|--------|
| A | Feature flag 双轨运行 (旧 KB Grant + 新 Group)，验证后删旧 | 安全但代码量大 |
| B | 一次性迁移脚本 (停服→迁移→重新上线) | 简单但有停机 |
| C | 在线迁移 (写新读旧→写新读新→清旧) | 复杂但零停机 |

**状态**: [/] 讨论中 — 2026-06-05

**暂定方向**: **Option B — 一次性迁移脚本**

- 停服 5 分钟：起新表（group / group_member / document_groups）→ 旧数据转换（KB → Group + KB Grant → group_member + kb_id → document_groups）→ 旧表标记废弃
- 迁移脚本先在 CI 测试 PG 实例上验证数据一致性
- Portfolio 项目无 SLA 约束，零停机不是硬性需求
- 不选双轨（A）：在代码里维护两套权限逻辑的复杂度 > 停服 5 分钟
- 不选在线迁移（C）：给有 SLA 的系统设计的，portfolio 用不上

**待确认**: 等 D20 模型定稿后确定迁移脚本的字段映射。

---

## 状态汇总

- 共 22 个决策点
- 状态: [x] 已决定: 19 (D1—D17, D19, D22)
- [/] 讨论中: 3 (D18, D20, D21)
- [ ] 待讨论: 0
