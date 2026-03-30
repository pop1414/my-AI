# 架构说明（Architecture）

## 1. 架构图
- 主架构图：`docs/architecture/diagrams/core-architecture-latest.puml`
- 渲染图：`docs/architecture/diagrams/core-architecture-latest-_____Latest___Clean_Layout_.png`

## 2. 分层设计
- 接入层：Upload/Knowledge/QA/SSE API
- 应用服务层：IngestService / KnowledgeService / RagService
- Spring AI 抽象层：DocumentReader & Splitter / EmbeddingModel / VectorStore / ChatClient / ChatModel
- 基础设施适配层：LLM / Embedding / Vector / Repository / ObjectStorage Adapter
- 数据层：MySQL / Vector DB / MinIO(S3)
- 横切治理层：Tenant/Auth/RateLimit/Observability/Audit

## 3. 核心链路
### 文档入库链路
上传 -> 入库队列 -> 解析分块 -> 向量化 -> 写入向量库 -> 写元数据 -> 原文存储

### 问答链路
提问 -> 检索 TopK -> 构建 Prompt -> 调用 ChatModel -> 返回回答（支持流式）

## 4. 依赖约束
- Controller 不直接访问数据库
- 应用层只依赖抽象接口，不依赖具体 SDK
- Provider 切换通过 Adapter 实现，不改业务逻辑

## 5. 扩展点（为 V2/V3 预留）
- LLM Provider 切换
- 向量库切换
- TenantContext 贯穿请求链路
- 会话记忆策略（短期/长期）

## 6. 非功能要求（初版）
- 可观测：请求日志、耗时、错误码
- 弹性：超时、重试、熔断
- 安全：最小权限、敏感信息脱敏
