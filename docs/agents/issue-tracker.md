# Issue tracker: GitHub

这个 repo 的 issues 和 PRDs 存放在 GitHub Issues 中。相关 skills 需要读写任务时，应以 GitHub Issues 为准。

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`
- **Read an issue**: `gh issue view <number> --comments`
- **List issues**: `gh issue list --state open`
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

从 `git remote -v` 推断 repo；在当前 clone 内运行时，`gh` 会自动定位到 `pop1414/my-AI`。

## When a skill says "publish to the issue tracker"

创建一个 GitHub issue。

## When a skill says "fetch the relevant ticket"

运行 `gh issue view <number> --comments`。
