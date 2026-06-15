# Triage Labels

Skills 使用五个 canonical triage roles。这个文件把这些 roles 映射到此 repo issue tracker 中实际使用的 label 字符串。

| Canonical triage role | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

当某个 skill 提到 role（例如 "apply the AFK-ready triage label"）时，使用此表中对应的 label 字符串。

编辑右侧列，使其匹配你实际使用的 vocabulary。

## BMad control issue kinds

如果当前 feature 启用了 BMad story cycle，issue 还应标明控制项类型。控制项类型不是 triage state；它说明为什么这个事项没有放进单条 BMad story。

| Issue kind | 中文术语 | Default handling |
| ---------- | -------- | ---------------- |
| `blocker` | 阻塞项 | 通常不能标记为 `ready-for-agent`，先解除阻塞 |
| `HITL decision` | 人工裁决项 | 等待人类决策，通常是 `needs-info` 或 `ready-for-human` |
| `cross-story risk` | 跨 Story 风险 | 需要在多个 stories 间同步处理 |
| `tech debt` | 技术债 | 独立排期，不阻塞当前 story 时不要打断 story cycle |
| `review follow-up` | 评审跟进项 | 只有脱离当前 story 的 follow-up 才进入 issue |
| `documentation fix` | 文档修正项 | 优先回写 `PRD.md`、`SPEC.md`、ADR 或 `CONTEXT.md` |
