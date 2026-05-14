# 会话交接：document 版本治理与删除/回退规则收敛

日期：2026-05-12  
仓库：`D:\Code\project\my-AI-ingest`

## 1. 本轮工作范围

本轮主要通过多轮 `grill-with-docs` 问答，持续收敛以下主题：

- `document` / `document version` 的领域语义
- 删除确认、删除后续引导、删除后的权限与导航规则
- 上传新版本的入口、状态约束、结果提示与版本链规则
- 历史版本查看、差异提示、问答基线与主视图关系
- 版本回退的语义、入口、结果提示、状态变化与历史标记
- 版本治理接口契约、成功响应、错误码与乐观并发校验
- 版本治理审计事件、审计字段分层、前端错误反馈
- 同一 `document` 下多个治理动作的并发互斥规则

本轮主要落盘位置：

- [CONTEXT.md](D:/Code/project/my-AI-ingest/CONTEXT.md)

## 2. 已收敛的核心结论

### 2.1 document 与版本链

- `documentId` 表示文档资产稳定身份，不是一次性任务 ID。
- 同一 `knowledge base` 下，同内容上传复用既有 `documentId`。
- 内容变化不做隐式归属猜测；必须从已有 `document` 显式发起“上传新版本”。
- 版本链保持**单条线性链**，不支持从任意历史版本直接分叉上传新版本。
- 若要基于旧版本继续演进，先回退该版本为最新版本，再从当前最新版本发起上传新版本。

### 2.2 数据模型方向

- 版本信息从当前单一 `document` 记录中拆出。
- 目标结构：
  - `document` 主表：稳定身份 + 当前最新版本轻量投影
  - `document version` 子表：每个版本的文件事实、处理事实、来源关系
  - 长期可演进出 `processing attempt` 表：重试/错误/重处理执行轨迹

`document` 主表当前建议保留：

- `documentId`
- `workspaceId`
- `kbId`
- `latestVersionNumber`
- `latestStatus`
- `latestFilename`
- `latestVersionOriginType`

当前阶段不在主表持久化：

- `askableVersionNumber`

`document version` 当前建议持久化：

- `documentId + versionNumber` 业务主键
- `versionOriginType`
- `rollbackFromVersionNumber`（可空）
- `fileHash`
- `filename`
- `fileSize`
- `status`
- `failureReason`
- `processingMetadata`
- `createdAt`
- `updatedAt`
- 创建人 / 上传人

长期建议从 `document version` 再拆出的字段：

- `retryCount`
- `retryMax`
- `nextRetryAt`
- `lastErrorCode`
- `lastErrorMessage`
- `lastErrorAt`
- `reprocessCount`
- `reprocessRequestedAt`

### 2.3 versionNumber 规则

- `versionNumber` 从 `1` 开始递增。
- 上传新版本创建新版本时，占用下一个递增编号。
- 版本回退创建的新最新版本，也占用下一个递增编号。
- 同内容复用、未创建新版本时，不消耗新的 `versionNumber`。

### 2.4 删除规则

- 删除是终止整个 `document` 资产，不是删除某个历史版本。
- 删除后同内容重新上传，生成新的 `documentId`。
- 新 `document` 不继承旧 `documentId` 的文档级授权。
- 删除确认必须明确提示：
  - 同内容重新上传会生成新的 `documentId`
  - 新 `document` 不自动继承旧文档级授权
- 删除确认使用结构化 `Modal`，不是轻量 `Popconfirm`。
- 删除需要更强确认：
  - 手动输入完整 `documentId`
  - 输入框保持为空，不可预填
  - 目标 `documentId` 只读展示，不提供复制
- 删除成功后：
  - 页面内稳定提示持续可见
  - 提供“去上传页”入口
  - 说明旧文档级授权已结束
  - 说明知识库级角色不因删除自动失效
- 详情页删除成功后返回列表页，尽量保留原筛选/分页上下文。

### 2.5 上传新版本

- 入口只保留在文档详情页。
- 仅对具备目标 `document` 管理权限的用户显示。
- 无权限时隐藏入口，不置灰。
- 发起时锁定原 `knowledge base`。
- 允许文件名变化，不打断版本链。
- 仅在当前最新版本状态为 `INDEXED`、`FAILED` 时开放。
- 若新文件内容与当前最新版本完全相同：
  - 不创建新版本
  - 走成功分支
  - 显式提示当前仍停留在原最新版本
- 创建新版本成功后：
  - 详情页自动切到新的最新版本
  - 主视图显示 `UPLOADED` / `INGESTING`
  - 使用页面内稳定结果提示
  - 展示新版本号与上一版本号
  - 提供“查看版本历史”入口
  - 若存在可问答版本，可提供“去问答”入口

### 2.6 历史版本查看

- 旧版本仅对具备目标 `document` 管理权限的用户可见。
- 历史版本列表默认折叠。
- 上传新版本成功后，版本历史自动展开一次。
- 用户切历史版本后，列表保持展开状态。
- 查看历史版本时：
  - 主视图必须明确提示“当前不是最新版本”
  - 差异提示显示在主视图顶部
  - 查看历史版本不改变问答基线
  - 提供“返回最新版本”快捷入口

### 2.7 版本回退

- 版本回退与删除是不同动作。
- 回退会创建一个新的最新版本，不回拨旧指针。
- 仅允许回退到至少已 `INDEXED` 的历史版本。
- 当前最新版本不能作为回退目标。
- 入口只放在详情页的版本历史列表中，针对具体历史版本提供“回退为最新版本”动作。
- 仅对具备目标 `document` 管理权限的用户开放。
- 回退确认需要：
  - 二次确认
  - 明确提示“会创建新的最新版本”
  - 明确提示问答基线可能变化
  - 不要求输入 `documentId`
- 回退成功后：
  - 新版本初始状态为 `UPLOADED`
  - 重新走处理链路
  - 若未 `INDEXED`，问答回退到最近一个已 `INDEXED` 版本
  - 若最终 `FAILED`，仍保留为当前最新版本
  - 详情页给稳定结果提示
  - 提示中展示新最新版本号与回退目标版本号

回退后的历史展示规则：

- 新最新版本标记：
  - `最新`
  - `回退产生`
- 回退目标版本标记：
  - `曾被回退为最新版本`
- 回退产生的新版本来源文件名，跟随回退目标版本文件名

### 2.8 问答基线

- 详情页主视图始终跟随当前最新版本，即使最新版本处于 `UPLOADED` / `INGESTING` / `FAILED`。
- 当最新版本尚未 `INDEXED` 时：
  - 问答继续使用最近一个已 `INDEXED` 的版本
  - 详情页与问答入口都要明确提示“当前问答使用的不是最新版本”
- 失败的新最新版本仍可作为主视图，但问答仍使用最近一个已 `INDEXED` 版本。

### 2.9 接口契约方向

上传新版本：

- 独立接口，不复用通用上传
- 方向：`POST /api/v1/documents/{documentId}/versions`
- 请求除文件外，不再额外要求业务字段
- 但需要带 `expectedLatestVersionNumber` 一类乐观并发校验字段

版本回退：

- 针对具体历史版本发动作
- 方向：`POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback`
- 请求体可为空
- 同样带 `expectedLatestVersionNumber`

成功响应不再沿用最小 `documentId + status` 模式。  
版本治理类成功响应至少应支持：

- `documentId`
- `versionNumber`
- `status`
- `latestVersionNumber`
- `canAskNow`
- `askableVersionNumber`
- `versionOriginType`
- `versionResultType`

上传新版本成功响应至少应再含：

- `previousVersionNumber`

版本回退成功响应至少应再含：

- `rollbackFromVersionNumber`

同内容复用成功分支还应显式支持：

- `versionCreated = false`
- `reusedLatestVersionNumber`
- `versionResultType = REUSED_IDENTICAL_CONTENT`

### 2.10 错误码、审计与前端反馈

版本治理错误响应：

- 保留 HTTP 状态 + 业务错误码 + message

至少覆盖：

- `VERSION_UPLOAD_NOT_ALLOWED_STATUS`
- `VERSION_UPLOAD_NO_MANAGE_PERMISSION`
- `VERSION_UPLOAD_DOCUMENT_NOT_FOUND`
- `VERSION_ROLLBACK_NOT_ALLOWED_STATUS`
- `VERSION_ROLLBACK_NO_MANAGE_PERMISSION`
- `VERSION_ROLLBACK_TARGET_NOT_INDEXED`
- `VERSION_ROLLBACK_TARGET_IS_LATEST`
- `VERSION_ROLLBACK_DOCUMENT_NOT_FOUND`
- `VERSION_ROLLBACK_VERSION_NOT_FOUND`
- `VERSION_ROLLBACK_SOURCE_FILE_MISSING`
- `VERSION_CONFLICT_STALE_LATEST_VERSION`

前端反馈规则：

- 上传新版本权限拒绝：明确提示“你没有管理该文档版本的权限”
- 版本回退权限拒绝：明确提示“你没有回退该文档版本的权限”
- 当前阶段统一后续指引：`联系管理员`
- 状态不允许时，文案应写清：
  - 上传新版本只允许在 `INDEXED` / `FAILED`
  - 回退只允许对已形成可用内容的版本
- 并发冲突时，明确提示：
  - “当前最新版本已变化，请刷新详情后重试”

审计事件：

- 上传新版本、版本回退都需独立审计事件
- 事件名采用动作导向命名
- 结果通过结果字段表达，不塞进事件名
- 同内容复用未创建新版本，也要记审计

审计最少业务字段：

- `documentId`
- `versionNumber`
- `targetVersionNumber`
- `versionOriginType`
- `versionResultType`
- `expectedLatestVersionNumber`
- 实际 `latestVersionNumber`

正式列建议：

- `documentId`
- `versionNumber`
- `targetVersionNumber`
- `versionOriginType`
- `versionResultType`

扩展 JSON 建议：

- `expectedLatestVersionNumber`
- 实际 `latestVersionNumber`
- 业务错误码
- 服务端 message

### 2.11 并发与冲突总原则

- 同一 `document` 任一时刻只允许一个治理动作进入执行态。
- 治理动作包括：
  - 上传新版本
  - 版本回退
  - 重处理
  - 删除
- 请求可并发到达，但只有一个可成功抢到执行资格。
- 其它请求统一按状态变化或并发冲突拒绝。
- 不做后台排队。
- 问答不属于治理动作，不参与互斥，但仍受状态与问答基线约束。

状态下的动作规则：

- `UPLOADED` / `INGESTING`
  - 禁止上传新版本
  - 禁止版本回退
  - 禁止删除
- `FAILED`
  - 可见恢复入口：上传新版本 / 版本回退 / 重处理
  - 但三者互斥，任一启动后其它立即失效
- `DELETING`
  - 禁止治理动作
  - 禁止问答
- `DELETED`
  - 禁止治理动作
  - 禁止问答
  - 状态查询仍可返回 `DELETED`
  - 管理权限用户仍可查看历史版本，仅用于审计/治理追溯

## 3. 本轮尚未同步的文档

当前主要落盘文档：

- [CONTEXT.md](D:/Code/project/my-AI-ingest/CONTEXT.md)

后续仍建议同步：

- [docs/04-api-contract.yaml](D:/Code/project/my-AI-ingest/docs/04-api-contract.yaml)
  - 新增上传新版本接口
  - 新增版本回退接口
  - 扩展成功响应 DTO
  - 扩展业务错误码

- [docs/03-architecture.md](D:/Code/project/my-AI-ingest/docs/03-architecture.md)
  - `document` / `document version` / `processing attempt` 三层关系
  - 最新版本投影与版本事实边界

- `docs/adr/` 下新增 ADR
  - 主题建议：
    - `document-version-chain-model`
    - `linear-versioning-and-rollback-semantics`

## 4. 仍未收敛的主题

建议下一轮优先继续：

### 4.1 问答引用与版本提示细节

还没完全定的点：

- 引用卡片里版本号、更新时间、当前问答基线怎么写
- 当问答使用的不是最新版本时，问答页提示具体如何组织
- 新版本一旦 `INDEXED` 后，问答基线何时切换
- 回退成功后问答页如何同步提示变化

### 4.2 版本差异与历史列表展示细节

还没完全定的点：

- 差异摘要怎么生成
- 各类标记如何排布：
  - `最新`
  - `回退产生`
  - `曾被回退为最新版本`
  - `可问答`
- `FAILED` 摘要长度和展开方式

### 4.3 实施拆分

设计层基本成熟后，建议进入：

- API 合同落地
- DB migration 设计
- 领域模型拆分
- FE 页面与状态管理改造
- 审计接入

## 5. 建议下一次 session 使用的 skill

- `grill-with-docs`
  - 继续收敛“问答引用与版本提示细节”

之后建议：

- `to-issues`
  - 把已定方案拆成可实现任务

- `improve-codebase-architecture`
  - 开始真正拆 `document` / `document version` / `processing attempt` 时使用
