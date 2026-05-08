# 版本路线图（Roadmap）

## 当前进度快照（截至 2026-05-08）

### 已完成
- `ingest` 受理闭环：上传受理、状态查询、`kbId + fileHash` 幂等
- `ingest` 处理执行：`UPLOADED -> INGESTING -> INDEXED/FAILED`
- 单进程异步 worker（可配置开关）
- 解析/清洗/分块/向量写入主链路（Tika + 结构优先分块 + PGVector）
- 分块预览调试接口：`GET /api/v1/documents/{documentId}/chunks/preview`
- 重处理能力：`POST /api/v1/documents/{documentId}/reprocess`（`splitVersion++`）
- 瞬时错误重试：指数退避 + jitter（区分 `is_transient`）
- 资产删除闭环：`DELETE /api/v1/documents/{documentId}`（`DELETING -> DELETED`）
- 知识库主数据管理：创建 / 列表 / 编辑 / 启停、上传与问答知识库校验
- 文档列表与管理台：`GET /api/v1/documents`、`/ingest/documents`、统一进入状态 / 预览 / 重处理 / 删除
- 控制台默认落点切换为文档列表页，旧路由保留兼容重定向
- `qa` 子域前端接入：单轮问答、结构化引用展示、无命中兜底提示
- V1.1 管理基础收口：知识库主数据化 + 文档列表管理能力已形成当前基线
- 本地端到端环境收敛：前后端联调路径、运行脚本与最小演示顺序
- V1 发布归档：README / Roadmap / Release Notes / Runbook 状态收敛，并冻结 `v1.0.0` 里程碑标签

### 进行中
- 课程交付材料整理：报告、演示脚本、截图素材与最终导出件归集
- 文档真源同步：README / API 契约 / Roadmap / Runbook 持续对齐当前 V1.1 基线
- 独立权限体系规划：从原 V1.1 轻量鉴权拆出，转向更成熟的 RAG 权限模型设计
  - 专题计划：[RAG 权限体系专项计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\rag-access-control-plan.md)
  - 决策留痕：[ADR-0005：RAG 权限体系基础决策](D:\Code\project\my-AI\docs\adr\ADR-0005-rag-access-control-foundation.md)

### 未开始（后续专项 / V2+）
- 成熟权限体系工程化落地
- OCR 与复杂版式增强

## 版本策略
- 优先小步快跑，每个版本只承载 3-5 个核心目标
- 使用语义化版本：`0.x` 快速迭代，达到稳定后发布 `1.0.0`

## V1.0（入门版，已归档）
### 目标
跑通 Spring AI 核心流程，形成最小可用产品。

### 关键能力
- 单用户
- TXT/PDF 入库
- 固定 DashScope（Spring AI Alibaba）
- 固定向量库（PostgreSQL + PGVector）
- 知识库统计列表
- 基础 RAG 问答（同步返回版）
- 前端控制台最小演示闭环

### 验收条件
- 端到端流程可演示
- 可持续本地开发和打包
- 前后端文案与当前实现一致
- 版本归档文档与里程碑 tag 已建立

## V1.1（管理基础收口版，范围已调整）
### 目标
在保留 V1 最小闭环的前提下，补齐“能持续使用”所需的基础管理能力，并明确后续权限体系的对象边界。

### 关键能力
- 知识库主数据管理（从统计视图升级为可维护对象）
- 文档列表与操作收口（按知识库/状态浏览，统一进入状态、预览、重处理、删除）
- 文档真源同步收口（README / API 契约 / Roadmap / Runbook 与当前实现一致）

### 范围调整说明
- 原计划中的“轻量认证与访问控制”已从 V1.1 范围拆出
- 权限相关工作不再以“单管理员轻量封口”方式收尾，而是单独规划成熟 RAG 权限体系

### 约束
- 不引入多租户
- 不提前并入多轮会话历史
- 不把权限体系以临时补丁形式并入 V1.1

### 当前理解的收口顺序
1. 完成知识库主数据化
2. 完成文档列表与管理台
3. 同步 V1.1 文档真源
4. 独立启动权限体系专项

## 权限体系专项（独立规划）
### 目标
围绕真实 RAG 系统的身份、资源、动作与作用域，单独规划一套可持续演进的权限体系。

### 专题入口
- 计划文档：[RAG 权限体系专项计划](D:\Code\project\my-AI\docs\runbooks\plans\rag-access-control\rag-access-control-plan.md)
- 决策文档：[ADR-0005：RAG 权限体系基础决策](D:\Code\project\my-AI\docs\adr\ADR-0005-rag-access-control-foundation.md)

### 关键能力
- 认证入口：登录、会话或令牌策略
- 授权模型：用户 / 角色 / 资源 / 动作 / 作用域
- 资源边界：知识库、文档、分块预览、问答、重处理、删除
- 审计留痕：访问记录与关键操作追踪

### 规划原则
- 先定义资源边界，再选实现技术
- 先支持知识库级 / 文档级授权，再考虑更细粒度扩展
- 为后续工作区、多团队、多租户预留演进空间
- 首期采用本地账号 + Session 基线，并补齐 CSRF 与登录防爆破防护

## V2.0（进阶版）
### 目标
增强可用性和灵活性，支持更真实业务场景。

### 关键能力
- 多知识库治理增强
- 历史会话记忆
- 多模型切换（智谱/通义 等）
- 结合独立权限体系专项的工程化接入

### 技术前提
- Provider 插件化接口已经在 V1 留好
- 知识库元数据模型支持扩展
- 权限体系专项已明确核心资源边界

## V3.0（SaaS 版）
### 目标
支持企业化与多租户运营。

### 关键能力
- 租户（企业）概念
- 多租户隔离
- 计费统计
- 企业自定义模型 API Key

### 技术前提
- 请求链路支持 TenantContext
- 数据层具备 tenant_id 隔离策略
- 权限体系可向租户维度扩展

## 版本进入规则（Gate）
- 当前版本的 DoD 必须达成
- 关键技术债已记录到 ADR/Backlog
- 发布说明与迁移说明已更新
