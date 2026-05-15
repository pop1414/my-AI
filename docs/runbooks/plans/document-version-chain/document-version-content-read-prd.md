# PRD：文档版本正文读取专项

关联 GitHub Issue：[#21 PRD: 文档版本正文读取专项](https://github.com/pop1414/my-AI/issues/21)

## 问题陈述

当前系统已经把 `document` 与 `document version` 的边界稳定下来，`cleaned.md` 也被定义为版本级正式正文产物。但用户在文档详情页、QA 引用侧栏和管理人员历史版本查看中，还缺少一条统一、可授权、可测试的正文读取链路。

从用户视角看，当前问题包括：

- 文档详情页需要读取当前 latest version 的正文，但不能在 latest 尚未处理完成时自动跳到旧版本，避免误导用户以为旧正文就是最新正文。
- QA 引用侧栏需要展示本次问答实际依据的 askable baseline version 正文，而不是无条件展示 latest version。
- 管理人员需要读取指定历史版本正文来做版本核对和治理排查，但普通 `KB_READER` 不能通过显式 version URL 绕过权限读取任意历史版本。
- 正文来源必须是版本级 `cleaned.md`，不能从源文件实时解析，也不能从 `vector_store` chunk 拼接完整正文。
- 源文件下载不进入当前阶段，避免把“正文查看”误扩展成“原文件分发”。

如果该能力缺失或边界不清，RAG 引用核对会和问答基线脱节，权限模型会出现绕过路径，版本治理也会重新退回到主表旧字段、源文件或 chunk 的不稳定事实源。

## 解决方案

实现“文档版本正文读取”专项，以 `document version` 的 `cleaned.md` 为唯一正文事实源，提供三类读取语义：

- 默认 latest 正文：`GET /api/v1/documents/{documentId}/content`
- QA 引用侧栏 askable baseline 正文：`GET /api/v1/documents/{documentId}/askable-content`
- 管理人员显式版本正文：`GET /api/v1/documents/{documentId}/versions/{versionNumber}/content`

后端通过版本处理产物存储端口读取 `cleaned.md`，定位必须包含 `workspaceId + documentId + versionNumber`。应用层不得直接依赖本地路径、MinIO SDK 或源文件解析逻辑。权限上延续 `ADR-0005` 与 `ADR-0006`：`KB_READER` 可问答、可查看问答基线版本正文，但不可浏览任意历史版本；`KB_MANAGER`、目标 `document` 管理权限用户和工作区管理员可读取任意历史版本正文。

前端在文档详情页展示 latest 正文，在 QA 引用侧栏展示 askable baseline 正文，在管理人员历史版本视图展示指定版本正文，并对非 latest、历史版本、处理中、产物缺失、正文过大和无权限场景给出明确状态。首期只做 Markdown 安全渲染和基础样式，不做编辑、源文件原版预览、复杂目录联动或分页阅读。

## 用户故事

1. 作为 `KB_READER`，我希望在 QA 引用侧栏打开文档正文，这样我可以核对回答所依据的 askable baseline version。
2. 作为 `KB_READER`，我希望引用侧栏展示返回的版本号，这样我能知道当前回答基于哪个 `document version`。
3. 作为 `KB_READER`，我希望当问答基线不是最新版本时看到明确提示，这样我能理解最新文档尚未成为问答依据。
4. 作为 `KB_READER`，我希望通过 URL 直达任意历史版本正文时被拒绝，这样只读权限不会变成版本历史浏览权限。
5. 作为 `KB_READER`，我希望 `403` 场景不渲染任何正文内容，这样无权内容不会被前端残留状态泄漏。
6. 作为 `KB_READER`，我希望正文暂不可用时看到只读不可用提示，这样我不会把产物缺失误解为空文档。
7. 作为 `KB_READER`，我希望文档详情保持只读体验，不显示版本历史、上传新版本、重处理、回退、删除、授权管理或源文件下载入口。
8. 作为 `KB_MANAGER`，我希望从文档详情页打开 latest 正文，这样我可以审核当前 `document` 的最新状态。
9. 作为 `KB_MANAGER`，我希望打开指定历史版本正文，这样我可以比较和排查版本演进问题。
10. 作为 `KB_MANAGER`，我希望查看历史版本正文时看到稳定提示，这样我知道该查看行为不会改变 QA baseline。
11. 作为 `KB_MANAGER`，我希望查看历史版本时有“返回最新版本”入口，这样我能快速回到当前 latest 视图。
12. 作为 `KB_MANAGER`，我希望 `cleaned.md` 产物缺失时看到面向修复的提示，这样我可以重处理或联系管理员排查。
13. 作为 `WORKSPACE_ADMIN`，我希望正文读取权限遵循工作区治理能力，这样管理员可以排查工作区内的文档版本问题。
14. 作为 `KB_CONTRIBUTOR`，我希望能读取自己维护范围内的正文，这样我可以验证上传、处理和重处理结果。
15. 作为文档管理者，我希望 latest 为 `FAILED` 且已有 `cleaned.md` 时仍能读取正文，这样我可以检查失败版本的产物内容。
16. 作为文档管理者，我希望 latest 为 `INGESTING` 且尚无 `cleaned.md` 时看到“正文处理中”，而不是自动展示旧正文。
17. 作为文档管理者，我希望 `DELETED` document 拒绝正文读取，这样已删除资产不会继续暴露正文。
18. 作为后端实现者，我希望用一个正文读取用例统一处理 latest、askable baseline 和 explicit version 三种来源，这样版本选择规则可测试且一致。
19. 作为后端实现者，我希望通过版本处理产物存储端口读取正文，这样应用层不依赖本地文件路径或对象存储 SDK。
20. 作为后端实现者，我希望产物 key 包含 `workspaceId`、`documentId`、`versionNumber` 和 artifact 名称，这样版本产物不会退化成 document 级共享文件。
21. 作为后端实现者，我希望 source 产物和 artifact 产物使用不同 prefix，这样源文件审计/重处理路径不会混入正文读取路径。
22. 作为后端实现者，我希望 artifact 缺失返回 `CONTENT_ARTIFACT_MISSING`，这样 GET 正文链路不会同步重解析源文件。
23. 作为后端实现者，我希望正文未就绪返回 `CONTENT_NOT_READY`，这样前端可以展示处理中状态。
24. 作为后端实现者，我希望正文超过服务端上限返回 `CONTENT_TOO_LARGE`，这样系统不会静默截断完整正文。
25. 作为 API 使用者，我希望响应包含 `documentId`、`versionNumber`、`latestVersionNumber`、`isLatestVersion`、`isAskableVersion`、`source`、`status`、`filename`、`createdAt`、`updatedAt`、`contentMarkdown`、`contentLength` 和 `truncated`，这样前端无需额外请求即可展示版本上下文。
26. 作为 API 使用者，我希望 `source` 区分 `LATEST`、`EXPLICIT_VERSION` 和 `ASKABLE_BASELINE`，这样前端可以渲染准确的标签和提示。
27. 作为 QA 用户，我希望已返回的问答引用持续指向回答生成时使用的版本，这样后续上传不会静默改写旧回答上下文。
28. 作为产品负责人，我希望源文件下载排除在本阶段之外，这样本专项聚焦于安全的正文核对，而不是文件分发。
29. 作为测试人员，我希望有同一 `document` 的三版本场景：`v1 INDEXED`、`v2 INGESTING`、`v3 FAILED with cleaned.md`，这样可以同时验证 latest、askable baseline 和 failed content 路径。
30. 作为测试人员，我希望 E2E 放在后端和前端单元/组件测试之后，这样浏览器测试验证端到端角色边界，而不是承担第一层回归防线。

## 实现决策

- 遵守 `ADR-0005` 与 `ADR-0006`，本 PRD 不引入新的 ADR 冲突。
- `cleaned.md` 是文档版本正文读取的唯一事实源。
- 默认正文展示不读取源文件。
- 不从 `vector_store` chunk 拼接完整正文。
- 当前阶段不提供源文件下载。
- 新增或调整版本处理产物存储端口，通过 `workspaceId + documentId + versionNumber + artifactName` 读取版本级处理产物。
- 新增或调整稳定的 artifact key resolver，区分 `source/...` 与 `artifacts/...` prefix。
- 新增或调整正文读取应用模块，解析三种来源语义：latest、askable baseline、explicit version。
- 授权集成需要覆盖：`WORKSPACE_OWNER` / `WORKSPACE_ADMIN` 工作区级访问，`KB_MANAGER` 与 `DOC_ALLOW_MANAGE` 历史版本读取，`KB_CONTRIBUTOR` 维护范围内正文读取，`KB_READER` 仅问答基线正文读取。
- `DOC_DENY` 对普通成员保持最高优先级拒绝。
- `KB_ASKER` 仅做废弃角色的必要兼容或迁移，不作为新能力正向角色。
- 文档不存在和版本不存在分别映射为 `404`，业务码为 `DOCUMENT_NOT_FOUND` 或 `VERSION_NOT_FOUND`。
- 文档级正文无权限映射为 `403`，业务码为 `DOCUMENT_CONTENT_FORBIDDEN`。
- 显式版本正文无权限映射为 `403`，业务码为 `VERSION_CONTENT_FORBIDDEN`。
- 正文未就绪映射为 `409`，业务码为 `CONTENT_NOT_READY`。
- 正文超过服务端读取上限映射为 `413`，业务码为 `CONTENT_TOO_LARGE`。
- 正文产物缺失映射为 `500`，业务码为 `CONTENT_ARTIFACT_MISSING`。
- 首期在 `contentMarkdown` 中返回完整 Markdown；`truncated` 固定为 `false`，超限直接失败，不静默截断。
- latest `INDEXED` 时，`/content` 与 `/askable-content` 都返回 latest。
- latest `INGESTING` 且存在旧 `INDEXED` 版本时，`/content` 返回 `CONTENT_NOT_READY`，`/askable-content` 返回旧 indexed askable baseline。
- latest `FAILED` 且已有 `cleaned.md` 时，`/content` 返回 failed latest 正文，`/askable-content` 返回最近一个 indexed askable baseline。
- `DELETED` document 拒绝正文读取；管理人员最多查看终态元数据与版本历史元数据。
- 前端文档详情默认读取 latest 正文，不自动回退到 askable baseline。
- 前端 QA 引用侧栏使用 askable baseline endpoint，并展示返回版本号。
- 前端历史版本正文视图使用 explicit version endpoint，并提示“查看历史版本不会改变问答基线”。
- 前端首期采用 Markdown 安全渲染和基础样式，不做正文编辑、源文件原版预览、复杂目录或 diff 交互。
- 交付顺序按后端、前端、E2E 验收推进，符合本仓库“后端正确性先行，再接前端”的协作约定。

## 测试决策

- 好测试应断言外部行为：选中的版本、返回的 `source`、授权结果、业务错误码、HTTP 状态码和可见 UI 状态；不要锁死内部 helper 调用顺序。
- 后端应用测试应覆盖默认 latest 正文读取、askable baseline 正文读取、显式版本正文读取、`KB_READER` 禁止历史版本、`DELETED` 禁止正文读取、`CONTENT_NOT_READY`、`CONTENT_ARTIFACT_MISSING` 和 `CONTENT_TOO_LARGE`。
- 权限测试应覆盖 `WORKSPACE_ADMIN`、`KB_MANAGER`、`KB_CONTRIBUTOR`、`KB_READER`，以及必要的 `KB_ASKER` 兼容或迁移行为。
- 后端集成测试应重点验证“读到的是哪个版本”：latest `INDEXED`、latest `INGESTING` 且旧版本 `INDEXED`、latest `FAILED with cleaned.md`、管理人员读取指定历史版本。
- 存储测试应验证 artifact key 包含 `workspaceId/documentId/versionNumber`，`source/...` 与 `artifacts/...` prefix 不混用，缺失 artifact 不触发源文件重解析。
- REST controller 测试应覆盖三个 endpoint、响应体字段和业务错误到 HTTP 状态码的映射。
- OpenAPI 契约检查应保持 `DocumentContentResponse` 与三个 planned endpoint 的实现一致。
- 前端测试应验证普通读者不显示版本历史、治理入口或下载入口。
- 前端测试应验证 QA 侧栏版本提示、历史版本“不改变问答基线”提示、正文错误态文案，以及 `403` 不渲染正文。
- E2E 应在后端和前端单元/组件测试完成之后补齐。
- 首期 E2E 覆盖 `KB_READER` 从 QA 引用侧栏打开 askable baseline 正文、`KB_MANAGER` 打开指定历史版本正文、`KB_READER` 不能通过 URL 直达历史版本正文。
- 可复用的后端测试先例包括 authorization service tests、document version history tests、document version read boundary tests、local artifact storage tests、QA askable document version adapter tests 和 ingest REST controller tests。
- 可复用的前端实现面包括 document detail、document version read prototype、QA page、shared API request handling 和 ingest API modules。
- 黄金样本回归应复用 `weak-pdf-001 -> md-001 -> md-002 -> html-001 -> word-001`，并新增同一 `document` 的三版本场景。

## 非本期范围

- 源文件下载。
- 源文件原版预览。
- 正文编辑。
- 复杂 Markdown 目录、锚点导航或 diff UI。
- 分页或流式正文读取。
- 从 chunk 重建完整文档正文。
- 在 GET 正文链路中同步重解析源文件。
- `KB_ASKER` 新正向能力。
- 超出当前单工作区优先模型的多租户 SaaS 扩展。
- 改写 QA 检索规则；本专项只复用并展示既有 askable baseline 语义。
- 重做版本回退、上传新版本、删除确认或授权治理流程中与正文读取无关的部分。

## 补充说明

- 本 PRD 基于交接包 `C:\Users\Ayanami\AppData\Local\Temp\tmpF308.tmp`、`CONTEXT.md`、`ADR-0005`、`ADR-0006`、RAG 权限体系专项计划和当前 OpenAPI 契约整理。
- 当前 OpenAPI 契约已经将三个正文读取接口标记为 planned，实现应收敛到该契约，而不是新增并行 API 形态。
- 最重要的产品区分是：latest 正文、askable baseline 正文和 explicit historical version 正文是三种不同用户意图，应保持三种不同 endpoint 语义。
- 最重要的安全区分是：普通读者能读取 askable baseline 正文，不代表可以浏览任意历史版本。
- 最重要的存储区分是：版本正文属于 `document version`，不是共享的 document 文件，也不是 chunk 的拼接视图。
- GitHub Issue 适合作为协作和实施跟踪入口；本地文档更适合作为长期事实源。后续若 issue 与本文档冲突，应以本文档、ADR 和 `CONTEXT.md` 为准。
