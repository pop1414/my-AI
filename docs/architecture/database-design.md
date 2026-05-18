# 数据库设计说明

本文档用于论文与项目文档中的数据库设计章节。图谱按阅读场景拆分：

- 首页总览：`docs/architecture/diagrams/database/database-er-overview.puml`
- 账号与权限模块：`docs/architecture/diagrams/database/database-er-auth-access.puml`
- 知识库与文档入库模块：`docs/architecture/diagrams/database/database-er-knowledge-ingest.puml`
- 问答向量检索模块：`docs/architecture/diagrams/database/database-er-qa-vector.puml`
- 审计治理模块：`docs/architecture/diagrams/database/database-er-audit.puml`
- 数据库设计答辩讲稿：`docs/architecture/database-design-defense-script.md`

说明：表结构以 `src/main/resources/db/migration/V1__auth_flyway_schema.sql` 到 `V8__add_deleted_knowledge_base_status.sql` 为准。ER 图中实线表示数据库外键，虚线表示业务或元数据约定关系。

V8 补充了 `knowledge_bases.status` 的生命周期约束，知识库支持 `ACTIVE`、`INACTIVE`、`DELETED` 三种状态；`DELETED` 表示软删除，业务列表、上传、问答和授权治理默认排除该状态。

## 总览精简 ER 图

论文首页建议只放总览精简 ER 图，保留“用户、工作区、知识库、文档、文档版本、向量分块”的主链路。

图源文件：`docs/architecture/diagrams/database/database-er-overview.puml`

### 总览图讲解

这张图适合用作数据库设计章节的第一张图。它不追求展示全部字段和全部权限细节，而是先把系统最核心的数据流讲清楚：用户进入工作区，工作区管理知识库，知识库组织文档，文档产生版本，版本处理后生成向量分块，最后由向量分块支撑问答检索。

对照图片阅读时，可以按从左到右的顺序理解：

1. `users` 是系统用户身份表，表示谁在使用系统。
2. `workspaces` 是工作区表，表示一组用户、知识库、文档和审计数据共同归属的业务空间。
3. `workspace_memberships` 是用户和工作区之间的成员关系表，说明某个用户是否属于某个工作区，以及在这个工作区里是什么角色。
4. `knowledge_bases` 是知识库主数据表，表示一个可以被上传文档、授权和问答的知识集合。
5. `ingest_documents` 是文档资产主表，表示长期存在的文档对象，例如“项目规范文档”。它保存文档稳定身份和最新版本投影。
6. `ingest_document_versions` 是文档版本事实表，保存每一次上传、回滚或重处理形成的版本事实。
7. `vector_store` 是向量分块表，保存用于语义检索的文本分块、metadata 和 embedding。

图中的主链路是“工作区 -> 知识库 -> 文档 -> 版本 -> 向量”。这条链路对应项目的主要业务流程：先创建知识库，再上传文档，系统把文档解析、清洗、分块、向量化，最终让问答模块能够按知识库范围召回内容。

需要特别注意两类关系线。实线表示数据库中声明了外键，例如 `users` 到 `workspace_memberships`、`workspaces` 到 `knowledge_bases`、`ingest_documents` 到 `ingest_document_versions`。虚线表示业务逻辑关系或 metadata 约定关系，例如 `knowledge_bases` 到 `ingest_documents` 的归属关系，以及 `ingest_document_versions` 到 `vector_store` 的向量分块关系。虚线不是“不重要”，而是说明这类关系由应用层校验或向量表 metadata 维持，适合兼容 Spring AI PGVector 和历史迁移。

从项目理解角度看，这张总览图回答的是“数据从哪里来，又如何进入问答”。用户不是直接访问所有向量，而是在工作区和知识库上下文中操作文档。文档也不是只有一份当前内容，而是有稳定资产身份和多个版本。问答时真正被召回的是 `vector_store` 中的分块，但这些分块必须能追溯回知识库、文档和版本，否则就无法保证权限、版本引用和审计追踪的正确性。

## 分模块详细 ER 图

### 账号与权限模块

图源文件：`docs/architecture/diagrams/database/database-er-auth-access.puml`

#### 账号与权限模块讲解

这张图用于说明系统如何判断“谁可以访问什么”。对照图片时，可以先把它分成三层：账号层、工作区成员层、资源授权层。

账号层包括 `users`、`local_credentials` 和 `login_lock_states`。`users` 保存用户稳定身份，例如 `user_id`、`username`、`display_name` 和 `status`。`local_credentials` 与 `users` 是一对一关系，用同一个 `user_id` 作为主键和外键，保存密码哈希、密码算法和密码更新时间。这样做的好处是把“用户是谁”和“用户如何登录”拆开，后续如果接入第三方登录，`users` 仍然可以作为统一身份表。`login_lock_states` 与 `users` 是一对零或一关系，用于记录连续登录失败次数、锁定截止时间和最近登录时间，解决账号暴力尝试和锁定状态持久化问题。

工作区成员层包括 `workspaces` 和 `workspace_memberships`。`workspaces` 是权限边界，当前项目主要使用默认工作区，但表结构保留多工作区能力。`workspace_memberships` 连接用户和工作区，表达“某个用户在某个工作区中是什么角色、是否有效”。图中 `users ||--o{ memberships` 表示一个用户可以拥有多条工作区成员关系，`workspaces ||--o{ memberships` 表示一个工作区可以包含多个成员。`UK(workspace_id, user_id)` 保证同一个用户在同一个工作区中只有一条成员记录，避免权限判断时出现重复身份。

资源授权层包括 `knowledge_base_grants` 和 `document_grants`。`knowledge_base_grants` 控制知识库级权限，角色包括 `KB_MANAGER`、`KB_CONTRIBUTOR`、`KB_READER`、`KB_ASKER（被弃用）`。它回答的是“用户在某个知识库上有什么能力”。例如，管理者可以治理知识库，贡献者可以上传内容，阅读者可以查看。`document_grants` 控制文档级覆盖权限，权限包括 `DOC_ALLOW_READ`、`DOC_ALLOW_MANAGE`、`DOC_DENY`。它回答的是“某个具体文档是否对这个用户有例外规则”。

这张图的关键设计点是权限不是直接挂在 `users` 上，而是放在工作区上下文中解释。知识库授权和文档授权都带有 `workspace_id` 和 `user_id`，并且与工作区成员关系形成业务闭环。这样可以避免全局角色过粗的问题。例如，同一个用户在 A 工作区可以是管理员，在 B 工作区可以只是普通成员；同一个用户在某个知识库有阅读权限，但对其中一份敏感文档可以被 `DOC_DENY` 拒绝访问。

对照图中的关系线，可以这样理解访问判断流程：用户先通过 `users` 和 `local_credentials` 完成登录，登录安全状态由 `login_lock_states` 约束；随后系统检查用户是否存在有效的 `workspace_memberships`；如果用户要访问知识库，则查询 `knowledge_base_grants`；如果用户要访问具体文档，则进一步查询 `document_grants`，文档级规则可以作为更细粒度的允许或拒绝。这个分层让系统既能支持常规的知识库级授权，也能处理敏感文档、例外授权和管理端治理场景。

如果只看图片中的字段，需要重点关注几个约束：`users.username` 唯一，防止登录名冲突；`workspace_memberships(workspace_id, user_id)` 唯一，防止同一工作区成员重复；`knowledge_base_grants(workspace_id, kb_id, user_id)` 唯一，防止同一用户对同一知识库出现多条冲突授权；`document_grants(workspace_id, document_id, user_id)` 唯一，防止同一文档授权重复。权限系统的可靠性很大程度来自这些唯一约束。

### 知识库与文档入库模块

图源文件：`docs/architecture/diagrams/database/database-er-knowledge-ingest.puml`

#### 知识库与文档入库模块讲解

这张图用于说明项目中的内容如何被组织、上传、版本化和处理。对照图片时，可以按“工作区 -> 知识库 -> 文档资产 -> 文档版本”的方向阅读。

`workspaces` 是入库数据的上层边界。一个工作区下可以有多个 `knowledge_bases`，也可以有多个 `ingest_documents`。这样设计的目的是让知识库、文档、成员和审计都能落在同一个业务空间里，后续做多团队或多组织隔离时，不需要重新改造核心表。

`knowledge_bases` 是知识库主数据表。它保存 `kb_id`、`workspace_id`、名称、描述和状态。图中 `UK(workspace_id, kb_id)` 表示同一个工作区内知识库业务标识唯一，`CK(status IN ACTIVE, INACTIVE, DELETED)` 表示知识库有明确生命周期。`ACTIVE` 表示正常可用，`INACTIVE` 表示停用，`DELETED` 表示软删除。这里使用软删除，是因为知识库下面可能已经有文档、版本、向量、授权和审计记录，直接物理删除会破坏历史追溯。

`ingest_documents` 是文档资产主表。它不是简单的“文件表”，而是保存一个长期存在的文档资产身份。比如用户第一次上传“项目说明书.pdf”时，系统生成一个 `document_id`；之后再次上传新版本、回滚或重处理，都仍然围绕这个 `document_id` 展开。表中的 `filename`、`file_size`、`status`、`processing_metadata` 等字段保留当前镜像信息，而 `latest_version_number`、`latest_status`、`latest_filename`、`latest_version_origin_type` 是最新版本投影，方便列表页、详情页和状态页快速展示当前文档头部状态。

`ingest_document_versions` 是文档版本事实表。它保存每一次上传或回滚形成的版本事实，包括 `version_number`、`version_origin_type`、`rollback_from_version_number`、版本文件哈希、文件名、文件大小、处理状态、处理元数据和创建人。图中 `UK(document_id, version_number)` 表示同一个文档下版本号不能重复。`documents ||--o{ versions` 表示一个文档资产可以有多个版本事实。

这张图中最重要的设计取舍是“文档资产主表”和“文档版本事实表”分离。`ingest_documents` 负责稳定身份和最新投影，适合列表、详情和当前状态查询；`ingest_document_versions` 负责不可混淆的版本事实，适合版本历史、回滚、问答引用和处理失败追踪。如果没有版本表，每次上传新版本都会覆盖旧数据，系统就无法回答“历史上有哪些版本”“回答引用的是哪个版本”“新版本失败时能不能继续使用旧版本”等问题。

图中 `knowledge_bases ||..o{ documents` 使用虚线，是因为文档通过 `workspace_id + kb_id` 逻辑归属知识库，当前没有声明数据库外键。这不代表文档可以随意归属不存在的知识库，而是说明这条关系主要由应用层在上传、列表和问答入口校验。这样保留了早期迁移和业务键演进的弹性。与之相比，`documents ||--o{ versions` 使用实线外键，因为版本事实必须严格从属于文档资产，删除文档时版本事实需要级联删除。

对照业务流程理解这张图：用户在工作区内创建知识库，上传文档时系统先确认知识库存在且状态可用，然后写入或更新 `ingest_documents`，同时在 `ingest_document_versions` 中创建一个版本事实。后台处理流程解析文档、清洗文本、分块、写入向量后，会更新版本状态，并同步主表 latest projection。这样前台可以快速看到文档最新状态，问答模块也可以精确选择可用版本。

### 问答向量检索模块

图源文件：`docs/architecture/diagrams/database/database-er-qa-vector.puml`

#### 问答向量检索模块讲解

这张图用于说明 RAG 问答如何从业务数据走到向量检索。对照图片时，可以从上到下看：知识库限定问题范围，文档承载内容资产，版本决定可引用内容，向量分块负责语义召回。

`knowledge_bases` 在问答链路中承担检索范围入口。用户提问时一般会选择某个知识库，或者系统根据当前页面上下文确定知识库范围。`workspace_id` 和 `kb_id` 一起用于定位这个知识库所在的业务空间，避免跨工作区检索。

`ingest_documents` 在问答链路中承担文档资产过滤职责。它包含 `workspace_id`、`kb_id`、`latest_version_number` 和 `latest_status`。问答前，系统不会直接检索所有向量，而是先根据知识库、文档状态和授权规则计算出用户可访问的文档集合。`latest_status` 可以帮助判断最新版本是否已经处理完成；当最新版本不可问答时，系统还可以通过版本表查找合适的已索引版本。

`ingest_document_versions` 在问答链路中承担版本边界职责。RAG 系统不仅要知道“召回了哪个文档”，还要知道“召回的是这个文档的哪个版本”。版本表里的 `version_number`、`status`、`split_version` 和 `updated_at` 能让系统判断某个版本是否已经完成索引、分块策略是哪一版、引用是否已经落后于最新版本。这样前端展示引用来源时，可以告诉用户回答引用了哪个文档版本，也能提示引用是否陈旧。

`vector_store` 是 Spring AI PGVector 使用的向量表，保存 `content`、`metadata` 和 `embedding vector(1024)`。其中 `content` 是分块文本，`embedding` 是语义向量，`metadata` 是连接业务世界和向量世界的关键字段。图右侧注释列出了 metadata 中用于过滤的核心信息：`workspaceId`、`kbId`、`documentId`、`documentVersionNumber`、`splitVersion`。这些字段让系统可以在向量检索阶段直接过滤掉不属于当前工作区、不属于当前知识库、用户无权访问或版本不正确的分块。

这张图中的 `versions ||..o{ vectors` 是虚线，表示文档版本和向量分块之间是 metadata 约定关系，而不是数据库外键。这样设计主要是为了兼容 Spring AI PGVector 的表结构和写入方式。向量表的核心职责是相似度检索，它需要 HNSW 索引和 metadata 过滤，而不是复杂外键约束。业务一致性由入库流程保证：处理某个文档版本时，系统用这个版本的信息生成分块，并把对应的业务标识写入 metadata。

对照问答流程理解这张图：用户发起问题后，应用层先校验用户身份和资源授权，再确定可问答的知识库、文档和版本范围；随后把这些范围转换成 `vector_store.metadata` 过滤条件，并结合 embedding 相似度做召回；最后回答生成模块只基于召回到的、有权限且版本正确的分块生成答案。也就是说，这张图强调的不是“向量表能搜到相似文本”这么简单，而是“向量检索必须服从权限边界和版本边界”。

如果没有 `ingest_document_versions` 和 metadata 中的版本字段，问答只能知道分块来自哪个文档，很难判断它是否来自最新或可用版本。如果没有 `workspaceId`、`kbId`、`documentId` 等 metadata，向量检索就可能跨知识库、跨工作区或越权召回。因此问答模块的数据库设计重点，是把语义检索和业务治理连接起来。

### 审计治理模块

图源文件：`docs/architecture/diagrams/database/database-er-audit.puml`

#### 审计治理模块讲解

这张图用于说明系统如何记录关键操作流水。它比其他模块简单，但对管理系统非常重要。对照图片时，可以把它理解为“谁在什么工作区，对什么目标，做了什么，结果如何”。

`workspaces` 表示审计事件发生的业务空间。多数管理操作、授权操作和文档操作都应该归属到某个工作区，这样管理端可以按工作区查询事件。图中 `workspaces ||--o{ audit_events` 表示一个工作区可以产生多条审计事件。

`users` 表示操作人。`audit_events.actor_user_id` 通过外键关联 `users.user_id`，同时 `actor_username` 保存操作人用户名快照。这里同时保存 ID 和用户名快照，是为了兼顾一致性和可读性。`actor_user_id` 便于关联用户身份，`actor_username` 便于审计列表直接展示当时的操作者名称。用户删除时，`actor_user_id` 可以置空，但审计事件仍然保留用户名快照，避免历史记录完全失去上下文。

`audit_events` 是审计事件主表。核心字段可以按四组理解：第一组是归属和操作人，包括 `workspace_id`、`actor_user_id`、`actor_username`；第二组是事件类型和目标，包括 `event_type`、`target_type`、`target_id`；第三组是结果，包括 `outcome` 和 `reason`；第四组是扩展上下文和时间，包括 `metadata` 和 `occurred_at`。`outcome` 受检查约束限制为 `SUCCESS`、`FAILURE`、`DENIED`，分别表示成功、失败和被拒绝。

这张图里最需要解释的是 `target_type + target_id` 为什么没有对每一种目标表建立外键。审计事件可能指向很多对象，例如账号、工作区成员、知识库、文档、知识库授权、文档授权、文档处理任务等。如果为每一种目标都建立外键，审计表会变得复杂，而且目标被软删除或物理删除后，审计记录可能受到影响。当前设计采用通用目标字段保存业务标识，保证审计表能长期保留历史事实。

对照业务流程理解，账号创建、密码重置、成员角色调整、知识库授权变更、文档授权变更、文档上传、处理失败、知识库软删除等操作，都可以在 `audit_events` 中留下结构化记录。管理端可以按 `workspace_id + occurred_at` 查询一个工作区最近发生了什么，也可以按 `actor_user_id + occurred_at` 查询某个用户做过什么，还可以按 `event_type + occurred_at` 查询某类事件的历史。

审计治理模块的价值不在于直接参与主业务查询，而是在系统出问题时提供证据链。例如出现越权访问争议时，可以查谁给用户授过权；出现文档误删时，可以查删除操作的操作者、目标和结果；出现处理失败时，可以用 `metadata` 保存错误上下文，方便排查。对于一个有权限和知识资产管理能力的 RAG 系统来说，审计表是治理能力的一部分，而不是可有可无的日志替代品。

## 完整关系数据表

### workspaces

| 字段         | 类型         | 键  | 说明       |
| ------------ | ------------ | --- | ---------- |
| workspace_id | varchar(64)  | PK  | 工作区标识 |
| name         | varchar(100) |     | 工作区名称 |
| description  | varchar(500) |     | 工作区描述 |
| status       | varchar(16)  |     | 工作区状态 |
| created_at   | timestamptz  |     | 创建时间   |
| updated_at   | timestamptz  |     | 更新时间   |

### users

| 字段         | 类型         | 键  | 说明                                     |
| ------------ | ------------ | --- | ---------------------------------------- |
| user_id      | varchar(64)  | PK  | 用户标识                                 |
| username     | varchar(100) | UK  | 登录用户名，唯一索引 `uk_users_username` |
| display_name | varchar(100) |     | 展示名称                                 |
| status       | varchar(16)  |     | 用户状态                                 |
| created_at   | timestamptz  |     | 创建时间                                 |
| updated_at   | timestamptz  |     | 更新时间                                 |

### local_credentials

| 字段                | 类型         | 键     | 说明                           |
| ------------------- | ------------ | ------ | ------------------------------ |
| user_id             | varchar(64)  | PK, FK | 关联 `users.user_id`，级联删除 |
| password_hash       | varchar(255) |        | 密码哈希                       |
| password_algo       | varchar(32)  |        | 密码算法                       |
| password_updated_at | timestamptz  |        | 密码更新时间                   |
| created_at          | timestamptz  |        | 创建时间                       |
| updated_at          | timestamptz  |        | 更新时间                       |

### workspace_memberships

| 字段          | 类型        | 键     | 说明                                                           |
| ------------- | ----------- | ------ | -------------------------------------------------------------- |
| membership_id | bigserial   | PK     | 成员关系主键                                                   |
| workspace_id  | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`，与 `user_id` 组成唯一索引      |
| user_id       | varchar(64) | FK, UK | 关联 `users.user_id`，级联删除；与 `workspace_id` 组成唯一索引 |
| role          | varchar(32) |        | 工作区角色                                                     |
| status        | varchar(16) |        | 成员状态                                                       |
| created_at    | timestamptz |        | 创建时间                                                       |
| updated_at    | timestamptz |        | 更新时间                                                       |

### login_lock_states

| 字段               | 类型        | 键     | 说明                           |
| ------------------ | ----------- | ------ | ------------------------------ |
| user_id            | varchar(64) | PK, FK | 关联 `users.user_id`，级联删除 |
| failed_login_count | int         |        | 连续登录失败次数               |
| locked_until       | timestamptz |        | 锁定截止时间                   |
| last_failed_at     | timestamptz |        | 最近失败时间                   |
| last_login_at      | timestamptz |        | 最近成功登录时间               |
| updated_at         | timestamptz |        | 更新时间                       |

### knowledge_bases

| 字段         | 类型         | 键     | 说明                                                    |
| ------------ | ------------ | ------ | ------------------------------------------------------- |
| id           | bigserial    | PK     | 自增主键                                                |
| kb_id        | varchar(64)  | UK     | 知识库业务标识，唯一索引 `uk_knowledge_bases_kb_id`     |
| workspace_id | varchar(64)  | FK, UK | 关联 `workspaces.workspace_id`，与 `kb_id` 组成唯一索引 |
| name         | varchar(100) |        | 知识库名称                                              |
| description  | varchar(500) |        | 知识库描述                                              |
| status       | varchar(16)  | CK     | 知识库状态：`ACTIVE`、`INACTIVE`、`DELETED`             |
| created_at   | timestamptz  |        | 创建时间                                                |
| updated_at   | timestamptz  |        | 更新时间                                                |

### ingest_documents

| 字段                       | 类型         | 键         | 说明                                                          |
| -------------------------- | ------------ | ---------- | ------------------------------------------------------------- |
| document_id                | varchar(64)  | PK, UK     | 文档资产标识；与 `workspace_id` 组成唯一索引                  |
| kb_id                      | varchar(128) | Logical FK | 逻辑关联 `knowledge_bases.kb_id`                              |
| workspace_id               | varchar(64)  | FK, UK     | 关联 `workspaces.workspace_id`；与 `document_id` 组成唯一索引 |
| file_hash                  | varchar(64)  | UK         | 文件哈希；与 `kb_id` 组成非删除文档唯一索引                   |
| filename                   | varchar(512) |            | 当前镜像文件名                                                |
| file_size                  | bigint       |            | 当前镜像文件大小                                              |
| status                     | varchar(32)  |            | 当前镜像处理状态                                              |
| failure_reason             | text         |            | 失败原因                                                      |
| retry_count                | int          |            | 当前镜像重试次数                                              |
| retry_max                  | int          |            | 最大重试次数                                                  |
| next_retry_at              | timestamptz  |            | 下次重试时间                                                  |
| last_error_code            | varchar(64)  |            | 最近错误码                                                    |
| last_error_message         | text         |            | 最近错误信息                                                  |
| last_error_at              | timestamptz  |            | 最近错误时间                                                  |
| reprocess_count            | int          |            | 重处理次数                                                    |
| reprocess_requested_at     | timestamptz  |            | 重处理请求时间                                                |
| split_version              | varchar(32)  |            | 当前镜像分块版本                                              |
| processing_metadata        | jsonb        |            | 解析、清洗、分块等处理元数据                                  |
| latest_version_number      | int          |            | 最新版本号投影                                                |
| latest_status              | varchar(32)  |            | 最新版本状态投影                                              |
| latest_filename            | varchar(512) |            | 最新版本文件名投影                                            |
| latest_version_origin_type | varchar(32)  |            | 最新版本来源类型投影                                          |
| created_at                 | timestamptz  |            | 创建时间                                                      |
| updated_at                 | timestamptz  |            | 更新时间                                                      |

### ingest_document_versions

| 字段                         | 类型         | 键         | 说明                                                                            |
| ---------------------------- | ------------ | ---------- | ------------------------------------------------------------------------------- |
| id                           | bigserial    | PK         | 版本事实自增主键                                                                |
| document_id                  | varchar(64)  | FK, UK     | 关联 `ingest_documents.document_id`，级联删除；与 `version_number` 组成唯一索引 |
| version_number               | int          | UK         | 文档版本号                                                                      |
| version_origin_type          | varchar(32)  |            | 版本来源类型                                                                    |
| rollback_from_version_number | int          |            | 回滚来源版本号                                                                  |
| file_hash                    | varchar(64)  | IDX        | 版本文件哈希                                                                    |
| filename                     | varchar(512) |            | 版本文件名                                                                      |
| file_size                    | bigint       |            | 版本文件大小                                                                    |
| status                       | varchar(32)  |            | 版本处理状态                                                                    |
| failure_reason               | text         |            | 失败原因                                                                        |
| retry_count                  | int          |            | 重试次数                                                                        |
| retry_max                    | int          |            | 最大重试次数                                                                    |
| next_retry_at                | timestamptz  |            | 下次重试时间                                                                    |
| last_error_code              | varchar(64)  |            | 最近错误码                                                                      |
| last_error_message           | text         |            | 最近错误信息                                                                    |
| last_error_at                | timestamptz  |            | 最近错误时间                                                                    |
| reprocess_count              | int          |            | 重处理次数                                                                      |
| reprocess_requested_at       | timestamptz  |            | 重处理请求时间                                                                  |
| split_version                | varchar(32)  |            | 分块版本                                                                        |
| processing_metadata          | jsonb        |            | 处理元数据                                                                      |
| created_by_user_id           | varchar(64)  | Logical FK | 逻辑关联 `users.user_id`                                                        |
| created_at                   | timestamptz  |            | 创建时间                                                                        |
| updated_at                   | timestamptz  |            | 更新时间                                                                        |

### vector_store

| 字段      | 类型         | 键         | 说明                                                                                              |
| --------- | ------------ | ---------- | ------------------------------------------------------------------------------------------------- |
| id        | uuid         | PK         | 向量分块标识，默认 `uuid_generate_v4()`                                                           |
| content   | text         |            | 分块文本                                                                                          |
| metadata  | json/jsonb   | Logical FK | Spring AI 元数据；通过 `documentId`、`kbId`、`documentVersionNumber`、`splitVersion` 关联业务数据 |
| embedding | vector(1024) | IDX        | 向量字段，HNSW 余弦索引                                                                           |

### knowledge_base_grants

| 字段         | 类型        | 键     | 说明                                                                      |
| ------------ | ----------- | ------ | ------------------------------------------------------------------------- |
| grant_id     | bigserial   | PK     | 授权记录主键                                                              |
| workspace_id | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`；与 `kb_id`、`user_id` 组成唯一索引        |
| kb_id        | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `knowledge_bases(workspace_id, kb_id)`         |
| user_id      | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `workspace_memberships(workspace_id, user_id)` |
| role         | varchar(32) | CK     | 知识库角色：`KB_MANAGER`、`KB_CONTRIBUTOR`、`KB_READER`、`KB_ASKER`       |
| status       | varchar(16) | CK     | 授权状态：`ACTIVE`、`DISABLED`                                            |
| created_at   | timestamptz |        | 创建时间                                                                  |
| updated_at   | timestamptz |        | 更新时间                                                                  |

### document_grants

| 字段         | 类型        | 键     | 说明                                                                      |
| ------------ | ----------- | ------ | ------------------------------------------------------------------------- |
| grant_id     | bigserial   | PK     | 授权记录主键                                                              |
| workspace_id | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`；与 `document_id`、`user_id` 组成唯一索引  |
| document_id  | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `ingest_documents(workspace_id, document_id)`  |
| user_id      | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `workspace_memberships(workspace_id, user_id)` |
| permission   | varchar(32) | CK     | 文档权限：`DOC_ALLOW_READ`、`DOC_ALLOW_MANAGE`、`DOC_DENY`                |
| status       | varchar(16) | CK     | 授权状态：`ACTIVE`、`DISABLED`                                            |
| created_at   | timestamptz |        | 创建时间                                                                  |
| updated_at   | timestamptz |        | 更新时间                                                                  |

### audit_events

| 字段           | 类型         | 键  | 说明                                   |
| -------------- | ------------ | --- | -------------------------------------- |
| audit_event_id | bigserial    | PK  | 审计事件主键                           |
| workspace_id   | varchar(64)  | FK  | 关联 `workspaces.workspace_id`，可为空 |
| actor_user_id  | varchar(64)  | FK  | 关联 `users.user_id`，用户删除时置空   |
| actor_username | varchar(100) |     | 操作人用户名快照                       |
| event_type     | varchar(64)  | IDX | 事件类型                               |
| target_type    | varchar(32)  |     | 目标类型                               |
| target_id      | varchar(128) |     | 目标标识                               |
| outcome        | varchar(16)  | CK  | 结果：`SUCCESS`、`FAILURE`、`DENIED`   |
| reason         | varchar(255) |     | 原因说明                               |
| metadata       | jsonb        |     | 审计扩展元数据                         |
| occurred_at    | timestamptz  | IDX | 发生时间                               |

## 关系补充

| 关系                                                | 类型       | 说明                                                                          |
| --------------------------------------------------- | ---------- | ----------------------------------------------------------------------------- |
| `workspaces` 1:N `workspace_memberships`            | FK         | 一个工作区包含多个成员关系                                                    |
| `users` 1:N `workspace_memberships`                 | FK         | 一个用户可加入多个工作区                                                      |
| `users` 1:1 `local_credentials`                     | FK         | 本地账号密码凭据                                                              |
| `users` 1:0..1 `login_lock_states`                  | FK         | 登录锁定状态                                                                  |
| `workspaces` 1:N `knowledge_bases`                  | FK         | 工作区下管理多个知识库                                                        |
| `workspaces` 1:N `ingest_documents`                 | FK         | 工作区下管理多个文档资产                                                      |
| `knowledge_bases` 1:N `ingest_documents`            | Logical FK | 通过 `workspace_id + kb_id` 形成业务关系，当前未声明数据库外键                |
| `ingest_documents` 1:N `ingest_document_versions`   | FK         | 一个文档资产拥有多个版本事实                                                  |
| `workspace_memberships` 1:N `knowledge_base_grants` | FK         | 知识库授权绑定工作区成员                                                      |
| `knowledge_bases` 1:N `knowledge_base_grants`       | FK         | 知识库被授权给成员                                                            |
| `workspace_memberships` 1:N `document_grants`       | FK         | 文档授权绑定工作区成员                                                        |
| `ingest_documents` 1:N `document_grants`            | FK         | 文档被授权给成员                                                              |
| `ingest_documents` 1:N `vector_store`               | Logical FK | 通过 `metadata.documentId` 关联                                               |
| `ingest_document_versions` 1:N `vector_store`       | Logical FK | 通过 `metadata.documentId + metadata.documentVersionNumber/splitVersion` 关联 |
| `knowledge_bases` 1:N `vector_store`                | Logical FK | 通过 `metadata.kbId` 关联                                                     |
| `users` 1:N `audit_events`                          | FK         | 通过 `actor_user_id` 关联，删除用户时置空                                     |
| `workspaces` 1:N `audit_events`                     | FK         | 审计事件归属工作区                                                            |
