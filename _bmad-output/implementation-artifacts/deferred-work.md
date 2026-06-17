# Deferred Work

## Deferred from: code review of 2-2-scope-filter-builder (2026-06-17)

- [Defer] metadata 常量重复定义（ScopeFilterBuilder 与 PgVectorChunkRetrievalAdapter）— AC#5 明确要求独立定义
- [Defer] scope 列表含 null 元素的 NPE — 与原始 `buildScopeFilter` 行为一致，Java 约定 List 元素非 null
- [Defer] `::int` 强制转换失败风险 — 数据由 ingest 管线写入，非本 Story 范围
- [Defer] SqlScopeCondition 当前无生产调用者 — Story 2.3 前置依赖
