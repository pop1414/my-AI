---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
workflowType: 'architecture'
lastStep: 8
status: 'complete'
completedAt: '2026-06-15'
inputDocuments:
  - _bmad-output/planning-artifacts/docling-upgrade/prd.md
  - docs/adr/ADR-0004-v1-ingest-processing-strategy.md
  - docs/adr/ADR-0007-s3-compatible-document-asset-storage.md
  - docs/architecture/overview.md
  - docs/architecture/domain/ingest.md
  - docs/project-context.md
workflowType: 'architecture'
project_name: 'my-AI'
user_name: 'spike'
date: '2026-06-15'
---

# Architecture Decision Document

_本文档通过逐步协作发现的方式构建。每个架构决策章节在我们共同讨论后追加。_

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**

PRD 定义 13 个 FR，按 5 个特性组组织：

| 特性组 | FR | 架构影响 |
|--------|-----|---------|
| 4.1 Docling Serve 基础设施 | FR-1, FR-2, FR-13 | 新增外部 sidecar 容器 + Arconia BOM 依赖 + Actuator health 集成 |
| 4.2 DoclingDocumentParser 统一解析 | FR-3 | 新 infrastructure adapter 实现 DocumentTextParser 端口，8 种格式统一路径 |
| 4.3 解析路由与域模型重构 | FR-4, FR-5, FR-6 | DocumentParserRouter 重构 + DocumentParseResult 字段简化 + ChunkMetadata 新值对象 |
| 4.4 遗留代码清理 | FR-9, FR-10, FR-11 | Tika 全量移除 + Java chunker 全量移除 + DoclingDocumentChunker 替换实现 + DocumentChunker 端口保留 |
| 4.5 可观测性与配置化 | FR-7, FR-8, FR-12 | HybridChunker 参数配置化 + Micrometer 指标 + 黄金样本重建 |

**Non-Functional Requirements:**

| NFR | 指标 | 架构影响 |
|-----|------|---------|
| NFR-1 | 解析延迟 ≤5s（典型 PDF） | Docling Serve 网络跳延迟 + 布局分析开销需在预算内 |
| NFR-2 | artifact 存储减少 ≥20% | 删除 rawXhtml/cleanedHtml 中间产物 |
| NFR-3 | fail-fast（首次启动豁免） | Arconia health 自动配置 + startup-timeout 机制 |

**Scale & Complexity:**

- 主要技术域：后端 ingest 子域（文档解析管道迁移）
- 复杂度等级：中等（基础设施替换，非新功能开发）
- 预估架构组件变更：~8 个（parser、router、chunking、storage artifact、vector indexer、config、health、docker-compose）

### Technical Constraints & Dependencies

**必须遵守的现有约束：**

- 六边形架构：domain 层零框架注解，infrastructure 通过 port 接口插入
- Java record 全覆盖，构造器注入，通过 Clock 获取时间
- JdbcTemplate 直连（无 JPA），SQL 用 text block
- Flyway 迁移不可变，schema 变更需新 migration 文件
- 存储接口 `DocumentProcessingArtifactStorage` 及其 Local/S3 实现不变（ADR-0007）

**外部依赖：**

- Docling Serve 容器（quay.io 镜像）— 新增，需 docker-compose 编排
- Arconia BOM 0.27.1 — Spring Boot 生态 Docling 集成框架（pre-1.0）
- [ASSUMPTION] 与 Spring Boot 3.5.8 / Spring AI 1.1.2 / Java 21 兼容

**决策前置：**

- ADR-0004（ingest 处理策略）已被 decision-register 取代，D11/D22 为本次迁移的决策依据
- ADR-0007（S3 存储）的存储端口设计在本次迁移中保持不变

### Cross-Cutting Concerns Identified

1. **级联改动面：** DocumentParseResult 字段删除 + SourceHint → ChunkMetadata 替换涉及 15+ 个文件同步更新，遗漏任何一处导致编译失败
2. **错误映射：** Docling 4xx → 永久失败、5xx/超时 → 瞬时重试，需与现有 Document 状态机的 retry 策略对齐
3. **启动依赖链：** Docling Serve 首次启动需下载模型（数分钟），需与 Spring Boot 应用启动顺序协调
4. **TextCleaningService 去留：** `cleanNativeMarkdown` 保留为最小调用，但 Docling 输出质量决定最终命运
5. **黄金样本重建：** 迁移后需建立新的回归基准（5 个基础样本），无基准期间回归测试不可用

## Starter Template Evaluation

### Primary Technology Domain

棕地项目（brownfield）— 技术栈已确立，无需 starter template 评估。

**现有技术栈：**
- Java 21 + Spring Boot 3.5.8 + Spring AI 1.1.2
- PostgreSQL 16 + PGVector（1024 维，HNSW 索引，余弦距离）
- JdbcTemplate 直连（无 JPA/Hibernate）
- Docker Compose（PGVector + RustFS）
- React 19 + TypeScript + Vite 8 + Ant Design 6

**决策：** 跳过 starter 评估，直接在现有架构上做 Docling Serve 集成决策。

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions（阻塞实现）：**
- D-1: Docling Serve 集成方式
- D-2: 错误处理与重试架构
- D-3: ChunkMetadata 迁移策略

**Important Decisions（影响架构）：**
- D-4: Docling Serve 网络拓扑
- D-5: TextCleaningService 处置

**Deferred Decisions（MVP 后）：**
- TextCleaningService 完全移除 → 取决于阶段四黄金样本 diff 结果

---

### D-1: Docling Serve 集成方式

**决策：** Arconia 自动配置 + 薄 adapter 封装

**方案：**
- 使用 `arconia-docling-spring-boot-starter` 自动注入 `DoclingServeApi`
- 在 `DoclingDocumentParser`（infrastructure adapter）和 `DoclingServeApi` 之间加一层薄封装
- `arconia-dev-services-docling`（test scope）提供 Testcontainers 开发服务支持

**权衡：**
- ✅ 最小开发量，Testcontainers 开箱即用
- ✅ adapter 层隔离 Arconia API 变动（pre-1.0 风险窗口）
- ⚠️ 锁定 Arconia BOM 0.27.1，breaking change 时需改 adapter

**影响范围：** FR-2, FR-3

---

### D-2: 错误处理与重试架构

**决策：** Worker 层统一重试 + DoclingParseException 异常层次

**方案：**
- DoclingDocumentParser 不重试，根据 Docling 响应抛出对应异常
- 现有 Worker 的 RetryPolicy（3 次指数退避）统一处理瞬时错误
- 异常层次：

```
DoclingParseException
├── DoclingPermanentException (4xx, 不支持的格式)  → Worker: markFailed
└── DoclingTransientException (5xx, 超时, 网络)     → Worker: markRetry
```

**权衡：**
- ✅ 重试逻辑集中在 Worker 层，符合 ADR-0004 设计
- ✅ Document 状态机自动生效（retryCount/nextRetryAt）
- ✅ 用户可见"重试中"状态（Parser 内部重试是静默的）
- ⚠️ PRD 要求"最多 2 次"，现有 retryMax=3，需确认对齐

**影响范围：** FR-3, FR-3 Consequences（4xx/5xx 映射）

---

### D-3: ChunkMetadata 迁移策略

**决策：** 硬切换，编译器驱动

**方案：**
- 一次性删除 SourceHint，新建 ChunkMetadata
- 执行顺序：domain 层 → application 层 → infrastructure 层，每层编译一次
- 编译器类型不匹配 = 自动发现所有引用点

**权衡：**
- ✅ 干净，无兼容层残留
- ✅ 15 个文件中 8 个是删除，7 个是修改，实际改写量可控
- ⚠️ 单次 commit 改动量大

**影响范围：** FR-5, FR-6, 级联改动面全部 15 个文件

---

### D-4: Docling Serve 网络拓扑

**决策：** Docker bridge 网络，端口 5001，服务名发现

**方案：**
- docker-compose.yml 中 docling-serve 使用默认 bridge 网络
- `arconia.docling.base-url=http://docling-serve:5001`
- health check 端口与应用配置端口一致

**权衡：**
- ✅ 与 PGVector、RustFS 的网络模式一致
- ✅ Arconia 自动配置默认支持服务名发现
- ⚠️ bridge 网络微秒级额外延迟（相比 Docling 秒级解析可忽略）

**影响范围：** FR-1, FR-13

---

### D-5: TextCleaningService 处置

**决策：** 保留 `cleanNativeMarkdown`，在 DoclingDocumentParser 内部对 convertSource 产出的 Markdown 做最小破坏清洗，设退出条件

**方案：**
- DoclingDocumentParser 流程：DoclingServeApi.convertSource → cleanNativeMarkdown(全文) → 映射 DocumentParseResult
- DoclingDocumentChunker 流程：DoclingServeApi.chunkSourceWithHybridChunker → 映射 List<DocumentChunk>
- 清洗内容：统一换行符（CRLF→LF）、去除控制字符、压缩连续空行
- 退出条件：阶段四验收时，5 个黄金样本的清洗前后 diff 为零差异 → 记录为 no-op，可选移除

**权衡：**
- ✅ 成本极低（纯正则，无 IO），防御 Docling 输出格式微调
- ✅ 退出条件明确，不是无限期保留
- ⚠️ 如果 Docling 输出始终干净，这一步永远是 no-op

**影响范围：** FR-9 Feature-specific NFRs, Open Question 1

---

### Decision Impact Analysis

**实现顺序：**

```
D-4（docker-compose）→ D-1（Arconia 依赖）→ D-2（DoclingDocumentParser + 异常层次）
→ D-5（清洗集成）→ D-3（硬切换 SourceHint → ChunkMetadata）→ 清理 + 验收
```

**跨组件依赖：**
- D-1 依赖 D-4（容器必须先 running，Arconia 才能连接）
- D-2 依赖 D-1（Parser 依赖 DoclingServeApi）
- D-3 依赖 D-2（ChunkMetadata 由 Parser 产出）
- D-5 嵌入 D-2（清洗是 DoclingDocumentParser 转换流程的一部分）

## Implementation Patterns & Consistency Rules

> 现有 162 条 project-context.md 规则继续生效。以下仅定义 Docling 迁移新增的实现模式。

### Pattern 1: Adapter 封装模式（DoclingDocumentParser + DoclingDocumentChunker）

```java
// infrastructure/parser/DoclingDocumentParser.java
// 实现 DocumentTextParser 端口，注入 DoclingServeApi
// 调用 convertSource 纯转换，只请求 MARKDOWN 格式
// 所有 Docling 特定逻辑（Base64 编码、响应映射、异常转换）封装在此

// infrastructure/chunking/DoclingDocumentChunker.java
// 实现 DocumentChunker 端口，注入 DoclingServeApi
// 调用 chunkSourceWithHybridChunker，只用 chunks 输出
// DoclingServeApi 的类型不暴露到这两个 adapter 以外
```

- 遵循六边形架构：infrastructure adapter 实现 domain port
- 遵循构造器注入（project-context.md 规则）
- 禁止 `@Autowired` 字段注入

### Pattern 2: 异常层次模式

```java
// domain/model/ 下定义（零框架注解）
public class DoclingParseException extends RuntimeException { ... }
public class DoclingPermanentException extends DoclingParseException { ... }  // 4xx
public class DoclingTransientException extends DoclingParseException { ... }  // 5xx, 超时, 网络
```

- domain 层定义异常类型，infrastructure 层抛出
- Worker 层根据异常类型决定 `markFailed`（永久）还是 `markRetry`（瞬时）
- 与现有 `BusinessException` 体系并行，不混用

### Pattern 3: ChunkMetadata 值对象模式

```java
// domain/model/ChunkMetadata.java
public record ChunkMetadata(
    List<String> headings,       // 面包屑标题链
    int pageNumber,              // 源页码（0 = 未知）
    ChunkContentType contentType // 内容类型枚举
) {
    public ChunkMetadata {
        headings = Collections.unmodifiableList(new ArrayList<>(headings));  // 防御性拷贝
    }
}

// domain/model/ChunkContentType.java
public enum ChunkContentType { PARAGRAPH, TABLE, LIST_ITEM, CODE_BLOCK, HEADING }
```

- 替代 SourceHint，遵循现有值对象模式（DocumentId、SourceHint 都是 record）
- headings 空 list 合法（TXT 格式场景）
- 遵循 record compact constructor 只做校验/防御性拷贝的规则

### Pattern 4: 配置属性模式

```yaml
# application.yaml
arconia:
  docling:
    base-url: http://docling-serve:5001   # Docker bridge 网络服务名

myai:
  ingest:
    chunking:
      max-tokens: 512       # HybridChunker 单块最大 token
      merge-peers: true     # 合并过小块
```

- Arconia 连接参数走 `arconia.docling.*`（starter 自动配置）
- HybridChunker 业务参数走 `myai.ingest.chunking.*`（IngestProperties 扩展）
- 参数修改后重启生效，不支持热加载

### Pattern 5: Micrometer 指标命名模式

| 指标名 | 类型 | 标签 | 说明 |
|--------|------|------|------|
| `docling.parse.duration` | Timer | `format` | 解析耗时 |
| `docling.parse.errors` | Counter | `errorType` (permanent/transient) | 错误计数 |
| `docling.chunk.count` | DistributionSummary | `format` | 分块数量分布 |

- 遵循 Micrometer 命名约定（小写点分隔）
- 标签值使用枚举常量的 lowercase

### Pattern 6: Markdown 清洗模式

```java
// DoclingDocumentParser.parse() 内部流程
ConvertDocumentResponse response = doclingServeApi.convertSource(request);
String rawMarkdown = response.getDocument().getMarkdownContent();
String cleanedMarkdown = textCleaningService.cleanNativeMarkdown(rawMarkdown);
// 映射为 DocumentParseResult
```

- 清洗在 DoclingDocumentParser 内部、convertSource 返回后执行
- 不暴露清洗逻辑到 Parser 外部
- 退出条件：黄金样本清洗前后 diff 为零差异时可移除

### Enforcement Guidelines

**所有 AI agent 必须：**
- 新代码遵循上述 6 个模式，不得自行发明替代模式
- DoclingDocumentParser 和 DoclingDocumentChunker 是仅有的两个允许引用 DoclingServeApi 的类
- 异常只能通过 DoclingParseException 层次抛出，不得直接抛 RuntimeException
- ChunkMetadata 的 headings 必须做防御性拷贝
- 配置参数必须通过 IngestProperties 读取，不得硬编码

## Project Structure & Boundaries

### Ingest 子域结构变化（Before → After）

**Before（当前）：**
```
ingest/
├── domain/model/
│   ├── SourceHint.java              ← 将删除
│   ├── DocumentChunk.java           ← 将修改（sourceHint → chunkMetadata）
│   ├── DocumentChunkPreview.java    ← 将修改（同上）
│   ├── DocumentParseResult.java     ← 将修改（删除 rawXhtml/cleanedHtml）
│   └── DocumentChunker.java         ← 保留（端口接口，实现切换为 DoclingDocumentChunker）
├── domain/port/
│   └── DocumentChunker.java         ← 保留
├── infrastructure/parser/
│   ├── DocumentParserRouter.java    ← 将修改（TIKA → DOCLING + REJECT）
│   ├── TikaDocumentTextParser.java  ← 将删除
│   ├── TikaParseContextFactory.java ← 将删除
│   ├── NoOpEmbeddedDocumentExtractor.java ← 将删除
│   ├── TextCleaningService.java     ← 将修改（删除 cleanHtml/toMarkdown，保留 cleanNativeMarkdown）
│   ├── HtmlSemanticCleaner.java     ← 将删除
│   ├── HtmlToMarkdownRenderer.java  ← 将删除
│   ├── MarkdownTextCleaner.java     ← 保留
│   ├── MarkdownStructureRepairer.java ← 保留
│   ├── NativeTextDecoder.java       ← 保留
│   └── ProcessingMetadataBuilder.java ← 将修改（适配 Docling metadata）
├── infrastructure/chunking/
│   ├── StructuredFallbackDocumentChunker.java ← 将删除
│   ├── MarkdownSegmenter.java       ← 将删除
│   ├── HeadingContextExtractor.java  ← 将删除
│   └── ChunkWindowAssembler.java    ← 将删除
└── infrastructure/vector/
    └── PgVectorDocumentVectorIndexer.java ← 将修改（SourceHint → ChunkMetadata）
```

**After（迁移后）：**
```
ingest/
├── domain/model/
│   ├── ChunkMetadata.java           ← 新增（值对象，替代 SourceHint）
│   ├── ChunkContentType.java        ← 新增（枚举）
│   ├── DoclingParseException.java   ← 新增（异常基类）
│   ├── DoclingPermanentException.java ← 新增（4xx）
│   ├── DoclingTransientException.java ← 新增（5xx/超时）
│   ├── DocumentChunk.java           ← 已修改
│   ├── DocumentChunkPreview.java    ← 已修改
│   └── DocumentParseResult.java     ← 已修改（仅 cleanedMarkdown + processingMetadata）
├── infrastructure/parser/
│   ├── DoclingDocumentParser.java   ← 新增（实现 DocumentTextParser，注入 DoclingServeApi）
│   ├── DocumentParserRouter.java    ← 已修改（DOCLING + REJECT）
│   ├── TextCleaningService.java     ← 已修改（仅保留 cleanNativeMarkdown）
│   ├── MarkdownTextCleaner.java     ← 保留
│   ├── MarkdownStructureRepairer.java ← 保留
│   ├── NativeTextDecoder.java       ← 保留
│   └── ProcessingMetadataBuilder.java ← 已修改
├── infrastructure/vector/
│   └── PgVectorDocumentVectorIndexer.java ← 已修改
└── infrastructure/chunking/
    └── DoclingDocumentChunker.java ← 新增（实现 DocumentChunker，注入 DoclingServeApi）
```

### 新增文件清单

| 层 | 文件 | 职责 |
|----|------|------|
| domain/model | `ChunkMetadata.java` | 值对象：headings + pageNumber + contentType |
| domain/model | `ChunkContentType.java` | 枚举：PARAGRAPH/TABLE/LIST_ITEM/CODE_BLOCK/HEADING |
| domain/model | `DoclingParseException.java` | 异常基类 |
| domain/model | `DoclingPermanentException.java` | 4xx 永久错误 |
| domain/model | `DoclingTransientException.java` | 5xx/超时瞬时错误 |
| infrastructure/parser | `DoclingDocumentParser.java` | Docling Serve adapter，实现 DocumentTextParser（convertSource 纯转换） |
| infrastructure/chunking | `DoclingDocumentChunker.java` | Docling Serve adapter，实现 DocumentChunker（HybridChunker 分块） |

### 删除文件清单

| 文件 | 原因 |
|------|------|
| `domain/model/SourceHint.java` | 被 ChunkMetadata 替代 |
| `infrastructure/parser/TikaDocumentTextParser.java` | Tika 全量移除 |
| `infrastructure/parser/TikaParseContextFactory.java` | Tika 全量移除 |
| `infrastructure/parser/NoOpEmbeddedDocumentExtractor.java` | Tika 全量移除 |
| `infrastructure/parser/HtmlSemanticCleaner.java` | 不再需要 HTML 清洗 |
| `infrastructure/parser/HtmlToMarkdownRenderer.java` | 不再需要 HTML→MD 转换 |
| `infrastructure/chunking/StructuredFallbackDocumentChunker.java` | Java chunker 全量移除 |
| `infrastructure/chunking/MarkdownSegmenter.java` | Java chunker 全量移除 |
| `infrastructure/chunking/HeadingContextExtractor.java` | Java chunker 全量移除 |
| `infrastructure/chunking/ChunkWindowAssembler.java` | Java chunker 全量移除 |
| 对应测试类（5 个） | 被测类已删除 |

### 基础设施变更

| 文件 | 变更 |
|------|------|
| `docker-compose.yml` | 新增 `docling-serve` 服务（bridge 网络，端口 5001，health check） |
| `pom.xml` | 新增 arconia-bom + arconia-docling-spring-boot-starter + arconia-dev-services-docling(test)；删除 tika-core + tika-parsers |
| `application.yaml` | 新增 `arconia.docling.*` + `myai.ingest.chunking.*` |
| `IngestProperties.java` | 扩展 chunking 配置域 |

### FR → 文件映射

| FR | 涉及文件 |
|----|---------|
| FR-1 | docker-compose.yml |
| FR-2 | pom.xml, application.yaml |
| FR-3 | DoclingDocumentParser.java（新增） |
| FR-4 | DocumentParserRouter.java |
| FR-5 | DocumentParseResult.java |
| FR-6 | ChunkMetadata.java, ChunkContentType.java（新增） |
| FR-7 | application.yaml, IngestProperties.java |
| FR-8 | DoclingDocumentParser.java（指标埋点） |
| FR-9 | Tika 相关全部删除 + pom.xml |
| FR-10 | chunking/ 目录删除 + DoclingDocumentChunker.java（新增） |
| FR-11 | DocumentChunker.java（端口保留）+ DoclingDocumentChunker.java（新增实现） |
| FR-12 | 黄金样本测试文件重写 |
| FR-13 | Arconia 自动配置（无需手动编码） |

### 集成边界

**Docling Serve 集成边界：**
```
Spring Boot 应用 → DoclingDocumentParser → DoclingServeApi.convertSource → HTTP → Docling Serve 容器
                → DoclingDocumentChunker → DoclingServeApi.chunkSourceWithHybridChunker ↗
                                        ↑
                              两个集成点（各自 adapter 封装）
```

- DoclingServeApi 的类型不暴露到 DoclingDocumentParser 和 DoclingDocumentChunker 以外
- 错误通过 DoclingParseException 层次传播到 Worker 层

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:**
- D-1（Arconia）与 D-4（bridge 网络）兼容——Arconia 默认按服务名发现 Docling Serve
- D-2（Worker 重试）与现有 ADR-0004 一致——复用现有 RetryPolicy + CAS 状态转换
- D-3（硬切换）与 D-6（实现模式）一致——编译器驱动，domain→application→infrastructure 分层
- D-5（cleanNativeMarkdown）与 FR-9 Feature-specific NFRs 一致——保留最小调用
- Arconia BOM 0.27.1 + Spring Boot 3.5.8 + Java 21 兼容性为已知假设，已在 §9 Assumptions Index 记录

**Pattern Consistency:**
- 6 个新模式均遵循现有 project-context.md 的 162 条规则
- Java record + 防御性拷贝 + 构造器注入 + Clock 获取时间 — 全部对齐
- 异常层次定义在 domain 层（零框架注解），infrastructure 层抛出 — 符合六边形架构

**Structure Alignment:**
- 新增 7 个文件全部在 ingest 子域范围内，不侵入其他子域
- 删除 10 个文件（含 5 个测试类），旧 chunking/ 类整体移除，新增 DoclingDocumentChunker
- 不涉及数据库 schema 变更（无 Flyway migration）

### Requirements Coverage Validation ✅

**Functional Requirements 覆盖：**

| FR | 架构支持 | 状态 |
|----|---------|------|
| FR-1 | D-4（docker-compose bridge 网络） | ✅ |
| FR-2 | D-1（Arconia 自动配置） | ✅ |
| FR-3 | D-1 + D-2（DoclingDocumentParser + 异常层次） | ✅ |
| FR-4 | 结构变更（DocumentParserRouter） | ✅ |
| FR-5 | D-3（DocumentParseResult 字段简化） | ✅ |
| FR-6 | D-3 + Pattern 3（ChunkMetadata 值对象） | ✅ |
| FR-7 | Pattern 4（配置属性） | ✅ |
| FR-8 | Pattern 5（Micrometer 指标） | ✅ |
| FR-9 | 结构变更（Tika 全量删除清单） | ✅ |
| FR-10 | 结构变更（chunker 全量删除清单） | ✅ |
| FR-11 | 结构变更（DocumentChunker 端口保留 + DoclingDocumentChunker 实现） | ✅ |
| FR-12 | 结构变更（黄金样本重写） | ✅ |
| FR-13 | D-1（Arconia Actuator 自动配置） | ✅ |

**Non-Functional Requirements 覆盖：**
- NFR-1（≤5s）：SM-3 监控 + SM-C1 反指标防止过度优化
- NFR-2（存储 ≥20%）：FR-5 删除 rawXhtml/cleanedHtml 直接达成
- NFR-3（fail-fast + 首次豁免）：D-4 + Arconia health 自动配置

### Implementation Readiness Validation ✅

- 5 个关键决策全部锁定，附权衡说明
- 6 个实现模式定义清晰，含代码示例
- 文件变更清单精确到类名（新增 6、删除 11、修改 7）
- FR → 文件映射完整

### Gap Analysis

**无 Critical Gaps。**

**Important Gaps（不阻塞实现）：**
- Arconia BOM 兼容性假设待阶段一验证（pom.xml 引入后编译测试）
- TextCleaningService 最终命运待阶段四黄金样本 diff 决定

**Nice-to-Have：**
- 黄金样本的"质量满足预期"量化标准待细化（FR-12 Consequences 的主观判断点）

### Architecture Completeness Checklist

**Requirements Analysis**
- [x] 项目上下文深度分析（162 条规则 + 7 份 ADR）
- [x] 规模和复杂度评估（中等，基础设施迁移）
- [x] 技术约束识别（六边形架构 + Java record + JdbcTemplate + Flyway）
- [x] 跨切面关注点映射（级联改动面 15 文件、错误映射、启动依赖链）

**Architectural Decisions**
- [x] 关键决策文档化（D-1 到 D-5，含版本和权衡）
- [x] 技术栈完全指定（现有栈 + Arconia BOM 0.27.1）
- [x] 集成模式定义（adapter 封装 + bridge 网络 + 服务名发现）
- [x] 性能考量处理（NFR-1 + SM-3 + SM-C1）

**Implementation Patterns**
- [x] 命名约定建立（6 个模式含代码示例）
- [x] 结构模式定义（新增/删除/修改文件清单）
- [x] 通信模式指定（DoclingServeApi adapter 边界）
- [x] 过程模式文档化（异常层次 + 重试策略 + 清洗流程）

**Project Structure**
- [x] 完整目录结构定义（Before/After 对比）
- [x] 组件边界建立（Docling Serve 集成边界图）
- [x] 集成点映射（FR → 文件映射表）
- [x] 需求到结构映射完整

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** high

**Key Strengths:**
- 级联改动面完整梳理（15 个文件精确到字段级），降低了遗漏风险
- 错误处理与现有 Worker 重试机制无缝对接，无需额外基础设施
- adapter 封装隔离了 Arconia BOM 的 pre-1.0 风险

**Areas for Future Enhancement:**
- Arconia BOM 升级到 1.0 后评估是否需要 adapter 层
- TextCleaningService 完全移除的决策点（黄金样本 diff 验证后）

### Implementation Handoff

**AI Agent Guidelines:**
- 严格遵循本文档的 5 个架构决策和 6 个实现模式
- 新增/删除/修改文件清单是权威来源，不得自行增减
- DoclingDocumentParser 和 DoclingDocumentChunker 是仅有的两个允许引用 DoclingServeApi 的类
- 异常只能通过 DoclingParseException 层次抛出

**First Implementation Priority:**
阶段一（基础设施 + 依赖）：docker-compose.yml 新增 docling-serve → pom.xml 引入 Arconia BOM → application.yaml 配置连接参数
