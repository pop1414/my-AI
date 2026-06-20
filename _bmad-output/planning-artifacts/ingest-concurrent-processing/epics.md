---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - '_bmad-output/planning-artifacts/ingest-concurrent-processing/prd.md'
  - '_bmad-output/planning-artifacts/ingest-concurrent-processing/architecture.md'
  - '_bmad-output/planning-artifacts/ingest-concurrent-processing/ingest-concurrent-processing-spec.md'
  - '_bmad-output/planning-artifacts/research/decision-register.md'
---

# my-AI - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for my-AI Ingest 并发处理 + 批量上传 + 前端体验升级，decomposing the requirements from the PRD, Architecture, and SPEC into implementable stories.

## Requirements Inventory

### Functional Requirements

FR-1.1: `InProcessWorker` 每轮 poll 最多 claim `batchSize` 个文档（默认 3）
FR-1.2: CAS 抢占成功后，通过 `Executors.newVirtualThreadPerTaskExecutor().submit()` 提交到 Virtual Thread 执行，`@Scheduled` 线程立即返回
FR-1.3: `Semaphore(parallelism)` 控制最大并发文档数（默认 3），`tryAcquire` 在 claim 前、`finally release` 在处理完成后
FR-1.4: `@PreDestroy` 优雅关闭：`executor.shutdown()` + `awaitTermination(5, MINUTES)`，保证容器关闭时 in-flight 任务完成
FR-1.5: MDC `documentId` 注入：每个 Virtual Thread 执行前设置 MDC，完成后清除，保证日志链路可追踪
FR-1.6: 配置项 `myai.ingest.worker.parallelism`（默认 3）、`myai.ingest.worker.batch-size`（默认 3）、`myai.ingest.worker.enabled` 默认改为 `true`
FR-1.7: Semaphore 满时跳过剩余 claim，log.debug 输出 "All N processing slots busy"
FR-1.8: 无待处理文档时提前退出循环，log.debug 输出
FR-1.9: Virtual Thread 内捕获未预期异常，log.error 输出 documentId + 异常栈，防止线程静默消失
FR-2.1: `Upload.Dragger` 改为 `multiple={true}`，移除 `maxCount={1}` 限制
FR-2.2: 拖入/选择多个文件后，显示上传队列列表：文件名、文件大小、当前状态（等待中/上传中/已提交/失败）
FR-2.3: 并发控制：前端实现轻量级并发队列，同时进行中的上传数限制为 3，用原生 Promise + 计数器实现（~20 LOC，零外部依赖）
FR-2.4: 上传成功：文件行显示 ✅ + 返回的 documentId（可复制）
FR-2.5: 上传失败：文件行显示 ⚠️ + 错误原因 + documentId（如有），支持单独重试
FR-2.6: 汇总结果区：显示"已提交 N 个文档"或"N 成功 / M 失败"，提供"查看文档目录"链接跳转 `/ingest/documents`
FR-2.7: 总进度条：显示已完成/总数的进度
FR-2.8: documentId 一键复制：点击即复制到剪贴板，运维可用该 ID 在 Kibana 直接定位后端日志
FR-3.1: 状态摘要栏：页面顶部显示全库各状态数量（处理中 N / 完成 N / 失败 N），不受筛选条件影响
FR-3.2: 条件轮询：当前页存在 UPLOADED 或 INGESTING 状态的文档时，`refetchInterval: 3000`；所有文档为终态时，`refetchInterval: false`，停止无意义请求
FR-3.3: INGESTING 状态动画：`DocumentStatusTag` 组件对 INGESTING 状态增加 CSS pulse 动画，视觉上区分"处理中"和"已完成"
FR-3.4: 快速重试：列表行增加"重试"按钮，对 FAILED 状态文档可直接触发 `reprocessDocument()`，无需进入详情页。所有用户可见（受现有 `canContributeKnowledgeBase` 权限约束）
FR-3.5: documentId 展示：失败行的错误信息 Tooltip 中附带 documentId，支持一键复制
FR-3.6: 批量上传后自动跳转：上传页提交完成后，点击"查看文档目录"自动带入 kbId 筛选条件，只显示刚上传的知识库文档

### NonFunctional Requirements

NFR-1: 性能 — 30 个文档并发处理时间 ≤ 10 min（parallelism=3，假设平均 Docling 处理 30s/文档），前端上传队列不阻塞 UI 线程
NFR-2: 可靠性 — Worker 优雅关闭：容器停止时等待 in-flight 任务完成（最多 5 min），CAS 状态机不变，并发处理不引入新竞态条件，上传失败不影响队列中其他文件
NFR-3: 可观测性 — 每个并发处理的文档有 MDC documentId，日志可按文档维度聚合，现有 IngestMetrics 自动覆盖并发场景
NFR-4: 可配置性 — parallelism、batchSize、pollDelayMs 均通过环境变量可配，不同环境可使用不同并发度
NFR-5: 升级路径 — Worker 层改造后，升级到 MQ 多实例只改任务来源，ProcessDocumentUseCase.handle() 和背压逻辑零改动

### Additional Requirements

- AR-1: Virtual Thread Executor + Semaphore 均为 JDK 标准库，零新 Maven 依赖
- AR-2: 前端并发队列用原生 Promise + 计数器实现，零新 npm 依赖
- AR-3: 零新 API 端点 — 前端循环调用现有单文件 `POST /upload` API
- AR-4: 零 Schema 变更 — CAS 状态机 + 现有表结构不变
- AR-5: MDC 跨 Virtual Thread 传播采用手动 MDC.put/clear（不选 InheritableThreadLocal）
- AR-6: 优雅关闭触发点为 `@PreDestroy`，超时后 `shutdownNow()` + log.warn
- AR-7: Semaphore 背压集成：tryAcquire 在 claim 前（避免 INGESTING 但无 worker 的状态不一致窗口）
- AR-8: 前端上传队列状态用 Zod schema 定义，`z.infer<>` 推导类型
- AR-9: 新组件使用命名导出 `export function`，禁止 `export default`
- AR-10: Query Key 遵循 `["resource", { params }]` 格式
- AR-11: 后端（InProcessWorker + IngestProperties + application.yaml）与前端（批量上传 + 状态面板）完全解耦，可独立开发和测试

### UX Design Requirements

不适用 — 本特性无独立 UX 设计文档。UI 交互需求已包含在 FR-2.x（批量上传）和 FR-3.x（状态面板）中。

### FR Coverage Map

| FR | Epic | 说明 |
|----|------|------|
| FR-1.1 | Epic 1 | 每轮 poll 最多 claim batchSize 个文档 |
| FR-1.2 | Epic 1 | CAS 抢占后 Virtual Thread 执行 |
| FR-1.3 | Epic 1 | Semaphore 并发控制 |
| FR-1.4 | Epic 1 | @PreDestroy 优雅关闭 |
| FR-1.5 | Epic 1 | MDC documentId 注入 |
| FR-1.6 | Epic 1 | 配置项扩展 |
| FR-1.7 | Epic 1 | Semaphore 满时跳过 |
| FR-1.8 | Epic 1 | 无文档时提前退出 |
| FR-1.9 | Epic 1 | Virtual Thread 异常捕获 |
| FR-2.1 | Epic 2 | 多文件选择 |
| FR-2.2 | Epic 2 | 上传队列列表 |
| FR-2.3 | Epic 2 | 前端并发控制 |
| FR-2.4 | Epic 2 | 上传成功展示 |
| FR-2.5 | Epic 2 | 上传失败处理 |
| FR-2.6 | Epic 2 | 汇总结果区 |
| FR-2.7 | Epic 2 | 总进度条 |
| FR-2.8 | Epic 2 | documentId 一键复制 |
| FR-3.1 | Epic 3 | 状态摘要栏 |
| FR-3.2 | Epic 3 | 条件轮询 |
| FR-3.3 | Epic 3 | INGESTING 状态动画 |
| FR-3.4 | Epic 3 | 快速重试按钮 |
| FR-3.5 | Epic 3 | 失败行 documentId 展示 |
| FR-3.6 | Epic 3 | 批量上传后自动跳转 |
| NFR-1 | Epic 1 | 性能：30 文档 ≤10min |
| NFR-2 | Epic 1 | 可靠性：优雅关闭 + 错误隔离 |
| NFR-3 | Epic 1 | 可观测性：MDC documentId |
| NFR-4 | Epic 1 | 可配置性：环境变量 |
| NFR-5 | Epic 1 | 升级路径：Poll → MQ |

**覆盖校验：23/23 FR ✅ | 5/5 NFR ✅**

## Epic List

### Epic 1: Worker 并发文档处理
将文档处理从串行改为 Virtual Thread 并发模式，吞吐量提升 3-5 倍，30 个文档从 15-30 分钟缩短至 ≤10 分钟。
**FRs covered:** FR-1.1, FR-1.2, FR-1.3, FR-1.4, FR-1.5, FR-1.6, FR-1.7, FR-1.8, FR-1.9
**NFRs covered:** NFR-1 (性能), NFR-2 (可靠性), NFR-3 (可观测性), NFR-4 (可配置性), NFR-5 (升级路径)
**关键文件:** InProcessWorker.java, IngestProperties.java, application.yaml, InProcessWorkerTest.java
**特点:** 零新依赖、零 Schema 变更、完全独立可测

### Epic 2: 批量文件上传
支持一次拖入/选择多个文件，队列化并发提交，每文件独立状态追踪和可复制的 documentId。
**FRs covered:** FR-2.1, FR-2.2, FR-2.3, FR-2.4, FR-2.5, FR-2.6, FR-2.7, FR-2.8
**关键文件:** IngestUploadPage.tsx, UploadQueue.tsx(新), UploadQueueItem.tsx(新), ingestApi.ts
**特点:** 零新 npm 依赖，与 Epic 1 完全解耦

### Epic 3: 文档状态面板升级
实时状态自动刷新、状态摘要栏、失败文档快速重试、INGESTING 处理动画、documentId 可视化。
**FRs covered:** FR-3.1, FR-3.2, FR-3.3, FR-3.4, FR-3.5, FR-3.6
**关键文件:** IngestListPage.tsx, DocumentStatusTag.tsx, DocumentTableActions.tsx, ingestApi.ts
**特点:** 与 Epic 1、Epic 2 完全解耦，可独立开发

### 依赖关系

```
Epic 1 (后端并发)  ── 三个 Epic 完全独立，可并行开发
Epic 2 (批量上传)  ──
Epic 3 (状态面板)  ──
```

---

## Epic 1: Worker 并发文档处理

将文档处理从串行改为 Virtual Thread 并发模式，吞吐量提升 3-5 倍，30 个文档从 15-30 分钟缩短至 ≤10 分钟。

### Story 1.1: Worker 核心并发改造

As a 系统管理员，
I want 文档处理 Worker 从单线程串行改为 Virtual Thread 并发模式，每轮可同时处理多个文档，
So that 30 个文档的处理时间从 15-30 分钟缩短至 ≤10 分钟，用户无需长时间等待。

**Acceptance Criteria:**

**Given** `InProcessWorker` 的 `@Scheduled` 轮询触发
**When** 存在多个 UPLOADED 状态的文档
**Then** Worker 每轮最多 claim `batchSize`（默认 3）个文档
**And** 每个 claim 成功的文档通过 `Executors.newVirtualThreadPerTaskExecutor().submit()` 提交到 Virtual Thread 执行
**And** `@Scheduled` 线程在 submit 后立即返回，不阻塞

**Given** `Semaphore(parallelism)` 已初始化（默认 3）
**When** Worker 尝试 claim 文档
**Then** 先调用 `semaphore.tryAcquire()`，成功才执行 CAS 抢占
**And** 抢占失败时 `semaphore.release()` 归还槽位
**And** 文档处理完成（无论成功/失败）后在 `finally` 块中 `semaphore.release()`

**Given** Semaphore 已满（所有并发槽位被占用）
**When** Worker 新一轮 poll 触发
**Then** 跳过剩余 claim，`log.debug` 输出 "All N processing slots busy"
**And** 不阻塞 `@Scheduled` 线程

**Given** 数据库中无 UPLOADED 状态的文档
**When** Worker poll 触发
**Then** 提前退出循环，`log.debug` 输出无待处理文档

**Given** `IngestProperties.Worker` 需要扩展
**When** 新增 `parallelism` 和 `batchSize` 字段
**Then** `IngestProperties.Worker` record 新增 `int parallelism`（默认 3）和 `int batchSize`（默认 3）
**And** `application.yaml` 新增配置项 `myai.ingest.worker.parallelism` 和 `myai.ingest.worker.batch-size`
**And** `myai.ingest.worker.enabled` 默认值改为 `true`
**And** 三个配置项均支持环境变量覆盖（`INGEST_WORKER_PARALLELISM`、`INGEST_WORKER_BATCH_SIZE`、`INGEST_WORKER_ENABLED`）

**涉及 FR:** FR-1.1, FR-1.2, FR-1.3, FR-1.6
**涉及文件:** `InProcessWorker.java`, `IngestProperties.java`, `application.yaml`

### Story 1.2: 可观测性与优雅关闭

As a 运维人员，
I want 每个并发处理的文档日志可按 documentId 追踪，且系统关闭时等待 in-flight 任务完成，
So that 并发场景下排障不丢失日志链路，容器重启不丢失正在处理的文档。

**Acceptance Criteria:**

**Given** Virtual Thread 正在执行文档处理
**When** 处理开始
**Then** 在 `executor.submit()` 的 Runnable lambda 中 `MDC.put("documentId", documentId)`
**And** 处理完成（无论成功/失败）后在 `finally` 块中 `MDC.clear()`
**And** 不使用 `InheritableThreadLocal`（VT 行为不可靠）

**Given** Spring 容器正在关闭
**When** `@PreDestroy` 触发
**Then** 调用 `executor.shutdown()` 停止接受新任务
**And** 调用 `executor.awaitTermination(5, MINUTES)` 等待 in-flight 任务完成
**And** 超时后调用 `executor.shutdownNow()` + `log.warn` 记录未完成任务数
**And** 关闭前 `log.info` 记录当前 in-flight 任务数

**Given** Virtual Thread 内发生未预期异常（非业务异常）
**When** 异常被捕获
**Then** `log.error` 输出 documentId + 完整异常栈
**And** 异常不会导致 Virtual Thread 静默消失
**And** Semaphore 在 `finally` 块中正确 release（即使异常发生）

**涉及 FR:** FR-1.4, FR-1.5, FR-1.7, FR-1.8, FR-1.9
**涉及 NFR:** NFR-2 (可靠性), NFR-3 (可观测性)
**涉及文件:** `InProcessWorker.java`
**依赖:** Story 1.1

### Story 1.3: 并发处理单元测试

As a 开发人员，
I want InProcessWorker 的并发、背压、优雅关闭、MDC 场景有完整的单元测试覆盖，
So that 后续改动不会破坏并发安全逻辑，CI 可自动回归验证。

**Acceptance Criteria:**

**Given** `InProcessWorkerTest` 测试类
**When** 测试 Virtual Thread 并发处理
**Then** mock `ClaimNextUseCase` 返回多个文档，验证 `processDocument.handle()` 被调用对应次数
**And** 验证每次调用在独立 Virtual Thread 中执行

**Given** Semaphore 并发限制为 2
**When** 同时有 3 个文档待处理
**Then** 前 2 个文档被 claim 并提交到 Virtual Thread
**And** 第 3 个文档未被 claim（Semaphore 满时跳过）
**And** 验证 `semaphore.tryAcquire()` 调用次数为 2

**Given** 一个文档处理过程中抛出异常
**When** Virtual Thread 内异常被捕获
**Then** Semaphore 被正确 release（通过验证后续 poll 可以正常 claim 新文档）
**And** 其他正在处理的文档不受影响

**Given** `@PreDestroy` 优雅关闭触发
**When** 有 in-flight 任务正在执行
**Then** `executor.shutdown()` 被调用
**And** `awaitTermination(5, MINUTES)` 被调用
**And** 超时后 `shutdownNow()` 被调用

**Given** Virtual Thread 执行文档处理
**When** 处理开始和结束
**Then** 验证 `MDC.put("documentId", ...)` 被调用
**And** 验证 `MDC.clear()` 被调用（finally 块中）

**Given** 无 UPLOADED 状态的文档
**When** Worker poll 触发
**Then** 循环提前退出，`processDocument.handle()` 未被调用

**测试规范:** JUnit 5 + Mockito，纯单元测试，package-private class，方法命名 `method_shouldExpectedBehavior_whenCondition`，`@DisplayName("中文业务描述")`
**涉及 NFR:** NFR-1, NFR-2, NFR-3
**涉及文件:** `InProcessWorkerTest.java`
**依赖:** Story 1.1 + Story 1.2

---

## Epic 2: 批量文件上传

支持一次拖入/选择多个文件，队列化并发提交，每文件独立状态追踪和可复制的 documentId。

### Story 2.1: 多文件选择与上传队列 UI

As a 用户，
I want 在上传页面一次选择或拖入多个文件，看到文件列表和上传进度，
So that 无需重复 10 次"选文件→选知识库→提交"操作，一次搞定所有文件上传。

**Acceptance Criteria:**

**Given** 用户在 `/ingest/upload` 页面
**When** 点击或拖入文件到 `Upload.Dragger` 区域
**Then** 支持同时选择多个文件（`multiple={true}`）
**And** 移除原有的 `maxCount={1}` 限制

**Given** 用户已选择多个文件
**When** 文件列表渲染
**Then** 显示上传队列列表，每行包含：文件名、文件大小（格式化）、当前状态 Tag
**And** 状态 Tag 使用 Ant Design `<Tag>` 组件，颜色映射如下：
    - 等待中 → `ink-mute` (#707070) 灰色
    - 上传中 → `primary` (#3ecf8e) Emerald
    - 已提交 → `semantic-success` (#1f8a65) 深绿
    - 失败 → `semantic-error` (#cf2d56) 红色
**And** 不使用 emoji 图标，纯颜色 + 文案区分状态

**Given** 上传队列中多个文件正在处理
**When** 查看队列区域
**Then** 页面底部显示总进度条
**And** 进度文案格式："已完成 3/10"
**And** 进度 = 已完成（成功 + 失败）/ 总文件数

**Given** 队列状态类型定义
**When** 定义上传队列数据结构
**Then** 使用 Zod schema 定义 `UploadQueueItem` 类型（`z.object({ file, status, documentId, error })`）
**And** 状态字段使用 `z.enum(['pending', 'uploading', 'done', 'error'])`
**And** 用 `z.infer<typeof schema>` 推导 TypeScript 类型
**And** 禁止手写 interface 或使用 `enum` 关键字

**涉及 FR:** FR-2.1, FR-2.2, FR-2.7
**涉及文件:** `IngestUploadPage.tsx`(修改), `UploadQueue.tsx`(新增), `UploadQueueItem.tsx`(新增)
**组件规范:** 命名导出 `export function`，Props 解构在函数签名处

### Story 2.2: 并发上传控制与状态反馈

As a 用户，
I want 多个文件自动排队上传，每个文件独立显示结果（含 documentId），上传失败可单独重试，
So that 上传过程透明可控，失败不会影响其他文件，运维可通过 documentId 快速定位问题。

**Acceptance Criteria:**

**Given** 上传队列中有多个 pending 状态的文件
**When** 用户点击"全部提交"或文件自动开始上传
**Then** 前端并发队列同时进行中的上传数限制为 3
**And** 用原生 Promise + `activeCountRef`（useRef）计数器实现（~20 LOC，零外部依赖）
**And** 完成一个上传后自动启动队列中下一个 pending 文件

**Given** 某个文件上传成功
**When** API 返回成功响应
**Then** 该文件行状态 Tag 更新为"已提交"（`semantic-success` 深绿）
**And** 显示返回的 `documentId`（monospace 字体）
**And** documentId 旁有复制按钮，点击调用 `navigator.clipboard.writeText()` 复制到剪贴板
**And** 复制成功后显示"已复制"反馈

**Given** 某个文件上传失败
**When** API 返回错误响应或网络异常
**Then** 该文件行状态 Tag 更新为"失败"（`semantic-error` 红色）
**And** 显示错误原因文案
**And** 如果响应中包含 `documentId`，同样显示（可复制）
**And** 该文件行显示"重试"按钮，点击重新提交该文件（不影响队列中其他文件）

**Given** 所有文件上传完成（全部为 done 或 error 状态）
**When** 渲染汇总结果区
**Then** 显示"已提交 N 个文档"（全部成功时）或"N 成功 / M 失败"（有失败时）
**And** 提供"查看文档目录"链接跳转 `/ingest/documents`
**And** 如果上传时选择了知识库，跳转链接自动带入 `kbId` 查询参数筛选

**Given** 队列状态用 Zod schema 定义
**When** 更新队列中单个文件的状态
**Then** 通过 `setQueue(prev => prev.map(...))` 更新对应文件行（不可变更新）
**And** `activeCountRef.current` 用于跟踪并发数（闭包陈旧值防护）
**And** 页面卸载时不阻塞用户（warn 但不阻止离开）

**涉及 FR:** FR-2.3, FR-2.4, FR-2.5, FR-2.6, FR-2.8
**涉及文件:** `IngestUploadPage.tsx`(修改), `UploadQueue.tsx`(修改), `UploadQueueItem.tsx`(修改)
**依赖:** Story 2.1

---

## Epic 3: 文档状态面板升级

实时状态自动刷新、状态摘要栏、失败文档快速重试、INGESTING 处理动画、documentId 可视化。

### Story 3.1: 状态自动刷新与摘要栏

As a 用户，
I want 文档列表页自动刷新状态，并在页面顶部看到各状态的数量摘要，
So that 无需手动刷新页面即可感知文档处理进度，一眼了解全局状态分布。

**Acceptance Criteria:**

**Given** `/ingest/documents` 页面已加载
**When** 当前页存在 UPLOADED 或 INGESTING 状态的文档
**Then** `useQuery` 的 `refetchInterval` 设置为 `3000`（3 秒轮询）
**And** `hasActiveDocuments` 使用 `useMemo` 计算（避免每次渲染创建新引用）

**Given** 当前页所有文档均为终态（INDEXED 或 FAILED）
**When** 数据刷新完成
**Then** `refetchInterval` 设置为 `false`，停止无意义的轮询请求
**And** 状态变化时自动重新评估轮询条件

**Given** `/ingest/documents` 页面顶部区域
**When** 页面渲染
**Then** 显示状态摘要栏，展示全库各状态数量：处理中 N / 完成 N / 失败 N
**And** 摘要数据不受筛选条件影响（独立 query，queryKey `["documents", "summary"]`）
**And** 摘要栏数据随条件轮询一起刷新

**Given** 列表中有 INGESTING 状态的文档
**When** `DocumentStatusTag` 组件渲染该状态
**Then** Tag 具有 CSS pulse 动画效果
**And** 视觉上明确区分"处理中"和"已完成"状态
**And** 动画使用 CSS `@keyframes` 实现（不引入 JS 动画库）

**Given** Query Key 定义
**When** 文档列表查询配置
**Then** Query Key 格式为 `["documents", { kbId, status, filename, page, pageSize }]`
**And** 包含所有影响结果的参数

**涉及 FR:** FR-3.1, FR-3.2, FR-3.3
**涉及文件:** `IngestListPage.tsx`(修改), `DocumentStatusTag.tsx`(修改)

### Story 3.2: 快速操作与文档标识

As a 用户/运维人员，
I want 失败文档可直接在列表行重试、documentId 可一键复制、批量上传后自动跳转到对应知识库的文档列表，
So that 运维排障效率提升（documentId 定位日志），操作步骤减少（无需进入详情页重试）。

**Acceptance Criteria:**

**Given** 文档列表中有 FAILED 状态的文档行
**When** 用户查看该行的操作区域
**Then** 显示"重试"按钮
**And** 点击后调用 `POST /documents/{documentId}/reprocess`
**And** 复用现有 `canContributeKnowledgeBase` 权限校验（非 admin 用户只要对 KB 有写权限即可重试）
**And** 重试按钮对所有有权限的用户可见（不限 admin）

**Given** 用户点击"重试"按钮
**When** reprocess 请求成功
**Then** 该文档状态从 FAILED 变为 UPLOADED/INGESTING
**And** 自动刷新列表数据（`invalidateQueries` 精确 key）

**Given** 文档列表中有 FAILED 状态的文档行
**When** 用户 hover 到错误信息区域
**Then** 显示 Tooltip，包含错误详情 + documentId
**And** documentId 使用 monospace 字体
**And** documentId 旁有复制按钮，点击复制到剪贴板
**And** 复制成功后显示"已复制"反馈

**Given** 用户在 `/ingest/upload` 页面完成批量上传
**When** 点击汇总结果区的"查看文档目录"链接
**Then** 跳转到 `/ingest/documents`
**And** URL 自动带入 `kbId` 查询参数（筛选刚上传的知识库文档）
**And** 列表页根据 `kbId` 参数自动筛选对应知识库的文档

**涉及 FR:** FR-3.4, FR-3.5, FR-3.6
**涉及文件:** `IngestListPage.tsx`(修改), `DocumentTableActions.tsx`(修改), `ingestApi.ts`(确认)
**依赖:** Story 3.1
