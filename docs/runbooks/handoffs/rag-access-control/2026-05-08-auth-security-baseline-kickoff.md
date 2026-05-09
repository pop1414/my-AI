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

当前仍在 `feature/auth-security-baseline` 分支，无需新开分支继续推进。

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

已完成验证：

- 后端测试：`.\\mvnw.cmd -q test`

当前首批实现仍有一个关键使用缺口：

- 系统尚未提供初始管理员账号创建方式
- 所有 `/api/v1/**` 受保护后，如果没有初始用户，只能手工插入数据库才能登录

因此下一步仍属于 `auth-security-baseline` 分支范围，不需要切新分支。

## 8. 下一步：bootstrap admin 初始账号引导

下一步建议实现初始管理员账号引导，让本地环境和后续联调可以在空库或无成员库中自动创建第一个可登录账号。

建议实现范围：

- 新增 bootstrap admin 配置项，例如：
  - `MYAI_AUTH_BOOTSTRAP_ADMIN_USERNAME`
  - `MYAI_AUTH_BOOTSTRAP_ADMIN_PASSWORD`
  - `MYAI_AUTH_BOOTSTRAP_ADMIN_DISPLAY_NAME`
- 应用启动时检查默认工作区是否已有 membership
- 当默认工作区没有成员，且配置了管理员用户名与密码时，创建：
  - `users`
  - `local_credentials`
  - `workspace_memberships`
- 初始角色建议使用 `WORKSPACE_OWNER`
- 密码必须通过 `BCryptPasswordEncoder` 加密后写入 `local_credentials`
- 不在 Flyway 脚本中写入明文密码或固定测试密码
- 已存在成员时不重复创建初始管理员
- 未配置密码时不创建账号，并通过日志说明跳过原因

建议测试覆盖：

- 空成员库且配置完整时创建 bootstrap admin
- 默认工作区已有成员时不重复创建
- 未配置密码时跳过创建
- 创建完成后可通过现有 `login` 链路登录

后续推进顺序建议保持：

1. bootstrap admin 初始账号引导
2. `CurrentUserProvider` 当前用户上下文
3. `AuthorizationService` 授权服务骨架
4. 接入 `knowledge-bases` 权限判断
5. 接入 `documents` 权限判断
6. 接入 `qa.ask` 召回后授权过滤
7. 后端稳定后再补前端登录页和路由守卫

## 9. 一句话交接

**`feature/auth-security-baseline` 已完成首批 Spring Security、Session 登录、认证接口、CSRF Header、登录锁定与审计写入；下一步继续在当前分支实现 bootstrap admin 初始账号引导，前端仍暂不编码。**
