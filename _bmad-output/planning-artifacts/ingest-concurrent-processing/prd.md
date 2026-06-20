---
title: "Ingest 并发处理 + 批量上传 + 前端体验升级"
status: final
created: 2026-06-20
updated: 2026-06-20 (final)
author: spike
---

# PRD: Ingest 并发处理 + 批量上传 + 前端体验升级

## 1. Problem & Context

### 1.1 现状

my-AI 的文档入库（ingest）流程采用单线程串行处理：`InProcessWorker` 每 5 秒轮询一次，每次只处理 1 个文档，同步执行"解析 → 分块 → 向量化"全链路。Docling OCR 处理一个大 PDF 耗时 30-60 秒，期间 Worker 完全阻塞。

**上传端**：只支持单文件上传，无批量操作能力。
**处理端**：30 个文档排队需要 15-30 分钟串行等待。
**反馈端**：上传后无实时进度反馈，用户需手动刷新页面查看状态。

### 1.2 用户痛点

| 痛点 | 表现 | 影响 |
|------|------|------|
| 无法批量上传 | 传 10 个文档需要重复 10 次"选文件→选知识库→提交" | 用户体验差，操作成本线性增长 |
| 处理队列阻塞 | 上传后所有文档排队串行处理 | 大文件阻塞后续小文件，整体等待时间不可接受 |
| 无进度感知 | 上传完成后不知道文档何时处理完 | 用户反复手动刷新页面，或误以为"卡住了" |
| 运维定位困难 | 文档处理失败时，用户无法提供有效的排障信息 | 运维需逐条翻日志，定位效率低 |

### 1.3 机会

Java 21 的 Virtual Thread 为 I/O 密集型任务提供了零成本并发能力。项目已在 decision register D1 中确认采用 Poll-and-Submit 模式，CAS 状态机天然支持并发安全。前端 Ant Design 的 Upload 组件原生支持多文件选择。**这次改动零新后端依赖，成本可控，收益立竿见影。**

---

## 2. Goals & Success Metrics

### 2.1 目标

| # | 目标 | 衡量方式 |
|---|------|---------|
| G1 | 后端文档处理吞吐量提升 3-5 倍 | 30 个文档：串行 15-30min → 并发 5-10min |
| G2 | 支持批量文件上传 | 一次操作可提交 ≥10 个文件 |
| G3 | 实时反馈处理进度 | 上传后页面自动刷新状态，无需手动操作 |
| G4 | 运维排障效率提升 | 失败文档可一键复制 documentId 定位日志 |

### 2.2 成功标准

| 指标 | 基线（当前） | 目标 |
|------|-------------|------|
| 30 个 PDF 串行处理时间 | 15-30 min | ≤10 min（parallelism=3） |
| 批量上传操作步骤（10 个文件） | 10 × 3步 = 30步 | 1步（拖入+提交） |
| 状态感知延迟 | 手动刷新（分钟级） | 自动轮询（3秒级） |
| 新增后端依赖数 | — | 0 |

---

## 3. Scope

### 3.1 In Scope

| 功能域 | 范围 |
|--------|------|
| 后端 Worker 并发 | `InProcessWorker` 改造：Virtual Thread + Semaphore + batch claim + graceful shutdown |
| 后端配置扩展 | `IngestProperties.Worker` 新增 `parallelism`、`batchSize` |
| 前端批量上传 | `/ingest/upload` 页面：多文件选择 + 上传队列 + 并发控制 + 结果展示 |
| 前端状态面板 | `/ingest/documents` 页面：条件轮询 + 状态摘要栏 + 快速重试 + documentId 展示 |
| API 层 | 确认现有 `POST /upload` 能被前端循环调用，无需新端点 |

### 3.2 Out of Scope

| 排除项 | 理由 |
|--------|------|
| 后端 `POST /upload/batch` 批量端点 | MVP 阶段前端循环调用现有单文件 API 已足够，batch 端点作为后续优化 |
| `ProcessDocumentApplicationService.handle()` 内部逻辑改动 | 5 步 pipeline 有严格数据依赖，内部并行化无收益 |
| CAS 状态机变更 | 现有状态机（UPLOADED → INGESTING → INDEXED/FAILED）已支持并发安全 |
| 消息队列（RabbitMQ/Kafka） | 当前单实例场景不需要，作为下一次架构升级的选项 |
| Worker 层超时控制 | 由 Docling client `read-timeout: 30s` + `RetryPolicy` 已覆盖 |
| 前端路由结构变更 | 只改现有页面组件，不新增路由 |
| 新增后端 Maven 依赖 | Virtual Thread 是 JDK 21 自带能力 |
| 前端 npm 依赖 | 并发队列用原生 Promise 实现 |

---

## 4. Features & Requirements

### F1: Worker 并发处理

> 核心改动：将 `InProcessWorker` 从单线程串行改为 Poll-and-Submit 并发模式。

**FR-1.1** `InProcessWorker` 每轮 poll 最多 claim `batchSize` 个文档（默认 3）

**FR-1.2** CAS 抢占成功后，通过 `Executors.newVirtualThreadPerTaskExecutor().submit()` 提交到 Virtual Thread 执行，`@Scheduled` 线程立即返回

**FR-1.3** `Semaphore(parallelism)` 控制最大并发文档数（默认 3），`tryAcquire` 在 claim 前、`finally release` 在处理完成后

**FR-1.4** `@PreDestroy` 优雅关闭：`executor.shutdown()` + `awaitTermination(5, MINUTES)`，保证容器关闭时 in-flight 任务完成

**FR-1.5** MDC `documentId` 注入：每个 Virtual Thread 执行前设置 MDC，完成后清除，保证日志链路可追踪

**FR-1.6** 配置项 `myai.ingest.worker.parallelism`（默认 3）、`myai.ingest.worker.batch-size`（默认 3）、`myai.ingest.worker.enabled` 默认改为 `true`

**FR-1.7** Semaphore 满时跳过剩余 claim，log.debug 输出 "All N processing slots busy"

**FR-1.8** 无待处理文档时提前退出循环，log.debug 输出

**FR-1.9** Virtual Thread 内捕获未预期异常，log.error 输出 documentId + 异常栈，防止线程静默消失

---

### F2: 批量上传（前端）

> 改造 `/ingest/upload` 页面，支持多文件选择和队列化提交。

**FR-2.1** `Upload.Dragger` 改为 `multiple={true}`，移除 `maxCount={1}` 限制

**FR-2.2** 拖入/选择多个文件后，显示上传队列列表：文件名、文件大小、当前状态（等待中/上传中/已提交/失败）

**FR-2.3** 并发控制：前端实现轻量级并发队列，同时进行中的上传数限制为 3，用原生 Promise + 计数器实现（~20 LOC，零外部依赖）

**FR-2.4** 上传成功：文件行显示 ✅ + 返回的 documentId（可复制）

**FR-2.5** 上传失败：文件行显示 ⚠️ + 错误原因 + documentId（如有），支持单独重试

**FR-2.6** 汇总结果区：显示"已提交 N 个文档"或"N 成功 / M 失败"，提供"查看文档目录"链接跳转 `/ingest/documents`

**FR-2.7** 总进度条：显示已完成/总数的进度

**FR-2.8** documentId 一键复制：点击即复制到剪贴板，运维可用该 ID 在 Kibana 直接定位后端日志

---

### F3: 文档状态面板升级（前端）

> 改造 `/ingest/documents` 页面，提供实时状态感知和快速操作。

**FR-3.1** 状态摘要栏：页面顶部显示全库各状态数量（处理中 N / 完成 N / 失败 N），不受筛选条件影响

**FR-3.2** 条件轮询：当前页存在 UPLOADED 或 INGESTING 状态的文档时，`refetchInterval: 3000`；所有文档为终态时，`refetchInterval: false`，停止无意义请求

**FR-3.3** INGESTING 状态动画：`DocumentStatusTag` 组件对 INGESTING 状态增加 CSS pulse 动画，视觉上区分"处理中"和"已完成"

**FR-3.4** 快速重试：列表行增加"重试"按钮，对 FAILED 状态文档可直接触发 `reprocessDocument()`，无需进入详情页。所有用户可见（受现有 `canContributeKnowledgeBase` 权限约束）

**FR-3.5** documentId 展示：失败行的错误信息 Tooltip 中附带 documentId，支持一键复制

**FR-3.6** 批量上传后自动跳转：上传页提交完成后，点击"查看文档目录"自动带入 kbId 筛选条件，只显示刚上传的知识库文档

---

## 5. Non-Functional Requirements

### NFR-1: 性能

- 30 个文档并发处理时间 ≤ 10 min（parallelism=3，假设平均 Docling 处理 30s/文档）
- 前端上传队列不阻塞 UI 线程（异步提交）

### NFR-2: 可靠性

- Worker 优雅关闭：容器停止时等待 in-flight 任务完成（最多 5 min）
- CAS 状态机不变：并发处理不引入新的竞态条件
- 上传失败不影响队列中其他文件（独立错误处理）

### NFR-3: 可观测性

- 每个并发处理的文档有 MDC documentId，日志可按文档维度聚合
- 现有 IngestMetrics（process.success.total / failed.total / retry_scheduled.total）自动覆盖并发场景，无需新增指标

### NFR-4: 可配置性

- parallelism、batchSize、pollDelayMs 均通过环境变量可配
- 不同环境可使用不同并发度（开发环境 parallelism=1，生产环境 parallelism=3+）

### NFR-5: 升级路径

- Worker 层改造后，升级到 MQ 多实例只改任务来源（`@Scheduled poll` → `MQ consume`），`ProcessDocumentUseCase.handle()` 和 Semaphore 背压逻辑零改动

---

## 6. Risks & Dependencies

| 风险 | 影响 | 缓解 |
|------|------|------|
| Docling Serve 并发能力不足 | 3 个并发解析请求可能压垮 Docling | Docling read-timeout=30s + RetryPolicy 瞬时错误重试；parallelism 可配，生产环境按 Docling 实际能力调整 |
| DashScope Embedding API 限流 | 3 个并发 embedding 可能触发 429 | 现有 `DashScopeEmbeddingBatchingStrategy`（max 10 条/batch）+ RetryPolicy（429 = 瞬时错误）已覆盖 |
| PostgreSQL 连接池压力 | 3 个并发 pipeline 同时写 DB | [ASSUMPTION] HikariCP 默认连接池（10）足够，3 个并发 < 连接池上限 |
| 前端浏览器并发限制 | HTTP/1.1 同域 6 连接上限 | 前端并发队列限制为 3，在浏览器限制内 |

---

## 7. Open Questions

| # | 问题 | Owner | 状态 |
|---|------|-------|------|
| OQ-1 | ~~FR-3.4 快速重试按钮是否 admin-only？非 admin 用户是否有 reprocess 权限？~~ | spike | ✅ 已确认：所有用户可见，只要对 KB 有写权限即可重试，复用现有 `canContributeKnowledgeBase` 校验 |
| OQ-2 | ~~状态摘要栏是否需要实时更新（WebSocket/SSE），还是依赖 3s 轮询即可？~~ | spike | ✅ 已确认：3s 轮询足够，不引入 WebSocket |
| OQ-3 | ~~parallelism 默认值 3 是否需要根据 Docling Serve 的实际并发能力做压力测试？~~ | spike | ✅ 已确认：先用 3，上线后根据 metrics 和 Docling CPU/内存观察再调优，运行时可配 |

---

## 附录

- **SPEC**: `ingest-concurrent-processing-spec.md`（同目录，功能规格细节）
- **Decision Register**: `research/decision-register.md` D1（技术选型过程和理由）
- **相关决策**: D22（Chunking 策略）、D11（Docling 替代 Tika）
