# 数据库设计说明

本文档用于论文与项目文档中的数据库设计章节。图谱按阅读场景拆分：

- 首页总览：`docs/architecture/diagrams/database/database-er-overview.puml`
- 账号与权限模块：`docs/architecture/diagrams/database/database-er-auth-access.puml`
- 知识库与文档入库模块：`docs/architecture/diagrams/database/database-er-knowledge-ingest.puml`
- 问答向量检索模块：`docs/architecture/diagrams/database/database-er-qa-vector.puml`
- 审计治理模块：`docs/architecture/diagrams/database/database-er-audit.puml`

说明：表结构以 `src/main/resources/db/migration/V1__auth_flyway_schema.sql` 到 `V8__add_deleted_knowledge_base_status.sql` 为准。ER 图中实线表示数据库外键，虚线表示业务或元数据约定关系。

V8 补充了 `knowledge_bases.status` 的生命周期约束，知识库支持 `ACTIVE`、`INACTIVE`、`DELETED` 三种状态；`DELETED` 表示软删除，业务列表、上传、问答和授权治理默认排除该状态。

## 总览精简 ER 图

论文首页建议只放总览精简 ER 图，保留“用户、工作区、知识库、文档、文档版本、向量分块”的主链路。

图源文件：`docs/architecture/diagrams/database/database-er-overview.puml`

## 分模块详细 ER 图

### 账号与权限模块

图源文件：`docs/architecture/diagrams/database/database-er-auth-access.puml`

### 知识库与文档入库模块

图源文件：`docs/architecture/diagrams/database/database-er-knowledge-ingest.puml`

### 问答向量检索模块

图源文件：`docs/architecture/diagrams/database/database-er-qa-vector.puml`

### 审计治理模块

图源文件：`docs/architecture/diagrams/database/database-er-audit.puml`

## 完整关系数据表

### workspaces

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| workspace_id | varchar(64) | PK | 工作区标识 |
| name | varchar(100) |  | 工作区名称 |
| description | varchar(500) |  | 工作区描述 |
| status | varchar(16) |  | 工作区状态 |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### users

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| user_id | varchar(64) | PK | 用户标识 |
| username | varchar(100) | UK | 登录用户名，唯一索引 `uk_users_username` |
| display_name | varchar(100) |  | 展示名称 |
| status | varchar(16) |  | 用户状态 |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### local_credentials

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| user_id | varchar(64) | PK, FK | 关联 `users.user_id`，级联删除 |
| password_hash | varchar(255) |  | 密码哈希 |
| password_algo | varchar(32) |  | 密码算法 |
| password_updated_at | timestamptz |  | 密码更新时间 |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### workspace_memberships

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| membership_id | bigserial | PK | 成员关系主键 |
| workspace_id | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`，与 `user_id` 组成唯一索引 |
| user_id | varchar(64) | FK, UK | 关联 `users.user_id`，级联删除；与 `workspace_id` 组成唯一索引 |
| role | varchar(32) |  | 工作区角色 |
| status | varchar(16) |  | 成员状态 |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### login_lock_states

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| user_id | varchar(64) | PK, FK | 关联 `users.user_id`，级联删除 |
| failed_login_count | int |  | 连续登录失败次数 |
| locked_until | timestamptz |  | 锁定截止时间 |
| last_failed_at | timestamptz |  | 最近失败时间 |
| last_login_at | timestamptz |  | 最近成功登录时间 |
| updated_at | timestamptz |  | 更新时间 |

### knowledge_bases

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| id | bigserial | PK | 自增主键 |
| kb_id | varchar(64) | UK | 知识库业务标识，唯一索引 `uk_knowledge_bases_kb_id` |
| workspace_id | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`，与 `kb_id` 组成唯一索引 |
| name | varchar(100) |  | 知识库名称 |
| description | varchar(500) |  | 知识库描述 |
| status | varchar(16) | CK | 知识库状态：`ACTIVE`、`INACTIVE`、`DELETED` |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### ingest_documents

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| document_id | varchar(64) | PK, UK | 文档资产标识；与 `workspace_id` 组成唯一索引 |
| kb_id | varchar(128) | Logical FK | 逻辑关联 `knowledge_bases.kb_id` |
| workspace_id | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`；与 `document_id` 组成唯一索引 |
| file_hash | varchar(64) | UK | 文件哈希；与 `kb_id` 组成非删除文档唯一索引 |
| filename | varchar(512) |  | 当前镜像文件名 |
| file_size | bigint |  | 当前镜像文件大小 |
| status | varchar(32) |  | 当前镜像处理状态 |
| failure_reason | text |  | 失败原因 |
| retry_count | int |  | 当前镜像重试次数 |
| retry_max | int |  | 最大重试次数 |
| next_retry_at | timestamptz |  | 下次重试时间 |
| last_error_code | varchar(64) |  | 最近错误码 |
| last_error_message | text |  | 最近错误信息 |
| last_error_at | timestamptz |  | 最近错误时间 |
| reprocess_count | int |  | 重处理次数 |
| reprocess_requested_at | timestamptz |  | 重处理请求时间 |
| split_version | varchar(32) |  | 当前镜像分块版本 |
| processing_metadata | jsonb |  | 解析、清洗、分块等处理元数据 |
| latest_version_number | int |  | 最新版本号投影 |
| latest_status | varchar(32) |  | 最新版本状态投影 |
| latest_filename | varchar(512) |  | 最新版本文件名投影 |
| latest_version_origin_type | varchar(32) |  | 最新版本来源类型投影 |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### ingest_document_versions

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| id | bigserial | PK | 版本事实自增主键 |
| document_id | varchar(64) | FK, UK | 关联 `ingest_documents.document_id`，级联删除；与 `version_number` 组成唯一索引 |
| version_number | int | UK | 文档版本号 |
| version_origin_type | varchar(32) |  | 版本来源类型 |
| rollback_from_version_number | int |  | 回滚来源版本号 |
| file_hash | varchar(64) | IDX | 版本文件哈希 |
| filename | varchar(512) |  | 版本文件名 |
| file_size | bigint |  | 版本文件大小 |
| status | varchar(32) |  | 版本处理状态 |
| failure_reason | text |  | 失败原因 |
| retry_count | int |  | 重试次数 |
| retry_max | int |  | 最大重试次数 |
| next_retry_at | timestamptz |  | 下次重试时间 |
| last_error_code | varchar(64) |  | 最近错误码 |
| last_error_message | text |  | 最近错误信息 |
| last_error_at | timestamptz |  | 最近错误时间 |
| reprocess_count | int |  | 重处理次数 |
| reprocess_requested_at | timestamptz |  | 重处理请求时间 |
| split_version | varchar(32) |  | 分块版本 |
| processing_metadata | jsonb |  | 处理元数据 |
| created_by_user_id | varchar(64) | Logical FK | 逻辑关联 `users.user_id` |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### vector_store

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| id | uuid | PK | 向量分块标识，默认 `uuid_generate_v4()` |
| content | text |  | 分块文本 |
| metadata | json/jsonb | Logical FK | Spring AI 元数据；通过 `documentId`、`kbId`、`documentVersionNumber`、`splitVersion` 关联业务数据 |
| embedding | vector(1024) | IDX | 向量字段，HNSW 余弦索引 |

### knowledge_base_grants

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| grant_id | bigserial | PK | 授权记录主键 |
| workspace_id | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`；与 `kb_id`、`user_id` 组成唯一索引 |
| kb_id | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `knowledge_bases(workspace_id, kb_id)` |
| user_id | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `workspace_memberships(workspace_id, user_id)` |
| role | varchar(32) | CK | 知识库角色：`KB_MANAGER`、`KB_CONTRIBUTOR`、`KB_READER`、`KB_ASKER` |
| status | varchar(16) | CK | 授权状态：`ACTIVE`、`DISABLED` |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### document_grants

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| grant_id | bigserial | PK | 授权记录主键 |
| workspace_id | varchar(64) | FK, UK | 关联 `workspaces.workspace_id`；与 `document_id`、`user_id` 组成唯一索引 |
| document_id | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `ingest_documents(workspace_id, document_id)` |
| user_id | varchar(64) | FK, UK | 与 `workspace_id` 共同关联 `workspace_memberships(workspace_id, user_id)` |
| permission | varchar(32) | CK | 文档权限：`DOC_ALLOW_READ`、`DOC_ALLOW_MANAGE`、`DOC_DENY` |
| status | varchar(16) | CK | 授权状态：`ACTIVE`、`DISABLED` |
| created_at | timestamptz |  | 创建时间 |
| updated_at | timestamptz |  | 更新时间 |

### audit_events

| 字段 | 类型 | 键 | 说明 |
| --- | --- | --- | --- |
| audit_event_id | bigserial | PK | 审计事件主键 |
| workspace_id | varchar(64) | FK | 关联 `workspaces.workspace_id`，可为空 |
| actor_user_id | varchar(64) | FK | 关联 `users.user_id`，用户删除时置空 |
| actor_username | varchar(100) |  | 操作人用户名快照 |
| event_type | varchar(64) | IDX | 事件类型 |
| target_type | varchar(32) |  | 目标类型 |
| target_id | varchar(128) |  | 目标标识 |
| outcome | varchar(16) | CK | 结果：`SUCCESS`、`FAILURE`、`DENIED` |
| reason | varchar(255) |  | 原因说明 |
| metadata | jsonb |  | 审计扩展元数据 |
| occurred_at | timestamptz | IDX | 发生时间 |

## 关系补充

| 关系 | 类型 | 说明 |
| --- | --- | --- |
| `workspaces` 1:N `workspace_memberships` | FK | 一个工作区包含多个成员关系 |
| `users` 1:N `workspace_memberships` | FK | 一个用户可加入多个工作区 |
| `users` 1:1 `local_credentials` | FK | 本地账号密码凭据 |
| `users` 1:0..1 `login_lock_states` | FK | 登录锁定状态 |
| `workspaces` 1:N `knowledge_bases` | FK | 工作区下管理多个知识库 |
| `workspaces` 1:N `ingest_documents` | FK | 工作区下管理多个文档资产 |
| `knowledge_bases` 1:N `ingest_documents` | Logical FK | 通过 `workspace_id + kb_id` 形成业务关系，当前未声明数据库外键 |
| `ingest_documents` 1:N `ingest_document_versions` | FK | 一个文档资产拥有多个版本事实 |
| `workspace_memberships` 1:N `knowledge_base_grants` | FK | 知识库授权绑定工作区成员 |
| `knowledge_bases` 1:N `knowledge_base_grants` | FK | 知识库被授权给成员 |
| `workspace_memberships` 1:N `document_grants` | FK | 文档授权绑定工作区成员 |
| `ingest_documents` 1:N `document_grants` | FK | 文档被授权给成员 |
| `ingest_documents` 1:N `vector_store` | Logical FK | 通过 `metadata.documentId` 关联 |
| `ingest_document_versions` 1:N `vector_store` | Logical FK | 通过 `metadata.documentId + metadata.documentVersionNumber/splitVersion` 关联 |
| `knowledge_bases` 1:N `vector_store` | Logical FK | 通过 `metadata.kbId` 关联 |
| `users` 1:N `audit_events` | FK | 通过 `actor_user_id` 关联，删除用户时置空 |
| `workspaces` 1:N `audit_events` | FK | 审计事件归属工作区 |
