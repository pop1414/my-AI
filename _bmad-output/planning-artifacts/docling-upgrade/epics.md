---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - _bmad-output/planning-artifacts/docling-upgrade/prd.md
  - _bmad-output/planning-artifacts/docling-upgrade/architecture.md
---

# docling-upgrade - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for my-AI, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR-1: docker-compose 新增 docling-serve 服务，含 health check 和依赖链
FR-2: 引入 Arconia Docling BOM + starter 依赖
FR-3: 新建 DoclingDocumentParser，调用 DoclingServeApi 接收 pre-chunked 结果
FR-3a: FR-3 完成条件：全部 8 种格式可产出非空 `DocumentParseResult`；Docling 超时（≥30s）触发重试（最多 2 次）；Docling 4xx 映射为永久失败（`FAILED`），5xx/网络超时映射为瞬时错误（重试）
FR-4: DocumentParserRouter 重构：TIKA → DOCLING + REJECT
FR-5: DocumentParseResult 删除 rawXhtml / cleanedHtml 字段
FR-6: ChunkMetadata 值对象：headings 面包屑 + pageNumber + contentType
FR-7: HybridChunker 参数从 application.yaml 读取（max_tokens / merge_peers）
FR-8: 基本观测指标：docling.parse.duration / docling.parse.errors / docling.chunk.count
FR-9: Tika 全部依赖和代码删除（TikaDocumentTextParser / pom.xml 依赖 / TikaParseContextFactory 等）
FR-10: Java 侧 chunker 删除（StructuredFallbackDocumentChunker / MarkdownSegmenter / HeadingContextExtractor / ChunkWindowAssembler）
FR-11: DocumentChunker 端口移除
FR-12: 黄金样本重建（Docling 基线，替换旧 Tika 基线）
FR-13: Actuator health 暴露 Docling 连通性（Arconia 自动配置）

### NonFunctional Requirements

NFR-1: 解析延迟：Docling 单文档解析（非 OCR）增加不超过 2s，目标 ≤5s（典型 PDF），优先级 P1
NFR-2: 存储占用：去除 XHTML/HTML 中间产物后减少约 30% artifact 存储，可观测，优先级 P1
NFR-3: 启动依赖：Docling 不可用时应 fail-fast，不启动 ingest 相关组件，Actuator health 检测，优先级 P0

### Additional Requirements

**From Architecture Document:**

- D-1: Docling Serve 集成方式 — Arconia 自动配置 + 薄 adapter 封装
- D-2: 错误处理与重试架构 — Worker 层统一重试 + DoclingParseException 异常层次
- D-3: ChunkMetadata 迁移策略 — 硬切换，编译器驱动
- D-4: Docling Serve 网络拓扑 — Docker bridge 网络，端口 5001，服务名发现
- D-5: TextCleaningService 处置 — 保留 cleanNativeMarkdown，逐 chunk 清洗，设退出条件

**技术集成需求：**

- 集成边界：Spring Boot 应用 → DoclingDocumentParser → DoclingServeApi → HTTP → Docling Serve 容器
- 唯一集成点：DoclingDocumentParser 是唯一允许引用 DoclingServeApi 的类
- 异常层次：DoclingParseException 层次定义在 domain 层，infrastructure 层抛出

### UX Design Requirements

（不适用 — 本次迁移为基础设施升级，无 UI 变更）

### FR Coverage Map

FR-1: Epic 1 — docker-compose 新增 docling-serve 服务，含 health check 和依赖链
FR-2: Epic 1 — 引入 Arconia Docling BOM + starter 依赖
FR-3: Epic 2 — 新建 DoclingDocumentParser，调用 DoclingServeApi 接收 pre-chunked 结果
FR-3a: Epic 2 — 全部 8 种格式支持 + 错误处理（4xx 永久失败、5xx/超时重试）
FR-4: Epic 3 — DocumentParserRouter 重构：TIKA → DOCLING + REJECT
FR-5: Epic 2 — DocumentParseResult 删除 rawXhtml / cleanedHtml 字段
FR-6: Epic 2 — ChunkMetadata 值对象：headings 面包屑 + pageNumber + contentType
FR-7: Epic 4 — HybridChunker 参数从 application.yaml 读取（max_tokens / merge_peers）
FR-8: Epic 4 — 基本观测指标：docling.parse.duration / docling.parse.errors / docling.chunk.count
FR-9: Epic 3 — Tika 全部依赖和代码删除
FR-10: Epic 3 — Java 侧 chunker 删除
FR-11: Epic 3 — DocumentChunker 端口移除
FR-12: Epic 4 — 黄金样本重建（Docling 基线，替换旧 Tika 基线）
FR-13: Epic 1 — Actuator health 暴露 Docling 连通性（Arconia 自动配置）

## Epic List

### Epic 1: Docling 解析基础设施集成

作为系统运维者，我希望 Docling Serve 在 docker-compose 中与现有基础设施一同编排，Actuator health 能检测 Docling 连通性，以便一键启动全栈并快速定位解析故障。

**FRs covered:** FR-1, FR-2, FR-13
**实施周期：** 半日
**依赖：** 无

### Story 1.1: Docker Compose 集成 Docling Serve 容器

As a 系统运维者，
I want Docling Serve 在 docker-compose 中与现有基础设施一同编排，
So that `docker compose up -d` 一键启动全栈，无需手动管理多个服务。

**Acceptance Criteria:**

**Given** docker-compose.yml 中已配置 PGVector 和 RustFS 服务
**When** 执行 `docker compose up -d`
**Then** docling-serve 服务正常启动（含模型自动下载完毕），health check 通过
**And** docling-serve 使用 bridge 网络，端口 5001
**And** 首次启动等待 ≤ 5 分钟（模型下载）
**And** 服务依赖链：docling-serve 不依赖其他服务，但应用启动时依赖 docling-serve

### Story 1.2: Maven 依赖引入 Arconia Docling BOM

As a 后端开发者，
I want 引入 Arconia Docling BOM + starter 依赖，
So that 应用能够通过自动配置连接 Docling Serve，无需手动编写 HTTP 客户端代码。

**Acceptance Criteria:**

**Given** pom.xml 中已有 Spring Boot 3.5.8 和 Spring AI 1.1.2 依赖
**When** 添加 arconia-bom、arconia-docling-spring-boot-starter 和 arconia-dev-services-docling（test scope）
**Then** 应用能够编译通过，无依赖冲突
**And** application.yaml 中配置 arconia.docling.base-url=http://docling-serve:5001
**And** Spring 应用上下文启动时，DoclingServeApi Bean 被自动注入
**And** ~~arconia-dev-services-docling 提供 Testcontainers 支持用于本地开发~~ `[intentional deviation]` 已移除 — 项目使用 docker-compose 编排 docling-serve（Story 1.1），模型缓存持久化卷挂载优于 Testcontainers 冷启动，不需要 Testcontainers 自动拉取镜像。决策记录于 Story 1.2 Dev Notes 及 Epic 1 回顾（epic-1-retro-2026-06-17.md）。

### Story 1.3: Actuator Health 检测 Docling 连通性

As a 系统运维者，
I want Actuator health 能检测 Docling 连通性，
So that Docling 不可用时系统状态变为 DOWN，快速定位解析故障。

**Acceptance Criteria:**

**Given** Arconia Docling BOM 已引入，DoclingServeApi Bean 已注入
**When** 访问 /actuator/health 端点
**Then** 响应包含 docling 组件的健康状态
**And** 当 docling-serve 服务正常运行时，状态为 UP
**And** 当 docling-serve 服务停止或不可达时，状态变为 DOWN
**And** 健康检查响应时间 ≤ 5 秒
**And** 首次启动时，若 Docling 不可用，系统应 fail-fast（不启动 ingest 相关组件）

### Epic 2: 统一文档解析器实现

作为后端开发者，我希望系统能够解析 8 种文档格式（PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT）并产出带结构化 metadata 的 chunks，以便下游检索获得更丰富的结构信号。

**FRs covered:** FR-3, FR-3a, FR-5, FR-6
**实施周期：** 1 日
**依赖：** Epic 1

### Story 2.1: 创建 ChunkMetadata 值对象

As a 后端开发者，
I want 创建 ChunkMetadata 值对象替代 SourceHint，
So that chunk 携带结构化的 metadata（headings 面包屑、pageNumber、contentType），下游检索能统一利用这些信号。

**Acceptance Criteria:**

**Given** 当前 SourceHint 类仅包含一个字符串 heading 字段
**When** 创建新的 ChunkMetadata record（headings: List<String>, pageNumber: int, contentType: ChunkContentType）
**Then** ChunkMetadata 遵循 Java record 规范，包含防御性拷贝
**And** headings 字段在 compact constructor 中做防御性拷贝：`Collections.unmodifiableList(new ArrayList<>(headings))`
**And** 创建 ChunkContentType 枚举：PARAGRAPH, TABLE, LIST_ITEM, CODE_BLOCK, HEADING
**And** headings 空 list 合法（TXT 格式场景）
**And** pageNumber 默认 0 表示未知
**And** contentType 默认 PARAGRAPH

### Story 2.2: 简化 DocumentParseResult 字段

As a 后端开发者，
I want 删除 DocumentParseResult 中的 rawXhtml / cleanedHtml 字段，
So that 解析结果只保留 cleanedMarkdown 和 processingMetadata，减少不必要的存储开销。

**Acceptance Criteria:**

**Given** 当前 DocumentParseResult 包含 rawXhtml、cleanedHtml、cleanedMarkdown、processingMetadata 四个字段
**When** 删除 rawXhtml 和 cleanedHtml 字段
**Then** DocumentParseResult 仅包含 cleanedMarkdown 和 processingMetadata
**And** compact constructor 仅保留这两个字段
**And** 所有引用 rawXhtml/cleanedHtml 的代码（TextCleaningService、HtmlSemanticCleaner、HtmlToMarkdownRenderer）被同步删除或重构
**And** 编译通过，无类型不匹配错误

### Story 2.3: 实现 DoclingDocumentParser 核心解析器

As a 后端开发者，
I want 实现 DoclingDocumentParser 调用 DoclingServeApi，
So that 系统能够解析 8 种文档格式并产出 pre-chunked 结果，统一所有格式的解析路径。

**Acceptance Criteria:**

**Given** Arconia Docling BOM 已引入，DoclingServeApi Bean 已注入
**When** 实现 DoclingDocumentParser（infrastructure adapter）实现 DocumentTextParser 端口
**Then** 能够接收文件内容，Base64 编码后调用 DoclingServeApi
**And** 配置 ConvertDocumentOptions（含 HybridChunkerOptions：max_tokens=512, merge_peers=true）
**And** 映射 Docling 响应为 DocumentParseResult（cleanedMarkdown + processingMetadata）
**And** 映射 ChunkMetadata（headings / pageNumber / contentType）
**And** 全部 8 种格式（PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT）可产出非空 DocumentParseResult
**And** DoclingServeApi 的类型不暴露到 DoclingDocumentParser 以外

### Story 2.4: 实现错误处理与重试逻辑

As a 后端开发者，
I want 实现 DoclingParseException 异常层次和错误映射逻辑，
So that Docling 4xx 错误映射为永久失败（FAILED），5xx/网络超时映射为瞬时错误（重试），与现有 Worker 重试策略无缝对接。

**Acceptance Criteria:**

**Given** 现有 Worker 层有 RetryPolicy（3 次指数退避）
**When** 创建 DoclingParseException 异常基类（domain/model/ 下，零框架注解）
**Then** 创建 DoclingPermanentException（4xx 错误）
**And** 创建 DoclingTransientException（5xx、超时、网络错误）
**And** DoclingDocumentParser 根据 Docling 响应抛出对应异常
**And** Worker 层根据异常类型决定 markFailed（永久）还是 markRetry（瞬时）
**And** 4xx 错误立即标记为 FAILED，不重试
**And** 5xx/超时/网络错误触发重试，最多 3 次（对齐现有 retryMax）

### Story 2.5: 实现 TextCleaningService 清洗逻辑

As a 后端开发者，
I want 保留 TextCleaningService 的 cleanNativeMarkdown 方法，
So that Docling 产出的 Markdown 能够统一换行符、去除控制字符、压缩连续空行，确保输出格式一致。

**Acceptance Criteria:**

**Given** Docling 产出格式化良好的 Markdown
**When** 在 DoclingDocumentParser 内部、映射之前调用 TextCleaningService.cleanNativeMarkdown
**Then** 清洗内容：统一换行符（CRLF→LF）、去除控制字符、压缩连续空行
**And** 清洗逻辑不暴露到 DoclingDocumentParser 外部
**And** 删除 TextCleaningService 中的 cleanHtml 和 toMarkdown 方法（不再需要）
**And** 保留 cleanNativeMarkdown 的最小调用
**And** 退出条件：黄金样本清洗前后 diff 为零差异时可移除（记录为 no-op）

### Epic 3: 遗留解析代码清理

作为后端开发者和质量维护者，我希望 Tika 及其全部依赖、Java 侧 chunker、以及相关端口接口被完整移除，以便减少维护负担和依赖安全风险。

**FRs covered:** FR-4, FR-9, FR-10, FR-11
**实施周期：** 半日
**依赖：** Epic 2

### Story 3.1: 重构 DocumentParserRouter

As a 后端开发者，
I want 重构 DocumentParserRouter，将 TIKA 路由改为 DOCLING + REJECT，
So that 所有支持的格式走 Docling 路径，不支持的格式返回 415 Unsupported Media Type。

**Acceptance Criteria:**

**Given** 当前 DocumentParserRouter 包含 TIKA 路由
**When** 删除 TIKA 路由，新增 DOCLING 路由和 REJECT 路由
**Then** 所有 8 种支持的格式（PDF/DOCX/PPTX/XLSX/图片/MD/HTML/TXT）走 DOCLING 路由
**And** 不支持的格式（CSV/EPUB/RTF 等）走 REJECT 路由
**And** REJECT 路由返回 415 Unsupported Media Type 状态码
**And** REJECT 路由响应体包含可读错误消息
**And** 编译通过，无类型不匹配错误

### Story 3.2: 删除 Tika 全部依赖和代码

As a 后端开发者，
I want 删除 Tika 全部依赖和代码，
So that 项目构建体积减少，依赖安全风险降低，不再维护两套解析逻辑。

**Acceptance Criteria:**

**Given** 项目中存在 Tika 相关代码和依赖
**When** 删除 tika-core 和 tika-parsers-standard-package Maven 依赖
**Then** 删除 TikaDocumentTextParser（~235 行）
**And** 删除 TikaParseContextFactory 和 NoOpEmbeddedDocumentExtractor
**And** 删除 HtmlSemanticCleaner 和 HtmlToMarkdownRenderer
**And** pom.xml 中无 tika 相关依赖
**And** 代码中无 `tika` 或 `org.apache.tika` 引用（`grep -r "tika\|org\.apache\.tika" src/ pom.xml` 无匹配）
**And** 应用编译通过，无类型不匹配错误

### Story 3.3: 删除 Java 侧 chunker

As a 后端开发者，
I want 删除 Java 侧 chunker（StructuredFallbackDocumentChunker / MarkdownSegmenter / HeadingContextExtractor / ChunkWindowAssembler），
So that chunking 逻辑完全由 Docling Serve server-side 完成，减少测试维护量。

**Acceptance Criteria:**

**Given** 项目中存在 Java 侧 chunker 代码
**When** 删除 StructuredFallbackDocumentChunker（~260 行）
**Then** 删除 MarkdownSegmenter
**And** 删除 HeadingContextExtractor
**And** 删除 ChunkWindowAssembler
**And** 删除 infrastructure/chunking/ 目录
**And** 删除对应的测试类（StructuredFallbackDocumentChunkerTest、SourceHintTest 等）
**And** 应用编译通过，无类型不匹配错误
**And** 存量测试通过

### Story 3.4: 移除 DocumentChunker 端口

As a 后端开发者，
I want 移除 DocumentChunker 端口接口，
So that chunking 由 Docling Serve server-side 完成，不再需要 Java 侧的 chunking 端口定义。

**Acceptance Criteria:**

**Given** 项目中存在 DocumentChunker 端口接口
**When** 删除 domain/port/DocumentChunker.java
**Then** 删除所有对 DocumentChunker 的引用
**And** 更新 DocumentChunk、DocumentChunkPreview 中的 SourceHint → ChunkMetadata（已在 Epic 2 完成）
**And** 更新 PgVectorDocumentVectorIndexer 中的 SourceHint → ChunkMetadata（已在 Epic 2 完成）
**And** 编译通过，无类型不匹配错误
**And** 存量测试通过

### Epic 4: 解析质量保障与可观测性

作为质量维护者和系统运维者，我希望 chunking 参数可配置、解析指标可观测、黄金样本有 Docling 基线，以便按场景区分设置并确保回归测试有准确基准。

**FRs covered:** FR-7, FR-8, FR-12
**实施周期：** 半日
**依赖：** Epic 2

### Story 4.1: HybridChunker 参数配置化

As a 后端开发者，
I want HybridChunker 参数从 application.yaml 读取（max_tokens / merge_peers），
So that 按场景（demo/production）区分设置而不需要改代码，参数修改后重启生效。

**Acceptance Criteria:**

**Given** HybridChunker 参数当前硬编码在 DoclingDocumentParser 中
**When** 在 application.yaml 中新增 myai.ingest.chunking 配置域
**Then** max-tokens 参数可配置，默认值 512
**And** merge-peers 参数可配置，默认值 true
**And** IngestProperties 扩展 chunking 配置域
**And** DoclingDocumentParser 从配置读取参数，而非硬编码
**And** 修改参数后重启生效，不支持热加载

### Story 4.2: Micrometer 指标埋点

As a 系统运维者，
I want 基本观测指标（docling.parse.duration / docling.parse.errors / docling.chunk.count），
So that 能够监控 Docling 解析性能，快速定位问题，确保 NFR-1（≤5s）达标。

**Acceptance Criteria:**

**Given** 应用已集成 Micrometer 指标框架
**When** 在 DoclingDocumentParser 中埋点
**Then** 创建 docling.parse.duration 指标（Timer 类型，标签：format）
**And** 创建 docling.parse.errors 指标（Counter 类型，标签：errorType: permanent/transient）
**And** 创建 docling.chunk.count 指标（DistributionSummary 类型，标签：format）
**And** 指标遵循 Micrometer 命名约定（小写点分隔）
**And** 标签值使用枚举常量的 lowercase

### Story 4.3: 黄金样本重建

As a 质量维护者，
I want 旧黄金样本用 Docling 基线重建，
So that 后续回归测试有准确基准，能够验证 Docling 解析质量。

**Acceptance Criteria:**

**Given** 当前存在 IngestCleaningGoldenSamplesTest（基于 Tika 基线）
**When** 重建黄金样本为 Docling 基线
**Then** 删除旧的 Tika 基线样本
**And** 创建 5 个基础样本（PDF/DOCX/MD/HTML/TXT）
**And** 每个样本包含 expected cleanedMarkdown 和 expected ChunkMetadata
**And** 测试验证 DoclingDocumentParser 产出与 expected 一致
**And** 测试验证 ChunkMetadata 正确（headings 面包屑、pageNumber、contentType）
**And** TXT 格式的这三种字段允许全部为空
**And** 存量测试通过

<!-- Repeat for each epic in epics_list (N = 1, 2, 3...) -->

## Epic {{N}}: {{epic_title_N}}

{{epic_goal_N}}

<!-- Repeat for each story (M = 1, 2, 3...) within epic N -->

### Story {{N}}.{{M}}: {{story_title_N_M}}

As a {{user_type}},
I want {{capability}},
So that {{value_benefit}}.

**Acceptance Criteria:**

<!-- for each AC on this story -->

**Given** {{precondition}}
**When** {{action}}
**Then** {{expected_outcome}}
**And** {{additional_criteria}}

<!-- End story repeat -->
