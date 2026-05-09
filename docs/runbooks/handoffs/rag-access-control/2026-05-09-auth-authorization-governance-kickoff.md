# 会话启动包：RAG 权限体系治理接口（auth-authorization-governance）

日期：2026-05-09  
仓库：`D:\Code\project\my-AI`  
建议分支：`feature/auth-authorization-governance`  
来源分支：`feature/auth-security-baseline`

## 1. 分支目标

`auth-authorization-governance` 分支的目标是承接阶段二后半段治理能力建设，
在既有认证、文档权限和问答过滤基线之上，补齐后台可运营的权限管理与审计查询接口。

本分支优先解决：

1. 工作区成员管理接口
2. 知识库授权管理接口
3. 文档授权管理接口
4. 审计事件查询接口
5. 治理动作对应的审计写入与回归验证

## 2. 前置状态

来自 `feature/auth-security-baseline` 的前置能力已经完成：

- `Spring Security + HttpSession + HttpOnly Cookie` 已完成落地
- `POST /api/v1/auth/login`、`POST /api/v1/auth/logout`、`GET /api/v1/auth/me` 已完成
- `401/403` 与统一 CSRF Header 语义已稳定
- `CurrentUser`、`CurrentUserProvider` 与 `AuthorizationService` 已成为统一身份与授权入口
- `knowledge-bases` 已完成权限接入
- `documents/upload`、`documents`、`documents/{id}/status`、`documents/{id}/chunks/preview`、`documents/{id}/reprocess`、`documents/{id}` 已完成权限接入
- `qa.ask` 已完成知识库 `ask` 权限校验与召回后文档授权过滤
- `knowledge_base_grants`、`document_grants`、`audit_events` 表已通过 Flyway 落地
- grant 表已通过 membership 外键对齐到 `(workspace_id, user_id)` 成员关系

这意味着新分支不需要再补认证基线或文档 / 问答访问控制本身，
而是聚焦“如何管理这些授权”和“如何查询这些变更”。

## 3. 本分支不做什么

- 不编写前端页面
- 不引入 OIDC、SSO、API Token
- 不引入多工作区路由或请求级工作区切换
- 不引入分块级授权
- 不重做现有 `knowledge/documents/qa` 的已完成权限基线

## 4. 建议实施顺序

### 4.1 成员管理接口

- 新增工作区成员列表查询接口
- 新增成员创建或邀请落库接口（以当前已有 `users` / `local_credentials` / `workspace_memberships` 设计为准）
- 新增成员角色调整接口
- 新增成员停用或移除接口

建议先完成成员查询和角色调整，再做新增与停用，这样后续知识库 / 文档授权可以直接复用成员真源。

### 4.2 知识库授权接口

- 查询知识库授权列表
- 授予或更新 `KB_MANAGER`
- 授予或更新 `KB_CONTRIBUTOR`
- 授予或更新 `KB_READER`
- 授予或更新 `KB_ASKER`
- 回收知识库授权

要求：

- 授权对象必须是当前工作区有效成员
- 同一 `(workspace_id, kb_id, user_id)` 授权关系需保持单一真源
- 授权变更后应立即影响既有 `knowledge/documents/qa` 访问控制结果

### 4.3 文档授权接口

- 查询文档级覆盖授权列表
- 授予或更新 `DOC_ALLOW_READ`
- 授予或更新 `DOC_ALLOW_MANAGE`
- 授予或更新 `DOC_DENY`
- 回收文档级覆盖授权

要求：

- 文档级覆盖继续遵循 `DOC_DENY` 最高优先级
- 文档级覆盖仅用于资源例外，不替代知识库级主授权模型

### 4.4 审计查询接口

- 提供 `audit_events` 查询接口
- 支持按事件类型过滤：
  - 登录成功 / 失败 / 锁定 / 解锁 / 登出
  - 成员变更
  - 知识库授权变更
  - 文档授权变更
- 支持按成员、资源、时间范围过滤

## 5. 接口与契约更新要求

本分支落地时需要同步更新 `docs/04-api-contract.yaml`，至少新增：

- 成员管理接口
- 知识库授权管理接口
- 文档授权管理接口
- 审计查询接口

并为这些接口补齐：

- Cookie 认证说明
- CSRF Header 说明
- `401/403` 语义
- 关键业务错误码与冲突语义

## 6. 测试建议

本分支至少覆盖：

- 未登录访问治理接口返回 `401`
- 非 `WORKSPACE_OWNER` / `WORKSPACE_ADMIN` 访问治理接口返回 `403`
- 成员角色调整后，访问控制结果立即生效
- 知识库授权变更后，`knowledge/documents/qa` 权限判断结果立即生效
- 文档级 `DOC_DENY` 变更后，`documents/qa` 结果立即生效
- 成员变更、知识库授权变更、文档授权变更写入 `audit_events`

## 7. 一句话交接

**`feature/auth-security-baseline` 已完成认证基线、文档权限接入和 `qa.ask` 召回后授权过滤；下一步请切到 `feature/auth-authorization-governance`，继续实现成员管理、知识库授权、文档授权和审计查询接口。**
