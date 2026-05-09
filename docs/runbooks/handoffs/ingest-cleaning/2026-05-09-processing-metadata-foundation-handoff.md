# 会话交接包：processing_metadata 基础设施收口

日期：2026-05-09  
状态同步：2026-05-09  
仓库：`D:\Code\project\my-AI-ingest`  
当前分支：`feature/ingest-processing-metadata-foundation`

## 1. 本次主题定位

本阶段目标不是直接改造完整的 `cleaned.md` 主链，而是先把 `processing_metadata` 做成正式可依赖的后端基础设施，解决以下前置问题：

1. `ingest_documents` 缺少正式处理结果元数据字段
2. 状态查询接口只能返回 `documentId + status`
3. 后续解析清洗链路即使产出元数据，也没有稳定的持久化与返回出口

本次收口完成后，后续阶段可以在不改状态查询契约方向的前提下，继续把 `cleaned.md` 与元数据自动回填接进去。

## 2. 当前完成概览

### 2.1 已完成：processing_metadata 字段落地

- 新增 Flyway 迁移 `V4__add_processing_metadata_to_ingest_documents.sql`
- `ingest_documents` 表已支持 `processing_metadata JSONB`
- `IngestSchemaVerifier` 已将 `processing_metadata` 纳入关键列校验

### 2.2 已完成：领域模型与仓储读写支持

- `Document` 聚合已增加 `processingMetadata` 字段
- `JdbcDocumentRepository` 已支持 `processing_metadata` 的查询、UPSERT 与状态更新
- `markIndexed` / `markFailed` 已支持在状态收口时写入处理结果元数据
- `markRetry` / `requestReprocess` 已明确在回退到 `UPLOADED` 时清空 `processing_metadata`

### 2.3 已完成：状态接口终态返回能力

- `GetDocumentStatusApplicationService` 已按状态控制暴露口径
- 仅 `INDEXED` / `FAILED` 终态允许返回 `processingMetadata`
- `UPLOADED` / `INGESTING` 阶段固定返回 `null`
- `DocumentIngestController` 已将 JSON 字符串解析为结构化 JSON 响应对象

## 3. 本次涉及的关键文件

后端代码：

- [Document.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/domain/model/Document.java)
- [DocumentRepository.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/domain/port/DocumentRepository.java)
- [JdbcDocumentRepository.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepository.java)
- [GetDocumentStatusApplicationService.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/application/service/GetDocumentStatusApplicationService.java)
- [DocumentStatusResult.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/application/result/DocumentStatusResult.java)
- [DocumentIngestController.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/interfaces/rest/DocumentIngestController.java)
- [DocumentStatusResponse.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/interfaces/rest/dto/DocumentStatusResponse.java)
- [IngestSchemaVerifier.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/IngestSchemaVerifier.java)
- [V4__add_processing_metadata_to_ingest_documents.sql](/D:/Code/project/my-AI-ingest/src/main/resources/db/migration/V4__add_processing_metadata_to_ingest_documents.sql)

测试：

- [GetDocumentStatusApplicationServiceTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/application/service/GetDocumentStatusApplicationServiceTest.java)
- [ProcessDocumentApplicationServiceTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationServiceTest.java)
- [JdbcDocumentRepositoryTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepositoryTest.java)
- [DocumentIngestControllerTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/interfaces/rest/DocumentIngestControllerTest.java)
- [IngestSchemaVerifierTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/infrastructure/persistence/IngestSchemaVerifierTest.java)

文档：

- [04-api-contract.yaml](/D:/Code/project/my-AI-ingest/docs/04-api-contract.yaml)
- [03-architecture.md](/D:/Code/project/my-AI-ingest/docs/03-architecture.md)
- [05-release-notes.md](/D:/Code/project/my-AI-ingest/docs/05-release-notes.md)
- [07-ingest-processing-execution.md](/D:/Code/project/my-AI-ingest/docs/07-ingest-processing-execution.md)
- [RAG 文档解析与清洗方案.md](/D:/Code/project/my-AI-ingest/docs/runbooks/plans/ingest-cleaning/RAG%20%E6%96%87%E6%A1%A3%E8%A7%A3%E6%9E%90%E4%B8%8E%E6%B8%85%E6%B4%97%E6%96%B9%E6%A1%88.md)
- [2026-05-09-ingest-cleaning-optimization-kickoff.md](/D:/Code/project/my-AI-ingest/docs/runbooks/handoffs/ingest-cleaning/2026-05-09-ingest-cleaning-optimization-kickoff.md)

## 4. 已完成验证

已执行：

- `.\\mvnw.cmd -q test`

验证结论：

- Flyway 迁移可正常将库版本推进到 `V4`
- schema 自检可识别 `processing_metadata` 关键列
- 状态接口在终态可返回结构化 `processingMetadata`
- 现有 ingest / auth / qa 主链路测试未被本次基础设施改动破坏

## 5. 当前边界与剩余缺口

当前已完成的是“字段、仓储、接口出口”，还没有完成“真正的元数据生产链路”。

当前仍未完成：

- `Tika -> Jsoup -> flexmark` 中间产物主链
- `cleaned.md` 的稳定落地
- 处理过程中对 `processing_metadata` 的自动构建与回填
- `raw.xhtml` / `cleaned.html` / `parse-result.json` 的调试旁路策略实现

## 6. 下一步建议

建议从当前分支合回 `feature/ingest-cleaning-optimization` 后，继续切下一阶段分支，例如：

- `feature/ingest-cleaned-md-pipeline`

下一阶段优先顺序：

1. 改造解析清洗主链，稳定产出 `cleaned.md`
2. 在 `ProcessDocumentApplicationService` 中接入 `processing_metadata` 自动回填
3. 保持 `documents/chunks/preview` 与 `qa.ask` 消费链路回归通过

## 7. 一句话交接

**`feature/ingest-processing-metadata-foundation` 已完成 `processing_metadata` 的数据库落地、schema 自检、仓储读写和状态接口终态透传；下一步应转入 `cleaned.md` 主链与元数据自动回填实现，而不是继续停留在纯字段层。**
