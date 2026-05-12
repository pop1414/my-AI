# Domain Docs

Engineering skills 探索 codebase 时，应如何消费这个 repo 的 domain documentation。

## Before exploring, read these

- repo 根目录的 **`CONTEXT.md`**（如果存在）
- **`docs/adr/`** — 当前仓库的架构决策记录
- 与当前工作主题相关的 runbooks、plans、reference 文档

如果这些文件不存在，静默继续。当前仓库尚未提供 `CONTEXT.md`，应优先参考 `docs/adr/` 与现有文档结构。

## File structure

本仓库按 Single-context repo 处理：

```text
/
├── AGENTS.md
├── CONTEXT.md                ← 未来可补充的仓库级领域上下文
├── docs/
│   ├── adr/
│   ├── agents/
│   ├── runbooks/
│   └── reference/
└── src/
```

## Use the glossary's vocabulary

当仓库后续补充 `CONTEXT.md` 后，相关 skills 应优先使用其中定义的术语。在此之前，优先沿用 ADR、runbook、计划文档中已经稳定使用的领域词汇，避免引入新的同义表达。

## Flag ADR conflicts

如果输出与现有 ADR 矛盾，应明确指出冲突，而不是静默覆盖。
