# Skill Workflow: AI-Native Document System

This repo uses `docs/AI_DOCUMENT_SYSTEM.md` as the source of truth for its document structure. Engineering skills cooperate through one document chain instead of inventing separate formats.

Agent entry files such as `AGENTS.md`, `CLAUDE.md`, Cursor rules, or Copilot instructions are adapters only. They should point agents to this file and sibling files in `docs/agents/`; they are not the canonical document system.

## Standard chain

```text
agent adapter
  -> docs/agents/*.md
  -> CONTEXT.md + docs/adr/
  -> docs/features/<feature>/PRD.md
  -> docs/plans/<plan>.md or docs/features/<feature>/PLAN.md (optional)
  -> docs/features/<feature>/SPEC.md
  -> docs/features/<feature>/issues/<NN>-slug.md
```

## Skill responsibilities

| Skill | Output | Use when |
| ----- | ------ | -------- |
| `setup-ai-document-system` | agent adapter pointer and `docs/agents/*.md` | Configuring skill behavior for this repo |
| `grill-with-docs` | `CONTEXT.md`, `docs/adr/` | Stress-testing terminology, boundaries, and durable decisions |
| `to-prd` | `docs/features/<feature>/PRD.md` | Turning conversation context into product requirements |
| `to-plan` | `docs/plans/<plan>.md` or `docs/features/<feature>/PLAN.md` | Turning PRD/Spec/release goals into phased execution plans |
| `to-spec` | `docs/features/<feature>/SPEC.md` | Turning PRD/Plan into functional and technical contracts |
| `to-issues` | `docs/features/<feature>/issues/` | Turning Spec/Plan into executable vertical slices |

## Optional BMad extension

If this repo enables BMad Level 3, read:

1. `docs/BMAD_INTEGRATION.md`
2. `docs/LEVEL3_BMAD_WORKFLOW.md`

Authority order:

```text
docs/ = formal knowledge base and long-term constraints
_bmad-output/ = BMad process artifacts, quality evidence, story execution history
```

Core rule: BMad expands, audits, and executes; AI-native docs decide, preserve, and control.

When BMad output conflicts with `docs/`, stop implementation. If BMad is more correct, promote the conclusion into `docs/` first, then continue.

### Execution queue rule

Only one system owns the execution queue for a feature:

- **AI-native implementation mode**: `docs/features/<feature>/issues/*.md` are the executable vertical slices.
- **BMad story cycle mode**: `_bmad-output/implementation-artifacts/sprint-status.yaml` and story files are the execution orchestration layer.

In BMad story cycle mode, issues are a control panel only. Create issues only for:

- blocker
- HITL decision
- cross-story risk
- tech debt
- review follow-up
- documentation fix

Do not duplicate every BMad story as an issue.

## Coordination rules

- `to-prd` records product intent and scope, not API contracts or implementation details.
- `to-plan` is optional; create a Plan only for cross-phase, cross-feature, release-level, or large single-feature sequencing.
- `to-plan` does not define API contracts, state machines, data models, or issue-level task lists; those belong in `to-spec` and `to-issues`.
- `to-spec` records behavior, API, state, data shape, error handling, and executable verification commands.
- `to-issues` should prefer `SPEC.md` as source material. If no Spec exists, create a Spec first or create only a HITL issue to write the Spec.
- If BMad story cycle owns execution, `to-issues` should create only control panel issues, not story duplicates.
- `grill-with-docs` updates `CONTEXT.md` and ADRs only when terminology or durable decisions actually crystallize.
- New markdown docs must use the paths and responsibility boundaries in `docs/AI_DOCUMENT_SYSTEM.md`; concrete output shape is owned by the corresponding producer skill.
