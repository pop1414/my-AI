# Deferred Work

## Deferred from: code review of Epic 1 stories 1-1, 1-2, 1-3 (2026-06-16)

- docker-compose docling-serve 缺少显式 bridge 网络声明 — 默认 bridge 网络满足需求，仅需补充注释说明 [Story 1.1]
- arconia.version 0.20.0 版本兼容性验证 — 已验证编译通过，长期需关注升级 [Story 1.2]
- SmartLifecycle PHASE 值可能与其他 bean 冲突 — 当前无冲突，未来新增高 phase bean 时检查 [Story 1.3]
- 12 个基础设施测试被 @Disabled 无跟踪 — scope 外，需独立 story 跟进重构 [Story 1.3]
- spring.factories FailureAnalyzer 注册机制 — 当前功能正常，Spring Boot 4.x 可能移除支持 [Story 1.3]
- SmartLifecycle stop(Runnable callback) 未重写 — 当前 stop() 已满足需求 [Story 1.3]
