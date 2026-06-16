# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目概览

my-AI 是一个基于 Spring Boot + Spring AI 的 RAG 文档入库与检索系统，支持文档上传、解析、向量化、知识库管理和智能问答。

- **技术栈**: Java 21, Spring Boot 3.5.8, Spring AI 1.1.2, PostgreSQL 16 + PGVector, Flyway, Apache Tika 2.9.2
- **前端**: React 19 + TypeScript + Vite 8 + Ant Design 6 + TanStack Query + Zod (独立工程 `web/`)
- **AI 模型**: 阿里云 DashScope (`qwen-plus` 聊天, `text-embedding-v4` 嵌入)
- **架构**: DDD-Lite 六边形架构，4 个子域：`auth` / `ingest` / `knowledge` / `qa`

## 关键文档入口

| 文档 | 路径 | 用途 |
|---|---|---|
| AI Agent 编码规则（162条） | `docs/project-context.md` | **实现代码前必须读取** — 所有不可违反的架构约束、命名约定、框架陷阱 |
| 架构设计总览 | `docs/architecture/overview.md` | 四层结构、子域详解、技术选型 |
| API 契约 | `docs/api/contracts.md` | 所有 REST 端点（37个）的请求/响应定义 |
| 源码结构 | `docs/guides/source-tree.md` | 完整目录树与文件注释 |
| 开发指南 | `docs/guides/development.md` | 环境变量、本地启动、测试 |
| 数据模型 | `docs/data/models.md` | 12 张表 + Flyway V1-V8 迁移历史 |
| README | `README.md` | 项目说明、快速开始、完整 API 摘要 |
| UI 设计系统 | `DESIGN.md` | 设计 Token、颜色、字体、组件规范 |

## 常用命令

### 后端

- 默认使用本机已安装的 `mvn`，不要默认使用项目内 Maven Wrapper（`./mvnw` / `mvnw.cmd`）。
- 如果本机 `mvn` 无法运行、版本异常或环境缺失，先告知用户并等待用户修复；不要自行切换到 `mvnw`、下载其他 Maven，或绕过问题继续开发。
- 只有用户明确要求验证 Maven Wrapper 时，才使用 `./mvnw`。
- **Maven 环境**:
  - Maven Home: `D:\02_Scoop\Scoop\apps\maven\current` (或你的实际路径)
  - 本地仓库: `D:\Administrator\.m2\repo` (或你的实际路径)
  - 配置文件: `D:\02_Scoop\Scoop\apps\maven\current\conf\settings.xml`
  - 如果命令失败，先检查 `mvn -version` 确认环境正常

```bash
# 编译
mvn clean compile

# 启动（端口 8080，Flyway 自动迁移）
mvn spring-boot:run

# 纯单元测试（无外部依赖，日常开发推荐）
mvn "-Dtest=!MyAiApplicationTests" test

# 完整测试（需要本地 PostgreSQL）
mvn test

# 构建
mvn clean package -DskipTests
```

### 前端 (`web/`)

```bash
cd web
npm install          # 安装依赖
npm run dev          # Vite 开发服务器，端口 55555，/api 代理到 localhost:8080
npm run build        # 生产构建（含 tsc 类型检查）
npm run lint         # ESLint 检查
npm run test:e2e     # Playwright E2E 测试（无头模式）
```

### 基础设施

```bash
cd infra && docker compose up -d   # 启动 PGVector 16 (5432) + RustFS S3 (9000/9001)
```

### 运行单个测试

```bash
mvn test "-Dtest=LoginApplicationServiceTest"                 # 单个测试类
mvn test "-Dtest=LoginApplicationServiceTest#testMethod"      # 单个测试方法
```

## 架构核心规则

### 六边形架构 (DDD-Lite)

```
✅ interfaces → application → domain ← infrastructure (port 实现)
❌ domain → infrastructure   (领域层不能依赖基础设施层)
❌ application → interfaces  (应用层不能依赖接口层)
```

| 层 | 职责 | 关键约束 |
|---|---|---|
| **interfaces** | REST 入口 (Controller + DTO) | 禁止业务逻辑，禁止注入 Repository/JdbcTemplate |
| **application** | 编排 + 授权 (Service + UseCase) | 只依赖 domain + port 接口，禁止直接引用 infrastructure |
| **domain** | 领域模型 + 端口接口 | **零框架注解** — 禁止 `@Service`、`@Autowired`、`jakarta.persistence.*` |
| **infrastructure** | 技术实现 (JDBC/配置/解析器) | 实现 domain 定义的 port 接口，adapter 间禁止互相引用 |

### 数据对象规范

- **所有数据对象使用 Java record**（虽然 pom.xml 中有 Lombok，但实际代码不使用）
- record 字段访问器是 `.userId()` 不是 `.getUserId()`
- record compact constructor 只做校验，不做默认值填充
- record 包含 List/Set 字段必须防御性拷贝：`Collections.unmodifiableList(new ArrayList<>(list))`

### 时间与依赖注入

- **禁止直接调用 `Instant.now()` / `LocalDateTime.now()`** — 必须通过注入的 `Clock` 获取时间
- Application Service 双构造器：public `@Autowired` 注入默认 `Clock.systemUTC()`，package-private 接受 `Clock` 参数供测试
- 禁止 `@Autowired` 字段注入，只用构造器注入

### 数据库与迁移

- **不使用 JPA/Hibernate** — 直接用 `JdbcTemplate` (Spring JDBC)
- SQL 用 Java text block（三引号字符串），PostgreSQL 方言
- **Schema 变更必须有对应 Flyway migration 文件** (`V{N}__{描述}.sql`)，不可修改已执行的 migration
- PGVector 维度硬编码在 migration 中，模型变更需独立 `ALTER TABLE`

### 异常映射

| 异常类型 | HTTP 状态码 |
|---|---|
| `{Entity}NotFoundException` | 404 |
| `{Entity}ConflictException` | 409 |
| `GovernanceAccessDeniedException` | 403 |
| `IllegalArgumentException` | 400 |
| `BadCredentialsException` | 401 |
| `LockedException` / `DisabledException` | 403 |

### TypeScript 关键约束

- **Zod-first**: API 响应类型先定义 Zod schema，再用 `z.infer<typeof schema>` 推导类型，禁止手写 interface
- **禁止 `enum` 关键字** — 用 `z.enum()` 或字符串字面量联合类型
- **`import type` 强制** (`verbatimModuleSyntax: true`)
- **禁止 `export default`** — 全部命名导出
- 所有 API 调用必须通过 React Query 的 `useQuery`/`useMutation`，禁止直接 `fetch`
- 100% 函数式组件，禁止 class 组件

### Git 提交

- 格式: `type(scope): message(中文分点描述)`，type 限 `feat/fix/refactor/test/docs/chore`
- scope: `auth`、`ingest`、`knowledge`、`qa`、`web`
- 一次提交只做一件事，依赖变更独立提交

### 文档语言约定

- Java: 中文 Javadoc，类级别含 `@author spike` + `@since 1.0.0`
- TypeScript: `//` 行内注释用中文，`/** */` JSDoc 用英文

## 子域快速索引

| 子域 | API 前缀 | 核心职责 | 关键入口类 |
|---|---|---|---|
| auth | `/api/v1/auth`, `/api/v1/admin/*` | 认证/三级授权/审计 | `SecurityConfig`, `LoginApplicationService` |
| ingest | `/api/v1/documents` | 文档上传→解析→分块→向量化→管理 | `DocumentIngestController`, `ProcessDocumentApplicationService` |
| knowledge | `/api/v1/knowledge-bases` | 知识库 CRUD + 统计 | `KnowledgeBaseController` |
| qa | `/api/v1/qa` | RAG 检索增强问答 | `AskQuestionApplicationService` |

## 测试规范

- JUnit 5 + Mockito，**纯单元测试**（不启动 Spring 上下文）
- 测试类与被测类同 package（package-private）
- `@Test` + `@DisplayName("中文业务描述")` — 必须含业务关键词
- 方法命名 `method_shouldExpectedBehavior_whenCondition`
- **禁止 mock JdbcTemplate/JDBC 链路** — SQL 正确性只能靠真实 DB 验证
- `MyAiApplicationTests` 是集成测试，需要完整 Spring 上下文 + 本地 PG

## 认证与安全

- Session Cookie 认证，CSRF 校验自定义 Header: `X-MYAI-CSRF: 1`
- 三级授权：工作区级 → 知识库级 → 文档级（DOC_DENY 最高优先级）
- 空库启动通过 `MYAI_AUTH_BOOTSTRAP_ADMIN_*` 环境变量引导首个管理员
- `/api/v1/**` 默认要求认证，POST `/login` 除外
