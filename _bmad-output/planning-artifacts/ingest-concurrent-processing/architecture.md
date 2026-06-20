---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
inputDocuments:
  - '_bmad-output/planning-artifacts/ingest-concurrent-processing/prd.md'
  - '_bmad-output/planning-artifacts/ingest-concurrent-processing/ingest-concurrent-processing-spec.md'
  - '_bmad-output/planning-artifacts/research/decision-register.md'
  - 'docs/project-context.md'
  - 'docs/architecture/overview.md'
  - 'docs/architecture/domain/ingest.md'
  - 'docs/api/contracts.md'
  - 'docs/data/models.md'
  - 'docs/adr/ADR-0004-v1-ingest-processing-strategy.md'
workflowType: 'architecture'
project_name: 'my-AI'
user_name: 'spike'
date: '2026-06-20'
lastStep: 8
status: 'complete'
completedAt: '2026-06-20'
---

# Architecture Decision Document: Ingest 并发处理 + 批量上传 + 前端体验升级

_本文档通过逐步协作发现的方式构建。每个架构决策完成后追加对应章节。_

## 项目上下文分析

### 需求概览

**功能需求（3 个功能域，17 个 FR）：**

| 功能域 | FR 数量 | 核心内容 | 架构影响 |
|--------|---------|----------|----------|
| F1: Worker 并发 | FR-1.1~1.9 | Poll-and-Submit 模式，Virtual Thread + Semaphore | 改动核心：`InProcessWorker` 重写，~60 LOC |
| F2: 批量上传 | FR-2.1~2.8 | 多文件选择 + 前端并发队列 + documentId 展示 | 前端改造，零后端变更 |
| F3: 状态面板 | FR-3.1~3.6 | 条件轮询 + 状态摘要 + 快速重试 + 动画 | 前端改造，零后端变更 |

**非功能需求：**

| NFR | 要求 | 架构影响 |
|-----|------|----------|
| 性能 | 30 文档 ≤ 10min（parallelism=3） | Semaphore 并发上限控制 |
| 可靠性 | 优雅关闭 5min，CAS 状态机不变 | `@PreDestroy` + `awaitTermination` |
| 可观测性 | MDC documentId 贯穿 Virtual Thread | MDC put/clear 在 VT 边界 |
| 可配置性 | parallelism/batchSize/pollDelayMs 环境变量 | `IngestProperties.Worker` 扩展 |
| 升级路径 | MQ 迁移只改任务来源，handle() 零改动 | Poll-and-Submit 天然解耦 |

### 规模与复杂度

- **复杂度等级**：中低 — 改动集中在 1 个类重写 + 前端页面改造，零新依赖，零 Schema 变更
- **主要技术域**：后端并发（Virtual Threads）+ 前端交互（上传队列 + 轮询）
- **跨切面关注点**：
  - **日志链路追踪**：MDC 在 Virtual Thread 边界的生命周期管理
  - **优雅关闭**：Spring 容器停止时 in-flight 任务的完整排空
  - **背压控制**：Semaphore 防止 PDF/OCR 并发解析炸内存
  - **升级路径**：Poll → MQ consume 的抽象边界保持

### 约束与依赖

| 约束 | 来源 | 影响 |
|------|------|------|
| 零新后端依赖 | PRD Out of Scope | Virtual Thread 是 JDK 21 自带 |
| 零前端 npm 依赖 | PRD Out of Scope | 并发队列用原生 Promise 实现 |
| 零 API 变更 | SPEC §3 | 前端循环调用现有单文件 API |
| 零 Schema 变更 | PRD/Decision Register | CAS 状态机 + 现有表结构不变 |
| HikariCP 默认连接池（10） | 风险评估 | 3 并发 < 连接池上限，已确认足够 |
| Docling read-timeout=30s + RetryPolicy | 风险缓解 | Worker 层不处理超时 |

### 架构决策点

1. **D1 落地**：InProcessWorker 的 Poll-and-Submit 并发模型（Virtual Thread Executor + Semaphore + MDC + Graceful Shutdown）
2. **前端批量上传架构**：上传队列组件设计 + 并发控制策略（原生 Promise 计数器）
3. **前端条件轮询架构**：refetchInterval 动态控制逻辑 + 状态摘要栏数据源
4. **不变项确认**：CAS 状态机、ProcessDocumentUseCase.handle()、RetryPolicy 严格不动

## Starter 模板评估

### 结论：不适用

my-AI 是已建立的 brownfield 项目，技术栈已锁定且稳定运行。本次改动是纯增量改造，不需要引入新脚手架。

### 已确立的技术基础

| 层面 | 当前选型 |
|------|---------|
| 后端运行时 | Java 21 + Spring Boot 3.5.8 |
| 前端运行时 | React 19 + TypeScript 6 + Vite 8 |
| UI 框架 | Ant Design 6 + TanStack Query |
| 数据库 | PostgreSQL 16 + PGVector |
| 文档解析 | Docling Serve（Docker） |
| AI 模型 | DashScope qwen-plus + text-embedding-v4 |
| 认证 | Session Cookie + CSRF Header |
| 架构模式 | DDD-Lite 六边形架构 |

### 本次改动的 Starter 约束

- **后端**：仅使用 JDK 21 自带 `Executors.newVirtualThreadPerTaskExecutor()` + `java.util.concurrent.Semaphore`
- **前端**：仅使用 React + Ant Design 已有能力
- **零新依赖**：PRD 明确排除了所有新 Maven/npm 依赖

## 核心架构决策

### 已决定项（不重新决策）

| 决策 | 选型 | 来源 |
|------|------|------|
| 数据库 | PostgreSQL 16 + PGVector，零 Schema 变更 | 既有 |
| 认证 | Session Cookie + CSRF，不变 | 既有 |
| 架构模式 | DDD-Lite 六边形，不变 | 既有 |
| API 策略 | REST，零新端点，前端循环调用现有 API | PRD |
| 异步模型 | Poll-and-Submit（D1 已决定） | Decision Register |
| CAS 状态机 | UPLOADED→INGESTING→INDEXED/FAILED，不变 | SPEC |
| ProcessDocumentUseCase.handle() | 内部 5 步 pipeline 不动 | PRD Out of Scope |
| RetryPolicy | 瞬时/永久错误分类 + 指数退避，不动 | PRD Out of Scope |

### Critical 决策

#### C1: InProcessWorker 并发模型落地

D1 Poll-and-Submit 方案落地细节：

| 维度 | 决定 | 理由 |
|------|------|------|
| 线程模型 | `Executors.newVirtualThreadPerTaskExecutor()` | JDK 21 推荐，I/O 阻塞时自动 unmount |
| 并发控制 | `Semaphore(parallelism)` + `tryAcquire`/`release` | JEP 444: "use semaphores to limit concurrency" |
| 批量发现 | 每轮 claim `batchSize` 条，for 循环 submit | CAS 抢占天然支持，无需额外锁 |
| 背压语义 | Semaphore 满时跳过剩余 claim，log.debug | 不阻塞 @Scheduled 线程 |
| 超时 | 不在 Worker 层处理 | Docling read-timeout=30s + RetryPolicy 覆盖 |

#### C2: MDC 跨 Virtual Thread 传播

**决定：手动 MDC.put/clear 在 Runnable 包装层**（~5 LOC）

- `executor.submit()` 的 Runnable lambda 中：进入时 `MDC.put("documentId", ...)`，finally 块中 `MDC.clear()`
- 不选 InheritableThreadLocal — VT 的 InheritableThreadLocal 行为与平台线程不同，不可靠
- 不选 TaskDecorator — Virtual Thread Executor 不走 Spring 抽象

#### C3: 优雅关闭策略

| 维度 | 决定 | 理由 |
|------|------|------|
| 触发点 | `@PreDestroy` | Spring 容器生命周期标准钩子 |
| 关闭序列 | `executor.shutdown()` → `awaitTermination(5, MINUTES)` | PRD 要求最多等待 5min |
| 超时行为 | 超时后 `executor.shutdownNow()` + log.warn | 防止无限等待 |
| MDC 清理 | 关闭前 log.info 记录 in-flight 任务数 | 运维可观测 |

### Important 决策

#### I1: 前端批量上传 — 并发队列

**决定：原生 Promise + 计数器**（~20 LOC，零依赖）

- `pending → running → done` 语义清晰
- 精确控制并发度（默认 3），不受 Ant Design 版本升级影响
- 不选 p-limit — PRD 明确排除新依赖

#### I2: 前端条件轮询策略

| 维度 | 决定 |
|------|------|
| 轮询条件 | 当前页存在 UPLOADED 或 INGESTING 状态文档时轮询 |
| 轮询间隔 | `refetchInterval: 3000`（3 秒） |
| 停止条件 | 所有文档为终态时 `refetchInterval: false` |
| 状态摘要栏数据源 | 独立 query，queryKey `["documents", "summary"]` |
| Query Key 精度 | `["documents", { kbId, status, filename, page, pageSize }]` |

#### I3: 上传失败与重试交互

| 维度 | 决定 |
|------|------|
| 上传失败 | 文件行显示 ⚠️ + 错误原因，支持单独重试 |
| 处理失败 | 列表行"重试"按钮，调用 `POST /documents/{documentId}/reprocess` |
| 重试权限 | 复用 `canContributeKnowledgeBase` 校验，非 admin 可用 |
| documentId 展示 | 成功/失败均显示可复制的 documentId |

### Deferred 决策（明确延后）

| 决策 | 延后理由 |
|------|---------|
| `POST /upload/batch` 批量端点 | MVP 前端循环调用单文件 API 已足够 |
| WebSocket/SSE 实时推送 | 3s 轮询已满足需求 |
| 消息队列（MQ）升级 | 单实例不需要，CAS 天然支持未来多实例 |
| Worker 层超时控制 | Docling read-timeout + RetryPolicy 已覆盖 |

### 实现顺序（依赖链）

```
1. InProcessWorker 重写（C1+C2+C3）  ← 后端核心，独立可测
2. IngestProperties.Worker 扩展       ← 配置，与 #1 同步
3. application.yaml 更新              ← 配置默认值
4. 前端批量上传（I1+I3）              ← 前端独立，不依赖后端改动
5. 前端状态面板升级（I2+I3）          ← 前端独立，可与 #4 并行
```

后端（#1-3）与前端（#4-5）完全解耦，可独立开发和测试。

## 实现模式与一致性规则

> 通用模式（命名、结构、格式、流程）继承自 `project-context.md`（162 条规则），此处仅定义本次改动中 AI 代理可能产生分歧的特定模式。

### P1: Virtual Thread Executor 生命周期

**冲突风险**：Executor 放置位置、shutdown 策略。

```
✅ 正确：
  InProcessWorker 类级 private final 字段
  @PreDestroy: shutdown() → awaitTermination(5, MINUTES) → shutdownNow()

❌ 反模式：
  - 方法内每次 new Executor → 资源泄漏
  - Thread.ofVirtual().start() → 无法优雅关闭
  - FixedThreadPool(N) → JEP 444 明确反对对 Virtual Thread 用固定池
```

### P2: MDC + Virtual Thread 边界

**冲突风险**：MDC 默认不跨 VT 传播，忘记清理导致泄漏。

```
✅ 正确：
  executor.submit(() -> {
      MDC.put("documentId", id);
      try { processDocument.handle(doc); }
      finally { MDC.clear(); semaphore.release(); }
  });

❌ 反模式：
  - pollAndClaim() 中 MDC.put 但不在 VT 内 clear
  - InheritableThreadLocal 替代 MDC → VT 行为不一致
  - 忘记 finally release → 并发槽位永久耗尽
```

### P3: Semaphore 背压集成

**冲突风险**：tryAcquire 位置影响状态一致性。

```
✅ 正确（claim 前 tryAcquire）：
  for (batch) {
      if (!semaphore.tryAcquire()) { break; }  // 背压
      var doc = claimNext();                    // CAS
      if (doc.isEmpty()) { semaphore.release(); break; }
      executor.submit(...);                     // release in finally
  }

❌ 反模式：
  - claim 后再 tryAcquire → INGESTING 但无 worker（状态不一致窗口）
  - synchronized 替代 Semaphore → 不符合 VT 设计哲学
```

### P4: 前端上传队列状态管理

**冲突风险**：useState vs useRef，Zod schema vs 手写 interface。

```
✅ 正确：
  const [queue, setQueue] = useState<UploadQueueItem[]>([]);
  const activeCountRef = useRef(0);  // 闭包陈旧值防护
  // Zod schema 定义，z.infer 推导类型
  const UploadQueueItemSchema = z.object({
      file: z.instanceof(File),
      status: z.enum(['pending', 'uploading', 'done', 'error']),
      documentId: z.string().optional(),
      error: z.string().optional(),
  });

❌ 反模式：
  - 全 useRef → 状态变化不触发重渲染
  - 手写 interface → 违反 Zod-first
  - enum 关键字 → 违反"禁止 enum"
```

### P5: 前端条件轮询

**冲突风险**：refetchInterval 动态计算导致无限重渲染。

```
✅ 正确：
  const hasActiveDocuments = useMemo(
      () => data?.items?.some(i => i.status === 'UPLOADED' || i.status === 'INGESTING') ?? false,
      [data]
  );
  useQuery({
      queryKey: ["documents", { kbId, status, filename, page, pageSize }],
      refetchInterval: hasActiveDocuments ? 3000 : false,
  });

❌ 反模式：
  - useQuery 内联计算 → 每次渲染新引用
  - 不用 useMemo → 每次渲染创建新布尔值
  - refetchInterval 用 0 代替 false → 类型不语义化
```

### P6: 新组件文件组织

```
✅ 正确（feature 内聚）：
  web/src/features/ingest/
  ├── components/
  │   ├── UploadQueue.tsx             # 新增：命名导出
  │   ├── UploadQueueItem.tsx         # 新增：命名导出
  │   ├── DocumentStatusTag.tsx       # 已有，加动画
  │   └── DocumentTableActions.tsx    # 已有，加快速重试

❌ 反模式：
  - 放 shared/components/ → 不是跨功能共享
  - export default → 违反规则
  - camelCase 组件文件名 → 返回 JSX 必须 PascalCase
```

### 强制规则

1. Executor 和 Semaphore 声明为 `private final` 字段
2. VT Runnable 内 try/finally 包裹 MDC.put + MDC.clear + semaphore.release
3. 前端队列状态用 Zod schema 定义，`z.infer<>` 推导类型
4. 新组件使用命名导出 `export function`，禁止 `export default`
5. Query Key 遵循 `["resource", { params }]` 格式
6. 测试方法 `method_shouldExpectedBehavior_whenCondition`，`@DisplayName` 含业务关键词

## 项目结构与边界

### 后端变更清单

| 文件 | 类型 | 改动 |
|------|------|------|
| `ingest/infrastructure/worker/InProcessWorker.java` | 修改 | 重写：Virtual Thread + Semaphore + batch claim + MDC + graceful shutdown |
| `ingest/infrastructure/config/IngestProperties.java` | 修改 | Worker 内部 record 扩展 `parallelism` + `batchSize` 字段 |
| `src/main/resources/application.yaml` | 修改 | 新增配置项 + `worker.enabled` 默认改 `true` |
| `ingest/infrastructure/worker/InProcessWorkerTest.java` | 修改 | 重写：覆盖并发、背压、优雅关闭、MDC 场景 |

**不动的文件**：ProcessDocumentApplicationService.handle()、DocumentRepository/CAS 状态机、RetryPolicy、DoclingDocumentParser、DoclingDocumentChunker

### 前端变更清单

| 文件 | 类型 | 改动 |
|------|------|------|
| `features/ingest/components/UploadQueue.tsx` | **新增** | 上传队列容器（文件列表 + 进度 + 操作按钮） |
| `features/ingest/components/UploadQueueItem.tsx` | **新增** | 队列中单文件行（状态 + documentId + 重试） |
| `features/ingest/pages/IngestUploadPage.tsx` | 修改 | `multiple={true}` + 队列管理 + 并发提交 |
| `features/ingest/pages/IngestListPage.tsx` | 修改 | 条件轮询 + 状态摘要栏 + 快速重试按钮 |
| `features/ingest/components/DocumentStatusTag.tsx` | 修改 | INGESTING 状态加 CSS pulse 动画 |
| `features/ingest/components/DocumentTableActions.tsx` | 修改 | 增加快速重试按钮 |
| `shared/api/ingestApi.ts` | 修改 | 确认 error response 结构含 documentId |

### 架构边界

#### API 边界（零新端点）

- `POST /api/v1/documents/upload` — 前端循环调用实现批量上传
- `GET /api/v1/documents` — 轮询数据源
- `POST /api/v1/documents/{id}/reprocess` — 快速重试触发点

#### 前端组件数据流

```
IngestUploadPage
  └── UploadQueue (state: queue[])
      └── UploadQueueItem (props: item, onRetry)
          └── uploadDocument() → 更新 queue state

IngestListPage
  └── useQuery + refetchInterval (3s 条件轮询)
  └── DocumentStatusTag (props: status) → CSS 动画
  └── DocumentTableActions (props: documentId, status) → 重试按钮
      └── reprocessDocument()
```

#### 后端并发边界

```
@Scheduled pollAndClaim() (单线程轮询)
  ├── semaphore.tryAcquire()     ← 背压门控
  ├── claimNext()                ← CAS UPLOADED → INGESTING
  └── executor.submit()          ← Virtual Thread
        └── processDocument.handle()
              ├── Docling parse   ← I/O 阻塞（VT 自动 unmount）
              ├── chunk           ← CPU（轻量）
              ├── DashScope embed ← I/O 阻塞（API 调用）
              └── PGVector write  ← I/O 阻塞（DB 写入）
```

### 需求到结构映射

| 需求 | 改动文件 |
|------|---------|
| F1 Worker 并发（FR-1.1~1.9） | InProcessWorker.java, IngestProperties.java, application.yaml |
| F2 批量上传（FR-2.1~2.8） | IngestUploadPage.tsx, UploadQueue.tsx, UploadQueueItem.tsx |
| F3 状态面板（FR-3.1~3.6） | IngestListPage.tsx, DocumentStatusTag.tsx, DocumentTableActions.tsx |
| NFR-3 可观测性（MDC） | InProcessWorker.java |
| NFR-4 可配置性 | IngestProperties.java, application.yaml |
| NFR-2 可靠性（优雅关闭） | InProcessWorker.java |
| 测试覆盖 | InProcessWorkerTest.java |

## 架构验证结果

### 一致性验证 ✅

| 维度 | 结果 |
|------|------|
| 决策兼容性 | ✅ Virtual Thread + Semaphore 均为 JDK 标准库，零冲突 |
| 模式一致性 | ✅ 6 个模式（P1-P6）与技术栈和架构决策对齐 |
| 结构对齐 | ✅ 11 个变更文件映射到 3 个功能域，边界清晰 |
| 矛盾检查 | ✅ 无矛盾 — CAS 状态机天然支持并发，背压在 claim 前保证一致性 |

### 需求覆盖验证 ✅

**功能需求（17/17 FR）**：F1 Worker 并发 9/9 · F2 批量上传 8/8 · F3 状态面板 6/6

**非功能需求（5/5 NFR）**：性能 · 可靠性 · 可观测性 · 可配置性 · 升级路径

### 差距分析

**Critical**：无

**Important**：

| # | 差距 | 建议 |
|---|------|------|
| 1 | InProcessWorker 并发测试策略未详述 | story 中补充：mock ClaimNextUseCase 返回顺序 + 验证 semaphore 释放 |
| 2 | 前端上传中途离开页面处理未定义 | 建议：页面卸载时 warn 但不阻塞 |

**Nice-to-have**：状态摘要栏 API 优化（独立 summary 端点）· INGESTING CSS pulse 动画实现细节

### 架构完整性清单

**需求分析**
- [x] 项目上下文深入分析
- [x] 规模与复杂度评估
- [x] 技术约束识别
- [x] 跨切面关注点映射

**架构决策**
- [x] 关键决策带版本记录
- [x] 技术栈完全指定
- [x] 集成模式定义
- [x] 性能考量处理

**实现模式**
- [x] 命名约定建立
- [x] 结构模式定义
- [x] 通信模式指定
- [x] 流程模式记录

**项目结构**
- [x] 完整目录结构定义
- [x] 组件边界建立
- [x] 集成点映射
- [x] 需求到结构映射完成

### 架构就绪评估

**总体状态**：READY FOR IMPLEMENTATION ✅

**置信度**：高 — 16/16 清单项通过，零 Critical 差距

**关键优势**：
- 零新依赖，改动范围极小（~60 LOC 后端 + ~200 LOC 前端）
- D1 Poll-and-Submit 方案经过 Decision Register 充分论证
- CAS 状态机天然支持并发安全
- 后端与前端完全解耦，可独立开发和测试
- 升级路径清晰（Poll → MQ 只改任务来源）

**未来增强**：D15 可观测性接入 · WebSocket/SSE 替代轮询 · `POST /upload/batch` 批量端点

**第一实现优先级**：`InProcessWorker.java` 重写（C1+C2+C3）
