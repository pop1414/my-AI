---
title: 'PR-Ready 修复：架构违规 + limit 顺序 + 虚拟线程 Executor'
type: 'refactor'
created: '2026-06-20'
status: 'done'
baseline_commit: 'a065f5f'
context:
  - 'docs/project-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** PR 就绪评估发现 3 个问题：(1) `AskQuestionApplicationService` 直接 import `infrastructure.config.QaRetrievalProperties`，违反六边形架构 application → infrastructure 规则；(2) `limit(topK)` 在 reranking 之前执行，截断了 reranker 输入池，削弱 reranking 扩展能力；(3) `HybridChunkRetrievalAdapter` 的 `CompletableFuture.supplyAsync()` 使用默认 ForkJoinPool 执行阻塞 JDBC，有线程饥饿风险。

**Approach:** (1) 在 `domain/port/` 新增 `RetrievalConfigPort` 接口，`QaRetrievalProperties` 实现该 port，application service 改为依赖 port；(2) 将 `limit(topK)` 从 retrieval 之后、reranking 之前，移到 reranking 之后；(3) 在 `HybridChunkRetrievalAdapter` 中注入虚拟线程 `Executor`，传给 `supplyAsync`。

## Boundaries & Constraints

**Always:**
- 六边形架构：domain 层零框架注解，application 层不 import infrastructure 类
- Java record + 构造器注入，禁止 `@Autowired` 字段注入
- `RetrievalConfigPort` 定义在 `domain/port/`，只有 getter 方法（`minCandidates()`、`candidateMultiplier()`）
- `QaRetrievalProperties` 现有 `@ConfigurationProperties` + `@Validated` + Lombok 保持不变，仅追加 `implements RetrievalConfigPort`
- 虚拟线程 Executor 通过构造器注入（非内部创建），便于测试

**Ask First:** 无

**Never:**
- 不修改 YAML 配置值或 Spring Boot 配置前缀
- 不引入新依赖（虚拟线程是 Java 21 标准库）
- 不修改检索逻辑本身（RRF、Sparse、Dense 不变）
- 不修改 Flyway migration

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 正常问答路径 | question + valid kbId + topK=5 | retrieval 返回 retrievalTopK 条 → rerank 全量候选 → limit 后返回 topK 条 | N/A |
| Reranker 返回少于 topK | rerank 返回 3 条，topK=5 | 返回 3 条（不补 null） | N/A |
| Reranker 返回多于 topK | rerank 返回 20 条，topK=5 | 返回 5 条 | N/A |
| 配置默认值验证 | 未配置 YAML | `minCandidates=20`, `candidateMultiplier=4` | @Validated 校验范围 |

</frozen-after-approval>

## Code Map

- `src/main/java/io/github/spike/myai/qa/domain/port/` -- 新增 `RetrievalConfigPort.java`
- `src/main/java/io/github/spike/myai/qa/infrastructure/config/QaRetrievalProperties.java` -- 追加 `implements RetrievalConfigPort`
- `src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java` -- import 替换 + limit 顺序调整
- `src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapter.java` -- 注入 Executor 替代 ForkJoinPool
- `src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java` -- import 替换
- `src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapterTest.java` -- 适配 Executor 注入

## Tasks & Acceptance

**Execution:**
- [x] `domain/port/RetrievalConfigPort.java` -- 新增接口，两个 getter 方法 `minCandidates()` 和 `candidateMultiplier()` -- 消除 application → infrastructure 架构违规
- [x] `infrastructure/config/QaRetrievalProperties.java` -- 追加 `implements RetrievalConfigPort`，Lombok @Getter 自动满足接口契约 -- 零改动适配
- [x] `application/service/AskQuestionApplicationService.java` -- 替换 import 为 `RetrievalConfigPort`，字段类型改接口；将 `.limit(topK)` 从 reranking 前移到 reranking 后 -- 修复架构违规 + 修复 limit 顺序
- [x] `infrastructure/retrieval/HybridChunkRetrievalAdapter.java` -- 构造器注入 `Executor`，`supplyAsync` 改用注入的 executor -- 消除 ForkJoinPool 阻塞 IO 风险
- [x] `application/service/AskQuestionApplicationServiceTest.java` -- 替换 `QaRetrievalProperties` 引用为 `RetrievalConfigPort`（匿名实现或 record 适配） -- 测试通过
- [x] `infrastructure/retrieval/HybridChunkRetrievalAdapterTest.java` -- 适配构造器新增 Executor 参数 -- 测试通过

**Acceptance Criteria:**
- Given `AskQuestionApplicationService` 编译通过，when 检查 import 列表，then 不包含 `io.github.spike.myai.qa.infrastructure.*` 任何引用
- Given retrieval 返回 N 条候选（N > topK），when reranking 后执行 limit，then reranker 看到完整 N 条候选而非截断后的 topK 条
- Given `HybridChunkRetrievalAdapter` 执行混合检索，when 检查 supplyAsync 调用，then 使用注入的 Executor 而非 ForkJoinPool.commonPool()
- Given 所有现有测试，when 运行 `mvn test "-Dtest=AskQuestionApplicationServiceTest,HybridChunkRetrievalAdapterTest"`，then 全部通过

## Spec Change Log

- **[patch][Finding #2]** `rerank()` 调用传入 `topK` 导致 NoOpRerankingAdapter 内部截断到 topK，reranker 仍未看到完整候选池 → 改为传 `retrievalTopK`，测试 verify 值同步更新 → known-bad: `rerank(matchedChunks, question, topK)` 使 limit 移位失去实际效果；KEEP: limit(topK) 在 rerank 之后执行的顺序

## Design Notes

**RetrievalConfigPort 最小接口设计：**

```java
public interface RetrievalConfigPort {
    int minCandidates();
    int candidateMultiplier();
}
```

方法名使用 record 风格（无 `get` 前缀），与项目中其他 port 保持一致。`QaRetrievalProperties` 的 Lombok `@Getter` 生成的 `getMinCandidates()` 不匹配，需要显式 override 两个方法委托给字段，或改用 record 风格的 getter。最简方案：在 `QaRetrievalProperties` 中手动添加两个 default 实现方法。

**Executor 注入策略：**

使用 `Executor` 接口（非 `ExecutorService`），Spring Boot 可通过 `@Bean` 提供虚拟线程 Executor：

```java
@Bean
Executor virtualThreadExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

测试中注入 `Runnable::run`（同步执行）或 `MoreExecutors.directExecutor()` 等价物。

## Verification

**Commands:**
- `mvn clean compile` -- expected: BUILD SUCCESS（编译通过，无架构违规 import）
- `mvn test "-Dtest=AskQuestionApplicationServiceTest,HybridChunkRetrievalAdapterTest"` -- expected: 全部测试通过
- `mvn test "-Dtest=!MyAiApplicationTests"` -- expected: 全部单元测试通过（回归验证）

## Suggested Review Order

**架构违规修复 — RetrievalConfigPort 抽取**

- 新增 domain port 接口，两个 getter 方法
  [`RetrievalConfigPort.java:16`](../../../src/main/java/io/github/spike/myai/qa/domain/port/RetrievalConfigPort.java#L16)

- 追加 `implements RetrievalConfigPort`，Lombok @Getter 自动满足契约
  [`QaRetrievalProperties.java:27`](../../../src/main/java/io/github/spike/myai/qa/infrastructure/config/QaRetrievalProperties.java#L27)

- import 替换 + 字段类型改为 port 接口 + rerank 传入 retrievalTopK
  [`AskQuestionApplicationService.java:24`](../../../src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java#L24)

**limit 顺序修复 — reranking 后截断**

- rerank 传 retrievalTopK（完整候选池），limit(topK) 在其后执行
  [`AskQuestionApplicationService.java:167`](../../../src/main/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationService.java#L167)

**虚拟线程 Executor — 消除 ForkJoinPool 阻塞 IO**

- 虚拟线程 @Bean 定义
  [`QaAsyncConfiguration.java:30`](../../../src/main/java/io/github/spike/myai/qa/infrastructure/config/QaAsyncConfiguration.java#L30)

- 构造器注入 Executor，supplyAsync 使用注入的 executor
  [`HybridChunkRetrievalAdapter.java:72`](../../../src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapter.java#L72)

**测试适配**

- QaRetrievalProperties → RetrievalConfigPort 匿名实现
  [`AskQuestionApplicationServiceTest.java:541`](../../../src/test/java/io/github/spike/myai/qa/application/service/AskQuestionApplicationServiceTest.java#L541)

- 构造器适配 Runnable::run 同步执行器
  [`HybridChunkRetrievalAdapterTest.java:34`](../../../src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/HybridChunkRetrievalAdapterTest.java#L34)
