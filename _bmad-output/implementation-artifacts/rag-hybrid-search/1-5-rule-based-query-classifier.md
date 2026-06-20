---
baseline_commit: ce6432b
---

# Story 1.5: 规则引擎查询分类器实现

Status: done

## Story

作为开发者，
我希望基于优先级规则的查询分类器能区分闲聊、操作、事实、比较等意图，
以便系统根据查询类型选择合适的处理路径。

## Acceptance Criteria

1. **Given** `QueryClassifierPort` 接口和 `QueryType` 枚举已就位（Story 1.4）
   **When** 新增 `RuleBasedQueryClassifier`（`qa/infrastructure/classifier/` 包下）
   **Then** 实现 5 条优先级规则（首个命中即返回）：

   | 优先级 | QueryType | 匹配规则 | 示例 |
   |--------|-----------|---------|------|
   | 1（最高） | CHITCHAT | 问候/感谢/闲聊关键词 | "你好"、"谢谢"、"今天天气" |
   | 2 | PROCEDURAL | 疑问词 + 操作动词 | "如何配置 Flyway"、"怎么实现" |
   | 3 | FACTOID | 疑问词 + 定义/概念 | "什么是向量数据库" |
   | 4 | COMPARATIVE | 比较关键词 | "Spring AI 和 LangChain 区别" |
   | 5（默认） | GENERAL | 以上均不匹配 | "文档管理" |

2. **Given** 该分类器是 infrastructure 层组件
   **When** 检查实现方式
   **Then** 纯 Java String/Regex 实现，零外部依赖，零 Spring 注解
   **And** 实现 `QueryClassifierPort` 接口

3. **Given** 查询可能同时匹配多条规则
   **When** 出现混合意图查询
   **Then** 按优先级匹配（首个命中即返回），有对应测试用例

4. **Given** 边界输入场景
   **When** 输入为空字符串或 null
   **Then** 返回 GENERAL

5. **Given** 分类器需要充分的测试覆盖
   **When** 编写测试
   **Then** 每种 QueryType 至少 3 个测试用例
   **And** CHITCHAT 优先级高于所有其他类型（有专门测试用例）

## Tasks / Subtasks

- [x] Task 1: 新增 RuleBasedQueryClassifier 实现类（AC: #1, #2）
  - [x] 1.1 在 `qa/infrastructure/classifier/` 包下新增 `RuleBasedQueryClassifier.java`
  - [x] 1.2 实现 `QueryClassifierPort` 接口的 `classify(String question)` 方法
  - [x] 1.3 定义 5 条优先级规则（详见 Dev Notes 规则设计参考）
  - [x] 1.4 每条规则使用 `Pattern` 正则匹配，编译为 `private static final Pattern` 常量
  - [x] 1.5 空字符串/null 输入返回 `QueryType.GENERAL`
  - [x] 1.6 类级 Javadoc 含 `@author spike` + `@since 1.0.0`
  - [x] 1.7 零 Spring 注解（`@Component` 由 Story 1.6 或配置类注册）

- [x] Task 2: 新增 RuleBasedQueryClassifierTest 单元测试（AC: #3, #4, #5）
  - [x] 2.1 在 `src/test/java/io/github/spike/myai/qa/infrastructure/classifier/` 下新增测试类
  - [x] 2.2 每种 QueryType 至少 3 个测试用例
  - [x] 2.3 CHITCHAT 优先级测试：同时包含问候和疑问词的输入仍返回 CHITCHAT
  - [x] 2.4 PROCEDURAL vs FACTOID 优先级测试：同时含操作词和定义词时返回 PROCEDURAL
  - [x] 2.5 混合意图优先级测试（覆盖 PROCEDURAL > COMPARATIVE > FACTOID > GENERAL）
  - [x] 2.6 边界测试：null、空字符串、纯标点、超长文本
  - [x] 2.7 测试类 package-private，`@DisplayName` 含中文业务描述

- [x] Task 3: 验证编译和现有测试（AC: #5）
  - [x] 3.1 `mvn clean compile` — 编译通过
  - [x] 3.2 `mvn test "-Dtest=RuleBasedQueryClassifierTest"` — 新增测试全部通过
  - [x] 3.3 `mvn test "-Dtest=AskQuestionApplicationServiceTest"` — 现有测试不受影响

## Dev Notes

### 前置故事上下文

- Story 1.1（done）：`RetrievedChunk` 已有 `double score` 字段
- Story 1.2（done）：`RerankingPort` 已定义在 `qa/domain/port/`，`NoOpRerankingAdapter` 已实现
- Story 1.3（done）：`QaRetrievalProperties` 配置外部化已完成，`AskQuestionApplicationService` 当前 8 参数构造器
- Story 1.4（done）：`QueryType` 枚举 + `QueryClassifierPort` 接口已定义在 domain 层

本 Story 新增 1 个实现类 + 1 个测试类，不修改任何现有文件。集成到 `AskQuestionApplicationService` 在 Story 1.6 完成。

### 当前 AskQuestionApplicationService 构造器状态（Story 1.3 完成后）

```java
// 文件: src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java
// 8 个依赖注入参数，本 Story 不修改
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

**注意：** `QueryClassifierPort` 集成到此服务在 Story 1.6 完成。本 Story 只实现分类器本身。

### RuleBasedQueryClassifier 设计参考

```java
// 文件: src/main/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifier.java
package io.github.spike.myai.qa.infrastructure.classifier;

import io.github.spike.myai.qa.domain.model.QueryType;
import io.github.spike.myai.qa.domain.port.QueryClassifierPort;
import java.util.regex.Pattern;

/**
 * 基于优先级规则的查询分类器。
 *
 * <p>通过正则匹配用户查询中的关键词，按优先级顺序返回首个命中的查询类型。
 * 规则优先级：CHITCHAT > PROCEDURAL > FACTOID > COMPARATIVE > GENERAL。
 * 纯 Java String/Regex 实现，零外部依赖。
 *
 * @author spike
 * @since 1.0.0
 */
public class RuleBasedQueryClassifier implements QueryClassifierPort {

    // === 优先级 1（最高）：CHITCHAT ===
    // 问候/感谢/闲聊关键词，必须在所有其他规则之前匹配
    private static final Pattern CHITCHAT_PATTERN = Pattern.compile(
            "你好|您好|嗨|哈喽|hello|hi|hey|谢谢|感谢|thanks|天气|开心|无聊|哈哈|呵呵|再见|拜拜|辛苦了|辛苦|早安|晚安|早上好|晚上好",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 2：PROCEDURAL ===
    // 疑问词 + 操作动词组合
    private static final Pattern PROCEDURAL_PATTERN = Pattern.compile(
            "(如何|怎么|怎样|如何实现|怎么做|怎么配置|如何使用|如何设置|如何部署|如何安装|如何解决|如何处理|步骤|教程|指南|方法|操作|配置|实现|使用|设置|部署|安装|解决|处理|运行|启动|搭建|迁移|部署)",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 3：FACTOID ===
    // 疑问词 + 定义/概念
    private static final Pattern FACTOID_PATTERN = Pattern.compile(
            "(什么是|什么叫|定义|含义|介绍|解释|说明|概念|意思|是什么|有哪些|哪些|几个|多少|为什么|原理|作用|功能|特点|区别|优缺点)",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 4：COMPARATIVE ===
    // 比较关键词
    private static final Pattern COMPARATIVE_PATTERN = Pattern.compile(
            "(对比|比较|区别|差异|不同|vs|versus|哪个好|哪个更好|优劣|选择|推荐|好还是|还是|以及|和.*区别|与.*对比)",
            Pattern.CASE_INSENSITIVE);

    // === 优先级 5（默认）：GENERAL ===
    // 以上均不匹配时返回 GENERAL，无对应 pattern

    /**
     * {@inheritDoc}
     *
     * <p>按优先级依次匹配规则，首个命中即返回。
     * 空字符串或 null 返回 {@link QueryType#GENERAL}。
     */
    @Override
    public QueryType classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryType.GENERAL;
        }
        if (CHITCHAT_PATTERN.matcher(question).find()) {
            return QueryType.CHITCHAT;
        }
        if (PROCEDURAL_PATTERN.matcher(question).find()) {
            return QueryType.PROCEDURAL;
        }
        if (FACTOID_PATTERN.matcher(question).find()) {
            return QueryType.FACTOID;
        }
        if (COMPARATIVE_PATTERN.matcher(question).find()) {
            return QueryType.COMPARATIVE;
        }
        return QueryType.GENERAL;
    }
}
```

**设计要点：**

- **Pattern 常量**：所有 `Pattern` 编译为 `private static final`，避免每次 classify 重复编译
- **`Pattern.CASE_INSENSITIVE`**：支持中英文混合查询（"Hello" vs "hello"）
- **CHITCHAT 最高优先级**：即使闲聊中包含疑问词（如"你好吗，怎么了"），仍归类为 CHITCHAT
- **PROCEDURAL > FACTOID**：同时含"如何"和"什么是"时返回 PROCEDURAL（操作优先）
- **正则设计**：CHITCHAT 用精确匹配词列表，PROCEDURAL/FACTOID/COMPARATIVE 用关键词组匹配
- **零框架注解**：无 `@Component`、`@Service`——纯 POJO，注册在 Story 1.6 或配置类中
- **接口实现**：`implements QueryClassifierPort`，来自 `qa/domain/port/`

**重要：** 上面的正则模式仅作设计参考，实际实现时需根据测试用例微调关键词覆盖。关键是保证优先级顺序正确（CHITCHAT > PROCEDURAL > FACTOID > COMPARATIVE > GENERAL）。

### 测试设计参考

```java
// 文件: src/test/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifierTest.java
package io.github.spike.myai.qa.infrastructure.classifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.spike.myai.qa.domain.model.QueryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RuleBasedQueryClassifier 单元测试。
 */
class RuleBasedQueryClassifierTest {

    private final RuleBasedQueryClassifier classifier = new RuleBasedQueryClassifier();

    // --- CHITCHAT 测试（至少 3 个） ---
    // "你好"、"谢谢"、"今天天气怎么样"、纯表情等

    // --- PROCEDURAL 测试（至少 3 个） ---
    // "如何配置 Flyway"、"怎么实现向量检索"、"Spring Boot 部署步骤" 等

    // --- FACTOID 测试（至少 3 个） ---
    // "什么是向量数据库"、"PGVector 是什么"、"RAG 的原理" 等

    // --- COMPARATIVE 测试（至少 3 个） ---
    // "Spring AI 和 LangChain 区别"、"对比 Redis 和 Memcached" 等

    // --- GENERAL 测试（至少 3 个） ---
    // "文档管理"、"数据存储"、"系统架构" 等无明确意图的关键词

    // --- 优先级测试 ---
    // CHITCHAT > PROCEDURAL: "你好，请问如何配置..." → CHITCHAT
    // PROCEDURAL > FACTOID: "什么是配置 Flyway 的方法" → PROCEDURAL
    // PROCEDURAL > COMPARATIVE: "如何对比 A 和 B" → PROCEDURAL

    // --- 边界测试 ---
    // null → GENERAL
    // "" → GENERAL
    // "   " → GENERAL
    // 纯标点 "?!@#" → GENERAL
    // 超长文本（1000+ 字符含关键词）→ 正确分类
}
```

**测试要点：**
- 每个测试方法只验证一个场景（project-context.md 规则）
- 方法命名 `classify_shouldReturnXxx_whenYyy`
- `@DisplayName("中文业务描述")`，含业务关键词
- 优先级测试必须覆盖 CHITCHAT > PROCEDURAL > COMPARATIVE > FACTOID > GENERAL 的顺序
- 边界输入不抛异常，统一返回 GENERAL

### 架构约束检查

| 约束 | 状态 | 说明 |
|------|------|------|
| infrastructure 层实现 port 接口 | ✅ | `RuleBasedQueryClassifier implements QueryClassifierPort` |
| 实现类在 `qa/infrastructure/classifier/` | ✅ | 架构文档明确指定此包路径 |
| 零 Spring 注解 | ✅ | 纯 Java 类，无 `@Component`/`@Service` |
| domain 层不被修改 | ✅ | 本 Story 不修改任何 domain 文件 |
| 不修改现有文件 | ✅ | 纯新增 2 个文件 |
| 零外部依赖 | ✅ | 仅使用 `java.util.regex.Pattern` |

### Git 情报

最近 5 次提交：
```
ce6432b feat(qa): Story 1.4 — QueryType 枚举 + QueryClassifierPort 接口定义
405781a feat(qa): Story 1.3 — 检索参数配置外部化（审查修复）
44a147f docs(qa): Story 1.2 状态更新为 done + 审查发现记录
cc675e9 feat(qa): Story 1.2 — RerankingPort 可插拔接口 + 审查修复（Javadoc 补全）
7165f79 feat(qa): Story 1.1 — RetrievedChunk 添加 score 字段 + 审查修复
```

**模式：**
- 前 4 个 Story 都遵循 `feat(qa): Story X.Y — 描述` 提交格式
- 审查修复作为同提交追加（如 Story 1.3）或独立提交
- 纯新增文件（1.1, 1.4）不需要审查修复
- 本 Story 纯新增，预期一次性通过

### Project Structure Notes

- 新增文件路径：
  - `src/main/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifier.java`（新增）
  - `src/test/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifierTest.java`（新增）
- `qa/infrastructure/classifier/` 目录本 Story 首次创建
- 不修改任何现有文件

### References

- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/epics.md#Story 1.5] — Story 定义与 AC
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/prd.md#FR-5] — RuleBasedQueryClassifier 功能需求
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#QueryType 枚举] — AD-6 枚举值命名锁定
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#project-structure] — 变更文件清单
- [Source: _bmad-output/planning-artifacts/rag-hybrid-search/architecture.md#实施顺序] — Phase 2 Step 5（FR-5）
- [Source: docs/project-context.md#Java — 六边形架构约束] — port 在 domain，实现类在 infrastructure
- [Source: docs/project-context.md#测试规则] — JUnit 5 + Mockito 规范
- [Source: src/main/java/io/github/spike/myai/qa/domain/port/QueryClassifierPort.java] — 要实现的接口
- [Source: src/main/java/io/github/spike/myai/qa/domain/model/QueryType.java] — 分类结果枚举
- [Source: src/main/java/io/github/spike/myai/qa/infrastructure/reranking/NoOpRerankingAdapter.java] — infrastructure adapter 模式参考

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1M][1m]

### Debug Log References

- 初版 PROCEDURAL 模式未覆盖英文关键词（"How to configure Flyway" → GENERAL）→ 补充 `how to|how do|how can|step|tutorial|guide`
- 优先级测试输入 "介绍 Redis 和 Memcached 的区别" 中 FACTOID 的 "介绍" 位置更左（position 0）→ 改用无 FACTOID 前缀的输入

### Completion Notes List

- 新增 `RuleBasedQueryClassifier`（`qa/infrastructure/classifier/`），实现 `QueryClassifierPort` 接口
- 5 条优先级规则：CHITCHAT > PROCEDURAL > FACTOID > COMPARATIVE > GENERAL
- 正则模式编译为 `private static final Pattern`，避免每次 classify 重复编译
- 支持中英混合查询（`Pattern.CASE_INSENSITIVE` + 英文关键词）
- 零 Spring 注解、零外部依赖，纯 Java 实现
- 31 个测试用例全部通过，现有 8 个 AskQuestionApplicationServiceTest 无回归

### File List

- src/main/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifier.java (new)
- src/test/java/io/github/spike/myai/qa/infrastructure/classifier/RuleBasedQueryClassifierTest.java (new)

## Change Log

- feat(qa): Story 1.5 — 规则引擎查询分类器实现（2026-06-17）
