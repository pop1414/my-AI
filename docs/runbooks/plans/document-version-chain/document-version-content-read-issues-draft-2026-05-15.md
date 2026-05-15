# 本地 Issue 草案：文档版本正文读取专项

## 生成信息

- 生成日期：2026-05-15
- 来源 PRD：`docs/runbooks/plans/document-version-chain/document-version-content-read-prd.md`
- 关联 PRD Issue：[#21 PRD: 文档版本正文读取专项](https://github.com/pop1414/my-AI/issues/21)
- 当前状态：已上传 GitHub Issues
- 目标标签：上传 GitHub 时建议使用 `ready-for-agent`

## 拆分原则

- 按 tracer bullet 思路拆分，每个 issue 都交付一条可验证的窄路径。
- 结合本仓库约定，执行顺序保持“后端契约与行为先稳定，再接前端，再做 E2E”。
- 每个 issue 都尽量避免依赖临时 mock 或平行 API，必须收敛到 `ADR-0005`、`ADR-0006` 和 OpenAPI 中已规划的正文读取接口。
- 本草案不直接创建 GitHub issue；后续上传时需要把本地编号替换成真实 issue 编号，并按依赖顺序发布。

## 总览

| 本地编号 | GitHub Issue | 标题 | 类型 | 标签 | 阻塞 |
| --- | --- | --- | --- | --- | --- |
| DVCR-01 | [#22](https://github.com/pop1414/my-AI/issues/22) | 版本正文 artifact storage port 与 key resolver 后端基线 | AFK | `ready-for-agent` | 无 |
| DVCR-02 | [#23](https://github.com/pop1414/my-AI/issues/23) | latest 正文读取后端端到端契约 | AFK | `ready-for-agent` | #22 |
| DVCR-03 | [#24](https://github.com/pop1414/my-AI/issues/24) | askable baseline 正文读取后端端到端契约 | AFK | `ready-for-agent` | #22, #23 |
| DVCR-04 | [#25](https://github.com/pop1414/my-AI/issues/25) | 显式版本正文读取后端权限与错误映射 | AFK | `ready-for-agent` | #22, #23 |
| DVCR-05 | [#26](https://github.com/pop1414/my-AI/issues/26) | 文档详情 latest 正文前端视图 | AFK | `ready-for-agent` | #23 |
| DVCR-06 | [#27](https://github.com/pop1414/my-AI/issues/27) | QA 引用侧栏 askable baseline 正文前端视图 | AFK | `ready-for-agent` | #24 |
| DVCR-07 | [#28](https://github.com/pop1414/my-AI/issues/28) | 管理者历史版本正文前端视图 | AFK | `ready-for-agent` | #25, #26 |
| DVCR-08 | [#29](https://github.com/pop1414/my-AI/issues/29) | 正文读取后端契约与错误映射收口 | AFK | `ready-for-agent` | #23, #24, #25 |
| DVCR-09 | [#30](https://github.com/pop1414/my-AI/issues/30) | 正文读取前端错误态与权限隐藏收口 | AFK | `ready-for-agent` | #26, #27, #28, #29 |
| DVCR-10 | [#31](https://github.com/pop1414/my-AI/issues/31) | 文档版本正文读取 E2E 专项验收 | AFK | `ready-for-agent` | #30 |

## 建议执行顺序

1. 先完成 DVCR-01，建立 version-level artifact 读取边界，避免后续 endpoint 直接依赖本地路径、MinIO SDK、源文件或 chunk。
2. 再完成 DVCR-02，打通 latest 正文读取的最小后端闭环，并完成 `CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING`、`CONTENT_TOO_LARGE` 的基础映射。
3. 然后完成 DVCR-03 与 DVCR-04，分别补齐 QA askable baseline 语义和显式历史版本权限语义。
4. 后端契约稳定后，按 DVCR-05、DVCR-06、DVCR-07 接前端三类视图。
5. DVCR-08 做后端契约、错误码和 OpenAPI 一致性收口。
6. DVCR-09 做前端错误态、权限隐藏和文案一致性收口。
7. 最后执行 DVCR-10，用 E2E 验证 `KB_READER` 与 `KB_MANAGER` 的关键端到端路径。

## Issue 草案

### DVCR-01 版本正文 artifact storage port 与 key resolver 后端基线

GitHub Issue：[#22](https://github.com/pop1414/my-AI/issues/22)

Type：AFK

Blocked by：None - can start immediately

User stories covered：18、19、20、21、22

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

建立版本正文读取的后端存储边界，让应用层可以通过稳定端口读取某个 `document version` 的 `cleaned.md`。该路径必须以 `workspaceId + documentId + versionNumber + artifactName` 定位版本级处理产物，并明确区分 `source/...` 与 `artifacts/...`。

该 issue 不需要暴露新的用户界面，也不需要完成三个 REST endpoint；它交付的是后续正文读取 endpoint 共享的深层模块和测试护栏。

#### Acceptance criteria

- [ ] 应用层通过版本处理产物存储端口读取 `cleaned.md`，不直接依赖本地路径、MinIO SDK 或源文件解析逻辑。
- [ ] artifact key 规则包含 `workspaceId`、`documentId`、`versionNumber` 和 artifact 名称。
- [ ] `source/...` 与 `artifacts/...` prefix 逻辑隔离，不混用源文件与处理产物。
- [ ] artifact 缺失能够被识别为稳定业务分支，供上层映射为 `CONTENT_ARTIFACT_MISSING`。
- [ ] 缺失 artifact 不触发源文件重解析，也不从 `vector_store` chunk 拼接正文。
- [ ] 后端测试覆盖 key 规则、prefix 隔离、缺失 artifact 和最大读取大小边界。

#### Blocked by

None - can start immediately

### DVCR-02 latest 正文读取后端端到端契约

GitHub Issue：[#23](https://github.com/pop1414/my-AI/issues/23)

Type：AFK

Blocked by：DVCR-01

User stories covered：8、13、14、15、16、17、23、24、25、26

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

实现 `GET /api/v1/documents/{documentId}/content` 的后端端到端能力。该接口读取目标 `document` 当前 latest version 的 `cleaned.md`，用于文档详情默认正文视图。

latest 正文读取必须忠实表达 latest 状态：latest `INGESTING` 且正文尚未生成时返回 `CONTENT_NOT_READY`，不能自动回退旧版本；latest `FAILED` 且已有 `cleaned.md` 时允许返回 failed latest 正文；`DELETED` document 不开放正文读取。

#### Acceptance criteria

- [ ] endpoint 返回 `DocumentContentResponse` 所需字段，包括 `documentId`、`versionNumber`、`latestVersionNumber`、`isLatestVersion`、`isAskableVersion`、`source`、`status`、`filename`、`createdAt`、`updatedAt`、`contentMarkdown`、`contentLength`、`truncated`。
- [ ] latest `INDEXED` 时返回 latest 正文，`source = LATEST`。
- [ ] latest `INGESTING` 且无 `cleaned.md` 时返回 `409` + `CONTENT_NOT_READY`，不回退旧版本。
- [ ] latest `FAILED` 且已有 `cleaned.md` 时返回 failed latest 正文，并保留失败状态。
- [ ] `DELETED` document 拒绝正文读取。
- [ ] 正文超出服务端读取上限时返回 `413` + `CONTENT_TOO_LARGE`，不静默截断。
- [ ] artifact 缺失时返回 `500` + `CONTENT_ARTIFACT_MISSING`。
- [ ] 后端应用服务和 REST controller 测试覆盖成功路径、状态分支、错误码和权限边界。

#### Blocked by

- DVCR-01

### DVCR-03 askable baseline 正文读取后端端到端契约

GitHub Issue：[#24](https://github.com/pop1414/my-AI/issues/24)

Type：AFK

Blocked by：DVCR-01、DVCR-02

User stories covered：1、2、3、6、13、22、23、24、25、26、27

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

实现 `GET /api/v1/documents/{documentId}/askable-content` 的后端端到端能力。该接口读取目标 `document` 当前 QA baseline version 的 `cleaned.md`，用于 QA 引用侧栏和回答依据核对。

该路径必须复用当前 QA 可问答版本选择语义：latest 已 `INDEXED` 时返回 latest；latest 尚未可问答但存在旧 `INDEXED` 时返回最近一个已 `INDEXED` 的 askable baseline；当前没有可问答版本时返回 `CONTENT_NOT_READY`。

#### Acceptance criteria

- [ ] endpoint 返回 `DocumentContentResponse`，其中 `source = ASKABLE_BASELINE`。
- [ ] latest `INDEXED` 时 `/content` 与 `/askable-content` 返回同一 latest version。
- [ ] latest `INGESTING` 且旧版本 `INDEXED` 时，`/askable-content` 返回旧 indexed askable baseline。
- [ ] latest `FAILED with cleaned.md` 时，`/askable-content` 返回最近一个 indexed askable baseline，而不是 failed latest。
- [ ] 当前没有可问答版本或 askable baseline 正文尚未生成时返回 `409` + `CONTENT_NOT_READY`。
- [ ] 普通 `KB_READER` 可读取 askable baseline 正文，但不能因此获得历史版本浏览能力。
- [ ] 后端测试覆盖 `WORKSPACE_ADMIN`、`KB_MANAGER`、`KB_CONTRIBUTOR`、`KB_READER` 和必要的 `KB_ASKER` 兼容分支。
- [ ] 后端测试明确验证 askable baseline 读取不改变后续 QA baseline。

#### Blocked by

- DVCR-01
- DVCR-02

### DVCR-04 显式版本正文读取后端权限与错误映射

GitHub Issue：[#25](https://github.com/pop1414/my-AI/issues/25)

Type：AFK

Blocked by：DVCR-01、DVCR-02

User stories covered：4、9、10、11、12、13、17、22、23、24、25、26

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

实现 `GET /api/v1/documents/{documentId}/versions/{versionNumber}/content` 的后端端到端能力。该接口用于管理人员查看指定 `document version` 的 `cleaned.md`，服务版本核对、历史版本查看和治理排查。

普通 `KB_READER` 不能通过显式 version URL 读取任意历史版本正文。具备目标 `document` 管理权限的用户可以读取指定版本正文，但读取历史版本不得改变 QA baseline。

#### Acceptance criteria

- [ ] endpoint 返回 `DocumentContentResponse`，其中 `source = EXPLICIT_VERSION`。
- [ ] 目标 document 不存在时返回 `404` + `DOCUMENT_NOT_FOUND`。
- [ ] 目标 version 不存在时返回 `404` + `VERSION_NOT_FOUND`。
- [ ] 普通 `KB_READER` 访问任意显式历史版本正文时返回 `403` + `VERSION_CONTENT_FORBIDDEN`。
- [ ] `WORKSPACE_ADMIN`、`KB_MANAGER` 和 `DOC_ALLOW_MANAGE` 用户可读取指定历史版本正文。
- [ ] `DELETED` document 拒绝正文读取，即使是管理人员也不返回正文。
- [ ] 指定版本正文未就绪、产物缺失、正文过大时分别映射到 `CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING`、`CONTENT_TOO_LARGE`。
- [ ] 后端测试覆盖显式版本读取、历史版本权限拒绝、`DOC_DENY` 覆盖和读取不改变 QA baseline。

#### Blocked by

- DVCR-01
- DVCR-02

### DVCR-05 文档详情 latest 正文前端视图

GitHub Issue：[#26](https://github.com/pop1414/my-AI/issues/26)

Type：AFK

Blocked by：DVCR-02

User stories covered：5、7、8、15、16、24、25、26

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

在文档详情页接入 latest 正文读取。默认正文区调用 `GET /api/v1/documents/{documentId}/content`，展示当前 latest version 的 Markdown 正文和版本上下文。

前端必须保持 latest 语义：latest 正文未生成时展示“正文处理中”空态，不自动切到旧版本；latest `FAILED` 且有正文时展示正文并提示失败状态；`403` 时不渲染正文内容。

#### Acceptance criteria

- [ ] 文档详情默认正文区读取 latest 正文 endpoint。
- [ ] 正文区展示版本号、最新版本号、状态、文件名和 Markdown 正文。
- [ ] latest `INGESTING` 或正文未生成时展示处理中空态，并提示稍后刷新。
- [ ] latest `FAILED` 且返回正文时展示失败状态提示和正文内容。
- [ ] `CONTENT_TOO_LARGE` 展示正文过大提示。
- [ ] `CONTENT_ARTIFACT_MISSING` 对管理人员提示“正文产物缺失，请重处理或联系管理员”，对普通读者提示“正文暂不可用”。
- [ ] `403` 不渲染正文区域内容。
- [ ] 普通 `KB_READER` 不显示版本历史、上传新版本、重处理、回退、删除、授权管理或源文件下载入口。
- [ ] 前端测试覆盖 latest 正文成功、处理中、失败正文、错误态和权限隐藏。

#### Blocked by

- DVCR-02

### DVCR-06 QA 引用侧栏 askable baseline 正文前端视图

GitHub Issue：[#27](https://github.com/pop1414/my-AI/issues/27)

Type：AFK

Blocked by：DVCR-03

User stories covered：1、2、3、5、6、25、26、27

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

在 QA 引用侧栏接入 askable baseline 正文读取。用户从回答引用打开正文时，前端调用 `GET /api/v1/documents/{documentId}/askable-content`，展示本次问答依据的可问答版本正文。

当返回版本不是 latest 时，侧栏顶部必须明确提示“当前问答基于 vN，最新版本为 vM”。该入口不能让普通 `KB_READER` 浏览任意历史版本。

#### Acceptance criteria

- [ ] QA 引用侧栏打开正文时调用 askable baseline endpoint。
- [ ] 侧栏顶部展示返回的 `versionNumber`、`latestVersionNumber` 和文件名。
- [ ] `isLatestVersion = false` 时展示“当前问答基于 vN，最新版本为 vM”提示。
- [ ] 侧栏只展示问答基线正文，不提供任意历史版本切换入口。
- [ ] `CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING`、`CONTENT_TOO_LARGE` 和 `403` 均有明确错误态。
- [ ] `403` 不渲染正文内容。
- [ ] 前端测试覆盖 askable baseline 版本提示、非 latest 提示、无权访问和错误态。

#### Blocked by

- DVCR-03

### DVCR-07 管理者历史版本正文前端视图

GitHub Issue：[#28](https://github.com/pop1414/my-AI/issues/28)

Type：AFK

Blocked by：DVCR-04、DVCR-05

User stories covered：4、9、10、11、12、25、26

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

在管理者历史版本查看态接入显式版本正文读取。用户在版本历史中选择某个历史版本时，前端调用 `GET /api/v1/documents/{documentId}/versions/{versionNumber}/content`，展示该版本的 Markdown 正文。

页面必须稳定提示“当前正在查看历史版本 vN，最新版本为 vM；查看历史版本不会改变问答基线”，并提供“返回最新版本”入口。

#### Acceptance criteria

- [ ] 管理人员在历史版本查看态读取 explicit version endpoint。
- [ ] 历史版本正文上方展示历史版本提示，并说明不会改变 QA baseline。
- [ ] 提供“返回最新版本”入口，并能切回 latest 正文视图。
- [ ] 普通 `KB_READER` 不能通过 URL 直达历史版本正文。
- [ ] 管理人员遇到 `CONTENT_ARTIFACT_MISSING` 时看到重处理或联系管理员提示。
- [ ] `CONTENT_NOT_READY`、`CONTENT_TOO_LARGE` 和 `403` 均有明确错误态。
- [ ] 前端测试覆盖管理者历史正文成功、返回 latest、普通读者直达拒绝和错误态。

#### Blocked by

- DVCR-04
- DVCR-05

### DVCR-08 正文读取后端契约与错误映射收口

GitHub Issue：[#29](https://github.com/pop1414/my-AI/issues/29)

Type：AFK

Blocked by：DVCR-02、DVCR-03、DVCR-04

User stories covered：17、22、23、24、25、26

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

对三个正文读取后端 endpoint 做统一收口，确保 OpenAPI 契约、响应字段、`source` 语义、业务错误码和 HTTP 状态码一致。该 issue 不包含前端页面实现，只处理后端契约与服务端行为一致性。

收口后，`GET /api/v1/documents/{documentId}/content`、`GET /api/v1/documents/{documentId}/askable-content`、`GET /api/v1/documents/{documentId}/versions/{versionNumber}/content` 的成功响应和错误响应应与 `docs/04-api-contract.yaml` 保持一致。

#### Acceptance criteria

- [ ] 三个正文读取 endpoint 的 OpenAPI 契约与实际实现一致。
- [ ] `DocumentContentResponse` 字段、`source` 枚举和业务错误码在后端 DTO、REST controller 与 OpenAPI 中一致。
- [ ] `CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING`、`CONTENT_TOO_LARGE`、`DOCUMENT_CONTENT_FORBIDDEN`、`VERSION_CONTENT_FORBIDDEN` 在三个 endpoint 中映射稳定。
- [ ] `DOCUMENT_NOT_FOUND` 与 `VERSION_NOT_FOUND` 的 `404` 分支清晰区分。
- [ ] 文档或版本为 `DELETED` 时不暴露正文。
- [ ] 后端测试覆盖关键错误码、权限拒绝、契约字段和 `source` 语义。
- [ ] 后端实现不引入源文件下载、源文件实时解析或 chunk 拼接正文路径。

#### Blocked by

- DVCR-02
- DVCR-03
- DVCR-04

### DVCR-09 正文读取前端错误态与权限隐藏收口

GitHub Issue：[#30](https://github.com/pop1414/my-AI/issues/30)

Type：AFK

Blocked by：DVCR-05、DVCR-06、DVCR-07、DVCR-08

User stories covered：5、6、7、12、28

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

对三个正文读取前端入口做统一收口，确保错误态、权限隐藏和产品边界一致。该 issue 不包含后端 endpoint 实现，只处理前端 API client、页面状态、文案和入口隐藏。

收口后，前端不展示源文件下载入口，不把正文查看扩展成源文件预览或编辑，也不在任何 `403` 场景渲染正文内容。

#### Acceptance criteria

- [ ] 前端 API client 与后端 `DocumentContentResponse` 字段、`source` 枚举和错误码保持一致。
- [ ] `CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING`、`CONTENT_TOO_LARGE`、`DOCUMENT_CONTENT_FORBIDDEN`、`VERSION_CONTENT_FORBIDDEN` 在不同入口有一致且符合角色语义的文案。
- [ ] 前端不展示源文件下载入口。
- [ ] 前端不提供正文编辑、源文件原版预览、复杂目录或 chunk 拼接查看能力。
- [ ] `KB_READER` 的文档详情和 QA 侧栏均保持只读边界。
- [ ] `403` 场景不渲染正文内容，也不残留上一份正文。
- [ ] 前端测试覆盖关键错误码、权限隐藏、文案和无权内容不渲染。

#### Blocked by

- DVCR-05
- DVCR-06
- DVCR-07
- DVCR-08

### DVCR-10 文档版本正文读取 E2E 专项验收

GitHub Issue：[#31](https://github.com/pop1414/my-AI/issues/31)

Type：AFK

Blocked by：DVCR-09

User stories covered：1、3、4、8、9、10、16、29、30

#### Parent

PRD #21：文档版本正文读取专项

#### What to build

补齐文档版本正文读取专项的 E2E 验收。该 issue 不再新增业务语义，只验证已完成的后端、前端和权限边界是否在真实浏览器流程中闭环。

首期 E2E 聚焦三条路径：`KB_READER` 从 QA 引用侧栏打开 askable baseline 正文，`KB_MANAGER` 打开指定历史版本正文，`KB_READER` 不能通过 URL 直达历史版本正文。

#### Acceptance criteria

- [ ] E2E 数据包含同一 `document` 的三版本场景：`v1 INDEXED`、`v2 INGESTING`、`v3 FAILED with cleaned.md`。
- [ ] `KB_READER` 从 QA 引用侧栏打开 askable baseline 正文时，看到 askable baseline 版本号和非 latest 提示。
- [ ] `KB_MANAGER` 能从历史版本入口打开指定版本正文，并看到“不改变问答基线”提示。
- [ ] `KB_READER` 通过 URL 直达历史版本正文时被拒绝，且页面不渲染正文。
- [ ] latest `INGESTING` 时文档详情 latest 正文展示处理中空态，不自动回退旧版本。
- [ ] latest `FAILED with cleaned.md` 时文档详情可展示 failed latest 正文并提示失败状态。
- [ ] E2E 放在后端接口测试与前端单元/组件测试之后运行，不替代低层测试。
- [ ] 验收说明记录可复用的黄金样本链：`weak-pdf-001 -> md-001 -> md-002 -> html-001 -> word-001`。

#### Blocked by

- DVCR-09

## 审阅提示

- 先审阅 DVCR-01 到 DVCR-04，确认后端边界没有重新引入源文件下载、源文件实时解析或 chunk 拼接正文。
- 再审阅 DVCR-05 到 DVCR-07，确认三类前端入口分别对应 latest、askable baseline、explicit version，不互相复用成模糊入口。
- 重点检查 `KB_READER`：可以读 askable baseline 正文，但不能浏览任意历史版本正文。
- 检查 DVCR-08 是否只处理后端契约与错误映射，不夹带前端页面实现。
- 检查 DVCR-09 是否只处理前端错误态、权限隐藏和文案，不夹带后端 endpoint 实现。
- 检查 DVCR-10 的 E2E 是否只做端到端验收，不替代后端和前端低层测试。
- GitHub Issues 已按总览表顺序发布；后续执行时以真实 issue 编号为协作入口，本地 `DVCR-*` 编号仅作为专题内排序辅助。
