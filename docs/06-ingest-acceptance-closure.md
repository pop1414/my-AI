# 受理闭环与可追踪化设计（Ingest Acceptance Closure）

## 1. 背景与目标
在“上传受理骨架”阶段，系统只返回 `documentId + ACCEPTED`，但缺少任务落库与状态查询能力。  
本阶段目标是把受理流程升级为“可追踪闭环”：

- 上传受理时把任务元数据落库（内部状态 `UPLOADED`）
- 提供状态查询接口（`GET /api/v1/documents/{id}/status`）
- 为后续异步处理状态流转（`INGESTING -> INDEXED/FAILED`）打基础

## 1.1 术语约定（本阶段）
- `documentId`：文档资产 ID，不是“一次性任务 ID”。
- 文档资产定义：存在于某个 `kbId` 下的某份文件内容（可由 `kbId + fileHash` 唯一刻画）。
- 语义规则：
  - 同一 `kbId` 下内容不变（`fileHash` 不变）时，`documentId` 保持稳定。
  - reprocess 只重建处理结果，不更换 `documentId`。

## 2. 组件清单（必须 / 可选）
### 必须组件（M）
- Interfaces: `DocumentIngestController`
- Application: `AcceptUploadUseCase`, `GetDocumentStatusUseCase`
- Domain: `Document`, `DocumentRepository(Port)`, `DocumentIdGenerator(Port)`, `UploadStatus`
- Infrastructure: `JdbcDocumentRepository`, `UuidDocumentIdGenerator`
- Data: PostgreSQL（`ingest_documents` 表）

### 可选组件（O，后续增强）
- `QueuePublisher` / 消息队列 / `IngestWorker`
- Embedding/LLM 服务调用链路

## 3. 标准设计图套餐（5 张必选）
### 3.1 用例图（对齐功能边界）
- `docs/architecture/diagrams/ingest/ingest-acceptance-closure-usecase.puml`

### 3.2 组件图 / 架构图（对齐模块分层与职责）
- `docs/architecture/diagrams/ingest/ingest-acceptance-closure-components.puml`

### 3.3 时序图（对齐核心流程交互）
- `docs/architecture/diagrams/ingest/ingest-acceptance-closure-sequence.puml`

### 3.4 状态机图（对齐状态流转规则）
- `docs/architecture/diagrams/ingest/ingest-acceptance-closure-state.puml`

### 3.5 ER / 领域模型图（对齐数据结构）
- `docs/architecture/diagrams/ingest/ingest-acceptance-closure-er-domain.puml`

## 4. 关键通信原则（为什么这样通信）
1. Controller 只依赖 UseCase，不直接访问数据库  
目的：隔离 HTTP 协议与业务编排，保证接口层轻量稳定。

2. Application 只依赖 Domain Port，不依赖 JdbcTemplate/SQL  
目的：替换存储实现时，不影响用例逻辑。

3. Infrastructure 实现 Port，并承载技术细节  
目的：把数据库、SDK、连接配置等变动限制在适配层。

4. 对外响应语义与内部状态语义分离  
`ACCEPTED` 是接口语义（请求已受理）；`UPLOADED` 是内部状态语义（任务已落库可追踪）。

## 5. 受理闭环流程（摘要）
1. 客户端调用 `POST /api/v1/documents/upload`
2. Controller 校验请求、计算 `fileHash` 并组装 `AcceptUploadCommand`
3. Application 按 `kbId + fileHash` 查询是否已存在资产记录
4. 若已存在：直接返回已存在的 `documentId + ACCEPTED`
5. 若不存在：生成新 `DocumentId`，创建 `Document(UPLOADED)` 并通过 `DocumentRepository` 落库
6. Controller 按 `documentId` 持久化原始源文件（供后续处理链路读取）
7. 客户端调用 `GET /api/v1/documents/{id}/status`
8. Application 通过 Repository 查询并返回当前状态；未命中返回 404

## 6. 后续扩展建议
下一阶段可在此闭环上增加真正处理链路：
- `UPLOADED -> INGESTING -> INDEXED/FAILED`
- 文档解析、分块、向量化、PGVector 入库
- 失败原因持久化与重试机制

## 6.1 受理幂等实现（2026-04-01）
为避免用户重复点击或客户端重试导致冲突任务，受理阶段已补充最小幂等能力：

1. Controller 在接收上传文件时计算 `SHA-256`，作为 `fileHash`。  
2. Application 在创建新任务前，先按 `kbId + fileHash` 查询是否已存在任务。  
3. 若已存在，直接返回已有 `documentId + ACCEPTED`；若不存在，落库新任务（`UPLOADED`）。  
4. PostgreSQL 在 `ingest_documents` 上增加唯一索引：`(kb_id, file_hash)`（`file_hash IS NOT NULL`）。

该变更对“受理闭环与可追踪化”的影响：
- 闭环主流程不变：仍然是“受理 -> 可查询状态”。
- 可追踪性增强：重复上传会收敛到同一文档资产 ID，避免同内容多条记录造成追踪分叉。
- 对外兼容：接口路径与响应结构不变，客户端仍使用 `documentId` 查询状态。

## 7. 复用建议（给后续类似业务）
当你设计新的“有状态业务流程”时，建议固定按本章 5 张图输出：
1. 用例图：先收边界，防止功能蔓延
2. 组件图：明确层次与职责归属
3. 时序图：验证主流程是否可执行
4. 状态机图：保证状态转换闭环
5. ER/领域模型图：保证数据结构可落地

## 8. 下一阶段导航
- 处理执行阶段文档：`docs/07-ingest-processing-execution.md`
- 处理执行 ADR（已采纳）：`docs/adr/ADR-0004-v1-ingest-processing-strategy.md`
