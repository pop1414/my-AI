# PostgreSQL / PGVector 运行手册

## 目的

本文说明如何在本项目中运行和维护 PostgreSQL。

PostgreSQL 在本项目中同时承载两类数据：

- 业务事实数据：工作区、账号、知识库、文档资产、文档版本、授权与审计。
- 向量检索数据：Spring AI PGVector 使用的 `vector_store` 表。

表结构以 `src/main/resources/db/migration/` 下的 Flyway 迁移为准。应用启动时由 Flyway 管理业务 schema，Spring AI PGVector 只负责使用既有 `vector_store` 表，不负责自动建表。

## 运行前提

- 已安装 Docker 或等价容器运行环境。
- 使用带 `pgvector` 扩展的 PostgreSQL 镜像。
- 后端配置可访问 PostgreSQL。
- 数据库用户具备建表、迁移和读写业务表的权限。

本地开发默认使用：

- image：`pgvector/pgvector:pg16`
- container：`myai-pg`
- database：`myai`
- username：`admin`
- password：`admin`
- port：`5432`

## 推荐配置

后端数据源配置建议通过环境变量注入：

```yaml
spring:
  datasource:
    # PostgreSQL JDBC 连接地址，默认连接本机 myai 数据库
    url: ${PGVECTOR_DATASOURCE_URL:jdbc:postgresql://localhost:5432/myai}
    # 数据库用户名，本地开发默认 admin
    username: ${PGVECTOR_DATASOURCE_USERNAME:admin}
    # 数据库密码，本地开发默认 admin；生产环境必须通过安全环境变量注入
    password: ${PGVECTOR_DATASOURCE_PASSWORD:admin}
  flyway:
    # 启用 Flyway，由迁移脚本统一管理数据库结构
    enabled: true
    # 允许对已有空基线数据库建立 Flyway baseline
    baseline-on-migrate: true
    # baseline 起始版本
    baseline-version: 0
  ai:
    vectorstore:
      pgvector:
        # 表结构由 Flyway 管理，禁止 Spring AI 自动创建或重建 vector_store
        initialize-schema: false
        # 启动时校验 vector_store 结构是否符合 Spring AI PGVector 预期
        schema-validation: true
        # PGVector 表所在 schema
        schema-name: public
        # Spring AI PGVector 使用的向量表名
        table-name: vector_store
        # 向量索引类型，当前使用 HNSW
        index-type: HNSW
        # 相似度计算方式，当前使用余弦距离
        distance-type: COSINE_DISTANCE
        # embedding 向量维度，必须与当前 embedding 模型输出一致
        dimensions: ${DASHSCOPE_EMBEDDING_DIMENSIONS:1024}
        # 单次批量写入向量分块的最大数量
        max-document-batch-size: 10000
        # 禁止启动时删除既有 vector_store
        remove-existing-vector-store-table: false
```

本地 PowerShell 示例：

```powershell
# PostgreSQL JDBC 连接地址
$env:PGVECTOR_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/myai"
# 数据库用户名
$env:PGVECTOR_DATASOURCE_USERNAME = "admin"
# 数据库密码
$env:PGVECTOR_DATASOURCE_PASSWORD = "admin"
# embedding 向量维度，必须与当前 embedding 模型输出一致
$env:DASHSCOPE_EMBEDDING_DIMENSIONS = "1024"
```

## Docker Compose

当前本地开发环境使用 `infra/docker-compose.yml` 中的 `postgres` 服务：

```yaml
services:
  postgres:
    # 使用内置 pgvector 扩展的 PostgreSQL 16 镜像
    image: pgvector/pgvector:pg16
    # 本地开发容器名称
    container_name: myai-pg
    environment:
      # 初始化数据库名称
      POSTGRES_DB: myai
      # 初始化数据库用户
      POSTGRES_USER: admin
      # 初始化数据库密码；生产环境不得使用默认值
      POSTGRES_PASSWORD: admin
      # 容器时区
      TZ: Asia/Shanghai
    ports:
      # 暴露本机 5432 端口供后端连接
      - "5432:5432"
    volumes:
      # 持久化 PostgreSQL 数据目录
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      # 使用 pg_isready 检查数据库是否可接受连接
      test: ["CMD-SHELL", "pg_isready -U admin -d myai"]
      # 每 5 秒检查一次
      interval: 5s
      # 单次检查超时时间
      timeout: 3s
      # 最多重试 20 次
      retries: 20
```

启动后，容器健康检查应显示 `healthy`。

## Schema 管理

本项目数据库 schema 由 Flyway 管理。

规则：

- 新增表、字段、索引、约束必须通过新的 `V{n}__*.sql` 迁移文件完成。
- 不要手工修改已经提交的历史迁移。
- 不要开启 Spring AI PGVector 自动建表；`initialize-schema` 必须保持 `false`。
- `vector_store` 表结构、向量维度和索引策略也应由 Flyway 管理。
- schema 自检由应用启动阶段执行，用于拒绝不符合当前 ingest 约束的数据库结构。

当前关键约束：

- `vector_store.embedding` 维度应与 embedding 模型输出一致，当前默认 `1024`。
- `vector_store` 使用 HNSW 索引和余弦距离。
- `ingest_documents` 保存 document latest projection。
- `ingest_document_versions` 保存版本级文件事实与处理事实。
- 问答检索必须通过 metadata 约束 `workspaceId`、`kbId`、`documentId`、`documentVersionNumber` 等业务边界。

## 健康检查

启动后至少确认：

- PostgreSQL 容器处于 `healthy` 状态。
- 后端可以成功建立 JDBC 连接。
- Flyway 迁移执行成功。
- 应用 `/actuator/health` 返回 UP。
- `flyway_schema_history` 中存在最新迁移记录。
- `vector_store` 表存在，且向量维度与 `DASHSCOPE_EMBEDDING_DIMENSIONS` 一致。
- 上传并处理测试文档后，`vector_store` 中出现对应分块。

## 常见问题

### 后端连接失败

检查：

- `PGVECTOR_DATASOURCE_URL` 是否指向正确主机和端口。
- PostgreSQL 容器是否启动并健康。
- 本机端口 `5432` 是否被其他服务占用。
- 后端运行环境是否能访问数据库容器网络。

### 账号或密码错误

检查：

- `PGVECTOR_DATASOURCE_USERNAME` 和 `PGVECTOR_DATASOURCE_PASSWORD` 是否与容器环境变量一致。
- 如果修改过密码，已有 Docker volume 不会自动重置数据库用户密码。
- 必要时应创建新数据库用户或按 PostgreSQL 方式修改密码，不要直接删除生产数据卷。

### Flyway 迁移失败

检查：

- 是否修改过已经执行的历史迁移文件。
- 新迁移版本号是否与已有文件冲突。
- SQL 是否依赖尚未创建的表、字段、函数或扩展。
- 当前数据库是否已经存在手工创建但 Flyway 不知道的对象。

处理原则：

- 开发环境可重建空库后重新验证迁移链路。
- 共享环境和生产环境不得直接删除 `flyway_schema_history` 或手工跳过失败迁移。
- 修复应优先追加新迁移，而不是改写已发布迁移。

### PGVector 扩展缺失

现象通常是创建 `vector` 字段或索引失败。

检查：

- 是否使用 `pgvector/pgvector:pg16` 或已安装 pgvector 扩展的 PostgreSQL 镜像。
- Flyway 迁移中是否执行了 `CREATE EXTENSION IF NOT EXISTS vector`。
- 数据库用户是否有创建扩展权限。

### 向量维度不匹配

现象通常是向 `vector_store.embedding` 写入时报维度错误。

检查：

- `DASHSCOPE_EMBEDDING_DIMENSIONS` 是否与当前 embedding 模型输出一致。
- `vector_store.embedding` 字段维度是否与配置一致。
- 是否切换过 embedding 模型但没有同步迁移向量表结构。

注意：向量维度变化通常不是简单配置变更，往往需要重新生成 embedding 并重建索引。

### 问答召回为空

检查：

- 文档版本是否已进入 `INDEXED`。
- `vector_store` 中是否存在对应 `documentId` 和 `documentVersionNumber` 的 metadata。
- 当前用户是否有目标知识库或文档访问权限。
- `kbId`、`workspaceId`、`documentId` 过滤条件是否与 metadata 一致。
- embedding 维度和模型配置是否一致。

### schema 自检失败

schema 自检失败说明数据库结构不满足当前 ingest 读写边界。

检查：

- Flyway 是否执行到最新版本。
- `ingest_documents` latest projection 字段是否存在。
- `ingest_document_versions` 版本事实字段是否存在。
- version 文件哈希相关索引是否存在。
- 当前运行代码是否与数据库迁移版本不匹配。

## 备份

本地或演示环境可使用 PostgreSQL 逻辑备份。

示例：

```powershell
docker exec myai-pg pg_dump -U admin -d myai -F c -f /tmp/myai.backup
docker cp myai-pg:/tmp/myai.backup .\myai.backup
```

恢复示例：

```powershell
docker cp .\myai.backup myai-pg:/tmp/myai.backup
docker exec myai-pg pg_restore -U admin -d myai --clean --if-exists /tmp/myai.backup
```

注意：

- 恢复到非空库前必须确认目标环境，避免覆盖有效数据。
- 生产或长期演示环境应建立定期备份和恢复演练。
- 对象存储中的 source/artifacts 不在 PostgreSQL 备份中，需单独备份 RustFS。

## 回滚

PostgreSQL schema 迁移不建议依赖自动 downgrade。

推荐原则：

- 代码发布失败但未执行破坏性迁移时，优先回滚应用版本。
- 已执行新增表或新增列的迁移时，通常可以保留向前兼容结构。
- 已执行删除列、改类型、重建向量维度等破坏性迁移前，必须先完成备份。
- 需要回滚数据结构时，编写新的向前迁移修正，而不是手工回退 Flyway 历史。

## 运维注意事项

- 不要把生产数据库密码写入仓库。
- 本地默认账号密码只适合开发环境。
- 长期运行环境必须使用持久化 volume。
- 定期检查磁盘空间，向量表和审计表会持续增长。
- 对大批量重处理或重新向量化任务，应关注连接池、索引维护和磁盘 IO。
- 修改 embedding 模型或维度前，必须先制定向量重建计划。
- PostgreSQL 备份不能替代 RustFS 备份；数据库事实与文档对象需要一起纳入恢复方案。
