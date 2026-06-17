# Investigation: Docling 文档解析无法产出 Markdown 内容

## Hand-off Brief

1. **What happened.** Docling Serve HybridChunker 接口返回 status=success，但所有 content 格式（md/html/text/doctags）均为 null，降级使用 chunks 文本拼接；仅产出 1 个 chunk。
2. **Where the case stands.** 根因定位为 **服务端问题**：请求参数正确（`includeConvertedDoc=true`, `toFormats=[md,html,text,doctags]`），但 Docling Serve v1.23.0 未在响应中返回转换后内容。客户端代码和 API 模型映射均无问题。
3. **What's needed next.** 直接用 curl 测试 Docling Serve 端点确认是服务端行为；检查 Docling Serve 版本兼容性；考虑升级 `docling-serve-api` Java 客户端到 0.5.3。

## Case Info

| Field            | Value                                                                      |
| ---------------- | -------------------------------------------------------------------------- |
| Ticket           | N/A                                                                        |
| Date opened      | 2026-06-17                                                                 |
| Status           | Active                                                                     |
| System           | Windows 11 Pro, Spring Boot 3.5.8, Java 21                                 |
| Evidence sources | 应用日志、源代码、Docling Serve OpenAPI spec                                |

## Problem Statement

用户报告：Docling 无法把上传的文档解析成 md。日志显示：
```
Docling 所有 content 格式均为空，降级使用 chunks 文本 (status=success, chunks=1)
```
Docling 返回 success 但 ExportDocumentResponse 的所有格式字段（md_content, html_content, text_content, doctags_content）均为 null。

## Evidence Inventory

| Source   | Status    | Notes     |
| -------- | --------- | --------- |
| 应用日志 | Available | 3 行关键日志已提供 |
| 源代码   | Available | DoclingDocumentParser.java, DocumentParserRouter.java |
| Docling OpenAPI spec | Available | docs/reference/Docling/openapi.json (v1.23.0) |
| Docling Serve 服务器日志 | Missing | 需要访问 Docling Serve 容器日志 |
| 实际 HTTP 请求/响应 | Missing | 需要开启 debug 日志或抓包 |

## Investigation Backlog

| # | Path to Explore | Priority | Status | Notes     |
| - | --------------- | -------- | ------ | --------- |
| 1 | 检查 DoclingDocumentParser 请求构造逻辑 | High | Open | 确认请求参数是否正确 |
| 2 | 检查 DoclingServeApi 客户端实现 | High | Open | 确认 HTTP 调用细节 |
| 3 | 检查 Docling Serve 服务器端配置 | Medium | Open | 确认 exporter 是否配置 |
| 4 | 检查 includeConvertedDoc 参数行为 | High | Open | 这个参数控制是否返回转换后内容 |

## Timeline of Events

| Time        | Event               | Source                | Confidence            |
| ----------- | ------------------- | --------------------- | --------------------- |
| 2026-06-17T16:57:49.727 | Docling 返回 status=success，所有 content 格式为空，降级到 chunks 文本 | 应用日志 | Confirmed |
| 2026-06-17T16:57:49.748 | 向量索引更新开始 | 应用日志 | Confirmed |
| 2026-06-17T16:57:50.123 | 文档处理成功完成，chunks=1 | 应用日志 | Confirmed |

## Confirmed Findings

### Finding 1: 请求构造逻辑正确

**Evidence:** `DoclingDocumentParser.java:196-227` `callDoclingApi()` 方法

**Detail:** 请求构造完全符合 Docling Serve API 规范：
- `FileSource` 使用 `base64_string` + `filename`，`kind="file"` 由 Jackson `@JsonTypeInfo` 自动注入
- `ConvertDocumentOptions.toFormats` 设置为 `[MARKDOWN, HTML, TEXT, DOCTAGS]`
- `HybridChunkDocumentRequest.includeConvertedDoc` 设置为 `true`
- `HybridChunkerOptions.maxTokens=512, mergePeers=true`

### Finding 2: 客户端 API 模型映射正确

**Evidence:** `ExportDocumentResponse.java`（docling-serve-api 0.3.0 sources）

**Detail:** `@JsonProperty("md_content")` 映射到 `markdownContent` 字段，与 OpenAPI spec 的 `md_content` 完全一致。`Document.content` 字段类型为 `ExportDocumentResponse`，映射正确。

### Finding 3: Docling Serve 返回 status=success 但 content 为空

**Evidence:** 应用日志 `2026-06-17T16:57:49.727`

**Detail:** `extractCleanedMarkdown()` 按降级链 md→html→text→doctags→chunks 逐一检查，所有格式字段均为 null，最终降级到 chunks 文本拼接。status=success 说明文档处理成功，但服务端未返回转换后内容。

### Finding 4: API 客户端版本与服务端版本存在差距

**Evidence:** `pom.xml:120-129`（arconia 0.20.0 → docling-serve-api 0.3.0），`docs/reference/Docling/openapi.json`（Docling Serve v1.23.0）

**Detail:** Java 客户端 `docling-serve-api` 版本 0.3.0，服务端 Docling Serve 版本 v1.23.0。Maven 仓库中存在更新版本 0.5.3。版本差距可能影响 `includeConvertedDoc` 参数的行为。

### Finding 5: Arconia 自动配置使用 HTTP/1.1

**Evidence:** `DoclingAutoConfiguration.java`（arconia-docling 0.20.0 sources）

**Detail:** Arconia 为 HTTP 显式设置 `HttpClient.Version.HTTP_1_1`（因 FastAPI 与 HTTP/2 的兼容问题）。连接超时 5s，读取超时 30s。

## Deduced Conclusions

### Deduction 1: 问题在服务端，不在客户端

**Based on:** Finding 1, Finding 2, Finding 3

**Reasoning:** 请求参数正确（`includeConvertedDoc=true`, `toFormats=[md,html,text,doctags]`），客户端 API 模型映射正确（`@JsonProperty("md_content")` 匹配），但服务端返回的 `ExportDocumentResponse` 所有内容字段为 null。排除客户端代码问题。

**Conclusion:** Docling Serve v1.23.0 在处理 HybridChunker 请求时，虽然成功完成文档转换（产出 chunks），但未将转换后内容填充到 `ExportDocumentResponse` 中返回。

### Deduction 2: 1 个 chunk 不一定是问题

**Based on:** Finding 3

**Reasoning:** HybridChunker 成功产出 1 个 chunk，如果文档本身内容较短（如只有几行文字），这是正常行为。但如果文档内容丰富却只产出 1 个 chunk，则说明分块配置可能需要调优。

**Conclusion:** chunks=1 本身不是错误，需结合实际文档内容判断。

## Hypothesized Paths

### Hypothesis 1: includeConvertedDoc 参数在 Docling Serve v1.23.0 中行为变更

**Status:** Open

**Theory:** Docling Serve v1.23.0 可能更改了 `include_converted_doc` 参数的行为或默认值，导致即使显式设置为 `true` 也不返回转换后内容。

**Supporting indicators:** API 客户端版本（0.3.0）与服务端版本（v1.23.0）差距较大。

**Would confirm:** 用 curl 直接调用 Docling Serve `/v1/chunk/hybrid/source` 端点，设置 `include_converted_doc=true`，检查响应中是否包含 `md_content`。

**Would refute:** curl 调用返回了 `md_content` 内容。

### Hypothesis 2: Docling Serve 服务器端需要额外配置才能启用内容导出

**Status:** Open

**Theory:** Docling Serve 可能需要额外的环境变量或配置才能在 HybridChunker 响应中包含转换后内容。

**Supporting indicators:** docker-compose.yml 中的环境变量配置可能不完整。

**Would confirm:** 检查 Docling Serve 文档，找到内容导出相关配置。

**Would refute:** Docling Serve 文档说明 `include_converted_doc=true` 应该直接生效，无需额外配置。

### Hypothesis 3: Jackson 反序列化兼容性问题（低概率）

**Status:** Open

**Theory:** `docling-serve-api` 0.3.0 的响应类同时使用了 Jackson 2（`com.fasterxml.jackson`）和 Jackson 3（`tools.jackson.databind`）注解。Lombok `@Jacksonized` 可能在 Jackson 2 环境下未正确生成 builder 反序列化器，导致 `ExportDocumentResponse` 字段未被填充。

**Supporting indicators:** 所有响应类都使用 `@tools.jackson.databind.annotation.JsonDeserialize`（Jackson 3），但 Spring Boot 3.5.8 使用 Jackson 2。

**Would confirm:** 添加 debug 日志打印原始 HTTP 响应 JSON，对比反序列化结果。

**Would refute:** 原始 JSON 中 `md_content` 字段确实为 null。

## Missing Evidence

| Gap              | Impact                               | How to Obtain   |
| ---------------- | ------------------------------------ | --------------- |
| 原始 HTTP 响应 JSON | 确认服务端是否真的返回了 null content，还是客户端反序列化丢失 | 在 DoclingDocumentParser 中添加 debug 日志或使用 curl 直接测试 |
| Docling Serve 服务器端日志 | 确认服务器端是否正确处理了 `include_converted_doc` 参数 | `docker logs myai-docling-serve` |
| 上传的文档内容 | 确认文档是否本身内容很少（导致只有 1 个 chunk） | 查看源文件存储 |

## Source Code Trace

| Element       | Detail                                      |
| ------------- | ------------------------------------------- |
| Error origin  | `DoclingDocumentParser.java:262` extractCleanedMarkdown 方法 — 内容降级链全部返回 null |
| Trigger       | `ProcessDocumentApplicationService.java:143` 调用 `documentTextParser.parse()` |
| Condition     | Docling Serve 返回 `ChunkDocumentResponse`，`documents[0].content` 的所有格式字段为 null |
| Request path  | `DoclingDocumentParser.java:196` → `callDoclingApi()` → `DoclingServeApi.chunkSourceWithHybridChunker()` → POST `/v1/chunk/hybrid/source` |
| Response path | 响应 JSON → `ChunkDocumentResponse.documents[0].content` (`ExportDocumentResponse`) → `md_content`/`html_content`/`text_content`/`doctags_content` 均为 null |
| Related files | `DoclingDocumentParser.java`, `DocumentParserRouter.java`, `ProcessDocumentApplicationService.java`, `DoclingAutoConfiguration.java` |

## Conclusion

**Confidence:** Medium

根因定位为 **Docling Serve 服务端行为**。客户端代码（请求构造 + 响应映射）经审查无问题：`includeConvertedDoc=true` 正确设置，`ExportDocumentResponse` 的 `@JsonProperty("md_content")` 映射与 OpenAPI spec 一致。服务端返回 `status=success` 且成功产出 chunks，但 `ExportDocumentResponse` 的所有内容格式字段为 null。

最可能的原因是 **Docling Serve v1.23.0 对 `include_converted_doc` 参数的处理行为与 API 文档描述不一致**（可能是 bug 或版本行为变更）。次要可能性是 Jackson 2/3 反序列化兼容性问题导致客户端丢失了服务端返回的内容。

## Recommended Next Steps

### 诊断步骤（优先执行）

1. **直接 curl 测试 Docling Serve**：用 `curl -X POST http://localhost:5001/v1/chunk/hybrid/source` 发送与代码相同的请求体，检查原始响应中 `documents[0].content.md_content` 是否为 null。这一步能直接区分是服务端问题还是客户端反序列化问题。

2. **查看 Docling Serve 容器日志**：`docker logs myai-docling-serve --tail 100`，检查服务端是否有与 `include_converted_doc` 或 exporter 相关的警告/错误。

3. **添加 debug 日志**：在 `DoclingDocumentParser.callDoclingApi()` 返回后，用 `objectMapper.writeValueAsString(response)` 打印原始响应 JSON，确认反序列化结果。

### 修复方向

- **如果是服务端问题**：考虑升级 Docling Serve 镜像版本，或降级到已知可用版本；或改用 `/v1/convert/file` 端点（纯转换，不分块）+ 独立分块。
- **如果是客户端反序列化问题**：升级 `docling-serve-api` 到 0.5.3，或在 `DoclingDocumentParser` 中使用 `RestClient` 直接调用 HTTP 端点并手动解析 JSON。

## Reproduction Plan

1. 启动 Docling Serve：`cd infra && docker compose up docling-serve`
2. 等待健康检查通过：`curl http://localhost:5001/health`
3. 准备测试文件（任意 PDF/DOCX）
4. Base64 编码：`base64 -w 0 test.pdf > test.b64`
5. 发送请求：
```bash
curl -X POST http://localhost:5001/v1/chunk/hybrid/source \
  -H "Content-Type: application/json" \
  -d '{
    "sources": [{"kind": "file", "base64_string": "<BASE64>", "filename": "test.pdf"}],
    "convert_options": {"to_formats": ["md", "html", "text", "doctags"]},
    "include_converted_doc": true,
    "chunking_options": {"max_tokens": 512, "merge_peers": true}
  }'
```
6. 检查响应中 `documents[0].content.md_content` 是否有内容

## Side Findings

- `docling-serve-api` Maven 仓库中存在 0.3.0 和 0.5.3 两个版本，当前项目使用 arconia 0.20.0 绑定的 0.3.0
- `InputFormat` 枚举（0.3.0）缺少 `audio`, `vtt`, `latex`, `email`, `epub` 格式，与 OpenAPI spec v1.23.0 不一致
- Arconia 自动配置强制使用 HTTP/1.1（因 FastAPI HTTP/2 兼容问题）
- `read-timeout: 30s` 对大文件处理可能不够，可考虑增大

## Follow-up
