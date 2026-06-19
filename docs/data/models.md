# 数据模型文档

## 数据库概览

- **数据库**: PostgreSQL 16 + pgvector 扩展
- **迁移管理**: Flyway（8 个迁移脚本 V1-V8）
- **存储策略**: 业务数据（关系表）+ 向量数据（vector_store）共用同一 PostgreSQL 实例

### PostgreSQL 扩展

```sql
CREATE EXTENSION IF NOT EXISTS vector;     -- PGVector 向量支持
CREATE EXTENSION IF NOT EXISTS hstore;     -- hstore 键值类型
CREATE EXTENSION IF NOT EXISTS "uuid-ossp"; -- uuid_generate_v4() 依赖
```

---

## 表清单（按子域分组）

### Auth 表组

#### `workspaces`（V1）

工作区，当前单工作区模式，预置 `workspace_id = 'default'`。

| 列名 | 类型 | 约束 |
|---|---|---|
| workspace_id | VARCHAR(64) | PK |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(500) | NOT NULL DEFAULT '' |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

预置数据: `workspace_id = 'default'`, `name = 'default'`

#### `users`（V1）

用户表。

| 列名 | 类型 | 约束 |
|---|---|---|
| user_id | VARCHAR(64) | PK |
| username | VARCHAR(100) | NOT NULL, UNIQUE |
| display_name | VARCHAR(100) | NOT NULL DEFAULT '' |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

#### `local_credentials`（V1）

本地认证凭证。

| 列名 | 类型 | 约束 |
|---|---|---|
| user_id | VARCHAR(64) | PK, FK → users(user_id) ON DELETE CASCADE |
| password_hash | VARCHAR(255) | NOT NULL |
| password_algo | VARCHAR(32) | NOT NULL DEFAULT 'bcrypt' |
| password_updated_at | TIMESTAMPTZ | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

#### `workspace_memberships`（V1）

工作区成员关系。

| 列名 | 类型 | 约束 |
|---|---|---|
| membership_id | BIGSERIAL | PK |
| workspace_id | VARCHAR(64) | NOT NULL, FK → workspaces |
| user_id | VARCHAR(64) | NOT NULL, FK → users ON DELETE CASCADE |
| role | VARCHAR(32) | NOT NULL（OWNER/ADMIN/MEMBER） |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE' |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

唯一索引: `(workspace_id, user_id)`

#### `login_lock_states`（V1）

登录失败锁定状态。

| 列名 | 类型 | 约束 |
|---|---|---|
| user_id | VARCHAR(64) | PK, FK → users ON DELETE CASCADE |
| failed_login_count | INT | NOT NULL DEFAULT 0 |
| locked_until | TIMESTAMPTZ | 可空 |
| last_failed_at | TIMESTAMPTZ | 可空 |
| last_login_at | TIMESTAMPTZ | 可空 |
| updated_at | TIMESTAMPTZ | NOT NULL |

#### `audit_events`（V2）

审计事件（append-only）。

| 列名 | 类型 | 约束 |
|---|---|---|
| audit_event_id | BIGSERIAL | PK |
| workspace_id | VARCHAR(64) | FK → workspaces |
| actor_user_id | VARCHAR(64) | FK → users ON DELETE SET NULL |
| actor_username | VARCHAR(100) | |
| event_type | VARCHAR(64) | NOT NULL |
| target_type | VARCHAR(32) | |
| target_id | VARCHAR(128) | |
| outcome | VARCHAR(16) | NOT NULL, CHECK (SUCCESS/FAILURE/DENIED) |
| reason | VARCHAR(255) | NOT NULL DEFAULT '' |
| metadata | JSONB | NOT NULL DEFAULT '{}' |
| occurred_at | TIMESTAMPTZ | NOT NULL DEFAULT CURRENT_TIMESTAMP |

索引: `(workspace_id, occurred_at DESC)`, `(actor_user_id, occurred_at DESC)`, `(event_type, occurred_at DESC)`

---

### Grant 授权表组

#### `knowledge_base_grants`（V2，V3 修改外键）

知识库级授权。

| 列名 | 类型 | 约束 |
|---|---|---|
| grant_id | BIGSERIAL | PK |
| workspace_id | VARCHAR(64) | NOT NULL, FK → workspaces |
| kb_id | VARCHAR(64) | NOT NULL, FK → knowledge_bases(workspace_id, kb_id) ON DELETE CASCADE |
| user_id | VARCHAR(64) | NOT NULL, FK → workspace_memberships(workspace_id, user_id) ON DELETE CASCADE |
| role | VARCHAR(32) | NOT NULL, CHECK (KB_MANAGER/KB_CONTRIBUTOR/KB_READER/KB_ASKER) |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE', CHECK (status IN ('ACTIVE', 'DISABLED')) |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

唯一索引: `(workspace_id, kb_id, user_id)`
索引: `(user_id, status)`

> **V3 迁移说明**: V3 将授权表外键从 `users(user_id)` 改为 `workspace_memberships(workspace_id, user_id)` 复合键，确保授权绑定到工作区成员身份，而非仅用户身份。

#### `document_grants`（V2，V3 修改外键）

文档级授权覆盖。

| 列名 | 类型 | 约束 |
|---|---|---|
| grant_id | BIGSERIAL | PK |
| workspace_id | VARCHAR(64) | NOT NULL, FK → workspaces |
| document_id | VARCHAR(64) | NOT NULL, FK → ingest_documents(workspace_id, document_id) ON DELETE CASCADE |
| user_id | VARCHAR(64) | NOT NULL, FK → workspace_memberships(workspace_id, user_id) ON DELETE CASCADE |
| permission | VARCHAR(32) | NOT NULL, CHECK (DOC_ALLOW_READ/DOC_ALLOW_MANAGE/DOC_DENY) |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE', CHECK (status IN ('ACTIVE', 'DISABLED')) |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

唯一索引: `(workspace_id, document_id, user_id)`
索引: `(user_id, status)`

---

### Ingest 表组

#### `ingest_documents`（V1，V4/V5 增强）

文档资产主表 + 最新版本投影。

| 列名 | 类型 | 约束 | 说明 |
|---|---|---|---|
| document_id | VARCHAR(64) | PK | 文档资产 ID |
| kb_id | VARCHAR(128) | NOT NULL | 所属知识库 |
| workspace_id | VARCHAR(64) | NOT NULL, FK → workspaces（无 ON DELETE CASCADE） | 工作区隔离 |
| file_hash | VARCHAR(64) | | SHA-256 文件哈希 |
| filename | VARCHAR(512) | | 原始文件名 |
| file_size | BIGINT | NOT NULL | 文件大小 |
| status | VARCHAR(32) | NOT NULL | 当前状态 |
| failure_reason | TEXT | | 失败原因 |
| retry_count | INT | NOT NULL DEFAULT 0 | 已重试次数 |
| retry_max | INT | NOT NULL DEFAULT 3 | 最大重试次数 |
| next_retry_at | TIMESTAMPTZ | | 下次重试时间 |
| last_error_code | VARCHAR(64) | | 最近错误码 |
| last_error_message | TEXT | | 最近错误消息 |
| last_error_at | TIMESTAMPTZ | | 最近错误时间 |
| reprocess_count | INT | NOT NULL DEFAULT 0 | 重处理次数 |
| reprocess_requested_at | TIMESTAMPTZ | | 重处理请求时间 |
| split_version | VARCHAR(32) | NOT NULL DEFAULT 'v1' | 分块版本标识 |
| processing_metadata | JSONB | | 处理元数据（V4） |
| latest_version_number | INT | NOT NULL | 最新版本号（V5） |
| latest_status | VARCHAR(32) | NOT NULL | 最新版本状态（V5） |
| latest_filename | VARCHAR(512) | | 最新版本文件名（V5） |
| latest_version_origin_type | VARCHAR(32) | NOT NULL | 版本来源类型（V5） |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |

> **注意**: `workspace_id` 的外键引用 `workspaces` 但无 ON DELETE CASCADE，删除工作区时不会级联删除文档。

部分唯一索引: `(kb_id, file_hash) WHERE file_hash IS NOT NULL AND status <> 'DELETED'`

#### `ingest_document_versions`（V5）

文档版本链。

| 列名 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGSERIAL | PK | |
| document_id | VARCHAR(64) | NOT NULL, FK → ingest_documents ON DELETE CASCADE | |
| version_number | INT | NOT NULL | 版本号（自增） |
| version_origin_type | VARCHAR(32) | NOT UPLOAD/ROLLBACK | 版本来源 |
| rollback_from_version_number | INT | 可空 | 回退来源版本 |
| file_hash | VARCHAR(64) | NOT NULL | |
| filename | VARCHAR(512) | NOT NULL | |
| file_size | BIGINT | NOT NULL | |
| status | VARCHAR(32) | NOT NULL | 版本独立状态 |
| failure_reason | TEXT | | |
| retry_count / retry_max / next_retry_at | — | | 重试控制 |
| last_error_code / last_error_message / last_error_at | — | | 错误信息 |
| reprocess_count / reprocess_requested_at | — | | 重处理控制 |
| split_version | VARCHAR(32) | NOT NULL DEFAULT 'v1' | |
| processing_metadata | JSONB | | |
| created_by_user_id | VARCHAR(64) | | 操作者 |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |

唯一索引: `(document_id, version_number)`
索引: `(document_id, version_number DESC)` — V5 普通查询索引
索引: `(file_hash)` — V6 文件哈希查找索引

> **V6 迁移说明**: V6 新增 `(file_hash)` 索引，用于支持按文件哈希快速查找版本记录。

---

### Knowledge 表组

#### `knowledge_bases`（V1，V2/V8 增强）

知识库主表。

| 列名 | 类型 | 约束 |
|---|---|---|
| id | BIGSERIAL | PK |
| kb_id | VARCHAR(64) | NOT NULL, UNIQUE |
| workspace_id | VARCHAR(64) | NOT NULL, FK → workspaces（无 ON DELETE CASCADE） |
| name | VARCHAR(100) | NOT NULL |
| description | VARCHAR(500) | NOT NULL DEFAULT '' |
| status | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE', CHECK (ACTIVE/INACTIVE/DELETED) |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

唯一索引: `(workspace_id, kb_id)`

> **注意**: `workspace_id` 的外键引用 `workspaces` 但无 ON DELETE CASCADE，删除工作区时不会级联删除知识库。

预置数据: `kb_id = 'default'`。迁移逻辑中包含从已有 `ingest_documents` 自动回填 `kb_id` 的脚本。

---

### QA 向量表

#### `vector_store`（V1）

PGVector 向量存储（Spring AI 管理表结构）。

| 列名 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK DEFAULT uuid_generate_v4() |
| content | TEXT | 分块文本内容 |
| metadata | JSON | 元数据（documentId, kbId, chunkIndex, splitVersion 等） |
| embedding | vector(1024) | 1024 维向量 |

HNSW 索引: `(embedding vector_cosine_ops)`

---

## 数据库函数（V7）

### `ingest_append_document_latest_version(...)`

追加新 latest version，同时更新 `ingest_documents` 的 latest projection 和 `ingest_document_versions`（双写一致性）。

- **参数数量**: 25 个
- **返回类型**: `BOOLEAN`（TRUE=成功，FALSE=失败）
- **乐观锁机制**: 检查 `latest_version_number` 进行并发控制
- **前置条件**: `latest_status IN ('INDEXED', 'FAILED')`
- **Upsert 策略**: 使用 `ON CONFLICT DO UPDATE` 进行插入或更新
- **双写一致性**: 同时更新 `ingest_documents` 和 `ingest_document_versions`

### `ingest_update_latest_document_version_processing(...)`

推进 latest version 处理状态，支持 INDEXED/FAILED/UPLOADED 三种目标状态。

- **参数数量**: 13 个
- **返回类型**: `BOOLEAN`（TRUE=成功，FALSE=失败）
- **行级锁机制**: 使用 `SELECT ... FOR UPDATE` 加行级锁进行并发控制
- **目标状态**: 支持 INDEXED / FAILED / UPLOADED，不支持的状态会抛出 `RAISE EXCEPTION`
- **双写一致性**: 同时更新 `ingest_documents` 和 `ingest_document_versions`

---

## 表关系总览

```
workspaces ──┬── workspace_memberships ──┬── users
              │                          ├── local_credentials
              │                          ├── login_lock_states
              │                          ├── knowledge_base_grants
              │                          └── document_grants
              │
              ├── ingest_documents ─────→ ingest_document_versions
              │        └── (kb_id) ──→ knowledge_bases
              │
              ├── knowledge_bases
              │
              └── audit_events

vector_store（独立，通过 metadata.documentId 关联 ingest_documents）
```

---

_最后更新: 2026-06-19 | 扫描模式: 深度扫描（Flyway V1-V8 源码级提取） | 表总数: 12_

> **Docling 集成说明**: 文档解析已从 Apache Tika 迁移至 Docling Serve，`ingest_documents.processing_metadata` 字段存储 Docling 解析结果（含文档结构、表格、图片等元数据）。
