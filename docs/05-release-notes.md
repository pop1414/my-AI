# 发布说明（Release Notes）

## [Unreleased]
### Added
- ADR-0003：明确 V1 落地基线为 OpenAI + PostgreSQL(PGVector)
- 新增受理闭环设计文档：`docs/06-ingest-acceptance-closure.md`
- 新增 5 张受理闭环标准设计图（用例图/组件图/时序图/状态机/ER领域模型图）
- 新增处理执行设计文档：`docs/07-ingest-processing-execution.md`
- 新增 5 张处理执行标准设计图（用例图/组件图/时序图/状态机/ER领域模型图）
- 新增 ADR-0004（Proposed）：处理执行策略草案

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
