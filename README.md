# my-AI

`my-AI` 是一个基于 Spring Boot + Spring AI 的文档入库与检索基线项目。  
当前阶段重点在 `ingest`（文档受理与处理）链路，目标是把“上传 -> 可追踪 -> 可索引”跑通，并为后续 RAG 问答能力打基础。

## 1. 当前能力（截至 2026-04-02）

- 上传受理：`POST /api/v1/documents/upload`
  - 返回 `documentId + ACCEPTED`
  - `documentId` 语义为“文档资产 ID”（不是一次性任务 ID）
- 状态查询：`GET /api/v1/documents/{documentId}/status`
- 受理幂等：
  - 基于 `kbId + fileHash(SHA-256)` 查重
  - 重复上传复用既有 `documentId`
- 任务抢占幂等：
  - `UPLOADED -> INGESTING` 采用 CAS（Compare-And-Set）更新
- 异步处理（单进程 worker）：
  - worker 抢占成功后触发处理用例
  - 支持状态推进到 `INDEXED` / `FAILED`
- 处理主链路（V1 最小实现）：
  - 源文件读取 -> 文本解析 -> 分块 -> 向量写入（PGVector）-> 状态收口
- 分块策略：
  - 结构优先 + 长度兜底
  - 参数：`chunk=500`、`overlap=100`

## 2. 技术栈

- Java 21
- Spring Boot 4.0.5
- Spring AI 2.0.0-M3
- PostgreSQL + PGVector
- Maven Wrapper（`mvnw` / `mvnw.cmd`）

## 3. 架构分层（ingest）

- `interfaces`：REST 控制器与 DTO
- `application`：用例接口与应用服务编排
- `domain`：领域模型与 Port 抽象
- `infrastructure`：JDBC 仓储、worker、解析/分块/向量适配实现

关键目录：

- `src/main/java/io/github/spike/myai/ingest`
- `src/test/java/io/github/spike/myai/ingest`
- `docs/`（设计文档、ADR、图纸）

## 4. 文档入口

- 架构总览：`docs/03-architecture.md`
- API 契约：`docs/04-api-contract.yaml`
- 受理闭环：`docs/06-ingest-acceptance-closure.md`
- 处理执行：`docs/07-ingest-processing-execution.md`
- ADR：
  - `docs/adr/ADR-0003-v1-openai-pgvector.md`
  - `docs/adr/ADR-0004-v1-ingest-processing-strategy.md`

## 5. 快速开始

### 5.1 前置条件

- 安装 JDK 21
- 启动 PostgreSQL（并可用 PGVector）
- 准备 OpenAI API Key（用于 embedding）

### 5.2 环境变量（常用）

- `OPENAI_API_KEY`
- `PGVECTOR_DATASOURCE_URL`（默认 `jdbc:postgresql://localhost:5432/myai`）
- `PGVECTOR_DATASOURCE_USERNAME`（默认 `admin`）
- `PGVECTOR_DATASOURCE_PASSWORD`（默认 `admin`）
- `INGEST_WORKER_ENABLED`（默认 `false`）
- `INGEST_WORKER_POLL_DELAY_MS`（默认 `5000`）
- `INGEST_STORAGE_ROOT_DIR`（默认 `data/ingest`）
- `INGEST_CHUNK_SIZE`（默认 `500`）
- `INGEST_CHUNK_OVERLAP`（默认 `100`）

### 5.3 启动

Windows:

```bash
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

### 5.4 测试

```bash
.\mvnw.cmd test
```

## 6. API 摘要

### 6.1 上传文档

- `POST /api/v1/documents/upload`
- `multipart/form-data`
  - `file`：必填
  - `kbId`：可选，默认 `default`

### 6.2 查询状态

- `GET /api/v1/documents/{documentId}/status`

### 6.3 重处理（草案）

- `POST /api/v1/documents/{documentId}/reprocess`
- 当前仍处于草案阶段（接口契约已预留，代码未完整实现）

## 7. 当前边界与注意事项

- worker 默认关闭，需要显式开启 `INGEST_WORKER_ENABLED=true`
- V1 解析器当前主要支持文本类文件；PDF/Word 专业解析仍待增强
- 瞬时错误重试（3 次指数退避）尚未完整实现
- reprocess “先删旧向量再重建”流程尚待落地

## 8. 版本目标

- `V1`：完成 ingest 最小闭环与可追踪处理
- `V2`：增强解析能力、重试机制、reprocess 与更完整的检索问答链路

