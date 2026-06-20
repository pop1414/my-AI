---
baseline_commit: cc675e9ec6bf17a5c3a23be6d2e1c879955c3f58
---

# Story 1.3: 检索参数配置外部化

Status: done

## Story

作为开发者，
我希望检索参数通过配置文件管理而非硬编码，
以便无需重新编译即可调整检索行为。

## Acceptance Criteria

1. **Given** `AskQuestionApplicationService` 中 `MIN_RETRIEVAL_CANDIDATES = 20` 和 `RETRIEVAL_CANDIDATE_MULTIPLIER = 4` 为硬编码常量
   **When** 新增 `QaRetrievalProperties` 配置类（`qa/infrastructure/config/` 包下）
   **Then** 配置类带 `@ConfigurationProperties(prefix = "app.qa.retrieval")` + `@Validated`
   **And** 包含 `minCandidates`（默认 20）和 `candidateMultiplier`（默认 4）字段
   **And** Lombok `@Getter` + `@Setter` 注解（与 `IngestProperties` 模式一致）

2. **Given** 配置需要在 `application.yaml` 中声明
   **When** 在 `application.yaml` 的 `app:` 段新增 `qa.retrieval` 子段
   **Then** 配置键为 `app.qa.retrieval.min-candidates`（默认 20）和 `app.qa.retrieval.candidate-multiplier`（默认 4）
   **And** 支持环境变量覆盖（`${APP_QA_RETRIEVAL_MIN_CANDIDATES:20}` 模式）

3. **Given** `AskQuestionApplicationService` 需要注入配置替代硬编码
   **When** 构造器新增 `QaRetrievalProperties` 参数
   **Then** 删除 `MIN_RETRIEVAL_CANDIDATES` 和 `RETRIEVAL_CANDIDATE_MULTIPLIER` 两个 static final 常量
   **And** `retrievalTopK` 计算改为 `Math.max(properties.getMinCandidates(), topK * properties.getCandidateMultiplier())`
   **And** 默认值与原硬编码值一致（20 和 4），行为无变化

4. **Given** 测试需要验证配置注入正确
   **When** 更新 `AskQuestionApplicationServiceTest`
   **Then** 每个测试方法新增 `QaRetrievalProperties` 实例化（手动构造 + 设值，非 Spring 注入）
   **And** 构造器调用新增 `properties` 参数
   **And** 所有 6 个现有测试通过（行为不变）
   **And** 新增至少 1 个测试验证自定义配置值生效（如 `minCandidates=10, candidateMultiplier=2`）

## Tasks / Subtasks

- [x] Task 1: 新增 QaRetrievalProperties 配置类（AC: #1）
  - [x] 1.1 在 `qa/infrastructure/config/` 包下新增 `QaRetrievalProperties.java`
  - [x] 1.2 类注解：`@Getter`、`@Setter`、`@ConfigurationProperties(prefix = "app.qa.retrieval")`、`@Validated`
  - [x] 1.3 字段 `int minCandidates` 默认值 20，`@Min(1)` 校验
  - [x] 1.4 字段 `int candidateMultiplier` 默认值 4，`@Min(1)` 校验
  - [x] 1.5 Javadoc 说明配置用途：检索候选参数控制

- [x] Task 2: application.yaml 新增配置段（AC: #2）
  - [x] 2.1 在 `application.yaml` 的 `app:` 段下新增 `qa.retrieval` 子段
  - [x] 2.2 `min-candidates: ${APP_QA_RETRIEVAL_MIN_CANDIDATES:20}`
  - [x] 2.3 `candidate-multiplier: ${APP_QA_RETRIEVAL_CANDIDATE_MULTIPLIER:4}`
  - [x] 2.4 配置注释说明用途和默认值

- [x] Task 3: AskQuestionApplicationService 注入配置（AC: #3）
  - [x] 3.1 新增 `private final QaRetrievalProperties properties` 字段
  - [x] 3.2 构造器新增 `QaRetrievalProperties properties` 参数（第 8 个参数，末尾追加）
  - [x] 3.3 删除 `MIN_RETRIEVAL_CANDIDATES` 和 `RETRIEVAL_CANDIDATE_MULTIPLIER` 两个 static final 常量
  - [x] 3.4 `retrievalTopK` 计算改为 `Math.max(properties.getMinCandidates(), topK * properties.getCandidateMultiplier())`
  - [x] 3.5 更新 Javadoc：新增 `@param properties` 说明
  - [x] 3.6 保留 `PREVIEW_MAX_LENGTH` 和 `FALLBACK_ANSWER` 常量不变

- [x] Task 4: 更新 AskQuestionApplicationServiceTest（AC: #4）
  - [x] 4.1 每个测试方法新增 `QaRetrievalProperties properties = new QaRetrievalProperties()` （默认值 20/4）
  - [x] 4.2 构造器调用新增 `properties` 参数（末尾追加）
  - [x] 4.3 所有 6 个现有测试通过（行为不变，`verify` 的 retrievalTopK 值不变）
  - [x] 4.4 新增测试：自定义配置 `minCandidates=10, candidateMultiplier=2`，验证 `similaritySearch` 的 anyInt() 参数符合预期

- [x] Task 5: 验证所有测试通过（AC: #3, #4）
  - [x] 5.1 `mvn test "-Dtest=AskQuestionApplicationServiceTest"` — 7 tests, 0 failures
  - [x] 5.2 `mvn compile` — 编译通过

## Dev Notes

### 当前 AskQuestionApplicationService 硬编码常量状态

```java
// 文件: src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java
// 需要删除的常量（第 55-57 行）
private static final int MIN_RETRIEVAL_CANDIDATES = 20;
private static final int RETRIEVAL_CANDIDATE_MULTIPLIER = 4;

// 需要修改的计算（第 143 行）
int retrievalTopK = Math.max(MIN_RETRIEVAL_CANDIDATES, topK * RETRIEVAL_CANDIDATE_MULTIPLIER);
```

**改造后：**
```java
int retrievalTopK = Math.max(properties.getMinCandidates(), topK * properties.getCandidateMultiplier());
```

### 当前构造器签名（Story 1.2 完成后，8 个参数）

```java
public AskQuestionApplicationService(
        ChunkRetrievalPort chunkRetrievalPort,
        RerankingPort rerankingPort,
        AnswerGenerationPort answerGenerationPort,
        KnowledgeBaseRepository knowledgeBaseRepository,
        CurrentUserProvider currentUserProvider,
        AuthorizationService authorizationService,
        AskableDocumentVersionPort askableDocumentVersionPort)
```

**改造后（9 个参数，末尾追加）：**
```java
public AskQuestionApplicationService(
        ChunkRetrievalPort chunkRetrievalPort,
        RerankingPort rerankingPort,
        AnswerGenerationPort answerGenerationPort,
        KnowledgeBaseRepository knowledgeBaseRepository,
        CurrentUserProvider currentUserProvider,
        AuthorizationService authorizationService,
        AskableDocumentVersionPort askableDocumentVersionPort,
        QaRetrievalProperties properties)            // ← Story 1.3 新增
```

**注意：** 构造器无 `@Autowired`（Spring 单构造器自动注入）。新参数放在末尾，不破坏现有参数顺序。

### QaRetrievalProperties 实现参考

```java
// 文件: src/main/java/io/github/spike/myai/qa/infrastructure/config/QaRetrievalProperties.java
package io.github.spike.myai.qa.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.qa.retrieval")
public class QaRetrievalProperties {
    /** 检索候选下限，避免 topK 较小时候选过少导致过滤后无结果 */
    @Min(1)
    private int minCandidates = 20;
    /** 检索候选放大倍率：先粗召回 topK×N 条，再按 kbId 精过滤 */
    @Min(1)
    private int candidateMultiplier = 4;
}
```

**模式参考：** `IngestProperties.java`（`src/main/java/io/github/spike/myai/ingest/infrastructure/config/IngestProperties.java`）
- 使用 Lombok `@Getter` + `@Setter`（非 Java record）
- `@ConfigurationProperties` 前缀命名

**区别于 IngestProperties：**
- 新增 `@Validated` 注解 + `@Min(1)` 校验（AD-7 要求）
- 更简洁：无嵌套 class，只有两个顶级字段

### application.yaml 配置段位置

```yaml
# 在 app: 段末尾新增（目前 app: 段下已有 auth: 和 ingest:）
app:
  auth:
    bootstrap-admin:
      # ... 现有配置 ...
  ingest:
    # ... 现有配置 ...
  qa:                                      # ← 新增
    retrieval:                             # ← 新增
      # 检索候选下限，避免 topK 较小时候选过少导致过滤后无结果
      min-candidates: ${APP_QA_RETRIEVAL_MIN_CANDIDATES:20}
      # 检索候选放大倍率：先粗召回 topK×N 条，再按 kbId 精过滤
      candidate-multiplier: ${APP_QA_RETRIEVAL_CANDIDATE_MULTIPLIER:4}
```

### 测试改造示例

```java
// 每个测试方法新增：
QaRetrievalProperties properties = new QaRetrievalProperties(); // 默认值 20/4

// 构造器调用新增 properties 参数：
AskQuestionApplicationService service =
        new AskQuestionApplicationService(
                chunkRetrievalPort,
                rerankingPort,
                answerGenerationPort,
                knowledgeBaseRepository,
                currentUserProvider,
                authorizationService,
                askableDocumentVersionPort,
                properties);              // ← 新增
```

**自定义配置测试示例：**
```java
@Test
@DisplayName("自定义检索配置应影响候选集放大计算")
void handle_shouldUseCustomRetrievalProperties_whenConfigured() {
    QaRetrievalProperties properties = new QaRetrievalProperties();
    properties.setMinCandidates(10);
    properties.setCandidateMultiplier(2);
    // topK=3, retrievalTopK = max(10, 3*2) = 10
    // 验证 similaritySearch 调用时的 anyInt() 参数 = 10
}
```

### 架构约束检查

| 约束 | 状态 |
|------|------|
| 六边形分层 | ✅ QaRetrievalProperties 在 infrastructure/config/ 层 |
| @ConfigurationProperties + @Validated | ✅ AD-7 要求 |
| 构造器注入 | ✅ 无 @Autowired 字段注入 |
| 默认值与原硬编码一致 | ✅ minCandidates=20, candidateMultiplier=4 |
| 配置前缀 `app.qa.retrieval.*` | ✅ AD-7 命名锁定 |
| Javadoc 含 @author + @since | ✅ 所有新文件 |

### 保留的常量

以下常量 **不迁移**（不是检索参数，而是展示/兜底配置）：
- `PREVIEW_MAX_LENGTH = 200` — 引用预览截断长度
- `FALLBACK_ANSWER` — 兜底回答文案

### Story 1.2 前置上下文

- `RerankingPort` 已在 Story 1.2 完成，构造器第 2 个参数
- `handle()` 方法编排：检索 → 截取 → **rerank** → 空检查 → buildPrompt
- 本 Story 只改动检索参数来源，不改动 rerank 调用逻辑
- `RetrievedChunk.score` 已在 Story 1.1 完成

### Project Structure Notes

- `QaRetrievalProperties.java` 路径：`src/main/java/io/github/spike/myai/qa/infrastructure/config/QaRetrievalProperties.java`
- `AskQuestionApplicationService.java` 路径：`src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`
- `AskQuestionApplicationServiceTest.java` 路径：`src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java`
- `application.yaml` 路径：`src/main/resources/application.yaml`

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 1.3] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#配置属性命名] — AD-7 配置前缀 `app.qa.retrieval.*`
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-3] — 检索参数配置外部化功能需求
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#project-structure] — 变更文件清单
- [Source: docs/project-context.md#Spring Boot — 框架陷阱] — @ConfigurationProperties 必须带 @Validated
- [Source: docs/project-context.md#Java — 构造器与依赖注入] — 构造器注入规范
- [Source: src/main/java/io/github/spike/myai/ingest/infrastructure/config/IngestProperties.java] — 配置类模式参考

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- 编译失败：`jakarta.validation.constraints` 包不可用 → 需要添加 `spring-boot-starter-validation` 依赖到 pom.xml

### Completion Notes List

- 新增 `QaRetrievalProperties` 配置类（`@Getter`/`@Setter`/`@Validated`/`@ConfigurationProperties`）
- 新增 `spring-boot-starter-validation` 依赖（pom.xml），用于支持 `@Min(1)` JSR-303 校验
- `AskQuestionApplicationService` 构造器从 7 参数扩展为 8 参数（末尾追加 `QaRetrievalProperties`）
- 删除 `MIN_RETRIEVAL_CANDIDATES` 和 `RETRIEVAL_CANDIDATE_MULTIPLIER` 硬编码常量
- `retrievalTopK` 计算改为从配置属性读取
- `application.yaml` 新增 `app.qa.retrieval` 配置段，支持环境变量覆盖
- 6 个现有测试全部通过（默认配置值与原硬编码一致），新增 1 个自定义配置测试
- 总计 7 tests, 0 failures

### File List

- src/main/java/io/github/spike/myai/qa/infrastructure/config/QaRetrievalProperties.java (new)
- src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java (modified)
- src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java (modified)
- src/main/resources/application.yaml (modified)
- pom.xml (modified)

### Review Findings

- [x] [Review][Decision] ~~@ConfigurationProperties 前缀 `app.qa.retrieval` 与 YAML 中 `myai.qa.retrieval` 不匹配~~ → 已修复：注解改为 `myai.qa.retrieval`
- [x] [Review][Patch] ~~过时注释引用已删除常量~~ `AskQuestionApplicationService.java:144` → 已修复：更新为 `max(minCandidates, topK × candidateMultiplier)`
- [x] [Review][Patch] ~~`candidateMultiplier` 和 `minCandidates` 缺少 `@Max` 约束~~ `QaRetrievalProperties.java:29;33` → 已修复：添加 `@Max(100)` 和 `@Max(1000)`
- [x] [Review][Patch] ~~测试仅覆盖 `Math.max` 一个分支~~ `AskQuestionApplicationServiceTest.java:279` → 已修复：新增 `handle_shouldUseMultiplierDrivenTopK_whenProductExceedsMinCandidates`
- [x] [Review][Patch] ~~测试验证使用硬编码值~~ `AskQuestionApplicationServiceTest.java:323` → 已修复：改用 `ArgumentCaptor<Integer>`
- [x] [Review][Defer] 应用层 import infrastructure 层（`AskQuestionApplicationService.java:22` → `QaRetrievalProperties`）— 既存模式，`GetDocumentContentApplicationService` 已有同模式，spec 明确批准
- [x] [Review][Defer] 双重默认值来源（Java 默认 + YAML 默认）— 低风险，不影响功能

## Change Log

- feat(qa): 检索参数配置外部化 — QaRetrievalProperties + 应用层集成（2026-06-17）
