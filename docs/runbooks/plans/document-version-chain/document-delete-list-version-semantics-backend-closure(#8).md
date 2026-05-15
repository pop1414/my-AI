# 删除与列表页适配版本语义后端收口说明

## 1. 收口范围

本文用于记录 GitHub issue #8《删除与列表页适配版本语义后端》的完成情况。

本次收口覆盖后端删除语义、上传查重身份边界、文档列表与详情 latest projection 读模型、治理动作互斥和对应后端回归测试；不包含前端删除确认文案、删除成功结果提示或问答引用版本提示。

## 2. 实现落点

- 删除用例：`src/main/java/io/github/spike/myai/ingest/application/service/DeleteDocumentApplicationService.java`
- 上传受理用例：`src/main/java/io/github/spike/myai/ingest/application/service/AcceptUploadApplicationService.java`
- 文档仓储端口：`src/main/java/io/github/spike/myai/ingest/domain/port/DocumentRepository.java`
- 文档仓储实现：`src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepository.java`
- 文档列表读模型：`src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentListRepository.java`
- 文档详情状态读模型：`src/main/java/io/github/spike/myai/ingest/application/service/GetDocumentStatusApplicationService.java`
- 后端测试：`src/test/java/io/github/spike/myai/ingest/**`

## 3. 验收对照

- 删除仍针对整个 `document` 资产：删除流程仍以 `documentId` 为入口，清理源文件和向量索引后将 document latest 状态推进为 `DELETED`，不提供删除单个历史版本的路径。
- 列表与详情跟随当前最新版本：列表读取 `latest_filename/latest_status/latest_version_number/latest_version_origin_type`，并通过 version 表读取最新版本的 `file_size/failure_reason`；详情状态查询通过 `findById` 读取 latest projection 与 latest version fact。
- 删除后同内容重新上传生成新的 `documentId`：上传查重只排除 `DELETED` document。旧 document 进入 `DELETED` 后不会被同内容上传复用，新上传会生成新的 document 身份。
- 旧 document 终态仍可查询：`findById` 不排除 `DELETED`，显式查询 `status=DELETED` 的列表读路径仍可返回删除终态，保留审计定位入口。
- 删除后授权不自动继承：新 `documentId` 是新的文档级授权边界；仓储端口与上传流程注释已明确旧 `documentId` 上的文档级授权不会自动继承到新 document。
- 治理动作互斥：删除用例拒绝 `UPLOADED`、`INGESTING`、`DELETING` 状态进入删除执行态；上传新版本、版本回退、重处理继续依赖 latest 状态与 CAS 校验，只允许从 `INDEXED/FAILED` 等稳定状态发起。
- 后端测试覆盖：补充了删除后同内容上传身份语义、删除中同内容上传仍复用原 document、`UPLOADED` 删除冲突、latest projection 读边界和 repository SQL 边界测试。

## 4. 边界决策

删除中（`DELETING`）的 document 仍参与同内容上传查重，只有最终 `DELETED` 后才断开身份。

原因是当前数据库仍存在迁移期兼容唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_ingest_documents_kb_file_hash
ON ingest_documents (kb_id, file_hash)
WHERE file_hash IS NOT NULL AND status <> 'DELETED';
```

如果应用层在 `DELETING` 时排除旧 document，同内容上传会绕过查重后撞上唯一索引；同时若删除失败回滚，还会制造两个活动 document 身份的语义风险。因此本阶段采用更保守的边界：删除完成前继续复用旧 document，删除完成后新建 document。

## 5. Code Review 处理

收口前已按 `requesting-code-review` 技能发起独立审查。审查指出 `DELETING` 被查重排除会与现有唯一索引不一致，可能导致生产插入失败。

已完成修正：

- `findByKbIdAndFileHash` 恢复为仅排除 `DELETED`。
- `DocumentRepository` 注释明确 `DELETING` 仍保留原 document 查重命中。
- `AcceptUploadApplicationServiceTest` 增加删除中同内容上传仍复用原 `documentId` 的测试。
- `DocumentVersionReadBoundaryTest` 与 `JdbcDocumentRepositoryTest` 同步约束 SQL 边界。

## 6. 测试结果

执行位置：仓库根目录。

```text
.\mvnw.cmd -q "-Dtest=AcceptUploadApplicationServiceTest,DeleteDocumentApplicationServiceTest,JdbcDocumentRepositoryTest,DocumentVersionReadBoundaryTest" test
```

结果：通过。

```text
.\mvnw.cmd -q "-Dtest=AcceptUploadApplicationServiceTest,JdbcDocumentRepositoryTest,DocumentVersionReadBoundaryTest,IngestSchemaVerifierTest" test
```

结果：通过。`IngestSchemaVerifierTest` 中的错误日志来自测试主动注入坏 schema 的预期分支。

```text
.\mvnw.cmd -q test
```

结果：通过。

## 7. 审阅建议

建议按以下顺序审阅：

1. 先看 `JdbcDocumentRepository.findByKbIdAndFileHash`，确认查重只排除 `DELETED`，与数据库唯一索引保持一致。
2. 再看 `DeleteDocumentApplicationService`，确认 `UPLOADED/INGESTING/DELETING` 都会被删除用例拒绝进入执行态。
3. 检查 `AcceptUploadApplicationServiceTest`，重点核对删除中复用旧 `documentId`、删除后新建 `documentId` 两个身份边界。
4. 检查 `JdbcDocumentListRepository` 与 `GetDocumentStatusApplicationService`，确认列表和详情主视图均来自 latest projection 与最新版本事实。
5. 最后看 `DocumentVersionReadBoundaryTest`，确认读边界没有回退到主表旧版本事实字段。
