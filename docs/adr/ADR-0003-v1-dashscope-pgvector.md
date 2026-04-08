# ADR-0003：V1 向量存储基线调整为 PGVector

- 编号：ADR-0003
- 标题：V1 使用 DashScope + PostgreSQL(PGVector) 作为固定技术基线
- 状态：Accepted
- 日期：2026-03-31
- 更新：2026-04-08（与 Spring AI Alibaba 实现收敛）
- 替代：ADR-0002

## 背景
在 `ADR-0002` 中，V1 选择了本地向量库以降低启动门槛。  
随着项目进入可持续开发阶段，需要更稳定的持久化、过滤能力和更接近生产的检索形态，因此调整向量存储基线。

## 备选方案
1. 继续使用本地向量库（SimpleVectorStore）
2. 切换到 PostgreSQL + PGVector
3. 切换到独立向量数据库（Redis/Milvus 等）

## 决策
选择方案 2：
- LLM/Embedding：固定 DashScope（通过 Spring AI Alibaba 接入）
- Vector Store：固定 PostgreSQL + PGVector
- 元数据：与业务元数据统一放在 PostgreSQL（V1 阶段优先简化运维）

## 决策理由
- 提供稳定持久化能力，避免进程内存/本地文件方案的环境依赖问题
- 支持向量检索与结构化过滤结合，便于后续多知识库演进
- 与 Spring AI 的集成路径清晰，适合作为学习到工程化的过渡方案

## 影响
### 正向影响
- 数据库能力更完整，便于调试与复现
- 后续向 V2 演进时，数据模型迁移成本更低

### 负向影响 / 风险
- 本地开发需新增 PostgreSQL 与扩展初始化步骤
- 运维复杂度高于本地向量库方案

## 后续动作
- M1 下一步：落地 PGVector 的本地开发环境与建表/扩展初始化脚本
- 在应用层实现 PGVector VectorStore 接入，并完成上传入库链路打通
- 新增 ADR-0004（Accepted）：明确处理执行链路的异步策略、重试与幂等规则
