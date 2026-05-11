# 会话交接包：cleaned.md 主链落地

日期：2026-05-09  
状态同步：2026-05-09  
仓库：`D:\Code\project\my-AI-ingest`  
当前分支：`feature/ingest-cleaned-md-pipeline`

## 1. 本次主题定位

本阶段目标是把 ingest-cleaning 一期从“纯文本直接分块”推进到“带中间产物主链”的实现状态，重点完成：

1. `raw.xhtml -> cleaned.html -> cleaned.md` 主链落地
2. `cleaned.md` 文件写入文档目录
3. `processing_metadata` 从“只有字段和出口”推进到“开始自动生成并回填”

本次不追求一步完成所有复杂解析能力，重点是先把主链和存储边界稳定下来。

## 2. 当前完成概览

### 2.1 已完成：解析端口升级为中间产物结果

- `DocumentTextParser` 不再只返回纯文本字符串
- 新增 `DocumentParseResult`，统一承载：
  - `rawXhtml`
  - `cleanedHtml`
  - `cleanedMarkdown`
  - `processingMetadata`

### 2.2 已完成：cleaned.md 主链落地

- `TikaDocumentTextParser` 已改为输出 XHTML
- `TextCleaningService` 已接入：
  - `Jsoup` 做 HTML 语义清洗
  - `flexmark-html2md-converter` 做 Markdown 转换
- `ProcessDocumentApplicationService` 已改为消费 `cleanedMarkdown`
- 文档目录下已强制写入 `cleaned.md`

### 2.3 已完成：调试产物与文件化元数据载体

- 新增 `LocalDocumentProcessingArtifactStorage`
- 已支持按配置保留：
  - `raw.xhtml`
  - `cleaned.html`
  - `parse-result.json`
- `parse-result.json` 当前写入内容与 `processing_metadata` 保持一致，作为文件化载体存在

### 2.4 已完成：processing_metadata 基础自动回填

- parser 已生成文档级基础元数据：
  - `schema_version`
  - `stable.source_file`
  - `stable.file_ext`
  - `stable.mime_type`
  - `stable.quality`
  - `stable.created_at`
  - 条件性 `language` / `page_count` / `primary_title` / `title_outline_sample`
- `ProcessDocumentApplicationService` 已在成功终态写入 `processing_metadata`
- 若解析成功但后续分块/向量化失败，也会尽量将已有 `processing_metadata` 回填到 `FAILED` 终态

### 2.5 已完成：源文件 fallback 兼容修正

- `LocalDocumentSourceStorage` 的 fallback 读取现在会跳过：
  - `cleaned.md`
  - `raw.xhtml`
  - `cleaned.html`
  - `parse-result.json`

这避免了文档目录里新增中间产物后，历史 fallback 误把中间文件当原始源文件读取的问题。

## 3. 本次涉及的关键文件

后端代码：

- [DocumentParseResult.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/domain/model/DocumentParseResult.java)
- [DocumentTextParser.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/domain/port/DocumentTextParser.java)
- [DocumentProcessingArtifactStorage.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/domain/port/DocumentProcessingArtifactStorage.java)
- [TextCleaningService.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/infrastructure/parser/TextCleaningService.java)
- [TikaDocumentTextParser.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/infrastructure/parser/TikaDocumentTextParser.java)
- [LocalDocumentProcessingArtifactStorage.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorage.java)
- [LocalDocumentSourceStorage.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentSourceStorage.java)
- [ProcessDocumentApplicationService.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java)
- [IngestProperties.java](/D:/Code/project/my-AI-ingest/src/main/java/io/github/spike/myai/ingest/infrastructure/config/IngestProperties.java)
- [application.yaml](/D:/Code/project/my-AI-ingest/src/main/resources/application.yaml)
- [pom.xml](/D:/Code/project/my-AI-ingest/pom.xml)

测试：

- [ProcessDocumentApplicationServiceTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationServiceTest.java)
- [TikaDocumentTextParserTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/infrastructure/parser/TikaDocumentTextParserTest.java)
- [TextCleaningServiceTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/infrastructure/parser/TextCleaningServiceTest.java)
- [LocalDocumentProcessingArtifactStorageTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorageTest.java)
- [LocalDocumentSourceStorageTest.java](/D:/Code/project/my-AI-ingest/src/test/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentSourceStorageTest.java)

文档：

- [03-architecture.md](/D:/Code/project/my-AI-ingest/docs/03-architecture.md)
- [05-release-notes.md](/D:/Code/project/my-AI-ingest/docs/05-release-notes.md)
- [07-ingest-processing-execution.md](/D:/Code/project/my-AI-ingest/docs/07-ingest-processing-execution.md)
- [RAG 文档解析与清洗方案.md](/D:/Code/project/my-AI-ingest/docs/runbooks/plans/ingest-cleaning/RAG%20%E6%96%87%E6%A1%A3%E8%A7%A3%E6%9E%90%E4%B8%8E%E6%B8%85%E6%B4%97%E6%96%B9%E6%A1%88.md)
- [2026-05-09-ingest-cleaning-optimization-kickoff.md](/D:/Code/project/my-AI-ingest/docs/runbooks/handoffs/ingest-cleaning/2026-05-09-ingest-cleaning-optimization-kickoff.md)

## 4. 已完成验证

已执行：

- `.\\mvnw.cmd -q test`

验证结论：

- 新增 `Jsoup + flexmark` 依赖后，项目可正常构建
- parser 中间产物链路可通过单测
- `cleaned.md` 与调试产物文件落地逻辑可通过单测
- 源文件 fallback 不会误读中间产物
- 处理主链在 parser 端口升级后仍可通过全量测试

## 5. 当前边界与剩余缺口

当前已完成的是“一期主链可运行版本”，还没有完成所有质量优化目标。

当前仍未完成：

- 更精细的文档质量分级策略（如扫描件、弱结构 PDF）
- 更稳定的页码提取与标题层级提取
- `documents/chunks/preview` 针对 `cleaned.md` 主链的专项行为优化验证
- `qa.ask` 基于新清洗结果的召回质量专项回归
- `Path/Reader` 级接口改造，当前仍以字符串结果为主链在应用层传递

## 6. 下一步建议

建议从当前分支合回 `feature/ingest-cleaning-optimization` 后，继续切下一阶段分支，例如：

- `feature/ingest-chunker-preview-regression`

下一阶段优先顺序：

1. 针对 `StructuredFallbackDocumentChunker` 做 `cleaned.md` 语义适配
2. 对 `documents/chunks/preview` 做回归和必要优化
3. 对 `qa.ask` 做基于新清洗链路的结果验证
4. 继续补强 `processing_metadata` 字段质量与提取稳定性

## 7. 一句话交接

**`feature/ingest-cleaned-md-pipeline` 已完成 `raw.xhtml -> cleaned.html -> cleaned.md` 主链落地、中间产物文件保存、基础 processing_metadata 自动回填和源文件 fallback 修正；下一步应转入 chunker、preview 与 qa.ask 的兼容回归，而不是继续只做 parser 内部重构。**
