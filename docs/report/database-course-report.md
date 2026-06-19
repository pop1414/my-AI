# 基于 RAG 的文档知识库系统 — 数据库课程设计报告

**项目名称：** 基于 RAG 的文档知识库系统

**参加人员姓名、学号、主要工作内容、工作量占比：**

| 姓名   | 学号     | 主要工作内容                                                                                            | 工作量占比 |
| ------ | -------- | ------------------------------------------------------------------------------------------------------- | ---------- |
| 冯颖   | 28230434 | 数据库总体架构设计、ER 图绘制与表结构设计、Flyway 迁移脚本编写、架构文档整理                            | 25%        |
| 丁达一 | 55230215 | 数据库对象实现（PL/pgSQL 函数）、Spring Boot REST API 开发、RAG 问答管线与权限校验逻辑实现              | 25%        |
| 邵希瑞 | 55230224 | React + TypeScript 前端架构搭建、全部页面与组件开发（登录、文档管理、知识库、问答）、API 对接与状态管理 | 25%        |
| 陆孝天 | 55230228 | 数据库索引优化与性能调优、触发器/视图等补充对象设计、测试用例编写与功能验证、Docker 环境部署与维护      | 25%        |

---

## 内容

---

## 一、需求说明

### 1.1 业务背景

随着企业知识积累的快速增长，传统的文档管理方式面临着**查找效率低、知识复用困难、跨部门协作不畅**等痛点。尤其在团队技术文档、产品需求文档、会议纪要等非结构化知识的管理上，简单的文件夹存储和关键词搜索已无法满足实际需求。

本项目设计并实现了一个**基于 RAG（检索增强生成）技术的文档知识库系统**，旨在解决以下核心问题：

1. **知识沉淀**：将散落在个人电脑中的文档统一上传、解析、存储，形成企业级知识库
2. **智能检索**：利用向量化语义检索替代传统关键词搜索，提升查找准确率
3. **智能问答**：基于检索到的相关文档片段，利用大语言模型（LLM）生成精准回答，实现"问答即获取知识"
4. **权限管控**：提供工作区、知识库、文档三级权限体系，保障数据安全

### 1.2 设计目标

| 目标               | 说明                                                     |
| ------------------ | -------------------------------------------------------- |
| 文档全生命周期管理 | 支持上传、解析、分块、向量化、版本管理、重处理等完整流程 |
| 多格式文档解析     | 支持 PDF、Word、HTML、Markdown 等常见文档格式            |
| 语义级检索         | 基于 PGVector 向量数据库实现 1024 维语义相似度检索       |
| RAG 问答           | 检索增强生成，回答附带原文引用，支持过时引用检测         |
| 多知识库隔离       | 支持创建多个知识库，文档按知识库组织，支持按知识库问答   |
| 三级权限体系       | 工作区级 → 知识库级 → 文档级，DOC_DENY 最高优先级        |
| 审计追踪           | 记录所有关键操作的审计事件，支持溯源                     |

### 1.3 功能模块图

![图 2-1 系统功能模块图](images/functional-module.png)

#### 模块一：用户管理模块

| 子模块        | 功能说明                                                                                                                             |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 用户登录/登出 | 基于 Session Cookie 的认证机制，支持登录失败锁定保护（连续 5 次失败锁定 15 分钟）                                                    |
| 托管账号管理  | 管理员可创建、禁用、重置密码、删除用户，支持开户即授权                                                                               |
| 权限管理      | 三级授权：工作区角色（OWNER/ADMIN/MEMBER）→ 知识库角色（MANAGER/CONTRIBUTOR/READER/ASKER）→ 文档权限（ALLOW_READ/ALLOW_MANAGE/DENY） |

#### 模块二：文档管理模块

| 子模块     | 功能说明                                                                             |
| ---------- | ------------------------------------------------------------------------------------ |
| 文档上传   | 支持多格式文件上传（PDF/Word/HTML/Markdown），自动计算 SHA-256 文件哈希实现幂等去重  |
| 文档解析   | 异步处理流水线：上传 → 解析（Docling Serve）→ 分块 → 向量化 → 入库，支持处理状态追踪 |
| 版本管理   | 文档支持多版本管理，可上传新版本、查看历史版本、回退到指定版本，采用乐观锁控制并发   |
| 文档重处理 | 解析失败后支持手动重试，系统也支持自动重试（最多 3 次）                              |

#### 模块三：知识库管理模块

| 子模块     | 功能说明                                                         |
| ---------- | ---------------------------------------------------------------- |
| 创建知识库 | 支持创建多个知识库，每个知识库有独立的名称、描述、状态           |
| 知识库列表 | 展示所有知识库及其已索引文档数量，支持软删除和恢复               |
| 知识库授权 | 管理员可为用户分配知识库级角色，控制用户对知识库的访问和操作权限 |

#### 模块四：智能问答模块

| 子模块       | 功能说明                                                                 |
| ------------ | ------------------------------------------------------------------------ |
| 语义检索     | 基于 PGVector 实现 1024 维向量余弦相似度检索，返回 Top-K 相关文档片段    |
| RAG 回答     | 检索结果送入大语言模型（通义千问 qwen-plus），生成附带原文引用的精准回答 |
| 过时引用检测 | 自动检测回答中引用的文档版本是否为最新版本，标记过时引用                 |

### 1.4 数据流图

![图 2-2 系统数据流图](images/data-flow.png)

---

## 二、总体设计

### 2.1 ER 图

![图 3-1 系统 ER 图](images/er-diagram.png)

### 2.2 关系模式

根据 ER 图转换，本系统共设计 **12 张数据表**，按业务子域分组如下。

#### 数据表汇总

| #   | 表名                     | 子域      | 核心字段                                                                     | 主要用途                              |
| --- | ------------------------ | --------- | ---------------------------------------------------------------------------- | ------------------------------------- |
| 1   | workspaces               | Auth      | workspace_id(PK), name, status                                               | 工作区管理                            |
| 2   | users                    | Auth      | user_id(PK), username, display_name, status                                  | 用户身份管理                          |
| 3   | local_credentials        | Auth      | user_id(PK/FK), password_hash, password_algo                                 | 本地密码认证                          |
| 4   | workspace_memberships    | Auth      | membership_id(PK), workspace_id(FK), user_id(FK), role                       | 工作区成员关系与工作区级角色          |
| 5   | login_lock_states        | Auth      | user_id(PK/FK), failed_login_count, locked_until                             | 登录失败锁定保护                      |
| 6   | audit_events             | Auth      | audit_event_id(PK), workspace_id(FK), actor_user_id(FK), event_type, outcome | 审计日志追踪                          |
| 7   | knowledge_bases          | Knowledge | kb_id(UK), workspace_id(FK), name, status                                    | 知识库管理                            |
| 8   | knowledge_base_grants    | Grant     | grant_id(PK), kb_id, user_id, role                                           | 知识库级权限授予                      |
| 9   | document_grants          | Grant     | grant_id(PK), document_id, user_id, permission                               | 文档级权限授予（DOC_DENY 最高优先级） |
| 10  | ingest_documents         | Ingest    | document_id(PK), kb_id, file_hash, status, latest_version                    | 文档资产主表（版本投影）              |
| 11  | ingest_document_versions | Ingest    | id(PK), document_id(FK), version_number, status, file_hash                   | 文档完整版本链                        |
| 12  | vector_store             | QA        | id(PK), content, metadata, embedding(VECTOR 1024)                            | 向量存储（RAG 语义检索）              |

#### Auth 子域（6 张表）

**1. workspaces（工作区表）**

| 字段名       | 数据类型     | 约束                      |
| ------------ | ------------ | ------------------------- |
| workspace_id | VARCHAR(64)  | PRIMARY KEY               |
| name         | VARCHAR(100) | NOT NULL                  |
| description  | VARCHAR(500) | NOT NULL DEFAULT ''       |
| status       | VARCHAR(16)  | NOT NULL DEFAULT 'ACTIVE' |
| created_at   | TIMESTAMPTZ  | NOT NULL                  |
| updated_at   | TIMESTAMPTZ  | NOT NULL                  |

**2. users（用户表）**

| 字段名       | 数据类型     | 约束                      |
| ------------ | ------------ | ------------------------- |
| user_id      | VARCHAR(64)  | PRIMARY KEY               |
| username     | VARCHAR(100) | NOT NULL, UNIQUE          |
| display_name | VARCHAR(100) | NOT NULL DEFAULT ''       |
| status       | VARCHAR(16)  | NOT NULL DEFAULT 'ACTIVE' |
| created_at   | TIMESTAMPTZ  | NOT NULL                  |
| updated_at   | TIMESTAMPTZ  | NOT NULL                  |

**3. local_credentials（本地认证凭证表）**

| 字段名              | 数据类型     | 约束                                               |
| ------------------- | ------------ | -------------------------------------------------- |
| user_id             | VARCHAR(64)  | PRIMARY KEY, FK → users(user_id) ON DELETE CASCADE |
| password_hash       | VARCHAR(255) | NOT NULL                                           |
| password_algo       | VARCHAR(32)  | NOT NULL DEFAULT 'bcrypt'                          |
| password_updated_at | TIMESTAMPTZ  | NOT NULL                                           |
| created_at          | TIMESTAMPTZ  | NOT NULL                                           |
| updated_at          | TIMESTAMPTZ  | NOT NULL                                           |

**4. workspace_memberships（工作区成员表）**

| 字段名        | 数据类型    | 约束                                                 |
| ------------- | ----------- | ---------------------------------------------------- |
| membership_id | BIGSERIAL   | PRIMARY KEY                                          |
| workspace_id  | VARCHAR(64) | NOT NULL, FK → workspaces(workspace_id)              |
| user_id       | VARCHAR(64) | NOT NULL, FK → users(user_id) ON DELETE CASCADE      |
| role          | VARCHAR(32) | NOT NULL, CHECK (role IN ('OWNER','ADMIN','MEMBER')) |
| status        | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE'                            |
| created_at    | TIMESTAMPTZ | NOT NULL                                             |
| updated_at    | TIMESTAMPTZ | NOT NULL                                             |
| -             | -           | UNIQUE (workspace_id, user_id)                       |

**5. login_lock_states（登录锁定状态表）**

| 字段名             | 数据类型    | 约束                                               |
| ------------------ | ----------- | -------------------------------------------------- |
| user_id            | VARCHAR(64) | PRIMARY KEY, FK → users(user_id) ON DELETE CASCADE |
| failed_login_count | INT         | NOT NULL DEFAULT 0                                 |
| locked_until       | TIMESTAMPTZ | NULL                                               |
| last_failed_at     | TIMESTAMPTZ | NULL                                               |
| last_login_at      | TIMESTAMPTZ | NULL                                               |
| updated_at         | TIMESTAMPTZ | NOT NULL                                           |

**6. audit_events（审计事件表）**

| 字段名         | 数据类型     | 约束                                                                                                      |
| -------------- | ------------ | --------------------------------------------------------------------------------------------------------- |
| audit_event_id | BIGSERIAL    | PRIMARY KEY                                                                                               |
| workspace_id   | VARCHAR(64)  | FK → workspaces(workspace_id)                                                                             |
| actor_user_id  | VARCHAR(64)  | FK → users(user_id) ON DELETE SET NULL                                                                    |
| actor_username | VARCHAR(100) | NULL                                                                                                      |
| event_type     | VARCHAR(64)  | NOT NULL                                                                                                  |
| target_type    | VARCHAR(32)  | NULL                                                                                                      |
| target_id      | VARCHAR(128) | NULL                                                                                                      |
| outcome        | VARCHAR(16)  | NOT NULL, CHECK (outcome IN ('SUCCESS','FAILURE','DENIED'))                                               |
| reason         | VARCHAR(255) | NOT NULL DEFAULT ''                                                                                       |
| metadata       | JSONB        | NOT NULL DEFAULT '{}'                                                                                     |
| occurred_at    | TIMESTAMPTZ  | NOT NULL DEFAULT CURRENT_TIMESTAMP                                                                        |
| -              | -            | 索引: (workspace_id, occurred_at DESC), (actor_user_id, occurred_at DESC), (event_type, occurred_at DESC) |

#### Knowledge 子域（1 张表）

**7. knowledge_bases（知识库表）**

| 字段名       | 数据类型     | 约束                                                                         |
| ------------ | ------------ | ---------------------------------------------------------------------------- |
| id           | BIGSERIAL    | PRIMARY KEY                                                                  |
| kb_id        | VARCHAR(64)  | NOT NULL, UNIQUE                                                             |
| workspace_id | VARCHAR(64)  | NOT NULL, FK → workspaces(workspace_id)                                      |
| name         | VARCHAR(100) | NOT NULL                                                                     |
| description  | VARCHAR(500) | NOT NULL DEFAULT ''                                                          |
| status       | VARCHAR(16)  | NOT NULL DEFAULT 'ACTIVE', CHECK (status IN ('ACTIVE','INACTIVE','DELETED')) |
| created_at   | TIMESTAMPTZ  | NOT NULL                                                                     |
| updated_at   | TIMESTAMPTZ  | NOT NULL                                                                     |
| -            | -            | UNIQUE (workspace_id, kb_id)                                                 |

#### Grant 子域（2 张表）

**8. knowledge_base_grants（知识库授权表）**

| 字段名       | 数据类型    | 约束                                                                             |
| ------------ | ----------- | -------------------------------------------------------------------------------- |
| grant_id     | BIGSERIAL   | PRIMARY KEY                                                                      |
| workspace_id | VARCHAR(64) | NOT NULL, FK → workspaces(workspace_id)                                          |
| kb_id        | VARCHAR(64) | NOT NULL                                                                         |
| user_id      | VARCHAR(64) | NOT NULL                                                                         |
| role         | VARCHAR(32) | NOT NULL, CHECK (role IN ('KB_MANAGER','KB_CONTRIBUTOR','KB_READER','KB_ASKER')) |
| status       | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE', CHECK (status IN ('ACTIVE','DISABLED'))               |
| created_at   | TIMESTAMPTZ | NOT NULL                                                                         |
| updated_at   | TIMESTAMPTZ | NOT NULL                                                                         |
| -            | -           | FK (workspace_id, kb_id) → knowledge_bases ON DELETE CASCADE                     |
| -            | -           | FK (workspace_id, user_id) → workspace_memberships ON DELETE CASCADE             |
| -            | -           | UNIQUE (workspace_id, kb_id, user_id)                                            |

**9. document_grants（文档授权表）**

| 字段名       | 数据类型    | 约束                                                                             |
| ------------ | ----------- | -------------------------------------------------------------------------------- |
| grant_id     | BIGSERIAL   | PRIMARY KEY                                                                      |
| workspace_id | VARCHAR(64) | NOT NULL, FK → workspaces(workspace_id)                                          |
| document_id  | VARCHAR(64) | NOT NULL                                                                         |
| user_id      | VARCHAR(64) | NOT NULL                                                                         |
| permission   | VARCHAR(32) | NOT NULL, CHECK (permission IN ('DOC_ALLOW_READ','DOC_ALLOW_MANAGE','DOC_DENY')) |
| status       | VARCHAR(16) | NOT NULL DEFAULT 'ACTIVE', CHECK (status IN ('ACTIVE','DISABLED'))               |
| created_at   | TIMESTAMPTZ | NOT NULL                                                                         |
| updated_at   | TIMESTAMPTZ | NOT NULL                                                                         |
| -            | -           | FK (workspace_id, document_id) → ingest_documents ON DELETE CASCADE              |
| -            | -           | FK (workspace_id, user_id) → workspace_memberships ON DELETE CASCADE             |
| -            | -           | UNIQUE (workspace_id, document_id, user_id)                                      |

#### Ingest 子域（2 张表）

**10. ingest_documents（文档资产主表）**

| 字段名                     | 数据类型     | 约束                                                                                 |
| -------------------------- | ------------ | ------------------------------------------------------------------------------------ |
| document_id                | VARCHAR(64)  | PRIMARY KEY                                                                          |
| kb_id                      | VARCHAR(128) | NOT NULL                                                                             |
| workspace_id               | VARCHAR(64)  | NOT NULL, FK → workspaces(workspace_id)                                              |
| file_hash                  | VARCHAR(64)  | 可空                                                                                 |
| filename                   | VARCHAR(512) | 可空                                                                                 |
| file_size                  | BIGINT       | NOT NULL                                                                             |
| status                     | VARCHAR(32)  | NOT NULL, CHECK (status IN ('UPLOADED','INGESTING','INDEXED','FAILED','DELETED'))    |
| failure_reason             | TEXT         | 可空                                                                                 |
| retry_count                | INT          | NOT NULL DEFAULT 0                                                                   |
| retry_max                  | INT          | NOT NULL DEFAULT 3                                                                   |
| next_retry_at              | TIMESTAMPTZ  | 可空                                                                                 |
| latest_version_number      | INT          | NOT NULL                                                                             |
| latest_status              | VARCHAR(32)  | NOT NULL                                                                             |
| latest_filename            | VARCHAR(512) | 可空                                                                                 |
| latest_version_origin_type | VARCHAR(32)  | NOT NULL, CHECK (latest_version_origin_type IN ('UPLOAD','ROLLBACK'))                |
| created_at                 | TIMESTAMPTZ  | NOT NULL                                                                             |
| updated_at                 | TIMESTAMPTZ  | NOT NULL                                                                             |
| -                          | -            | 部分唯一索引: (kb_id, file_hash) WHERE file_hash IS NOT NULL AND status <> 'DELETED' |

**11. ingest_document_versions（文档版本表）**

| 字段名                       | 数据类型     | 约束                                                           |
| ---------------------------- | ------------ | -------------------------------------------------------------- |
| id                           | BIGSERIAL    | PRIMARY KEY                                                    |
| document_id                  | VARCHAR(64)  | NOT NULL, FK → ingest_documents(document_id) ON DELETE CASCADE |
| version_number               | INT          | NOT NULL                                                       |
| version_origin_type          | VARCHAR(32)  | NOT NULL, CHECK (version_origin_type IN ('UPLOAD','ROLLBACK')) |
| rollback_from_version_number | INT          | 可空                                                           |
| file_hash                    | VARCHAR(64)  | NOT NULL                                                       |
| filename                     | VARCHAR(512) | NOT NULL                                                       |
| file_size                    | BIGINT       | NOT NULL                                                       |
| status                       | VARCHAR(32)  | NOT NULL, CHECK (status IN ('INDEXED','FAILED','DELETED'))     |
| failure_reason               | TEXT         | 可空                                                           |
| split_version                | VARCHAR(32)  | NOT NULL DEFAULT 'v1'                                          |
| processing_metadata          | JSONB        | 可空                                                           |
| created_by_user_id           | VARCHAR(64)  | 可空                                                           |
| created_at                   | TIMESTAMPTZ  | NOT NULL                                                       |
| updated_at                   | TIMESTAMPTZ  | NOT NULL                                                       |
| -                            | -            | UNIQUE (document_id, version_number)                           |
| -                            | -            | B-Tree 索引: (document_id, version_number DESC)                |
| -                            | -            | B-Tree 索引: (file_hash)                                       |

#### QA 子域（1 张表）

**12. vector_store（向量存储表）**

| 字段名    | 数据类型     | 约束                                             |
| --------- | ------------ | ------------------------------------------------ |
| id        | UUID         | PRIMARY KEY DEFAULT uuid_generate_v4()           |
| content   | TEXT         | NULL — 分块文本内容                              |
| metadata  | JSON         | NULL — 元数据（documentId, kbId, chunkIndex 等） |
| embedding | VECTOR(1024) | NULL — 1024 维语义向量                           |
| -         | -            | HNSW 索引: (embedding vector_cosine_ops)         |

### 2.3 表关系总览

![图 3-2 表关系总览](images/table-relation.png)

**外键关系说明**：

| 源表                     | 源字段                      | 目标表                | 目标字段                    | 删除策略 |
| ------------------------ | --------------------------- | --------------------- | --------------------------- | -------- |
| local_credentials        | user_id                     | users                 | user_id                     | CASCADE  |
| workspace_memberships    | workspace_id                | workspaces            | workspace_id                | —        |
| workspace_memberships    | user_id                     | users                 | user_id                     | CASCADE  |
| knowledge_base_grants    | (workspace_id, kb_id)       | knowledge_bases       | (workspace_id, kb_id)       | CASCADE  |
| knowledge_base_grants    | (workspace_id, user_id)     | workspace_memberships | (workspace_id, user_id)     | CASCADE  |
| document_grants          | (workspace_id, document_id) | ingest_documents      | (workspace_id, document_id) | CASCADE  |
| document_grants          | (workspace_id, user_id)     | workspace_memberships | (workspace_id, user_id)     | CASCADE  |
| ingest_documents         | workspace_id                | workspaces            | workspace_id                | —        |
| ingest_document_versions | document_id                 | ingest_documents      | document_id                 | CASCADE  |
| audit_events             | workspace_id                | workspaces            | workspace_id                | —        |
| audit_events             | actor_user_id               | users                 | user_id                     | SET NULL |

---

## 三、详细设计

### 3.1 数据库对象总览

本系统在 PostgreSQL 16 数据库中创建了以下数据库对象：

| 对象类型   | 数量   | 说明                                                   |
| ---------- | ------ | ------------------------------------------------------ |
| 数据表     | 12 张  | 覆盖 Auth、Knowledge、Grant、Ingest、QA 五个业务子域   |
| 索引       | 15+ 个 | 包括 B-Tree 索引、部分唯一索引、HNSW 向量索引          |
| 数据库函数 | 2 个   | 文档版本追加函数、版本状态推进函数（PL/pgSQL）         |
| 扩展       | 3 个   | vector（向量）、hstore（键值）、uuid-ossp（UUID 生成） |

### 3.2 数据表创建

#### 3.2.1 建表 SQL 示例

**创建扩展**：

系统依赖以下 PostgreSQL 扩展，于数据库初始化时启用：

| 扩展名    | 功能说明                 | SQL 命令                                      |
| --------- | ------------------------ | --------------------------------------------- |
| vector    | 向量数据类型（PGVector） | `CREATE EXTENSION IF NOT EXISTS vector;`      |
| hstore    | 键值对数据类型           | `CREATE EXTENSION IF NOT EXISTS hstore;`      |
| uuid-ossp | UUID 生成函数            | `CREATE EXTENSION IF NOT EXISTS "uuid-ossp";` |

**创建 workspaces 表**：

**表结构**：`workspace_id`（主键）、`name`（非空）、`description`（默认空串）、`status`（默认 ACTIVE）、时间戳字段。

**初始化数据**：预置一条 `default` 工作区记录，作为系统默认工作区。

**创建 users 表**：

**表结构**：`user_id`（主键）、`username`（唯一且非空）、`display_name`（默认空串）、`status`（默认 ACTIVE）、时间戳字段。

**创建 workspace_memberships 表（含复合外键和 CHECK 约束）**：

**表结构**：`membership_id`（自增主键）、`workspace_id`（外键引用 workspaces）、`user_id`（外键引用 users，级联删除）、`role`（CHECK 约束限 OWNER/ADMIN/MEMBER）、`status`、时间戳。

**关键约束**：

1. `CHECK (role IN ('OWNER','ADMIN','MEMBER'))` — 限制角色枚举值
2. `UNIQUE (workspace_id, user_id)` — 防止重复成员关系
3. `FK user_id → users ON DELETE CASCADE` — 用户删除时自动清理成员关系

**创建 vector_store 表（向量存储）**：

**表结构**：`id`（UUID 主键，自动生成）、`content`（分块文本）、`metadata`（JSON 元数据）、`embedding`（1024 维向量）。

**向量索引**：创建 HNSW 索引 `(embedding vector_cosine_ops)` 用于余弦相似度快速检索。

### 3.3 索引设计

| 索引名称/位置            | 类型     | 字段                                 | 用途                     |
| ------------------------ | -------- | ------------------------------------ | ------------------------ |
| workspace_memberships    | UNIQUE   | (workspace_id, user_id)              | 防止重复成员关系         |
| knowledge_base_grants    | UNIQUE   | (workspace_id, kb_id, user_id)       | 防止重复授权             |
| document_grants          | UNIQUE   | (workspace_id, document_id, user_id) | 防止重复文档授权         |
| knowledge_bases          | UNIQUE   | (workspace_id, kb_id)                | 工作区内知识库 ID 唯一   |
| ingest_documents         | 部分唯一 | (kb_id, file_hash) WHERE ...         | 文件去重（已删除的除外） |
| ingest_document_versions | UNIQUE   | (document_id, version_number)        | 版本号唯一               |
| ingest_document_versions | B-Tree   | (file_hash)                          | 按文件哈希快速查找       |
| audit_events             | B-Tree   | (workspace_id, occurred_at DESC)     | 按时间查询审计日志       |
| audit_events             | B-Tree   | (actor_user_id, occurred_at DESC)    | 按操作者查询审计日志     |
| audit_events             | B-Tree   | (event_type, occurred_at DESC)       | 按事件类型查询审计日志   |
| vector_store             | HNSW     | (embedding vector_cosine_ops)        | 向量相似度检索           |

### 3.4 数据库函数

#### 函数一：`ingest_append_document_latest_version`

**功能**：追加新版本到文档，同时更新主表的最新版本投影和版本链表，保证双写一致性。

**设计要点**：

- 使用乐观锁机制：检查 `latest_version_number` 防止并发冲突
- 前置条件检查：仅当 `latest_status IN ('INDEXED', 'FAILED')` 时允许追加
- 使用 `ON CONFLICT DO UPDATE` 实现 Upsert 语义
- 返回 `BOOLEAN`：TRUE 表示成功，FALSE 表示条件不满足

**调用参数说明**：

| 参数名                           | 示例值     | 说明                     |
| -------------------------------- | ---------- | ------------------------ |
| p_document_id                    | doc-001    | 目标文档 ID              |
| p_workspace_id                   | default    | 所属工作区               |
| p_kb_id                          | default    | 所属知识库               |
| p_file_hash                      | abc123hash | 新版本文件 SHA-256 哈希  |
| p_filename                       | report.pdf | 新版本文件名             |
| p_file_size                      | 102400     | 文件大小（字节）         |
| p_status                         | INDEXED    | 初始处理状态             |
| p_expected_latest_version_number | 2          | 乐观锁：期望的当前版本号 |

#### 函数二：`ingest_update_latest_document_version_processing`

**功能**：推进文档最新版本的处理状态，支持 INDEXED/FAILED/UPLOADED 三种目标状态。

**设计要点**：

- 使用 `SELECT ... FOR UPDATE` 加行级锁，防止并发状态推进
- 支持三种目标状态转换，不支持的状态会抛出异常
- 双写一致性：同时更新主表和版本表

### 3.5 功能说明与测试

#### 3.5.1 用户管理模块

**（1）用户登录**

- **接口**：`POST /api/v1/auth/login`
- **实现逻辑**：接收用户名和密码，通过 `local_credentials` 表验证密码哈希（bcrypt 算法），成功后创建 Session，同时更新 `login_lock_states` 表记录登录时间
- **安全机制**：连续 5 次登录失败自动锁定 15 分钟，记录 `failed_login_count` 和 `locked_until`

**密码验证流程**：

1. 连接 `local_credentials` 和 `users` 表
2. 按用户名和状态过滤：`WHERE u.username = 'admin' AND u.status = 'ACTIVE'`
3. 获取 `password_hash`（bcrypt 哈希）和 `password_algo` 字段进行验证

> ![图 4-8 登录页面](images/page-login.png)

**（2）托管账号管理**

- **接口**：`POST /api/v1/admin/accounts`
- **实现逻辑**：管理员创建新用户，同时写入 `users`、`local_credentials`、`workspace_memberships` 三张表，实现开户即授权

**事务操作步骤**（三表同写，保证原子性）：

1. `BEGIN` — 开启事务
2. `INSERT INTO users` — 写入用户身份信息（user_id、username、display_name、status）
3. `INSERT INTO local_credentials` — 写入密码凭证（password_hash 采用 bcrypt 加密）
4. `INSERT INTO workspace_memberships` — 分配默认工作区 MEMBER 角色
5. `COMMIT` — 提交事务，三表同时落库或全部回滚

> ![图 4-9 账号管理页面](images/page-account-mgmt1.png)
> ![图 4-10 账号管理页面](images/page-account-mgmt2.png)
> ![图 4-10 账号管理页面](images/page-account-mgmt3.png)
> ![图 4-10 账号管理页面](images/page-account-mgmt4.png)
> ![图 4-10 账号管理页面](images/page-account-mgmt5.png)

#### 3.5.2 文档管理模块

**（1）文档上传**

- **接口**：`POST /api/v1/documents/upload`
- **实现逻辑**：
  1. 计算文件 SHA-256 哈希，检查是否已存在相同文件（幂等去重）
  2. 将文件存储到 S3 对象存储（RustFS）
  3. 在 `ingest_documents` 和 `ingest_document_versions` 中创建记录
  4. 提交异步处理任务（解析 → 分块 → 向量化）

**文件去重检查**：按知识库 ID、文件哈希查询已有文档，排除已删除的记录

> ![图 4-10 文档上传页面](images/page-upload1.png)
> ![图 4-10 文档上传页面](images/page-upload2.png)

**（2）文档处理状态查询**

- **接口**：`GET /api/v1/documents/{documentId}/status`
- **状态流转**：`UPLOADED → INGESTING → INDEXED`（成功）或 `FAILED`（失败）

![图 4-1 文档处理状态流转图](images/document-status-flow.png)

**状态查询**：从 `ingest_documents` 表查询文档 ID、文件名、处理状态、最新版本号和失败原因

> ![图 4-11 文档列表页面](images/page-doc-list1.png)
> ![图 4-11 文档列表页面](images/page-doc-list2.png)

**（3）文档版本管理**

- **接口**：`POST /api/v1/documents/{documentId}/versions`
- **实现逻辑**：上传新版本时使用乐观锁（`expectedLatestVersionNumber`），通过数据库函数 `ingest_append_document_latest_version` 保证双写一致性

**版本历史查询**：从 `ingest_document_versions` 表按版本号倒序查询文档的所有版本记录（包括版本号、源类型、文件名、状态、大小、创建时间）

> ![图 4-12 文档版本历史](images/page-doc-versions1.png)
> ![图 4-12 文档版本历史](images/page-doc-versions2.png)
> ![图 4-12 文档版本历史](images/page-doc-versions3.png)
> ![图 4-12 文档版本历史](images/page-doc-versions4.png)

#### 3.5.3 知识库管理模块

**（1）创建知识库**

- **接口**：`POST /api/v1/knowledge-bases`
- **实现逻辑**：插入 `knowledge_bases` 表新记录，包含知识库 ID、所属工作区、名称、描述、初始状态（ACTIVE）和时间戳

**（2）知识库列表与统计**

- **接口**：`GET /api/v1/knowledge-bases`
- **实现逻辑**：通过左联接统计每个知识库已索引的文档数量

**查询步骤**：

1. 从 `knowledge_bases` 表获取知识库基本信息
2. 左联接 `ingest_documents` 表统计已索引文档数（`latest_status = 'INDEXED'`）
3. 过滤有效记录（非删除状态）
4. 按知识库分组聚合

> ![图 4-13 知识库列表页面](images/page-kb-list1.png)
> ![图 4-13 知识库列表页面](images/page-kb-list2.png)
> ![图 4-13 知识库列表页面](images/page-kb-list3.png)

#### 3.5.4 智能问答模块

**（1）RAG 问答**

- **接口**：`POST /api/v1/qa/ask`
- **实现逻辑**：
  1. 将用户问题通过 DashScope Embedding API 转换为 1024 维向量
  2. 在 `vector_store` 表中执行余弦相似度检索，返回 Top-K 相关分块
  3. 将检索到的分块文本作为上下文，送入 qwen-plus 大语言模型生成回答
  4. 返回回答文本和引用来源信息

![图 4-2 RAG 问答处理管线](images/rag-pipeline.png)

**向量语义检索**：

1. 将用户问题编码为 1024 维向量
2. 在 `vector_store` 表中按余弦距离查询，使用 HNSW 索引加速
3. 按相似度倒序返回 Top-5 文档分块
4. 提取 `id`、`content`、`metadata` 三个字段

> ![图 4-14 智能问答页面](images/page-qa1.png)
> ![图 4-14 智能问答页面](images/page-qa2.png)
> ![图 4-14 智能问答页面](images/page-qa3.png)

#### 3.5.5 权限管理模块

**（1）三级授权体系**

本系统实现了精细的三级权限控制：

![图 4-3 三级权限模型](images/permission-model.png)

| 级别     | 授权粒度              | 角色/权限                                                 |
| -------- | --------------------- | --------------------------------------------------------- |
| 工作区级 | workspace_memberships | OWNER > ADMIN > MEMBER                                    |
| 知识库级 | knowledge_base_grants | KB_MANAGER > KB_CONTRIBUTOR > KB_READER > KB_ASKER        |
| 文档级   | document_grants       | DOC_DENY（最高优先级）> DOC_ALLOW_MANAGE > DOC_ALLOW_READ |

**权限查询逻辑**：根据工作区 ID、知识库 ID、用户 ID 从 `knowledge_base_grants` 表查询该用户的有效角色（状态为 ACTIVE）

> ![图 4-15 权限管理页面](images/page-permission1.png)
> ![图 4-15 权限管理页面](images/page-permission2.png)
> ![图 4-15 权限管理页面](images/page-permission3.png)
> ![图 4-15 权限管理页面](images/page-permission4.png)

#### 3.5.6 审计追踪

**审计事件查询**：从 `audit_events` 表按工作区过滤，按发生时间倒序查询最近 20 条事件，包含事件 ID、事件类型、操作者、目标对象、操作结果、发生时间

> ![图 4-16 审计日志页面](images/page-audit.png)

---

## 四、心得体会

### 【成员1（学号1）— 架构设计】

> 在本次数据库课程设计中，我担任架构设计角色，负责数据库总体架构设计、ER 图绘制、12 张表的结构设计以及 Flyway 数据库迁移脚本的编写。
>
> 设计过程中最大的挑战是**文档版本管理的双写一致性**问题——`ingest_documents` 主表需要维护最新版本的投影字段（`latest_version_number`、`latest_status` 等），而 `ingest_document_versions` 表存储完整的版本链，两者必须保持一致。我设计了基于 PL/pgSQL 存储函数 + 乐观锁的方案：函数内部使用 `latest_version_number` 作为乐观锁条件，先更新主表再插入版本记录，任何一步失败都会回滚，从而保证原子性。
>
> 另一个难点是**三级权限体系的外键设计**。知识库授权表 `knowledge_base_grants` 和文档授权表 `document_grants` 都使用了 `workspace_memberships` 的复合外键 `(workspace_id, user_id)`，而非简单的 `user_id` 单字段外键。这个设计确保了授权必须绑定到工作区成员身份上，删除成员时授权自动级联清除，避免了孤儿授权记录。
>
> 通过本次实验，我对数据库范式设计、复合外键约束、部分唯一索引（Partial Unique Index）有了更深入的理解，也体会到了"先设计后实现"的重要性——前期 ER 图的反复推敲，极大减少了后期的表结构变更。

### 【成员2（学号2）— 后端开发】

> 我负责后端开发，主要工作包括 PL/pgSQL 数据库函数的编写、Spring Boot REST API 的实现，以及 RAG 问答管线和权限校验逻辑的开发。
>
> 本项目没有使用 JPA/Hibernate 等 ORM 框架，而是直接通过 JdbcTemplate 编写 SQL 操作 PostgreSQL。这种"裸 SQL"的方式让我对数据库操作有了更深的理解——每一条 SQL 的执行计划、索引命中情况都需要关注。
>
> 实现过程中印象最深的是**文档版本追加函数** `ingest_append_document_latest_version` 的开发。这个函数有 25 个参数，内部需要同时写入两张表、检查乐观锁、处理 Upsert 冲突。最初版本没有加行级锁，并发测试时出现了版本号冲突的问题。后来在状态推进函数中加入了 `SELECT ... FOR UPDATE` 行级锁，配合乐观锁的双重保护才彻底解决。
>
> RAG 问答管线的开发也让我学到了很多。将用户问题向量化后在 PGVector 中执行余弦距离检索，再把 Top-K 结果作为上下文送入大语言模型——整个流程涉及向量运算、SQL 查询优化、API 编排等多个技术点，是本次项目中技术含量最高的部分。

### 【成员3（学号3）— 前端开发】

> 我负责前端开发，使用 React 19 + TypeScript + Ant Design 6 从零搭建了完整的 Web 前端应用，涵盖登录、文档管理、知识库管理、智能问答、权限管理、审计日志等全部页面。
>
> 前端架构采用 Vite 作为构建工具，TanStack Query 管理服务端状态，Zod 做 API 响应的运行时类型校验。所有 API 调用都通过 React Query 的 `useQuery`/`useMutation` 封装，避免了直接 `fetch` 带来的状态管理混乱。
>
> 开发中最棘手的部分是**文档处理状态的实时反馈**。文档上传后会经历 `UPLOADED → INGESTING → INDEXED` 的异步状态流转，前端需要轮询状态并在 UI 上实时展示进度。我通过 TanStack Query 的 `refetchInterval` 实现了智能轮询——处理中时每 2 秒刷新，完成后停止轮询，既保证了用户体验又避免了不必要的请求。
>
> 另一个挑战是**三级权限体系的前端适配**。不同角色的用户看到的页面和可操作的按钮完全不同。我通过后端返回的 `capabilities` 对象控制 UI 元素的显示/隐藏，确保了前后端权限判断的一致性。

### 【成员4（学号4）— 数据库工程与测试】

> 我负责数据库工程优化和系统测试工作，包括索引设计与性能调优、触发器/视图等补充数据库对象的创建、测试用例编写与功能验证，以及 Docker 开发环境的部署与维护。
>
> 索引优化方面，`vector_store` 表的 HNSW 向量索引是最关键的优化点。最初使用顺序扫描执行向量相似度检索，当分块数据量增长到数千条后，单次查询耗时超过 10 秒。创建 `USING hnsw (embedding vector_cosine_ops)` 索引后，查询时间降至毫秒级。此外，审计日志表 `audit_events` 的三个 B-Tree 索引也是根据实际查询模式设计的——分别覆盖按时间、按操作者、按事件类型三种查询路径。
>
> 测试方面，我编写了覆盖全部 37 个 REST API 端点的测试用例，重点关注了以下场景：(1) 文件哈希幂等去重——重复上传同一文件不会创建新记录；(2) 版本乐观锁冲突——并发上传新版本时只有先到的成功；(3) 权限校验——DOC_DENY 优先级高于 ALLOW_READ；(4) 登录锁定——连续 5 次失败后账号自动锁定 15 分钟。
>
> 通过本次实验，我深刻体会到数据库性能优化不是"加索引就完事"，而是需要结合查询模式、数据分布、并发场景综合考量。测试也不只是验证"功能能用"，更重要的是验证"异常场景下数据仍然一致"。

---

_报告完成日期：2026 年 6 月_
