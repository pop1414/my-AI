# GitHub Issues 快照：文档版本链

## 拉取信息

- 更新时间：2026-05-15
- 拉取方式：基于当前仓库实现、本地回归结果与 GitHub issue 状态更新；#1、#2、#3、#4、#5、#6、#7、#8、#9、#10、#11、#12、#19、#20 已关闭，版本历史只读前后端基线、上传新版本前后端闭环、版本回退前后端链路、删除与列表版本语义前后端闭环、QA 版本链前后端闭环、版本治理审计与冲突后端闭环已完成
- 仓库：`pop1414/my-AI`
- 范围：文档版本链相关 issues；#1、#2、#3、#4、#5、#6、#7、#8、#9、#10、#11、#12、#19、#20 已完成关闭

## 收口状态

- #1 已按“基础版本链后端能力”完成并关闭。
- #2 已按“文档详情页版本历史交互确认”完成并关闭，交互确认产物见 `document-detail-version-history-interaction-confirmation.md`。
- #1 保留的兼容式 seam 已由 #19 收口：生产读路径切到 `latest projection + ingest_document_versions`，并补齐 guard 与 ADR。
- #20 已按“版本历史只读后端查询接口”完成并关闭：`GET /api/v1/documents/{documentId}/versions` 提供 #3 所需稳定后端契约。
- #3 已按“文档详情页版本历史前端只读视图”完成并关闭，收口说明见 `document-detail-version-history-frontend-readonly-closure.md`。
- #4 已按“上传新版本后端链路与契约”完成并关闭：`POST /api/v1/documents/{documentId}/versions` 提供 #5 所需稳定后端契约。
- #5 已按“上传新版本前端交互与结果提示”完成并关闭，收口说明见 `document-version-upload-frontend-closure.md`。
- #6 已按“版本回退后端链路与契约”完成并关闭：`POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback` 提供 #7 所需稳定后端契约。
- #7 已按“版本回退前端交互与结果提示”完成并关闭，收口说明见 `document-version-rollback-frontend-closure.md`。
- #8 已按“删除与列表页适配版本语义后端”完成并关闭，收口说明见 `document-delete-list-version-semantics-backend-closure.md`。
- #9 已按“删除确认与列表页版本语义前端”完成并关闭，收口说明见 `document-delete-list-version-semantics-frontend-closure.md`。
- #10 已按“qa 可问答版本选择与引用版本化后端”完成并关闭：`POST /api/v1/qa/ask` 已返回版本化引用字段与 `staleReferences` 汇总。
- #11 已按“问答页版本提示与引用版本展示前端”完成并关闭；收口说明见 `qa-reference-version-frontend-closure(#11).md`。
- #12 已按“版本治理审计与冲突收口后端”完成并关闭；收口说明见 `document-version-governance-audit-conflict-closure(#12).md`。

## 总览

| Issue | 标题 | 状态 | 标签 | 阻塞 |
| --- | --- | --- | --- | --- |
| [#1](https://github.com/pop1414/my-AI/issues/1) | document / document version 基础版本链后端落地 | CLOSED | `ready-for-agent` | 无 |
| [#2](https://github.com/pop1414/my-AI/issues/2) | 文档详情页版本历史交互确认 | CLOSED | `ready-for-human` | #1 已完成 |
| [#3](https://github.com/pop1414/my-AI/issues/3) | 文档详情页版本历史前端只读视图 | CLOSED | `ready-for-agent` | #1 已完成, #2 已完成, #20 已完成 |
| [#4](https://github.com/pop1414/my-AI/issues/4) | 上传新版本后端链路与契约 | CLOSED | `ready-for-agent` | #1 已完成, #19 已完成 |
| [#5](https://github.com/pop1414/my-AI/issues/5) | 上传新版本前端交互与结果提示 | CLOSED | `ready-for-agent` | #4 已完成 |
| [#6](https://github.com/pop1414/my-AI/issues/6) | 版本回退后端链路与契约 | CLOSED | `ready-for-agent` | #1 已完成, #19 已完成 |
| [#7](https://github.com/pop1414/my-AI/issues/7) | 版本回退前端交互与结果提示 | CLOSED | `ready-for-agent` | #3 已完成, #6 已完成 |
| [#8](https://github.com/pop1414/my-AI/issues/8) | 删除与列表页适配版本语义后端 | CLOSED | `ready-for-agent` | #1 已完成, #19 已完成 |
| [#9](https://github.com/pop1414/my-AI/issues/9) | 删除确认与列表页版本语义前端 | CLOSED | `ready-for-agent` | #8 已完成 |
| [#10](https://github.com/pop1414/my-AI/issues/10) | qa 可问答版本选择与引用版本化后端 | CLOSED | `ready-for-agent` | #1 已完成, #19 已完成 |
| [#11](https://github.com/pop1414/my-AI/issues/11) | 问答页版本提示与引用版本展示前端 | CLOSED | `ready-for-agent` | #10 已完成 |
| [#12](https://github.com/pop1414/my-AI/issues/12) | 版本治理审计与冲突收口后端 | CLOSED | `ready-for-agent` | #4 已完成, #6 已完成, #8 已完成 |
| [#19](https://github.com/pop1414/my-AI/issues/19) | 清理 document 主表旧版本事实字段与读写边界 | CLOSED | `ready-for-agent` | #1 已完成 |
| [#20](https://github.com/pop1414/my-AI/issues/20) | 版本历史只读后端查询接口 | CLOSED | `ready-for-agent` | #1 已完成, #2 已完成 |

## 建议执行顺序

1. #1 已完成关闭；后续不再把基础版本链能力作为 blocker。
2. #19 已完成关闭；后续后端任务默认以 `latest projection + ingest_document_versions` 为生产读路径，不再新增对主表旧版本事实列的读取依赖。
3. #2 已完成关闭；后续前端与后端实现以交互确认文档为准。
4. #20 已完成关闭，#3 已基于 `GET /api/v1/documents/{documentId}/versions` 完成版本历史前端只读视图。
5. #4 与 #5 已完成关闭，上传新版本前后端闭环已具备入口、提交流程、稳定结果提示和 E2E 覆盖。
6. #6 与 #7 已完成关闭；版本回退前后端链路已形成稳定闭环，前端回退动作、确认交互、稳定结果提示与 E2E 覆盖均已落地。
7. #8 与 #9 已完成关闭；删除与列表版本语义已形成前后端闭环，后续无需再保留删除确认与列表反馈 blocker。
8. #10 已完成关闭，QA 后端已覆盖按文档独立选择可问答版本、版本化引用字段、`staleReferences` 汇总和检索阶段权限边界。
9. #11 已完成关闭，问答页已接入引用版本卡片、stale reference 顶部提示、无引用兜底规则和控制台顶部入口；收口说明见 `qa-reference-version-frontend-closure(#11).md`。
10. #12 已完成关闭；上传新版本、版本回退、删除、重处理的互斥、业务错误码、审计事件和竞争场景测试已收口，说明见 `document-version-governance-audit-conflict-closure(#12).md`。

## Issue 摘要

### #1 document / document version 基础版本链后端落地

状态：已关闭，按“基础版本链后端能力”完成收口。

目标是把当前由单条 `document` 记录承载全部版本事实的实现，演进为 `document` 主表 + `document version` 子表。该任务只交付后端基础能力，不包含新的前端治理交互。

验收重点：

- `document` 主表只保留稳定身份与最新版本轻量投影。
- `document version` 承载版本级文件事实、处理事实与来源事实。
- 现有上传、状态查询、文档列表、文档详情查询在版本链落地后继续可用。
- 后端可以稳定查询最新版本号、最新状态、最新来源文件名与最新版本来源类型。
- `askable` 不持久化，当前可问答版本继续由规则推导。
- 补齐 schema、持久化 adapter 与回归测试。

收口说明：

- 当前实现采用兼容式演进，主表旧版本事实字段仍存在；该风险由 #19 承接。
- 版本历史只读后端契约不纳入 #1，已由 #20 承接。
- #20 已完成关闭，版本历史只读契约现以 `GET /api/v1/documents/{documentId}/versions` 为准。

### #2 文档详情页版本历史交互确认

状态：已关闭，按“交互确认完成”收口。

目标是由人工确认文档详情页中版本历史相关的前端交互与信息结构。该任务不做实现，产出应能直接指导后续前端开发。

验收重点：

- 明确版本历史列表的默认展示字段、排序方式、折叠规则和标记体系。
- 明确历史版本查看态与最新版本主视图的区分方式。
- 明确差异摘要区在详情页中的位置、摘要粒度和默认比较对象。
- 明确上传新版本、版本回退完成后的稳定结果提示区信息结构。

收口说明：

- 交互确认产物位于 `docs/runbooks/plans/document-version-chain/document-detail-version-history-interaction-confirmation.md`。
- #20 已补齐 `versionHistory[]` 只读后端查询契约。
- #3 前端只读视图可基于 #20 契约推进，不应基于 mock 数据或临时复用 status/list 接口进入正式实现。

### #3 文档详情页版本历史前端只读视图

状态：已关闭，按“文档详情页版本历史前端只读视图”完成收口。

目标是在版本链后端和交互确认完成后，实现文档详情页的版本历史只读视图。不包含上传新版本与版本回退动作。

验收重点：

- 展示版本号、来源文件名、上传人、上传时间、状态与当前是否可用于问答。
- 没有目标 document 管理权限的普通用户看不到旧版本视图。
- 历史版本查看态明确提示当前不是最新版本，并提供返回最新版本入口。
- 历史版本查看不改变当前问答基线。
- 前端测试覆盖版本历史只读展示与历史版本查看态。

收口说明：

- 详情页通过 `GET /api/v1/documents/{documentId}/versions` 获取版本历史，前端 API client 使用稳定 DTO 校验响应。
- 版本历史列表已展示版本号、来源文件名、上传人、更新时间、状态、最新版本标记、当前查看标记和当前问答基线标记。
- 历史版本查看态通过 `?version=N` 表达，页面展示历史查看提示、差异摘要和返回最新版本入口。
- 后端返回 `403` 时，前端不渲染旧版本历史列表，展示“旧版本视图不可见”。
- 已通过 `npm.cmd run build` 与 `document-version-history.spec.ts` 端到端测试。
- 收口说明位于 `docs/runbooks/plans/document-version-chain/document-detail-version-history-frontend-readonly-closure.md`。

### #4 上传新版本后端链路与契约

状态：已关闭，按“上传新版本后端链路与契约”完成收口。

目标是交付上传新版本的完整后端能力。用户可针对既有 `document` 发起上传新版本，系统以当前最新版本作为基线推进线性版本链。

验收重点：

- 提供绑定既有 `document` 上下文的上传新版本接口，不复用通用上传接口。
- 仅允许在当前最新版本为 `INDEXED` 或 `FAILED` 时发起上传新版本。
- 支持 `expectedLatestVersionNumber` 一类乐观并发校验。
- 新文件与当前最新版本同内容时按成功复用处理，不创建新版本。
- 成功结果 DTO 返回 `documentId`、新版本号或复用结果、上一版本号、当前最新版本号、当前可问答版本号等上下文。
- 后端测试覆盖权限、状态门禁、并发冲突与同内容复用。

收口说明：

- 已提供 `POST /api/v1/documents/{documentId}/versions`，接口阶段在 `docs/04-api-contract.yaml` 中标记为 `implemented`。
- 接口请求使用 `multipart/form-data`，路径参数绑定目标 `documentId`，请求字段包含 `file` 与 `expectedLatestVersionNumber`。
- 应用服务统一校验目标 document 存在性、文档管理权限、`expectedLatestVersionNumber` 与当前 latest 是否一致、当前最新版本状态是否为 `INDEXED` / `FAILED`。
- 新文件与当前最新版本 `fileHash` 一致时返回 `versionCreated = false` 与 `versionResultType = REUSED_IDENTICAL_CONTENT`，不创建新版本也不消耗新的 `versionNumber`。
- 创建新版本时返回 `documentId`、`versionNumber`、`previousVersionNumber`、`latestVersionNumber`、`askableVersionNumber`、`canAskNow`、`status` 与 `versionOriginType` 等上下文。
- 源文件版本化落盘由应用服务编排，先保存源文件，再追加 DB 版本事实，避免 latest 指向缺失源文件。
- 已覆盖应用服务与 REST controller 回归测试，包括权限拒绝、状态门禁、乐观并发冲突、CAS 冲突、同内容复用、源文件保存失败不追加版本、业务错误码透传。

### #5 上传新版本前端交互与结果提示

状态：已关闭，按“上传新版本前端交互与结果提示”完成收口。

目标是在文档详情页接入上传新版本入口、提交流程和稳定结果提示。

验收重点：

- 仅在用户具备管理权限且当前最新版本状态允许时显示上传新版本入口。
- 上传新版本流程锁定当前 knowledge base，不允许切换到其他 knowledge base。
- 成功后页面切换到新的最新版本视图，并显示稳定结果提示区。
- 稳定结果提示区展示新版本号与上一版本号，并提供“查看版本历史”和“去问答”入口。
- 命中同内容复用时明确提示未生成新版本，且仍停留在原最新版本。
- 前端测试覆盖入口显示规则、成功提示与复用分支。

收口说明：

- 已在文档详情页接入上传新版本入口，仅当当前查看最新版本且最新版本状态为 `INDEXED` / `FAILED` 时展示。
- 上传 Modal 锁定当前 document 上下文，提交流程只携带 `file` 与 `expectedLatestVersionNumber`，不携带 `kbId`。
- 创建新版本成功后，页面清除历史版本 query，回到最新版本视图，并刷新版本历史。
- 稳定结果提示区展示版本号、上一版本号、最新版本号、问答基线版本号与当前状态，并提供“查看版本历史”“去问答”“关闭提示”等后续动作。
- 同内容复用时返回信息态提示，明确未创建新版本，并停留在原最新版本。
- 同步完成可访问性和响应式收口：上传结果 live region、标题层级、长 `documentId`、结果事实区自适应网格、正文阅读未接入状态说明。
- 已通过 `npm.cmd run build` 与 `document-version-history.spec.ts` 端到端测试；收口说明位于 `docs/runbooks/plans/document-version-chain/document-version-upload-frontend-closure.md`。

### #6 版本回退后端链路与契约

状态：已关闭，按“版本回退后端链路与契约”完成收口。

目标是交付版本回退的完整后端能力。回退应创建一个新的最新版本并重新进入 ingest 处理链路，而不是回拨旧版本指针。

验收重点：

- 提供绑定目标历史版本的版本回退接口，并要求乐观并发校验。
- 仅允许将曾经成功形成可用内容的历史版本作为回退目标，且当前最新版本不能作为回退目标。
- 回退创建新的最新版本，版本来源类型为 `ROLLBACK`，并记录回退来源版本号。
- 回退产生的新最新版本初始状态为 `UPLOADED`，并重新进入处理链路。
- 成功结果 DTO 返回新最新版本号、回退目标版本号、当前最新版本号与当前可问答版本号。
- 后端测试覆盖目标版本校验、并发冲突、回退后状态推进与失败保留最新版本语义。

收口说明：

- 已提供 `POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback`，接口阶段在 `docs/04-api-contract.yaml` 中标记为 `implemented`。
- 接口以路径参数绑定目标历史版本，并通过 `expectedLatestVersionNumber` 查询参数执行 latest 乐观并发校验。
- 应用服务统一校验目标 document 存在性、文档管理权限、当前 latest 状态、目标版本是否为非 latest 且已 `INDEXED`。
- 回退创建新的 latest 版本，`versionOriginType = ROLLBACK`，`rollbackFromVersionNumber` 指向被回退目标版本，新版本初始状态为 `UPLOADED`。
- 成功响应返回 `documentId`、新建 `versionNumber`、`rollbackFromVersionNumber`、`latestVersionNumber`、`askableVersionNumber`、`canAskNow`、`status` 与 `versionOriginType`。
- 版本源文件保存收口为“先 DB CAS 写入版本事实，再保存版本化源文件”；保存失败触发事务回滚，避免 latest 指向缺失源文件。
- 本地版本源文件存储对同版本同名文件做内容一致性校验，不一致时按 `VERSION_CONFLICT_STALE_LATEST_VERSION` 处理，避免版本事实与实际文件字节错位。
- 回退来源文件缺失返回稳定错误码 `VERSION_ROLLBACK_SOURCE_FILE_MISSING`。
- 已覆盖应用服务、REST controller、本地源文件存储与 JDBC repository 回归测试，包括目标版本校验、权限拒绝、状态门禁、乐观并发冲突、源文件缺失、源文件内容冲突和失败后 latest projection 语义。

### #7 版本回退前端交互与结果提示

状态：已关闭，按“版本回退前端交互与结果提示”完成收口。

目标是在版本历史前端视图中接入“回退为最新版本”动作、确认交互与成功后的稳定结果提示。

验收重点：

- 版本历史列表中对可回退目标展示“回退为最新版本”入口。
- 回退确认提示该操作会创建新的最新版本，并可能改变问答基线。
- 回退成功后展示稳定结果提示区，包含新最新版本号与回退目标版本号。
- 当回退产生的新最新版本尚未 `INDEXED` 时，提示当前问答暂时仍使用最近一个已 `INDEXED` 的版本。
- 回退成功后版本历史刷新，并展示“最新”“回退产生”“曾被回退为最新版本”等标记。
- 前端测试覆盖入口可见性、确认交互和结果提示。

收口说明：

- 已在版本历史列表中仅对可回退目标展示“回退为最新版本”入口；当前最新版本与不满足条件的历史版本不显示该动作。
- 已提供回退确认 Modal，明确提示该操作会创建新的最新版本，并可能改变当前问答基线。
- 回退成功后，页面清除历史版本 query、切回最新版本主视图，并刷新版本历史与主概览。
- 稳定结果提示区展示 `latestVersionNumber`、`rollbackFromVersionNumber`、`status`、`askableVersionNumber` 等关键事实。
- 当回退产生的新最新版本尚未 `INDEXED` 时，结果提示区明确提示当前问答暂时仍使用最近一个已 `INDEXED` 的版本。
- 已通过 `npm.cmd run build` 与 `document-version-history.spec.ts` 端到端测试；收口说明位于 `docs/runbooks/plans/document-version-chain/document-version-rollback-frontend-closure.md`。

### #8 删除与列表页适配版本语义后端

目标是把删除、文档列表和文档详情查询适配到版本链语义。删除仍终止整个 `document` 资产，而不是某个历史版本。

完成状态：

- 已完成并关闭，收口说明见 `document-delete-list-version-semantics-backend-closure.md`。
- 删除仍针对整个 `document` 资产，不提供删除单个历史版本路径。
- 文档列表与详情主视图已锚定 latest projection 与最新版本事实。
- 删除完成后同内容重新上传生成新的 `documentId`；`DELETING` 期间同内容上传仍复用原 `documentId`，与现有唯一索引和删除失败回滚语义保持一致。
- 后端文档明确表达旧 `documentId` 上的文档级授权不会自动继承到新 document。
- 删除用例拒绝 `UPLOADED/INGESTING/DELETING` 执行态，配合上传新版本、版本回退、重处理的状态与 CAS 校验保持互斥。

### #9 删除确认与列表页版本语义前端

目标是在前端把删除确认与列表页适配到版本链语义。用户通过结构化确认 Modal 删除整个 `document` 资产，并在列表和详情页看到与最新版本语义一致的反馈。

状态：已关闭，按“删除确认与列表页版本语义前端”完成收口。

验收重点：

- 删除确认要求用户手动输入完整 `documentId` 才能确认删除。
- 删除确认文案提示同内容重新上传会生成新的 `documentId`，且新文档不继承旧文档级授权。
- 删除成功后详情页返回列表，并尽量保留原筛选和分页上下文。
- 删除成功后的稳定结果提示区展示旧 `documentId` 与后续引导。
- 文档列表即时反映删除结果，并继续展示基于当前最新版本的状态与文件名。
- 前端测试覆盖删除确认、删除后返回列表与结果提示。

收口说明：

- 已新增结构化删除确认 Modal，要求输入完整 `documentId` 后才能确认删除整个 `document` 资产。
- 删除确认与删除成功结果提示均明确说明：同内容重新上传会生成新的 `documentId`，新 document 不继承旧 document 级授权。
- 文档列表页通过 URL 保存筛选与分页上下文，删除成功后刷新列表并展示旧 `documentId` 与后续上传入口。
- 文档详情页新增删除入口，删除成功后返回 `returnTo` 指向的文档列表上下文，并携带稳定结果提示。
- 文档列表展示 latest version 语义字段，包括最新文件名、最新版本号、最新版本来源和当前最新状态。
- 已完成 `web-design-guidelines` 审阅收口：删除确认输入、旧删除页输入、筛选控件、列表跳转 Link 和异步结果提示均补齐可访问性细节。
- 已通过 `npm.cmd run build` 与 `document-version-history.spec.ts` 端到端测试；收口说明位于 `docs/runbooks/plans/document-version-chain/document-delete-list-version-semantics-frontend-closure.md`。

### #10 qa 可问答版本选择与引用版本化后端

状态：已关闭，按“qa 可问答版本选择与引用版本化后端”完成收口。

目标是把 qa 接入版本链语义。问答对每个 document 独立选择当前可问答版本，在最新版本尚不可问答时回退到最近一个已 `INDEXED` 的版本。

验收重点：

- qa 对每个命中的 document 独立决定当前可问答版本。
- 当某个 document 的最新版本尚未 `INDEXED` 时，继续使用最近一个已 `INDEXED` 的版本。
- 每条引用返回来源版本号、来源更新时间、是否为最新版本、当前最新版本号和来源文件名等字段。
- 顶层响应返回 stale reference 汇总字段，并遵守“没有引用时不展示版本提示”的语义前提。
- 保持 ADR-0005 的权限边界，不允许先越权召回再裁剪引用。
- 后端测试覆盖可问答版本选择、版本字段输出与 stale reference 汇总。

收口说明：

- 问答链路已先按当前用户、目标知识库和文档级覆盖权限查询可问答范围，再将 `documentId + versionNumber` 成对条件下推到向量检索。
- 每个 `document` 独立选择最近一个已 `INDEXED` 版本作为当前可问答版本，最新版本未可问答时不会阻塞该文档的问答。
- `references[]` 已返回 `sourceVersionNumber`、`sourceUpdatedAt`、`isLatestVersion`、`latestVersionNumber` 与 `sourceFilename`。
- 存在引用时返回 `staleReferences` 汇总；无引用时返回空汇总，前端不应展示版本提示。
- 联调发现的 PGVector `ISNULL` 兼容问题已修复：版本过滤只生成 PGVector 支持的等值表达式，并兼容 `documentVersionNumber`、`splitVersion=version-{versionNumber}-v1` 与历史 `splitVersion=v1`。

### #11 问答页版本提示与引用版本展示前端

状态：已关闭，按“问答页版本提示与引用版本展示前端”完成收口。

目标是在问答页接入引用版本信息和 stale reference 提示。

验收重点：

- 引用卡片展示来源版本号、来源更新时间与来源文件名。
- 当引用不是对应 document 的最新版本时，卡片提示当前最新版本号。
- 问答页顶部仅在至少存在一条 stale reference 时展示版本提示。
- 一次问答没有任何文档引用、仅返回模型兜底内容时，不展示版本提示。
- 前端测试覆盖引用卡片字段展示与顶部版本提示显示规则。

收口说明：

- 问答 API client 已接入 #10 的版本化响应 schema，包括 `references[]` 版本字段与 `staleReferences` 顶层汇总。
- 问答页引用来源已升级为卡片展示，展示来源文件名、`documentId`、分块序号、来源版本号与来源更新时间。
- stale 引用卡片已明确提示当前最新版本号；顶部 stale 提示只在 `hasStaleReferences = true` 且存在引用时展示。
- 无引用兜底回答展示“无命中”空态，不展示版本提示。
- 控制台顶部已补充“问答控制台”快捷入口，避免入口只依赖侧边栏。
- 已通过 `npm.cmd run build` 与 `qa-reference-version.spec.ts` 端到端测试；收口说明位于 `docs/runbooks/plans/document-version-chain/qa-reference-version-frontend-closure(#11).md`。

### #12 版本治理审计与冲突收口后端

状态：已关闭，按“版本治理审计与冲突收口后端”完成收口。

目标是为版本治理动作补齐审计与冲突收口。上传新版本、版本回退、删除、重处理等治理动作应具备一致的互斥、错误码和审计上下文。

验收重点：

- 上传新版本、版本回退、删除、重处理在同一 document 上的互斥执行态规则统一落地。
- 并发冲突返回明确业务错误码，并能区分状态变化与过期页面导致的冲突。
- 上传新版本与版本回退分别写入独立审计事件。
- 审计事件包含版本号、目标版本号、版本来源类型、结果类型与乐观并发上下文。
- 失败场景的业务错误码与服务端 message 进入审计扩展信息。
- 审计与冲突测试覆盖上传新版本、版本回退、删除和重处理之间的竞争场景。

收口说明：

- 上传新版本与版本回退均通过 `expectedLatestVersionNumber`、latest 状态门禁和仓储 CAS 创建线性新版本，仅允许 latest 为 `INDEXED` / `FAILED` 时发起。
- 删除拒绝 `UPLOADED`、`INGESTING`、`DELETING` 执行态；重处理只允许 `INDEXED` / `FAILED`，对 `INGESTING` 统一返回状态变化冲突。
- 并发冲突错误码已区分：`VERSION_CONFLICT_STALE_LATEST_VERSION` 表示页面基于旧 latest 提交，`VERSION_CONFLICT_STATE_CHANGED` 表示 latest 未变但状态在 CAS 窗口内变化。
- 上传新版本审计事件 `DOCUMENT_VERSION_UPLOAD_REQUESTED` 与版本回退审计事件 `DOCUMENT_VERSION_ROLLBACK_REQUESTED` 已独立记录版本号、目标版本号、来源类型、结果类型和乐观并发上下文。
- 删除与重处理分别记录 `DOCUMENT_DELETE_REQUESTED`、`DOCUMENT_REPROCESS_REQUESTED`，失败审计扩展信息包含 `errorCode` 与 `errorMessage`。
- 审计写入采用 best-effort 策略，审计仓储异常不反向改变主业务结果。
- 已覆盖上传新版本、版本回退、删除、重处理的状态门禁、过期 latest、CAS 冲突、审计写入失败与失败上下文测试。
- 收口说明位于 `docs/runbooks/plans/document-version-chain/document-version-governance-audit-conflict-closure(#12).md`。

### #19 清理 document 主表旧版本事实字段与读写边界

状态：已关闭，按“主表旧版本事实字段读写边界收紧”完成收口。

目标是收紧 #1 兼容式演进留下的主表旧版本事实字段边界，避免后续实现继续把 `ingest_documents.file_hash`、`filename`、`file_size`、`status`、`processing_metadata` 等字段当作真实版本事实来源。

验收重点：

- 盘点当前所有读取或写入主表旧版本事实字段的生产路径。
- 可迁移的读取路径切换为 `ingest_document_versions` 或 latest projection。
- 暂时不能移除的兼容写入必须有注释和测试保护，明确只是迁移兼容。
- 增加回归测试，证明主链路以 latest projection / version 表为准。
- 补充 schema/read-model guard，避免后续新代码重新依赖旧主表字段。
- 给出后续物理删列迁移方案；若可安全删列，则在本 issue 内通过 Flyway 完成。

收口说明：

- 上传幂等查询已从 `ingest_documents.file_hash/status` 切换为 `ingest_document_versions.file_hash + ingest_documents.latest_status`。
- 知识库已索引文档计数已切换为 `doc.latest_status`，不再读取主表旧 `status`。
- `JdbcDocumentRepository` 已明确主表旧事实列仅作兼容镜像写入，不再作为新生产读路径事实源。
- `IngestSchemaVerifier` 已扩展到 version 事实列与 `file_hash` 查询索引校验。
- 已补充 `DocumentVersionReadBoundaryTest`、`KnowledgeBaseDocumentCountReadBoundaryTest` 与 ADR-0006，作为后续防回退护栏。

### #20 版本历史只读后端查询接口

状态：已关闭，按“版本历史只读后端查询接口”完成收口。

目标是补齐按 `documentId` 查询版本历史的后端契约，为 #3 文档详情页版本历史前端只读视图提供稳定数据来源。

验收重点：

- 提供版本历史列表后端接口或 use case，并返回稳定 DTO。
- 返回字段至少包含 `documentId`、`versionNumber`、`versionOriginType`、`rollbackFromVersionNumber`、`filename`、`fileSize`、`status`、`failureReason`、`createdAt`、`updatedAt`、`isLatestVersion`、`isAskableVersion`。
- 排序规则稳定，并在契约中明确。
- 权限边界清楚；如果旧版本查看要求管理权限，由后端统一校验。
- `isAskableVersion` 由规则推导，不持久化为字段。
- 历史版本查询不改变 latest projection，也不改变 QA 可问答基线。
- 后端测试覆盖权限、排序、latest 标记、askable 推导、rollback 来源字段和缺失 document 场景。

收口说明：

- 已提供 `GET /api/v1/documents/{documentId}/versions`，接口阶段在 `docs/04-api-contract.yaml` 中标记为 `implemented`。
- 响应 DTO 返回 `documentId`、`sort = versionNumber,DESC` 与 `versions[]`，版本项覆盖 #20 要求的核心字段。
- 应用服务先校验目标 document 存在，再通过 `AuthorizationService.requireCanManageDocument` 统一校验文档管理权限。
- JDBC 读仓储从 `ingest_document_versions` 读取版本事实，并结合 `ingest_documents.latest_version_number` 推导 latest 标记。
- `isAskableVersion` 由领域读模型按“最近一个已 `INDEXED` 版本”推导，不写入数据库。
- 已覆盖应用服务、领域读模型、JDBC repository 与 REST controller 测试。

## 审阅提示

- 先核对总览表中的标签与阻塞关系是否和 GitHub Issues 当前状态一致，尤其是 #1/#2/#3/#10/#11/#12/#19/#20 均已关闭。
- 再按 #19、#20、#3、后端功能 issues、后续前端 issues、#12 的顺序审阅，确认执行顺序是否符合“后端契约先稳定，前端再接入”的原则。
- 特别检查 #19 是否足以阻止后续代码继续依赖主表旧版本事实字段，#20 是否足以支撑 #3 不靠 mock 或临时接口推进，#10/#11 是否足以支撑问答版本化引用、stale 提示和 PGVector 过滤兼容，#12 是否足以覆盖治理动作互斥、冲突分类与审计上下文。
- 最后检查每个 issue 的验收重点是否足够指导后续领取任务；如果 GitHub issue 后续有更新，应重新拉取并更新本快照。
