# 会话交接包：文档列表与管理台

日期：2026-05-08  
仓库：`D:\Code\project\my-AI`

## 1. 主题目标回顾

本主题的目标是把当前依赖手填 `documentId` 的单点操作页，收拢为一个面向真实使用的文档管理主入口。

计划来源：

- [V1.1 总规划](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\v1-1-plan.md)
- [主题执行计划：02-文档列表与管理台](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\02-文档列表与管理台.md)

## 2. 当前状态

V1.1 主题一“文档列表与管理台”已经完成主体实现，并通过了基础验证。

当前已具备：

- 后端文档列表接口：`GET /api/v1/documents`
- 后端分页、筛选、默认排除 `DELETED` 规则
- 控制台文档列表页与默认落点切换
- 文档列表 API 前端接入
- 列表页基础操作入口：状态 / 分块预览 / 重处理 / 删除

已验证：

- 后端编译：`.\\mvnw.cmd -q -DskipTests compile`
- 后端定向测试：`.\\mvnw.cmd -q "-Dtest=DocumentIngestControllerTest,ListDocumentsApplicationServiceTest,JdbcDocumentListRepositoryTest,JdbcDocumentRepositoryTest" test`
- 前端构建：`npm.cmd run build`

## 3. 当前完成情况拆解

### 3.1 后端已完成

- 已新增 `GET /api/v1/documents`
- 已支持 `kbId / status / filename / limit / offset`
- 已实现分页响应结构：
  - `items`
  - `total`
  - `limit`
  - `offset`
- 已实现默认过滤规则：
  - 未传 `status` 时默认排除 `DELETED`
  - 显式传 `status=DELETED` 时可查询已删除文档
- 已实现默认排序：
  - `createdAt DESC`
- 已将文档列表查询拆为独立读模型链路：
  - `ListDocumentsQuery`
  - `ListDocumentsUseCase`
  - `ListDocumentsApplicationService`
  - `DocumentListRepository`
  - `JdbcDocumentListRepository`
- 已补充控制器、应用层、JDBC 查询相关测试

### 3.2 前端已完成

- 控制台左侧导航已新增“文档列表”
- 控制台默认落点已切到文档列表页
- 已新增文档列表页：
  - [IngestListPage.tsx](D:\Code\project\my-AI\web\src\features\ingest\pages\IngestListPage.tsx)
- 前端已接入文档列表查询 API：
  - [ingestApi.ts](D:\Code\project\my-AI\web\src\shared\api\ingestApi.ts)
- 列表页已具备：
  - `kbId / status / filename` 筛选
  - 分页展示
  - 文档状态标签展示
  - 失败原因列展示
  - 跳转状态 / 分块预览 / 重处理 / 删除入口

## 4. 本次判断结论

当前结论不是“完全未完成”，也不是“已经 100% 收口”，而是：

**主题主体已完成，前后端都已经可运行，但与执行计划相比仍有几处明显偏差待收口。**

最重要的判断是：

- 后端主链路已可认为完成
- 前端已完成基础可用版
- 剩余工作主要是“按计划收紧行为”和“文档同步收口”

## 5. 与计划一致的部分

当前已经与计划保持一致的关键点：

- 后端分页列表接口已经存在
- 后端筛选参数与默认排除 `DELETED` 规则已经存在
- 控制台已经有“文档列表”正式入口
- 控制台默认进入文档列表页
- 列表页已经具备知识库、状态、文件名筛选能力
- 列表页已经具备状态 / 分块预览 / 重处理 / 删除的进入点

## 6. 与计划存在的关键偏差

下一窗口接手时，优先关注这 6 项：

1. 当前前端路由是 `/ingest/list`，计划写的是 `/ingest/documents`
2. 当前行内跳转依赖 `localStorage` 传递 `documentId`，计划要求走 URL 路径参数
3. 当前知识库下拉尚未明确只展示 `ACTIVE`
4. 当前状态筛选包含 `ACCEPTED`，与计划约定不一致
5. 当前按钮显隐规则还不够严格，更多是“可用”而不是“完全按状态约束”
6. 当前尚未同步更新 README / API 契约 / Roadmap / Release Notes

## 7. 当前阶段判断

如果从“是否已经能演示文档列表管理能力”来判断，当前答案是：

**可以。**

如果从“是否已经完全达到 02-文档列表与管理台执行计划的收口标准”来判断，当前答案是：

**还差最后一轮前端对齐与文档同步。**

## 8. 建议下一窗口目标

建议下一步直接做“文档列表与管理台收口”，而不是再扩展新功能。

推荐目标：

- 统一前端路由命名
- 改掉 `localStorage` 传参，切换为 URL 参数跳转
- 收紧列表页状态枚举与按钮显隐
- `kbId` 下拉只展示 `ACTIVE`
- 补一次主题级联调验收
- 同步主题级文档

建议收口顺序：

1. 统一前端路由与跳转参数策略
2. 收紧列表页状态枚举、知识库过滤与按钮显隐规则
3. 做一次前后端联调验收
4. 同步 README / Roadmap / API 契约 / Release Notes
5. 再考虑是否把本主题标记为 `Completed`

## 9. 推荐阅读入口

- [V1.1 总规划](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\v1-1-plan.md)
- [主题计划：02-文档列表与管理台](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\02-文档列表与管理台.md)
- [文档控制器](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\interfaces\rest\DocumentIngestController.java)
- [文档列表应用服务](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\application\service\ListDocumentsApplicationService.java)
- [文档列表 JDBC 仓储](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\infrastructure\persistence\JdbcDocumentListRepository.java)
- [前端文档列表页](D:\Code\project\my-AI\web\src\features\ingest\pages\IngestListPage.tsx)
- [前端 ingest API](D:\Code\project\my-AI\web\src\shared\api\ingestApi.ts)
- [路由配置](D:\Code\project\my-AI\web\src\app\AppRoutes.tsx)

## 10. 重要提醒

- 当前工作区在本次生成交接文档前是干净的
- 当前这份交接文档已经合并了原“完成概览”的内容，不再需要额外维护第二份同主题总结
- 当前内容是对“现状”的总结，不代表主题已经正式宣布 `Completed`
- 如果下一窗口要继续改前端，请优先做收口，不要同时引入新的体验扩展
- 课程视角下，这个主题已经接近“功能达标”；工程视角下，还需要一次行为对齐和文档同步

## 11. 一句话交接

**文档列表与管理台已经做到了“能用、能构建、能测试”，下一窗口应专注把它从“基础可用版”收口到“与执行计划一致的完成版”。**
