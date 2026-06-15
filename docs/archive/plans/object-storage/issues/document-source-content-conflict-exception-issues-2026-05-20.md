# DocumentSourceStorage 内容冲突异常类型化

## Parent

本地架构 review 第 3 项：`docs/runbooks/plans/object-storage/issues/architecture-issues.md`

## Source

- Spec：`docs/runbooks/plans/object-storage/rustfs-storage-spec.md`
- ADR：`docs/adr/ADR-0007-s3-compatible-document-asset-storage.md`

## Type

AFK - can start immediately

## What to build

将 `DocumentSourceStorage.saveVersionIfAbsent(...)` 的“同一版本源文件已存在但内容不一致”错误从字符串 message 契约升级为稳定异常类型。

完成后，local 与 S3 source adapter 都通过专用 domain exception 表达内容冲突；上传新版本与版本回退 application service 通过异常类型映射为 `VERSION_CONFLICT_STALE_LATEST_VERSION`，不再依赖 `IllegalStateException.getMessage()`。

## Spec coverage

- FS：同一版本同名 source 已存在且内容一致时重复写入保持幂等；内容不一致时拒绝覆盖。
- TS：`saveVersionIfAbsent` 内容不一致时抛出稳定冲突异常；上传新版本和回退链路保持既有 HTTP 409 / `VERSION_CONFLICT_STALE_LATEST_VERSION` 业务语义。

## Acceptance criteria

- [x] 新增稳定异常类型表达 source 内容冲突。
- [x] `LocalDocumentSourceStorage` 与 `S3DocumentSourceStorage` 在内容不一致时抛出该异常。
- [x] `UploadNewDocumentVersionApplicationService` 与 `RollbackDocumentVersionApplicationService` 捕获该异常并映射为既有业务错误码。
- [x] 删除 application 层对 `VERSION_SOURCE_CONTENT_CONFLICT_MESSAGE` / `ex.getMessage()` 的分支依赖。
- [x] adapter 与 application service 相关测试覆盖异常类型和业务码映射。

## Blocked by

None - can start immediately

## Notes

不发布到 GitHub。本文件仅作为本地 issue 草案与本次修复记录。
