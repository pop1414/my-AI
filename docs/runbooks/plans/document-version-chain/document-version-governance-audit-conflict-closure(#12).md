# 版本治理审计与冲突收口后端收口说明

## 1. 收口范围

本文用于记录 GitHub issue #12《版本治理审计与冲突收口后端》的完成情况。

本次核对覆盖上传新版本、版本回退、删除、重处理四类后端治理动作的互斥执行态、并发冲突错误码、审计事件字段与失败审计上下文；不包含前端交互调整，也不替代 GitHub Issue 的人工关闭动作。

## 2. 实现落点

- 审计事件工厂：`src/main/java/io/github/spike/myai/ingest/application/service/IngestAuditEvents.java`
- 审计写入保护：`src/main/java/io/github/spike/myai/ingest/application/service/IngestAuditEventRecorder.java`
- 上传新版本用例：`src/main/java/io/github/spike/myai/ingest/application/service/UploadNewDocumentVersionApplicationService.java`
- 版本回退用例：`src/main/java/io/github/spike/myai/ingest/application/service/RollbackDocumentVersionApplicationService.java`
- 删除用例：`src/main/java/io/github/spike/myai/ingest/application/service/DeleteDocumentApplicationService.java`
- 重处理用例：`src/main/java/io/github/spike/myai/ingest/application/service/ReprocessDocumentApplicationService.java`
- 文档仓储端口：`src/main/java/io/github/spike/myai/ingest/domain/port/DocumentRepository.java`
- 文档仓储实现：`src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepository.java`
- 后端测试：`src/test/java/io/github/spike/myai/ingest/application/service/*Document*ApplicationServiceTest.java`、`src/test/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepositoryTest.java`

## 3. 验收对照

- 治理动作互斥已统一落地：上传新版本与版本回退只允许 latest 为 `INDEXED` / `FAILED` 时创建新版本；删除拒绝 `UPLOADED`、`INGESTING`、`DELETING` 执行态；重处理只允许 `INDEXED` / `FAILED`，并对 `INGESTING` 返回状态变化冲突。
- 并发冲突错误码已区分：`VERSION_CONFLICT_STALE_LATEST_VERSION` 表示调用方基于过期详情页提交；`VERSION_CONFLICT_STATE_CHANGED` 表示 latest 版本号未变但状态在 CAS 窗口内变化。
- 上传新版本审计事件已独立：成功和失败均使用 `DOCUMENT_VERSION_UPLOAD_REQUESTED`，目标类型为 `DOCUMENT_VERSION`，扩展信息包含版本号、上一版本号、来源类型、结果类型、期望 latest 与服务端观察 latest。
- 版本回退审计事件已独立：成功和失败均使用 `DOCUMENT_VERSION_ROLLBACK_REQUESTED`，扩展信息包含新版本号、回退目标版本号、来源类型、结果类型、期望 latest 与服务端观察 latest。
- 删除与重处理具备治理审计：删除使用 `DOCUMENT_DELETE_REQUESTED`，重处理使用 `DOCUMENT_REPROCESS_REQUESTED`，成功和失败事件均包含 latest 版本号、期望 latest、服务端观察 latest 与治理结果类型。
- 失败上下文进入审计扩展信息：四类治理动作失败时均记录 `errorCode` 与 `errorMessage`，审计写入失败采用 best-effort 记录策略，不反向改变主业务结果。
- 竞争场景测试已覆盖关键分支：上传新版本、版本回退、删除、重处理的状态门禁、过期 latest、CAS 冲突、审计写入失败、源文件冲突和失败上下文均有应用层或仓储层测试保护。

## 4. 关闭判断

#12 可以关闭。

判断依据如下：

- 前置 issue #4、#6、#8 均已关闭，上传新版本、版本回退、删除与列表版本语义后端能力已经稳定。
- 当前后端实现已经把四类治理动作收敛到同一组互斥原则：只有稳定终态可以发起新治理动作，执行态通过状态门禁或 CAS 拒绝。
- 冲突错误码已经能表达两类排障语义：页面过期刷新后重试，或状态变化等待当前处理完成后重试。
- 审计事件已经覆盖成功、失败和审计仓储异常不影响主业务的边界，具备后续审计查询与排障所需字段。

## 5. 测试结果

执行位置：仓库根目录。

```text
.\mvnw.cmd -q "-Dtest=UploadNewDocumentVersionApplicationServiceTest,RollbackDocumentVersionApplicationServiceTest,DeleteDocumentApplicationServiceTest,ReprocessDocumentApplicationServiceTest,JdbcDocumentRepositoryTest" test
```

结果：通过。

```text
.\mvnw.cmd -q test
```

结果：未通过。失败原因是 Spring Boot 集成测试启动时无法连接 `localhost:5432`，本地 PostgreSQL/PGVector 未运行；本次未据此修改生产代码。

## 6. 审阅建议

建议按以下顺序审阅：

1. 先看 `IngestAuditEvents`，确认事件类型、目标类型与扩展字段是否满足审计查询和排障需要。
2. 再看上传新版本与版本回退两个应用服务，重点核对 `expectedLatestVersionNumber`、CAS 失败后的冲突分类、成功/失败审计字段。
3. 检查删除与重处理应用服务，确认 `UPLOADED/INGESTING/DELETING` 等执行态不会被并发治理动作穿透。
4. 查看 `JdbcDocumentRepository` 中 `appendVersion`、`requestReprocess`、`markDeleting` 的 SQL 条件，确认 latest 版本号和 latest 状态共同形成互斥边界。
5. 最后运行上方后端测试命令，确认应用层行为和仓储 CAS 边界仍保持一致。
