---
baseline_commit: f25f043ec03aef1b3beba8880a985ea3df765941
---

# Story 1.2: RerankingPort 可插拔接口

Status: done

## Story

作为开发者，
我希望系统预留重排序扩展点，
以便未来引入 Reranking 能力时无需修改应用层代码。

## Acceptance Criteria

1. **Given** domain 层需要一个重排序端口接口
   **When** 定义 `RerankingPort` 接口（`qa/domain/port/` 包下）
   **Then** 方法签名为 `List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN)`
   **And** 接口零框架注解

2. **Given** domain 层端口需要基础设施层实现
   **When** infrastructure 层新增 `NoOpRerankingAdapter`（`qa/infrastructure/reranking/` 包下）
   **Then** 透传输入列表的前 topN 条
   **And** 输入为 null 或空列表时返回空列表
   **And** 候选数不超过 topN 时返回全部候选（防御性拷贝 `List.copyOf`）
   **And** 候选数超过 topN 时截断到前 topN 条（保持输入顺序）

3. **Given** `AskQuestionApplicationService` 需要编排重排序步骤
   **When** 注入 `RerankingPort` 并在检索后、context 拼接前调用
   **Then** 在 `chunkRetrievalPort.similaritySearch()` 之后、`buildPrompt()` 之前调用 `rerankingPort.rerank(matchedChunks, question, topK)`
   **And** 默认注入 `NoOpRerankingAdapter`，现有行为不变

4. **Given** 测试需要验证重排序集成
   **When** 更新 `AskQuestionApplicationServiceTest`
   **Then** mock `RerankingPort`，行为设为透传（`thenAnswer(inv -> inv.getArgument(0))`）
   **And** 验证 `rerank()` 被正确调用（参数：candidates, question, topK）
   **And** 无检索命中路径验证 `rerank()` 未被调用
   **And** 现有 6 个测试全部通过

5. **Given** `NoOpRerankingAdapter` 需要独立验证
   **When** 新增 `NoOpRerankingAdapterTest`
   **Then** 至少覆盖 5 个场景：候选数不超过 topN、候选数超过 topN、null 输入、空列表输入、顺序保持
   **And** 所有测试通过

## Tasks / Subtasks

- [x] Task 1: 新增 RerankingPort 接口（AC: #1）
  - [x] 1.1 在 `qa/domain/port/` 包下新增 `RerankingPort.java` 接口
  - [x] 1.2 方法签名：`List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN)`
  - [x] 1.3 Javadoc 说明端口用途：检索后、上下文拼接前的重排序扩展点
  - [x] 1.4 零框架注解

- [x] Task 2: 新增 NoOpRerankingAdapter（AC: #2）
  - [x] 2.1 在 `qa/infrastructure/reranking/` 包下新增 `NoOpRerankingAdapter.java`
  - [x] 2.2 实现 `RerankingPort` 接口，`@Component` 注册
  - [x] 2.3 透传逻辑：null/空 → 空列表；不超过 topN → `List.copyOf(candidates)`；超过 topN → `List.copyOf(candidates.subList(0, topN))`
  - [x] 2.4 Javadoc 说明透传行为

- [x] Task 3: 集成到 AskQuestionApplicationService（AC: #3）
  - [x] 3.1 新增 `private final RerankingPort rerankingPort` 字段
  - [x] 3.2 构造器新增 `RerankingPort rerankingPort` 参数（第 2 个参数位置，在 `chunkRetrievalPort` 之后）
  - [x] 3.3 在 `handle()` 方法中，检索后（line ~148）调用 `rerankingPort.rerank(matchedChunks, question, topK)`
  - [x] 3.4 更新 Javadoc：新增 `@param rerankingPort` 说明

- [x] Task 4: 更新 AskQuestionApplicationServiceTest（AC: #4）
  - [x] 4.1 每个测试方法新增 `RerankingPort rerankingPort = Mockito.mock(RerankingPort.class)`
  - [x] 4.2 mock 行为：`when(rerankingPort.rerank(anyList(), anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(0))`
  - [x] 4.3 构造器调用新增 `rerankingPort` 参数
  - [x] 4.4 成功路径验证：`verify(rerankingPort).rerank(anyList(), eq(question), eq(topK))`
  - [x] 4.5 无检索命中路径验证：`verify(rerankingPort, never()).rerank(anyList(), anyString(), anyInt())`
  - [x] 4.6 拒绝/异常路径验证：`verify(rerankingPort, never()).rerank(anyList(), anyString(), anyInt())`

- [x] Task 5: 新增 NoOpRerankingAdapterTest（AC: #5）
  - [x] 5.1 候选数不超过 topN → 返回全部
  - [x] 5.2 候选数超过 topN → 截断到 topN
  - [x] 5.3 null 输入 → 空列表
  - [x] 5.4 空列表输入 → 空列表
  - [x] 5.5 顺序保持（透传行为）

- [x] Task 6: 验证所有测试通过（AC: #4, #5）
  - [x] 6.1 `mvn test "-Dtest=AskQuestionApplicationServiceTest"` — 6 tests, 0 failures
  - [x] 6.2 `mvn test "-Dtest=NoOpRerankingAdapterTest"` — 5 tests, 0 failures

## Dev Notes

### 当前 AskQuestionApplicationService 构造器状态（Story 1.2 完成后）

```java
// 文件: src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java
// 7 个依赖注入参数
public AskQuestionApplicationService(
        ChunkRetrievalPort chunkRetrievalPort,
        RerankingPort rerankingPort,              // ← Story 1.2 新增
        AnswerGenerationPort answerGenerationPort,
        KnowledgeBaseRepository knowledgeBaseRepository,
        CurrentUserProvider currentUserProvider,
        AuthorizationService authorizationService,
        AskableDocumentVersionPort askableDocumentVersionPort)
```

**关键改动：**
- `RerankingPort` 作为第 2 个参数（紧随 `chunkRetrievalPort` 之后），语义上与检索链路相邻
- `handle()` 方法中调用位置：检索后 → 截取 topK → **rerank** → 空检查 → buildPrompt

### 当前 handle() 方法编排流程

```java
// 1. 检索 + 截取
List<RetrievedChunk> matchedChunks = chunkRetrievalPort.similaritySearch(question, retrievalTopK, askableVersionScope)
        .stream().limit(topK).toList();

// 2. 重排序（Story 1.2 新增调用）
matchedChunks = rerankingPort.rerank(matchedChunks, question, topK);

// 3. 空结果兜底
if (matchedChunks.isEmpty()) {
    return new AskQuestionResult(FALLBACK_ANSWER, List.of(), null);
}

// 4. 构造提示词 + LLM 生成
String prompt = buildPrompt(question, matchedChunks);
String answer = answerGenerationPort.generateAnswer(prompt);
```

**注意：** `rerank()` 接收的 `matchedChunks` 已经经过 `limit(topK)` 截取。对于 NoOp 实现，topN >= candidates.size()，所以不会二次截断。未来真正的 Reranker 实现可以重新排序并可能返回不同顺序的结果。

### NoOpRerankingAdapter 实现细节

```java
// 文件: src/main/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapter.java
@Component
public class NoOpRerankingAdapter implements RerankingPort {
    @Override
    public List<RetrievedChunk> rerank(List<RetrievedChunk> candidates, String question, int topN) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (topN >= candidates.size()) return List.copyOf(candidates);
        return List.copyOf(candidates.subList(0, topN));
    }
}
```

**防御性拷贝：** 使用 `List.copyOf()` 返回不可变列表，防止调用方意外修改内部状态。

### 架构约束检查

| 约束 | 状态 |
|------|------|
| domain 层零框架注解 | ✅ RerankingPort 是纯 Java 接口 |
| adapter 不互相引用 | ✅ NoOpRerankingAdapter 无其他 adapter 依赖 |
| application 层只依赖 port 接口 | ✅ AskQuestionApplicationService 只依赖 RerankingPort 抽象 |
| 构造器注入 | ✅ 无 @Autowired 字段注入 |
| Javadoc 含 @author + @since | ✅ 所有新文件 |

### 测试规范

- JUnit 5 + Mockito，纯单元测试（不启动 Spring 上下文）
- 测试类 package-private，与被测类同 package
- `@Test` + `@DisplayName("中文业务描述")`
- 方法命名 `method_shouldExpectedBehavior_whenCondition`
- 每个测试方法只断言一个行为

### Project Structure Notes

- `RerankingPort.java` 路径：`src/main/java/io/github/spike/myai/qa/domain/port/RerankingPort.java`
- `NoOpRerankingAdapter.java` 路径：`src/main/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapter.java`
- `NoOpRerankingAdapterTest.java` 路径：`src/test/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapterTest.java`
- `AskQuestionApplicationService.java` 路径：`src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`
- `AskQuestionApplicationServiceTest.java` 路径：`src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java`

### Story 1.1 前置上下文

- `RetrievedChunk` 已在 Story 1.1 新增 `double score` 字段（默认 0.0）
- `PgVectorChunkRetrievalAdapter.toRetrievedChunk()` 已读取 `Document.getScore()` 映射到 score
- 所有 `RetrievedChunk` 构造器调用已从 7 参更新为 8 参（末尾加 score）
- 本 Story 的 NoOpRerankingAdapterTest 使用 4 参简化构造器（score 默认 0.0），无需额外适配

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 1.2] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#AD-2] — RRF 融合位置决策（RerankingPort 在 RRF 之前）
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-2] — RerankingPort 功能需求
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#数据流] — 完整数据流（步骤 4: RerankingPort）
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#project-structure] — 变更文件清单
- [Source: docs/project-context.md#Java — 六边形架构约束] — port 在 domain，adapter 在 infrastructure
- [Source: docs/project-context.md#Java — 构造器与依赖注入] — 构造器注入规范

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

- RerankingPort 接口定义在 domain/port，零框架注解
- NoOpRerankingAdapter 透传实现，使用 List.copyOf() 防御性拷贝
- AskQuestionApplicationService 在检索后、context 拼接前调用 rerankingPort.rerank()
- 全部 11 个测试通过（AskQuestion: 6, NoOpReranking: 5）
- 审查修复：Javadoc 补全重排序编排步骤

### File List

- src/main/java/io/github/spike/myai/qa/domain/port/RerankingPort.java (new)
- src/main/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapter.java (new)
- src/test/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapterTest.java (new)
- src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java (modified)
- src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java (modified)

## Change Log

- feat(qa): RerankingPort 可插拔接口 + NoOpRerankingAdapter + 应用层集成（2026-06-17）

### Review Findings (2026-06-17)

#### decision_needed

- [x] [Review][Decision] **D1: AskQuestionApplicationService Javadoc 未反映新增重排序步骤** → 修复：类级和 handle() 方法级 Javadoc 均补充重排序编排步骤

#### patch

- [x] [Review][Patch] **P1: 类级 Javadoc 缺少重排序步骤** [AskQuestionApplicationService.java:32-37]
- [x] [Review][Patch] **P2: handle() Javadoc 步骤编号与代码不一致** [AskQuestionApplicationService.java:108-116]
- [x] [Review][Patch] **P3: 测试类中 mock setup 代码重复** [AskQuestionApplicationServiceTest.java] → 选 B：仅标注不重构，与现有 per-method mock 风格一致

#### defer

- [x] [Review][Defer] **W1: 缺少"检索命中空但 scope 非空"路径的 rerank 验证** [AskQuestionApplicationServiceTest.java] — 预存测试缺口，非本次引入
- [x] [Review][Defer] **W2: NoOpRerankingAdapter 未防御 topN <= 0** [NoOpRerankingAdapter.java:21] — 当前调用方保证 topK >= 1，风险极低
