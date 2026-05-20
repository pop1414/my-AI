---
name: to-issues
description: 使用 tracer-bullet vertical slices，把已批准的 Spec 拆成项目 issue tracker 上可独立领取的 issues。Use when user wants to create implementation tickets after Spec or break a functional/technical spec into executable issues.
---

# To Issues

这个 skill 处在文档体系的第五层：`PRD -> ADR -> Plan -> Spec -> Issue`。

它承接已批准的 FS + TS Spec，把功能规则、技术契约和测试策略拆成可独立领取的 issue tracker issues。Issue 负责执行拆分，不重新定义 PRD、ADR、Plan 或 Spec。

Issue tracker 和 triage label vocabulary 应该已经提供给你；如果没有，运行 `/setup-matt-pocock-skills`。

## Process

### 1. Gather context

优先读取：

- 当前 conversation context
- 已批准的 Spec
- 对应 Plan
- 相关 PRD 和 ADR
- 项目 domain glossary / `CONTEXT.md`
- 相关 API contract、architecture docs、runbooks 和 prior issues

如果用户传入 issue number、URL 或 path，只把它作为 source material 的入口；如果 source 是 issue tracker item，读取完整 body 和 comments。

如果没有 Spec，先指出当前缺少文档体系中的 Spec 层，并建议先使用 `to-spec`。只有用户明确要求跳过 Spec 时，才从 Plan 或 PRD 直接拆 issue，并在输出中标记这个假设。

### 2. Explore the codebase (optional)

如果还没探索 codebase，就先探索，以理解代码当前状态。Issue titles 和 descriptions 应使用项目 domain glossary vocabulary，并遵守相关 ADRs。

重点查清：

- Spec 中每个 contract 当前是否已有实现基础
- 相关 modules、integration boundaries 和测试 prior art
- 哪些 slices 可以 AFK 完成，哪些需要 HITL 决策或验收
- 哪些依赖必须先完成，才能让后续 issue 可验证

### 3. Draft vertical slices

把 Spec 拆成 **tracer bullet** issues。每个 issue 都是一个薄 vertical slice，end-to-end 穿过必要 integration layers，而不是某一层的 horizontal slice。

Slices 可以是 `HITL` 或 `AFK`。HITL slices 需要人类交互，例如 architecture decision 或 design review。AFK slices 可以无人交互地实现并合并。尽可能优先 AFK。

<vertical-slice-rules>
- 每个 slice 都交付一条窄但 COMPLETE 的路径，穿过每一层（schema, API, UI, tests）
- 完成后的 slice 自身可 demo 或验证
- 偏好多而薄的 slices，而不是少而厚的 slices
- 每个 slice 都应能追溯到 Spec 中的 FS / TS section
- 不要在 Issue 中重新争论 PRD、ADR 或 Plan decisions；如果发现冲突，先暂停并反馈
</vertical-slice-rules>

### 4. Quiz the user

把 proposed breakdown 作为编号列表展示。每个 slice 显示：

- **Title**：短描述名
- **Type**：HITL / AFK
- **Blocked by**：哪些其他 slices 必须先完成（如果有）
- **Spec coverage**：覆盖哪些 FS / TS section
- **User stories covered**：覆盖哪些 user stories（如果 source material 中有）

询问用户：

- Granularity 是否合适？（too coarse / too fine）
- Dependency relationships 是否正确？
- 是否需要 merge 或继续 split 某些 slices？
- HITL 和 AFK 标记是否正确？
- 是否有 slice 偏离 Spec 或遗漏关键 contract？

迭代直到用户批准 breakdown。

### 5. Publish the issues to the issue tracker

对每个批准的 slice，把新 issue 发布到 issue tracker。使用下面的 issue body template。除非另有指示，AFK issue 应用 `ready-for-agent`；HITL issue 应用 `ready-for-human`。

按 dependency order 发布 issues（blockers first），这样可以在 "Blocked by" 字段引用真实 issue identifiers。

<issue-template>
## Parent

对 issue tracker 中 parent issue 的引用（如果 source 是现有 issue；否则省略本 section）。

## Source

对 Spec 的引用。必要时补充 Plan、PRD 或 ADR 引用。

## What to build

这个 vertical slice 的简洁描述。描述 end-to-end behavior，不要按 layer-by-layer implementation 描述。

避免具体 file paths 或 code snippets；它们很快会过时。例外：如果 prototype 产出的 snippet 比 prose 更精确地编码了某个决策（state machine、reducer、schema、type shape），可以内联在这里，并简短说明它来自 prototype。保留决策密集部分，不要放完整 working demo。

## Spec coverage

- FS:
- TS:

## Acceptance criteria

- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Criterion 3

## Blocked by

- 对 blocking issue 的引用（如果有）

如果没有 blocker，写 "None - can start immediately"。

</issue-template>

不要 close 或 modify 任何 parent issue。
