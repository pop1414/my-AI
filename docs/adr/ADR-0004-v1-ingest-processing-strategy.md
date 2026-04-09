# ADR-0004：V1 文档处理执行策略

- 编号：ADR-0004
- 标题：V1 采用异步处理执行链路与最小可追踪策略
- 状态：Accepted
- 日期：2026-04-01
- 接受日期：2026-04-08

## 背景
当前系统已完成“受理闭环”：
- 上传受理返回 `documentId + ACCEPTED`
- 文档元数据落库为 `UPLOADED`
- 可查询文档状态

下一阶段需要落地真实处理链路：`UPLOADED -> INGESTING -> INDEXED/FAILED`。

## 决策议题
1. 处理模式：同步执行还是异步 worker
2. 分块策略：chunk size/overlap 初值
3. 元数据策略：vector metadata 最小字段集
4. 失败策略：是否重试、最大重试次数
5. 幂等策略：同一 documentId 重复处理如何处理
6. 状态可见性：是否需要处理中百分比

## 决策方案
### 1) 处理模式
- 采用异步 worker（V1 先单进程 worker，不引入外部 MQ）
- 理由：避免上传接口超时，提升稳定性，便于失败重试和状态追踪

### 2) 分块策略
- 初值：`chunk = 500 tokens`, `overlap = 100 tokens`
- 理由：在召回质量与成本之间取得中位平衡，后续根据检索效果迭代
- 约束：采用“结构优先、长度兜底”策略，并要求同一输入+同一参数下拆分结果尽量确定性

### 3) 向量元数据最小集
- `documentId`, `kbId`, `chunkIndex`, `sourceFile`, `contentHash`
- 理由：满足过滤、追踪、幂等重建的最小需要

### 4) 失败策略
- 仅对瞬时错误重试（网络波动、429、5xx、连接池耗尽、短暂 IO）
- 最大重试次数：3 次，指数退避（1s/2s/4s + jitter）
- 非瞬时错误（如 402 欠费、403 无权限、格式不支持、校验错误）直接 `FAILED`

### 5) 幂等策略
- 同一 `documentId` 重复处理默认不重复写入
- 重建流程采用“`splitVersion++` + 先删旧向量再写新向量”
- 结果要求：同一输入重复执行，最终状态一致
- 关键控制点：
  1. 受理幂等：基于 `fileHash` 识别重复上传
  2. 抢占幂等：`UPLOADED -> INGESTING` 使用条件更新（CAS）
  3. chunk 幂等：使用确定性 chunk 标识并 upsert 向量
  4. 状态幂等：状态更新需带前置状态，拒绝非法回退
  5. reprocess 幂等：重复执行 reprocess 后结果保持一致

实现状态（2026-04-02）：
- 控制点 1（受理幂等）已在代码落地：上传时计算 `fileHash`，按 `kbId + fileHash` 查重并复用既有 `documentId`，数据库增加唯一索引保护。
- 控制点 2（抢占幂等）已落地：仓储提供 `UPLOADED -> INGESTING` 的 CAS 更新接口，单进程 worker 已接入并调用处理用例。
- 控制点 3（chunk 幂等）已落地基础能力：采用确定性 `chunkId`，并以“先删后写”方式避免重复膨胀。
- 控制点 4（状态幂等）已落地基础能力：`INGESTING -> INDEXED/FAILED` 状态推进均通过 CAS 完成。
- 控制点 5（reprocess 幂等）仍待实现。

### 6) 状态可见性
- V1 先不做百分比进度
- 保留阶段状态：`UPLOADED/INGESTING/INDEXED/FAILED`
- 可选附加：`processedChunks/totalChunks`

## 影响
### 正向影响
- 上传链路更稳定，用户可持续追踪处理状态
- 为 V2 队列化与多 worker 扩展保留演进空间

### 负向影响 / 风险
- 引入 worker 后，系统复杂度上升
- 需要额外设计任务调度与并发控制

## 后续动作
- 落地瞬时错误重试（3 次指数退避 + jitter）
- 落地 `POST /api/v1/documents/{documentId}/reprocess` 并保证“先删旧向量再重建”的幂等语义
- 持续同步设计文档、图纸与实现边界，避免文档状态漂移

补充实现进展（2026-04-02）：
- 解析能力已从“纯文本直读”升级为 `Tika + NoOpEmbeddedDocumentExtractor + TextCleaningService`，用于抑制嵌入资源噪音并提升文本质量。
