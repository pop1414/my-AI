# 会话交接包：ingest-cleaning 纯文字阶段收口

日期：2026-05-14

状态：纯文字解析与清洗优化链路已按当前代码实现收口；本文作为后续图片、表格、OCR 与 richer node model 优化前的上下文基线

## 1. 本次主题定位

本阶段已经完成的是 `ingest` 主链中“进入 chunking 前的纯文字文本基底”优化。

当前主链事实：

`源文件读取 -> 文件类型路由 -> 解析与清洗 -> cleaned.md 落盘 -> 结构优先分块 -> 向量写入 -> INDEXED/FAILED 状态收口`

本阶段不声称已经解决：

- 图片理解
- 表格结构化节点
- OCR 与复杂扫描件稳定支持
- 父子分块
- richer retrieval / reference DTO
- 向量 metadata shape 升级

## 2. 当前代码事实

### 2.1 parser routing

入口实现：`TikaDocumentTextParser`

- `md` / `markdown` / `mdown` / `mkd`：进入原生 Markdown 最小破坏路径
- `html` / `htm`：进入原生 HTML 清洗路径
- 其他格式：进入 Tika 通用路径
- 原生文本严格解码失败时，回退 Tika 让其执行字符集检测

### 2.2 Tika 通用路径

适用：PDF、Word、Excel 等复杂格式。

处理顺序：

1. Apache Tika 自动检测并输出 XHTML
2. `HtmlSemanticCleaner` 删除脚本、样式、导航、侧栏、页脚、注释和表现型属性
3. Word 常见 `MsoTitle` / `MsoHeading*` 段落映射为标题标签
4. 图片转换为 `[图片]` 或 `[图片: alt]` 占位文本
5. `HtmlToMarkdownRenderer` 使用 flexmark 转为 Markdown
6. `MarkdownTextCleaner` 与 `MarkdownStructureRepairer` 处理行级噪音、表格顺序、软换行和标题粘连

### 2.3 原生 Markdown 路径

目标是保留作者原始结构，不再把 Markdown 送进 Tika / HTML 转换链。

当前保留重点：

- ATX / Setext 标题
- 围栏代码块和缩进代码块
- Markdown 表格
- 列表缩进
- 代码块中的 HTML 示例
- 正文内图片 URL / file URL

当前清理重点：

- BOM、不可见格式字符和控制字符
- 明显页面噪音
- 独立图片文件名行、独立图片 URL 行、独立 file URL 行
- 代码块外的危险 raw HTML 块

### 2.4 原生 HTML 路径

目标是绕过 Tika 对 HTML 的无谓二次转换。

当前保留重点：

- 主正文标题、段落、列表和表格可读形态
- 可进入 `sourceHint` 的正文标题上下文

当前清理重点：

- `nav`、`aside`、`footer`
- `script`、`style`、`noscript`、`link`、`meta`
- `iframe`、`object`、`embed`、`applet`
- HTML 注释与样式类属性

### 2.5 processingMetadata

`processingMetadata` 当前仍是文档级处理结果元数据，不是节点契约。

基础结构由 `ProcessingMetadataBuilder` 生成：

- `schema_version`
- `stable.source_file`
- `stable.file_ext`
- `stable.mime_type`
- `stable.quality`
- `stable.created_at`
- 条件性 `language`
- 条件性 `page_count`
- 条件性 `primary_title`
- 条件性 `title_outline_sample`

`ProcessDocumentApplicationService` 在成功终态写入 `markIndexed`；如果解析成功但后续失败，会尽量在 `markFailed` 时带上已生成的 `processingMetadata`。

## 3. 中间产物策略

正式主链产物：

- `cleaned.md`：强制写入 `{rootDir}/{documentId}/cleaned.md`

可配置调试产物：

- `raw.xhtml`
- `cleaned.html`
- `parse-result.json`

默认配置含义：

- `cleaned.md` 不受配置影响，永远写入
- `raw.xhtml` 默认不保留
- `cleaned.html` 默认不保留
- `parse-result.json` 默认保留，用于文件化查看当前 `processingMetadata`

## 4. 分块与 sourceHint 当前行为

当前分块器：`StructuredFallbackDocumentChunker`

实现路径：

1. `MarkdownSegmenter` 将 `cleaned.md` 按空行切成结构片段，并按空白分隔为近似 token
2. `HeadingContextExtractor` 识别 Markdown 标题、中文编号标题、数字标题和清洗后独立短标题
3. `ChunkWindowAssembler` 按 `chunkSize` / `overlapSize` 组装窗口，并把标题上下文写入 `SourceHint.heading(...)`

当前 `sourceHint` 主要形态：

```json
{"heading":"标题文本"}
```

它只用于解释 chunk 来源，不是最终 RAG 节点 metadata，也不是对外 reference DTO 的扩展依据。

## 5. 黄金样本与回归基线

当前固定样本顺序：

1. `weak-pdf-001`
2. `md-001`
3. `md-002`
4. `html-001`
5. `word-001`

权威 runbook：

- `docs/runbooks/plans/ingest-cleaning/黄金样本与验收说明.md`
- `docs/runbooks/plans/ingest-cleaning/cleaned-md质量回归闭环.md`

自动化覆盖：

- `IngestCleaningGoldenSamplesTest`
- `TikaDocumentTextParserTest`
- `TextCleaningServiceTest`
- `ProcessingMetadataBuilderTest`
- `StructuredFallbackDocumentChunkerTest`

人工验收仍按三面记录：

- `cleaned.md`
- `GET /api/v1/documents/{documentId}/chunks/preview`
- `POST /api/v1/qa/ask`

## 6. 后续优化边界

后续如果继续优化图片、表格、OCR 或节点模型，应先明确是否仍属于 parser / cleaner 内部质量增强，还是已经越过当前契约边界。

默认安全增强：

- 改进图片 alt / caption 的文字保留
- 改进 Markdown 表格可读性
- 增加黄金样本与回归断言
- 优化 `cleaned.md` 内部文本质量
- 优化不改变对外契约的 `sourceHint` 可解释性

需要先同步设计的增强：

- 图片 OCR 或视觉理解结果入库
- 表格作为独立 node / table block 持久化
- 父子分块
- `vector_store.metadata` shape 变更
- `qa.ask` reference DTO 增字段
- `processingMetadata` 从文档级处理结果升级为节点级契约

## 7. 一句话交接

**当前 ingest-cleaning 已完成纯文字阶段：文件类型路由、Tika/HTML/Markdown 清洗、`cleaned.md` 强制落盘、基础 `processingMetadata` 回填、结构优先分块与黄金样本回归闭环都已落地；下一轮应围绕图片、表格、OCR 或 richer node model 重新划定契约边界，而不是继续把它们混在 parser 清洗小改里推进。**
