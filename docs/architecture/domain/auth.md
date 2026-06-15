# Auth 子域 — 领域模型

> 认证、授权、治理、审计

## 概述

Auth 子域是系统的安全核心，负责用户身份认证、三级授权模型、账号治理和安全审计。所有领域模型均为 Java record（不可变）或 enum，业务逻辑集中在 application 层。

## 领域模型

### 枚举类型

#### WorkspaceRole

工作区成员角色。按名称映射到 `workspace_memberships.role` 列（VARCHAR 存储）。

| 常量 | 说明 |
|------|------|
| `WORKSPACE_OWNER` | 工作区所有者 — 最高权限，不可触碰 |
| `WORKSPACE_ADMIN` | 工作区管理员 — 可管理主要资源 |
| `WORKSPACE_MEMBER` | 普通成员 — 依赖资源授权访问知识库和文档 |

#### KnowledgeBaseRole

知识库授权角色。按名称映射到 `knowledge_base_grants.role` 列。

| 常量 | 说明 |
|------|------|
| `KB_MANAGER` | 知识库管理员 — 可管理 KB 配置和授权 |
| `KB_CONTRIBUTOR` | 知识库贡献者 — 可上传、重处理和维护内容 |
| `KB_READER` | 知识库读者 — 可读取 KB 文档内容 |
| `KB_ASKER` | 知识库提问者 — 可在 QA 场景中使用 KB 内容 |

#### DocumentPermission

文档级权限覆盖。按名称映射到 `document_grants.permission` 列。在知识库授权之上表达文档级允许或拒绝规则。

| 常量 | 说明 |
|------|------|
| `DOC_ALLOW_READ` | 允许读取文档 |
| `DOC_ALLOW_MANAGE` | 允许管理文档 |
| `DOC_DENY` | 显式拒绝文档访问（最高优先级，覆盖所有 KB 角色） |

---

### 认证模型

#### LoginAccount

登录账号领域模型（只读视图）。通过 infrastructure 仓储的多表 JOIN（users + local_credentials + workspace_memberships + login_lock_states）加载，避免 N+1 查询。业务行为（密码验证、锁定判定）由 `LoginApplicationService` 编排。

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `String` | 用户唯一标识（业务主键） |
| `username` | `String` | 用户名 |
| `displayName` | `String` | 显示名称 |
| `userStatus` | `String` | 用户状态（`ACTIVE` / `DISABLED`） |
| `passwordHash` | `String` | BCrypt 密码哈希 |
| `workspaceId` | `String` | 工作区 ID |
| `workspaceRole` | `WorkspaceRole` | 工作区角色 |
| `membershipStatus` | `String` | 成员资格状态（`ACTIVE` / `INACTIVE`） |
| `failedLoginCount` | `int` | 连续登录失败次数 |
| `lockedUntil` | `Instant` | 锁定过期时间（`null` = 未锁定） |

#### LoginFailureState

登录失败状态领域模型。封装密码验证失败后的账号锁定状态。由仓储的 `recordFailedLogin` 方法返回，供应用层决定是否抛出锁定异常。

| 字段 | 类型 | 说明 |
|------|------|------|
| `failedLoginCount` | `int` | 当前连续失败次数（含本次） |
| `lockedUntil` | `Instant` | 锁定过期时间（`null` = 未触发锁定） |

**业务方法**：`locked()` — 当 `lockedUntil` 非 null 时返回 `true`。不检查锁定是否已过期（这是应用层的职责）。

#### BootstrapAdminAccount

初始管理员账号写入模型。服务于空数据库引导场景（"第零个用户"创建）。携带 INSERT/UPSERT 到 `users`、`local_credentials`、`workspace_memberships` 三张表所需的最小字段集。与 `LoginAccount` 的区别：这是写入模型，不含运行时状态字段。

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `String` | 用户唯一标识（由应用层生成，通常为随机 UUID） |
| `username` | `String` | 登录用户名（空白已修剪） |
| `displayName` | `String` | 显示名称（已回退处理，保证非 null） |
| `passwordHash` | `String` | BCrypt 编码密码哈希 |
| `workspaceId` | `String` | 工作区 ID |
| `role` | `WorkspaceRole` | 工作区角色（固定为 `WORKSPACE_OWNER`） |
| `createdAt` | `Instant` | 创建时间戳（UTC） |

---

### 账号治理模型

#### ManagedAccount

账号治理读取模型。聚合用户基本信息、工作区成员资格和登录锁定状态，用于治理端的账号管理操作。

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `String` | 用户唯一标识 |
| `username` | `String` | 用户名 |
| `displayName` | `String` | 显示名称 |
| `userStatus` | `String` | 用户状态 |
| `workspaceId` | `String` | 工作区 ID |
| `workspaceRole` | `WorkspaceRole` | 工作区角色 |
| `membershipStatus` | `String` | 成员资格状态 |
| `failedLoginCount` | `int` | 连续登录失败次数 |
| `lockedUntil` | `Instant` | 锁定过期时间 |

#### WorkspaceMember

工作区成员领域模型（只读视图）。聚合用户基本信息和工作区成员关系。不可变 record，由 JDBC RowMapper 构造。

| 字段 | 类型 | 说明 |
|------|------|------|
| `userId` | `String` | 用户唯一标识 |
| `username` | `String` | 用户名（登录名） |
| `displayName` | `String` | 显示名称（UI 渲染用） |
| `workspaceId` | `String` | 工作区唯一标识 |
| `workspaceRole` | `WorkspaceRole` | 工作区角色枚举 |
| `membershipStatus` | `String` | 成员资格状态 |

---

### 授权模型

#### KnowledgeBaseGrant

知识库授权领域模型（只读视图）。聚合授权关系与被授权者基本信息。由 JDBC RowMapper 从 `knowledge_base_grants` JOIN `users` 表构造。

| 字段 | 类型 | 说明 |
|------|------|------|
| `workspaceId` | `String` | 工作区唯一标识 |
| `kbId` | `String` | 知识库唯一标识 |
| `userId` | `String` | 被授权用户唯一标识 |
| `username` | `String` | 被授权用户名（登录名） |
| `displayName` | `String` | 被授权显示名称（UI 渲染用） |
| `role` | `KnowledgeBaseRole` | 知识库授权角色枚举 |
| `status` | `String` | 授权状态（`ACTIVE` / `DISABLED`） |

#### DocumentGrant

文档级授权领域模型（只读视图）。聚合文档级权限覆盖与被授权者基本信息。由 JDBC RowMapper 从 `document_grants` JOIN `users` 表构造。

| 字段 | 类型 | 说明 |
|------|------|------|
| `workspaceId` | `String` | 工作区唯一标识 |
| `documentId` | `String` | 文档唯一标识 |
| `userId` | `String` | 被授权用户唯一标识 |
| `username` | `String` | 被授权用户名（登录名） |
| `displayName` | `String` | 被授权显示名称（UI 渲染用） |
| `permission` | `DocumentPermission` | 文档权限覆盖枚举 |
| `status` | `String` | 授权状态（`ACTIVE` / `DISABLED`） |

---

### 审计模型

#### AuditEvent

审计事件领域模型。记录安全相关操作事件（登录成功、登录失败、账号锁定等），用于事后审计追踪和安全分析。不可变 record 确保审计日志完整性。推荐使用静态工厂方法而非规范构造器。

| 字段 | 类型 | 说明 |
|------|------|------|
| `workspaceId` | `String` | 工作区 ID（用户不存在时可为 null） |
| `actorUserId` | `String` | 操作者用户 ID |
| `actorUsername` | `String` | 操作者用户名 |
| `eventType` | `String` | 事件类型代码（如 `LOGIN_SUCCESS`、`LOGIN_FAILURE`） |
| `targetType` | `String` | 操作目标类型（当前固定为 `"USER"`） |
| `targetId` | `String` | 操作目标 ID（通常等于 `actorUserId`） |
| `outcome` | `String` | 结果代码（`"SUCCESS"` 或 `"FAILURE"`） |
| `reason` | `String` | 失败原因代码（如 `BAD_CREDENTIALS`、`ACCOUNT_LOCKED`）；成功时为空字符串 |
| `metadata` | `String` | 扩展元数据（JSON 字符串，当前固定为 `"{}"`） |
| `occurredAt` | `Instant` | 事件发生时间戳（UTC） |

**工厂方法**：

- `AuditEvent.success(workspaceId, actorUserId, actorUsername, eventType, occurredAt)` — 创建成功审计事件
- `AuditEvent.failure(workspaceId, actorUserId, actorUsername, eventType, reason, occurredAt)` — 创建失败审计事件

#### AuditEventEntry

审计事件查询领域模型（只读视图）。审计查询界面的投影。相比 `AuditEvent` 补充了自增主键 `auditEventId`，用于稳定分页排序（防止时间戳重复导致的页面漂移）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `auditEventId` | `long` | 自增主键，用于稳定分页排序 |
| `workspaceId` | `String` | 工作区 ID |
| `actorUserId` | `String` | 操作者用户标识 |
| `actorUsername` | `String` | 操作者用户名 |
| `eventType` | `String` | 事件类型 |
| `targetType` | `String` | 操作目标类型 |
| `targetId` | `String` | 操作目标标识 |
| `outcome` | `String` | 结果代码（`SUCCESS` / `FAILURE` / `DENIED`） |
| `reason` | `String` | 失败原因代码 |
| `metadata` | `String` | 扩展元数据（JSON 字符串） |
| `occurredAt` | `Instant` | 事件发生时间 |

#### AuditEventPage

审计事件分页结果领域模型。

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `List<AuditEventEntry>` | 当前页审计事件列表 |
| `total` | `long` | 匹配筛选条件的总记录数 |
| `limit` | `int` | 每页最大条目数 |
| `offset` | `int` | 当前页偏移量 |

#### AuditEventSearchCriteria

审计事件查询条件领域模型。封装分页审计事件查询的所有过滤维度。字段为 `null` 表示该维度不过滤。

| 字段 | 类型 | 说明 |
|------|------|------|
| `eventType` | `String` | 事件类型过滤；`null` = 不过滤 |
| `actorUserId` | `String` | 操作者用户 ID 过滤；`null` = 不过滤 |
| `actorKeyword` | `String` | 操作者关键词过滤（用户名或用户 ID）；`null` = 不过滤 |
| `targetType` | `String` | 目标类型过滤；`null` = 不过滤 |
| `targetId` | `String` | 目标 ID 过滤；`null` = 不过滤 |
| `outcome` | `String` | 结果代码过滤；`null` = 不过滤 |
| `occurredFrom` | `Instant` | 起始时间（含）；`null` = 无下界 |
| `occurredTo` | `Instant` | 结束时间（含）；`null` = 无上界 |
| `limit` | `int` | 每页最大条目数 |
| `offset` | `int` | 分页偏移量 |

---

## 出站端口（Port）

### 认证端口

#### LocalAccountRepository

本地账号仓储端口。定义本地账号查询和登录状态更新的持久化契约。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findByUsername` | `Optional<LoginAccount>` | `username` | 按用户名查找登录账号，返回聚合的用户信息、密码哈希、工作区成员资格和锁定状态 |
| `recordFailedLogin` | `LoginFailureState` | `userId, failedAt, maxFailedAttempts, lockUntil` | 记录登录失败并返回更新后的锁定状态。必须原子性地：递增失败计数器、达到阈值时设置锁定截止、更新最后失败时间 |
| `recordSuccessfulLogin` | `void` | `userId, loginAt` | 记录登录成功。必须原子性地：重置失败计数器为 0、清除锁定截止、更新最后登录时间 |

#### BootstrapAdminRepository

初始管理员仓储端口。定义空数据库引导场景的持久化契约。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `countWorkspaceMemberships` | `int` | `workspaceId` | 统计工作区成员数。返回 0 表示工作区为空（需要引导） |
| `saveBootstrapAdmin` | `String` | `BootstrapAdminAccount account` | 写入/补充初始管理员账号。实现层在单一事务中对 `users`、`local_credentials`、`workspace_memberships` 三表执行 UPSERT。幂等语义 |

### 授权端口

#### AuthorizationGrantRepository

授权查询仓储端口（CQRS 读端）。定义授权决策所需的最小查询能力。仅暴露 KB 角色查询和文档权限覆盖查询。只读语义（写操作通过治理接口进行）。实现层过滤 `status = ACTIVE` 记录。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findKnowledgeBaseRole` | `Optional<KnowledgeBaseRole>` | `workspaceId, kbId, userId` | 查询用户对指定 KB 的有效授权角色 |
| `listGrantedKnowledgeBaseRoles` | `Set<KnowledgeBaseRole>` | `workspaceId, userId` | 返回用户通过 ACTIVE 显式授权持有的所有 KB 角色集合 |
| `listGrantedKnowledgeBaseIds` | `Set<String>` | `workspaceId, userId` | 返回用户有 ACTIVE 显式授权的 KB ID 集合。用于 KB 列表可见性收窄 |
| `findDocumentPermission` | `Optional<DocumentPermission>` | `workspaceId, documentId, userId` | 查询用户的有效文档权限覆盖。`DOC_DENY` 最高优先级 |

#### KnowledgeBaseGrantManagementRepository

知识库授权治理仓储端口。定义 KB 授权查询、授予和撤销的持久化契约。所有查询方法仅返回 ACTIVE 记录。撤销为软删除（状态变为 DISABLED）。`saveGrant` 使用 Upsert 语义。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findActiveGrants` | `List<KnowledgeBaseGrant>` | `workspaceId, kbId` | 查询知识库的所有活跃授权 |
| `findActiveGrantsByUser` | `List<KnowledgeBaseGrant>` | `workspaceId, userId` | 查询用户的所有活跃 KB 授权 |
| `findActiveGrant` | `Optional<KnowledgeBaseGrant>` | `workspaceId, kbId, userId` | 查询单条匹配的活跃授权 |
| `saveGrant` | `void` | `workspaceId, kbId, userId, role, updatedAt` | 授予或更新 KB 授权（Upsert） |
| `disableGrant` | `boolean` | `workspaceId, kbId, userId, updatedAt` | 撤销 KB 授权（软删除）。返回是否找到匹配记录 |

#### DocumentGrantManagementRepository

文档授权治理仓储端口。定义文档级授权查询、授予和撤销的持久化契约。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findActiveGrants` | `List<DocumentGrant>` | `workspaceId, documentId` | 查询文档的所有活跃授权 |
| `findActiveGrantsByUser` | `List<DocumentGrant>` | `workspaceId, userId` | 查询用户的所有活跃文档授权 |
| `findActiveGrant` | `Optional<DocumentGrant>` | `workspaceId, documentId, userId` | 查询单条匹配的活跃授权 |
| `saveGrant` | `void` | `workspaceId, documentId, userId, permission, updatedAt` | 授予或更新文档授权（Upsert） |
| `disableGrant` | `boolean` | `workspaceId, documentId, userId, updatedAt` | 撤销文档授权（软删除）。返回是否找到匹配记录 |

### 治理端口

#### ManagedAccountRepository

账号治理仓储端口。所有操作限定工作区范围，保障多租户数据安全。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findWorkspaceAccounts` | `List<ManagedAccount>` | `workspaceId` | 列出工作区所有托管账号 |
| `findWorkspaceAccount` | `Optional<ManagedAccount>` | `workspaceId, userId` | 查询单个托管账号 |
| `existsUsername` | `boolean` | `username` | 检查用户名是否已被占用 |
| `createAccount` | `ManagedAccount` | `workspaceId, username, displayName, passwordHash, workspaceRole, now` | 创建托管账号。单一事务插入用户记录、本地凭证和工作区成员资格，然后回读确认一致性 |
| `updateUserStatus` | `boolean` | `workspaceId, userId, userStatus, updatedAt` | 更新用户状态（`ACTIVE` / `DISABLED`） |
| `resetPassword` | `boolean` | `workspaceId, userId, passwordHash, updatedAt` | 重置密码。更新密码哈希并清除锁定状态 |
| `deactivateMembership` | `boolean` | `workspaceId, userId, updatedAt` | 将成员资格标记为 `INACTIVE`。仅当前状态为 ACTIVE 时执行（幂等） |

#### WorkspaceMemberRepository

工作区成员治理仓储端口。所有方法操作"有效成员"——用户状态和成员资格状态都必须为 ACTIVE。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findActiveMembers` | `List<WorkspaceMember>` | `workspaceId` | 列出工作区的有效成员 |
| `findActiveMember` | `Optional<WorkspaceMember>` | `workspaceId, userId` | 查询单个有效成员 |
| `updateWorkspaceRole` | `boolean` | `workspaceId, userId, role, updatedAt` | 更新成员工作区角色。条件更新：仅在目标成员满足"有效"条件时执行 |

### 审计端口

#### AuditEventRepository

审计事件仓储端口（写端）。仅定义持久化方法。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `save` | `void` | `AuditEvent event` | 持久化一条审计事件。仅追加（不修改、不删除） |

#### AuditEventQueryRepository

审计事件查询仓储端口（CQRS 读端）。与写端分离，支持动态多维过滤。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `findPage` | `AuditEventPage` | `workspaceId, criteria` | 按工作区和筛选条件分页查询审计事件。强制租户级数据隔离 |

---

## 关联关系

```
LoginAccount ──(聚合)──→ users + local_credentials + workspace_memberships + login_lock_states
ManagedAccount ──(聚合)──→ users + workspace_memberships + login_lock_states
WorkspaceMember ──(聚合)──→ users + workspace_memberships
BootstrapAdminAccount ──(写入)──→ users + local_credentials + workspace_memberships

AuthorizationGrantRepository ──(读取)──→ knowledge_base_grants + document_grants
KnowledgeBaseGrantManagementRepository ──(治理)──→ knowledge_base_grants
DocumentGrantManagementRepository ──(治理)──→ document_grants

AuditEvent ──(写入)──→ audit_events
AuditEventEntry ──(读取)──→ audit_events
```

## 设计约束

- **零框架注解**：domain 层不包含任何 Spring 或 JPA 注解
- **只读视图模型**：所有 record 除 `LoginFailureState.locked()` 外不含业务方法，业务逻辑集中在 application 层
- **软删除**：授权记录使用 `ACTIVE` / `DISABLED` 状态标记，不物理删除
- **Upsert 语义**：授权保存使用 UPSERT，保证幂等性
- **CQRS 分离**：审计事件读写端口分离（`AuditEventRepository` vs `AuditEventQueryRepository`），授权读取与治理分离（`AuthorizationGrantRepository` vs `*ManagementRepository`）
- **多租户隔离**：所有端口方法要求 `workspaceId` 作为必选参数

---

_生成时间: 2026-06-15 | 扫描模式: 深度扫描_
