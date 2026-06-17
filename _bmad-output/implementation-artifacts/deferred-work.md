# Deferred Work

## Deferred from: code review of 1-3-retrieval-parameter-externalization (2026-06-17)

- **应用层 import infrastructure 层** (`AskQuestionApplicationService.java:22` → `QaRetrievalProperties`) — 既存模式，`GetDocumentContentApplicationService` 已有同模式，spec 明确批准此位置。如需修复，需整体重构配置类注入方式（例如通过 domain port 抽象配置接口）。
- **双重默认值来源** — Java 字段默认值（20/4）与 YAML 默认值（`${...:20}`）相同，存在维护不一致风险。建议二选一：只在 Java 设置默认值，YAML 去掉 `:20/:4`；或只在 YAML 占位符设置默认值，Java 字段不设初始值。
