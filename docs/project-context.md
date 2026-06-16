---
project_name: 'my-AI-bmad-method'
user_name: 'spike'
date: '2026-06-15'
sections_completed: ['technology_stack', 'language_rules', 'framework_rules', 'workflow_rules', 'critical_rules']
status: 'complete'
rule_count: 162
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

### Backend

- Java 21 + Spring Boot 3.5.8
- Spring AI 1.1.2 + Spring AI Alibaba 1.1.2.0 (DashScope)
- PostgreSQL 16 (PGVector) — 1024维向量，HNSW索引，余弦距离
- Flyway — 数据库版本迁移
- JdbcTemplate 直接 JDBC（无 JPA/Hibernate）
- Apache Tika 2.9.2 — 文档解析
- AWS SDK 2.42.14 — S3 兼容存储 (RustFS)
- Lombok 存在于 pom.xml 但实际代码未使用（全部用 Java record）

### Frontend

- React 19.2.4 + TypeScript ~6.0.2 (target: ES2023)
- Vite 8.0.4 — dev 端口 55555，`/api` 代理到 `localhost:8080`
- Ant Design 6.3.5 — 主 UI 框架
- TanStack React Query 5.96.2 — 服务端状态管理
- React Router 7.14.0 — 嵌套路由 + 懒加载
- Zod 4.3.6 — 运行时类型校验，schema-first 类型推导
- Playwright 1.56.1 — E2E 测试（Chromium only，顺序执行）

### Infrastructure

- Docker Compose: PGVector 16 + RustFS (S3兼容)
- AI 模型: DashScope `qwen-plus`（聊天）+ `text-embedding-v4`（嵌入）
- tsconfig 关键标志: `erasableSyntaxOnly: true`, `verbatimModuleSyntax: true`

---

## Critical Implementation Rules

### Java — 六边形架构约束

- **domain 层零框架注解**：禁止 `@Service`、`@Component`、`@Autowired`、`jakarta.persistence.*`、`org.springframework.*`。domain 只依赖 Java 标准库
- **application 层只依赖 domain + port 接口**，禁止直接 import infrastructure 类
- **adapter 层禁止互相引用**：RustFS adapter 不直接调用 PostgreSQL adapter，通信必须经过 application 层
- **Port 接口定义在 domain/port/ 或 application/usecase/**，在 infrastructure 层实现。禁止在 adapter 层定义 port
- **Controller 禁止注入 Repository 或 JdbcTemplate**，只能依赖 application service
- **DTO 转换只发生在 adapter（Controller）层**，domain 层不定义 DTO
- **Application Service 返回 domain 对象或 Result record**，不返回 DTO

### Java — Record 与数据对象

- 所有数据对象使用 Java record（非 Lombok）。访问器是 `.userId()` 不是 `.getUserId()`
- record 字段名与数据库列名对应（snake_case 列 → camelCase 字段）
- 嵌套 record 不超过 2 层，超过则拆分为独立值对象
- record 的 compact constructor 用于校验，抛 `IllegalArgumentException`
- record 包含 List/Set 字段时，防御性拷贝：`Collections.unmodifiableList(new ArrayList<>(list))`
- 可变配置对象（如 `AuthSecurityProperties`）是唯一使用普通 class + setter 的例外

### Java — 构造器与依赖注入

- Application Service 双构造器：public `@Autowired` 构造器注入默认 `Clock.systemUTC()`，package-private 构造器接受 `Clock` 参数供测试注入
- Controller 构造器无 `@Autowired`（Spring 单构造器自动注入）
- Repository 构造器无注解，接受 `JdbcTemplate`
- 所有字段 `private final`

### Java — 时间与时钟

- **禁止直接调用 `Instant.now()` / `LocalDateTime.now()`**，必须通过注入的 Clock 获取时间
- 测试中用 `Clock.fixed(NOW, ZoneOffset.UTC)` 注入，确保时间可确定

### Java — JdbcTemplate 模板

- SQL 用 Java text block（三引号字符串），PostgreSQL 方言
- `RowMapper` 写成 lambda 或私有方法，禁止匿名内部类
- `NamedParameterJdbcTemplate` 与 `?` 占位符二选一，同一文件内禁止混用
- 批量操作用 `batchUpdate`，不要循环里单条 update

### Java — 异常处理映射

| 异常类型                                | HTTP 状态码 |
| --------------------------------------- | ----------- |
| `{Entity}NotFoundException`             | 404         |
| `{Entity}ConflictException`             | 409         |
| `GovernanceAccessDeniedException`       | 403         |
| `IllegalArgumentException`              | 400         |
| `BadCredentialsException`               | 401         |
| `LockedException` / `DisabledException` | 403         |

Controller 层 try/catch 翻译为 `ResponseStatusException`，null 请求体显式检查。

### TypeScript — 类型系统

- **Zod-first**：API 响应类型必须先定义 Zod schema，再用 `z.infer<typeof schema>` 推导类型。禁止手写 interface 替代
- **禁止 `enum` 关键字** — 用 `z.enum()` 或字符串字面量联合类型
- **`import type` 强制用于纯类型导入**（`verbatimModuleSyntax: true` 要求）
- **禁止 `export default`** — 全部命名导出 `export function`

### TypeScript — 组件规范

- 100% 函数式组件，禁止 class 组件
- Props 解构在函数签名处，不在函数体内 `props.xxx`
- Props 接口命名 `{ComponentName}Props`
- 组件文件 PascalCase.tsx，非组件文件 camelCase.ts
- 自定义 Hook 文件命名 `useXxx.ts`（无额外后缀），必须有返回类型标注
- 只有返回 JSX 的函数才用 PascalCase，工具函数一律 camelCase

### TypeScript — React Query

- 所有 API 调用必须通过 React Query 的 `useQuery` / `useMutation`
- **禁止在组件内直接 `fetch`**，绕过 React Query 会破坏缓存管理
- Query Key 是层级数组：`["admin", "members"]`，首元素是实体名
- Mutation 成功后用 `queryClient.invalidateQueries()` 刷新缓存，**禁止 `window.location.reload()`**
- 页面组件懒加载：`React.lazy()` + `.then((m) => ({ default: m.ComponentName }))`

### TypeScript — 错误处理

- API 层统一处理 4xx/5xx，抛 `ApiError`（含 status、message、body）
- 组件层用 `<ApiErrorAlert>` 展示错误
- 401 自动重定向到 `/login?redirect=...`
- auth 端点使用 `authPolicy: "ignore-401"` 由调用方自行处理

### TypeScript — 导入顺序

1. React 核心
2. 第三方库（@tanstack/react-query、react-router-dom、antd）
3. 图标（@ant-design/icons，逐个命名导入）
4. 本地 shared 模块
5. 本地 feature 模块

### Java — 文档与注释（遵循阿里巴巴规范）

**总则：**

- 全部使用中文 Javadoc / 注释，英文仅用于 `@param`、`@return`、`@throws` 标签中的类型名或技术术语
- Javadoc 必须说"为什么"和"做了什么"，禁止空洞噪音注释（如 `// 获取用户信息`、`// 设置名称`）
- 禁止注释掉的代码（要么删掉，要么 `// TODO(spike): 恢复于...` 标明意图）
- TODO 格式：`// TODO(spike): 描述`
- 区块分隔统一格式：`// === Section Name ===`

**类 / 接口 / 枚举级别（必写）：**

- 使用 `/** */` 块注释，必须包含：职责描述、设计意图或关键约束
- 必须包含 `@author spike` + `@since 1.0.0`
- 如有使用注意事项、线程安全性说明，用 `<p>` 标签追加
- 格式示例：
  ```java
  /**
   * 用户登录应用服务，编排认证领域逻辑。
   *
   * <p>负责接收登录命令、协调账户查询与密码校验，
   * 是认证子域的核心用例入口。
   *
   * @author spike
   * @since 1.0.0
   */
  ```

**构造器级别：**

- 公共构造器：说明参数约束和初始化逻辑
- 私有 / 包级私有构造器：说明为何限制可见性（如"禁止实例化"、"仅供工厂方法使用"）

**方法级别（必写）：**

- 公共方法和受保护方法必须有 Javadoc
- 必须包含：方法用途（一句话概述）+ 业务上下文说明（`<p>` 标签）
- 必须标注 `@param`（每个参数）、`@return`（非 void）、`@throws`（所有受检异常及关键运行时异常）
- 方法级别不加 `@author`
- 格式示例：
  ```java
  /**
   * 执行用户登录认证。
   *
   * <p>依次完成参数校验、账户查询、状态检查、密码比对四阶段，
   * 任一阶段失败立即终止并抛出对应异常。
   *
   * @param command 登录命令，包含用户名和明文密码
   * @return 认证成功的当前用户信息
   * @throws IllegalArgumentException 用户名或密码为空
   * @throws BadCredentialsException 凭证无效（密码错误或用户不存在）
   * @throws LockedException 账户已被锁定
   */
  ```
- package-private 方法：至少一行行内注释说明用途

**字段级别：**

- 依赖注入的字段（`private final`）：必须有 `/** */` 单行注释，说明该依赖的职责归属
  ```java
  /** 本地账户仓储（出站端口），由基础设施层实现 */
  private final LocalAccountRepository localAccountRepository;
  ```
- 常量字段：说明业务含义和取值依据（如"单位：秒"、"来源：配置文件 xxx"）
- 普通字段：非显而易见的字段需加注释

**行内注释：**

- 复杂业务逻辑的关键节点必须有行内注释，解释决策原因
- 阶段性流程用区块分隔标注：
  ```java
  // ---------- 第一阶段：参数校验 ----------
  // 命令对象为 null 或用户名/密码为空，直接拒绝
  ```
- 一行代码的注释放在代码上方（而非行尾），除非是简单的类型说明

### TypeScript — 注释与风格

- `//` 行内注释用中文，`/** */` JSDoc 用英文（TypeScript 工具链默认英文）
- 禁止注释掉的代码（要么删掉，要么 `// TODO: 恢复于...` 标明意图）
- CSS class 命名：kebab-case + `console-` 前缀，BEM 修饰符用 `--`

### 命名约定（双端通用）

**Java 方法动词映射：**

- `find*` — 有则返回，无则 `Optional.empty()`
- `get*` — 有则返回，无则抛异常
- `create*` / `update*` / `delete*` — CUD 操作
- 布尔方法用 `is*` 或 `has*`

**类命名规则：**

- Service: `{Verb}{Noun}ApplicationService`
- Repository: `Jdbc{EntityName}Repository`
- Port: `{EntityName}Repository`
- Command: `{Verb}{Noun}Command`
- Result: `{Noun}Result`
- Controller: `{Domain}Controller` 或 `{Domain}AdminController`

**TypeScript：**

- API 模块 `{domain}Api.ts`，Zod schema 文件 `{domain}Api.ts` 内联定义
- 常量 UPPER_SNAKE_CASE，变量/函数 camelCase

### 测试规则

**Java 测试：**

- JUnit 5 + Mockito，**无 Spring 上下文**的纯单元测试
- 测试类 package-private class，与被测类同 package
- `@Test` + `@DisplayName("中文描述")` — 描述必须含业务关键词，禁止"返回200"
- 方法命名 `method_shouldExpectedBehavior_whenCondition`
- 测试方法只断言一个行为，禁止一个 test 验证多个场景
- Mockito mock port 接口，`ArgumentCaptor` 验证参数
- 每个 Domain Exception → HTTP 状态码映射必须有测试用例
- **禁止 mock JdbcTemplate/JDBC 链路**（SQL 正确性只能靠真实数据库验证）

**Playwright E2E：**

- Chromium only，顺序执行（`fullyParallel: false`, `workers: 1`）
- 测试 ID 属性：`data-testid`
- E2E 测试每个 case 前重置数据到已知状态，禁止依赖前一个 case 的副作用

### 代码质量与结构

- 单个方法不超过 30 行
- 字符串字面量禁止散落在代码中，提取为常量（Java `static final String`，TS `const`）
- Java 类成员排序：静态字段 → 实例字段 → 构造器 → 公有方法 → 私有方法
- 每个 `.java` 文件只能有一个 public 类
- 新文件必须归入正确的 layer 目录：entity → domain，port → application，adapter → infrastructure，DTO → interfaces
- 禁止在功能实现中"顺手重构" scope 外的代码，重构必须有独立 story

### Spring Boot — 框架陷阱

- **`ResponseStatusException` 的 message 禁止暴露内部细节**（stack trace、SQL、内部路径），只放用户可读的简短描述
- **所有 `@ConfigurationProperties` 类必须带 `@Validated`** + JSR-303 注解，没有校验的配置绑定启动不报错但运行时 NPE
- **`@Transactional` 只在 Application Service 层使用**，禁止在 Controller 和 Repository 层。同类内部调用不走代理，`@Transactional` 无效
- **Flyway 迁移中 PGVector 维度必须硬编码**，embedding 模型维度变更需要独立的 `ALTER TABLE` migration
- **Cookie session 的 `SameSite`、`HttpOnly`、`Secure` 必须在 `application.yml` 中显式配置**
- **Bootstrap admin 初始化必须确认 Flyway 已完成**（用 `@DependsOn("flyway")` 或 Flyway callback）

### React Query — 缓存策略

- **QueryKey 定义集中在 `shared/api/` 层**，组件只调 hook，不允许在组件里直接写 `useQuery` 的 key
- **QueryKey 必须包含所有影响结果的参数**（筛选、分页等），格式统一为 `['resource', { page, filters }]`
- **`useSearchParams` 进入 queryKey 前必须序列化为稳定值**（`Object.fromEntries`），URLSearchParams 对象每次渲染都是新引用
- **Mutation 成功后 `invalidateQueries` 的 key 粒度要精确**：list key 带 `'list'`，detail key 带 `'detail'`，避免 invalidate `['articles']` 刷掉所有子查询
- **登出顺序不可逆**：先 `queryClient.clear()` 再清 AuthContext 状态，否则 stale 缓存会在登出瞬间发出带旧 session 的请求

### Ant Design — 组件使用规范

- **Form 禁止混用 `useState` 管理同一字段**：Ant Design Form 内部有状态管理（`form.setFieldsValue`），与 React 受控模式冲突
- **Table `dataSource` 引用必须用 `useMemo` 稳定化**：每次 render 做 `.map()` 转换会导致引用变化，触发全量重渲染
- **`notification` vs `message`**：需要用户阅读并行动的系统通知用 `notification`，轻量反馈用 `message`
- **`Drawer` vs `Modal`**：信息量大、需参照背景内容用 Drawer；需聚焦决策用 Modal
- **`Tooltip` vs `Popover`**：纯文字提示用 Tooltip，需要交互的用 Popover，禁止在 Tooltip 里放按钮
- **Portal 组件必须配置 `getPopupContainer`**：确保 Modal/Select/DatePicker 的弹层挂在 ConfigProvider 范围内，否则主题丢失

### 状态驱动的 UI 模式

- **加载态**：列表页用 `<Skeleton>` 替代全页 `<Spin>`，保留页面结构让用户感知"内容马上来"
- **按钮 loading 态**：所有异步操作触发按钮加 `loading` 属性，防止重复提交
- **空状态**：列表页/搜索结果/通知中心必须有 `<Empty>` + 行动引导文案，不是白屏或"暂无数据"四个字
- **操作确认**：删除、批量操作、不可逆操作必须有 `Modal.confirm`，危险操作用 `danger` 类型按钮
- **搜索防抖**：搜索输入 `onChange` 必须防抖 300-500ms，按钮点击防重复提交
- **键盘交互**：Enter 提交表单，Escape 关闭弹窗（Ant Design Modal 默认支持，自定义弹窗需手动处理）

### 开发工作流 — 构建与启动

**后端命令（始终用 `./mvnw`，不用 `mvn`）：**

- `./mvnw clean compile` — 编译
- `./mvnw spring-boot:run` — 本地启动，端口 8080，Flyway 自动 migrate
- `./mvnw test` — 仅单测，**无外部依赖**
- `./mvnw verify` — 含集成测试（需 PostgreSQL）

**前端命令：**

- `npm run dev` — Vite dev server，端口 55555，`/api` 代理到 `localhost:8080`
- `npm run build` — 生产构建（含 `tsc -b` 类型检查）
- `npm run test:e2e` — Playwright E2E（需前后端都跑起来）

**本地基础设施：**

- `cd infra && docker compose up -d` — 启动 PGVector 16 (5432) + RustFS (9000/9001)
- Docker image 必须用 `pgvector/pgvector:pg16`，不是官方 postgres（PGVector 扩展预装）
- RustFS 本地 S3 endpoint: `http://localhost:9000`，credentials: `admin/Admin@123`

### 开发工作流 — Git 与提交

- 提交格式：`type(scope): message(分点描述，中文)`，type 限 `feat/fix/refactor/test/docs/chore`
- scope 对应模块名：`auth`、`ingest`、`knowledge`、`qa`、`web`
- 一次提交只做一件事，混合重构 + 功能 = 违规
- **依赖变更必须独立提交**，不与业务代码混在一起
- Lock 文件（`package-lock.json`）必须入库，安装用 `npm ci` 而非 `npm install`

### 开发工作流 — 数据库迁移纪律

- **Schema 变更必须有对应的 Flyway migration 文件**，`V{N}__{描述}.sql`
- **不可修改已执行的 migration**，只能追加新版本
- **Domain entity 变更和 migration 必须在同一提交中**
- Flyway 在 Spring Boot 启动时自动执行（`spring.flyway.enabled=true`）
- **PGVector extension 必须在第一支 migration 中创建**：`CREATE EXTENSION IF NOT EXISTS vector`

### 开发工作流 — API 契约

- API 路径前缀统一 `/api/v1/`，kebab-case
- **变更已有接口遵循"先增后删"**：新字段先加，旧字段标记 deprecated
- 前端代理配置在 `web/vite.config.ts` 的 `server.proxy`
- Cookie-based session auth，CSRF header: `X-MYAI-CSRF: 1`
- 错误响应格式：`{status, error, message}`，前端通过 `ApiError` 类解析

### 开发工作流 — 日志与环境

- Java 用 SLF4J 占位符 `log.info("user={}", userId)`，**禁止字符串拼接**
- 日志级别：ERROR（告警）、WARN（可恢复异常）、INFO（业务节点）、DEBUG（调试）
- 后端环境变量全部 `${ENV_VAR:default}` 模式，12-factor 合规
- 前端环境变量仅限 `VITE_` 前缀，其他前缀构建时被忽略
- 敏感信息走环境注入，不进代码库

### 关键防错规则 — AI 代理必犯陷阱

**架构腐蚀类（编译通过但架构已死）：**

- **domain 包 import 列表出现 `javax.persistence` / `jakarta.persistence` / `org.springframework` 即为架构违规** — domain 必须是纯 Java，零框架依赖
- **`@Transactional` 只允许出现在 Application Service 层** — 同类内部方法自调用不走 Spring AOP 代理，`@Transactional` 完全不生效
- **禁止在循环体内调用任何 repository 方法** — 所有批量数据获取必须在循环外通过批量查询完成，防止 N+1 查询

**编译通过但运行时炸裂类：**

- **`Stream.toList()` 返回 unmodifiable list** — 如需修改集合内容，用 `.collect(Collectors.toList())` 替代
- **Record 的 compact constructor 只做校验（拒绝非法状态），不做默认值填充** — 默认值在 factory method 或 mapper 中处理
- **Record 包含 List/Set 字段时必须防御性拷贝** — `Collections.unmodifiableList(new ArrayList<>(list))`，否则浅不可变是逻辑炸弹
- **`CompletableFuture` 必须有 `.exceptionally()` 错误处理** — 异步链中异常会被永久吞没，静默成功

**前端隐性 bug 类：**

- **React Query `useQuery` 必须处理 `isLoading` 和 `data === undefined`** — 禁止假设 `data` 一定存在，staleTime + 条件渲染组合会产生 stale 副本
- **Ant Design Form `preserve={false}` 的动态字段在 `onFinish` 中可能不存在** — 动态字段用 `Form.useWatch` 跟踪
- **后端 `ON CONFLICT` 与 partial index 不兼容** — `ON CONFLICT (column)` 引用的是唯一约束，不是 partial unique index

**测试虚假安全类：**

- **每个测试方法必须至少有一个显式 assert 或 verify，且断言必须检查具体字段** — 禁止"不抛异常就算过"
- **禁止 mock JDBC/PGVector 链路** — SQL 正确性只能靠真实数据库验证，mock 给的是虚假覆盖率
- **时间相关断言的容差必须 >= 10 秒或用 Clock injection** — CI 服务器与本地的时钟偏差会产生系统性 flaky test

---

## 使用指南

**AI Agent：**

- 实现任何代码前必须先读取此文件
- 严格遵守所有规则，当有疑问时选择更严格的选项
- 新模式出现时更新此文件

**人类维护者：**

- 保持文件精简，聚焦于 agent 需求
- 技术栈变更时同步更新
- 定期审查，移除已过时或已变得显而易见的规则
- 相关改进任务记录在 `docs/backlog/BACKLOG.md`

Last Updated: 2026-06-15
