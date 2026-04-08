# my-AI

`my-AI` 是一个基于 Spring Boot + Spring AI 的文档入库与检索基线项目。  
当前阶段重点在 `ingest`（文档受理与处理）链路，目标是把“上传 -> 可追踪 -> 可索引”跑通，并为后续 RAG 问答能力打基础。

## 1. 当前能力（截至 2026-04-07）

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
  - 源文件读取 -> Tika 解析 + 文本清洗 -> 分块 -> 向量写入（PGVector）-> 状态收口
- 分块策略：
  - 结构优先 + 长度兜底
  - 参数：`chunk=500`、`overlap=100`
- 已实现 API（`ingest`）：
  - `POST /api/v1/documents/upload`
  - `GET /api/v1/documents/{documentId}/status`
  - `GET /api/v1/documents/{documentId}/chunks/preview`
- 规划中 API（未实现）：
  - `GET /api/v1/knowledge-bases`
  - `POST /api/v1/qa/ask`
  - `POST /api/v1/documents/{documentId}/reprocess`

## 2. 技术栈

- Java 21
- Spring Boot 3.5.8
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.x（DashScope + Agent Framework）
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
- 准备 DashScope API Key（用于 embedding / chat）

### 5.2 环境变量（常用）

- `DASHSCOPE_API_KEY`
- `DASHSCOPE_CHAT_MODEL`（默认 `qwen-plus`）
- `DASHSCOPE_EMBEDDING_MODEL`（默认 `text-embedding-v4`）
- `DASHSCOPE_EMBEDDING_DIMENSIONS`（默认 `1024`，需与模型维度一致）
- `PGVECTOR_DATASOURCE_URL`（默认 `jdbc:postgresql://localhost:5432/myai`）
- `PGVECTOR_DATASOURCE_USERNAME`（默认 `admin`）
- `PGVECTOR_DATASOURCE_PASSWORD`（默认 `admin`）
- `INGEST_WORKER_ENABLED`（默认 `true`）
- `INGEST_WORKER_POLL_DELAY_MS`（默认 `5000`）
- `INGEST_PARSER_MAX_TEXT_LENGTH`（默认 `2000000`）
- `INGEST_PARSER_PARSE_EMBEDDED_RESOURCE`（默认 `false`）
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

说明：
- `MyAiApplicationTests` 会拉起完整 Spring 上下文，依赖本地 PostgreSQL（默认 `localhost:5432`）。
- 若仅验证当前已完成的 ingest 模块单测，可执行：

```bash
.\mvnw.cmd "-Dtest=!MyAiApplicationTests" test
```

## 6. API 摘要

### 6.1 上传文档

- `POST /api/v1/documents/upload`
- `multipart/form-data`
  - `file`：必填
  - `kbId`：可选，默认 `default`

### 6.2 查询状态

- `GET /api/v1/documents/{documentId}/status`

### 6.3 分块预览（调试）

- `GET /api/v1/documents/{documentId}/chunks/preview`
- 可选参数：
  - `limit`（默认 20，范围 1~200）
  - `previewChars`（默认 200，范围 20~2000）
- 用途：验证“向量化前分块文本”是否符合预期

### 6.4 重处理（草案）

- `POST /api/v1/documents/{documentId}/reprocess`
- 当前仍处于草案阶段（接口契约已预留，代码未完整实现）

## 7. 当前边界与注意事项

- worker 默认开启；如需关闭可显式设置 `INGEST_WORKER_ENABLED=false`
- 解析已升级为 Tika 基线能力；扫描版 PDF 的 OCR 与复杂版式提取仍待增强
- 瞬时错误重试（3 次指数退避）尚未完整实现
- reprocess “先删旧向量再重建”流程尚待落地

## 8. 版本目标

- `V1`：完成 ingest 最小闭环与可追踪处理
- `V2`：增强解析能力、重试机制、reprocess 与更完整的检索问答链路

## 9. 前端控制台（web）

- 前端工程根目录：`web/`
- 技术栈：React + TypeScript + Vite + React Router + TanStack Query + Ant Design + zod
- 当前页面范围：
  - `ingest/upload`
  - `ingest/status`
  - `ingest/chunks-preview`
  - `knowledge/qa/reprocess`（草案占位页）

启动方式：

```bash
cd web
npm install
npm run dev
```

联调默认通过 Vite 代理：
- `/api/** -> http://localhost:8080`
- 可通过 `web/.env.example` 的 `VITE_PROXY_TARGET` 覆盖
