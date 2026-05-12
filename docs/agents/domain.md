# Domain Docs

Engineering skills 探索 codebase 时，应如何消费这个 repo 的 domain documentation。

## Before exploring, read these

- repo 根目录的 **`CONTEXT.md`**
- **`docs/adr/`** — 当前仓库的架构决策记录
- 与当前工作主题相关的 runbooks、plans、reference 文档

如果这些文件不存在，静默继续。当前仓库已经提供 `CONTEXT.md`，相关 skills 应优先用它恢复仓库级语义，再结合 `docs/adr/` 与专题文档进入具体实现区域。

## File structure

本仓库按 Single-context repo 处理：

```text
/
├── AGENTS.md
├── CONTEXT.md                ← 仓库级领域上下文真源
├── docs/
│   ├── adr/
│   ├── agents/
│   ├── runbooks/
│   └── reference/
└── src/
```

## Use the glossary's vocabulary

相关 skills 应优先使用 `CONTEXT.md` 中定义的术语；当某个概念在 `CONTEXT.md` 中尚未明确展开时，再沿用 ADR、runbook、计划文档中已经稳定使用的领域词汇，避免引入新的同义表达。

## Flag ADR conflicts

如果输出与现有 ADR 矛盾，应明确指出冲突，而不是静默覆盖。
