# 架构设计文档

## 架构模式：DDD-Lite（六边形架构简化版）

核心思想：**领域层（domain）是系统的"心脏"**，不依赖任何外部技术。基础设施层（infrastructure）通过实现 domain 定义的 port 接口来"插入"系统。

```
✅ interfaces → application → domain ← infrastructure (port 实现)
❌ domain → infrastructure   (领域层不能依赖基础设施层)
❌ application → interfaces  (应用层不能依赖接口层)
```

## 四层结构

| 层 | 职责 | 允许内容 | 禁止内容 |
|---|---|---|---|
| **interfaces** | REST 入口 | Controller + DTO | 业务逻辑 |
| **application** | 编排 + 授权 | Service + UseCase + Command/Result | Spring 注解在 UseCase 上 |
| **domain** | 领域模型 + 端口 | Model + Port 接口 | Spring 注解、基础设施依赖 |
| **infrastructure** | 技术实现 | Jdbc 实现 + 配置类 | 绕过 port 直接调用 |

## 子域架构

### Auth 子域

**职责**：认证、授权、治理、审计

```
auth/
├── interfaces/rest/          # 6 个 Controller
│   ├── AuthController             # 登录/登出/me
│   ├── AccountAdminController     # 托管账号 CRUD
│   ├── WorkspaceMemberAdminController  # 成员管理 + 批量授权
│   ├── DocumentGrantAdminController    # 文档级授权
│   ├── KnowledgeBaseGrantAdminController  # 知识库级授权
│   └── AuditEventAdminController   # 审计事件查询
├── application/service/      # 28 个应用服务
├── domain/model/             # 14 个领域模型
├── domain/port/              # 9 个出站端口
├── infrastructure/persistence/  # 9 个 JDBC 仓储
└── security/                 # 7 个安全组件
```

**核心机制**：
- **认证**：Session + BCrypt 密码哈希，登录失败锁定
- **三级授权模型**：工作区级（OWNER/ADMIN 直接放行）→ 文档级（DOC_DENY 最高优先级）→ 知识库级（KB_MANAGER > CONTRIBUTOR > READER > ASKER）
- **治理边界**：OWNER 不可触碰、ADMIN 互不可管、最小提权
- **CSRF**：自定义 `X-MYAI-CSRF: 1` Header 校验
- **审计**：append-only，独立事务（REQUIRES_NEW）

**安全组件明细（7 个）**：

| 组件 | 类型 | 职责 |
|---|---|---|
| `SecurityConfig` | @Configuration | 核心过滤链：禁用 CSRF/formLogin/httpBasic/logout，SessionCreationPolicy.IF_REQUIRED，HttpSessionSecurityContextRepository，POST /login 与 actuator 端点 permitAll |
| `CsrfHeaderFilter` | OncePerRequestFilter | 对 /api/v1/ 下非安全方法（非 GET/HEAD/OPTIONS/TRACE）校验 `X-MYAI-CSRF: 1` 请求头 |
| `JsonAuthenticationEntryPoint` | AuthenticationEntryPoint | 未认证请求返回 401 JSON：`{"code":"UNAUTHORIZED","message":"authentication is required"}` |
| `JsonAccessDeniedHandler` | AccessDeniedHandler | 已认证但权限不足返回 403 JSON：`{"code":"FORBIDDEN","message":"access is denied"}` |
| `SecurityConstants` | 常量类 | 定义 CSRF_HEADER_NAME="X-MYAI-CSRF"、CSRF_HEADER_VALUE="1" |
| `MyAiPrincipal` | Serializable record | Session 持久化主体：(userId, username, displayName, workspaceId, workspaceRole) |
| `SpringSecurityCurrentUserProvider` | Anti-Corruption Layer 适配器 | 从 SecurityContext 提取 MyAiPrincipal → 映射为应用层 CurrentUser |

### Ingest 子域

**职责**：文档资产入库生命周期

```
ingest/
├── interfaces/rest/          # DocumentIngestController + EmbeddingController
├── application/service/      # 11 个应用服务 + RetryPolicy
├── domain/model/             # 聚合根 Document + 值对象
├── domain/port/              # 10 个端口接口
├── infrastructure/
│   ├── parser/               # 11 个文件
│   ├── chunking/             # 4 个文件
│   ├── storage/              # 5 个文件（Source / Artifact 双存储接口）
│   ├── vector/               # PGVector 向量索引
│   ├── worker/               # 进程内轮询 Worker
│   ├── persistence/          # 6 个 JDBC 仓储 + Schema 校验
│   ├── id/                   # UUID 文档 ID 生成器
│   └── config/               # 配置属性
```

**基础设施详细说明**：

| 子目录 | 文件 | 职责 |
|---|---|---|
| `parser/` | DocumentParserRouter | 路由策略：8 种支持格式 → DOCLING，不支持格式 → REJECT (415) |
| | DoclingDocumentParser | Docling Serve 转换 adapter（convertSource 纯转换 → Markdown） |
| | MarkdownTextCleaner | Markdown 文本清洗 |
| | MarkdownStructureRepairer | Markdown 结构修复 |
| | TextCleaningService | Markdown 最小破坏清洗 facade |
| `chunking/` | DoclingDocumentChunker | Docling Serve 分块 adapter（HybridChunker → List&lt;DocumentChunk&gt;） |
| `storage/` | DocumentDocumentStorageKeyResolver | 路由逻辑：Source 文件 vs 处理产物使用 SEPARATE 存储接口 |
| | LocalDocumentSourceStorage | Source 文件本地存储 |
| | S3DocumentSourceStorage | Source 文件 S3 存储 |
| | LocalDocumentProcessingArtifactStorage | 处理产物本地存储（cleaned.md、raw.xhtml 等） |
| | S3DocumentProcessingArtifactStorage | 处理产物 S3 存储 |
| `vector/` | PgVectorDocumentVectorIndexer | 确定性 chunkId（UUID 由 docId+index+version 生成），Spring AI VectorStore 批量写入，JdbcTemplate SQL 删除（JSONB 元数据过滤） |
| `worker/` | InProcessWorker | @Scheduled fixedDelay=5000ms，claim + process 循环 |
| `persistence/` | JdbcDocumentRepository | 文档聚合根 JDBC 仓储 |
| | JdbcDocumentListRepository | 文档列表查询 |
| | JdbcDocumentVersionHistoryRepository | 版本历史查询 |
| | JdbcDocumentChunkPreviewRepository | 分块预览查询 |
| | IngestSchemaVerifier | 启动时 Schema 校验 |
| | UuidDocumentIdGenerator | 文档 ID 生成器 |
| `id/` | UuidDocumentIdGenerator | UUID 文档 ID 生成器（基础设施层实现） |

**文档处理流水线**：
```
上传 → 受理(幂等去重) → Worker 抢占(CAS) → 解析(Docling) → 分块(结构优先)
     → 向量化(PGVector) → 状态收口(INDEXED/FAILED)
```

**状态机**：
```
UPLOADED → INGESTING → INDEXED（成功）
                    → FAILED（致命错误）
                    → UPLOADED（瞬时错误，指数退避重试）
                    → DELETING → DELETED（删除）
```

**关键设计**：
- 单进程 Worker + CAS 抢占（V1 权衡，非最佳实践）
- 结构优先分块（Markdown 标题分段 + 滑动窗口兜底）
- 版本链（上传新版本 / 版本回退 / 重处理）

### Knowledge 子域

**职责**：知识库主数据管理

```
knowledge/
├── interfaces/rest/          # KnowledgeBaseController
├── application/service/      # 4 个应用服务
├── domain/model/             # KnowledgeBase 聚合根 + 枚举
├── domain/port/              # KnowledgeBaseRepository + IdGenerator
└── infrastructure/persistence/  # JDBC 仓储
```

**关键设计**：
- 知识库三态：ACTIVE → INACTIVE → DELETED
- 列表查询实时统计已索引文档数（LEFT JOIN ingest_documents）
- 普通成员仅可见被授权的知识库

### QA 子域

**职责**：检索与回答生成（RAG 管线）

```
qa/
├── interfaces/rest/          # QaController
├── application/service/      # AskQuestionApplicationService
├── domain/model/             # RetrievedChunk + AskableDocumentVersion
└── domain/port/              # ChunkRetrievalPort + AnswerGenerationPort + AskableDocumentVersionPort
```

**RAG 管线流程**：
```
1.  输入规范化（trim + 默认值）
2.  知识库校验（存在 + ACTIVE）
3.  授权校验（三级授权模型）
3.5 可召回版本范围为空保护（若 askableVersionScope 为空 → 立即返回兜底文案，跳过检索）
4.  版本范围确定（查询用户可召回的文档版本）
5.  语义检索（PGVector 余弦相似度，放大系数 4x）
6.  空结果保护（兜底文案，避免幻觉）
7.  提示词构造（参考片段模板）
8.  LLM 回答生成（DashScope qwen-plus）
9.  引用组装 + 陈旧引用检测
9.5 生成结果空值保护（若 generateAnswer 返回 null/空白 → 使用兜底文案）
```

**引用组装细节**：
- `toReferenceResult()` 合并 chunk + AskableDocumentVersion 信息：版本号（version number）、源文件更新时间（sourceUpdatedAt）、是否最新版本（isLatestVersion）、最新版本号（latestVersionNumber）、源文件名（sourceFilename）
- 陈旧引用检测：`AskStaleReferenceSummaryResult` 结构包含 hasStaleReferences（是否存在陈旧引用）、staleReferenceCount（陈旧引用数量）、staleDocumentCount（陈旧文档数量）、documents（陈旧文档列表）

**关键常量**：检索候选下限 20、放大倍率 4x、引用预览 200 字符

## 跨子域关联

```
ingest_documents.kb_id ──→ knowledge_bases.kb_id
knowledge_base_grants.user_id ──→ workspace_memberships(user_id)
document_grants.user_id ──→ workspace_memberships(user_id)
vector_store ←→ ingest_documents（通过元数据 documentId 关联）
```

## 共享基础设施

| 组件 | 职责 |
|---|---|
| `ErrorResponse` | DTO record，包含 code + message 字段，所有错误处理器统一使用 |
| `GlobalRestExceptionHandler` | 统一 JSON 错误响应：处理 ResponseStatusException（Controller 层 HTTP 错误）、BusinessException（稳定业务异常）、AccessDeniedException（其中 GovernanceAccessDeniedException 使用 reasonCode 区分治理拒绝原因） |
| `BusinessException` | 业务异常基类，携带稳定 code + HttpStatus；与 ResponseStatusException 区别：BusinessException 用于应用/领域层业务规则校验，ResponseStatusException 用于接口层直接抛 HTTP 错误 |
| `ResponseStatusException` | Controller 层 HTTP 错误（Spring 内置），携带 HttpStatus + reason |
| `WorkspaceConstants` | 单工作区模式常量 `DEFAULT_WORKSPACE_ID = "default"` |
| `SecurityConfig` | Spring Security 过滤链配置 |
| `CsrfHeaderFilter` | 自定义 CSRF 防护 |

## @ConfigurationProperties 配置属性

| 配置类 | 属性前缀 | 职责 |
|---|---|---|
| `IngestProperties` | `myai.ingest.*` | Ingest 子域配置，6 个嵌套配置域：Parser（解析器配置）、Storage（存储配置，含 S3 子配置和 Artifacts 子配置）、Chunk（分块配置）、Worker（Worker 轮询配置）、SchemaCheck（Schema 校验配置） |
| `AuthBootstrapAdminProperties` | `myai.auth.bootstrap-admin.*` | 初始管理员账号配置，从环境变量注入 |
| `AuthSecurityProperties` | `myai.auth.security.*` | 安全策略配置：maxFailedAttempts=5（最大登录失败次数）、lockDuration=1min（锁定持续时间） |
| `S3StorageConfiguration` | @ConditionalOnProperty(havingValue="s3") | 当存储类型为 S3 时激活，创建 S3Client Bean |

## 技术选型背景

| 选型 | 为什么选 | 替代方案 |
|---|---|---|
| PostgreSQL + PGVector | 一个数据库同时满足业务存储和向量检索 | Milvus/Pinecone（专用向量库） |
| DashScope | 阿里云百炼，国内网络可达、中文优化 | OpenAI、Anthropic |
| Docling Serve | IBM 开源文档解析，支持表格/图片/公式结构化提取，Docker 部署 | Apache Tika（已迁移）、Unstructured.io |
| Flyway | Java 原生、SQL 文件直观 | Liquibase |
| Session 认证 | V1 最简方案，无需 JWT 刷新机制 | JWT + Refresh Token |

---

_最后更新: 2026-06-19 | 扫描模式: 深度扫描 | 变更: Tika→Docling 技术选型更新_
