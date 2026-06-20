# SPEC: Ingest 并发处理 + 批量上传 + 前端体验升级

> 来源: 2026-06-20 spike + Mary(业务分析师) + Winston(架构师) 联合讨论
> 决策依据: decision-register.md D1, D22, spike 的三个前端建议
> 状态: 待实现

---

## 1. 背景与动机

### 1.1 当前瓶颈

`InProcessWorker` 是 `@Scheduled` 单线程轮询，每 5 秒只处理 1 个文档，同步执行整个 pipeline（Docling parse → chunk → DashScope embed → PGVector write）。大 PDF OCR 耗时 30-60 秒，期间 Worker 完全阻塞。**30 个文档排队 = 15-30 分钟串行处理。**

上传端只支持单文件，前端无批量操作和实时状态反馈。

### 1.2 目标

- 后端并发处理：单实例下文档处理吞吐量提升 3-5 倍
- 前端批量上传：支持一次选择多个文件提交
- 前端状态面板：实时反馈处理进度，运维友好

---

## 2. 后端：D1 Worker 并发处理

### 2.1 方案：Poll-and-Submit 模式

**架构**：`@Scheduled` 轮询保持作为任务发现机制，CAS 抢占后立即通过 `executor.submit()` 提交到 Virtual Thread 执行，`@Scheduled` 线程立即返回。

```
@Scheduled pollAndClaim()
    ├── semaphore.tryAcquire()          ← 背压：最多 N 个并发
    ├── claimNext.handle()              ← CAS 抢占一条文档（UPLOADED → INGESTING）
    └── executor.submit(() ->           ← 创建 Virtual Thread
            processDocument.handle()    ← Docling parse + chunk + embed
        );                              ← @Scheduled 线程立即返回
```

### 2.2 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 线程模型 | `Executors.newVirtualThreadPerTaskExecutor()` | JDK 21 推荐，I/O 阻塞时自动 unmount 不占平台线程 |
| 并发控制 | `Semaphore(parallelism)` | JEP 444 推荐 "use semaphores to limit concurrency, not pool size" |
| 超时 | **不在 Worker 层处理** | 由 Docling `read-timeout: 30s` + `RetryPolicy`（瞬时→指数退避）覆盖 |
| 优雅关闭 | `@PreDestroy` + `executor.shutdown()` + `awaitTermination(5min)` | 保证容器关闭时 in-flight 任务完成 |
| MDC | `MDC.put("documentId", ...)` | 日志链路可追踪 |

### 2.3 排除的方案

| 方案 | 排除理由 |
|------|---------|
| D1 原方案（裸 `Thread.ofVirtual().start()`） | 无线程池生命周期管理，容器关闭时无法优雅等待 |
| Worker-Supervisor（常驻 worker + BlockingQueue） | claim 和 enqueue 两步有状态不一致窗口（INGESTING 但无 worker 处理）；Virtual Thread 创建成本接近零，"常驻"无实际收益 |
| @Async + 配置类 | 多一个配置文件，且 @Async 不支持 Semaphore 背压控制 |

### 2.4 配置项

```yaml
myai:
  ingest:
    worker:
      enabled: ${INGEST_WORKER_ENABLED:true}      # 默认改为 true
      poll-delay-ms: ${INGEST_WORKER_POLL_DELAY_MS:5000}
      parallelism: ${INGEST_WORKER_PARALLELISM:3}  # 新增：最大并发文档数
      batch-size: ${INGEST_WORKER_BATCH_SIZE:3}    # 新增：每轮 claim 上限
```

### 2.5 改动清单

| 文件 | 改动类型 | 内容 | 预估 |
|------|---------|------|------|
| `InProcessWorker.java` | 重写 | Virtual Thread Executor + Semaphore + batch claim + graceful shutdown + MDC | ~60 LOC |
| `IngestProperties.Worker` | 扩展 | +`parallelism` +`batchSize` 字段 | ~10 LOC |
| `application.yaml` | 更新 | +新配置项 + worker.enabled 默认值改 true | ~5 LOC |

**零新依赖，零新配置类。**

### 2.6 升级路径

当前方案 → MQ 多实例时只改任务来源（poll → MQ consume），`ProcessDocumentUseCase.handle()` 和背压逻辑零改动。CAS 抢占天然支持多实例竞争。

---

## 3. 后端：批量上传 API

### 3.1 方案

新增 `POST /api/v1/documents/upload/batch`，接受 `List<MultipartFile>`，循环调用现有 `AcceptUploadUseCase.handle()`，返回 `List<UploadTicket>`。

**MVP 阶段前端不依赖此接口** — 前端可循环调用现有单文件 `POST /upload` API。Batch 端点作为优化后做。

---

## 4. 前端：批量上传（方案一）

### 4.1 目标页面

`/ingest/upload` — 改造 `IngestUploadPage.tsx`

### 4.2 核心交互

1. `Upload.Dragger` 改为 `multiple={true}`，移除 `maxCount={1}` 限制
2. 拖入/选择多个文件 → 加入上传队列列表（显示文件名、大小、等待状态）
3. 点击"全部提交" → **并发队列逐个调用** `uploadDocument()` API
4. 每个文件独立显示状态：等待中 → 上传中 → 已提交/失败
5. 汇总结果 + "查看文档目录"链接跳转 `/ingest/documents`

### 4.3 并发控制（spike 建议 1）

**前端实现轻量级并发队列，并发度限制为 3-4。**

- 不引入 p-limit 等外部依赖，用原生 Promise + 计数器实现（~20 LOC）
- 界面显示"等待中"，但实际网络传输中的只有 3 个文件
- 完成一个，再启动下一个，进度条走得平稳，服务器内存消耗安全

### 4.4 documentId 展示（spike 建议 3）

上传成功后显示可复制的 documentId。上传失败时在错误信息旁附带 documentId（支持一键复制），运维可用该 ID 在 Kibana 直接定位后端日志。

### 4.5 改动清单

| 文件 | 改动 |
|------|------|
| `IngestUploadPage.tsx` | `multiple={true}` + 队列管理 + 并发队列提交 + 结果展示 |
| `ingestApi.ts` | 新增 `batchUploadDocuments()` 或复用循环逻辑 |
| 新增: `UploadQueue.tsx` | 上传队列组件（文件列表 + 进度 + 操作按钮） |
| 新增: `UploadQueueItem.tsx` | 上传队列中的单文件行组件 |

---

## 5. 前端：文档状态面板升级（方案二）

### 5.1 目标页面

`/ingest/documents` — 改造 `IngestListPage.tsx`

### 5.2 核心升级

| 功能 | 现状 | 改造后 |
|------|------|--------|
| 状态自动刷新 | 无，需手动刷新页面 | 条件轮询（见 5.3） |
| 状态摘要栏 | 无 | 页面顶部显示各状态数量：处理中 N / 完成 N / 失败 N |
| 失败重试 | 需进入详情页点重处理 | 列表行直接显示"重试"按钮 |
| 处理中动画 | 静态 Tag | INGESTING 状态有 CSS pulse 动画 |
| documentId 展示 | 无 | 失败行 Tooltip 显示 documentId，支持一键复制 |

### 5.3 条件轮询（spike 建议 2）

```typescript
// 当前页存在非终态文档 → 轮询；否则停止
const hasActiveDocuments = data?.items?.some(
  item => item.status === 'UPLOADED' || item.status === 'INGESTING'
);

useQuery({
  queryKey: ["documents", filters, page, pageSize],
  queryFn: () => listDocuments({ ... }),
  refetchInterval: hasActiveDocuments ? 3000 : false,
});
```

当没有后台任务时，前端不发送无意义的轮询请求。

### 5.4 改动清单

| 文件 | 改动 |
|------|------|
| `IngestListPage.tsx` | 状态摘要栏 + 条件轮询 + 快速重试按钮 |
| `DocumentStatusTag.tsx` | INGESTING 状态加 CSS loading 动画 |
| `DocumentTableActions.tsx` | 增加快速重试按钮（非 admin 可用） |
| `ingestApi.ts` | 确认 error response 结构，确保 documentId 可获取 |

---

## 6. 实现顺序

```
1. 后端 D1 Worker 并发（InProcessWorker + IngestProperties + application.yaml）
2. 前端批量上传（IngestUploadPage + UploadQueue 组件）
3. 前端状态面板升级（IngestListPage + 条件轮询 + 摘要栏）
4. [可选] 后端 batch 端点（POST /upload/batch）
```

每个步骤独立提交，遵循 `type(scope): message` 格式。

---

## 7. 约束与不变项

- **不变**：`ProcessDocumentApplicationService.handle()` 内部逻辑不动
- **不变**：CAS 状态机（UPLOADED → INGESTING → INDEXED/FAILED）不动
- **不变**：`RetryPolicy` 瞬时/永久错误分类 + 指数退避逻辑不动
- **不变**：前端路由结构不动（只改现有页面组件）
- **约束**：零新后端依赖（Virtual Thread 是 JDK 21 自带）
- **约束**：前端并发上传队列不引入外部依赖（原生 Promise 实现）
