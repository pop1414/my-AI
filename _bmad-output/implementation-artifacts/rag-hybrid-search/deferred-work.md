# Deferred Work

## Deferred from: code review of 2-2-scope-filter-builder (2026-06-17)

- [Defer] metadata 常量重复定义（ScopeFilterBuilder 与 PgVectorChunkRetrievalAdapter）— AC#5 明确要求独立定义
- [Defer] scope 列表含 null 元素的 NPE — 与原始 `buildScopeFilter` 行为一致，Java 约定 List 元素非 null
- [Defer] `::int` 强制转换失败风险 — 数据由 ingest 管线写入，非本 Story 范围
- [Defer] SqlScopeCondition 当前无生产调用者 — Story 2.3 前置依赖

## Deferred from: PR-fix-qa-hybrid review (2026-06-20)

- [Defer] `QaAsyncConfiguration` 的 `Executor` bean 名称 `virtualThreadExecutor` 通用，未来新增 Executor bean 时有冲突风险 — 可加 `@Bean("qaVirtualThreadExecutor")` + `@Qualifier`
- [Defer] PostgreSQL JDBC driver < 42.6.0 使用 `synchronized` 块会导致虚拟线程 pinning — 需确认 pom.xml 中 PG driver 版本 ≥ 42.6.0
- [Defer] `QaRetrievalProperties` 使用 Lombok `@Getter/@Setter`，与项目"不使用 Lombok"惯例不一致 — `@ConfigurationProperties` 可变类的已知惯例例外
