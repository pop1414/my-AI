# V1 稳定化与债务收敛审计报告

状态：审计产物  
日期：2026-05-19  
分支：`docs/v1-stabilization`  
范围：只审计并记录，不修改业务代码、不创建 GitHub Issues。

## 1. 当前基线

### 1.1 仓库状态

- 执行 `git status --short` 时输出为空，审计开始前工作树干净。
- 本轮新增文件仅限本报告：`docs/runbooks/plans/v1-stabilization/v1-stabilization-audit.md`。
- 当前仓库已经有明确文档分层：`docs/` 是工程真源，`docs/learning/` 是学习沉淀，`deliverables/course/` 是课程交付层。

### 1.2 后端技术栈

- Java 21
- Spring Boot 3.5.8
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.x
- PostgreSQL + PGVector
- Flyway
- Maven Wrapper：`.\mvnw.cmd`

### 1.3 前端技术栈

- React 19
- TypeScript 6
- Vite 8
- Ant Design 6
- TanStack Query
- React Router
- Zod
- Playwright

### 1.4 后端 module 现状

- `auth`：本地账号、Session、工作区角色、知识库授权、文档授权、审计、治理接口。
- `ingest`：文档上传、处理、版本链、正文读取、重处理、删除、处理产物、向量索引。
- `knowledge`：知识库主数据管理、状态、统计视图。
- `qa`：同步问答、授权后可问答版本范围、PGVector 检索、版本化引用与 stale 汇总。
- `shared`：REST 错误响应、业务异常、默认工作区常量。

### 1.5 当前验证命令

```powershell
.\mvnw.cmd -q test
```

```powershell
cd web
npm.cmd run build
npm.cmd run test:e2e
```

## 2. 文档债务清单

| 文档                        | 状态   | 问题                                                                                                                                                   | 建议                                                                                                     |
| --------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| `README.md`                 | 需更新 | 当前能力列表较新，但治理 API 摘要未完整覆盖账号治理接口组；文档日期混用 `2026-05-13` 与 `2026-05-16`，不利于判断事实新旧。                             | 保留为项目入口，补齐账号治理 API 摘要，并统一“截至日期”为最新稳定基线。                                  |
| `docs/README.md`            | 可信   | 文档分层、阅读顺序、真源规则清晰，适合作为文档治理入口。                                                                                               | 后续新增本报告后，可在 plans 导航中补充入口。                                                            |
| `docs/01-product-scope.md`  | 冲突   | 文档仍写 V1 是“单用户模式”，并把“复杂权限系统”列为 out of scope；当前代码和 API 契约已经包含 auth/governance、工作区角色、知识库授权、文档授权和审计。 | 改为历史 V1 范围说明，或新增“当前基线已超出原 V1 scope”的显式说明。                                      |
| `docs/02-roadmap.md`        | 需更新 | 当前快照承认权限体系规划，但“V1.1 范围调整”仍表达为权限从 V1.1 拆出；实际 auth/governance 已经工程化落地。                                             | 将权限体系状态拆成“已落地基线”和“后续增强”，避免读者误以为权限还停留在规划阶段。                         |
| `docs/03-architecture.md`   | 冲突   | 第 2 章目标架构仍包含 `MySQL / MinIO / Tenant / SSE` 等未实现或仅预留内容；第 2.1 又描述当前已实现子集，二者并列容易误读。                             | 将目标蓝图与当前事实分成两个显式小节；当前事实应写 PostgreSQL/PGVector、本地存储、Session auth、无 SSE。 |
| `docs/04-api-contract.yaml` | 可信   | 契约覆盖 auth/governance/ingest/knowledge/qa，和当前实现方向基本一致。                                                                                 | 后续以它作为 API 真源，README 只保留摘要，不重复维护完整接口细节。                                       |
| `docs/05-release-notes.md`  | 需更新 | `Unreleased` 内容已经很大，包含多个专题完成项；继续堆叠会降低检索效率。                                                                                | 下一轮稳定化可切一个 `1.1.x` 或 `v1-stabilization` 小节，把已收口事项从 Unreleased 中归档。              |
| `web/README.md`             | 需更新 | 仍聚焦文档、知识库、问答；未完整同步系统管理、成员/账号/授权/审计、成员阅读页和问答基线阅读页等当前前端范围。                                          | 补成前端控制台当前能力摘要，并声明 E2E 覆盖范围。                                                        |

### 2.1 需要明确标记的冲突

- 产品范围冲突：`docs/01-product-scope.md` 的“单用户 / 不做复杂权限”与当前 `auth` module、OpenAPI `governance` tag、前端系统管理页面冲突。
- 架构蓝图冲突：`docs/03-architecture.md` 的 MySQL、MinIO、Tenant、SSE 是目标蓝图或未来扩展，不是当前实现。
- 路线图状态冲突：`docs/02-roadmap.md` 仍把权限体系表述成独立规划，但仓库已有本地账号、Session、权限和审计的实现基线。

## 3. 代码债务清单

以下按稳定性优先排序。

### P0-1 latest projection 双写仍是最高风险点

- 涉及文件：
    - `src/main/java/io/github/spike/myai/ingest/infrastructure/persistence/JdbcDocumentRepository.java`
    - `src/main/resources/db/migration/V7__extract_latest_projection_maintenance_functions.sql`
- 问题：
    - `JdbcDocumentRepository` 已通过数据库函数收口部分 latest projection 维护，但仍有 `markDeleting`、`markDeleted`、`rollbackDeleting`、`requestReprocess` 等路径手工维护 `ingest_documents` 与 `ingest_document_versions` 的镜像状态。
    - 当前 module interface 表达的是“推进 latest version”，implementation 仍暴露为多处主表/版本表双写，locality 不够集中。
- 建议方案：
    - 新增 `LatestDocumentVersionProjection` 或等价 persistence module，把所有 latest projection 状态推进收敛到一个 seam。
    - 先不改业务语义，只迁移 `markDeleting / markDeleted / rollbackDeleting / requestReprocess` 到同一 projection maintenance interface。
- 收益：
    - 提升 locality：版本状态、latest 快照、版本事实的一致性只在一个地方维护。
    - 提升 leverage：调用方只关心业务动作，不再关心双表镜像细节。
- 风险：
    - 涉及数据库函数和 JDBC repository，必须保留现有并发冲突语义。
- 建议测试：
    - `JdbcDocumentRepositoryTest`
    - `DocumentVersionReadBoundaryTest`
    - 删除、重处理、上传新版本、版本回退的 CAS 冲突测试。

### P0-2 文件系统、向量库与数据库事务不在同一原子边界

- 涉及文件：
    - `src/main/java/io/github/spike/myai/ingest/interfaces/rest/DocumentIngestController.java`
    - `src/main/java/io/github/spike/myai/ingest/application/service/UploadNewDocumentVersionApplicationService.java`
    - `src/main/java/io/github/spike/myai/ingest/application/service/RollbackDocumentVersionApplicationService.java`
    - `src/main/java/io/github/spike/myai/ingest/application/service/DeleteDocumentApplicationService.java`
- 问题：
    - 上传受理、新版本上传、版本回退、删除都会跨 DB、源文件、处理产物、向量库执行副作用。
    - 代码已有幂等写入、状态回滚和冲突分类，但缺少统一的“副作用恢复/巡检”策略文档和测试入口。
- 建议方案：
    - 先补一个 `ingest` 恢复性 runbook，定义可能出现的 orphan source、missing source、orphan artifact、orphan vector、DELETING 卡住等状态。
    - 后续再考虑将关键副作用封装为 `DocumentAssetLifecycle` module，集中处理补偿与巡检。
- 收益：
    - 稳定性优先：即使本地文件或向量删除失败，也有可执行的恢复路径。
    - 降低排障成本：避免只靠日志猜测半成功状态。
- 风险：
    - 如果直接重构实现，容易触碰上传、回退、删除三条高风险链路；第一步应先做 runbook 和 characterization tests。
- 建议测试：
    - 上传新版本源文件保存失败。
    - 回退来源文件缺失。
    - 删除源文件成功但向量删除失败后的状态回滚。
    - DELETING 状态残留的人工恢复流程。

### P1-1 `Document` 聚合仍偏浅，版本治理和处理执行耦合高

- 涉及文件：
    - `src/main/java/io/github/spike/myai/ingest/domain/model/Document.java`
    - `src/main/java/io/github/spike/myai/ingest/domain/model/DocumentVersion.java`
    - `src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java`
- 问题：
    - `Document` 同时承载 document identity、latest projection、处理状态、重试、splitVersion、processingMetadata 等信息。
    - 处理执行和版本治理都要理解同一个大 record，interface 复杂度接近 implementation。
- 建议方案：
    - 不急于拆类；先在 issue 中明确三种概念：`DocumentAsset`、`DocumentVersionFact`、`ProcessingAttempt`。
    - 只有当 latest projection seam 收口后，再考虑拆分领域模型。
- 收益：
    - 提升后续重构顺序的安全性，避免先拆模型导致多个路径同时震荡。
- 风险：
    - 直接拆模型会影响大量测试和 DTO 映射，应该排在 P0 projection 收口之后。
- 建议测试：
    - 先保留现有 `DocumentTest`、`DocumentVersionHistoryTest`。
    - 新增 model characterization tests，锁住状态、版本号、askable 推导行为。

### P1-2 QA 向量元数据兼容逻辑需要长期收口计划

- 涉及文件：
    - `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`
    - `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/JdbcAskableDocumentVersionAdapter.java`
    - `src/main/java/io/github/spike/myai/ingest/infrastructure/vector/PgVectorDocumentVectorIndexer.java`
- 问题：
    - QA 检索同时兼容 `documentVersionNumber`、`splitVersion=version-{n}-v1` 和历史 `splitVersion=v1`。
    - 这让向量元数据既承担检索过滤，也承担历史兼容；后续修改 chunk 或 splitVersion 时容易误伤问答版本引用。
- 建议方案：
    - 明确 `documentVersionNumber` 为新向量元数据事实源。
    - 新增一份向量元数据迁移/清理计划，规定何时可以停止依赖历史 `splitVersion=v1`。
- 收益：
    - 提升 QA 检索 interface 的稳定性，避免历史兼容逻辑长期污染新实现。
- 风险：
    - 清理旧兼容前必须确认历史向量是否都已重建。
- 建议测试：
    - `PgVectorChunkRetrievalAdapterTest`
    - `JdbcAskableDocumentVersionAdapterTest`
    - QA 版本化引用 E2E。

### P1-3 授权基线已落地，但安全策略仍需要明确升级边界

- 涉及文件：
    - `src/main/java/io/github/spike/myai/auth/security/CsrfHeaderFilter.java`
    - `src/main/java/io/github/spike/myai/auth/security/SecurityConfig.java`
    - `src/main/java/io/github/spike/myai/auth/application/service/AuthorizationService.java`
- 问题：
    - 当前 CSRF 是固定 Header 值，代码注释也明确未来可升级为动态 token。
    - `SecurityConfig` 中 `UserDetailsService` 注释仍称“占位实现，后续对接真实用户数据源”，但项目实际通过自定义登录服务和 Session 维护认证态，容易误导后续维护者。
- 建议方案：
    - 短期只同步注释和文档，说明当前认证链路是真实实现，不依赖 Spring Security `UserDetailsService` 登录。
    - 动态 CSRF token 单独拆 issue，不与文档同步混做。
- 收益：
    - 避免后续误删或误改安全配置。
    - 为动态 CSRF 升级留下清晰入口。
- 风险：
    - 动态 CSRF 会影响前端 request 层和所有写操作 E2E，应单独回归。
- 建议测试：
    - `AuthSecurityBaselineTest`
    - `login.spec.ts`
    - 所有 admin/ingest 写操作 E2E。

### P1-4 REST 错误码来源已经分层，但控制器仍混用异常映射方式

- 涉及文件：
    - `src/main/java/io/github/spike/myai/shared/rest/GlobalRestExceptionHandler.java`
    - `src/main/java/io/github/spike/myai/ingest/interfaces/rest/DocumentIngestController.java`
    - `src/main/java/io/github/spike/myai/qa/interfaces/rest/QaController.java`
- 问题：
    - `BusinessException` 已提供稳定业务错误码，`ResponseStatusException` 仍在多个控制器中直接抛出。
    - 对前端而言，部分错误码是稳定业务码，部分是 HTTP 状态名，长期会增加 request 层分支。
- 建议方案：
    - 不一次性全改；先为 ingest/qa 的用户可见冲突、权限和状态错误定义最小业务错误码清单。
    - 保留纯参数错误使用 `BAD_REQUEST` 的路径。
- 收益：
    - 前端错误展示更稳定，E2E 更容易断言。
- 风险：
    - 修改错误码会影响现有前端和测试，必须逐条迁移。
- 建议测试：
    - Controller tests 覆盖错误码。
    - 前端 `ApiErrorAlert` 展示回归。

### P2-1 前端 E2E 已覆盖关键路径，但缺少 frontend module-level 测试

- 涉及文件：
    - `web/src/shared/api/*.ts`
    - `web/src/features/**`
    - `web/e2e/*.spec.ts`
- 问题：
    - 当前前端验证主要靠 Playwright E2E。
    - API zod schema、权限能力位路由、复杂页面状态映射缺少更轻量的 module-level tests。
- 建议方案：
    - 本轮不引入前端测试框架。
    - 后续如要优化前端，先为 `shared/api` 和 `shared/auth` 建立轻量测试基线。
- 收益：
    - 降低每次前端调整都必须跑完整 E2E 的成本。
- 风险：
    - 引入测试框架会改前端工具链，应独立计划。
- 建议测试：
    - 保留现有 Playwright。
    - 后续再评估 Vitest 或等价方案。

### P2-2 文档体系有真源规则，但缺少“冲突处理”固定入口

- 涉及文件：
    - `docs/README.md`
    - `docs/runbooks/workflows/my-ai-document-workflow.md`
    - `docs/runbooks/plans/README.md`
- 问题：
    - 文档分层清楚，但当工程真源冲突时，目前没有固定的审计清单入口。
    - 本报告是第一次稳定化审计，可以沉淀为后续模板。
- 建议方案：
    - 后续把“文档冲突审计表”提炼进文档工作流。
    - plans 导航补充 `v1-stabilization` 入口。
- 收益：
    - 让文档债显性化，减少正式文档内容过时但无人发现的问题。
- 风险：
    - 只加流程不执行没有价值，应和每次版本收口绑定。
- 建议测试：
    - 文档审阅即可，无需自动化测试。

## 4. 测试缺口

### 4.1 后端测试现状

- `auth` 覆盖较充分：应用服务、治理 guard、JDBC persistence、REST controller、security baseline 均有测试。
- `ingest` 覆盖较充分：应用服务、领域模型、parser、chunking、storage、vector、JDBC repository、controller 均有测试。
- `knowledge` 覆盖基础完整：应用服务、JDBC repository、controller、统计读边界均有测试。
- `qa` 覆盖关键 seam：应用服务、command、controller、PGVector retrieval、askable version adapter、answer generation adapter 均有测试。
- `shared` 当前没有单独测试，主要通过 controller 和应用服务测试间接覆盖。

### 4.2 后端缺口

- latest projection 双写一致性虽然有 repository 测试，但还没有一个统一的 projection maintenance contract test。
- 文件系统、处理产物、向量库与 DB 的半成功状态缺少恢复性测试和 runbook。
- 统一错误码迁移前，前端可依赖的业务错误码清单仍不够稳定。
- 动态 CSRF token 未实现，当前固定 Header 只能作为阶段性安全基线。

### 4.3 前端测试现状

当前 E2E 文件：

- `web/e2e/login.spec.ts`
- `web/e2e/member-grants.spec.ts`
- `web/e2e/document-version-history.spec.ts`
- `web/e2e/qa-reference-version.spec.ts`
- `web/e2e/console-page-shell.spec.ts`

前端缺口：

- `shared/api` 的 zod schema 与错误解析没有轻量单元测试。
- 能力位路由、菜单显隐和默认落点主要靠 E2E 间接验证。
- 复杂页面状态映射缺少 module-level 回归。

## 5. 第一批建议 issues

以下只是草稿，不在本轮创建 GitHub Issues。

### 5.1 `refactor(ingest): 收敛 latest projection 状态推进 seam`

- 优先级：P0
- 类型：架构整理
- 背景：latest projection 仍有多处主表/版本表双写，状态一致性风险最高。
- 范围：收敛删除、重处理、状态回滚等路径的 projection maintenance。
- 不做什么：不改变 API、不调整前端、不改变版本治理语义。
- 验收标准：
    - 删除、重处理、上传新版本、版本回退测试全部通过。
    - CAS 冲突错误码保持不变。
    - 代码中新增状态推进不再手写双表镜像 SQL。
- 涉及文件：
    - `JdbcDocumentRepository.java`
    - Flyway migration
    - repository/application service tests

### 5.2 `docs(ingest): 补充文档资产副作用恢复 runbook`

- 优先级：P0
- 类型：文档同步
- 背景：DB、源文件、处理产物、向量库不在同一事务边界，缺少半成功状态恢复说明。
- 范围：定义 orphan source、missing source、orphan vector、DELETING 卡住等排障和恢复步骤。
- 不做什么：不改代码、不实现自动修复任务。
- 验收标准：
    - runbook 能指导本地检查和人工恢复。
    - 明确哪些状态需要停止 worker 后操作。
- 涉及文件：
    - `docs/runbooks/`
    - `README.md` 可加入口摘要

### 5.3 `docs(scope): 同步 V1/V1.1 产品范围与权限事实`

- 优先级：P1
- 类型：文档同步
- 背景：产品范围仍写单用户和不做复杂权限，但当前权限基线已经落地。
- 范围：更新 `docs/01-product-scope.md`、`docs/02-roadmap.md`、`README.md` 中的权限状态。
- 不做什么：不新增权限功能。
- 验收标准：
    - 文档明确区分“原 V1 范围”和“当前已落地权限基线”。
    - 不再把已实现 auth/governance 表述为纯规划。
- 涉及文件：
    - `docs/01-product-scope.md`
    - `docs/02-roadmap.md`
    - `README.md`

### 5.4 `docs(architecture): 区分目标蓝图与当前实现事实`

- 优先级：P1
- 类型：文档同步
- 背景：架构文档混写 MySQL/MinIO/Tenant/SSE 蓝图和当前 PostgreSQL/本地存储/Session auth 实现。
- 范围：重写 `docs/03-architecture.md` 的第 2 章和当前实现子集说明。
- 不做什么：不更新架构图，不迁移目录结构。
- 验收标准：
    - 当前事实和未来蓝图分区清晰。
    - 读者不会误以为当前已实现 Tenant/SSE/MinIO。
- 涉及文件：
    - `docs/03-architecture.md`

### 5.5 `test(ingest): 增加副作用半成功 characterization tests`

- 优先级：P1
- 类型：测试补强
- 背景：上传、回退、删除跨 DB 与存储副作用，需要锁住失败行为。
- 范围：补上传新版本、回退、删除的源文件/向量失败分支测试。
- 不做什么：不改业务流程。
- 验收标准：
    - 源文件保存失败、回退来源缺失、删除清理失败路径都有明确断言。
    - 失败后状态和审计语义保持当前行为。
- 涉及文件：
    - `UploadNewDocumentVersionApplicationServiceTest.java`
    - `RollbackDocumentVersionApplicationServiceTest.java`
    - `DeleteDocumentApplicationServiceTest.java`

### 5.6 `refactor(qa): 制定向量元数据兼容清理计划`

- 优先级：P1
- 类型：架构整理
- 背景：QA 检索长期兼容多个版本元数据表达，后续 RAG 优化容易误改。
- 范围：定义 `documentVersionNumber` 作为新事实源，并给出历史 `splitVersion=v1` 退出条件。
- 不做什么：不立即删除兼容逻辑。
- 验收标准：
    - 有明确的迁移前置条件和回归测试清单。
    - QA 版本引用和 stale 汇总语义不变。
- 涉及文件：
    - `PgVectorChunkRetrievalAdapter.java`
    - `PgVectorDocumentVectorIndexer.java`
    - `docs/runbooks/plans/`

### 5.7 `docs(auth): 修正认证链路说明与 CSRF 升级边界`

- 优先级：P1
- 类型：文档同步
- 背景：当前认证已通过自定义登录服务和 Session 落地，但部分注释和文档仍像占位实现。
- 范围：同步 `SecurityConfig` 相关说明、README/auth 文档，并拆出动态 CSRF 后续项。
- 不做什么：不实现动态 CSRF。
- 验收标准：
    - 文档明确当前固定 Header CSRF 是阶段性基线。
    - 认证实现不再被描述为未接真实用户数据源。
- 涉及文件：
    - `SecurityConfig.java`
    - `CsrfHeaderFilter.java`
    - `README.md`

### 5.8 `test(frontend): 评估 shared/api 与权限路由轻量测试基线`

- 优先级：P2
- 类型：测试补强
- 背景：前端当前主要依赖 E2E，API schema 和权限路由缺少更快反馈。
- 范围：评估是否引入 Vitest 或等价方案，并先覆盖 `shared/api`、`shared/auth`。
- 不做什么：不重构页面 UI，不替代现有 Playwright。
- 验收标准：
    - 给出是否引入 module-level tests 的结论。
    - 若引入，先覆盖 API 错误解析、能力位路由和默认落点。
- 涉及文件：
    - `web/src/shared/api/*.ts`
    - `web/src/shared/auth/*.tsx`
    - `web/e2e/*.spec.ts`

## 6. 暂不处理项

- 不在本轮修改业务代码。
- 不在本轮创建 GitHub Issues。
- 不在本轮调整数据库结构。
- 不在本轮迁移目录结构。
- 不在本轮同时做前端重构。
- 不在本轮实现动态 CSRF token。
- 不在本轮清理历史向量元数据兼容逻辑。

## 7. 审阅指南

建议按下面顺序审阅本报告：

1. 先看第 2 章，确认文档债务是否真实反映当前冲突。
2. 再看第 3 章，确认代码债务是否按稳定性风险排序。
3. 再看第 5 章，确认每个 issue 草稿是否能独立交付、独立回归。
4. 最后看第 6 章，确认暂不处理项是否足够克制。

如果后续开始实现，建议先做：

1. `docs(ingest): 补充文档资产副作用恢复 runbook`
2. `docs(scope): 同步 V1/V1.1 产品范围与权限事实`
3. `refactor(ingest): 收敛 latest projection 状态推进 seam`

原因是：先补恢复文档和事实文档，可以降低后续重构时的排障成本；projection seam 是最高价值但也最高风险的代码改动，应在文档和测试边界清楚后再动。
