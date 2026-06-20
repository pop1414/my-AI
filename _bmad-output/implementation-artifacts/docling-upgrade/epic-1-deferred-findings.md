# Epic 1 Deferred Review Findings — 跟踪清单

**来源：** Epic 1 回顾（epic-1-retro-2026-06-17.md）行动项 T1
**创建日期：** 2026-06-17
**负责人：** Charlie

---

## 跟踪说明

以下 review 发现在 Epic 1 开发过程中被标记为 deferred。本文件确保这些项不被遗忘，每个项都有明确的状态和处置计划。

---

## #1 — docker-compose bridge 网络声明

- **来源：** Story 1.1, Line 114
- **内容：** `docker-compose docling-serve 缺少显式 bridge 网络声明`
- **原始处置：** deferred，默认 bridge 网络满足需求，仅需补充注释
- **当前状态：** ✅ 无需处理 — 默认 bridge 网络行为正确，显式声明是可选的文档改进
- **结论：** 关闭。如未来 docker-compose 网络拓扑复杂化再重新评估。

---

## #2 — 集成测试 profile 覆盖缺失

- **来源：** Story 1.2, Line 203 `[ ]`（未关闭）
- **内容：** `缺少集成测试 profile 覆盖，arconia 配置变更导致 @SpringBootTest 回归`
- **原始处置：** deferred: 与 P7（Verifier 重试机制）合并处理
- **当前状态：** ⏳ 待处理 — DoclingStartupVerifier 的重试机制已实现（Story 1.3），但集成测试 profile 仍缺失
- **影响：** `@SpringBootTest` 测试在无 docling-serve 环境下可能失败
- **建议：** 为集成测试创建 `test` profile，mock 或禁用 Docling 连接。在 Epic 3 完成后统一处理。
- **目标完成：** Epic 3 结束前

---

## #3 — Arconia 版本兼容性

- **来源：** Story 1.2, Line 204
- **内容：** `arconia.version 0.20.0 版本兼容性`
- **原始处置：** deferred，已验证编译通过
- **当前状态：** ✅ 已验证 — 0.20.0 与 Spring Boot 3.5.8 编译和运行时均兼容
- **结论：** 关闭当前项。但 Arconia 0.20.0 是较旧版本，长期升级需求记录为 Epic 1 回顾 T2 行动项（Low priority）。

---

## #4 — SmartLifecycle PHASE 值冲突

- **来源：** Story 1.3, Line 254
- **内容：** `SmartLifecycle PHASE 值可能与其他 bean 冲突`
- **原始处置：** deferred，当前无冲突
- **当前状态：** ✅ 无冲突 — `Integer.MAX_VALUE - 100` 足够大，当前无其他 SmartLifecycle Bean 使用相近 PHASE
- **结论：** 关闭。如有新 SmartLifecycle Bean 引入，需检查 PHASE 值分配。

---

## #5 — 12 个基础设施测试 @Disabled 无跟踪

- **来源：** Story 1.3, Line 255
- **内容：** `12 个基础设施测试被 @Disabled 无跟踪`
- **原始处置：** deferred，scope 外
- **当前状态：** ⏳ 待处理 — 这些测试在 Epic 1 之前就已存在，与本次迁移无直接关系
- **影响：** 测试套件中有 `@Disabled` 测试未说明原因，可能存在遗忘的测试债务
- **建议：** 审查所有 `@Disabled` 测试，为每个添加 `@Disabled("原因 + TODO(issue)")` 或删除。可在 Epic 3 清理阶段一并处理。
- **目标完成：** Epic 3 结束前

---

## #6 — spring.factories 机制选择

- **来源：** Story 1.3, Line 256
- **内容：** `spring.factories 机制选择`
- **原始处置：** deferred，不影响功能
- **当前状态：** ✅ 无需处理 — Spring Boot 3.x 推荐 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，但 `spring.factories` 对 FailureAnalyzer 仍为官方推荐路径
- **结论：** 关闭。当前实现正确，仅在 Spring Boot 未来版本废弃 spring.factories 时需要迁移。

---

## 汇总

| # | 发现 | 状态 | 是否需要行动 |
|---|------|------|-------------|
| 1 | bridge 网络声明 | ✅ 关闭 | 否 |
| 2 | 集成测试 profile | ⏳ 待处理 | **是** — Epic 3 前 |
| 3 | Arconia 版本兼容性 | ✅ 关闭 | 否（长期升级见 T2） |
| 4 | SmartLifecycle PHASE | ✅ 关闭 | 否 |
| 5 | @Disabled 测试无跟踪 | ⏳ 待处理 | **是** — Epic 3 前 |
| 6 | spring.factories 机制 | ✅ 关闭 | 否 |

**待处理项：2 个**（#2 集成测试 profile、#5 @Disabled 测试审计），均计划在 Epic 3 阶段解决。
