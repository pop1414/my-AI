# 发布说明（Release Notes）

## [Unreleased]
### Added
- ADR-0003：明确 V1 落地基线为 OpenAI + PostgreSQL(PGVector)
- 新增受理闭环设计文档：`docs/06-ingest-acceptance-closure.md`
- 新增 5 张受理闭环标准设计图（用例图/组件图/时序图/状态机/ER领域模型图）
- 新增处理执行设计文档：`docs/07-ingest-processing-execution.md`
- 新增 5 张处理执行标准设计图（用例图/组件图/时序图/状态机/ER领域模型图）
- 新增 ADR-0004（Proposed）：处理执行策略草案
- 新增单进程异步 worker 骨架（默认关闭）与任务启动 CAS 抢占能力
- 新增处理执行主链路最小实现：源文件存储、文本解析、结构优先分块、PGVector 向量写入
- 新增 Tika 文档解析实现与文本二次清洗服务（禁用嵌入资源提取）
- 新增分块预览调试接口：`GET /api/v1/documents/{documentId}/chunks/preview`

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
- 文档同步（2026-04-07）：README 增补“已实现 API / 规划中 API”区分，并补充测试执行前置条件说明
- 文档同步（2026-04-07）：路线图新增“当前进度快照”，明确 ingest 完成项与未开始项
- API 契约同步（2026-04-07）：`/api/v1/knowledge-bases`、`/api/v1/qa/ask` 明确标记为 `draft` 阶段

### Fixed
- 

### Removed
- 

---

## [0.1.0] - 2026-03-30
### Added
- 初始化项目结构
- 新增核心架构图（latest）
- 新增最小文档体系（Scope/Roadmap/Architecture/ADR/API Contract）

### Notes
- 当前版本目标：跑通 V1 基础链路
