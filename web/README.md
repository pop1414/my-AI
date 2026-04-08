# my-AI 前端控制台

这是 `my-AI` 的前端控制台工程，当前聚焦 `ingest` 链路联调与验证。

## 技术栈

- React + TypeScript + Vite
- React Router
- TanStack Query
- Ant Design
- zod

## 当前页面范围

- 文档上传：`/ingest/upload`
- 状态查询：`/ingest/status`
- 分块预览：`/ingest/chunks-preview`
- 占位页（草案）：`/knowledge`、`/qa`、`/reprocess`

## 本地开发

```bash
npm install
npm run dev
```

默认访问地址：`http://localhost:5173`

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
