# 版本路线图（Roadmap）

## 当前进度快照（截至 2026-05-07）

### 已完成
- `ingest` 受理闭环：上传受理、状态查询、`kbId + fileHash` 幂等
- `ingest` 处理执行：`UPLOADED -> INGESTING -> INDEXED/FAILED`
- 单进程异步 worker（可配置开关）
- 解析/清洗/分块/向量写入主链路（Tika + 结构优先分块 + PGVector）
- 分块预览调试接口：`GET /api/v1/documents/{documentId}/chunks/preview`
- 重处理能力：`POST /api/v1/documents/{documentId}/reprocess`（`splitVersion++`）
- 瞬时错误重试：指数退避 + jitter（区分 `is_transient`）
- 资产删除闭环：`DELETE /api/v1/documents/{documentId}`（`DELETING -> DELETED`）
- `knowledge` 子域前端接入：知识库列表、`indexedDocumentCount` 展示、跳转问答
- `qa` 子域前端接入：单轮问答、结构化引用展示、无命中兜底提示
- V1 控制台闭环：上传 -> 状态查询 -> 知识库统计 -> 单轮问答
- 本地端到端环境收敛：前后端联调路径、运行脚本与最小演示顺序
- V1 发布归档：README / Roadmap / Release Notes / Runbook 状态收敛，并冻结 `v1.0.0` 里程碑标签

### 进行中
- 课程交付材料整理：报告、演示脚本、截图素材与最终导出件归集
- V1.1 范围规划：用户/权限、文档列表与知识库管理能力梳理

### 未开始（V1 后半段 / V2）
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

## V2.0（进阶版）
### 目标
增强可用性和灵活性，支持更真实业务场景。

### 关键能力
- 多知识库管理
- 历史会话记忆
- 多模型切换（智谱/通义 等）

### 技术前提
- Provider 插件化接口已经在 V1 留好
- 知识库元数据模型支持扩展

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

## 版本进入规则（Gate）
- 当前版本的 DoD 必须达成
- 关键技术债已记录到 ADR/Backlog
- 发布说明与迁移说明已更新
