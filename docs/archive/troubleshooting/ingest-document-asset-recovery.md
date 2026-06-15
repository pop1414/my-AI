# Ingest 文档资产副作用恢复手册

状态：恢复手册  
日期：2026-05-19  
适用范围：`ingest` 文档资产、源文件、处理产物、PGVector 向量数据与数据库状态排障。

## 1. 目的和边界

`ingest` 链路会跨越多个副作用边界：

- 数据库：`ingest_documents`、`ingest_document_versions`、`vector_store`
- 源文件：`data/ingest/source/default/documents/{documentId}/versions/{versionNumber}/{filename}`
- 处理产物：`data/ingest/artifacts/default/documents/{documentId}/versions/{versionNumber}/`
- 向量数据：`vector_store.metadata` 中的 `documentId`、`documentVersionNumber`、`splitVersion`

这些资源不在同一个原子事务里。上传、处理、回退、删除遇到进程中断、磁盘异常、向量库异常或人工误删时，可能出现半成功状态。本手册只定义人工排查和恢复步骤，不改变业务代码，不替代后续自动巡检任务。

默认假设：

- 工作区 ID 为 `default`。
- 本地存储根目录为 `INGEST_STORAGE_ROOT_DIR`，未配置时为 `data/ingest`。
- PostgreSQL 连接配置来自 `PGVECTOR_DATASOURCE_URL`、`PGVECTOR_DATASOURCE_USERNAME`、`PGVECTOR_DATASOURCE_PASSWORD`，未配置时默认连接 `jdbc:postgresql://localhost:5432/myai`。
- 当前向量表为 `vector_store`。

## 2. 恢复前安全步骤

执行任何写操作前，先完成下面步骤。

1. 暂停会推进 ingest 状态的进程。
   - 本地开发环境可以停止 Spring Boot 应用。
   - 如果必须保持应用运行，至少设置 `INGEST_WORKER_ENABLED=false` 后重启，避免 worker 继续认领 `UPLOADED` 文档。
2. 备份数据库中目标文档的状态。

```sql
SELECT *
FROM ingest_documents
WHERE document_id = '<documentId>';

SELECT *
FROM ingest_document_versions
WHERE document_id = '<documentId>'
ORDER BY version_number;

SELECT id, metadata
FROM vector_store
WHERE metadata->>'documentId' = '<documentId>';
```

3. 备份本地文件目录。

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Copy-Item -LiteralPath "$root\source\default\documents\<documentId>" -Destination "$root\backup-source-<documentId>" -Recurse -ErrorAction SilentlyContinue
Copy-Item -LiteralPath "$root\artifacts\default\documents\<documentId>" -Destination "$root\backup-artifacts-<documentId>" -Recurse -ErrorAction SilentlyContinue
```

4. 先分类再恢复，不要直接删除或更新。

| 分类 | 说明 | 是否需要停 worker |
| --- | --- | --- |
| 可直接修复 | 孤儿文件或孤儿向量，数据库已是 `DELETED` 或不存在 | 建议停止应用后处理 |
| 需要停止处理流程后修复 | `UPLOADED`、`INGESTING`、`DELETING` 等执行态残留 | 必须停止 worker |
| 需要先备份后修复 | 需要手工 `UPDATE` / `DELETE` 数据库 | 必须停止应用或确认无并发请求 |

## 3. 快速总览检查

替换 `<documentId>` 后执行：

```sql
SELECT
    document_id,
    kb_id,
    latest_version_number,
    latest_status,
    latest_filename,
    status,
    failure_reason,
    last_error_code,
    last_error_message,
    retry_count,
    next_retry_at,
    updated_at
FROM ingest_documents
WHERE document_id = '<documentId>';
```

```sql
SELECT
    document_id,
    version_number,
    version_origin_type,
    rollback_from_version_number,
    filename,
    status,
    failure_reason,
    split_version,
    last_error_code,
    updated_at
FROM ingest_document_versions
WHERE document_id = '<documentId>'
ORDER BY version_number;
```

```sql
SELECT
    metadata->>'documentId' AS document_id,
    metadata->>'documentVersionNumber' AS document_version_number,
    metadata->>'splitVersion' AS split_version,
    COUNT(*) AS vector_count
FROM vector_store
WHERE metadata->>'documentId' = '<documentId>'
GROUP BY 1, 2, 3
ORDER BY 2, 3;
```

文件系统检查：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Get-ChildItem -LiteralPath "$root\source\default\documents\<documentId>" -Recurse -File
Get-ChildItem -LiteralPath "$root\artifacts\default\documents\<documentId>" -Recurse -File
```

## 4. 状态分类与恢复步骤

### 4.1 orphan source

定义：源文件目录存在，但数据库中没有对应 document，或 document 已经是 `DELETED`。

识别：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Get-ChildItem -LiteralPath "$root\source\default\documents" -Directory
```

对每个目录名执行：

```sql
SELECT document_id, status, latest_status
FROM ingest_documents
WHERE document_id = '<documentId>';
```

恢复策略：

- 如果数据库无记录：确认不是刚上传但事务尚未提交后，可以删除该 source 目录。
- 如果数据库状态是 `DELETED`：确认 `vector_store` 已无对应向量后，可以删除该 source 目录。
- 如果数据库状态不是 `DELETED`：不要删除，转到 `missing source` 或执行态残留排查。

恢复命令：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Remove-Item -LiteralPath "$root\source\default\documents\<documentId>" -Recurse
```

恢复后验证：

```powershell
Test-Path "$root\source\default\documents\<documentId>"
```

### 4.2 missing source

定义：数据库中存在非 `DELETED` 文档版本，但该版本源文件缺失。常见影响是处理链路报 `source file not found`，版本回退报 `VERSION_ROLLBACK_SOURCE_FILE_MISSING`。

识别：

```sql
SELECT
    d.document_id,
    d.latest_version_number,
    d.latest_status,
    v.version_number,
    v.filename,
    v.status
FROM ingest_documents d
JOIN ingest_document_versions v ON v.document_id = d.document_id
WHERE d.document_id = '<documentId>'
  AND d.status <> 'DELETED'
ORDER BY v.version_number;
```

按版本检查：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Test-Path "$root\source\default\documents\<documentId>\versions\<versionNumber>\<filename>"
```

恢复策略：

- 如果能从备份、原上传文件或对象存储副本找回相同文件：恢复到原版本路径，文件名必须使用数据库中的 `filename`。
- 如果找不回源文件，且版本状态是 `UPLOADED` 或 `INGESTING`：将 latest 版本标记为 `FAILED`，避免 worker 无限重试。
- 如果找不回的是历史 `INDEXED` 版本：不要直接删除版本事实；先记录为不可回退版本，后续通过重新上传新版本恢复业务可用性。

恢复文件：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
New-Item -ItemType Directory -Force -Path "$root\source\default\documents\<documentId>\versions\<versionNumber>"
Copy-Item -LiteralPath "<backupFile>" -Destination "$root\source\default\documents\<documentId>\versions\<versionNumber>\<filename>"
```

无法恢复 latest 源文件时的数据库收口：

```sql
BEGIN;

UPDATE ingest_documents
SET status = 'FAILED',
    latest_status = 'FAILED',
    failure_reason = 'source file missing; manual recovery required',
    last_error_code = 'SOURCE_FILE_MISSING',
    last_error_message = 'source file missing during manual recovery',
    last_error_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE document_id = '<documentId>'
  AND latest_version_number = <versionNumber>
  AND latest_status IN ('UPLOADED', 'INGESTING');

UPDATE ingest_document_versions
SET status = 'FAILED',
    failure_reason = 'source file missing; manual recovery required',
    last_error_code = 'SOURCE_FILE_MISSING',
    last_error_message = 'source file missing during manual recovery',
    last_error_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE document_id = '<documentId>'
  AND version_number = <versionNumber>
  AND status IN ('UPLOADED', 'INGESTING');

COMMIT;
```

恢复后验证：

- 重新查询 `/api/v1/documents/{documentId}/status`。
- 如果恢复了文件且状态仍是 `UPLOADED`，重启 worker 后等待处理。
- 如果手工标记 `FAILED`，前端应展示失败原因并允许后续上传新版本。

### 4.3 orphan artifact

定义：处理产物目录存在，但数据库中对应版本不存在，或 document 已是 `DELETED`。

处理产物目录：

```text
data/ingest/artifacts/default/documents/{documentId}/versions/{versionNumber}/
```

当前可能出现的文件：

- `cleaned.md`：主链产物，正常处理成功后必须存在。
- `parse-result.json`：默认保留的 processing metadata 文件化载体。
- `raw.xhtml`：仅在 `INGEST_STORAGE_KEEP_RAW_XHTML=true` 时保留。
- `cleaned.html`：仅在 `INGEST_STORAGE_KEEP_CLEANED_HTML=true` 时保留。

识别：

```sql
SELECT document_id, version_number, status
FROM ingest_document_versions
WHERE document_id = '<documentId>'
ORDER BY version_number;
```

恢复策略：

- 如果 document 已是 `DELETED`：可以删除整个 artifacts 文档目录。
- 如果版本不存在：可以删除该版本 artifacts 目录。
- 如果版本是 `INDEXED` 但缺少 `cleaned.md`：这不是 orphan artifact，而是 `missing artifact`，需要重新处理或从备份恢复。

删除命令：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Remove-Item -LiteralPath "$root\artifacts\default\documents\<documentId>\versions\<versionNumber>" -Recurse
```

### 4.4 missing artifact

定义：数据库版本状态为 `INDEXED`，但对应版本缺少 `cleaned.md`。常见影响是正文读取接口无法返回已索引版本的正文。

识别：

```sql
SELECT document_id, version_number, filename, status
FROM ingest_document_versions
WHERE document_id = '<documentId>'
  AND version_number = <versionNumber>
  AND status = 'INDEXED';
```

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Test-Path "$root\artifacts\default\documents\<documentId>\versions\<versionNumber>\cleaned.md"
```

恢复策略：

- 如果有 artifacts 备份：恢复 `cleaned.md`。
- 如果源文件存在：优先通过上传新版本或重处理机制重新生成产物，不建议手工拼接向量内容生成正文。
- 如果源文件也缺失：按 `missing source` 处理。

### 4.5 orphan vector

定义：`vector_store` 中存在某个 document 的向量，但数据库 document 不存在、已删除，或版本事实不存在。

识别：

```sql
SELECT
    metadata->>'documentId' AS document_id,
    metadata->>'documentVersionNumber' AS document_version_number,
    metadata->>'splitVersion' AS split_version,
    COUNT(*) AS vector_count
FROM vector_store
WHERE metadata->>'documentId' = '<documentId>'
GROUP BY 1, 2, 3
ORDER BY 2, 3;
```

恢复策略：

- 如果 `ingest_documents.status = 'DELETED'`：删除该 document 全部向量。
- 如果某个 `documentVersionNumber` 在 `ingest_document_versions` 中不存在：删除该版本向量。
- 如果版本存在且 `status = 'INDEXED'`：不要删除，除非已经确认该版本向量内容损坏。

删除整个 document 的向量：

```sql
DELETE FROM vector_store
WHERE metadata->>'documentId' = '<documentId>';
```

删除单个版本的向量：

```sql
DELETE FROM vector_store
WHERE metadata->>'documentId' = '<documentId>'
  AND metadata->>'documentVersionNumber' = '<versionNumber>';
```

如果需要按旧兼容 `splitVersion` 删除：

```sql
DELETE FROM vector_store
WHERE metadata->>'documentId' = '<documentId>'
  AND metadata->>'splitVersion' = '<splitVersion>';
```

恢复后验证：

```sql
SELECT COUNT(*)
FROM vector_store
WHERE metadata->>'documentId' = '<documentId>';
```

### 4.6 DELETING 卡住

定义：文档长时间处于 `DELETING`，说明删除动作已经通过 CAS 抢占执行权，但没有完成最终 `DELETED` 标记，或回滚失败。

识别：

```sql
SELECT
    document_id,
    latest_version_number,
    status,
    latest_status,
    updated_at,
    last_error_code,
    last_error_message
FROM ingest_documents
WHERE status = 'DELETING'
  AND updated_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes';
```

恢复前必须确认：

- 应用或 worker 已停止。
- 没有正在执行的删除请求。
- 已备份目标文档 DB 行和文件目录。

判断分支：

1. 源文件、处理产物、向量都已清理完：将状态收口为 `DELETED`。
2. 任一物理资产仍存在：优先补清理物理资产；如果清理失败，回滚到删除前状态。
3. 无法判断删除前状态：查询审计事件或版本历史；不能确认时不要手工回滚为 `INDEXED`。

确认物理资产：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Test-Path "$root\source\default\documents\<documentId>"
Test-Path "$root\artifacts\default\documents\<documentId>"
```

```sql
SELECT COUNT(*)
FROM vector_store
WHERE metadata->>'documentId' = '<documentId>';
```

物理资产已清理完时，收口为 `DELETED`：

```sql
BEGIN;

UPDATE ingest_documents
SET status = 'DELETED',
    latest_status = 'DELETED',
    failure_reason = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE document_id = '<documentId>'
  AND status = 'DELETING';

UPDATE ingest_document_versions
SET status = 'DELETED',
    failure_reason = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE document_id = '<documentId>'
  AND status = 'DELETING';

COMMIT;
```

需要回滚删除中状态时：

```sql
BEGIN;

UPDATE ingest_documents
SET status = '<previousStatus>',
    latest_status = '<previousStatus>',
    failure_reason = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE document_id = '<documentId>'
  AND status = 'DELETING';

UPDATE ingest_document_versions
SET status = '<previousStatus>',
    failure_reason = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE document_id = '<documentId>'
  AND version_number = <latestVersionNumber>
  AND status = 'DELETING';

COMMIT;
```

`<previousStatus>` 只能使用删除前明确记录的状态，通常是 `INDEXED` 或 `FAILED`。不要把未知状态回滚成 `UPLOADED` 或 `INGESTING`。

## 5. 常见组合场景

### 5.1 上传新版本后，DB 有新 latest，但源文件缺失

特征：

- `ingest_documents.latest_version_number = N`
- `ingest_document_versions.version_number = N`
- 状态通常是 `UPLOADED`
- `source/default/documents/{documentId}/versions/N/{filename}` 不存在

处理：

1. 如果能找回原上传文件，按 `missing source` 恢复文件。
2. 如果找不回，手工标记 latest version 为 `FAILED`。
3. 恢复后让用户重新上传新版本。

### 5.2 回退创建了新版本，但回退源文件复制失败

特征：

- 新版本来源 `version_origin_type = 'ROLLBACK'`
- `rollback_from_version_number` 指向历史版本
- 新版本源文件缺失

处理：

1. 检查回退来源版本源文件是否存在。
2. 如果来源文件存在，复制到新版本路径。
3. 如果来源文件也缺失，按 `missing source` 处理来源版本；新版本标记 `FAILED`。

### 5.3 删除失败后残留物理文件

特征：

- 删除接口返回 500 或审计中出现 `DOCUMENT_DELETE_FAILED`
- 文档状态可能已回滚为原状态，也可能卡在 `DELETING`
- source、artifacts 或 vector 只清了一部分

处理：

1. 先停止应用或 worker。
2. 如果状态已回滚，用户可重试删除；不要直接改 DB。
3. 如果状态卡在 `DELETING`，按 `DELETING 卡住` 分支处理。

## 6. 恢复后统一验证

恢复完成后，至少执行下面检查。

```sql
SELECT document_id, latest_version_number, status, latest_status, updated_at
FROM ingest_documents
WHERE document_id = '<documentId>';

SELECT version_number, status, split_version, updated_at
FROM ingest_document_versions
WHERE document_id = '<documentId>'
ORDER BY version_number;

SELECT
    metadata->>'documentVersionNumber' AS document_version_number,
    metadata->>'splitVersion' AS split_version,
    COUNT(*) AS vector_count
FROM vector_store
WHERE metadata->>'documentId' = '<documentId>'
GROUP BY 1, 2
ORDER BY 1, 2;
```

接口验证：

- `GET /api/v1/documents/{documentId}/status`
- `GET /api/v1/documents/{documentId}/versions`
- 如果存在 `INDEXED` 版本：`GET /api/v1/documents/{documentId}/content?source=LATEST`
- 如果该文档可问答：执行一次 QA，确认引用版本符合预期。

文件验证：

```powershell
$root = $env:INGEST_STORAGE_ROOT_DIR
if ([string]::IsNullOrWhiteSpace($root)) { $root = 'data/ingest' }
Get-ChildItem -LiteralPath "$root\source\default\documents\<documentId>" -Recurse -File -ErrorAction SilentlyContinue
Get-ChildItem -LiteralPath "$root\artifacts\default\documents\<documentId>" -Recurse -File -ErrorAction SilentlyContinue
```

## 7. 不建议的操作

- 不要在未备份时直接 `DELETE FROM ingest_documents`。
- 不要手工创建 `ingest_document_versions` 新版本来绕过应用层版本治理。
- 不要把未知来源的文件复制到版本源文件路径后直接标记 `INDEXED`。
- 不要在 worker 运行中批量修改 `UPLOADED`、`INGESTING`、`DELETING` 状态。
- 不要用向量内容反推 `cleaned.md` 作为正式正文恢复来源。
- 不要删除历史 `splitVersion=v1` 向量，除非已确认该文档版本事实不再依赖历史兼容。

## 8. 后续自动化巡检候选项

后续可以把本手册中的检查固化为只读巡检命令：

- 数据库存在、source 缺失的版本清单。
- source 存在、数据库不存在或已删除的孤儿目录。
- artifacts 存在、版本事实不存在的孤儿目录。
- `INDEXED` 版本缺少 `cleaned.md` 的清单。
- `vector_store` 中版本事实不存在的向量清单。
- 超过阈值仍停留在 `DELETING` 的文档清单。
