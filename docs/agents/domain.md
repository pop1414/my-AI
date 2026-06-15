# Domain Docs

Engineering skills 探索 codebase 时，应如何消费这个 repo 的 domain documentation 和 feature documentation。

## Before exploring, read these

- repo 中实际存在的 agent adapter 文件，例如 **`AGENTS.md`**、**`CLAUDE.md`**、`.cursor/rules/` 或 `.github/copilot-instructions.md`，了解工具限制和验证命令
- **`docs/agents/document-system.md`**、**`docs/agents/domain.md`**、**`docs/agents/issue-tracker.md`** 和 **`docs/agents/triage-labels.md`**，了解本 repo 的 skill 配置
- **`docs/AI_DOCUMENT_SYSTEM.md`**，了解项目采用的 AI-native 文档结构
- 如果启用了 BMad Level 3，读取 **`docs/BMAD_INTEGRATION.md`** 和 **`docs/LEVEL3_BMAD_WORKFLOW.md`**
- repo 根目录的 **`CONTEXT.md`**
- **`docs/adr/`** — 读取与你即将处理区域相关的 ADRs。在 multi-context repos 中，也检查 `src/<context>/docs/adr/` 中的 context-scoped decisions。
- 当前 feature 的 **`docs/features/<feature>/PRD.md`**、**`SPEC.md`**、可选 **`PLAN.md`** 和 **`issues/`**。

如果这些文件不存在，**静默继续**。不要标记缺失；不要提前建议创建。producer skill（`/grill-with-docs`、`/to-prd`、`/to-plan`、`/to-spec`）会在 terms、decisions 或 feature docs 实际需要时懒创建它们。

## File structure

Single-context repo（大多数 repos）：

```
/
├── AGENTS.md or another agent adapter (optional)
├── CONTEXT.md
├── docs/
│   ├── AI_DOCUMENT_SYSTEM.md
│   ├── agents/
│   │   ├── document-system.md
│   │   ├── domain.md
│   │   ├── issue-tracker.md
│   │   └── triage-labels.md
│   ├── BMAD_INTEGRATION.md             ← optional BMad Level 3 extension
│   ├── LEVEL3_BMAD_WORKFLOW.md         ← optional BMad Level 3 extension
│   ├── adr/
│   │   ├── 0001-event-sourced-orders.md
│   │   └── 0002-postgres-for-write-model.md
│   ├── plans/
│   └── features/
│       └── <feature>/
│           ├── PRD.md
│           ├── SPEC.md
│           └── issues/
└── src/
```

## Use the glossary's vocabulary

当你的输出命名某个 domain concept 时（issue title、refactor proposal、hypothesis、test name），使用 `CONTEXT.md` 中定义的 term。不要漂移到 glossary 明确避免的 synonyms。

如果你需要的概念还不在 glossary 中，这是一个信号：要么你正在发明项目没有使用的语言（重新考虑），要么确实存在缺口（为 `/grill-with-docs` 记录）。

## Flag ADR conflicts

如果你的输出与现有 ADR 矛盾，明确指出，而不是静默覆盖：

> _Contradicts ADR-0007 (event-sourced orders) — but worth reopening because…_

## Use feature docs

- Product scope belongs in `docs/features/<feature>/PRD.md`
- Functional and technical contracts belong in `docs/features/<feature>/SPEC.md`
- Work breakdown belongs in `docs/features/<feature>/issues/`
- Single-feature phasing belongs in `docs/features/<feature>/PLAN.md` only when needed
- Cross-feature plans belong in `docs/plans/`
