---
baseline_commit: 5f064d4
---

# Story 1.6: 应用层集成查询分类 — CHITCHAT 拦截

Status: review

## Story

作为用户，
我希望输入闲聊内容时系统跳过检索直接回答，
以便获得更快的响应速度（延迟降低 ≥50%）。

## Acceptance Criteria

1. **Given** `QueryClassifierPort` 和 `RuleBasedQueryClassifier` 已就位（Story 1.4、1.5）
   **When** `AskQuestionApplicationService` 注入 `QueryClassifierPort`
   **Then** 构造器参数从 8 个增加到 9 个（新增 `QueryClassifierPort`）
   **And** 字段声明为 `private final QueryClassifierPort queryClassifierPort`

2. **Given** 用户输入问题
   **When** `handle()` 方法执行
   **Then** 在知识库校验和授权通过后、检索前调用 `queryClassifierPort.classify(question)` 判断查询类型

3. **Given** 查询被分类为 CHITCHAT
   **When** 处理 CHITCHAT 查询
   **Then** 跳过 `ChunkRetrievalPort` 调用（不触发检索）
   **And** 跳过 `RerankingPort` 调用
   **And** 跳过 `askableDocumentVersionPort` 查询
   **And** 直接调用 `answerGenerationPort.generateAnswer(question)` 传入原始问题
   **And** 返回 `AskQuestionResult(answer, List.of())` — references 为空列表，staleReferences 为 null
   **And** 模型返回空/空白时回退到 `FALLBACK_ANSWER`

4. **Given** 查询被分类为非 CHITCHAT（FACTOID / PROCEDURAL / COMPARATIVE / GENERAL）
   **When** 处理非 CHITCHAT 查询
   **Then** 走现有完整检索流程（行为不变）

5. **Given** CHITCHAT 路径的安全约束
   **When** 处理 CHITCHAT 查询
   **Then** 仍执行知识库校验（`validateKnowledgeBase`）和授权检查（`requireCanAskKnowledgeBase`）
   **And** 知识库不存在或停用时抛出对应异常（与非 CHITCHAT 一致）
   **And** 无权限时抛出 AccessDeniedException（与非 CHITCHAT 一致）

6. **Given** `RuleBasedQueryClassifier` 是纯 Java 类（无 Spring 注解）
   **When** 注册为 Spring Bean
   **Then** 添加 `@Component` 注解（与 `NoOpRerankingAdapter` 模式一致）

7. **Given** 测试覆盖要求
   **When** 编写测试
   **Then** CHITCHAT 输入不触发 `ChunkRetrievalPort` 调用（mock `verify(never())`）
   **And** CHITCHAT 输入不触发 `RerankingPort` 调用
   **And** CHITCHAT 输入不触发 `askableDocumentVersionPort` 查询
   **And** CHITCHAT 输入调用 `answerGenerationPort.generateAnswer(question)`
   **And** CHITCHAT 返回结果 references 为空列表
   **And** 非 CHITCHAT 路径行为不变（现有 7 个测试全部通过）

## Tasks / Subtasks

- [x] Task 1: RuleBasedQueryClassifier 添加 `@Component` 注解（AC: #6）
  - [x] 1.1 在 `RuleBasedQueryClassifier` 类上添加 `@Component` 注解
  - [x] 1.2 添加 `import org.springframework.stereotype.Component`
  - [x] 1.3 更新类级 Javadoc 说明该类由 Spring 自动注册

- [x] Task 2: AskQuestionApplicationService 注入 QueryClassifierPort（AC: #1, #2, #3, #4, #5）
  - [x] 2.1 新增 `import io.github.spike.myai.qa.domain.port.QueryClassifierPort` 和 `import io.github.spike.myai.qa.domain.model.QueryType`
  - [x] 2.2 新增字段 `private final QueryClassifierPort queryClassifierPort`
  - [x] 2.3 构造器新增第 1 个参数 `QueryClassifierPort queryClassifierPort`（插在 `chunkRetrievalPort` 之前，保持端口类参数在前）
  - [x] 2.4 在 `handle()` 方法中，授权检查后、`askableDocumentVersionPort` 查询前，插入分类逻辑
  - [x] 2.5 CHITCHAT 路径：跳过检索，直接调用 `answerGenerationPort.generateAnswer(question)` 并返回
  - [x] 2.6 CHITCHAT 返回 `new AskQuestionResult(answer, List.of())`（空引用，staleReferences 为 null）
  - [x] 2.7 CHITCHAT 模型返回空/空白时回退到 `FALLBACK_ANSWER`
  - [x] 2.8 更新类级 Javadoc 描述新增的查询分类步骤

- [x] Task 3: 更新 AskQuestionApplicationServiceTest（AC: #7）
  - [x] 3.1 所有现有 7 个测试方法：构造 service 时新增 `QueryClassifierPort` mock 参数
  - [x] 3.2 所有现有测试：mock `queryClassifierPort.classify()` 返回 `QueryType.GENERAL`（走正常检索路径）
  - [x] 3.3 新增测试：CHITCHAT 输入应跳过检索、直接调用 LLM、返回空引用
  - [x] 3.4 新增测试：CHITCHAT 模型返回空时应返回兜底回答
  - [x] 3.5 新增测试：非 CHITCHAT 输入走完整检索流程（验证 classify 被调用但不影响现有行为）

- [x] Task 4: 验证编译和测试（AC: #7）
  - [x] 4.1 `mvn clean compile` — 编译通过
  - [x] 4.2 `mvn test "-Dtest=AskQuestionApplicationServiceTest"` — 全部测试通过（11 tests, 0 failures）
  - [x] 4.3 `mvn test "-Dtest=RuleBasedQueryClassifierTest"` — 现有测试不受影响（31 tests, 0 failures）

## Dev Notes

### 前置故事上下文

- Story 1.1（done）：`RetrievedChunk` 已有 `double score` 字段
- Story 1.2（done）：`RerankingPort` 已定义，`NoOpRerankingAdapter` 已实现（使用 `@Component` 注解）
- Story 1.3（done）：`QaRetrievalProperties` 配置外部化已完成，构造器 8 参数
- Story 1.4（done）：`QueryType` 枚举 + `QueryClassifierPort` 接口已定义在 domain 层
- Story 1.5（done）：`RuleBasedQueryClassifier` 已实现（纯 Java，零 Spring 注解）

本 Story 是 Epic 1 的最后一个 Story，将查询分类能力集成到应用层，完成 CHITCHAT 拦截。

### 当前 AskQuestionApplicationService 状态（Story 1.3 完成后）

```
文件: src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java
行数: 299 行
构造器: 8 参数（chunkRetrievalPort, rerankingPort, answerGenerationPort, knowledgeBaseRepository, currentUserProvider, authorizationService, askableDocumentVersionPort, properties）
字段: 8 个 private final 字段
```

**本 Story 变更：**
- 新增 1 个字段：`QueryClassifierPort queryClassifierPort`
- 构造器从 8 参数增加到 9 参数
- `handle()` 方法新增 CHITCHAT 分支（在授权检查后、检索前）
- 预计净增 ~30 行代码

### CHITCHAT 路径设计

数据流（与架构文档 data flow 图一致）：

```
用户提问 "你好"
  │
  ▼
AskQuestionApplicationService.handle()
  ├── 1. 输入规范化 + 知识库校验 + 授权检查（不变）
  ├── 2. QueryClassifierPort.classify("你好") → CHITCHAT
  ├── 3. [CHITCHAT] 跳过：askableDocumentVersion / ChunkRetrieval / Reranking
  ├── 4. [CHITCHAT] 直接调用 AnswerGenerationPort.generateAnswer("你好")
  └── 5. 返回 AskQuestionResult(answer, List.of())
```

**关键设计决策：**

1. **CHITCHAT 仍执行知识库校验和授权** — 安全约束不可绕过。用户不能对不存在或无权限的知识库发闲聊
2. **传入原始 question** — CHITCHAT 不构造 context prompt，直接将用户问题传给 LLM
3. **跳过 askableDocumentVersionPort** — CHITCHAT 不需要文档版本信息（references 为空）
4. **空/空白模型回退** — 与现有 FALLBACK_ANSWER 逻辑一致

### handle() 方法修改位置

```java
// 现有代码（不变）：
CurrentUser currentUser = currentUserProvider.requireCurrentUser();
String question = command.normalizedQuestion();
String kbId = command.resolvedKbId();
validateKnowledgeBase(currentUser, kbId);
authorizationService.requireCanAskKnowledgeBase(currentUser, kbId);
int topK = command.resolvedTopK();

// === 新增：查询分类 ===
QueryType queryType = queryClassifierPort.classify(question);
if (queryType == QueryType.CHITCHAT) {
    String answer = answerGenerationPort.generateAnswer(question);
    if (answer == null || answer.isBlank()) {
        answer = FALLBACK_ANSWER;
    }
    return new AskQuestionResult(answer, List.of());
}

// 现有代码（不变）：
List<AskableDocumentVersion> askableVersionScope = ...
```

### 构造器参数顺序

```java
public AskQuestionApplicationService(
        QueryClassifierPort queryClassifierPort,     // 新增：放在首位（端口类参数在前）
        ChunkRetrievalPort chunkRetrievalPort,
        RerankingPort rerankingPort,
        AnswerGenerationPort answerGenerationPort,
        KnowledgeBaseRepository knowledgeBaseRepository,
        CurrentUserProvider currentUserProvider,
        AuthorizationService authorizationService,
        AskableDocumentVersionPort askableDocumentVersionPort,
        QaRetrievalProperties properties)
```

**理由：** 新增的 domain port 参数放在构造器首位，与现有端口参数聚合。这使构造器参数从 8 个变为 9 个。

### Bean 注册模式

`RuleBasedQueryClassifier` 当前是纯 Java 类（零 Spring 注解）。本 Story 需要添加 `@Component` 注解使其被 Spring 自动扫描注册。

**参考模式：** `NoOpRerankingAdapter`（Story 1.2）同样使用 `@Component` 注解注册。

```java
@Component
public class RuleBasedQueryClassifier implements QueryClassifierPort {
```

### 测试设计参考

**现有测试修改要点：**
- 所有 7 个测试方法的 service 构造器调用都需要新增 `QueryClassifierPort` 参数
- mock 默认返回 `QueryType.GENERAL`（不影响现有行为）

```java
// 在每个现有测试中新增：
QueryClassifierPort queryClassifierPort = Mockito.mock(QueryClassifierPort.class);
when(queryClassifierPort.classify(anyString())).thenReturn(QueryType.GENERAL);

// 构造器新增参数：
new AskQuestionApplicationService(
        queryClassifierPort,     // 新增
        chunkRetrievalPort,
        rerankingPort, ...);
```

**新增测试方法：**

```java
@Test
@DisplayName("CHITCHAT 查询应跳过检索直接调用模型并返回空引用")
void handle_shouldSkipRetrievalAndReturnEmptyReferences_whenQueryIsChitchat() {
    // mock queryClassifierPort.classify("你好") → QueryType.CHITCHAT
    // mock answerGenerationPort.generateAnswer("你好") → "你好！有什么可以帮你的吗？"
    // verify: chunkRetrievalPort.similaritySearch 从未被调用
    // verify: rerankingPort.rerank 从未被调用
    // verify: askableDocumentVersionPort 从未被调用
    // verify: answerGenerationPort.generateAnswer 被调用，参数为 "你好"
    // assert: result.answer() == "你好！有什么可以帮你的吗？"
    // assert: result.references() 为空列表
    // assert: result.staleReferences() 为 null
}

@Test
@DisplayName("CHITCHAT 查询模型返回空时应返回兜底回答")
void handle_shouldReturnFallback_whenChitchatAndModelReturnsBlank() {
    // mock queryClassifierPort.classify("你好") → QueryType.CHITCHAT
    // mock answerGenerationPort.generateAnswer("你好") → "" 或 null
    // assert: result.answer() == FALLBACK_ANSWER
    // assert: result.references() 为空列表
}

@Test
@DisplayName("非 CHITCHAT 查询应走完整检索流程且分类不影响结果")
void handle_shouldFollowFullRetrievalFlow_whenQueryIsNotChitchat() {
    // mock queryClassifierPort.classify("什么是 RAG") → QueryType.FACTOID
    // 其余与现有 handle_shouldReturnAnswerAndReferences 测试一致
    // verify: queryClassifierPort.classify 被调用
    // verify: 完整检索流程执行
}
```

### 架构约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| application 层只依赖 domain + port 接口 | ✅ | `QueryClassifierPort` 是 domain port，`QueryType` 是 domain model |
| application 层不引用 infrastructure 类 | ✅ | 不直接引用 `RuleBasedQueryClassifier`，通过 port 接口注入 |
| domain 层零框架注解 | ✅ | `QueryClassifierPort` 和 `QueryType` 不被修改 |
| 不修改现有端口接口 | ✅ | 只修改 `AskQuestionApplicationService`（application 层） |
| 构造器注入 | ✅ | 无 `@Autowired` 字段注入 |
| CHITCHAT 路径安全约束 | ✅ | 知识库校验和授权在分类前执行 |

### Git 情报

最近 5 次提交：
```
5f064d4 feat(qa): Story 1.5 — 规则引擎查询分类器实现
ce6432b feat(qa): Story 1.4 — QueryType 枚举 + QueryClassifierPort 接口定义
405781a feat(qa): Story 1.3 — 检索参数配置外部化（审查修复）
44a147f docs(qa): Story 1.2 状态更新为 done + 审查发现记录
cc675e9 feat(qa): Story 1.2 — RerankingPort 可插拔接口 + 审查修复（Javadoc 补全）
```

**模式：**
- 所有 Story 遵循 `feat(qa): Story X.Y — 描述` 提交格式
- Story 1.5 纯新增，一次性通过无审查修复
- 本 Story 修改现有文件，预期需要审查：构造器参数顺序、CHITCHAT 分支位置

### Project Structure Notes

- 修改文件路径：
  - `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java`（修改）
  - `src/main/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifier.java`（修改 — 添加 `@Component`）
  - `src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java`（修改）
- 不新增文件（所有依赖已在 Story 1.4/1.5 就位）
- `AskQuestionApplicationService` 的构造器变更会影响所有调用方（仅测试文件，Controller 不直接构造）

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 1.6] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-6] — 查询分类集成需求
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#数据流] — CHITCHAT 路径跳过检索直接调 LLM
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#实施顺序] — Phase 2 Step 6（FR-6）
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#project-structure] — 变更文件清单
- [Source: docs/project-context.md#Java — 六边形架构约束] — application 层不引用 infrastructure
- [Source: src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java] — 要修改的服务
- [Source: src/main/java/io/github/spike/myai/qa/domain/port/QueryClassifierPort.java] — 要注入的端口
- [Source: src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java] — CHITCHAT 枚举值
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifier.java] — 要添加 @Component 的类
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapter.java] — @Component 注册模式参考

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- 无调试问题。实现一次性通过，编译和测试均无错误。

### Completion Notes List

- `RuleBasedQueryClassifier` 添加 `@Component` 注解，与 `NoOpRerankingAdapter` 注册模式一致
- `AskQuestionApplicationService` 构造器从 8 参数扩展到 9 参数（新增 `QueryClassifierPort`）
- CHITCHAT 拦截逻辑插入在知识库校验+授权检查之后、检索之前，安全约束不可绕过
- CHITCHAT 路径：`classify(question)` → `generateAnswer(question)` → `AskQuestionResult(answer, List.of())`
- 非 CHITCHAT 路径：行为完全不变
- 现有 8 个测试全部更新（新增 `QueryClassifierPort` mock，返回 `QueryType.GENERAL`）
- 新增 3 个测试：CHITCHAT 跳过检索、CHITCHAT 空回退、非 CHITCHAT 完整流程
- 全量测试 484 pass, 0 fail（AskQuestionApplicationServiceTest 11 tests, RuleBasedQueryClassifierTest 31 tests）

### File List

- src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java (modified)
- src/main/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifier.java (modified)
- src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java (modified)

## Change Log

- feat(qa): Story 1.6 — 应用层集成查询分类 — CHITCHAT 拦截（2026-06-17）
