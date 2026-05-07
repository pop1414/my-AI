# my-AI

`my-AI` 是一个基于 Spring Boot + Spring AI 的文档入库与检索基线项目。  
当前阶段已按 DDD-Lite 拆分为 `ingest / knowledge / qa` 三个子域，目标是把“上传 -> 可追踪 -> 可索引 -> 可问答”最小闭环跑通。

## 0. 项目入口

如果你是隔了一段时间重新接手这个项目，建议先看下面这些入口：

- 项目文档总导航：[docs/README.md](./docs/README.md)
- 产品范围：[docs/01-product-scope.md](./docs/01-product-scope.md)
- 路线图：[docs/02-roadmap.md](./docs/02-roadmap.md)
- 架构总览：[docs/03-architecture.md](./docs/03-architecture.md)
- API 契约：[docs/04-api-contract.yaml](./docs/04-api-contract.yaml)
- V1 收口计划：[docs/runbooks/v1-closure-plan.md](./docs/runbooks/v1-closure-plan.md)
- V1 版本归档：[docs/runbooks/v1-release-archive.md](./docs/runbooks/v1-release-archive.md)
- V1.1 规划草案：[docs/runbooks/v1-1-plan.md](./docs/runbooks/v1-1-plan.md)
- 文档工作流：[docs/runbooks/my-ai-document-workflow.md](./docs/runbooks/my-ai-document-workflow.md)
- Git 工作流：[docs/runbooks/my-ai-git-workflow.md](./docs/runbooks/my-ai-git-workflow.md)
- 学习沉淀入口：[docs/learning/README.md](./docs/learning/README.md)
- 课程交付入口：[deliverables/course/README.md](./deliverables/course/README.md)

## 0.1 文档分层

仓库内文档采用三层结构：

- `docs/`：工程真源层，描述当前系统事实
- `docs/learning/`：学习沉淀层，记录原理理解、踩坑、复盘与面试表达
- `deliverables/course/`：课程交付层，从工程文档整理导出，不作为系统事实真源

规则：

- 当前系统事实以 `docs/` 为准
- 历史变化通过 ADR、Roadmap、Release Notes 留痕
- 课程材料与长期工程文档分开维护，但课程内容主要整理自工程真源

## 1. 当前能力（截至 2026-05-07）

- 上传受理：`POST /api/v1/documents/upload`
    - 返回 `documentId + ACCEPTED`
    - `documentId` 语义为“文档资产 ID”（不是一次性任务 ID）
- 状态查询：`GET /api/v1/documents/{documentId}/status`
    - 删除成功后仍可查询，状态返回 `DELETED`（不返回 `404`）
- 受理幂等：
    - 基于 `kbId + fileHash(SHA-256)` 查重
    - 重复上传复用既有 `documentId`（仅限未删除资产）
    - 已 `DELETED` 后允许同 `kbId + fileHash` 重新上传（生成新 `documentId`）
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
    - `POST /api/v1/documents/{documentId}/reprocess`
    - `DELETE /api/v1/documents/{documentId}`
- 已实现 API（knowledge / qa）：
    - `GET /api/v1/knowledge-bases`（仅统计 `INDEXED`）
    - `POST /api/v1/qa/ask`（同步返回）
    - 无命中场景：`200 + 兜底回答 + 空 references`
    - `references` 结构：`documentId/chunkIndex/contentPreview`

- 前端控制台（`web/`，截至 2026-05-06）：
    - React 19 + TypeScript 6 + Vite 8 + Ant Design 6
    - 7 个功能页面，按路由懒加载代码拆分
    - 知识库统计列表 + 单轮问答（answer + references）
    - 上传 → 状态查询 → 分块预览 → 重处理 → 删除 完整链路
    - TanStack Query 自动轮询 + 终态检测
    - Zod 运行时接口校验

## 2. 技术栈

- Java 21
- Spring Boot 3.5.8
- Spring AI 1.1.2
- Spring AI Alibaba 1.1.2.x（DashScope + Agent Framework）
- PostgreSQL + PGVector
- Maven Wrapper（`mvnw` / `mvnw.cmd`）

## 3. 架构分层（DDD-Lite）

- `ingest`：文档资产入库生命周期（受理/处理/重处理/删除）
- `knowledge`：知识库目录与统计视图（只读聚合）
- `qa`：检索与回答生成编排（同步问答）
- 各子域统一采用 `interfaces / application / domain / infrastructure` 四层结构

关键目录：

- `src/main/java/io/github/spike/myai/ingest`
- `src/main/java/io/github/spike/myai/knowledge`
- `src/main/java/io/github/spike/myai/qa`
- `src/test/java/io/github/spike/myai/ingest`
- `src/test/java/io/github/spike/myai/knowledge`
- `src/test/java/io/github/spike/myai/qa`
- `docs/`（设计文档、ADR、图纸）

## 4. 文档入口

- 架构总览：`docs/03-architecture.md`
- API 契约：`docs/04-api-contract.yaml`
- 受理闭环：`docs/06-ingest-acceptance-closure.md`
- 处理执行：`docs/07-ingest-processing-execution.md`
- ADR：
    - `docs/adr/ADR-0003-v1-dashscope-pgvector.md`（V1 基线：DashScope + PGVector）
    - `docs/adr/ADR-0004-v1-ingest-processing-strategy.md`（状态：Accepted）

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
- `INGEST_SCHEMA_CHECK_ENABLED`（默认 `true`，启动时进行 ingest 表结构自检）

### 5.3 启动

Windows:

```bash
.\mvnw.cmd spring-boot:run
```

或使用一键脚本（先拉起 PG，再启动后端）：

```bash
.\infra\dev-up.ps1
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

或使用一键脚本：

```bash
./infra/dev-up.sh
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

### 5.5 V1 本地闭环演示

完整闭环：上传 → 入库 → 知识库统计 → 问答，可通过前端控制台或 curl 完成。

**前端方式**（推荐）：

```bash
cd web
npm install
npm run dev
```

访问 `http://localhost:3000`，按左侧菜单依次操作：

1. 文档上传 → 拖入文件提交
2. 状态查询 → 等待文档到达 `INDEXED`
3. 知识库 → 查看 `indexedDocumentCount`，点击"去问答"
4. 问答 → 输入问题，查看 `answer` 与引用分块

**curl 方式**：

1. 上传文档：

```powershell
$upload = curl.exe -sS -X POST "http://localhost:8080/api/v1/documents/upload" -F "file=@D:/tmp/sample.txt" -F "kbId=default" | ConvertFrom-Json
$docId = $upload.documentId
```

Linux/macOS:

```bash
UPLOAD_JSON=$(curl -sS -X POST "http://localhost:8080/api/v1/documents/upload" -F "file=@/tmp/sample.txt" -F "kbId=default")
echo "$UPLOAD_JSON"
```

2. 轮询状态直到 INDEXED：

```bash
curl -sS "http://localhost:8080/api/v1/documents/{documentId}/status"
```

3. 查看知识库统计：

```bash
curl -sS "http://localhost:8080/api/v1/knowledge-bases"
```

4. 单轮问答：

```bash
curl -sS -X POST "http://localhost:8080/api/v1/qa/ask" \
  -H "Content-Type: application/json" \
  -d '{"question":"文档主要内容是什么？","kbId":"default","topK":5}'
```

5. 删除文档资产（可选）：

```bash
curl -i -X DELETE "http://localhost:8080/api/v1/documents/{documentId}"
```

6. 删除后再次查状态，预期返回 `DELETED`（不是 `404`）：

```bash
curl -sS "http://localhost:8080/api/v1/documents/{documentId}/status"
```

7. 查看 ingest 指标：

```bash
curl -sS "http://localhost:8080/actuator/metrics/myai.ingest.process.success.total"
curl -sS "http://localhost:8080/actuator/metrics/myai.ingest.process.failed.total"
curl -sS "http://localhost:8080/actuator/metrics/myai.ingest.process.retry_scheduled.total"
curl -sS "http://localhost:8080/actuator/metrics/myai.ingest.delete.conflict.total"
curl -sS "http://localhost:8080/actuator/metrics/myai.ingest.delete.success.total"
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
    - `offset`（默认 0，范围 0~100000）
    - `previewChars`（默认 200，范围 20~2000）
- 用途：验证“向量化前分块文本”是否符合预期

### 6.4 重处理

- `POST /api/v1/documents/{documentId}/reprocess`
- 允许状态：`FAILED` / `INDEXED`（`INGESTING` 返回 `409`）

### 6.5 删除文档资产

- `DELETE /api/v1/documents/{documentId}`
- 删除行为：
    - 删除源文件（`{root}/{documentId}`）
    - 删除该 `documentId` 的全部向量版本
    - 状态推进：`可删状态 -> DELETING -> DELETED`
- 返回语义：
    - 成功或重复删除：`204`
    - 文档不存在：`404`
    - `INGESTING/DELETING`：`409`

### 6.6 查询知识库列表

- `GET /api/v1/knowledge-bases`
- 返回字段：
    - `id`
    - `name`（V1 与 `id` 一致）
    - `indexedDocumentCount`（统计口径：`status=INDEXED`）

### 6.7 文档问答（同步）

- `POST /api/v1/qa/ask`
- 请求字段：
    - `question`：必填非空
    - `kbId`：可选，默认 `default`
    - `topK`：可选，默认 `5`，范围 `1~20`
- 返回字段：
    - `answer`
    - `references`（chunk 级对象数组）
        - `documentId`
        - `chunkIndex`
        - `contentPreview`
- 说明：
    - 当前仅支持同步返回
    - SSE 仅文档预留，暂不开放接口

## 7. 当前边界与注意事项

- worker 默认开启；如需关闭可显式设置 `INGEST_WORKER_ENABLED=false`
- 启动时默认执行 `ingest_documents` 结构自检；如需临时跳过可设置 `INGEST_SCHEMA_CHECK_ENABLED=false`
- 解析已升级为 Tika 基线能力；扫描版 PDF 的 OCR 与复杂版式提取仍待增强
- 状态枚举包含：`UPLOADED / INGESTING / INDEXED / FAILED / DELETING / DELETED`
- 同一 `kbId + fileHash` 在 `DELETED` 后允许重新上传（生成新 documentId）

## 8. 版本目标

- `V1.0.0`：已于 `2026-05-07` 完成版本归档，形成 ingest / knowledge / qa 最小完整闭环与前端控制台演示链路
- `V1.1`：规划补齐用户/权限、文档列表与更完整的知识库管理体验
- `V2`：增强解析能力与更完整的检索问答链路

## 9. 前端控制台（web）

- 前端工程根目录：`web/`
- 技术栈：React + TypeScript + Vite + React Router + TanStack Query + Ant Design + zod
- 当前页面范围：
    - `ingest/upload`
    - `ingest/status`
    - `ingest/chunks-preview`
    - `ingest/reprocess`
    - `ingest/delete`
    - `knowledge`
    - `qa`
- 当前控制台能力：
    - 文档上传、状态查询、分块预览、重处理、删除
    - 知识库统计列表与跳转问答
    - 单轮问答结果与引用来源展示

启动方式：

```bash
cd web
npm install
npm run dev
```

联调默认通过 Vite 代理：

- `/api/** -> http://localhost:8080`
- 可通过 `web/.env.example` 的 `VITE_PROXY_TARGET` 覆盖
