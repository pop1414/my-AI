# 调查：Docling 响应无 Markdown Content

## Hand-off Brief

1. **发生了什么。** 文档上传成功后，Docling Serve 返回的 `Document` 对象 `markdownContent` 为 null，`extractCleanedMarkdown()` 第 250 行抛出 `IllegalStateException`。（**Confirmed** — 栈帧直接指向该行）
2. **案件状态。** 根因已定位并修复。Concluded。
3. **下一步。** 修复已完成，25 个测试全部通过。

## Case Info

| Field            | Value                                                                      |
| ---------------- | -------------------------------------------------------------------------- |
| Ticket           | N/A                                                                        |
| Date opened      | 2026-06-17                                                                 |
| Status           | Concluded                                                                  |
| System           | Windows 11, Java 21, Spring Boot 3.5.8, docling-serve-api 0.3.0           |
| Evidence sources | 应用日志、源代码、单元测试、docling-serve-api JAR                         |

## Problem Statement

用户报告：文件上传没问题，docling 解析与清洗出现问题。日志显示 `IllegalStateException: docling response document has no markdown content`。

## Evidence Inventory

| Source                          | Status    | Notes                                      |
| ------------------------------- | --------- | ------------------------------------------ |
| 应用日志 (stack trace)          | Available | 完整栈帧，指向 DoclingDocumentParser:250   |
| DoclingDocumentParser.java      | Available | 问题代码所在                               |
| TikaDocumentTextParser.java     | Available | 路由层，DOCLING 分支委托给 DoclingDocumentParser |
| DoclingDocumentParserTest.java  | Available | 25 个测试用例，覆盖各种响应场景            |
| docling-serve-api 0.3.0 sources | Available | 响应模型类定义                             |

## Confirmed Findings

### Finding 1: Status 检查顺序缺陷导致错误信息丢失根因

**Evidence:** `DoclingDocumentParser.java:249-254`（修复前）

**Detail:** 修复前代码先检查 `markdownContent == null`（第 249 行），再检查 `status == "error"`（第 253 行）。当 Docling 转换失败时（status = "failure"/"error"），markdownContent 为 null 是预期行为，但代码先命中 null 检查就抛出含糊的 "no markdown content" 异常，永远到不了 status 检查。

### Finding 2: Status 检查只覆盖 "error"，未覆盖 "failure"

**Evidence:** `DoclingDocumentParser.java:253`（修复前）— `if ("error".equalsIgnoreCase(document.getStatus()))`

**Detail:** Docling Serve 的 `Document.status` 有 4 个值：`"success"`, `"failure"`, `"partial"`, `"error"`。原代码只判断了 `"error"`，遗漏了 `"failure"`。

## Fix Applied

**File:** `DoclingDocumentParser.java:249-258`

**Changes:**
1. **调换检查顺序** — status 非正常检查移到 markdownContent null 检查之前
2. **扩展 status 检查** — 从只检查 `"error"` 改为检查 `"error"` || `"failure"`
3. **增强错误信息** — 异常消息包含 `status` 值和 `errors` 列表详情
4. **新增 `formatDocumentErrors()` 辅助方法** — 格式化 Docling 错误列表

**Test changes:**
- 更新 `parse_shouldThrowException_whenDocumentStatusError` 断言消息格式
- 新增 `parse_shouldThrowExceptionWithStatusDetail_whenDocumentStatusFailure` 测试用例

## Conclusion

**Confidence:** High

根因确认为代码缺陷：`extractCleanedMarkdown()` 方法的防御性检查顺序错误，导致 Docling 转换失败时只能报出含糊的 "no markdown content" 而非真正的失败原因。修复后，Docling 返回 failure/error 状态时会抛出包含 status 和 errors 详情的异常，便于后续排查。25 个单元测试全部通过。
