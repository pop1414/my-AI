# 问答页版本提示与引用版本展示前端收口说明

## 1. 收口范围

本文用于记录 GitHub issue #11《问答页版本提示与引用版本展示前端》的完成情况。

本次收口覆盖问答页引用版本字段展示、stale reference 顶部提示、无引用兜底提示规则、问答页显式入口和对应前端 E2E 测试。#11 依赖的 #10 后端契约已在 GitHub 上关闭，且本地联调期间发现的 PGVector 过滤表达式兼容问题已补齐回归测试。

## 2. 实现落点

- 问答 API client：`web/src/shared/api/qaApi.ts`
- 问答页主界面：`web/src/features/qa/pages/QaPage.tsx`
- 问答页样式：`web/src/features/qa/pages/QaPage.css`
- 控制台问答入口：`web/src/app/ConsoleLayout.tsx`
- 控制台 header 响应式样式：`web/src/index.css`
- 前端 E2E 覆盖：`web/e2e/qa-reference-version.spec.ts`
- PGVector 过滤兼容修复：`src/main/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapter.java`
- PGVector 过滤兼容回归测试：`src/test/java/io/github/spike/myai/qa/infrastructure/retrieval/PgVectorChunkRetrievalAdapterTest.java`

## 3. 验收对照

- 引用来源已从表格升级为卡片展示，卡片展示来源文件名、`documentId`、分块序号、来源版本号和来源更新时间。
- 当引用来源不是对应 `document` 的最新版本时，引用卡片展示“当前最新版本为 vN”的明确提示。
- 问答页顶部仅在本次回答存在引用且 `staleReferences.hasStaleReferences = true` 时展示版本提示。
- 当一次问答没有任何文档引用、仅返回模型兜底内容时，页面展示“无命中”空态，不展示顶部 stale reference 版本提示。
- 前端测试覆盖引用字段展示、stale 顶部提示、不存在 stale 时不提示、无引用兜底不提示，以及控制台顶部问答入口。

## 4. 联调问题与修复摘要

联调时发现问答检索会触发 `Not supported expression type: ISNULL`。根因是版本 1 兼容过滤中使用了 `builder.isNull(...)`，但 Spring AI 1.1.2 的 `PgVectorFilterExpressionConverter` 不支持 `ISNULL`。

修复后，问答检索过滤只生成 PGVector 支持的等值表达式：

- 新向量优先使用 `documentVersionNumber = N`。
- 版本化旧向量兼容 `splitVersion = version-{N}-v1`。
- 历史初始向量兼容 `splitVersion = v1`。
- 新增测试直接调用 `PgVectorFilterExpressionConverter.convertExpression(...)`，防止同类过滤表达式再次导致运行时 500。

该修复解除 #11 前端联调的实际阻塞：前端能稳定拿到 #10 契约中的版本化引用字段与 `staleReferences` 汇总。

## 5. 可访问性与界面收口

- 控制台顶部新增“问答控制台”快捷入口，避免只依赖侧边栏菜单导致入口不明显。
- 引用卡片对长文件名和长 `documentId` 使用换行与复制能力，避免挤压版本标签。
- 引用卡片列表使用自适应网格，窄屏下自动切换为单列。
- stale reference 顶部提示使用 warning 语义，避免和普通引用数量标签混淆。

## 6. 测试结果

执行位置：`web/`

```text
npm.cmd run build
```

结果：通过。`tsc -b && vite build` 成功完成。

```text
npx.cmd eslint src/app/ConsoleLayout.tsx src/features/qa/pages/QaPage.tsx src/shared/api/qaApi.ts e2e/qa-reference-version.spec.ts
```

结果：通过。#11 相关前端文件无 lint error。

```text
npx.cmd playwright test e2e/qa-reference-version.spec.ts
```

结果：通过。`qa-reference-version.spec.ts` 共 4 个用例全部通过。

执行位置：仓库根目录

```text
.\mvnw.cmd -q "-Dtest=PgVectorChunkRetrievalAdapterTest#similaritySearch_shouldBuildPgVectorCompatibleFilterExpression" test
.\mvnw.cmd -q "-Dtest=PgVectorChunkRetrievalAdapterTest" test
.\mvnw.cmd -q "-Dtest=AskQuestionApplicationServiceTest" test
.\mvnw.cmd -q test
```

结果：均已通过。PGVector 兼容修复、问答应用服务和全量后端测试均无回归。

```text
npm.cmd run lint
```

结果：未通过。失败点来自仓库既有页面与共享组件的 React Hooks / Fast Refresh 规则问题，不是本次 #11 修改引入。

## 7. 关闭判断

#11 当前可以关闭。

判断依据：

- #10 后端 blocker 已关闭，版本化问答响应契约稳定。
- #11 的 5 条验收项均已有代码落点与 E2E 覆盖。
- 联调发现的 PGVector `ISNULL` 运行时失败已修复，并有 converter 级回归测试防护。
- 前端入口可见性已补齐，用户可从控制台顶部直接进入问答页。

## 8. 审阅建议

建议按以下顺序审阅：

1. 先看 `web/src/shared/api/qaApi.ts`，确认前端 schema 与 #10 的 `AskResponse.references[]` 和 `staleReferences` 契约一致。
2. 再看 `web/src/features/qa/pages/QaPage.tsx`，重点核对 `shouldShowStaleBanner` 与 `ReferenceCard` 的展示规则。
3. 检查 `web/src/app/ConsoleLayout.tsx` 与 `web/src/index.css`，确认顶栏问答入口只在具备 `canAskQuestion` 能力时出现，且移动端不挤压账号区。
4. 检查 `web/e2e/qa-reference-version.spec.ts`，确认入口、stale、非 stale、无引用兜底四类场景均覆盖。
5. 最后看 `PgVectorChunkRetrievalAdapter.java` 和 `PgVectorChunkRetrievalAdapterTest.java`，确认版本 1 兼容过滤不再生成 PGVector 不支持的 `ISNULL`。
