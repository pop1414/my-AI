# Worker 重试设计笔记

## 1. 背景 / 问题

RAG 文档入库不是一步完成的同步动作，而是一个包含上传、解析、分块、向量化的处理链路。  
这条链路里既可能出现瞬时错误，也可能出现永久错误，所以不能只靠“失败就结束”。

在 `my-AI` 的 V1 里，我需要解决两个问题：

1. 如何让上传后的文档被异步 worker 稳定处理
2. 如何区分“值得重试”和“应该直接失败”的错误

## 2. 这次项目里我是怎么遇到它的

项目一开始只做了上传受理和状态查询，但如果没有真正的处理执行链路，文档永远停在 `UPLOADED`。  
等我补上 worker 后，新的问题是：如果模型服务短暂异常、数据库瞬时抖动、文件读取偶发失败，系统不能因为一次异常就把资产永远打成 `FAILED`。

## 3. 我最后采用了什么方案

当前方案是：

- 上传成功先落库为 `UPLOADED`
- worker 周期轮询可处理文档
- 抢占成功后将状态推进为 `INGESTING`
- 处理成功推进为 `INDEXED`
- 处理失败时由 `RetryPolicy` 判断是否为瞬时错误
- 瞬时错误：
  - 记录错误上下文
  - `retryCount + 1`
  - 计算 `nextRetryAt`
  - 状态回到 `UPLOADED`
- 非瞬时错误或重试次数达到上限：
  - 状态推进为 `FAILED`

## 4. 为什么不用别的方案

### 4.1 为什么不是“失败就直接 FAILED”

因为外部依赖很多：

- 模型 API
- PostgreSQL / PGVector
- 本地文件存储

这些依赖都可能出现短暂波动。直接失败会让系统韧性很差。

### 4.2 为什么不是引入完整消息队列

V1 的目标是先跑通最小闭环。  
单进程 worker 足够验证：

- 状态机是否成立
- 重试策略是否合理
- 处理链路是否可追踪

真正的独立任务队列更适合放在后续版本。

## 5. 这件事面试官可能怎么问

### Q1：为什么要把状态回退成 `UPLOADED`，而不是保持 `INGESTING`？

可以回答：

当前 worker 的调度入口就是“寻找可再次抢占的待处理资产”。  
把状态回到 `UPLOADED`，配合 `nextRetryAt`，可以复用同一套调度逻辑，而不需要额外维护一个复杂的“重试中”状态。

### Q2：为什么要区分瞬时错误和永久错误？

可以回答：

因为系统的外部依赖既有短暂抖动，也有真正的业务错误。  
如果不区分，两种问题都会进入同一条失败路径，要么浪费重试资源，要么丢失恢复机会。

### Q3：为什么需要指数退避和 jitter？

可以回答：

指数退避避免错误发生后立刻重复打满依赖。  
jitter 避免多个任务在同一时刻一起重试，形成新的尖峰。

## 6. 我该怎么回答

一句话总结：

> 我在 V1 里用“单进程 worker + 状态机 + RetryPolicy + 指数退避”的方式，把文档入库从静态接口变成了可追踪、可恢复的异步处理链路。

## 7. 相关代码 / 文档入口

- 正式文档：[07-ingest-processing-execution.md](../../07-ingest-processing-execution.md)
- 架构总览：[03-architecture.md](../../03-architecture.md)
- 处理应用服务：[ProcessDocumentApplicationService.java](../../../src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java)
- Worker：[InProcessWorker.java](../../../src/main/java/io/github/spike/myai/ingest/infrastructure/worker/InProcessWorker.java)
