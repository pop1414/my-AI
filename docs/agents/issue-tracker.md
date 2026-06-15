# Issue tracker: Local Markdown

这个 repo 的 PRD、Spec 和 issues 作为 markdown 文件维护在 `docs/features/<feature>/` 中。

## Conventions

- 每个 feature 一个目录：`docs/features/<feature-slug>/`
- PRD 是 `docs/features/<feature-slug>/PRD.md`
- Spec 是 `docs/features/<feature-slug>/SPEC.md`
- 单功能阶段计划是 `docs/features/<feature-slug>/PLAN.md`
- Implementation issues 是 `docs/features/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 开始编号
- 跨 feature / release 计划放在 `docs/plans/`
- Triage state 记录为每个 issue file 顶部附近的 `Status:` 行（role 字符串见 `triage-labels.md`）
- Comments 和 conversation history 追加到 issue 文件底部的 `## Comments` heading 下

## When a skill says "publish to the issue tracker"

在 `docs/features/<feature-slug>/issues/` 下创建新 issue 文件（必要时创建目录）。

如果当前 feature 启用了 BMad story cycle，不要为每个 BMad story 创建重复 issue。此时 `_bmad-output/implementation-artifacts/sprint-status.yaml` 和 story files 是执行队列；这里只创建控制面板项：

- blocker（阻塞项）
- HITL decision（人工裁决项）
- cross-story risk（跨 Story 风险）
- tech debt（技术债）
- review follow-up（评审跟进项）
- documentation fix（文档修正项）

## When a skill says "create a PRD, Plan, or Spec"

- PRD 写入 `docs/features/<feature-slug>/PRD.md`
- 单功能 Plan 写入 `docs/features/<feature-slug>/PLAN.md`
- 跨功能或版本级 Plan 写入 `docs/plans/<plan-slug>.md`
- Spec 写入 `docs/features/<feature-slug>/SPEC.md`

## When a skill says "fetch the relevant ticket"

读取引用路径处的文件。用户通常会直接传入 `docs/features/<feature-slug>/...` 下的路径，或提供 feature slug 和 issue number。
