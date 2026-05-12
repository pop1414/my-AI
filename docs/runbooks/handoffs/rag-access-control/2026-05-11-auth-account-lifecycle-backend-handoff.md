# 会话交接包：账号生命周期后端治理

日期：2026-05-11  
仓库：`D:\Code\project\my-AI`  
主题：`rag-access-control` 专项 - 账号生命周期后端  
当前状态：后端能力已完成，前端未开始接入

## 1. 本次主题结论

本次会话已完成账号生命周期后端治理闭环，补齐了此前权限专项中的账号运营缺口。

当前系统已新增：

- 工作区账号列表查询
- 本地账号创建
- 账号启用 / 停用
- 密码重置
- 成员移除

并且全部纳入：

- 现有 Session 认证体系
- 工作区管理员授权校验
- 审计日志落库

## 2. 本次已完成事项

### 2.1 后端接口

已完成接口：

- `GET /api/v1/admin/accounts`
- `POST /api/v1/admin/accounts`
- `PATCH /api/v1/admin/accounts/{userId}/status`
- `POST /api/v1/admin/accounts/{userId}/password/reset`
- `DELETE /api/v1/admin/accounts/{userId}/membership`

### 2.2 后端规则收口

已固定以下规则：

- 账号停用只改 `users.status`
- 成员移除只改 `workspace_memberships.status`
- 密码重置同时清空失败次数和锁定状态
- 账号治理接口统一要求工作区管理员权限
- 账号治理动作全部写审计

### 2.3 文档沉淀

本次新增文档：

- [账号生命周期后端实施计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\账号生命周期后端实施计划.md)
- [账号生命周期后端完成概览](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\账号生命周期后端完成概览.md)
- [本交接包](D:\Code\project\my-AI\docs\runbooks\handoffs\rag-access-control\2026-05-11-auth-account-lifecycle-backend-handoff.md)

同时建议同步更新：

- [docs/05-release-notes.md](D:\Code\project\my-AI\docs\05-release-notes.md)
- [docs/runbooks/README.md](D:\Code\project\my-AI\docs\runbooks\README.md)
- [docs/runbooks/plans/README.md](D:\Code\project\my-AI\docs\runbooks\plans\README.md)
- [docs/README.md](D:\Code\project\my-AI\docs\README.md)
- [RAG 权限体系专项计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\rag-access-control-plan.md)

## 3. 已完成验证

已执行并通过：

```bash
.\mvnw.cmd -q "-Dtest=CreateManagedAccountApplicationServiceTest,UpdateManagedAccountStatusApplicationServiceTest,ResetManagedAccountPasswordApplicationServiceTest,RemoveManagedAccountMembershipApplicationServiceTest,AccountAdminControllerTest,AuthSecurityBaselineTest,WorkspaceMemberAdminControllerTest" test
```

验证覆盖：

- 创建账号
- 更新账号状态
- 重置密码
- 移除成员关系
- 账号治理控制器
- 既有安全基线

## 4. 当前工作区判断

如果下一次继续推进，不需要再回头重构账号主数据模型。

现有模型已经够用：

- `users`
- `local_credentials`
- `workspace_memberships`
- `login_lock_states`

下一次应该直接承接后续主题，而不是再重复讨论“账号表要不要重做”。

## 5. 下一步建议

按当前优先级，建议严格顺序推进：

1. 动态 CSRF token 升级
2. 前端账号管理页接入
3. 前端知识库页 / 知识库选择器联调验证

### 5.1 动态 CSRF token

当前问题：

- 仍使用固定 Header：`X-MYAI-CSRF: 1`

建议下一步处理：

- 服务端生成并持久化动态 token
- 登录态恢复时下发 token
- 请求层改为自动携带动态值

### 5.2 前端账号管理页

当前状态：

- 后端接口已具备接入条件
- 前端尚未新增账号管理页

建议前端页最小范围：

- 账号列表
- 创建账号
- 启用 / 停用
- 重置密码
- 移除成员

### 5.3 前端知识库页 / 知识库选择器联调

当前状态：

- 后端知识库列表已按授权可见范围收紧
- 前端接口结构无需改造

建议补一轮联调确认：

- 普通成员打开知识库页只看到有权知识库
- 上传页知识库选择器只展示有权知识库
- 问答页知识库选择器只展示有权知识库

## 6. 关键阅读入口

下一位继续推进时，建议按这个顺序读：

1. [RAG 权限体系专项计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\rag-access-control-plan.md)
2. [账号生命周期后端实施计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\账号生命周期后端实施计划.md)
3. [账号生命周期后端完成概览](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\账号生命周期后端完成概览.md)
4. [本交接包](D:\Code\project\my-AI\docs\runbooks\handoffs\rag-access-control\2026-05-11-auth-account-lifecycle-backend-handoff.md)

## 7. 一句话交接

**账号生命周期后端已经补齐，知识库列表可见性也已收紧；下一步应直接进入动态 CSRF 与前端联调。**
