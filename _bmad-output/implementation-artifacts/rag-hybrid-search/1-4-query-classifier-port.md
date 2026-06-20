---
baseline_commit: 405781a
---

# Story 1.4: 查询分类端口与 QueryType 定义

Status: done

## Story

作为开发者，
我希望系统能对用户查询进行意图分类，
以便不同类型查询走不同的检索策略。

## Acceptance Criteria

1. **Given** domain 层需要查询分类能力
   **When** 新增 `QueryType` 枚举（`qa/domain/model/` 包下）
   **Then** 枚举值严格为 5 个：`FACTOID` / `PROCEDURAL` / `COMPARATIVE` / `CHITCHAT` / `GENERAL`（不多不少）
   **And** 零框架注解
   **And** 每个枚举值含中文 Javadoc 注释说明含义

2. **Given** domain 层需要查询分类端口接口
   **When** 新增 `QueryClassifierPort` 接口（`qa/domain/port/` 包下）
   **Then** 方法签名为 `QueryType classify(String question)`
   **And** 接口零框架注解
   **And** Javadoc 含 `@param question` 和 `@return` 说明

3. **Given** 两个新文件均在 domain 层
   **When** 检查依赖
   **Then** 两个文件均不依赖任何 Spring 或第三方库（仅依赖 `java.lang` 和同包 domain 类型）
   **And** `QueryClassifierPort` import 仅含 `QueryType`（同包无需 import）

4. **Given** 现有测试不受影响
   **When** 运行 `mvn test "-Dtest=AskQuestionApplicationServiceTest"`
   **Then** 所有现有测试通过（本 Story 不修改任何现有文件）

## Tasks / Subtasks

- [x] Task 1: 新增 QueryType 枚举（AC: #1, #3）
  - [x] 1.1 在 `qa/domain/model/` 包下新增 `QueryType.java` 枚举
  - [x] 1.2 枚举值严格为 5 个：`FACTOID` / `PROCEDURAL` / `COMPARATIVE` / `CHITCHAT` / `GENERAL`
  - [x] 1.3 每个枚举值附中文 Javadoc：FACTOID("事实性查询")、PROCEDURAL("操作/步骤查询")、COMPARATIVE("对比查询")、CHITCHAT("闲聊")、GENERAL("通用/兜底查询")
  - [x] 1.4 类级 Javadoc 含 `@author spike` + `@since 1.0.0`
  - [x] 1.5 零框架注解，零外部依赖

- [x] Task 2: 新增 QueryClassifierPort 接口（AC: #2, #3）
  - [x] 2.1 在 `qa/domain/port/` 包下新增 `QueryClassifierPort.java` 接口
  - [x] 2.2 方法签名：`QueryType classify(String question)`
  - [x] 2.3 类级 Javadoc 说明端口用途：查询意图分类，为检索策略路由提供依据
  - [x] 2.4 方法级 Javadoc：`@param question` 用户输入的问题文本，`@return` 分类后的查询类型
  - [x] 2.5 `@author spike` + `@since 1.0.0`
  - [x] 2.6 零框架注解

- [x] Task 3: 验证编译和现有测试（AC: #4）
  - [x] 3.1 `mvn clean compile` — 编译通过
  - [x] 3.2 `mvn test "-Dtest=AskQuestionApplicationServiceTest"` — 所有现有测试通过

## Dev Notes

### 前置故事上下文

- Story 1.1（done）：`RetrievedChunk` 已有 `double score` 字段
- Story 1.2（done）：`RerankingPort` 已定义在 `qa/domain/port/`，`NoOpRerankingAdapter` 已实现
- Story 1.3（done）：`QaRetrievalProperties` 配置外部化已完成，`AskQuestionApplicationService` 当前 8 参数构造器

本 Story 纯新增 2 个 domain 层文件，不修改任何现有文件。

### 当前 AskQuestionApplicationService 构造器状态（Story 1.3 完成后）

```java
// 文件: src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java
// 8 个依赖注入参数
public AskQuestionApplicationService(
        ChunkRetrievalPort chunkRetrievalPort,
        RerankingPort rerankingPort,
        AnswerGenerationPort answerGenerationPort,
        KnowledgeBaseRepository knowledgeBaseRepository,
        CurrentUserProvider currentUserProvider,
        AuthorizationService authorizationService,
        AskableDocumentVersionPort askableDocumentVersionPort,
        QaRetrievalProperties properties)
```

**注意：** 本 Story 不修改此构造器。`QueryClassifierPort` 集成在 Story 1.6 中完成。

### QueryType 枚举设计参考

```java
// 文件: src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java
package io.github.spike.myai.qa.domain.model;

/**
 * 查询意图类型枚举。
 *
 * <p>定义用户查询的意图分类，用于驱动检索策略路由。
 * 由 {@code QueryClassifierPort} 返回，应用层根据类型决定走检索或跳过检索。
 *
 * @author spike
 * @since 1.0.0
 */
public enum QueryType {

    /** 事实性查询 — 询问定义、概念或具体事实，如"什么是向量数据库" */
    FACTOID,

    /** 操作/步骤查询 — 询问操作方法或步骤，如"如何配置 Flyway" */
    PROCEDURAL,

    /** 对比查询 — 比较两个或多个事物，如"Spring AI 和 LangChain 区别" */
    COMPARATIVE,

    /** 闲聊 — 问候、感谢、日常对话，如"你好"、"谢谢" */
    CHITCHAT,

    /** 通用/兜底查询 — 以上均不匹配时的默认分类 */
    GENERAL
}
```

**设计要点：**
- 枚举值命名严格使用 PRD 定义的 5 个值（AD-6），不可用 CHAT、CASUAL、QUESTION 等替代
- 每个枚举值用行内 `/** */` 注释说明含义和示例（而非 block Javadoc）
- 无字段、无构造器、无方法 — 最简枚举

### QueryClassifierPort 接口设计参考

```java
// 文件: src/main/java/io/github/spike/myai/qa/domain/port/QueryClassifierPort.java
package io.github.spike.myai.qa.domain.port;

import io.github.spike.myai.qa.domain.model.QueryType;

/**
 * 查询分类端口（Domain Port）。
 *
 * <p>定义问答流程中对用户查询进行意图分类的能力。
 * 应用层根据分类结果决定检索策略（如 CHITCHAT 跳过检索直接调用 LLM）。
 *
 * <p>实现方应保证分类结果的确定性：相同输入始终返回相同类型。
 *
 * @author spike
 * @since 1.0.0
 */
public interface QueryClassifierPort {

    /**
     * 对用户查询进行意图分类。
     *
     * @param question 用户输入的问题文本
     * @return 分类后的查询类型
     */
    QueryType classify(String question);
}
```

**设计要点：**
- import 仅含 `QueryType`（同包 `domain.port` 和 `domain.model` 在同一子域下，`QueryType` 需要显式 import）
- 方法签名极简：一个 String 入参，一个 QueryType 返回值
- Javadoc 说明"实现方应保证确定性"（为 RuleBasedQueryClassifier 的纯函数特性铺路）

### 命名规范对齐

| 维度 | 现有模式 | 本 Story 对齐 |
|------|---------|--------------|
| Port 命名 | `*Port`（`ChunkRetrievalPort`, `RerankingPort`） | `QueryClassifierPort` ✅ |
| Model 类型 | Java record / 枚举（`RetrievedChunk`, `AskableDocumentVersion`） | Java 枚举 `QueryType` ✅ |
| 包位置 | `domain/port/` 放接口，`domain/model/` 放模型 | 一致 ✅ |
| Javadoc | 中文描述 + `@author spike` + `@since 1.0.0` | 一致 ✅ |
| 零框架 | 无 `@Service`、`@Component`、`@Autowired` | 一致 ✅ |

### 架构约束检查

| 约束 | 状态 |
|------|------|
| domain 层零框架注解 | ✅ 纯 Java 枚举和接口 |
| domain 不依赖 infrastructure | ✅ 无任何 infrastructure import |
| domain 不依赖 Spring | ✅ 仅用 `java.lang` + 同包类型 |
| 六边形 port 定义在 domain 层 | ✅ `QueryClassifierPort` 在 `qa/domain/port/` |
| 枚举值与 PRD 严格一致 | ✅ 5 个值不多不少（AD-6） |

### 测试规范

- 本 Story 纯新增 domain 层文件（枚举 + 接口），无行为逻辑需要测试
- 验证方式：编译通过 + 现有测试不回归
- `QueryType` 和 `QueryClassifierPort` 的行为测试在 Story 1.5（RuleBasedQueryClassifier）中覆盖

### Story 1.5/1.6 衔接说明

- **Story 1.5** 将新增 `RuleBasedQueryClassifier`（`qa/infrastructure/classifier/`），实现 `QueryClassifierPort`，规则引擎纯 Java String/Regex
- **Story 1.6** 将在 `AskQuestionApplicationService` 中注入 `QueryClassifierPort`，CHITCHAT 跳过检索直接调 LLM
- 本 Story 只定义契约（接口 + 枚举），不涉及实现和集成

### Project Structure Notes

- `QueryType.java` 路径：`src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java`（新增）
- `QueryClassifierPort.java` 路径：`src/main/java/io/github/spike/myai/qa/domain/port/QueryClassifierPort.java`（新增）
- 无现有文件修改

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 1.4] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-4] — QueryClassifierPort + QueryType 功能需求
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#QueryType 枚举] — AD-6 枚举值命名锁定
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#project-structure] — 变更文件清单
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#实施顺序] — Phase 2（FR-4, FR-5, FR-6）
- [Source: docs/project-context.md#Java — 六边形架构约束] — port 在 domain，零框架注解
- [Source: src/main/java/io/github/spike/myai/qa/domain/port/RerankingPort.java] — Port 接口模式参考
- [Source: src/main/java/io/github/spike/myai/qa/domain/model/RetrievedChunk.java] — domain model 模式参考

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

（无调试问题）

### Completion Notes List

- 新增 `QueryType` 枚举（`qa/domain/model/`），5 个值严格符合 PRD 定义（AD-6）
- 新增 `QueryClassifierPort` 接口（`qa/domain/port/`），方法签名 `classify(String question) → QueryType`
- 两个文件均为纯 Java，零框架注解、零外部依赖
- 编译通过（337 source files），现有 8 个 AskQuestionApplicationServiceTest 全部通过
- 无现有文件修改，纯增量 Story

### File List

- src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java (new)
- src/main/java/io/github/spike/myai/qa/domain/port/QueryClassifierPort.java (new)

## Change Log

- feat(qa): Story 1.4 — QueryType 枚举 + QueryClassifierPort 接口定义（2026-06-17）
