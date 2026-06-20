---
baseline_commit: d41f617
---

# Story 2.1: Flyway V9 tsvector 全文检索基础设施

Status: done

## Story

作为开发者，
我希望数据库支持全文检索索引，
以便 BM25 稀疏检索路径有底层基础设施支撑。

## Acceptance Criteria

1. **Given** `vector_store` 表有 `content` 列但无全文检索支持
   **When** 新增 `V9__hybrid_search_tsvector.sql`
   **Then** 添加 `content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED` 生成列

2. **Given** tsvector 列已创建
   **When** 创建 GIN 索引
   **Then** 索引名为 `idx_vector_store_fts ON vector_store USING GIN (content_tsv)`（AD-8 命名锁定）

3. **Given** 使用 `'simple'` 文本搜索配置
   **When** 迁移执行
   **Then** 中文内容逐字 token（每个汉字一个 token），英文内容按空格/标点 token

4. **Given** 迁移文件执行成功
   **When** 检查已有数据
   **Then** 现有 `content_tsv` 列自动回填（GENERATED ALWAYS AS 是 STORED 列，PostgreSQL 自动计算）

5. **Given** Flyway 迁移纪律
   **When** 新增 V9 迁移
   **Then** 不修改已执行的 V1-V8 迁移文件

6. **Given** 迁移文件内容
   **When** 检查 SQL
   **Then** 列名严格为 `content_tsv`（不是 `content_tsvector`、`tsv_content` 等）
   **And** 索引名严格为 `idx_vector_store_fts`（不是 `idx_vector_store_content_tsv`、`idx_fts` 等）
   **And** 配置严格为 `'simple'`（不是 `'english'`、`'chinese'`）

## Tasks / Subtasks

- [x] Task 1: 新增 Flyway V9 迁移文件（AC: #1, #2, #3, #4, #5, #6）
  - [x] 1.1 创建 `src/main/resources/db/migration/V9__hybrid_search_tsvector.sql`
  - [x] 1.2 添加 `ALTER TABLE vector_store ADD COLUMN content_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED`
  - [x] 1.3 添加 `CREATE INDEX idx_vector_store_fts ON vector_store USING GIN (content_tsv)`
  - [x] 1.4 确认不修改 V1-V8 任何文件

- [x] Task 2: 验证迁移可执行（AC: #4）
  - [x] 2.1 `mvn clean compile` — 编译通过（Flyway 校验）
  - [x] 2.2 本地启动 `mvn spring-boot:run` — Flyway 验证 9 个迁移成功，schema up to date
  - [x] 2.3 连接数据库验证：`\d vector_store` 确认 `content_tsv` 列和 `idx_vector_store_fts` 索引存在
  - [x] 2.4 验证已有数据自动回填：`SELECT id, content_tsv FROM vector_store LIMIT 5` — 非空，分词正确

## Dev Notes

### 前置故事上下文

- Story 1.1（done）：`RetrievedChunk` 已有 `double score` 字段
- Story 1.2（done）：`RerankingPort` + `NoOpRerankingAdapter` 已就位
- Story 1.3（done）：`QaRetrievalProperties` 配置外部化已完成
- Story 1.4（done）：`QueryType` 枚举 + `QueryClassifierPort` 接口已定义
- Story 1.5（done）：`RuleBasedQueryClassifier` 已实现
- Story 1.6（done）：`AskQuestionApplicationService` 已集成 CHITCHAT 拦截

本 Story 是 Epic 2 的第一个 Story，为 BM25 稀疏检索准备数据库基础设施。

### 当前 vector_store 表结构

```
文件: src/main/resources/db/migration/V1__auth_flyway_schema.sql (lines 150-158)

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1024)
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
ON vector_store USING HNSW (embedding vector_cosine_ops);
```

**现有列：** id (UUID PK), content (text), metadata (json), embedding (vector(1024))
**现有索引：** idx_vector_store_embedding (HNSW, cosine distance)

V2-V8 迁移文件均不涉及 `vector_store` 表。

### V9 迁移文件内容（精确 SQL）

```sql
-- V9__hybrid_search_tsvector.sql
-- 添加 tsvector 生成列和 GIN 索引，为 BM25 稀疏检索提供基础设施

ALTER TABLE vector_store
    ADD COLUMN content_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

CREATE INDEX idx_vector_store_fts
    ON vector_store USING GIN (content_tsv);
```

**关键设计决策：**

1. **GENERATED ALWAYS AS ... STORED** — PostgreSQL 自动维护，INSERT/UPDATE content 时自动重新计算 content_tsv，无需应用层干预
2. **`'simple'` 配置** — 英文按空格/标点分词，中文逐字拆分为单字符 token。NFR-2 零新依赖约束下不引入 zhparser
3. **GIN 索引** — 支持 `@@` 操作符和 `ts_rank()` 函数，是全文检索的标准索引类型

### 命名锁定（AD-8，不可协商）

| 对象 | 名称 | 禁止的替代名 | 理由 |
|------|------|-------------|------|
| 列名 | `content_tsv` | `content_tsvector`, `tsv_content` | Flyway 迁移不可回滚，命名一次到位 |
| 索引名 | `idx_vector_store_fts` | `idx_vector_store_content_tsv`, `idx_fts` | 与现有索引命名风格一致（idx_表名_用途） |
| 配置 | `'simple'` | `'english'`, `'chinese'` | 'simple' 不做语言特定词干提取，最通用 |

### `'simple'` 配置的分词行为

| 输入 | 分词结果 | 说明 |
|------|---------|------|
| `"Flyway migration"` | `flyway`, `migration` | 英文按空格分词，自动 lowercase |
| `"PGVector 配置"` | `pgvector`, `配`, `置` | 英文按词，中文逐字拆分 |
| `"你好世界"` | `你`, `好`, `世`, `界` | 中文逐字拆分为单字符 token |

**注意：** 中文逐字 token 在 BM25 检索中效果有限（Story 2.3 会依赖此列）。如果后续评估数据证明 `'simple'` 不足，Phase 2 可考虑引入 zhparser（AD-7 延后决策）。

### PostgreSQL tsvector 自动回填机制

`GENERATED ALWAYS AS ... STORED` 是 PostgreSQL 12+ 的特性（本项目使用 PG 16）：
- 列值由表达式自动计算，不可手动 INSERT/UPDATE
- `STORED` 意味着物理存储（非虚拟列），占额外磁盘空间但查询无需重算
- 执行 `ALTER TABLE ADD COLUMN` 时，PostgreSQL 自动为所有已有行计算 `content_tsv`
- 后续 INSERT/UPDATE `content` 列时，`content_tsv` 自动同步更新

**风险：** 如果 `vector_store` 表数据量很大（>100 万行），`ALTER TABLE ADD COLUMN` 可能需要较长时间（几秒到几分钟）。当前项目规模不构成问题。

### 架构约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| 不修改已执行的 Flyway 迁移 | ✅ | 纯新增 V9 文件 |
| NFR-2 零新外部依赖 | ✅ | 使用 PG 原生 tsvector + GIN，无新 Maven 依赖 |
| NFR-3 六边形合规 | ✅ | 数据库迁移不影响 Java 层边界 |
| AD-8 命名锁定 | ✅ | 列名、索引名、配置严格一致 |

### 本 Story 无 Java 代码变更

这是一个纯数据库迁移 Story，不涉及任何 Java 代码修改。后续 Story 2.3（SparseRetrievalAdapter）将依赖此迁移创建的 `content_tsv` 列。

### Project Structure Notes

- 新增文件：`src/main/resources/db/migration/V9__hybrid_search_tsvector.sql`
- 不修改任何现有文件
- 不涉及 Java 测试（迁移正确性通过手动数据库验证确认）

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 2.1] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#Flyway V9 命名] — 命名锁定规则
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#实施顺序] — Phase 3 Step 7（FR-7）
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-7] — tsvector 迁移需求
- [Source: src/main/resources/db/migration/V1__auth_flyway_schema.sql#L150-158] — vector_store 表定义
- [Source: docs/project-context.md#数据库与迁移] — Flyway 迁移纪律
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-8] — Flyway V9 命名约束

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- 无调试问题。纯 SQL 迁移文件，一次通过。

### Completion Notes List

- 创建 `V9__hybrid_search_tsvector.sql`，包含 `content_tsv` tsvector 生成列 + GIN 索引
- 命名严格遵循 AD-8：列名 `content_tsv`、索引名 `idx_vector_store_fts`、配置 `'simple'`
- `mvn clean compile` 编译通过
- Flyway 验证 9 个迁移全部成功，schema up to date
- 数据库 DDL 确认 content_tsv 列和 idx_vector_store_fts 索引存在
- 已有数据自动回填验证通过，tsvector 分词结果正确
- V1-V8 迁移文件未修改（git status 确认）

### File List

- src/main/resources/db/migration/V9__hybrid_search_tsvector.sql (新增)

## Change Log

- feat(qa): Story 2.1 — Flyway V9 tsvector 全文检索基础设施（2026-06-17）
