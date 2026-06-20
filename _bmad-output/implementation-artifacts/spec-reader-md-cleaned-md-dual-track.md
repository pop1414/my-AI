---
title: 'reader.md / cleaned.md 双轨正文产物'
type: 'bugfix'
created: '2026-06-18T00:00:00+08:00'
status: 'done'
baseline_commit: '882ac8022a04ee0c0709525cdcdb226df6c33f9c'
context:
  - '{project-root}/docs/project-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/docling-upgrade/architecture.md'
---

<frozen-after-approval reason="human-owned intent – do not modify unless human renegotiates">

## Intent

**Problem:** 当前 ingest 链路把同一份 `cleaned.md` 同时用于管理端正文阅读和 RAG 分块/向量化，导致阅读侧拿到的是为检索清洗过的内容，图片、表格、URL 和原始 Markdown 结构会被主动削弱或删除。现有未提交改动只为 Markdown 源文件增加了回溯读取，但还没有在版本级 artifact 上建立“阅读版正文”和“RAG 版正文”的明确边界。

**Approach:** 在解析结果和 artifact 存储层引入 `reader.md` / `cleaned.md` 双轨正文事实：`reader.md` 面向人类阅读，尽量保留 Docling 原始 Markdown 或原始 Markdown 源文件内容；`cleaned.md` 继续只服务于 chunking、向量化和检索。正文读取服务改为优先返回 `reader.md`，仅对历史数据按“源 Markdown 回溯 -> cleaned.md”降级。

## Boundaries & Constraints

**Always:** 继续保持 `documentChunker.chunk(...)` 和后续向量化只消费 `cleaned.md`；新旧正文 artifact 都必须是版本级存储；Markdown 源文件上传时 `reader.md` 必须保留原始源内容；现有 `CONTENT_NOT_READY` / `CONTENT_ARTIFACT_MISSING` / `CONTENT_TOO_LARGE` 语义不能变化；只改 ingest 后端与直接相关单元测试。

**Ask First:** 如果实现过程中需要新增 REST 字段、修改现有正文读取 API 契约、引入数据库迁移、改变权限语义，或把当前未提交的外部图片代理工作纳入同一次交付，先停下确认。

**Never:** 不把 `reader.md` 用作分块或向量化输入；不继续让阅读侧默认读取 `cleaned.md`；不重写 TextCleaningService 的 RAG 清洗策略；不顺手改前端页面、图片代理、检索策略或其他与双轨正文无关的逻辑。

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 非 Markdown 文档入库 | PDF/DOCX/PPTX/HTML/TXT 等经 Docling 转换 | `reader.md` 保存 Docling 原始 Markdown，`cleaned.md` 保存清洗后 Markdown，分块仍使用 `cleaned.md` | 维持现有解析失败/重试语义 |
| Markdown 文档入库 | 上传 `.md/.markdown/.mdown/.mkd` 源文件 | `reader.md` 保存原始源 Markdown，`cleaned.md` 保存清洗后 Markdown | 维持现有解析失败/重试语义 |
| 新版正文读取 | 目标 version 已有 `reader.md` | latest / askable baseline / explicit version 全部优先返回 `reader.md` | 过大仍映射 `CONTENT_TOO_LARGE` |
| 历史版本正文读取 | 目标 version 缺少 `reader.md` | 对 Markdown 源文件先回溯源文件内容；回溯不到时再退回 `cleaned.md` | 处理中版本仍映射 `CONTENT_NOT_READY`，终态缺失仍映射 `CONTENT_ARTIFACT_MISSING` |

</frozen-after-approval>

## Code Map

- `src/main/java/io/github/spike/myai/ingest/domain/model/DocumentParseResult.java` -- 解析结果值对象，需承载 reader/cleaned 双正文
- `src/main/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParser.java` -- Docling 转换入口，决定 raw Markdown 与 RAG 清洗产物的分流
- `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentProcessingArtifactStorage.java` -- 版本级正文 artifact 端口，需显式声明 `reader.md`
- `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorage.java` -- 本地 artifact 写入/读取实现
- `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorage.java` -- S3 artifact 写入/读取实现
- `src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java` -- 正文落盘与 chunking 编排边界
- `src/main/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationService.java` -- 阅读正文读取优先级与历史降级逻辑
- `src/test/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParserTest.java` -- 双轨解析结果断言
- `src/test/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationServiceTest.java` -- `reader.md` 优先级与历史回退断言
- `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorageTest.java` -- 本地双 artifact 写入/读取断言
- `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorageTest.java` -- S3 双 artifact 写入/读取断言
- `src/test/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationServiceTest.java` -- 编排层继续只把 `cleaned.md` 送入 chunker

## Tasks & Acceptance

**Execution:**
- [x] `src/main/java/io/github/spike/myai/ingest/domain/model/DocumentParseResult.java`, `src/main/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParser.java` -- 扩展解析结果为 `readerMarkdown` + `cleanedMarkdown`，并按文件类型区分“原始阅读正文”与“RAG 清洗正文” -- 在解析边界一次性建立双轨事实，避免后续层猜测
- [x] `src/main/java/io/github/spike/myai/ingest/domain/port/DocumentProcessingArtifactStorage.java`, `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorage.java`, `src/main/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorage.java` -- 新增 `reader.md` 常量与强制落盘逻辑，同时保留 `cleaned.md` 和 `parse-result.json` 现有规则 -- 让版本级 artifact 成为唯一正文事实来源
- [x] `src/main/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationService.java` -- 适配新的解析结果结构，确认落盘写入双正文而 chunker 仍只消费 `cleanedMarkdown()` -- 保证阅读修复不影响 RAG 主链
- [x] `src/main/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationService.java` -- 将读取顺序收敛为 `reader.md -> Markdown 源文件 -> cleaned.md`，并维持现有错误映射 -- 兼容新数据与历史数据
- [x] `src/test/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParserTest.java`, `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorageTest.java`, `src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorageTest.java`, `src/test/java/io/github/spike/myai/ingest/application/service/ProcessDocumentApplicationServiceTest.java`, `src/test/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationServiceTest.java` -- 覆盖双轨正文生成、落盘、优先读取与历史回退场景 -- 防止后续回归重新混淆阅读链路和 RAG 链路

**Acceptance Criteria:**
- Given 非 Markdown 文档完成解析，when 版本 artifact 落盘，then 同一版本下同时存在 `reader.md` 和 `cleaned.md`，且两者分别代表 Docling 原始阅读正文与 RAG 清洗正文。
- Given Markdown 源文件完成解析，when 版本 artifact 落盘，then `reader.md` 保留原始源 Markdown，`cleaned.md` 保留清洗后内容，且两者不会被互相覆盖。
- Given 处理服务完成分块编排，when 文档进入 chunking / indexing，then 下游仍只接收 `cleanedMarkdown()`，不会改用 `reader.md`。
- Given 正文读取命中已完成版本，when `reader.md` 存在，then latest / askable baseline / explicit version 均优先返回 `reader.md`。
- Given 历史版本缺少 `reader.md`，when 该版本原文件是 Markdown，then 服务优先回溯源文件内容；when 回溯不到源文件，then 才降级读取 `cleaned.md`，并保持现有错误码语义不变。

## Design Notes

优先级约定只在读取服务发生，不扩散到其他用例：

```text
write path: parser -> {readerMarkdown, cleanedMarkdown} -> artifact storage
read path: reader.md -> source markdown -> cleaned.md
rag path: cleaned.md -> chunker -> vector indexer
```

这次改动的关键不是“再加一个 fallback”，而是把阅读正文和检索正文在解析结果、artifact 命名和读取顺序上都显式分开；否则未来任何页面或用例都可能再次误读 `cleaned.md`。

## Verification

**Commands:**
- `mvn test "-Dtest=DoclingDocumentParserTest,LocalDocumentProcessingArtifactStorageTest,S3DocumentProcessingArtifactStorageTest,ProcessDocumentApplicationServiceTest,GetDocumentContentApplicationServiceTest"` -- expected: 相关单元测试全部通过

## Suggested Review Order

**读取优先级**

- 先看阅读回退链。
  [`GetDocumentContentApplicationService.java:218`](../../src/main/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationService.java#L218)

- 限制处理中版本回源。
  [`GetDocumentContentApplicationService.java:292`](../../src/main/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationService.java#L292)

- 看源 Markdown 兜底。
  [`GetDocumentContentApplicationService.java:264`](../../src/main/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationService.java#L264)

**解析分轨**

- 入口先分 Markdown。
  [`DoclingDocumentParser.java:119`](../../src/main/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParser.java#L119)

- Docling 输出建双轨。
  [`DoclingDocumentParser.java:193`](../../src/main/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParser.java#L193)

- 原始 Markdown 直存阅读版。
  [`DoclingDocumentParser.java:210`](../../src/main/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParser.java#L210)

- 值对象锁住双正文。
  [`DocumentParseResult.java:19`](../../src/main/java/io/github/spike/myai/ingest/domain/model/DocumentParseResult.java#L19)

**Artifact 落盘**

- 先看新 artifact 常量。
  [`DocumentProcessingArtifactStorage.java:29`](../../src/main/java/io/github/spike/myai/ingest/domain/port/DocumentProcessingArtifactStorage.java#L29)

- 本地存储双写正文。
  [`LocalDocumentProcessingArtifactStorage.java:96`](../../src/main/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorage.java#L96)

- S3 存储保持对齐。
  [`S3DocumentProcessingArtifactStorage.java:66`](../../src/main/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorage.java#L66)

**回归验证**

- 验证处理中 Markdown 不回源。
  [`GetDocumentContentApplicationServiceTest.java:148`](../../src/test/java/io/github/spike/myai/ingest/application/service/GetDocumentContentApplicationServiceTest.java#L148)

- 验证 Markdown 源文件分轨。
  [`DoclingDocumentParserTest.java:107`](../../src/test/java/io/github/spike/myai/ingest/infrastructure/parser/DoclingDocumentParserTest.java#L107)

- 验证本地 artifact 双写。
  [`LocalDocumentProcessingArtifactStorageTest.java:28`](../../src/test/java/io/github/spike/myai/ingest/infrastructure/storage/LocalDocumentProcessingArtifactStorageTest.java#L28)

- 验证 S3 artifact 双写。
  [`S3DocumentProcessingArtifactStorageTest.java:46`](../../src/test/java/io/github/spike/myai/ingest/infrastructure/storage/S3DocumentProcessingArtifactStorageTest.java#L46)
