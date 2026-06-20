# 源码结构分析

## 顶层目录

```
my-AI/
├── src/                    # Java 后端源码（主项目）
├── web/                    # React 前端 SPA
├── infra/                  # 基础设施配置（Docker Compose）
├── docs/                   # 项目文档（本文件所在）
├── _bmad/                  # BMad Method 配置（.gitignore）
├── _bmad-output/           # BMad 工作流产物（.gitignore）
├── design-artifacts/       # 设计产物（WDS）
├── .claude/                # Claude Code 配置（.gitignore）
├── .agents/                # Agent 配置（.gitignore）
├── .github/                # GitHub 配置
├── .mvn/                   # Maven Wrapper 配置
├── .mcp.json               # MCP 服务器配置
├── target/                 # Maven 构建输出（.gitignore）
├── pom.xml                 # Maven 构建配置
├── mvnw / mvnw.cmd         # Maven Wrapper
├── README.md               # 项目说明
└── DESIGN.md               # UI 设计系统规范
```

## Java 源码结构

```
src/main/java/io/github/spike/myai/
├── MyAiApplication.java              # 入口类：@SpringBootApplication + @EnableScheduling + @ConfigurationPropertiesScan
│
├── auth/                             # Auth 子域（认证/授权/治理/审计）
│   ├── interfaces/rest/              # 6 个 REST Controller
│   │   ├── AuthController                 # POST /login, /logout; GET /me
│   │   ├── AccountAdminController         # 托管账号 CRUD（6 个端点）
│   │   ├── WorkspaceMemberAdminController # 成员管理 + 批量授权（6 个端点）
│   │   ├── DocumentGrantAdminController   # 文档授权（3 个端点）
│   │   ├── KnowledgeBaseGrantAdminController  # 知识库授权（3 个端点）
│   │   ├── AuditEventAdminController      # 审计事件查询（1 个端点）
│   │   └── dto/                        # 20 个 REST 请求/响应 DTO
│   │
│   ├── application/
│   │   ├── command/                    # 14 个命令对象（LoginCommand, CreateManagedAccountCommand, ...）
│   │   ├── context/                    # CurrentUser + CurrentUserProvider（认证上下文端口）
│   │   ├── exception/                  # 8 个业务异常
│   │   ├── query/                      # ListAuditEventsQuery
│   │   ├── result/                     # 8 个结果 DTO
│   │   ├── service/                    # 28 个应用服务 + 守卫 + 配置属性
│   │   └── usecase/                    # 20 个用例接口
│   │
│   ├── domain/
│   │   ├── model/                      # 14 个领域模型（LoginAccount, ManagedAccount, AuditEvent, 枚举...）
│   │   └── port/                       # 9 个出站端口接口
│   │
│   ├── infrastructure/
│   │   └── persistence/                # 9 个 JDBC 仓储（Jdbc* 前缀）
│   │
│   └── security/                       # 6 个安全组件
│       ├── SecurityConfig                   # Spring Security 过滤链
│       ├── CsrfHeaderFilter                 # 自定义 CSRF 防护
│       ├── JsonAuthenticationEntryPoint     # 401 JSON 响应
│       ├── JsonAccessDeniedHandler          # 403 JSON 响应
│       ├── MyAiPrincipal                    # 认证主体 record
│       └── SpringSecurityCurrentUserProvider # SecurityContext → CurrentUser 适配器
│
├── ingest/                           # Ingest 子域（文档入库生命周期）
│   ├── interfaces/rest/
│   │   ├── DocumentIngestController        # 10 个端点（上传/状态/版本/分块/重处理/删除）
│   │   ├── EmbeddingController             # 向量化调试端点
│   │   └── dto/                            # REST 请求/响应 DTO
│   │
│   ├── application/
│   │   ├── command/                    # 命令对象
│   │   ├── exception/                  # 业务异常
│   │   ├── monitoring/                 # IngestMetrics 等监控指标组件
│   │   ├── result/                     # 结果 DTO
│   │   └── service/                    # 11 个应用服务 + RetryPolicy + 审计
│   │
│   ├── domain/
│   │   ├── model/                      # Document（聚合根）+ 值对象 + UploadStatus 枚举
│   │   ├── port/                       # 10 个端口接口（Repository/Parser/Chunker/Storage/Vector）
│   │   └── exception/                  # 领域异常（DocumentSourceContentConflictException, DocumentVersionArtifactTooLargeException）
│   │
│   └── infrastructure/
│       ├── config/                     # IngestProperties + S3StorageConfiguration（2 个文件）
│       ├── parser/                     # Docling 解析 + HTML 清洗 + Markdown 转换（11 个文件）
│       ├── chunking/                   # 结构优先分块器（4 个文件）
│       ├── storage/                    # Local/S3 双实现（源文件 + 处理产物）（5 个文件）
│       ├── vector/                     # PGVector 向量索引（1 个文件）
│       ├── worker/                     # InProcessWorker（进程内轮询）（1 个文件）
│       ├── persistence/                # 6 个 JDBC 仓储 + Schema 校验器（6 个文件）
│       └── id/                         # UUID 文档 ID 生成器（1 个文件）
│
├── knowledge/                        # Knowledge 子域（知识库管理）
│   ├── interfaces/rest/
│   │   ├── KnowledgeBaseController         # 4 个端点（CRUD + 软删除）
│   │   └── dto/                            # REST 请求/响应 DTO
│   │
│   ├── application/
│   │   ├── command/                    # 命令对象（CreateKnowledgeBaseCommand, UpdateKnowledgeBaseCommand）
│   │   ├── exception/                  # 业务异常（KnowledgeBaseNotFoundException, KnowledgeBaseInactiveException）
│   │   ├── result/                     # 结果 DTO（KnowledgeBaseResult）
│   │   ├── usecase/                    # 用例接口（Create/Update/Delete/List KnowledgeBaseUseCase）
│   │   └── service/                    # 4 个应用服务
│   │
│   ├── domain/
│   │   ├── model/                      # KnowledgeBase 聚合根 + 枚举
│   │   └── port/                       # Repository + IdGenerator
│   │
│   └── infrastructure/
│       ├── id/                         # UuidKnowledgeBaseIdGenerator
│       └── persistence/                # 2 个 JDBC 仓储
│
├── qa/                               # QA 子域（RAG 问答）
│   ├── interfaces/rest/
│   │   ├── QaController                    # 1 个端点（POST /ask）
│   │   └── dto/                            # REST 请求/响应 DTO（AskRequest, AskResponse 等）
│   │
│   ├── application/
│   │   ├── command/                    # AskQuestionCommand
│   │   ├── usecase/                    # AskQuestionUseCase 接口
│   │   ├── result/                     # AskQuestionResult + AskReferenceResult + AskStaleReferenceSummaryResult
│   │   └── service/                    # AskQuestionApplicationService（RAG 管线）
│   │
│   ├── domain/
│   │   ├── model/                      # RetrievedChunk + AskableDocumentVersion
│   │   └── port/                       # 3 个端口（检索/生成/版本查询）
│   │
│   └── infrastructure/
│       ├── retrieval/                  # PgVectorChunkRetrievalAdapter + JdbcAskableDocumentVersionAdapter
│       └── generation/                 # ChatModelAnswerGenerationAdapter
│
└── shared/                           # 共享基础设施
    ├── rest/                         # GlobalRestExceptionHandler + ErrorResponse + BusinessException
    └── workspace/                    # WorkspaceConstants（DEFAULT_WORKSPACE_ID）
```

## 资源文件

```
src/main/resources/
├── application.yaml              # 应用配置
└── db/migration/                 # Flyway 迁移脚本
    ├── V1__auth_flyway_schema.sql                    # 认证表（workspaces/users/credentials/memberships/locks）
    ├── V2__add_authorization_grants_and_audit_tables.sql  # 授权表 + 审计表
    ├── V3__align_grants_with_workspace_memberships.sql    # 外键对齐
    ├── V4__add_processing_metadata_to_ingest_documents.sql # processing_metadata 字段
    ├── V5__add_document_version_chain_foundation.sql       # 版本链 + latest projection
    ├── V6__add_document_version_fact_lookup_indexes.sql    # 索引优化
    ├── V7__extract_latest_projection_maintenance_functions.sql  # 数据库函数
    └── V8__add_deleted_knowledge_base_status.sql           # 知识库 DELETED 状态
```

## 测试结构

```
src/test/java/io/github/spike/myai/
├── auth/application/service/     # 22 个 Service 层纯单测
├── auth/interfaces/rest/         # 6 个 Controller 测试
├── auth/infrastructure/          # 6 个 Repository 测试
├── auth/security/                # 1 个安全测试
├── ingest/application/service/   # 11 个 Service 层纯单测
├── ingest/domain/model/          # 4 个领域模型测试
├── ingest/infrastructure/        # 13 个基础设施测试（parser/storage/vector/worker/persistence）
├── ingest/interfaces/rest/       # 1 个 Controller 测试
├── knowledge/application/service/  # 4 个 Service 层纯单测
├── knowledge/infrastructure/     # 2 个 Repository 测试
├── knowledge/interfaces/rest/    # 1 个 Controller 测试
├── qa/application/               # 2 个测试
├── qa/infrastructure/            # 3 个测试
└── qa/interfaces/rest/           # 1 个 Controller 测试
```

## 基础设施文件

```
infra/
└── docker-compose.yml            # PostgreSQL (pgvector/pgvector:pg16) + RustFS (S3 兼容) + Docling Serve (文档解析)
```

## Web 前端结构

```
web/src/
├── main.tsx                      # 应用引导：QueryClient, BrowserRouter, Antd ConfigProvider, AuthProvider
├── app/
│   ├── AppRoutes.tsx             # 路由定义（懒加载），基于 capability 的守卫
│   └── ConsoleLayout.tsx         # Shell 布局：可调侧边栏、头部、面包屑、活动中心
├── features/                     # 功能模块（按后端子域划分）
│   ├── admin/                    # 管理后台（账户、成员、授权、审计）
│   ├── auth/                     # 登录页
│   ├── ingest/                   # 文档管理（列表、上传、详情、版本、分块预览）
│   ├── knowledge/                # 知识库管理（卡片布局、CRUD）
│   ├── member/                   # 成员视图（可读基线、阅读器）
│   └── qa/                       # RAG 问答（对话界面 + 检查面板）
└── shared/
    ├── api/                      # API 客户端层（5 个模块：auth/ingest/knowledge/qa/admin）
    ├── auth/                     # AuthContext + RouteGuards（3 级守卫）
    └── ui/                       # 共享 UI 组件（ApiErrorAlert, ConsolePageFrame 等）
```

---

_最后更新: 2026-06-19 | 扫描模式: 深度扫描 | 变更: Docling 迁移 + 前端结构补充_
