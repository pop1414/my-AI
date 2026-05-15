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
- Ingest 技术指导参考：`docs/reference/ingest-文档处理技术指导.md`（参考资料，非图谱资产）
- 受理闭环说明文档：`docs/06-ingest-acceptance-closure.md`
- 执行责任域边界图：`docs/architecture/diagrams/ingest/execution/ingest-execution-boundary.puml`
- 执行 worker 时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-worker-process-sequence.puml`
- 执行分块预览时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-chunks-preview-sequence.puml`
- 执行重处理时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-reprocess-sequence.puml`
- 执行删除时序图：`docs/architecture/diagrams/ingest/execution/ingest-execution-delete-sequence.puml`
- 处理执行说明文档：`docs/07-ingest-processing-execution.md`
- Knowledge 责任域边界图：`docs/architecture/diagrams/knowledge/knowledge-boundary.puml`
- Knowledge 列表时序图：`docs/architecture/diagrams/knowledge/knowledge-list-sequence.puml`
- QA 责任域边界图：`docs/architecture/diagrams/qa/qa-boundary.puml`
- QA 问答时序图：`docs/architecture/diagrams/qa/qa-ask-sequence.puml`
- 子域关系图（实现视角）：`docs/architecture/diagrams/ingest-knowledge-qa-relations.puml`

## 2. 分层设计
- 接入层：Upload/Knowledge/QA/SSE API
- 应用服务层：IngestService / KnowledgeService / RagService
- Spring AI 抽象层：DocumentReader & Splitter / EmbeddingModel / VectorStore / ChatClient / ChatModel
- 基础设施适配层：LLM / Embedding / Vector / Repository / ObjectStorage Adapter
- 数据层：MySQL / Vector DB / MinIO(S3)
- 横切治理层：Tenant/Auth/RateLimit/Observability/Audit

## 2.1 当前已实现子集（截至 2026-05-15）
- 已实现：`ingest` 子域（上传受理、上传新版本、状态查询、文档列表、版本历史查询、分块预览、异步处理执行、重处理、资产删除）
- 已实现：`knowledge` 子域（知识库列表与 INDEXED 统计）
- 已实现：`qa` 子域（同步问答、按文档独立选择可问答版本、版本化结构引用、陈旧引用汇总、无命中兜底）
- 已实现 API：`GET /api/v1/documents`、`POST /api/v1/documents/upload`、`GET /api/v1/documents/{documentId}/status`、`GET /api/v1/documents/{documentId}/versions`、`POST /api/v1/documents/{documentId}/versions`、`POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback`、`GET /api/v1/documents/{documentId}/chunks/preview`、`POST /api/v1/documents/{documentId}/reprocess`、`DELETE /api/v1/documents/{documentId}`、`GET /api/v1/knowledge-bases`、`POST /api/v1/qa/ask`
- 版本历史查询说明：接口只读 `ingest_document_versions` 与 latest projection，按 `versionNumber DESC` 暴露版本链，并由领域读模型推导 `isLatestVersion` 与 `isAskableVersion`
- 上传新版本说明：接口绑定既有 `document` 上下文，应用层校验管理权限、`INDEXED` / `FAILED` 状态门禁与 `expectedLatestVersionNumber`，并在源文件版本化落盘成功后追加新的 `UPLOAD` 版本事实；同内容上传返回复用分支，不创建新版本
- 问答版本选择说明：问答先按当前用户、目标知识库和文档级覆盖权限查询可问答范围；每个 `document` 独立选择最近一个已 `INDEXED` 版本作为当前可问答版本，并将 `documentId + versionNumber` 成对条件下推到向量检索
- 问答引用说明：`references` 返回来源版本号、来源更新时间、是否为最新版本、当前最新版本号与来源文件名；存在引用时返回 `staleReferences` 汇总，完全无引用时不返回版本提示
- 说明：本文件第 2 章是目标架构蓝图，不等于当前全部实现

## 3. 核心链路
### 文档入库链路
上传 -> 入库队列 -> 文件类型路由 -> 解析与清洗 -> cleaned.md 落盘 -> 结构优先分块 -> 向量化 -> 写入向量库 -> 写处理结果元数据

### 问答链路
提问 -> 校验知识库与问答权限 -> 查询当前用户可问答的 document/version 范围 -> 带版本范围检索 TopK -> 构建 Prompt -> 调用 ChatModel -> 返回回答、版本化引用与 stale 汇总

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
- 状态查询：`INDEXED` / `FAILED` 可顺带返回 `processingMetadata`
- 中间产物：文档目录下已接入 `cleaned.md` 主链，并支持按配置保留 `raw.xhtml`、`cleaned.html`、`parse-result.json`
- 解析路由：原生 Markdown 走最小破坏清洗；原生 HTML 绕过 Tika 后进入 HTML 语义清洗；PDF/Word 等复杂格式继续走 Tika XHTML
- 清洗边界：当前纯文字阶段已形成黄金样本回归闭环；图片只保留占位或说明文本，表格只保留 Markdown 可读形态，OCR 与复杂版式仍是后续增强项
- 删除推进：`可删状态 -> DELETING -> DELETED`
- 分块参数初值：`chunk=500`, `overlap=100`
- 失败策略：瞬时错误最多 3 次重试（指数退避 + jitter）
- 幂等目标：同一 `documentId` 重复处理最终一致

## 9. 当前架构摩擦（截至 2026-05-15）
- 当前 `ingest.domain.model.Document` 仍是一个浅模块：同时承载文档资产身份、当前处理状态、文件事实、`splitVersion`、重试上下文与 `processingMetadata`，导致“文档版本治理”和“RAG 质量优化”容易在同一模块相撞。
- `ProcessDocumentApplicationService` 是当前 ingest 主链路的共享 seam：解析、清洗、分块、向量写入、状态收口都在这里编排；当一条分支重塑 `document` / `document version` 模型时，另一条分支若同时改处理主流程，冲突概率会明显升高。
- `qa` 检索与引用链路已吸收版本语义，但向量元数据仍需要兼容旧的 `splitVersion=version-{versionNumber}-v1` 形式；后续清理旧向量或调整向量元数据时，需要同步验证 `PgVectorChunkRetrievalAdapter` 的过滤与兼容解析。
- `vector_store` 元数据当前既服务于 RAG 检索，也承担 reprocess / splitVersion 幂等控制；一旦修改向量元数据结构，往往会同时影响 ingest、qa 与治理语义。
- 与“文档版本治理”和“RAG 优化”并行开发相关的具体边界、禁改范围与合并顺序，详见 [docs/runbooks/plans/ingest-cleaning/并行开发边界约定-文档版本治理与RAG优化.md](./runbooks/plans/ingest-cleaning/并行开发边界约定-文档版本治理与RAG优化.md)。
