# 文档处理执行设计（Ingest Processing Execution）

## 1. 目标与范围
本阶段目标是在“受理闭环”基础上，打通真实处理链路：

- 从 `UPLOADED` 推进到 `INGESTING`
- 执行解析、分块、向量化、向量入库
- 成功更新为 `INDEXED`，失败更新为 `FAILED`

## 2. 非目标（本阶段暂不做）
- 引入外部消息队列（Kafka/RabbitMQ）
- 百分比进度条（先保留阶段状态）
- 多租户隔离和复杂权限控制

## 3. 关键决策（已采纳，部分待实现）
具体详见 ADR：`docs/adr/ADR-0004-v1-ingest-processing-strategy.md`

- 处理模式：异步 worker（单进程）
- 分块参数：`chunk=500`, `overlap=100`
- 元数据最小集：`documentId`, `kbId`, `chunkIndex`, `sourceFile`, `contentHash`
- 失败策略：瞬时错误重试 3 次，指数退避（1s/2s/4s + jitter）
- 幂等策略：同一 `documentId` 重复处理最终一致
- 状态可见性：V1 先不做百分比，保留阶段状态

## 4. 文本拆分策略（V1 可执行规则）
### 4.1 拆分总原则
1. 先解析清洗再拆分  
- 先把 PDF/Word/Markdown 解析为纯文本并清洗噪声，再进入拆分。

2. 结构优先、长度兜底  
- 能按标题/段落切分就按结构切；仅在段落过长时，才启用长度切分。

3. 重叠适度  
- `overlap=100` 仅用于跨段语义衔接，避免过大造成冗余与噪声放大。

4. 元数据透传  
- 每个 chunk 必须携带最小元数据：`documentId`, `kbId`, `chunkIndex`, `sourceFile`, `contentHash`。

5. 拆分结果应尽量确定性  
- 同一输入文本 + 同一拆分参数，应产出一致的 chunk 序列（顺序与边界稳定）。

### 4.2 初始参数（可调）
- `chunk = 500 tokens`
- `overlap = 100 tokens`
- 评估维度：召回命中率、答案引用准确度、处理成本与延迟

## 5. 幂等控制清单（重点）
以下环节都可能因重试/重复触发导致重复操作，必须做幂等控制：

### 5.1 上传受理阶段
- 风险：用户重复点击、客户端超时重试
- 要求：同一文件内容重复上传不应创建多条冲突任务
- 建议：记录 `fileHash`，并在文档表建立约束（如 `kbId + fileHash` 维度）
- 当前实现进度（2026-04-01）：已落地。上传受理链路已计算 `SHA-256 fileHash`，并在受理前执行 `kbId + fileHash` 查重；数据库已增加对应唯一索引保护。

### 5.2 任务启动阶段（UPLOADED -> INGESTING）
- 风险：多个 worker 同时抢到同一任务
- 要求：只有一个执行者能成功进入 `INGESTING`
- 建议：条件更新（Compare-And-Set），例如按当前状态为 `UPLOADED` 才允许更新
- 当前实现进度（2026-04-02）：已落地。仓储层已提供 `CAS` 状态更新接口，单进程 worker 已接入并在抢占成功后触发处理用例。
 - 轮询建议（重试友好）：仅抢占 `next_retry_at <= now()` 且 `retry_count < retry_max` 的任务。

### 5.3 Chunk 向量写入阶段
- 风险：任务重试导致重复写入向量
- 要求：同一 chunk 重复执行后，向量库结果不应重复膨胀
- 建议：
  - 使用确定性 chunk 标识（如 `documentId + chunkIndex + splitVersion`）
  - 向量写入使用 upsert
- 当前实现进度（2026-04-02）：已落地基础能力。当前实现使用确定性 `chunkId`（`hash(documentId, chunkIndex, splitVersion)`），并采用“先按 chunkId 删除再写入”策略，达到与 upsert 等价的幂等效果。

### 5.4 状态更新阶段
- 风险：状态乱序写入（例如已 INDEXED 又被写回 INGESTING）
- 要求：状态只允许按合法路径前进
- 建议：状态更新带前置条件，拒绝非法回退
- 当前实现进度（2026-04-02）：已落地基础能力。`INGESTING -> INDEXED/FAILED` 均采用带前置状态的 CAS 更新。

### 5.5 重处理（reprocess）阶段
- 风险：重复重建造成“旧向量 + 新向量”叠加污染
- 要求：重处理可重复执行，最终结果一致
- 建议：默认 `splitVersion++`，先清理旧 `splitVersion` 向量，再写新向量（避免误删正在写入的新版本）
- 注意：若删除成功但写入失败，必须将状态准确置为 `FAILED` 并保留错误日志，避免“隐形丢失”

### 5.6 瞬时错误判定（RetryPolicy）
- 判定原则：区分瞬时错误与永久错误，减少无意义重试
- 需要重试（`is_transient=true`）：
  - 网络抖动/超时/连接中断
  - 429 / 5xx
  - 数据库连接池耗尽、短暂 IO 异常
- 不重试（`is_transient=false`）：
  - 402 欠费、403 无权限
  - 文件格式不支持、解析失败（非瞬时）
  - 参数校验类错误

## 6. 标准设计图套餐（5 张必选）
### 6.1 用例图（功能边界）
- `docs/architecture/diagrams/ingest/ingest-processing-execution-usecase.puml`

### 6.2 组件图（模块职责与依赖）
- `docs/architecture/diagrams/ingest/ingest-processing-execution-components.puml`

### 6.3 时序图（主流程交互）
- `docs/architecture/diagrams/ingest/ingest-processing-execution-sequence.puml`

### 6.4 状态机图（状态流转规则）
- `docs/architecture/diagrams/ingest/ingest-processing-execution-state.puml`

### 6.5 ER/领域模型图（数据结构）
- `docs/architecture/diagrams/ingest/ingest-processing-execution-er-domain.puml`

## 7. 建议接口（实现前评审）
1. `POST /api/v1/documents/upload`
说明：受理上传，返回 `ACCEPTED`

2. `GET /api/v1/documents/{documentId}/status`
说明：查询文档处理状态

3. `GET /api/v1/documents/{documentId}/chunks/preview`（调试/审计）
说明：查询向量化前分块预览，便于验证解析/清洗/分块结果
建议参数：`limit`、`offset`、`previewChars`
返回字段建议：`chunkIndex`、`contentLength`、`contentPreview`、`truncated`、`sourceFile`、`sourceHint`、`splitVersion`、`contentHash`

4. `POST /api/v1/documents/{documentId}/reprocess`
说明：人工触发重处理（允许状态：`FAILED/INDEXED`，`INGESTING` 返回 409）

## 8. 验收口径（DoD）
- 上传后可观察到状态从 `UPLOADED` 进入 `INGESTING`
- 成功文档状态进入 `INDEXED`，可用于后续检索
- 失败文档状态为 `FAILED`，可查看失败原因
- 重复触发处理不会造成向量重复污染
- 瞬时错误在重试次数内可自动恢复，超过次数后进入 `FAILED`
- reprocess 失败时保留错误日志与新 `splitVersion` 记录

## 9. 风险与回滚
- 风险：分块参数不合理导致召回质量差
- 风险：重试策略不当导致重复写入
- 回滚：保留受理闭环，关闭处理 worker 开关，保持系统可用

## 10. 当前实现边界（2026-04-02）
- 已实现：
  - 上传受理幂等（`kbId + fileHash`）
  - 任务启动 `UPLOADED -> INGESTING` 的 CAS 抢占能力
  - 单进程异步 worker（轮询 + 抢占 + 调用处理用例）
  - 处理主链路：源文件读取、Tika 文本解析（禁用嵌入资源）+ 二次清洗、分块、向量写入、状态推进到 `INDEXED/FAILED`
- 尚未实现：
  - 瞬时错误 3 次重试（指数退避 + jitter）
  - reprocess 接口与重建流程
  - OCR 场景与复杂排版优化（如扫描版 PDF、表格结构化提取）
