# Spec: 文档版本治理

> Parent PRD: 无（从 CONTEXT.md §5.2–§5.3、§4.1 提取，对应已实现的系统事实）
> Related ADR: ADR-0005-rag-access-control-foundation.md、ADR-0006-document-version-read-boundary.md、ADR-0007-s3-compatible-document-asset-storage.md

## 概述

文档版本治理是文档资产的管理层：上传新版本、版本回退、删除文档、查看版本链。它运行在 ingest 子域内，受 ADR-0005 定义的授权模型约束。

## 功能规格

### 用户场景

| # | 角色 | 动作 | 结果 |
|---|------|------|------|
| 1 | KB_CONTRIBUTOR、KB_MANAGER、WORKSPACE_ADMIN、WORKSPACE_OWNER | 从文档详情页上传新版本 | 创建新版本，进入处理链路 |
| 2 | 同上 | 从版本历史列表回退到历史 INDEXED 版本 | 创建 ROLLBACK 类型新版本，进入处理链路 |
| 3 | 同上 + DOC_ALLOW_MANAGE | 从列表页或详情页删除文档 | 文档进入 DELETING → DELETED |
| 4 | KB_MANAGER、WORKSPACE_ADMIN、WORKSPACE_OWNER | 查看版本历史、历史版本正文与差异 | 只读查看历史版本 |
| 5 | KB_READER | 仅读取当前可问答基线正文 | 返回可问答版本的 cleaned.md |

### 状态流转

文档版本生命周期状态：

```text
UPLOADED → INGESTING → INDEXED
                     → FAILED

  UPLOADED|INDEXED|FAILED → DELETING → DELETED
```

**上传新版本**门禁：仅当前最新版本状态为 `INDEXED` 或 `FAILED` 时允许。`UPLOADED`、`INGESTING`、`DELETING`、`DELETED` 时禁止。

**版本回退**门禁：仅当前最新版本状态为 `INDEXED` 或 `FAILED` 时允许。回退目标版本必须至少为 `INDEXED`。当前最新版本不能作为回退目标。

**删除**门禁：仅当前最新版本状态为 `UPLOADED`、`INDEXED` 或 `FAILED` 时允许。

**重处理**门禁：当前最新版本为 `FAILED` 时可触发。重处理不创建新版本号，仅更新当前版本的处理状态。

### 业务规则

#### 上传新版本

- 入口：**仅**文档详情页（不在上传页、不在列表页）
- 仅对具备目标 document 管理权限的用户可见
- 无权限时隐藏入口，不使用置灰
- 专用接口：`POST /api/v1/documents/{documentId}/versions`
- 参数：`file`（multipart）、`expectedLatestVersionNumber`（int）。不额外要求业务字段
- 目标 document 所属 knowledge base 锁定，不允许切换
- 来源文件名可变化，不打断版本链
- 乐观并发校验：`expectedLatestVersionNumber` 必须匹配服务端当前最新版本号。不匹配时拒绝，引导刷新重试
- 同内容（SHA-256 匹配当前最新版本）：不创建新版本，返回 `versionCreated=false` 与 `reusedLatestVersionNumber`
- 成功创建：新版本初始状态 `UPLOADED`，进入处理链路

**创建成功后的 UI 行为：**
- 详情页默认切换到新最新版本视图
- 页面内稳定结果提示（非一次性 toast），同时展示新版本号与上一版本号
- 提供"查看版本历史"快捷入口
- 仅当存在可问答版本时，提供"去问答"快捷入口
- 版本历史列表自动展开一次

#### 版本回退

- 创建新最新版本，内容等同于被选中的历史版本。**不直接改写**既有版本链
- 入口：文档详情页的版本历史列表中，针对具体历史版本提供"回退为最新版本"动作
- 专用接口：`POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback`
- 参数：`expectedLatestVersionNumber`（int）。请求体可为空
- 回退目标版本必须至少为 `INDEXED`
- 当前最新版本不能作为回退目标
- 二次确认：明确提示将创建新版本（非指针回拨）、问答基线可能变化
- 二次确认**不要求**手动输入 documentId

**回退成功后：**
- 新版本初始状态 `UPLOADED`，重新进入处理链路
- 新版本标记 `versionOriginType=ROLLBACK`
- 被回退目标版本标记"曾被回退为最新版本"
- 页面内稳定结果提示：同时展示新版本号与回退目标版本号
- 版本历史列表自动展开一次
- 新版本尚未 INDEXED 时，问答继续使用最近一个已 INDEXED 的版本

#### 删除文档

- 软删除：保留 documentId、状态轨迹与审计上下文，删除后仍可查询到该文档的 `DELETED` 终态信息
- 删除后同内容重新上传生成**新** documentId
- 入口：文档列表页和文档详情页，共用同一套确认 Modal
- 二次确认：
  1. 风险提示 Modal 展示 documentId 与删除后果
  2. 用户必须手动输入完整 documentId 后确认按钮才可用
- 输入框初始为空（不由路由、缓存或系统默认值预填）
- 展示的 documentId 为只读对照信息，不提供一键复制
- 输入不匹配时，确认按钮保持禁用，显示错误提示（仅在用户开始输入后显示）

**删除确认主文案关键后果（独立条目展示）：**
- 删除后同内容重新上传会生成新的 documentId
- 原 documentId 对应的文档级授权随旧 document 资产结束
- 知识库级角色不因旧 document 删除自动失效

**删除成功后：**
- 从详情页删除 → 立即返回文档列表页，尽量保留筛选条件与分页位置
- 列表立即移除已删除项
- 空页自动回退到上一页
- 页面内稳定结果提示：展示已删除的原 documentId，提供"去上传页"主按钮
- "去上传页"不自动带入原文档所属 knowledge base

#### 治理动作互斥

- 同一 document 任一时刻只允许一个治理动作进入执行态
- 治理动作：上传新版本、版本回退、重处理、删除
- 并发请求统一拒绝，不做后台排队
- 问答不属于治理动作，不参与互斥
- 当前最新版本为 FAILED 时，"上传新版本""版本回退""重处理"可同时作为入口开放，但互斥——任一启动后其它入口立即失效

#### 版本历史

- 默认按 `versionNumber DESC`（最新在前）排序
- 默认折叠
- 上传新版本/版本回退成功后自动展开一次
- 每项展示：versionNumber、来源文件名、上传时间、上传人、中文状态、是否可用于问答
- 标记体系（固定展示顺序）：`最新` → `可问答` → `回退产生` → `曾被回退为最新版本`
- 同一版本可同时出现多个标记
- `可问答` 标记由问答基线规则推导，**不持久化**
- FAILED 版本展示简短失败原因摘要

#### 历史版本查看

- 仅管理权限用户可切换查看历史版本
- 查看时显示稳定提示："当前正在查看历史版本 vN，最新版本为 vM；查看历史版本不会改变问答基线"
- 差异摘要：比较历史版本 vs 当前最新版本（非相邻版本比较）。摘要级默认，可展开详细 diff。至少包含新增、删除、修改三类
- 详情页主操作（重处理、删除）仍作用于当前最新版本，非当前查看的历史版本
- 提供"返回最新版本"快捷入口
- 查看历史不改变问答基线

### 错误场景

#### 正文读取错误

| 错误码 | 管理人员提示 | 普通读者提示 |
|--------|-------------|-------------|
| `CONTENT_NOT_READY` | 处理中空态，提示稍后刷新 | 处理中空态，提示稍后刷新 |
| `CONTENT_ARTIFACT_MISSING` | 正文产物缺失，请重处理或联系管理员 | 正文暂不可用 |
| `CONTENT_TOO_LARGE` | 正文过大，当前版本暂不支持完整在线查看 | 同管理人员 |
| `403` | 不展示正文区域内容 | 不展示正文区域内容 |

#### 权限拒绝提示

| 动作 | 拒绝文案 |
|------|---------|
| 上传新版本无权限 | "你没有管理该文档版本的权限" |
| 版本回退无权限 | "你没有回退该文档版本的权限" |
| 上传因状态不允许被拒绝 | "当前仅允许在 INDEXED、FAILED 状态下发起上传新版本" |
| 回退因目标不满足条件被拒绝 | "只能回退到已形成可用内容的版本" |
| 乐观并发校验失败 | "当前最新版本已变化，请刷新详情后重试" |

#### 治理权限矩阵

删除入口可见性：

- `WORKSPACE_OWNER` / `WORKSPACE_ADMIN`：**可见**
- 文档存在 `DOC_DENY` 覆盖：**不可见**
- 文档存在 `DOC_ALLOW_MANAGE` 覆盖：**可见**
- 文档无显式授权：回退到 `KB_MANAGER` 判断

上传新版本、版本回退：同上权限规则。

## 技术规格

### 数据模型

```
document（主表）
├── documentId（PK）
├── kbId
├── workspaceId
├── latestVersionNumber          ← 轻量版本头指针
├── latestStatus                 ← 镜像 latestVersionNumber 的真实状态
├── latestFilename               ← 镜像 latestVersionNumber 的来源文件名
├── latestVersionOriginType      ← UPLOAD | ROLLBACK
└── version[]（1:N → document_version）

document_version（版本表）
├── documentId + versionNumber（业务主键）
├── versionOriginType            ← UPLOAD | ROLLBACK（必填，持久化）
├── rollbackFromVersionNumber    ← 可空，仅 ROLLBACK 时设置
├── fileHash、filename、fileSize ← 来源文件事实
├── status                       ← 处理结果事实
├── failureReason                ← 处理失败事实
├── processingMetadata           ← 处理结果元数据（JSON）
├── retryCount、retryMax、nextRetryAt   ← 重试上下文
├── lastErrorCode、lastErrorMessage、lastErrorAt  ← 错误上下文
├── createdByUserId              ← 上传人/创建人
├── createdAt、updatedAt
```

**约束：**
- `versionNumber` 从 1 开始递增
- 版本回退占用下一个递增编号（不复用被回退目标编号）
- 同内容复用不消耗 versionNumber
- `askableVersionNumber` 不持久化到主表（由问答基线规则推导）
- `askable` / `askableAt` 不持久化到 document_version
- 长期方向：retry/reprocess 字段应从 document_version 拆分到独立处理尝试表

### API 契约

#### POST /api/v1/documents/{documentId}/versions
上传新版本。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | multipart | 是 | 上传文件 |
| `expectedLatestVersionNumber` | int | 是 | 乐观并发校验 |

创建新版本成功响应：
```json
{
  "documentId": "string",
  "versionCreated": true,
  "versionResultType": "CREATED",
  "versionNumber": 2,
  "previousVersionNumber": 1,
  "latestVersionNumber": 2,
  "askableVersionNumber": 1,
  "canAskNow": true,
  "status": "UPLOADED",
  "versionOriginType": "UPLOAD"
}
```

同内容复用成功响应：
```json
{
  "documentId": "string",
  "versionCreated": false,
  "versionResultType": "REUSED_IDENTICAL_CONTENT",
  "reusedLatestVersionNumber": 1,
  "latestVersionNumber": 1,
  "askableVersionNumber": 1,
  "canAskNow": true,
  "status": "INDEXED",
  "versionOriginType": "UPLOAD"
}
```

错误码：`VERSION_UPLOAD_NOT_ALLOWED_STATUS`、`VERSION_UPLOAD_NO_MANAGE_PERMISSION`、`VERSION_UPLOAD_DOCUMENT_NOT_FOUND`

#### POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback
版本回退。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `expectedLatestVersionNumber` | int | 是 | 乐观并发校验 |

请求体：空。

成功响应：
```json
{
  "documentId": "string",
  "versionNumber": 3,
  "rollbackFromVersionNumber": 1,
  "latestVersionNumber": 3,
  "askableVersionNumber": 2,
  "canAskNow": true,
  "status": "UPLOADED",
  "versionOriginType": "ROLLBACK"
}
```

错误码：`VERSION_ROLLBACK_NOT_ALLOWED_STATUS`、`VERSION_ROLLBACK_NO_MANAGE_PERMISSION`、`VERSION_ROLLBACK_TARGET_NOT_INDEXED`、`VERSION_ROLLBACK_TARGET_IS_LATEST`、`VERSION_ROLLBACK_DOCUMENT_NOT_FOUND`、`VERSION_ROLLBACK_VERSION_NOT_FOUND`、`VERSION_ROLLBACK_SOURCE_FILE_MISSING`

#### DELETE /api/v1/documents/{documentId}
删除文档。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `expectedLatestVersionNumber` | int | 否 | 传入时作为并发校验 |

响应：`204 No Content`。

#### GET /api/v1/documents/{documentId}/content
正文读取。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | 枚举 | 是 | `LATEST` / `ASKABLE_BASELINE` / `EXPLICIT_VERSION` |
| `versionNumber` | int | 仅 EXPLICIT_VERSION | 必须为正整数 |

响应字段：`documentId`、`versionNumber`、`latestVersionNumber`、`isLatestVersion`、`isAskableVersion`、`source`、`status`、`filename`、`createdAt`、`updatedAt`、`contentMarkdown`、`contentLength`、`truncated`

错误码：`DOCUMENT_NOT_FOUND`、`DOCUMENT_CONTENT_FORBIDDEN`、`CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING`、`CONTENT_TOO_LARGE`、`VERSION_NOT_FOUND`、`VERSION_CONTENT_FORBIDDEN`

> 正文读取的完整处理链路侧契约（artifact 存储策略、回退版本的 cleaned.md 独立落盘、源文件不开放下载、调试产物等）见 `docs/features/ingest/SPEC.md`。本 SPEC 为版本治理侧的权威定义。

#### GET /api/v1/documents/{documentId}/versions
版本历史列表。每项字段：`documentId`、`versionNumber`、`versionOriginType`、`rollbackFromVersionNumber`、`filename`、`fileSize`、`status`、`failureReason`、`createdByUserId`、`createdByDisplayName`、`createdAt`、`updatedAt`、`isLatestVersion`、`isAskableVersion`

### 校验规则

| 规则 | 执行层 |
|------|--------|
| 上传文件不能为空 | Controller（400） |
| `source` 必须为合法枚举值 | Controller（400） |
| `source=EXPLICIT_VERSION` 必须带 `versionNumber` | Controller（400） |
| `source≠EXPLICIT_VERSION` 禁止带 `versionNumber` | Controller（400） |
| `versionNumber` 必须为正整数 | Controller（400） |
| `expectedLatestVersionNumber` 必须匹配服务端状态 | 应用层（VERSION_CONFLICT_STALE_LATEST_VERSION） |
| 治理动作互斥 | 应用层（409 CONFLICT） |

### 审计

上传新版本与版本回退各自记录独立审计事件（不复用泛化的文档更新事件）。

审计事件字段：
- `documentId`、`versionNumber`（若已创建）、`targetVersionNumber`（回退目标版本）
- `expectedLatestVersionNumber` + 校验时实际看到的 `latestVersionNumber`
- `versionOriginType`、`versionResultType`

审计表正式列：`documentId`、`versionNumber`、`targetVersionNumber`、`versionOriginType`、`versionResultType`

审计扩展 JSON：`expectedLatestVersionNumber`、实际 `latestVersionNumber`、失败时的业务错误码与服务端 message

同内容复用也记录审计事件（归属"上传新版本请求"事件族，结果码为复用成功未创建新版本）。

## 验证

### 自动化验证命令

| 命令 | 预期结果 | 失败处理 |
|------|----------|----------|
| `mvn test -pl . -Dtest="*DocumentContent*"` | 正文读取测试通过 | 修复代码或测试 |
| `mvn test -pl . -Dtest="*DocumentVersion*"` | 版本治理测试通过 | 修复代码或测试 |
| `mvn test -pl . -Dtest="*DocumentDelete*"` | 删除测试通过 | 修复代码或测试 |

### 后端测试覆盖

正文读取测试（至少覆盖）：
- 默认正文读取（LATEST）
- 问答基线正文读取
- 指定版本正文读取
- KB_READER 禁止任意历史版本
- DELETED 文档禁止正文读取
- CONTENT_NOT_READY
- CONTENT_ARTIFACT_MISSING
- CONTENT_TOO_LARGE

权限测试覆盖角色：`WORKSPACE_ADMIN`、`KB_MANAGER`、`KB_CONTRIBUTOR`、`KB_READER`。`KB_ASKER` 不作为新能力正向测试角色（仅保留兼容/迁移测试）。

集成测试场景（核心验证"读到的是哪个版本"）：
- latest 已 INDEXED
- latest INGESTING + 旧版本已 INDEXED
- latest FAILED 且有 cleaned.md
- 指定历史版本管理读取

存储测试：
- artifact key 含 workspaceId/documentId/versionNumber
- source/ 与 artifacts/ prefix 不混用
- 缺失 artifact 不触发源文件重解析

### 前端测试覆盖

- KB_READER 不显示版本历史与治理入口
- 问答侧栏版本提示（基线 ≠ 最新版本时）
- 历史版本查看"不改变问答基线"提示
- 正文错误态文案正确
- 403 不渲染正文区域

### 手动 QA 步骤

| 步骤 | 验证点 |
|------|--------|
| 在 INDEXED 文档上上传新版本 | 新版本创建，详情页切换 |
| 上传完全相同内容 | versionCreated=false，详情显示复用结果 |
| 在 INGESTING 状态上传新版本 | 被拒绝，提示具体状态限制 |
| 回退到 INDEXED 版本 | 新 ROLLBACK 版本创建，进入处理链路 |
| 回退到当前最新版本 | 被拒绝 |
| 输入正确 documentId 完成删除 | 成功，返回列表并保留筛选 |
| 输入不匹配时确认删除 | 按钮保持禁用 |
| 并发治理动作 | 第二个被拒绝 |
| 版本历史自动展开 | 上传新版本/回退成功后展开一次 |

### 验收样例

复用现有黄金样本链，新增同一 document 的三版本场景：
- `v1 INDEXED`（原始上传）
- `v2 INGESTING`（新版本处理中）
- `v3 FAILED with cleaned.md`（处理失败但正文存在）

### E2E 排期

E2E 排在后端接口与前端单元/组件测试完成后。首期覆盖：
- KB_READER 从 QA 引用侧栏打开问答基线正文
- KB_MANAGER 打开指定历史版本正文
- KB_READER 不能通过 URL 直达历史版本正文

## 待定问题

- 处理尝试表（processing attempt table）何时从 document_version 拆分，吸收 retryCount、retryMax、reprocessCount 等字段？
- 正文读取分页策略（当前仅 size cap + CONTENT_TOO_LARGE 拒绝，何时引入分页/分段读取）？
