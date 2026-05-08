# 会话交接包：RAG 权限体系阶段二前置收口（auth-flyway-schema）

日期：2026-05-08  
状态同步：2026-05-08  
仓库：`D:\Code\project\my-AI`  
当前分支：`feature/auth-flyway-schema`

## 1. 本次主题定位

本次分支工作，目标不是直接完成完整认证与授权，而是先完成阶段二中的两块前置收口：

1. `Flyway` 接管 schema，关闭仓储和框架侧隐式 DDL
2. 在“当前仍为单工作区运行现实”的前提下，把 `workspace_id` 从数据库约束推进到运行时代码显式字段与查询条件中

计划来源：

- [RAG 权限体系总计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\rag-access-control-plan.md)
- [阶段二实施计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\阶段二实施计划.md)

## 2. 当前完成概览

### 2.1 已完成：DDL 收口与迁移接管

当前已完成以下收口动作：

- 已引入 `Flyway`
- 已开启 `baseline-on-migrate`
- 已关闭 `spring.ai.vectorstore.pgvector.initialize-schema`
- 已将 `knowledge_bases`、`ingest_documents`、`vector_store` 及扩展纳入 Flyway 脚本
- 已移除 `JdbcKnowledgeBaseRepository` 中的建表、默认数据、回填逻辑
- 已移除 `JdbcDocumentRepository` 中的建表、补字段、索引维护逻辑
- 已将 `IngestSchemaVerifier` 调整为只读校验模式，不再依赖仓储初始化顺序

这部分可视为阶段二 `0. DDL 收口与迁移接管` 基本完成。

### 2.2 已完成：默认工作区显式入模

在本次会话中，已继续完成以下收口：

- 新增 `WorkspaceConstants`，统一承载当前阶段默认工作区 `default`
- 为 `KnowledgeBase`、`KnowledgeBaseSummary`、`Document`、`DocumentListFilter`、`DocumentListItem` 补充 `workspaceId`
- 为知识库、文档相关仓储端口补充工作区维度参数
- 为知识库与文档 JDBC 仓储 SQL 补充 `workspace_id` 入库、查询和状态更新条件
- 为文档列表、文档状态、文档分块预览、文档处理、删除、重处理、问答前置校验补充默认工作区作用域
- 保留兼容构造器/工厂方法，避免测试和旧调用点全部一次性重写

这部分解决了此前最关键的运行时缺口：

> `Flyway` 已把 `knowledge_bases.workspace_id`、`ingest_documents.workspace_id` 设为 `NOT NULL`，  
> 但应用层旧代码仍未显式传递工作区，导致创建和读写链路与新 schema 约束不一致。

## 3. 本次会话新增的关键代码点

### 3.1 新增工作区常量入口

- [WorkspaceConstants.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\shared\workspace\WorkspaceConstants.java)

作用：

- 统一默认工作区标识
- 避免在多个应用服务和仓储中散落 `"default"` 字面量

### 3.2 知识库链路已显式带工作区

核心文件：

- [KnowledgeBase.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\knowledge\domain\model\KnowledgeBase.java)
- [KnowledgeBaseRepository.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\knowledge\domain\port\KnowledgeBaseRepository.java)
- [JdbcKnowledgeBaseRepository.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\knowledge\infrastructure\persistence\JdbcKnowledgeBaseRepository.java)
- [CreateKnowledgeBaseApplicationService.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\knowledge\application\service\CreateKnowledgeBaseApplicationService.java)
- [UpdateKnowledgeBaseApplicationService.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\knowledge\application\service\UpdateKnowledgeBaseApplicationService.java)
- [ListKnowledgeBasesApplicationService.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\knowledge\application\service\ListKnowledgeBasesApplicationService.java)

当前策略：

- 运行时统一落在默认工作区
- 不提前引入认证上下文或请求头工作区解析
- 先保证与 Flyway 后的 schema 强约束一致

### 3.3 文档链路已显式带工作区

核心文件：

- [Document.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\domain\model\Document.java)
- [DocumentRepository.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\domain\port\DocumentRepository.java)
- [JdbcDocumentRepository.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\infrastructure\persistence\JdbcDocumentRepository.java)
- [JdbcDocumentListRepository.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\infrastructure\persistence\JdbcDocumentListRepository.java)
- [JdbcDocumentChunkPreviewRepository.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\ingest\infrastructure\persistence\JdbcDocumentChunkPreviewRepository.java)

已覆盖的业务入口：

- 上传受理
- 待处理任务抢占
- 文档处理成功/失败/重试
- 状态查询
- 分块预览
- 文档删除
- 文档重处理
- 文档列表查询

### 3.4 问答前置校验已收口到默认工作区

核心文件：

- [AskQuestionApplicationService.java](D:\Code\project\my-AI\src\main\java\io\github\spike\myai\qa\application\service\AskQuestionApplicationService.java)

当前仅完成：

- 知识库存在性与状态校验使用默认工作区维度查询

当前尚未完成：

- 真正的授权过滤
- 文档级 grant 裁剪
- `qa.ask` 召回后授权过滤与审计

## 4. 已完成验证

本次会话已执行并通过：

- 后端测试：`.\\mvnw.cmd -q test`

验证结论：

- 本次默认工作区显式化改造未破坏现有后端主链路
- `Flyway + schema-validation + IngestSchemaVerifier` 可以在当前工作区正常共存

## 5. 当前剩余缺口

当前分支虽然已经把“数据库强约束”和“运行时代码显式字段”对齐，但还不能视为阶段二整体完成。

### 5.1 仍未完成的数据库对象

按阶段二计划，以下表仍未落地：

- `knowledge_base_grants`
- `document_grants`
- `audit_events`

### 5.2 仍未完成的认证与授权基线

以下内容尚未开始或未成型：

- `Spring Security`
- `HttpSession + HttpOnly Cookie`
- 登录/登出/当前用户接口
- 登录锁定与解锁流程
- CSRF 写操作 Header 基线
- 成员关系驱动的授权判断

### 5.3 当前工作区策略的边界

本次实现是“默认工作区显式化”，不是“完整多工作区化”：

- 当前没有请求级工作区上下文解析
- 当前没有用户-工作区 membership 绑定
- 当前没有跨工作区路由和隔离能力
- 当前默认工作区仍是运行时常量，而不是认证态派生值

## 6. 建议如何审阅本次改动

建议按下面顺序审，不要按 `git diff` 全量逐文件扫：

### 6.1 第一批：领域模型与常量

- `shared/workspace`
- `KnowledgeBase`
- `Document`
- `DocumentListFilter`
- `DocumentListItem`

关注点：

- 是否接受“当前阶段用默认工作区显式化”的实现策略
- 是否接受兼容构造器保留方式

### 6.2 第二批：仓储端口与 SQL

- `KnowledgeBaseRepository`
- `DocumentRepository`
- `DocumentChunkPreviewRepository`
- `JdbcKnowledgeBaseRepository`
- `JdbcDocumentRepository`
- `JdbcDocumentListRepository`
- `JdbcDocumentChunkPreviewRepository`

关注点：

- SQL 是否全面补齐 `workspace_id`
- 是否只做了作用域收口，没有引入额外业务语义变化

### 6.3 第三批：应用服务联动

- `CreateKnowledgeBaseApplicationService`
- `AcceptUploadApplicationService`
- `DeleteDocumentApplicationService`
- `ProcessDocumentApplicationService`
- `AskQuestionApplicationService`

关注点：

- 是否只是把默认工作区传透
- 是否没有额外改变既有业务流程

### 6.4 第四批：测试

重点看：

- [JdbcKnowledgeBaseRepositoryTest.java](D:\Code\project\my-AI\src\test\java\io\github\spike\myai\knowledge\infrastructure\persistence\JdbcKnowledgeBaseRepositoryTest.java)
- [JdbcDocumentRepositoryTest.java](D:\Code\project\my-AI\src\test\java\io\github\spike\myai\ingest\infrastructure\persistence\JdbcDocumentRepositoryTest.java)
- [JdbcDocumentListRepositoryTest.java](D:\Code\project\my-AI\src\test\java\io\github\spike\myai\ingest\infrastructure\persistence\JdbcDocumentListRepositoryTest.java)
- [AcceptUploadApplicationServiceTest.java](D:\Code\project\my-AI\src\test\java\io\github\spike\myai\ingest\application\service\AcceptUploadApplicationServiceTest.java)
- [AskQuestionApplicationServiceTest.java](D:\Code\project\my-AI\src\test\java\io\github\spike\myai\qa\application\service\AskQuestionApplicationServiceTest.java)

## 7. 下一步建议

建议继续留在当前分支，把“阶段二第 1 步剩余数据库对象”收完，再考虑切到认证分支。

推荐顺序：

1. 补齐 `knowledge_base_grants`、`document_grants`、`audit_events` 的 Flyway 迁移
2. 明确这些表在当前阶段是否只落 schema，不接业务逻辑
3. 确认当前分支收口后，再切下一分支进入认证基线

如果当前分支确认收口，下一条分支建议命名：

- `feature/auth-security-baseline`

## 8. 一句话交接

**当前 `feature/auth-flyway-schema` 分支已经完成 Flyway 接管与默认工作区显式化收口，数据库约束与运行时代码边界已基本对齐；但认证、授权、grant 表与审计能力仍未开始，后续应继续在“阶段二第 1 步剩余 schema”或下一分支“认证基线”上推进。**
