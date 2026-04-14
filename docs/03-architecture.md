# 架构说明（Architecture）

## 1. 架构图
- 主架构图：`docs/architecture/diagrams/core-architecture-latest.puml`
- 渲染图：`docs/architecture/diagrams/core-architecture-latest-_____Latest___Clean_Layout_.png`
- Ingest 总览图：`docs/architecture/diagrams/ingest/ingest-overview-map.puml`
- 受理责任域边界图：`docs/architecture/diagrams/ingest/acceptance/ingest-acceptance-boundary.puml`
- 受理上传时序图：`docs/architecture/diagrams/ingest/acceptance/ingest-acceptance-upload-sequence.puml`
- 受理状态查询时序图：`docs/architecture/diagrams/ingest/acceptance/ingest-acceptance-status-sequence.puml`
- Ingest 共享状态机：`docs/architecture/diagrams/ingest/shared/ingest-shared-state-machine.puml`
- Ingest 共享 ER/领域模型图：`docs/architecture/diagrams/ingest/shared/ingest-shared-er-domain.puml`
- 受理闭环说明文档：`docs/06-ingest-acceptance-closure.md`
- 执行责任域边界图：`docs/architecture/diagrams/ingest/execution/ingest-execution-boundary.puml`
- 执行 worker 时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-worker-process-sequence.puml`
- 执行分块预览时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-chunks-preview-sequence.puml`
- 执行重处理时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-reprocess-sequence.puml`
- 执行删除时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-delete-sequence.puml`
- 处理执行说明文档：`docs/07-ingest-processing-execution.md`

## 2. 分层设计
- 接入层：Upload/Knowledge/QA/SSE API
- 应用服务层：IngestService / KnowledgeService / RagService
- Spring AI 抽象层：DocumentReader & Splitter / EmbeddingModel / VectorStore / ChatClient / ChatModel
- 基础设施适配层：LLM / Embedding / Vector / Repository / ObjectStorage Adapter
- 数据层：MySQL / Vector DB / MinIO(S3)
- 横切治理层：Tenant/Auth/RateLimit/Observability/Audit

## 2.1 当前已实现子集（截至 2026-04-07）
- 已实现：`ingest` 子域（上传受理、状态查询、分块预览、异步处理执行）
- 已实现 API：`/api/v1/documents/upload`、`/api/v1/documents/{documentId}/status`、`/api/v1/documents/{documentId}/chunks/preview`
- 规划中（未实现）：`/api/v1/knowledge-bases`、`/api/v1/qa/ask`、`/api/v1/documents/{documentId}/reprocess`
- 说明：本文件第 2 章是目标架构蓝图，不等于当前全部实现

## 3. 核心链路
### 文档入库链路
上传 -> 入库队列 -> 解析分块 -> 向量化 -> 写入向量库 -> 写元数据 -> 原文存储

### 问答链路
提问 -> 检索 TopK -> 构建 Prompt -> 调用 ChatModel -> 返回回答（支持流式）

## 4. 依赖约束
- Controller 不直接访问数据库
- 应用层只依赖抽象接口，不依赖具体 SDK
- Provider 切换通过 Adapter 实现，不改业务逻辑

## 5. 扩展点（为 V2/V3 预留）
- LLM Provider 切换
- 向量库切换
- TenantContext 贯穿请求链路
- 会话记忆策略（短期/长期）

## 6. 非功能要求（初版）
- 可观测：请求日志、耗时、错误码
- 弹性：超时、重试、熔断
- 安全：最小权限、敏感信息脱敏

## 7. 受理闭环设计补充
- 上传受理接口返回 `ACCEPTED`（表示请求已被受理）
- 内部状态落库为 `UPLOADED`（表示任务已可追踪）
- 通过 `GET /api/v1/documents/{id}/status` 查询任务状态
- 该阶段重点是“可追踪闭环”，非完整入库处理链路

## 8. 处理执行设计补充（已采纳，部分能力待实现）
- 处理模式：异步 worker（单进程）
- 状态推进：`UPLOADED -> INGESTING -> INDEXED/FAILED`
- 分块参数初值：`chunk=500`, `overlap=100`
- 失败策略：瞬时错误最多 3 次重试（待实现）
- 幂等目标：同一 `documentId` 重复处理最终一致
