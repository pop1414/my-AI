# Knowledge 子域 — 领域模型

> 知识库主数据管理

## 概述

Knowledge 子域负责知识库的全生命周期管理，包括创建、更新、启用/停用和软删除。聚合根 `KnowledgeBase` 封装了所有业务规则和不变性约束，是四个子域中最精简的领域模型。

## 枚举类型

### KnowledgeBaseStatus

知识库生命周期状态枚举。

| 常量 | 语义 |
|------|------|
| `ACTIVE` | 启用 — 知识库处于正常工作状态，可接收文档索引与检索请求 |
| `INACTIVE` | 停用 — 知识库处于不可用状态（维护/归档等场景） |
| `DELETED` | 已删除 — 知识库已软删除，保留历史数据与审计追溯 |

---

## 聚合根

### KnowledgeBase

知识库聚合根。承担不变性约束、生命周期管理和业务规则封装三重职责。

**设计决策**：
- 使用 Java record 实现不可变性
- `update` 方法返回新实例而非修改自身，确保线程安全与审计追踪
- `createdAt` 在创建时固化，`updatedAt` 在每次更新时刷新

#### 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `kbId` | `String` | 对外业务键（系统生成的全局唯一标识） |
| `workspaceId` | `String` | 所属工作区标识（当前固定为 `"default"`） |
| `name` | `String` | 展示名称（1~100 字符） |
| `description` | `String` | 描述信息（可为空字符串，最长 500 字符） |
| `status` | `KnowledgeBaseStatus` | 当前生命周期状态 |
| `createdAt` | `Instant` | 创建时间（不可变） |
| `updatedAt` | `Instant` | 最后更新时间 |

#### 工厂方法

| 方法 | 说明 |
|------|------|
| `create(kbId, name, description, status, now)` | 创建新聚合根（默认工作区），createdAt 与 updatedAt 设为同一时间 |
| `create(kbId, workspaceId, name, description, status, now)` | 同上，显式指定工作区 |

#### 实例方法

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `update(nextName, nextDescription, nextStatus, now)` | `KnowledgeBase` | 生成更新后的新实例。保留 kbId 和 createdAt 不变，仅刷新 updatedAt |

#### 验证规则（compact constructor）

| 规则 | 说明 |
|------|------|
| `kbId` | 必填，不可为空白 |
| `workspaceId` | 必填，不可为空白 |
| `name` | 必填，去除首尾空格后长度 1~100 字符 |
| `description` | 选填，null 规整为空字符串，去除首尾空格后最长 500 字符 |
| `status` | 必填，不可为 null |
| `createdAt` / `updatedAt` | 必填，不可为 null |

compact constructor 中会自动对 `name` 去除首尾空格，对 `description` 做 null 归一化和去空格处理。

---

## 读取模型

### KnowledgeBaseSummary

知识库列表摘要视图（读模型）。专用于知识库列表查询场景，与写模型分离。

**与聚合根的区别**：

| 维度 | KnowledgeBase（聚合根） | KnowledgeBaseSummary（视图） |
|------|------------------------|------------------------------|
| 用途 | 创建/更新等写操作 | 列表查询等读操作 |
| 时间戳 | 含 createdAt / updatedAt | 不含 |
| 文档计数 | 不含 | 含 indexedDocumentCount |
| 不变性校验 | 紧凑构造器强校验 | 无（数据来自仓储层，已保证有效） |

| 字段 | 类型 | 说明 |
|------|------|------|
| `kbId` | `String` | 知识库业务键 |
| `workspaceId` | `String` | 所属工作区标识（当前固定为 `"default"`） |
| `name` | `String` | 展示名称 |
| `description` | `String` | 描述信息 |
| `status` | `KnowledgeBaseStatus` | 当前生命周期状态 |
| `indexedDocumentCount` | `long` | 已索引文档数量（聚合统计，非负整数） |

### KnowledgeBaseDocumentCount

知识库文档计数领域模型（查询聚合结果）。承载"按知识库聚合后的文档数量"，通常由持久化层执行分组统计后返回。

| 字段 | 类型 | 说明 |
|------|------|------|
| `kbId` | `String` | 知识库唯一标识 |
| `indexedDocumentCount` | `long` | 已索引文档数量（统计口径：status = INDEXED） |

---

## 出站端口（Port）

### KnowledgeBaseRepository

知识库主数据仓储端口。定义领域层所需的持久化能力契约。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `save` | `void` | `KnowledgeBase` | 保存聚合根（已存在则更新，否则新增） |
| `findByKbId` | `Optional<KnowledgeBase>` | `workspaceId, kbId` | 按业务键查询聚合根（不含软删除） |
| `findByKbIdIncludingDeleted` | `Optional<KnowledgeBase>` | `workspaceId, kbId` | 按业务键查询聚合根（含软删除记录）。仅用于删除等需要识别终态幂等性的治理用例 |
| `listKnowledgeBases` | `List<KnowledgeBaseSummary>` | `workspaceId` | 查询知识库摘要视图列表（排除软删除），含 indexedDocumentCount 聚合统计 |
| `listKnowledgeBasesIncludingDeleted` | `List<KnowledgeBaseSummary>` | `workspaceId` | 查询知识库摘要视图列表（含软删除记录）。仅用于管理员治理视角 |

### KnowledgeBaseIdGenerator

知识库业务键生成器端口。将 ID 生成策略抽象为接口，允许运行时替换实现（UUID、Snowflake、Nano ID 等）。

| 方法 | 返回类型 | 参数 | 说明 |
|------|----------|------|------|
| `nextKbId` | `String` | — | 生成下一个全局唯一的知识库标识符（不可为 null 或空字符串） |

---

## 关联关系

```
KnowledgeBase ──(聚合根)──→ KnowledgeBaseStatus
KnowledgeBaseSummary ──(读模型投影)──→ KnowledgeBase + indexedDocumentCount
KnowledgeBaseDocumentCount ──(聚合统计)──→ ingest_documents (status = INDEXED)

跨子域关联：
ingest_documents.kb_id ──→ knowledge_bases.kb_id
```

## 设计约束

- **不可变更新**：`update()` 返回新实例，不修改当前对象
- **软删除**：通过 `DELETED` 状态标记，不物理删除；`findByKbId` 默认排除已删除记录
- **读写分离**：聚合根 `KnowledgeBase` 用于写操作，`KnowledgeBaseSummary` 用于列表查询
- **软删除查询隔离**：`findByKbIdIncludingDeleted` 和 `listKnowledgeBasesIncludingDeleted` 仅限治理用例，普通业务链路禁止使用
- **compact constructor 副作用**：`name` 自动 trim，`description` 做 null 归一化 + trim

---

_生成时间: 2026-06-15 | 扫描模式: 深度扫描_
