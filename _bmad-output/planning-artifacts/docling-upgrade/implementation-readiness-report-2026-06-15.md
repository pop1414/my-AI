---
stepsCompleted: ['step-01-document-discovery', 'step-02-prd-analysis', 'step-03-epic-coverage-validation', 'step-04-ux-alignment', 'step-05-epic-quality-review', 'step-06-final-assessment']
date: 2026-06-15
project_name: my-AI docling-upgrade
---

# Implementation Readiness Assessment Report

**Date:** 2026-06-15
**Project:** my-AI docling-upgrade

## Document Inventory

### PRD Documents
- `prd.md` (24,209 B, 2026-06-15 22:36) ✅

### Architecture Documents
- `architecture.md` (23,905 B, 2026-06-15 22:36) ✅

### Epics & Stories Documents
- `epics.md` (17,875 B, 2026-06-15 22:36) ✅

### UX Design Documents
- ⚠️ 未找到 — 评估中将标注缺失

### Decision Log
- `.decision-log.md` (3,619 B, 2026-06-15 22:36)

### Issues
- 无重复文档
- UX 设计文档缺失（影响评估完整性）

## PRD Analysis

### Functional Requirements

| FR | 特性 | 需求描述 | 依赖 |
|----|------|---------|------|
| FR-1 | 4.1 基础设施 | Docling Serve 容器编排：docker-compose.yml 新增 docling-serve 服务，含 health check 和启动依赖链 | — |
| FR-2 | 4.1 基础设施 | Arconia Docling 依赖引入：引入 arconia-bom + arconia-docling-spring-boot-starter + arconia-dev-services-docling (test) | FR-1 |
| FR-13 | 4.1 基础设施 | Actuator Health 暴露 Docling 连通性（Arconia 自动配置） | FR-1 |
| FR-3 | 4.2 统一解析 | DoclingDocumentParser 实现：新建 DoclingDocumentParser 调用 DoclingServeApi，8 种格式可产出非空 DocumentParseResult | FR-2 |
| FR-4 | 4.3 路由+域模型 | DocumentParserRouter 重构：删除 TIKA 路由，新增 DOCLING 和 REJECT 路由 | FR-3 |
| FR-5 | 4.3 路由+域模型 | DocumentParseResult 字段简化：删除 rawXhtml/cleanedHtml 字段 | FR-3 |
| FR-6 | 4.3 路由+域模型 | ChunkMetadata 值对象：替代 SourceHint，含 headings、pageNumber、contentType | FR-3 |
| FR-9 | 4.4 遗留清理 | Tika 全量移除：代码 + Maven 依赖 + 配置 | FR-4 |
| FR-10 | 4.4 遗留清理 | Java 侧 Chunker 全量移除：StructuredFallbackDocumentChunker、MarkdownSegmenter、HeadingContextExtractor、ChunkWindowAssembler | FR-4 |
| FR-11 | 4.4 遗留清理 | DocumentChunker 端口保留，实现切换为 DoclingDocumentChunker | FR-10 |
| FR-7 | 4.5 可观测 | HybridChunker 参数配置化：max_tokens 和 merge_peers 从 application.yaml 读取 | FR-3 |
| FR-8 | 4.5 可观测 | 观测指标埋点：docling.parse.duration、docling.parse.errors、docling.chunk.count | FR-3 |
| FR-12 | 4.5 可观测 | 黄金样本重建：旧 Tika 基线删除，以 Docling 为基线重建 5 个样本 | FR-3 |

**Total FRs: 13**

### Non-Functional Requirements

| ID | 类型 | 需求 | 指标 | 优先级 |
|----|------|------|------|--------|
| NFR-1 | 性能 | 解析延迟：Docling 单文档解析（非 OCR）增加不超过 2s | ≤5s（典型 PDF） | P1 |
| NFR-2 | 存储 | 去除 XHTML/HTML 中间产物后减少 artifact 存储 | 迁移前后同一 10 页 PDF 的 artifact 存储对比，减少 ≥20% | P1 |
| NFR-3 | 可靠性 | 启动依赖：Docling 不可用时 fail-fast，不启动 ingest 相关组件 | Actuator health 检测；startup-timeout 超期后生效 | P0 |

**Total NFRs: 3**

### Additional Requirements

**Success Metrics (SM):**
- SM-1: 净删 ≥300 行维护代码（Validates FR-9, FR-10）
- SM-2: 全部 8 种格式的 chunk 均携带 headings 数组（Validates FR-6）
- SM-3: 10 页文本 PDF 解析耗时 ≤ Tika 基线 + 2s（Validates FR-3）
- SM-4: INDEXED 率不下降（Validates FR-3, FR-4）

**Constraints / Assumptions:**
- Docling 对 8 种格式的输出质量 ≥ 当前 Tika + Java chunker 组合
- Arconia BOM 0.27.1 与 Spring Boot 3.5.8、Spring AI 1.1.2、Java 21 兼容
- TXT 格式的 headings/pageNumber/contentType 允许全部为空

**Cascade Change Surface:** 13 个类需同步修改或删除，见 PRD §级联改动面

### PRD Completeness Assessment

- ✅ 需求编号完整（FR-1 ~ FR-13，无跳号）
- ✅ 每个 FR 均有 Consequences (testable) 验收条件
- ✅ NFR 有明确指标和优先级
- ✅ 依赖关系链清晰
- ✅ MVP scope 边界明确（In/Out of Scope）
- ✅ 风险和缓解措施已列出
- ⚠️ UX 设计文档缺失（本项目为基础设施升级，影响有限）

## Epic Coverage Validation

### Coverage Matrix

| FR | PRD 需求 | Epic 覆盖 | Story 覆盖 | 状态 |
|----|---------|-----------|-----------|------|
| FR-1 | Docling Serve 容器编排 | Epic 1 | Story 1.1 | ✅ 已覆盖 |
| FR-2 | Arconia Docling 依赖引入 | Epic 1 | Story 1.2 | ✅ 已覆盖 |
| FR-13 | Actuator Health 暴露 | Epic 1 | Story 1.3 | ✅ 已覆盖 |
| FR-3 | DoclingDocumentParser 实现 | Epic 2 | Story 2.3 | ✅ 已覆盖 |
| FR-3a | 8 格式支持 + 错误处理（Epic 细化） | Epic 2 | Story 2.3 + 2.4 | ✅ 已覆盖 |
| FR-5 | DocumentParseResult 字段简化 | Epic 2 | Story 2.2 | ✅ 已覆盖 |
| FR-6 | ChunkMetadata 值对象 | Epic 2 | Story 2.1 | ✅ 已覆盖 |
| FR-4 | DocumentParserRouter 重构 | Epic 3 | Story 3.1 | ✅ 已覆盖 |
| FR-9 | Tika 全量移除 | Epic 3 | Story 3.2 | ✅ 已覆盖 |
| FR-10 | Java 侧 Chunker 全量移除 | Epic 3 | Story 3.3 | ✅ 已覆盖 |
| FR-11 | DocumentChunker 端口保留 + DoclingDocumentChunker 实现 | Epic 3 | Story 3.3 | ✅ 已覆盖 |
| FR-7 | HybridChunker 参数配置化 | Epic 4 | Story 4.1 | ✅ 已覆盖 |
| FR-8 | 观测指标埋点 | Epic 4 | Story 4.2 | ✅ 已覆盖 |
| FR-12 | 黄金样本重建 | Epic 4 | Story 4.3 | ✅ 已覆盖 |

### Missing Requirements

**无缺失的 FR 覆盖。** 所有 13 个 PRD FR + 1 个 Epic 细化 FR-3a 均有明确的 Epic 和 Story 对应。

### NFR 覆盖检查

| NFR | 覆盖 Story | 状态 |
|-----|-----------|------|
| NFR-1 (解析延迟 ≤5s) | Story 4.2 (Micrometer 指标 docling.parse.duration) | ⚠️ 间接覆盖 — 指标埋点可监控，但无自动验证断言 |
| NFR-2 (存储减少 ≥20%) | Story 2.2 (删除 rawXhtml/cleanedHtml) | ⚠️ 间接覆盖 — 字段删除减少存储，但无量化验证步骤 |
| NFR-3 (启动 fail-fast) | Story 1.3 (Actuator Health + fail-fast) | ✅ 已覆盖 |

### Coverage Statistics

- **Total PRD FRs:** 13
- **FRs covered in epics:** 13 (+ 1 Epic 细化 FR-3a)
- **Coverage percentage:** 100%
- **Total Stories:** 14（跨 4 个 Epic）
- **Epic dependency chain:** Epic 1 → Epic 2 → Epic 3, Epic 2 → Epic 4 ✅ 无循环依赖

## UX Alignment Assessment

### UX Document Status

**未找到 UX 设计文档。**

### UX 是否隐含？

**不适用。** 判断依据：

- PRD §0 明确声明："本 PRD 面向后端开发者、质量维护者和系统运维者"
- PRD §2.2 明确声明："终端用户无感，以开发者和维护者为视角"
- Epics §UX Design Requirements 明确标注"不适用 — 本次迁移为基础设施升级，无 UI 变更"
- 所有 Story 均为后端实现，无前端组件变更
- PRD §5 Non-Goals 列出"不升级 qa.ask 对外响应结构"——明确排除了 UI 层变更

### Alignment Issues

**无对齐问题。** 本次升级为纯后端基础设施迁移，不涉及任何 UI/UX 变更，UX 文档缺失不构成风险。

### Warnings

无警告。

## Epic Quality Review

### Epic-Level Assessment

| Epic | 标题 | 用户价值 | 独立性 | FR 覆盖 | 评估 |
|------|------|---------|--------|---------|------|
| Epic 1 | Docling 解析基础设施集成 | ✅ 运维者视角，可独立验证 Docker + Health | ✅ 无前置依赖 | FR-1, FR-2, FR-13 | ✅ 合格 |
| Epic 2 | 统一文档解析器实现 | ✅ 开发者视角，核心解析能力 | ⚠️ 技术依赖 Epic 1 | FR-3, FR-3a, FR-5, FR-6 | ✅ 合格 |
| Epic 3 | 遗留解析代码清理 | ✅ 开发者视角，减少维护负担 | ⚠️ 技术依赖 Epic 2 | FR-4, FR-9, FR-10, FR-11 | ✅ 合格 |
| Epic 4 | 解析质量保障与可观测性 | ✅ 质量维护者+运维者视角 | ⚠️ 技术依赖 Epic 2 | FR-7, FR-8, FR-12 | ✅ 合格 |

> **注：** 本次为基础设施迁移项目，Epic 均以角色 (运维者/开发者/质量维护者) 为主体陈述用户价值，符合 BMAD 规范。Epic 依赖链 (1→2→3, 2→4) 为技术性前置依赖（编译依赖），非功能循环依赖。

### Story Quality Assessment

#### Epic 1 Stories

| Story | 标题 | Given/When/Then | 可测试 | 独立性 | 评估 |
|-------|------|----------------|--------|--------|------|
| 1.1 | Docker Compose 集成 | ✅ 完整 | ✅ 可验证 | ✅ | ✅ |
| 1.2 | Maven 依赖引入 | ✅ 完整 | ✅ 可验证 | ✅ | ✅ |
| 1.3 | Actuator Health | ✅ 完整 | ✅ 可验证 | ⚠️ 依赖 1.2 | ✅ |

#### Epic 2 Stories

| Story | 标题 | Given/When/Then | 可测试 | 独立性 | 评估 |
|-------|------|----------------|--------|--------|------|
| 2.1 | ChunkMetadata 值对象 | ✅ 完整 | ✅ 防御性拷贝 + 枚举 | ✅ | ✅ |
| 2.2 | DocumentParseResult 简化 | ✅ 完整 | ✅ 编译验证 | ✅ | ✅ |
| 2.3 | DoclingDocumentParser | ✅ 完整 | ✅ 8 种格式验证 | ⚠️ 依赖 2.1, 2.2 | ✅ |
| 2.4 | 错误处理与重试 | ✅ 完整 | ✅ 4xx/5xx 映射 | ⚠️ 依赖 2.3 | ✅ |
| 2.5 | TextCleaningService | ✅ 完整 | ✅ 清洗逻辑 + 退出条件 | ⚠️ 依赖 2.3 | ✅ |

#### Epic 3 Stories

| Story | 标题 | Given/When/Then | 可测试 | 独立性 | 评估 |
|-------|------|----------------|--------|--------|------|
| 3.1 | DocumentParserRouter 重构 | ✅ 完整 | ✅ 415 验证 | ⚠️ 依赖 Epic 2 | ✅ |
| 3.2 | Tika 全部删除 | ✅ 完整 | ✅ grep 零匹配 | ⚠️ 依赖 Epic 2 | ✅ |
| 3.3 | 删除 Java 侧 chunker + 实现 DoclingDocumentChunker | ✅ 完整 | ✅ 编译 + 测试 | ⚠️ 依赖 Epic 2 | ✅ |
| 3.4 | 清理 Tika 遗留辅助类与废弃方法 | ✅ 完整 | ✅ grep 零匹配 | ⚠️ 依赖 3.2 | ✅ |

#### Epic 4 Stories

| Story | 标题 | Given/When/Then | 可测试 | 独立性 | 评估 |
|-------|------|----------------|--------|--------|------|
| 4.1 | HybridChunker 配置化 | ✅ 完整 | ✅ 重启生效验证 | ⚠️ 依赖 Epic 2 | ✅ |
| 4.2 | Micrometer 指标 | ✅ 完整 | ✅ /actuator/metrics | ⚠️ 依赖 Epic 2 | ✅ |
| 4.3 | 黄金样本重建 | ✅ 完整 | ✅ 测试通过 | ⚠️ 依赖 Epic 2 | ✅ |

### Findings by Severity

#### 🔴 Critical Violations

**无。**

#### 🟠 Major Issues

**无。**

#### 🟡 Minor Concerns

**1. Story 3.4 AC 包含跨 Epic 前向引用**

Story 3.4 的 AC 中写道：
> "更新 DocumentChunk、DocumentChunkPreview 中的 SourceHint → ChunkMetadata（**已在 Epic 2 完成**）"
> "更新 PgVectorDocumentVectorIndexer 中的 SourceHint → ChunkMetadata（**已在 Epic 2 完成**）"

**问题：** AC 引用了 Epic 2 中的工作成果作为"已完成"事实。虽然执行顺序上 Epic 3 确实在 Epic 2 之后，但 AC 应当自包含——描述"最终状态"而非"已完成的步骤"。

**建议：** 将 AC 改为：
> "DocumentChunk、DocumentChunkPreview、PgVectorDocumentVectorIndexer 中的 SourceHint 已被 ChunkMetadata 替代"

**2. Epic 4 对 Epic 2 的隐式依赖未在 Epic 层级声明**

Story 4.1 (配置化)、4.2 (指标)、4.3 (黄金样本) 均隐含 DoclingDocumentParser 已实现（Epic 2），但 Epic 4 的 `依赖:` 字段仅写 "Epic 2"，未指明具体 Story。

**影响：** 低。执行者需理解 Epic 2 全部完成后才能开始 Epic 4，但从 dependency chain 已可推导。

### Best Practices Compliance Summary

| 检查项 | 结果 |
|--------|------|
| Epic 交付用户价值 | ✅ 4/4 Epic 以角色+价值陈述 |
| Epic 独立性 | ✅ 依赖链单向无循环 |
| Story 适当规模 | ✅ 14 个 Story，每个可独立完成 |
| 无前向依赖 | 🟡 Story 3.4 有轻微跨 Epic 引用 |
| 数据库表按需创建 | N/A — 本次无新表创建 |
| AC 清晰可测试 | ✅ 全部 Given/When/Then 格式 |
| FR 可追溯性 | ✅ 100% FR 覆盖 |

## Summary and Recommendations

### Overall Readiness Status

## ✅ READY — 可进入实施阶段

### Assessment Overview

| 维度 | 评估 | 详情 |
|------|------|------|
| PRD 完整性 | ✅ 通过 | 13 FR + 3 NFR，每条有可测试验收条件 |
| FR 覆盖率 | ✅ 100% | 全部 13 FR 在 Epics 中有 Story 对应 |
| UX 对齐 | ✅ N/A | 纯基础设施迁移，无 UI 变更 |
| Epic 质量 | ✅ 通过 | 4 Epic / 14 Story，角色导向，依赖链单向 |
| 架构对齐 | ✅ 通过 | Architecture 文档与 PRD/Epics 一致 |

### Issues Summary

| 严重度 | 数量 | 详情 |
|--------|------|------|
| 🔴 Critical | 0 | — |
| 🟠 Major | 0 | — |
| 🟡 Minor | 2 | Story 3.4 AC 跨 Epic 引用；Epic 4 隐式依赖未细化 |

### Critical Issues Requiring Immediate Action

**无阻断性问题。** 所有发现均为 Minor 级别，不影响实施启动。

### Recommended Next Steps

1. **（可选）修复 Story 3.4 AC 文案** — 将 "已在 Epic 2 完成" 改为描述最终状态，避免跨 Epic 引用。可在 Story 创建阶段由开发者代理自动处理。

2. **（可选）补充 NFR-1/NFR-2 量化验证方案** — 当前仅通过 Micrometer 指标间接监控，可在 Story 4.2 的 AC 中增加 NFR-1 的断言（`docling.parse.duration` P95 ≤ 5s）。

3. **直接进入 Phase 4 实施** — 按 Epic 1 → 2 → 3 / 4（3 和 4 可并行）的顺序执行，预估 2.5 日。

### Final Note

本次评估发现 **2 个 Minor 问题**，均不构成实施阻断。PRD 需求完整度高（13 FR 全部有 Consequences 验收条件），Epics 覆盖率 100%，依赖链单向无循环，Story 结构规范。规划产物质量优良，可直接进入实施阶段。

---

**评估日期:** 2026-06-15
**评估工具:** BMAD Implementation Readiness Check
**评估文件:** `_bmad-output/planning-artifacts/docling-upgrade/`
