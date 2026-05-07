# 会话交接包：文档列表与管理台

日期：2026-05-07  
仓库：`D:\Code\project\my-AI`

## 1. 当前状态

知识库主数据化已完成实现并完成验证，当前系统已具备：

- `knowledge_bases` 主数据表
- 知识库创建 / 列表 / 编辑接口
- 上传与问答链路的知识库存在性 / 状态校验
- 前端知识库管理页与知识库选择器

已验证：

- 后端编译：`.\\mvnw.cmd -q -DskipTests compile`
- 后端测试：`.\\mvnw.cmd "-Dtest=!MyAiApplicationTests" test`
- 前端构建：`npm.cmd run build`

## 2. 下一窗口目标

下一步进入 **V1.1 主题一：文档列表与管理台**，目标是把当前依赖手填 `documentId` 的单点操作页，收拢为文档管理主入口。

建议目标：

- 新增 `GET /api/v1/documents`
- 支持 `kbId / status / filename` 轻量过滤
- 默认排除 `DELETED`
- 默认按 `createdAt desc`
- 前端新增文档列表页，作为 `ingest` 管理主入口
- 行内提供跳转入口：状态 / 分块预览 / 重处理 / 删除

## 3. 推荐阅读入口

- [V1.1 总规划](D:\Code\project\my-AI\docs\runbooks\plans\v1-1\v1-1-plan.md)
- [知识库控制器](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\knowledge\interfaces\rest\KnowledgeBaseController.java)
- [上传受理应用服务](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\application\service\AcceptUploadApplicationService.java)
- [问答应用服务](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\qa\application\service\AskQuestionApplicationService.java)
- [当前 ingest 控制器](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\interfaces\rest\DocumentIngestController.java)

## 4. 重要提醒

- 当前工作区里，知识库主数据化相关改动 **尚未提交**
- 新窗口做文档列表任务时，建议不要把“知识库主数据化”和“文档列表与管理台”混成一个 commit
- 当前还有两份未跟踪的 V1.1 计划文档草稿，提交代码时注意不要误混入

## 5. 一句话交接

**知识库主数据化已经完成，下一窗口应基于当前 worktree 继续推进“文档列表与管理台”，重点补齐文档集合视角，而不是再回头扩展知识库能力。**
