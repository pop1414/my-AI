# V1 闭环收口计划

状态：Completed（2026-05-07）

## 1. 目标

本轮只做“当前 V1 收口”，目标是把现有后端能力接成一个**可稳定演示的最小完整产品闭环**。

本轮不把权限系统并入范围。  
当前优先级是先把现有 `ingest / knowledge / qa` 能力收成一条完整演示链。

## 1.1 实际结果

本计划对应的 V1 收口工作已完成，当前结果为：

- `knowledge` 页面已落地并接入 `GET /api/v1/knowledge-bases`
- `qa` 页面已落地并接入 `POST /api/v1/qa/ask`
- 前端控制台已形成上传、状态查询、知识库统计、单轮问答的完整闭环
- 前后端文案已进入收口同步阶段，本文件作为历史执行记录继续保留

## 2. 完成标准

V1 完成时，应满足以下条件：

1. 本地真实联调下，用户可以完成：
   - 上传文档
   - 等待入库
   - 查看知识库统计
   - 发起单轮问答并看到引用
2. 前后端文案、路由和说明与实际实现一致
3. README 与前端说明文档能反映当前真实能力

## 3. 本轮范围

### 3.1 已完成事项

#### 前端补齐 `knowledge` 与 `qa`

- 新增正式的 `knowledge` 页面，替换占位页
- `knowledge` 页面只承担**统计列表页**职责：
  - 调用 `GET /api/v1/knowledge-bases`
  - 表格展示 `id / name / indexedDocumentCount`
  - 处理空状态、加载态、错误态
  - 提供“去问答”入口，把所选 `kbId` 带到 `qa` 页

- 新增正式的 `qa` 页面，替换占位页
- `qa` 页面只做**单轮问答**：
  - 字段：`kbId`、`question`、`topK`
  - 调用 `POST /api/v1/qa/ask`
  - 展示 `answer`
  - 展示 `references`：
    - `documentId`
    - `chunkIndex`
    - `contentPreview`
  - 支持无命中兜底回答场景
  - 完整处理加载态、错误态、空输入校验

#### 前端共享状态与 API 收口

- 在前端 API 层新增：
  - `listKnowledgeBases()`
  - `askQuestion()`
- 增加对应的 zod 响应校验
- 使用轻量前端状态约定：
  - `localStorage` 保存最近一次使用的 `kbId`
  - `knowledge` 页选中的 `kbId` 默认带入 `qa` 页

#### V1 联调与收边

- 用真实环境完成一次联调闭环：
  - PostgreSQL + PGVector
  - DashScope Key
  - Spring Boot 后端
  - Vite 前端
- 验证以下场景：
  - 上传文档后状态从 `ACCEPTED/UPLOADED` 推进到 `INDEXED`
  - `knowledge` 页面能看到对应 `kb` 的 `indexedDocumentCount`
  - `qa` 页面能成功返回 `answer + references`
  - 无命中时返回兜底回答

#### 文档同步

- 同步 `README` 和 `web/README`
- 去掉 `knowledge / qa` 仍是占位页的过期描述
- 补充当前 V1 的最小演示顺序

### 3.2 未并入本轮范围的事项

- 不做登录系统
- 不做角色权限
- 不做多轮会话历史
- 不做完整知识库管理台
- 不做工作台级问答产品化打磨

这些内容放到后续版本处理，不并入当前 V1。

## 4. 验收方式

### 4.1 自动验证

- 后端测试：
  - `.\\mvnw.cmd "-Dtest=!MyAiApplicationTests" test`
- 前端构建：
  - `npm run build`

### 4.2 页面手工验证

- `knowledge` 页面：
  - 加载态
  - 空态
  - 错误态
  - 成功态
- `qa` 页面：
  - 正常命中
  - 无命中兜底
  - 参数校验
  - 错误提示

### 4.3 真实联调闭环

按下面顺序验收：

1. 启动 PGVector 和后端
2. 启动前端
3. 上传一份可解析文档
4. 在状态页确认文档到达 `INDEXED`
5. 在 `knowledge` 页看到知识库统计
6. 在 `qa` 页针对同一 `kbId` 提问，得到 `answer + references`
7. 再测试一个无命中问题，确认兜底回答成立

## 5. 当前约束

- V1 仍然是**单用户基线**
- `knowledge` 页本轮只做统计列表，不承接复杂管理动作
- `qa` 页本轮只做单轮问答，不做多轮记忆
- 原则上不新增后端业务接口，除非联调发现阻塞闭环的真实缺口

## 6. 交付结果

本轮完成后，前端应能完整覆盖当前 V1 中“看得见”的能力：

- 上传
- 状态查询
- 分块预览
- 重处理
- 删除
- 知识库统计
- 单轮问答

一句话判断标准：

> 当前 V1 不再只是后端能力集合，而是一个能被稳定演示的最小完整产品闭环。

## 7. 关闭说明

本计划已经完成使命，后续保留用途如下：

- 作为 V1 收口阶段的历史执行记录
- 用于回看本轮范围、边界与验收标准
- 为后续 V1 发布归档、课程展示和面试复盘提供上下文

当前系统事实请以以下文档为准：

- `README.md`
- `docs/product/roadmap.md`
- `docs/architecture/README.md`
- `docs/api/openapi.yaml`
