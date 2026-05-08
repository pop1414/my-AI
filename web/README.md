# my-AI 前端控制台

这是 `my-AI` 的前端控制台工程，当前聚焦文档管理、知识库管理与问答链路联调。

## 技术栈

- React + TypeScript + Vite
- React Router
- TanStack Query
- Ant Design
- zod

## 当前页面范围

### Ingest 链（当前基线）

- 文档列表：`/ingest/documents`
    - 默认首页与 `ingest` 管理主入口
    - 支持 `kbId / status / filename` 过滤
    - 统一进入状态 / 分块预览 / 重处理 / 删除页面
- 文档上传：`/ingest/upload`
- 状态查询：`/ingest/status`
- 分块预览：`/ingest/chunks-preview`
- 文档重处理：`/ingest/reprocess`
- 文档删除：`/ingest/delete`

### Knowledge 与 QA（当前基线）

- 知识库管理：`/knowledge`
    - 展示 `id / name / description / status / indexedDocumentCount`
    - 支持创建、编辑、停用
    - 空态提示、加载骨架、错误提示
    - 行点击或"去问答"按钮跳转至 QA 页
- 单轮文档问答：`/qa`
    - 字段：`kbId`、`question`、`topK`
    - 展示 `answer` + `references` 表格
    - 无命中兜底提示
    - 从知识库页携带 `kbId` 跳入

## 本地开发

```bash
npm install
npm run dev
```

默认访问地址：`http://localhost:3000`

## 与后端联调

### 方式一：Vite 代理（默认）

前端请求 `/api/**` 时，会通过 Vite 转发到后端。

默认转发目标：

- `http://localhost:8080`

可通过环境变量覆盖：

```bash
VITE_PROXY_TARGET=http://localhost:8080
```

### 方式二：直接指定 API 基地址

如需绕过代理，可设置：

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## 环境变量示例

参考文件：`web/.env.example`

## 构建与预览

```bash
npm run build
npm run preview
```
