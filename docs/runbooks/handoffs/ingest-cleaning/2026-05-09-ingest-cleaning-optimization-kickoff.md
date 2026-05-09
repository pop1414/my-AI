# 会话启动包：文档解析清洗优化（ingest-cleaning-optimization）

日期：2026-05-09  
仓库：`D:\Code\project\my-AI`  
建议分支：`feature/ingest-cleaning-optimization`  
来源分支：`feature/auth-security-baseline`

## 1. 分支目标

`ingest-cleaning-optimization` 分支的目标是优化文档处理链路中的**解析、清洗与现有分块质量**，
提升中间文本稳定性、降低噪音、改善后续预览和问答召回质量，
但**不在本分支内展开权限治理接口建设**。

本分支优先解决：

1. 多格式文档解析质量提升
2. 文本清洗规则增强与配置化
3. 现有 chunker 的兼容性优化
4. 大文件场景下的处理中间态与内存风险收口
5. 面向二期节点化的中间产物边界澄清

## 2. 前置状态

当前仓库已经具备以下 ingest 基线能力：

- 上传、异步处理、重试、重处理、删除主链路已打通
- `TikaDocumentTextParser` 已接入 Apache Tika，能够解析常见文档格式
- `TextCleaningService` 已提供基础去噪和空白规范化能力
- `StructuredFallbackDocumentChunker` 已提供结构优先 + 长度兜底的确定性分块能力
- `documents/chunks/preview` 已能输出现有分块预览结果
- `qa.ask` 已接入知识库问答权限和召回后文档授权过滤

这意味着本分支不需要重新搭建 ingest 主流程，而是聚焦“文本进入向量化前”的质量优化。

## 3. 本分支不做什么

- 不实现成员管理、知识库授权、文档授权、审计查询接口
- 不重做认证、Session、CSRF、`401/403` 基线
- 不引入 OIDC、SSO、API Token
- 不把一期纯文本清洗目标直接扩展为完整节点 Schema 落库
- 不引入新的节点模型
- 不引入新的分块策略
- 不把多模态图片理解、表格摘要和正式节点对象生成作为本分支主交付

## 4. 建议实施顺序

### 4.1 解析链路收口

- 审视 `TikaDocumentTextParser` 当前 `parse(String, byte[]) -> String` 路径
- 评估是否需要从“整串字符串常驻内存”逐步演进为 `Path/Reader` 级处理中间态
- 对 PDF、Word、HTML、Markdown 的失败样本建立抽样样本集
- 对扫描件、弱结构 PDF 做质量降级标记，而不是强行伪造结构化结果

### 4.2 清洗规则增强

- 将当前正则去噪扩展为可分层配置的清洗策略
- 优先解决：
  - 图片文件名噪音
  - URL / 本地临时路径噪音
  - 多余分隔线
  - 标题、段落、列表的语义稳定性
- 评估是否引入 `Jsoup + flexmark-java` 作为“HTML 语义清洗 -> Markdown 中间产物”主链方案

### 4.3 现有分块质量优化

- 在不破坏现有 ingest 主链路的前提下，对 `StructuredFallbackDocumentChunker` 做兼容性优化
- 优化目标以“接入 `cleaned.md` 主链”和“适配 `Path/Reader` 输入边界”为主
- 允许提升：
  - 标题边界识别
  - 段落完整性
  - `sourceHint` 的稳定性与可解释性
- 不允许：
  - 引入新的父子块组织模型
  - 引入新的分块策略
  - 在本分支中把 chunker 直接升级为正式节点生成器
- 若优化后需改变预览口径，先保证 `documents/chunks/preview` 回归可验证

### 4.4 中间产物与元数据

- 一期主产物确定为 `cleaned.md`
- `raw.xhtml` / `cleaned.html` 作为调试旁路产物按需保留
- 解析阶段若产出 `parse-result.json`，其正式归宿应为 `ingest_documents.processing_metadata`（JSONB）
- `processing_metadata` 是正式处理记录，不是调试垃圾文件，也不是独立状态查询入口
- `parse-result.json` 只是文件化载体或回放副本，数据库字段才是最终事实来源
- `UPLOADED` / `INGESTING` 阶段允许 `processing_metadata = null`
- `INDEXED` / `FAILED` 阶段可由状态查询接口顺带返回 `processingMetadata`
- 元数据字段应优先保持文档级语义，例如：
  - `file_ext`、`mime_type`
  - `page_count`
  - `primary_title`
  - `title_outline_sample`
- 避免在该字段中重复写入 `failure_reason`、`last_error_code`、`last_error_message` 这类已有错误状态字段

## 5. 与治理接口分支的协作边界

本分支与 `feature/auth-authorization-governance` 可以并行，但需遵守以下边界：

- 本分支不修改成员、授权、审计治理接口
- 本分支不顺手改工作区成员、知识库授权、文档授权的数据库模型
- 本分支若涉及 `documents/chunks/preview`、`qa.ask` 引用口径或 Flyway 迁移，必须先同步影响范围
- 本分支应尽量保持对外 REST 契约稳定，除非文档中已明确需要升级

## 6. 契约与文档更新要求

若本分支改变以下任一内容，需要同步更新文档：

- 中间产物形态（如引入 `cleaned.md`）
- `processing_metadata` 字段结构或返回口径
- `documents/chunks/preview` 返回口径
- 文档解析质量标记或新增解析元数据
- 处理链路中的文件保留、调试产物或回放方式

涉及对外接口时同步更新：

- `docs/04-api-contract.yaml`

涉及方案边界时同步更新：

- `docs/runbooks/plans/ingest-cleaning/RAG 文档解析与清洗方案.md`
- `docs/runbooks/plans/ingest-cleaning/RAG 文档节点标准数据契约.md`

## 7. 测试建议

本分支至少覆盖：

- 多格式文档解析成功与失败降级场景
- 文本清洗后关键噪音被移除
- 标题、段落、列表在清洗后保持可分块语义
- 分块结果在核心样本文档上保持稳定或得到可解释改进
- `documents/chunks/preview` 回归通过
- `qa.ask` 在不修改权限规则前提下继续正常消费优化后的文档内容

## 8. 一句话交接

**`feature/ingest-cleaning-optimization` 专注于文档解析、清洗和现有分块质量优化，不承担成员治理与授权管理接口建设；若涉及中间产物、预览口径或迁移脚本变化，请先同步影响范围再推进。**

## 9. 当前进度快照（2026-05-09）

已完成第一阶段基础收口：

- `ingest_documents.processing_metadata` 已完成数据库字段落地
- ingest schema 自检已纳入 `processing_metadata` 关键列校验
- `GET /api/v1/documents/{documentId}/status` 已支持在 `INDEXED` / `FAILED` 终态返回 `processingMetadata`
- 相关单元测试、接口契约、执行设计文档与发布说明已同步更新

下一步建议：

- 进入 `cleaned.md` 主链改造
- 在处理链路中补齐 `processing_metadata` 的自动回填逻辑
- 开始评估 `Tika -> Jsoup -> flexmark` 的中间产物输出方案
