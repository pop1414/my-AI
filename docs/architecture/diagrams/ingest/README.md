# Ingest 领域图纸索引（重构版）

本目录按三层图谱组织：
- `L0`：总览导航
- `L1`：责任域（acceptance / execution）
- `L2`：共享权威图（状态机 / ER）

## 推荐阅读顺序
1. `ingest-overview-map.puml`（L0）
2. `acceptance/ingest-acceptance-boundary.puml`（L1）
3. `execution/ingest-execution-boundary.puml`（L1）
4. 单用例时序图（L1，见下）
5. `shared/ingest-shared-state-machine.puml`（L2）
6. `shared/ingest-shared-er-domain.puml`（L2）

## L1：责任域图
### acceptance
- `acceptance/ingest-acceptance-boundary.puml`
- `acceptance/ingest-acceptance-upload-sequence.puml`
- `acceptance/ingest-acceptance-status-sequence.puml`

### execution
- `execution/ingest-execution-boundary.puml`
- `execution/ingest-execution-worker-process-sequence.puml`
- `execution/ingest-execution-chunks-preview-sequence.puml`
- `execution/ingest-execution-reprocess-sequence.puml`
- `execution/ingest-execution-delete-sequence.puml`

## L2：共享权威图（唯一事实来源）
- `shared/ingest-shared-state-machine.puml`
- `shared/ingest-shared-er-domain.puml`

## 迁移状态
旧命名图已完成迁移并移除，统一使用分层目录：
- `acceptance/*.puml`
- `execution/*.puml`
- `shared/*.puml`

## 质量约束
- 单时序图参与者不超过 7 个
- 主消息不超过 25 条
- 共享模型（状态机、ER）仅维护 1 份
