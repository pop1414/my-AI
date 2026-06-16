# Deferred Work

## Deferred from: code review of Epic 1 stories 1-1, 1-2, 1-3 (2026-06-16)

- docker-compose docling-serve 缺少显式 bridge 网络声明 — 默认 bridge 网络满足需求，仅需补充注释说明 [Story 1.1]
- arconia.version 0.20.0 版本兼容性验证 — 已验证编译通过，长期需关注升级 [Story 1.2]
- SmartLifecycle PHASE 值可能与其他 bean 冲突 — 当前无冲突，未来新增高 phase bean 时检查 [Story 1.3]
- 12 个基础设施测试被 @Disabled 无跟踪 — scope 外，需独立 story 跟进重构 [Story 1.3]
- spring.factories FailureAnalyzer 注册机制 — 当前功能正常，Spring Boot 4.x 可能移除支持 [Story 1.3]
- SmartLifecycle stop(Runnable callback) 未重写 — 当前 stop() 已满足需求 [Story 1.3]

## Deferred from: code review of Story 2.2 (2026-06-16)

- 外部部署环境变量 `INGEST_STORAGE_KEEP_RAW_XHTML` / `INGEST_STORAGE_KEEP_CLEANED_HTML` 需同步清理运维文档，否则 Spring Boot 启动会报 unknown property [Story 2.2]
- `docs/architecture/domain/ingest.md:270-271, :458` 数据模型表/实体关系图仍列出已删除字段 `rawXhtml` / `cleanedHtml`，需同步更新架构文档 [Story 2.2]
- `ProcessDocumentApplicationService.java:135` parseResult null 初始化模式，未来扩展时需注意 NPE 风险 [Story 2.2]
- `ProcessDocumentApplicationService.markFailed()` null processingMetadata 测试覆盖缺失 [Story 2.2]
- `DocumentParseResult` 失去 rawXhtml/cleanedHtml 中间产物审计能力 — 设计取舍 [Story 2.2]

## Deferred from: code review of Story 2.3 (2026-06-16)

- MIME type硬编码为"application/octet-stream" — Docling API不提供精确MIME，设计取舍。如有更高精度需求，可在Story 4.1配置化时通过file_ext映射 [Story 2.3]
- mapChunkMetadata()未被主流程调用 — 为Story 3.4（SourceHint → ChunkMetadata切换）预置，当前为package-private dead code [Story 2.3]
- contentType硬编码为PARAGRAPH — AC6要求的Docling原始类型映射由Story 3.4（chunk pipeline集成）完成 [Story 2.3]
- Markdown heading正则匹配代码块内注释行 — title_outline_sample可能含噪声，当前影响仅为metadata质量，非关键路径 [Story 2.3]
