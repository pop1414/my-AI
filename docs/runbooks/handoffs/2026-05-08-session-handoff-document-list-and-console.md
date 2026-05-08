# 会话交接包：文档列表与管理台（已完成归档）

日期：2026-05-08  
状态同步：2026-05-08  
仓库：`D:\Code\project\my-AI`

## 1. 主题目标回顾

本主题的目标，是把此前依赖手填 `documentId` 的单点操作页，收拢为一个面向真实使用的文档管理主入口。

计划来源：

- [V1.1 总规划](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\v1-1-plan.md)
- [主题执行计划：02-文档列表与管理台](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\02-文档列表与管理台.md)

## 2. 当前最终状态

V1.1 主题一“文档列表与管理台”已完成，并且此前与执行计划不一致的部分已经在当前工作区中对齐。

当前应以“已完成归档”理解本主题，而不再以“待收口”理解本主题。

## 3. 已完成能力

### 3.1 后端

- 已提供分页文档列表接口：`GET /api/v1/documents`
- 已支持 `kbId / status / filename / limit / offset`
- 已实现默认排除 `DELETED`；显式传 `status=DELETED` 时可查询已删除文档
- 已实现默认排序：`createdAt DESC`
- 已将列表查询拆为独立读模型链路：
  - `ListDocumentsQuery`
  - `ListDocumentsUseCase`
  - `ListDocumentsApplicationService`
  - `DocumentListRepository`
  - `JdbcDocumentListRepository`

### 3.2 前端

- 控制台默认落点已切换到 `/ingest/documents`
- 旧路由 `/ingest/list` 已转为兼容重定向
- 列表页筛选已按计划收紧：
  - `kbId` 仅展示 `ACTIVE` 知识库
  - `status` 枚举与执行计划保持一致
  - 行内按钮显隐按状态约束执行
- 行内跳转已按计划改为 URL 路径参数传递 `documentId`，不再依赖 `localStorage`
- 现有 `status / chunks-preview / reprocess / delete` 页面继续保留，并由列表页统一进入

## 4. 已记录验证

本主题交付时已记录以下基础验证：

- 后端编译：`.\\mvnw.cmd -q -DskipTests compile`
- 后端定向测试：`.\\mvnw.cmd "-Dtest=DocumentIngestControllerTest,ListDocumentsApplicationServiceTest,JdbcDocumentListRepositoryTest,JdbcDocumentRepositoryTest" test`
- 前端构建：`npm.cmd run build`

## 5. 与 V1.1 的关系更新

- 本主题不再作为 V1.1 的待办项存在
- 当前 V1.1 中“知识库主数据化”与“文档列表与管理台”两项管理基础能力，均应按已完成处理
- V1.1 原先剩余的“轻量认证与访问控制”不再作为本主题后续工作承接，而是独立拆出为更完整的 RAG 权限体系规划

## 6. 推荐阅读入口

- [V1.1 总规划](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\v1-1-plan.md)
- [主题计划：02-文档列表与管理台](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\02-文档列表与管理台.md)
- [文档控制器](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\interfaces\rest\DocumentIngestController.java)
- [文档列表应用服务](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\application\service\ListDocumentsApplicationService.java)
- [文档列表 JDBC 仓储](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\infrastructure\persistence\JdbcDocumentListRepository.java)
- [前端文档列表页](D:\Code\project\my-AI\web\src\features\ingest\pages\IngestListPage.tsx)
- [前端 ingest API](D:\Code\project\my-AI\web\src\shared\api\ingestApi.ts)
- [路由配置](D:\Code\project\my-AI\web\src\app\AppRoutes.tsx)

## 7. 一句话交接

**文档列表与管理台已按计划完成，并已从“待收口主题”更新为“已完成归档主题”；后续应把关注点转到独立的 RAG 权限体系规划，而不是继续回到本主题补尾巴。**
