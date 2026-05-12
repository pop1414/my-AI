# 会话交接包：知识库列表授权可见性收紧

日期：2026-05-11  
仓库：`D:\Code\project\my-AI`  
主题：`rag-access-control` 专项 - 知识库列表可见性  
当前状态：后端规则已收紧，前端接口形态未变化

## 1. 本次主题结论

本次会话已完成知识库列表按授权可见范围收紧。

当前 `GET /api/v1/knowledge-bases` 不再对普通成员按工作区全量返回，
而是按当前用户显式知识库授权过滤结果。

## 2. 本次已完成事项

### 2.1 后端规则

已固定：

- `WORKSPACE_OWNER / WORKSPACE_ADMIN` 看全部
- `WORKSPACE_MEMBER` 只看自己存在 `ACTIVE` 显式知识库授权的知识库

### 2.2 实现位置

本次改动主要落在：

- 授权读取端口
- JDBC 授权实现
- 知识库列表应用服务

接口路径和响应结构保持不变。

### 2.3 文档沉淀

本次新增文档：

- [知识库列表授权可见性收紧实施计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\知识库列表授权可见性收紧实施计划.md)
- [知识库列表授权可见性收紧完成概览](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\知识库列表授权可见性收紧完成概览.md)
- [本交接包](D:\Code\project\my-AI\docs\runbooks\handoffs\rag-access-control\2026-05-11-auth-knowledge-base-visibility-handoff.md)

## 3. 已完成验证

已执行并通过：

```bash
.\mvnw.cmd -q "-Dtest=ListKnowledgeBasesApplicationServiceTest,KnowledgeBaseControllerTest,AuthorizationServiceTest,AuthSecurityBaselineTest" test
```

## 4. 当前工作区判断

这一轮已经把“知识库列表是否应该对普通成员全量可见”这个问题收口了。

下一次继续推进时，不需要再回头讨论列表可见性原则，
应直接承接剩余主题。

## 5. 下一步建议

当前剩余优先级建议：

1. 动态 CSRF token 升级
2. 前端账号管理页接入
3. 前端知识库页 / 知识库选择器联调验证

### 5.1 动态 CSRF token

当前问题：

- 仍使用固定 Header：`X-MYAI-CSRF: 1`

建议下一步处理：

- 服务端生成动态 token
- 登录态恢复时下发 token
- 请求层改为自动携带动态 token

### 5.2 前端联调

虽然本轮未改前端接口字段，
但仍建议补一轮联调确认：

- 普通成员打开知识库页是否只看到有权知识库
- 上传页知识库选择器是否自动收紧
- 问答页知识库选择器是否自动收紧

## 6. 推荐阅读顺序

1. [RAG 权限体系专项计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\rag-access-control-plan.md)
2. [知识库列表授权可见性收紧实施计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\知识库列表授权可见性收紧实施计划.md)
3. [知识库列表授权可见性收紧完成概览](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\知识库列表授权可见性收紧完成概览.md)
4. [本交接包](D:\Code\project\my-AI\docs\runbooks\handoffs\rag-access-control\2026-05-11-auth-knowledge-base-visibility-handoff.md)

## 7. 一句话交接

**知识库列表可见性已经按授权收紧，下一步不要再把“普通成员能否看到全部知识库”当成开放问题，直接进入动态 CSRF 和前端联调。**
