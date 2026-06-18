# Ingest 子域 — 领域模型

> 文档资产入库生命周期

## 概述

Ingest 子域负责文档从上传到向量索引的完整生命周期管理。核心聚合根 `Document` 封装了文档资产的身份、文件信息、处理状态机和重试控制。所有领域模型均为 Java record（不可变）或 enum，业务逻辑集中在 application 层。

**文档处理流水线**：
```
上传 → 受理(幂等去重) → Worker 抢占(CAS) → 解析(Docling) → 分块(结构优先)
     → 向量化(PGVector) → 状态收口(INDEXED/FAILED)
```

## 枚举类型

### UploadStatus

上传/处理状态（领域枚举）。表达文档在入库流水线中的生命周期。

| 常量 | 语义 |
|------|------|
| `ACCEPTED` | 上传请求已被系统受理（API 响应语义） |
| `UPLOADED` | 文档元数据已持久化，等待进入处理流水线 |
| `INGESTING` | 正在执行解析、分块、向量化和存储 |
| `INDEXED` | 向量索引完成，可被 QA 检索 |
| `FAILED` | 处理失败，需 failureReason 诊断 |
| `DELETING` | 文档资产删除进行中（清理源文件和向量数据） |
| `DELETED` | 文档资产已删除（元数据保留用于审计和可观测） |

**状态机**：
```
UPLOADED → INGESTING → INDEXED（成功）
                     → FAILED（致命错误）
                     → UPLOADED（瞬时错误，指数退避重试）
                     → DELETING → DELETED（删除）
```

### DocumentVersionOriginType

文档版本来源类型。

| 常量 | 语义 |
|------|------|
| `UPLOAD` | 由上传产生的新版本 |
| `ROLLBACK` | 由回退到历史版本产生的新最新版本 |

---

## 聚合根

### Document

文档聚合根（"文档入库任务"）。封装文档资产身份、知识库、文件信息、文件哈希（幂等上传）、处理状态与失败信息、重试控制、重处理计数、分块版本、时间戳。

#### 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `DocumentId` | 全局唯一文档聚合 ID（值对象） |
| `workspaceId` | `String` | 工作区标识（当前固定为 `"default"`） |
| `kbId` | `String` | 知识库 ID，用于数据隔离和路由 |
| `latestVersionNumber` | `int` | 最新版本号 |
| `latestVersionOriginType` | `DocumentVersionOriginType` | 最新版本来源类型 |
| `fileHash` | `String` | 原始文件内容哈希（SHA-256），用于去重和幂等上传 |
| `filename` | `String` | 用户上传的原始文件名 |
| `fileSize` | `long` | 物理文件大小（字节） |
| `status` | `UploadStatus` | 当前生命周期状态 |
| `failureReason` | `String` | 失败时的错误摘要 |
| `retryCount` | `int` | 已尝试重试次数 |
| `retryMax` | `int` | 最大允许重试次数 |
| `nextRetryAt` | `Instant` | 下次可重试时间（基于退避策略） |
| `lastErrorCode` | `String` | 最近一次失败的内部错误码 |
| `lastErrorMessage` | `String` | 最近一次失败的详细错误信息 |
| `lastErrorAt` | `Instant` | 最近一次失败的时间戳 |
| `reprocessCount` | `int` | 累计重处理（"重洗数据"）次数 |
| `reprocessRequestedAt` | `Instant` | 最近一次重处理请求时间 |
| `splitVersion` | `String` | 分块/向量化策略版本（如 v1/v2），用于检索版本匹配 |
| `processingMetadata` | `String` | 文档级处理结果元数据（JSON 字符串） |
| `createdAt` | `Instant` | 创建时间戳 |
| `updatedAt` | `Instant` | 最后更新时间戳 |

#### 常量

| 常量 | 值 | 说明 |
|------|---|------|
| `DEFAULT_RETRY_MAX` | `3` | 默认最大重试次数 |
| `DEFAULT_SPLIT_VERSION` | `"v1"` | 默认分块版本 |

#### 工厂方法

| 方法 | 说明 |
|------|------|
| `uploaded(documentId, kbId, fileHash, filename, fileSize, now)` | 创建默认工作区的 UPLOADED 状态文档，retryCount=0，DEFAULT_RETRY_MAX，DEFAULT_SPLIT_VERSION |
| `uploaded(documentId, workspaceId, kbId, fileHash, filename, fileSize, now)` | 同上，但指定 workspaceId |

#### 状态转换方法

| 方法 | 说明 |
|------|------|
| `markIngesting(at)` | 转换到 INGESTING，清除 failureReason |
| `markIndexed(at)` | 转换到 INDEXED，清除 failureReason |
| `markFailed(reason, at)` | 转换到 FAILED，携带失败原因 |

#### 验证规则（compact constructor）

- `documentId` / `workspaceId` / `kbId` / `status` / `splitVersion` / `createdAt` / `updatedAt` — 不可为 null 或 blank
- `latestVersionNumber` / `retryMax` — 必须 >= 1
- `fileSize` / `retryCount` — 不可为负数

---

## 版本模型

### DocumentVersion

文档版本事实。携带版本级文件事实、处理事实和版本来源事实。将稳定的 `document` 身份与版本细节分离。

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `DocumentId` | 文档资产 ID |
| `versionNumber` | `int` | 版本号，从 1 开始 |
| `versionOriginType` | `DocumentVersionOriginType` | 版本来源类型 |
| `rollbackFromVersionNumber` | `Integer` | 回退源版本号（仅回退来源版本设置） |
| `fileHash` | `String` | 文件内容哈希 |
| `filename` | `String` | 源文件名 |
| `fileSize` | `long` | 文件大小 |
| `status` | `UploadStatus` | 版本处理状态 |
| `failureReason` | `String` | 失败原因 |
| `retryCount` | `int` | 当前重试次数 |
| `retryMax` | `int` | 最大重试次数 |
| `nextRetryAt` | `Instant` | 下次重试时间 |
| `lastErrorCode` | `String` | 最近错误码 |
| `lastErrorMessage` | `String` | 最近错误信息 |
| `lastErrorAt` | `Instant` | 最近错误时间 |
| `reprocessCount` | `int` | 该版本重处理次数 |
| `reprocessRequestedAt` | `Instant` | 最近重处理请求时间 |
| `splitVersion` | `String` | 该版本的分块版本 |
| `processingMetadata` | `String` | 处理结果元数据 |
| `createdByUserId` | `String` | 创建该版本的用户 ID（历史迁移数据可能为 null） |
| `createdAt` | `Instant` | 版本创建时间 |
| `updatedAt` | `Instant` | 版本更新时间 |

### DocumentVersionHistory

文档版本历史只读模型。携带同一线性版本链的只读视图，集中排序、最新标记和 QA 基线推导规则。

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `DocumentId` | 文档资产 ID |
| `items` | `List<DocumentVersionHistoryItem>` | 版本历史项，按 versionNumber 降序排列 |

**业务方法**：

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `isLatestVersion(item)` | `boolean` | 当 `item.versionNumber() == item.latestVersionNumber()` 时返回 true |
| `isAskableVersion(item)` | `boolean` | 当 `item.versionNumber() == askableVersionNumber()` 时返回 true |
| `askableVersionNumber()` | `int` | 返回状态为 INDEXED 的最大 versionNumber；无则返回 0 |

### DocumentVersionHistoryItem

文档版本历史读取模型项。服务于按文档查询版本链的只读场景，仅携带前端版本历史视图所需的稳定事实。数据来自 `ingest_documents` 与 `ingest_document_versions` 的 JOIN。

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `DocumentId` | 文档资产标识 |
| `workspaceId` | `String` | 工作区 ID |
| `kbId` | `String` | 知识库 ID |
| `latestVersionNumber` | `int` | 文档当前最新版本号 |
| `versionNumber` | `int` | 此版本记录的版本号 |
| `versionOriginType` | `DocumentVersionOriginType` | 版本来源类型 |
| `rollbackFromVersionNumber` | `Integer` | 回退源版本号（非回退版本为 null） |
| `filename` | `String` | 此版本的原始文件名 |
| `fileSize` | `long` | 文件大小（字节） |
| `status` | `UploadStatus` | 处理状态 |
| `failureReason` | `String` | 失败原因（仅 FAILED 时有值） |
| `createdByUserId` | `String` | 创建该版本的用户 ID（历史数据可能为 null） |
| `createdByDisplayName` | `String` | 创建该版本的用户显示名称（历史数据可能为 null） |
| `createdAt` | `Instant` | 版本创建时间 |
| `updatedAt` | `Instant` | 版本最后更新时间 |

### DocumentVersionArtifactContent

文档版本处理产物内容。承载从版本级产物存储读取的内容和元数据。上层内容接口仅消费此对象，不感知本地文件路径或对象存储 SDK。

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | `String` | 存储层逻辑键（用于审计和排查，非本地文件路径） |
| `content` | `String` | 产物正文内容（当前为 Markdown 文本） |
| `contentLength` | `long` | 内容 UTF-8 字节长度 |

---

## 值对象

### DocumentId

文档身份值对象。在知识库内唯一标识一个"文档资产"。当 fileHash 不变时（含重处理）保持稳定。包装原始 String，防止跨层裸字符串传递。

| 字段 | 类型 | 说明 |
|------|------|------|
| `value` | `String` | 文档 ID 字符串 |

### ChunkMetadata

分块结构化元数据。由 Docling Serve 的 HybridChunker 在 server-side 产出后映射到此值对象。携带面包屑标题链、源页码和内容类型三个结构化信号，供下游向量索引和检索使用。

| 字段 | 类型 | 说明 |
|------|------|------|
| `headings` | `List<String>` | 面包屑标题链（不可变，禁止 null） |
| `pageNumber` | `int` | 源页码（0 表示未知，禁止负数） |
| `contentType` | `ChunkContentType` | 内容类型（禁止 null，如 PARAGRAPH） |

**紧凑构造函数**：校验 + 防御性拷贝，不做默认值填充。headings 为 null 抛 NPE，pageNumber 为负抛 IAE，contentType 为 null 抛 NPE。

**工厂方法**：

| 方法 | 说明 |
|------|------|
| `of(headings, pageNumber, contentType)` | 安全构造：headings 为 null → 空 list，过滤 null 和空白字符串；pageNumber 仍拒绝负数；contentType 为 null → PARAGRAPH |

**设计约束**：TXT 等无结构标记的格式——headings 为空 list、pageNumber 为 0、contentType 为 PARAGRAPH，下游代码不可假定三个字段为非空/非默认值。

### SplitVersion

分块版本工具类。使用 "vN" 格式（如 v1/v2）。无效版本回退到 v1。

| 方法 | 说明 |
|------|------|
| `next(current)` | 递增版本号。current 为 null/blank/不匹配 `^v(\d+)$` 时返回 `"v1"` |

---

## 分块与解析模型

### DocumentChunk

文档分块结果模型。

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | `String` | 分块正文 |
| `chunkMetadata` | `ChunkMetadata` | 分块结构化元数据（null 时归一化为默认值） |

### DocumentChunkPreview

文档分块预览模型。用于前端调试页面的分块预览。

| 字段 | 类型 | 说明 |
|------|------|------|
| `chunkIndex` | `int` | 分块序号 |
| `content` | `String` | 分块全文 |
| `contentLength` | `int` | 分块内容长度（来自原始内容，用于质量评估） |
| `sourceFile` | `String` | 源文件名 |
| `contentHash` | `String` | 分块内容哈希 |
| `splitVersion` | `String` | 分块版本 |
| `chunkMetadata` | `ChunkMetadata` | 分块结构化元数据 |

### DocumentParseResult

文档解析结果。携带清洗后的 Markdown（主管线输出）和处理元数据 JSON 字符串。

| 字段 | 类型 | 说明 |
|------|------|------|
| `cleanedMarkdown` | `String` | 清洗后经转换的 Markdown 主管线输出，作为分块输入 |
| `processingMetadata` | `String` | 文档级处理结果元数据 JSON 字符串，在终态回填到 ingest_documents.processing_metadata |

---

## 列表查询模型

### DocumentListFilter

文档列表读取模型过滤条件。封装文档列表查询的过滤和分页参数。null 字段表示该维度不过滤。

| 字段 | 类型 | 说明 |
|------|------|------|
| `workspaceId` | `String` | 工作区标识 |
| `kbId` | `String` | 知识库 ID（可 null） |
| `status` | `UploadStatus` | 文档状态（可 null） |
| `filename` | `String` | 文件名模糊匹配关键词（可 null） |
| `excludeDeleted` | `boolean` | 是否排除 DELETED 文档 |
| `limit` | `int` | 每页大小（正整数） |
| `offset` | `int` | 偏移量（非负整数） |

### DocumentListItem

文档列表读取模型结果项。由仓储通过 SQL 直接投影，不经过聚合根。不包含领域行为（如状态机转换），仅数据载体。

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `DocumentId` | 文档唯一标识 |
| `workspaceId` | `String` | 工作区标识 |
| `kbId` | `String` | 知识库业务键 |
| `latestVersionNumber` | `int` | 当前最新版本号 |
| `latestVersionOriginType` | `DocumentVersionOriginType` | 当前最新版本来源类型 |
| `filename` | `String` | 原始上传文件名 |
| `fileSize` | `long` | 文件大小（字节） |
| `status` | `UploadStatus` | 当前处理状态 |
| `failureReason` | `String` | 失败原因（仅 FAILED 时有值） |
| `createdAt` | `Instant` | 上传受理时间 |
| `updatedAt` | `Instant` | 最后更新时间 |

### DocumentListPage

文档列表分页结果。

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `List<DocumentListItem>` | 当前页文档列表项（非 null，可为空） |
| `total` | `long` | 匹配过滤条件的总记录数（非负） |
| `limit` | `int` | 每页大小（正整数） |
| `offset` | `int` | 偏移量（非负整数） |

### UploadTicket

上传受理票据（领域返回模型）。表达"系统已受理上传请求"的业务结果。

| 字段 | 类型 | 说明 |
|------|------|------|
| `documentId` | `DocumentId` | 文档资产 ID（相同 kbId + fileHash 下稳定） |
| `status` | `UploadStatus` | 当前受理状态 |

---

## 出站端口（Port）

### 处理管线端口

#### DocumentTextParser

文档文本解析端口。定义将原始文件字节解析为结构化中间产物的能力。管线：raw.xhtml → cleaned.html → cleaned.md，其中 cleaned.md 是后续分块和向量化的唯一输入。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `parse` | `DocumentParseResult` | `filename, content` | 解析原始文件字节为第一阶段中间产物 |

#### DocumentChunker

文档分块端口。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `chunk` | `List<DocumentChunk>` | `text` | 将纯文本拆分为可向量化分块 |

#### DocumentVectorIndexer

文档向量索引端口。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `index` | `void` | `document, chunks` | 将文档分块写入向量索引 |
| `deleteByDocumentIdAndSplitVersion` | `void` | `documentId, splitVersion` | 删除指定文档+分块版本的向量（重处理前避免陈旧向量参与检索） |
| `deleteByDocumentId` | `void` | `documentId` | 删除文档所有向量（所有 splitVersion） |

### 存储端口

#### DocumentSourceStorage

文档源文件存储端口。职责：上传受理后持久化原始文件内容；处理期间按文档资产 ID 读取原始文件内容。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `save` | `void` | `documentId, filename, content` | 保存源文件 |
| `saveVersion` | `void` | `documentId, versionNumber, filename, content` | 保存版本化源文件（默认回退到 `save()`） |
| `saveVersionIfAbsent` | `boolean` | `documentId, versionNumber, filename, content` | 幂等版本化保存；新文件创建返回 true |
| `load` | `Optional<byte[]>` | `documentId, filename` | 读取源文件 |
| `loadVersion` | `Optional<byte[]>` | `documentId, versionNumber, filename` | 读取版本化源文件（默认回退到 `load()`） |
| `deleteByDocumentId` | `void` | `documentId` | 删除文档资产的所有源文件 |

#### DocumentProcessingArtifactStorage

文档处理中间产物存储端口。定义处理流水线产出的中间产物（cleaned.md、raw.xhtml 等）的持久化能力。设计约定：cleaned.md 必须写入（强制），调试产物可选（配置控制）。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `save` | `void` | `documentId, parseResult` | 默认方法；委托到 `saveVersion`（DEFAULT_WORKSPACE_ID，version 1） |
| `saveVersion` | `void` | `workspaceId, documentId, versionNumber, parseResult` | 保存版本化解析产物 |
| `loadVersionArtifact` | `Optional<DocumentVersionArtifactContent>` | `workspaceId, documentId, versionNumber, artifactName, maxBytes` | 加载版本化产物；不存在返回 empty，超大抛 `DocumentVersionArtifactTooLargeException` |
| `deleteByDocumentId` | `void` | `workspaceId, documentId` | 删除文档所有处理产物 |

### 仓储端口

#### DocumentRepository

文档仓储端口。所有写操作使用 CAS（Compare And Set）模式通过 `expectedStatus` 实现乐观锁，防止并发状态覆盖。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `save` | `void` | `document` | 保存文档聚合（插入或更新） |
| `save` | `void` | `document, createdByUserId` | 保存并记录初始版本创建者 |
| `findById` | `Optional<Document>` | `workspaceId, documentId` | 按 ID 查找文档 |
| `findVersionByNumber` | `Optional<DocumentVersion>` | `workspaceId, documentId, versionNumber` | 按版本号查找版本事实 |
| `findByKbIdAndFileHash` | `Optional<Document>` | `workspaceId, kbId, fileHash` | 按知识库+文件哈希查找（幂等上传）；排除 DELETED 状态 |
| `findOldestReadyForProcessing` | `Optional<Document>` | `workspaceId, now` | 查找最老的可处理文档（status=UPLOADED，next_retry_at 到期或为 null，retry_count < retry_max） |
| `compareAndSetStatus` | `boolean` | `workspaceId, documentId, expectedStatus, targetStatus, failureReason, updatedAt` | CAS 状态更新 |
| `markIndexed` | `boolean` | `workspaceId, documentId, expectedStatus, processingMetadata, updatedAt` | 标记处理成功；清除失败原因和重试信息 |
| `markFailed` | `boolean` | `workspaceId, documentId, expectedStatus, failureReason, processingMetadata, errorCode, errorMessage, errorAt, updatedAt` | 标记处理失败（含错误详情） |
| `markRetry` | `boolean` | `workspaceId, documentId, expectedStatus, retryCount, nextRetryAt, errorCode, errorMessage, errorAt, updatedAt` | 标记瞬时失败并调度重试（回退状态到 UPLOADED） |
| `requestReprocess` | `boolean` | `workspaceId, documentId, expectedStatus, newSplitVersion, requestedAt` | 请求重处理（通常伴随 splitVersion 递增） |
| `appendUploadVersion` | `boolean` | `workspaceId, documentId, expectedLatestVersionNumber, newVersion, updatedAt` | 追加上传来源新版本（CAS on latestVersionNumber） |
| `appendRollbackVersion` | `boolean` | `workspaceId, documentId, expectedLatestVersionNumber, newVersion, updatedAt` | 追加回退来源新版本（CAS on latestVersionNumber） |
| `findLatestIndexedVersionNumber` | `int` | `workspaceId, documentId` | 查找当前可问答版本号（最大 INDEXED 版本）；无则返回 0 |
| `markDeleting` | `boolean` | `workspaceId, documentId, expectedStatus, updatedAt` | 推进状态到 DELETING |
| `markDeleted` | `boolean` | `workspaceId, documentId, updatedAt` | 从 DELETING 推进到 DELETED |
| `rollbackDeleting` | `boolean` | `workspaceId, documentId, rollbackStatus, updatedAt` | 从 DELETING 回退到原状态（删除失败补偿） |

#### DocumentListRepository

文档列表查询仓储端口（CQRS 读端）。与命令型仓储（DocumentRepository）分离。仅处理分页查询，返回读取模型（DocumentListPage），不返回完整聚合根。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findPage` | `DocumentListPage` | `filter` | 按过滤条件查询文档分页列表 |

#### DocumentVersionHistoryRepository

文档版本历史只读仓储端口。仅暴露版本历史查询能力，不提供写/更新操作。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findByDocumentId` | `DocumentVersionHistory` | `workspaceId, documentId` | 按工作区+文档 ID 查询版本历史；无记录时返回空历史（items 为空列表） |

#### DocumentChunkPreviewRepository

文档分块预览查询端口。用于前端调试页面的分块预览。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findByDocumentId` | `List<DocumentChunkPreview>` | `workspaceId, documentId, splitVersion, limit, offset` | 按分块索引升序的分页分块预览数据 |
| `countByDocumentId` | `int` | `workspaceId, documentId, splitVersion` | 指定文档+分块版本的总分块数（无数据返回 0） |

#### DocumentIdGenerator

文档 ID 生成端口。领域层声明"生成一个有效 DocumentId"的能力，不关心实现方式（UUID、雪花、DB 序列等）。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `nextId` | `DocumentId` | — | 生成下一个文档 ID（必须有效且非 null） |

---

## 关联关系

```
Document ──(聚合根)──→ DocumentId, UploadStatus, DocumentVersionOriginType
DocumentVersion ──(版本事实)──→ DocumentId, UploadStatus, DocumentVersionOriginType
DocumentVersionHistory ──(版本链)──→ List<DocumentVersionHistoryItem>
DocumentVersionHistoryItem ──(版本项)──→ DocumentId, UploadStatus, DocumentVersionOriginType
DocumentChunk ──(分块结果)──→ ChunkMetadata
DocumentParseResult ──(解析产物)──→ cleanedMarkdown, processingMetadata
UploadTicket ──(受理票据)──→ DocumentId, UploadStatus

跨子域关联：
ingest_documents.kb_id ──→ knowledge_bases.kb_id
```

## 设计约束

- **聚合根状态机**：Document 内部通过 `withStatus()` 私有方法实现不可变状态转换，返回新实例
- **CAS 乐观锁**：DocumentRepository 所有写操作通过 `expectedStatus` 防止并发覆盖
- **幂等上传**：通过 kbId + fileHash 唯一标识文档资产，相同哈希不创建新文档
- **版本链**：支持上传新版本和版本回退两种来源，通过 latestVersionNumber 的 CAS 保证版本号单调递增
- **分块版本**：splitVersion（v1/v2/...）用于策略迭代时隔离新旧向量，支持重处理而不影响已有检索
- **CQRS 分离**：DocumentListRepository（读端）与 DocumentRepository（命令端）分离
- **重试控制**：retryCount/retryMax/nextRetryAt 三字段控制指数退避重试策略
- **源文件与产物分离存储**：DocumentSourceStorage 和 DocumentProcessingArtifactStorage 使用 SEPARATE 存储接口

---

_生成时间: 2026-06-15 | 扫描模式: 深度扫描 | 最后更新: 2026-06-18 (SourceHint→ChunkMetadata 对齐)_
