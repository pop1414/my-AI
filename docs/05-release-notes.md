# 发布说明（Release Notes）

## [Unreleased]
### Added
- 新增知识库主数据表：`knowledge_bases`（自增主键 + 唯一业务键 `kb_id`）
- 新增知识库创建接口：`POST /api/v1/knowledge-bases`
- 新增知识库编辑接口：`PATCH /api/v1/knowledge-bases/{kbId}`
- 新增文档分页列表接口：`GET /api/v1/documents`
- 新增前端文档列表主入口：`/ingest/documents`
- 新增文档列表统一跳转链路：状态 / 分块预览 / 重处理 / 删除

### Changed
- 知识库列表接口升级为“主数据 + `INDEXED` 统计”视图，保留既有 `id/name/indexedDocumentCount` 字段并新增 `description/status`
- 上传与问答链路增加知识库存在性/状态校验：显式传入不存在知识库返回 `400`，传入停用知识库返回 `409`
- 上传页与问答页改为以知识库选择器为主，知识库页升级为可创建、可编辑的管理台
- 控制台默认落点从上传页切换为文档列表页，旧路由 `/ingest/list` 改为兼容重定向
- 文档列表页行为与执行计划对齐：URL 路径参数传递 `documentId`、`kbId` 仅展示 `ACTIVE`、状态筛选与按钮显隐规则收紧
- V1.1 范围调整：原“轻量认证与访问控制”从收口范围中拆出，转为独立的成熟 RAG 权限体系规划

### Notes
- 启动迁移会自动补齐 `default` 知识库，并从 `ingest_documents` 回填历史 `kb_id`，避免旧数据升级后失联
- 当前 `Unreleased` 阶段的重点，已从“补一个轻量权限入口”调整为“先完成 V1.1 管理基础收口，再独立规划权限体系”
- 文档治理（2026-05-08）：新增成熟 RAG 权限体系专题计划与 `ADR-0005`，将权限方向从路线级描述升级为可引用的正式规划与决策留痕

---

## [1.0.0] - 2026-05-07
### Added
- ADR-0003：明确 V1 落地基线为 DashScope + PostgreSQL(PGVector)
- 新增受理闭环设计文档：`docs/06-ingest-acceptance-closure.md`
- 新增 5 张受理闭环标准设计图（用例图/组件图/时序图/状态机/ER领域模型图）
- 新增处理执行设计文档：`docs/07-ingest-processing-execution.md`
- 新增 5 张处理执行标准设计图（用例图/组件图/时序图/状态机/ER领域模型图）
- 新增 ADR-0004：处理执行策略（当前状态已更新为 Accepted）
- 新增单进程异步 worker 骨架（初版默认关闭）与任务启动 CAS 抢占能力
- 新增处理执行主链路最小实现：源文件存储、文本解析、结构优先分块、PGVector 向量写入
- 新增 Tika 文档解析实现与文本二次清洗服务（禁用嵌入资源提取）
- 新增分块预览调试接口：`GET /api/v1/documents/{documentId}/chunks/preview`
- 新增文档资产删除接口：`DELETE /api/v1/documents/{documentId}`（软删除，清理源文件与向量）
- 新增本地启动脚本：`infra/dev-up.ps1`、`infra/dev-up.sh`（一键拉起 PGVector 与后端）
- 新增 ingest 启动结构自检（`IngestSchemaVerifier`）：关键列/唯一索引不匹配时拒绝启动
- 新增 ingest 核心指标计数：`myai.ingest.process.success.total`、`myai.ingest.process.failed.total`、`myai.ingest.process.retry_scheduled.total`、`myai.ingest.delete.conflict.total`、`myai.ingest.delete.success.total`
- 新增知识库列表接口：`GET /api/v1/knowledge-bases`（`ingest_documents` 按 `INDEXED` 聚合）
- 新增问答接口：`POST /api/v1/qa/ask`（同步返回，支持无命中兜底）
- 新增前端知识库列表页：`/knowledge`（展示 `id / name / indexedDocumentCount`，支持跳转问答）
- 新增前端问答页：`/qa`（单轮问答、引用展示、无命中兜底提示）

### Changed
- ADR-0001 后续动作补充 ADR-0002 跟进项
- ADR-0002 状态调整为 Deprecated，并由 ADR-0003 替代
- V1 范围与路线图中的向量库基线同步更新为 PostgreSQL + PGVector
- 架构总览文档新增受理闭环设计索引与说明
- 架构总览文档新增处理执行设计索引与说明
- ingest 图纸索引文档支持“受理闭环 + 处理执行”双套图
- 处理执行文档新增“文本拆分规则”和“幂等控制清单”详细章节
- ADR-0004 补充幂等控制点与数据库约束建议
- 上传受理链路补充 `fileHash` 幂等：重复上传同一文件内容时复用既有 `documentId`，避免重复创建冲突任务
- 文档术语统一为“documentId = 文档资产 ID”，并同步接口契约说明
- worker 从“仅抢占”升级为“抢占后执行处理用例”，可推进 `INGESTING -> INDEXED/FAILED`
- 配置收敛（2026-04-08）：worker 默认配置改为开启（可显式关闭）
- 文档同步（2026-04-07）：README 增补“已实现 API / 规划中 API”区分，并补充测试执行前置条件说明
- 文档同步（2026-04-07）：路线图新增“当前进度快照”，明确 ingest 完成项与未开始项
- API 契约同步（2026-04-07）：`/api/v1/knowledge-bases`、`/api/v1/qa/ask` 明确标记为 `draft` 阶段
- 文档与 ADR 收敛（2026-04-08）：V1 LLM Provider 表述统一为 DashScope（Spring AI Alibaba）
- ADR 状态收敛（2026-04-08）：ADR-0004 从 `Proposed` 更新为 `Accepted`
- ingest 状态机扩展：新增 `DELETING`、`DELETED` 状态
- 上传幂等约束更新：`kbId + fileHash` 唯一索引仅约束未删除记录，支持删除后同 hash 重传
- API 契约同步（2026-04-14）：`reprocess` 标记为 implemented，`chunks/preview` 增加 `offset` 与审计字段
- 文档同步（2026-04-14）：README 与处理执行文档补充删除闭环说明，双套 ingest puml 更新
- 文档收敛（2026-04-14）：删除闭环契约表述统一（`DELETED` 可查、`INGESTING/DELETING` 返回 `409`、重复删除 `204`、不存在 `404`）
- UML 索引收敛（2026-04-14）：`文档处理.md` 迁移至 `docs/reference/ingest-文档处理技术指导.md`，图谱目录仅保留分层 puml 资产
- 基础设施修正（2026-04-14）：`infra/docker-compose.yml` 的 PG 健康检查用户改为 `admin`，与默认配置对齐
- 管理端点收敛（2026-04-14）：开放 `health/info/metrics` 以支持 ingest 最小可观测
- API 契约同步（2026-04-14）：`knowledge-bases` 与 `qa/ask` 从 draft 更新为 implemented
- 问答引用结构升级（2026-04-14）：`AskResponse.references` 从 `string[]` 升级为 chunk 级对象数组（`documentId/chunkIndex/contentPreview`）
- SSE 策略收敛（2026-04-14）：仅文档预留后续流式版本，不新增接口端点
- DDD 边界重构（2026-04-14）：`knowledge` / `qa` 从 `ingest` 拆分为独立子域包结构，`ingest` 收敛为文档资产生命周期职责
- 前端控制台收口（2026-05-07）：`knowledge` / `qa` 不再是占位页，V1 形成上传、状态查询、知识库统计、单轮问答的完整演示闭环
- 文档同步（2026-05-07）：README、前端说明、路线图与 V1 收口计划更新为当前闭环状态

### Fixed
- 

### Removed
- 

### Notes
- V1 版本归档完成，冻结范围覆盖 ingest / knowledge / qa 最小完整闭环
- 对应里程碑 tag：`v1.0.0`

---

## [0.1.0] - 2026-03-30
### Added
- 初始化项目结构
- 新增核心架构图（latest）
- 新增最小文档体系（Scope/Roadmap/Architecture/ADR/API Contract）

### Notes
- 当前版本目标：跑通 V1 基础链路
