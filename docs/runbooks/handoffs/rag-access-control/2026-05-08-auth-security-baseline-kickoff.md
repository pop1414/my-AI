# 会话启动包：RAG 权限体系认证基线（auth-security-baseline）

日期：2026-05-08  
仓库：`D:\Code\project\my-AI`  
建议分支：`feature/auth-security-baseline`  
来源分支：`feature/auth-flyway-schema`

## 1. 分支目标

`auth-security-baseline` 分支的目标是进入阶段二认证与安全基线建设，先完成后端认证闭环，再进入业务授权与前端接入。

本分支优先解决：

1. 引入 `Spring Security`
2. 建立 `HttpSession + HttpOnly Cookie` 登录态
3. 提供登录、登出、当前用户接口
4. 固化未登录 `401`、无权限 `403` 的后端语义
5. 建立写操作 CSRF Header 基线
6. 为登录失败计数、锁定与审计写入铺好应用服务入口

## 2. 前置状态

来自 `feature/auth-flyway-schema` 的前置能力已经完成：

- Flyway 已接管 schema
- 仓储和框架侧隐式 DDL 已关闭或收口
- `knowledge_bases.workspace_id` 与 `ingest_documents.workspace_id` 已运行时显式传递
- `workspaces`、`users`、`local_credentials`、`workspace_memberships`、`login_lock_states` 已入模
- `knowledge_base_grants`、`document_grants`、`audit_events` 已通过 Flyway 迁移落地
- grant 表已对齐到 `(workspace_id, user_id)` membership 关系

这意味着新分支不需要继续补权限基础表，除非认证实现过程中发现必要的字段或索引缺口。

## 3. 本分支不做什么

- 不编写前端页面
- 不实现完整知识库、文档、问答授权规则
- 不实现成员管理、授权管理、审计查询后台接口
- 不接入 OIDC、SSO、API Token
- 不实现多工作区路由或请求级工作区选择

前端接入应等待后端认证接口和错误语义稳定后再开始。

## 4. 建议实施顺序

### 4.1 安全框架接入

- 引入 `spring-boot-starter-security`
- 新增安全配置类
- 明确公开接口与受保护接口边界
- 关闭默认表单登录跳转，统一返回 JSON 错误

### 4.2 认证接口

首批接口固定为：

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

首批认证态固定为：

- 登录成功后创建服务端 Session
- 浏览器通过 `HttpOnly Cookie` 维护 Session
- 未登录访问受保护接口返回 `401`
- 已登录但授权不足返回 `403`

### 4.3 账号与锁定逻辑

- 账号来源使用 `users` 与 `local_credentials`
- 登录失败计数落入 `login_lock_states`
- 达阈值后拒绝登录
- 登录成功后清空失败计数
- 登录成功、失败、锁定事件写入 `audit_events`

首期可先完成登录成功、失败与锁定自动写审计；管理员手动解锁可以放到后续治理接口分支。

### 4.4 CSRF Header 基线

- `GET/HEAD/OPTIONS` 不要求 CSRF Header
- 写操作必须携带统一自定义 Header
- 缺失或非法时返回 `403`
- Header 名称建议在配置或常量中集中定义，避免散落在过滤器和测试中

## 5. 测试建议

本分支至少覆盖：

- 未登录访问受保护接口返回 `401`
- 登录成功后 `GET /api/v1/auth/me` 返回当前用户
- 登出后 Session 失效
- 密码错误增加失败计数
- 达到锁定阈值后登录被拒绝
- 写操作缺少 CSRF Header 返回 `403`
- 登录成功、失败、锁定事件写入 `audit_events`

## 6. 前端接入判断点

当前不建议补前端。进入前端前至少需要满足：

- `login/logout/me` 接口稳定
- `401/403` JSON 错误结构稳定
- CSRF Header 名称与触发规则稳定
- Session Cookie 行为在本地开发环境验证通过

满足以上条件后，再更新 `docs/04-api-contract.yaml` 并开始前端登录态与路由守卫接入。

## 7. 当前完成状态（2026-05-09）

当前 `feature/auth-security-baseline` 分支的原定职责已经完成，后续治理接口不建议继续堆叠在该分支。

首批认证基线已完成：

- 已引入 `Spring Security`
- 已实现 `POST /api/v1/auth/login`
- 已实现 `POST /api/v1/auth/logout`
- 已实现 `GET /api/v1/auth/me`
- 已通过 `HttpSession + HttpOnly Cookie` 维护登录态
- 已关闭默认表单登录、HTTP Basic 与默认登出流程，改为 JSON 风格接口
- 未登录访问受保护接口返回 `401`
- 账号锁定、禁用、缺少 CSRF Header 等场景返回 `403`
- 写操作已接入统一 CSRF Header 基线：`X-MYAI-CSRF: 1`
- 登录失败计数已接入 `login_lock_states`
- 登录成功、失败与锁定事件已接入 `audit_events`
- `docs/04-api-contract.yaml` 已补充 auth 接口、Cookie 认证和 CSRF Header 说明

空库初始账号引导已完成：

- 已新增 bootstrap admin 配置项：
  - `MYAI_AUTH_BOOTSTRAP_ADMIN_USERNAME`
  - `MYAI_AUTH_BOOTSTRAP_ADMIN_PASSWORD`
  - `MYAI_AUTH_BOOTSTRAP_ADMIN_DISPLAY_NAME`
- 应用启动时会检查默认工作区是否已有 membership
- 当默认工作区无成员且配置了管理员用户名与密码时，会创建：
  - `users`
  - `local_credentials`
  - `workspace_memberships`
- 初始角色固定为 `WORKSPACE_OWNER`
- 密码通过 `BCryptPasswordEncoder` 加密后写入 `local_credentials`
- 已存在成员时不重复创建初始管理员
- 未配置用户名或密码时不创建账号，并通过日志说明跳过原因

角色与权限常量已完成 Java 类型化收口：

- 已新增 `WorkspaceRole` 枚举：
  - `WORKSPACE_OWNER`
  - `WORKSPACE_ADMIN`
  - `WORKSPACE_MEMBER`
- 已新增 `KnowledgeBaseRole` 枚举：
  - `KB_MANAGER`
  - `KB_CONTRIBUTOR`
  - `KB_READER`
  - `KB_ASKER`
- 已新增 `DocumentPermission` 枚举：
  - `DOC_ALLOW_READ`
  - `DOC_ALLOW_MANAGE`
  - `DOC_DENY`
- 登录链路中的 `workspaceRole` 已从字符串收口为 `WorkspaceRole`
- REST 响应仍输出字符串角色值，保持 `login/me` 接口契约不变
- JDBC 读写仍与数据库 `VARCHAR` 字段保持 `enum.name()` 同名映射

当前用户上下文已完成：

- 已新增 `CurrentUser` 应用层模型，统一承载当前登录用户的：
  - `userId`
  - `username`
  - `workspaceId`
  - `WorkspaceRole workspaceRole`
- 已新增 `CurrentUserProvider` 应用层接口，提供：
  - `currentUser()`
  - `requireCurrentUser()`
- 已新增 `SpringSecurityCurrentUserProvider`，从 Spring Security `SecurityContext` 读取 `MyAiPrincipal`
- 未登录、未认证或 Principal 类型不匹配时按未登录处理
- 业务服务后续应通过 `CurrentUserProvider` 获取当前用户，避免散落读取 `SecurityContextHolder`

授权服务骨架已完成：

- 已新增 `AuthorizationService` 授权服务入口
- 已新增 `AuthorizationGrantRepository` 授权 grant 读取端口
- 已新增 JDBC 授权读取实现，仅查询 `ACTIVE` 状态的知识库与文档授权
- 工作区级授权规则已固化：
  - `WORKSPACE_OWNER` 放行
  - `WORKSPACE_ADMIN` 放行
  - `WORKSPACE_MEMBER` 继续检查资源授权
- 知识库授权判断入口已预留：
  - `requireCanManageKnowledgeBase`
  - `requireCanContributeKnowledgeBase`
  - `requireCanReadKnowledgeBase`
  - `requireCanAskKnowledgeBase`
- 文档权限覆盖入口已预留：
  - `requireCanManageDocument`
  - `requireCanReadDocument`
- 文档级 `DOC_DENY` 已按最高优先级拒绝处理
- 授权拒绝统一抛出 `AccessDeniedException`，可沿用现有 `403` JSON 语义
- 已补充 `AuthorizationService` 单元测试，覆盖工作区管理员放行、普通成员无授权拒绝、未登录拒绝与 `DOC_DENY` 覆盖场景

知识库接口权限接入已完成：

- 创建知识库已接入 `AuthorizationService.requireCanManageWorkspace()`
- 创建知识库仅允许 `WORKSPACE_OWNER` / `WORKSPACE_ADMIN`
- 创建知识库已使用当前用户 `workspaceId`，不再在应用服务中硬编码默认工作区
- 更新知识库已接入 `AuthorizationService.requireCanManageKnowledgeBase(kbId)`
- 更新知识库允许 `WORKSPACE_OWNER` / `WORKSPACE_ADMIN` / `KB_MANAGER`
- 查询知识库列表已使用当前用户 `workspaceId`
- `docs/04-api-contract.yaml` 已补充 knowledge-bases 接口的 Cookie 认证、CSRF Header 与 `401/403` 语义
- 已补充知识库应用服务与授权服务单元测试

上传接口权限接入已完成：

- `documents/upload` 已接入 `AuthorizationService.requireCanContributeKnowledgeBase(kbId)`
- 上传文档允许 `WORKSPACE_OWNER` / `WORKSPACE_ADMIN` / `KB_MANAGER` / `KB_CONTRIBUTOR`
- 上传文档无贡献权限时拒绝，并沿用 `403` JSON 语义
- 上传文档已使用当前用户 `workspaceId` 查询知识库、执行幂等去重与保存文档
- 无贡献权限时不会查询知识库、不会生成文档 ID、不会保存文档记录
- `docs/04-api-contract.yaml` 已补充 `documents/upload` 的 Cookie 认证、CSRF Header 与 `401/403` 语义
- 已补充上传受理应用服务单元测试，覆盖贡献权限放行、无权限拒绝和当前工作区写入

文档其他接口权限接入已完成：

- 文档列表已改为在当前用户工作区范围内查询，并对结果执行文档可读权限过滤
- `documents/{id}/status` 已要求可读取目标文档
- `documents/{id}/chunks/preview` 已要求可读取目标文档
- `documents/{id}/reprocess` 已要求可贡献目标文档所属知识库
- `documents/{id}` 删除已要求可管理目标文档
- `docs/04-api-contract.yaml` 已补充上述接口的 Cookie 认证、CSRF Header 与 `401/403` 语义

问答接口权限接入已完成：

- `qa.ask` 已要求当前用户对目标知识库具备 `ask` 权限
- `qa.ask` 已在“召回后、生成前”按文档授权过滤候选分块
- `qa.ask` 过滤后无可用内容时返回问答兜底结果，不返回 `403`
- `docs/04-api-contract.yaml` 已补充 `qa.ask` 的 Cookie 认证、CSRF Header 与 `401/403` 语义

已完成验证：

- 后端测试：`.\\mvnw.cmd -q test`

当前实现边界：

- 系统已经能够完成登录、登出、当前用户查询、Session 登录态维护和空库初始管理员创建
- 认证层已经知道当前登录用户是谁、属于哪个默认工作区、拥有哪个工作区角色，并已提供应用层统一读取入口
- `AuthorizationService` 已完成知识库、文档和问答场景所需的应用层授权骨架
- `knowledge-bases`、`documents` 与 `qa.ask` 已完成当前阶段要求的访问控制接入
- 接下来进入成员管理、授权管理和审计查询接口建设，建议切出新的治理接口分支继续推进

## 8. 下一步：进入治理接口建设

下一步建议从 `feature/auth-security-baseline` 切出新的治理接口分支，
例如 `feature/auth-authorization-governance`，
继续实现成员管理、知识库授权管理、文档授权管理和审计查询接口。

建议实现范围：

- 成员列表、成员新增、成员角色调整、成员停用 / 移除接口
- 知识库授权列表、授予、调整、回收接口
- 文档级覆盖授权列表、授予、调整、回收接口
- 审计事件查询接口
- 同步更新 `docs/04-api-contract.yaml` 中治理接口的契约说明

建议测试覆盖：

- 未登录访问治理接口返回 `401`
- 非工作区管理员访问治理接口返回 `403`
- 授权变更后立即影响 `knowledge/documents/qa` 权限判断结果
- 成员变更、知识库授权变更、文档授权变更写入 `audit_events`

后续推进顺序建议保持：

1. 成员管理接口
2. 知识库授权接口
3. 文档授权接口
4. 审计查询接口
5. 后端稳定后再评估前端后台页面接入

## 9. 一句话交接

**`feature/auth-security-baseline` 已完成 Spring Security、Session 登录、认证接口、CSRF Header、登录锁定、审计写入、bootstrap admin 初始账号引导、角色枚举收口、`CurrentUserProvider` 当前用户上下文、`AuthorizationService` 授权服务骨架、`knowledge-bases` / `documents` / `qa.ask` 权限接入；下一步请切到 `feature/auth-authorization-governance`，继续实现成员管理、知识库授权、文档授权和审计查询接口。**
