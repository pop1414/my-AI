# 并行开发边界约定：文档版本治理与 RAG 优化

日期：2026-05-12

适用分支（示例）：

- `feature/document-version-chain`
- `feature/rag-quality-optimization`

## 1. 目的

为避免“文档版本治理升级”和“RAG 回答质量优化”在并行开发时反复冲突，
需要提前约定当前 codebase 中哪些 module 属于共享深模块、哪些 seam 可以独立演进、
以及何时必须停下来先同步设计再继续改代码。

本约定的目标不是阻止并行开发，而是降低以下风险：

- 两条分支同时重塑 `document` 相关主模型，最后只能靠大范围手工解冲突
- 一条分支修改向量元数据或问答引用契约，另一条分支仍按旧契约继续开发
- 一条分支为了提升 RAG 质量顺手改主链路编排，另一条分支为了引入版本治理也改同一条编排链路
- 合并后无法快速判断回归来自“内容质量变化”还是“文档资产/版本语义变化”

## 2. 当前架构分析

### 2.1 `Document` module 过载

涉及 files：

- `src/main/java/io/github/spike/myai/ingest/domain/model/Document.java`
- `src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepository.java`
- `src/main/resources/db/migration/V4__add_processing_metadata_to_ingest_documents.sql`

当前问题：

- 当前 `Document` 同时承载：
  - 文档资产稳定身份
  - 当前处理状态
  - 文件事实（`fileHash`、`filename`、`fileSize`）
  - `splitVersion`
  - 重试与错误上下文
  - `processingMetadata`
- 这使得 `Document` 既像“文档资产”，又像“当前最新处理记录”，还是“重试执行上下文”。

冲突含义：

- 文档版本治理升级希望把它拆成 `document` 主表 + `document version` 子表，甚至长期再拆 `processing attempt`
- RAG 优化则倾向继续往解析结果、分块来源提示、检索元数据上加字段
- 如果两条线都继续直接改这个 module，复杂性只会越来越分散，而不是集中

结论：

- 这是当前最大的共享深模块，也是最高冲突区
- 任何涉及 `Document` 语义重塑的工作，应默认归属于文档版本治理分支

### 2.2 `ProcessDocumentApplicationService` 是 ingest 主链共享 seam

涉及 files：

- `src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java`
- `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentTextParser.java`
- `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentChunker.java`
- `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentVectorIndexer.java`

当前问题：

- 解析、清洗、分块、向量写入、状态收口都在一个 application service 中编排
- `DocumentTextParser`、`DocumentChunker`、`DocumentVectorIndexer` 虽然是独立 port，但真正把它们串起来的行为集中在同一个 service

冲突含义：

- RAG 优化分支通常需要改 parser / cleaner / chunker / retrieval 行为
- 文档版本治理分支则需要引入“当前最新版本”“版本回退重新处理”“按版本落向量”等语义
- 当两条分支同时修改处理主流程时，看似都只是在改一条 service，实际上是在争夺 ingest 主链的编排权

结论：

- parser / cleaner / chunker adapter 可以独立演进
- 但 `ProcessDocumentApplicationService` 本身属于共享高冲突区，不适合作为 RAG 优化分支的常规落点

### 2.3 retrieval / reference 契约过薄

涉及 files：

- `src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java`
- `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`
- `src/main/java/io/github/spike/myai/qa/application/result/AskReferenceResult.java`
- `src/main/java/io/github/spike/myai/qa/interfaces/rest/dto/AskReferenceResponse.java`

当前问题：

- 当前 retrieval 结果最小只包含：
  - `documentId`
  - `kbId`
  - `chunkIndex`
  - `content`
- 当前问答引用结果也只稳定暴露：
  - `documentId`
  - `chunkIndex`
  - `contentPreview`

冲突含义：

- 文档版本治理升级要给 retrieval / reference 吸收版本信息、最新版本与可问答版本的差异
- RAG 优化则可能希望引入更丰富的 source metadata、标题路径、页码、重排信息
- 两条线都会扩这个 interface；如果不先约束 ownership，就会在 `qa` 契约层反复冲突

结论：

- retrieval / reference 是共享 seam，但当前 interface 太浅
- 一旦某次优化涉及对外 DTO 或向量元数据字段升级，就不再属于“安全并行”的小改

### 2.4 vector metadata 既是检索契约，也是幂等控制点

涉及 files：

- `src/main/java/io/github/spike/myai/ingest/infrastructure/vector/PgVectorDocumentVectorIndexer.java`
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`

当前问题：

- 当前向量元数据同时承担两类职责：
  - 检索过滤与引用溯源
  - `splitVersion` 下的 reprocess 幂等控制
- 当前 metadata 至少包含：
  - `documentId`
  - `kbId`
  - `chunkIndex`
  - `sourceFile`
  - `contentHash`
  - `splitVersion`
  - `sourceHint`

冲突含义：

- RAG 优化分支若调整 metadata 结构，可能影响检索召回、过滤逻辑、预览结果甚至已有幂等行为
- 文档版本治理分支若引入 `versionNumber`、`versionOriginType` 或 askable version 语义，也会改同一块 metadata

结论：

- 向量元数据 shape 变更默认视为高风险共享改动
- 不应在未同步文档契约的情况下由任一分支单方面扩字段或改字段语义

## 3. `parallel-safe` 范围（RAG 优化分支可优先承担）

以下改动可作为默认安全并行范围，只要不顺手扩大到共享契约层：

- `src/main/java/io/github/spike/myai/ingest/infrastructure/parser/**`
- `src/main/java/io/github/spike/myai/ingest/infrastructure/chunking/**`
- 与解析/清洗/分块质量相关的 `src/test/java/io/github/spike/myai/ingest/**`
- 纯评测、回归样本、runbook 与对比实验文档
- `qa` 侧仅限“检索质量内部策略优化”，前提是不改变返回 DTO、metadata shape 与权限语义

典型可并行内容：

- 清洗规则增强
- 标题识别与 Markdown 还原质量提升
- 结构优先 chunking 的确定性优化
- 召回质量评测、回归测试、离线对比

## 4. `high-conflict` 范围（默认归文档版本治理分支所有）

以下改动默认属于高冲突区：

- `src/main/java/io/github/spike/myai/ingest/domain/model/Document.java`
- `src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepository.java`
- `src/main/resources/db/migration/**`
- `src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java`
- `src/main/java/io/github/spike/myai/ingest/interfaces/rest/DocumentIngestController.java`
- `src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java`
- `src/main/java/io/github/spike/myai/qa/application/result/AskReferenceResult.java`
- `src/main/java/io/github/spike/myai/qa/interfaces/rest/dto/AskReferenceResponse.java`
- `docs/04-api-contract.yaml`

典型高冲突改动：

- `document` / `document version` 模型拆分
- 新增或调整版本治理接口
- 调整 `qa.ask` 引用结果结构
- 调整 `vector_store` metadata shape
- 调整 `processing_metadata` 的领域语义或持久化位置

## 5. 允许交叉但必须先同步的触发条件

以下内容不是绝对禁止，但一旦触发，必须先写文档同步：

1. Flyway 迁移新增或变更
2. `docs/04-api-contract.yaml` 的 ingest / qa 契约变化
3. `vector_store` metadata 字段增删或字段语义改变
4. `processing_metadata` 结构升级
5. `documents/{documentId}/status` 或未来详情接口的返回结构变化
6. `qa.ask` 的 retrieval / reference 字段变化
7. `ProcessDocumentApplicationService` 中的主链编排顺序变化
8. `reprocess` 的幂等语义从 `splitVersion` 扩展到真正的 `document version`

同步要求：

- 先在 runbook 或 handoff 中写清变化范围
- 再明确另一条分支是否需要临时 rebase、停止写共享文件或等待先合并

## 6. 合并顺序建议

### 6.1 可独立合并的情况

若 RAG 优化只涉及：

- parser / cleaner 规则
- 现有 chunker 的边界优化
- 不改 schema、不改 API、不改向量 metadata shape 的评测与回归测试

则可与文档版本治理分支独立推进和独立合并。

### 6.2 应优先合并文档版本治理分支的情况

若 RAG 优化涉及以下内容，建议优先完成文档版本治理基线，再接着做 RAG 优化：

- 需要把检索与引用升级为版本感知
- 需要给向量 metadata 引入版本字段
- 需要把 `reprocess`、`splitVersion` 语义并入真正的版本链
- 需要调整 `Document` 主模型或 `ingest_documents` 主表结构

原因：

- 这些改动都依赖“文档资产”和“文档版本”的新真相
- 若先在旧模型上做 RAG 优化，后续版本治理落地时还会再拆一次

### 6.3 RAG 优化分支应刻意回避的做法

以下做法会显著提高冲突概率，不推荐：

- 为了携带更多检索信息，直接扩 `Document` 主模型
- 为了接新 metadata，直接重写 `ProcessDocumentApplicationService`
- 为了调试方便，先把问答引用 DTO 改成 richer shape 而不同步版本治理设计
- 为了表达多轮处理差异，把 `splitVersion` 暂时伪装成“文档版本号”

## 7. 推荐文件所有权

### 7.1 文档版本治理分支优先所有权

- `src/main/java/io/github/spike/myai/ingest/domain/model/**`
- `src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/**`
- `src/main/resources/db/migration/**`
- `src/main/java/io/github/spike/myai/ingest/interfaces/rest/**` 中的版本治理入口
- `src/main/java/io/github/spike/myai/qa/**` 中与版本信息暴露直接相关的 DTO / result
- `docs/04-api-contract.yaml` 中版本治理与版本感知问答部分

### 7.2 RAG 优化分支优先所有权

- `src/main/java/io/github/spike/myai/ingest/infrastructure/parser/**`
- `src/main/java/io/github/spike/myai/ingest/infrastructure/chunking/**`
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/**` 中不改变对外契约的内部检索策略
- `src/test/java/io/github/spike/myai/ingest/**`
- `docs/runbooks/plans/ingest-cleaning/**`

### 7.3 共享文件

以下文件属于共享文件，修改前应先判断是否越过各自职责边界：

- `src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java`
- `src/main/java/io/github/spike/myai/ingest/infrastructure/vector/PgVectorDocumentVectorIndexer.java`
- `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`
- `src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java`
- `src/main/java/io/github/spike/myai/qa/interfaces/rest/dto/AskReferenceResponse.java`
- `docs/04-api-contract.yaml`

## 8. 一句话原则

**RAG 优化分支负责“内容质量与召回质量”，文档版本治理分支负责“文档资产与版本真相”；凡是会同时改变检索内容形态和文档版本语义的改动，必须先同步设计，再继续实现。**
