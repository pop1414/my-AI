# my-AI Git 工作流

## 1. 这份文档是干什么的

这份文档用来规定 `my-AI` 项目里如何使用 Git 支持长期开发。
目标不是追求很重的大团队流程，而是建立一套适合当前项目阶段的、轻量但工程化的工作方式。

这套工作流主要解决下面几个问题：

1. 如何避免所有改动长期堆在 `main`
2. 如何把功能、文档、课程材料区分开来
3. 如何让提交历史对未来的自己有用
4. 如何在快速迭代时，仍然保留清晰的里程碑

一句话原则：

> Git 的目标不是“把代码存起来”，而是把每一轮有意义的变化组织成可回看、可合并、可讲述的历史。

## 2. 当前仓库适用的开发模型

`my-AI` 当前是单人主导、快速演进、同时包含代码与文档的大项目。因此推荐使用下面的模型：

- `main`：主线分支，保持相对稳定
- 短命主题分支：
    - `feature/*`
    - `docs/*`
    - `fix/*`
    - `refactor/*`
    - `spike/*（实验性探索）`
- 里程碑通过 `tag` 标记
- 必要时保留极少量 `snapshot/*` 分支

这意味着：

- 平时不要长期在 `main` 上堆很多未完成改动
- 每次做一个明确主题时，都应该拉一个分支
- 做完后合回 `main`

## 3. 分支职责

### 3.1 `main`

`main` 是当前主线，要求：

- 始终代表“最近相对稳定”的项目状态
- 尽量保持可运行、可阅读、可回看
- 不长期堆积多个未完成主题

适合直接在 `main` 上做的事情只有非常小的修改，例如：

- 明显错字
- 小型文档链接修复
- 10 分钟内能完成并提交的微调

如果改动已经具备“明确主题”，就不建议继续直接堆在 `main` 上。

### 3.2 `feature/*`

用于新功能或新页面开发。

典型例子：

- `feature/qa-page`
- `feature/knowledge-page`
- `feature/v1-closure`
- `feature/rag-access-control`

适用场景：

- 新增页面
- 新增接口联调
- 新增业务能力
- 某个阶段收口

### 3.3 `docs/*`

用于文档体系建设和大块文档更新。

典型例子：

- `docs/document-workflow`
- `docs/git-workflow`
- `docs/course-material-setup`

适用场景：

- 新建文档结构
- 增加文档规范
- 大批量同步 README / runbook / learning

### 3.4 `fix/*`

用于修复明确问题。

典型例子：

- `fix/qa-empty-state`
- `fix/upload-status-sync`

适用场景：

- Bug 修复
- 小范围行为纠正
- 前后端状态不一致修复

### 3.5 `refactor/*`

用于不直接新增能力的结构调整。

典型例子：

- `refactor/frontend-api-layer`
- `refactor/qa-service-boundary`

适用场景：

- 包结构整理
- 模块拆分
- 提炼复用逻辑

### 3.6 `spike/*`

用于试验性探索。

典型例子：

- `spike/reranker-eval`
- `spike/auth-model-options`

适用场景：

- 技术验证
- 方案探索
- 还不确定是否正式进入主线的实验

规则：

- `spike/*` 可以失败
- 实验结论如果成立，应转成正式计划 / ADR / feature 分支工作

### 3.7 `snapshot/*`

仅用于阶段冻结，不建议滥用。

适用场景：

- 课程提交前冻结
- 某次答辩前保留快照
- 某个可演示版本需要长期留存

规则：

- `snapshot/*` 不是日常开发分支
- 能用 `tag` 的场景，优先用 `tag`

## 4. 什么时候一定要建分支

下面这些情况，建议一定建分支：

1. 做一个明确功能
2. 改一个完整页面
3. 做一次结构性文档改造
4. 做一次可能持续好几天的任务
5. 做一个可能反复试错的大主题

简单判断规则：

> 如果三天后你会想单独回看这次改动，就应该建分支。

## 5. 什么时候可以不建分支

下面这些小改动可以直接在 `main` 上处理：

- 错字修复
- 单个链接修复
- 很小的注释修正
- 极小的文档补充

前提：

- 改动足够小
- 不涉及多个主题
- 能很快完成并提交

只要开始出现“这一轮其实有一个主题”，就应该切分支。

## 6. 推荐命名规范

分支命名规则：

- 英文路径式命名
- 短而明确
- 用主题词，不要用模糊词

推荐格式：

- `feature/<topic>`
- `docs/<topic>`
- `fix/<topic>`
- `refactor/<topic>`
- `spike/<topic>`
- `snapshot/<topic>`

例子：

- `feature/qa-page`
- `feature/knowledge-page`
- `feature/v1-closure`
- `docs/document-workflow`
- `docs/git-workflow`
- `fix/chunks-preview-pagination`
- `refactor/knowledge-boundary`

不推荐：

- `test`
- `update`
- `new`
- `final`
- `ddd`

## 7. 标准开发流程

### 7.1 开始一个主题前

先同步主线：

```bash
git switch main
git pull
```

然后创建主题分支：

```bash
git switch -c feature/qa-page
```

### 7.2 开发过程中

不要等所有东西都做完再一次性提交。尽量按有意义的阶段提交，例如：

1. 骨架提交
2. 主要功能提交
3. 文档同步提交
4. learning 沉淀提交

### 7.3 功能完成后

先检查：

- 代码是否可运行
- 文档是否需要同步
- 当前分支提交历史是否表达清楚

然后回到主线合并：

```bash
git switch main
git merge feature/qa-page
```

如果分支历史过碎，也可以在合并前整理提交。

## 8. 提交粒度与提交信息

### 8.1 提交粒度原则

一次提交最好只表达一件事。
不是“今天改了很多东西”，而是“这一提交完成了一个明确变化”。

好的提交粒度例子：

- 搭建一个页面骨架
- 接通一个 API
- 修复一个明确 bug
- 同步一组正式文档
- 增加一篇 learning 文档

不好的提交粒度例子：

- 页面 + 后端 + 文档 + 样式 + 课程材料全塞一起
- “顺手”改了很多不相关内容

### 8.2 推荐提交信息格式

推荐沿用当前仓库已经在使用的风格：

- `feat(...)`
- `fix(...)`
- `refactor(...)`
- `docs(...)`
- `chore(...)`

例子：

- `feat(web): add qa page skeleton`
- `feat(web): integrate ask api and reference rendering`
- `docs(runbook): add v1 closure checklist`
- `docs(workflow): add project git workflow`
- `fix(web): handle empty references state`

不推荐：

- `update`
- `修改`
- `继续开发`
- `fix bug`

## 9. 代码和文档如何一起提交

这是 `my-AI` 里非常重要的规则。

### 9.1 当文档描述的是这次代码变化的当前事实

应该和代码在同一主题分支里完成。
必要时可以分多个 commit，但应属于同一轮工作。

例如：

- 页面范围变了 -> 更新 `README` / `web/README`
- 接口字段变了 -> 更新 `docs/04-api-contract.yaml`
- 架构边界变了 -> 更新 `docs/03-architecture.md`

### 9.2 当文档本身是独立治理改动

可以单独建 `docs/*` 分支和单独提交。

例如：

- 文档结构重组
- 文档工作流
- Git 工作流
- 新建学习沉淀层

### 9.3 Learning 文档

建议和对应功能在同一主题分支内完成，但可以单独一个 commit。
这样既保留关联性，又方便未来回看。

## 10. 里程碑与 Tag 规则

对于 `my-AI`，重要阶段不要只靠提交记忆，建议用 tag 标记。

适合打 tag 的场景：

- V1 收口完成
- 课程提交版本
- 某次答辩版本
- 某个企业级里程碑版本

例子：

```bash
git tag -a v1-closure -m "V1 closure demo-ready state"
git tag -a course-submission-v1 -m "Course submission version"
```

规则：

- Tag 用来标记阶段成果
- `snapshot/*` 分支只在确实需要保留一个长期冻结分支时使用

优先级建议：

1. 优先用 `tag`
2. 只有在需要长期保留一条冻结分支时，才用 `snapshot/*`

## 11. 针对当前项目的具体建议

结合 `my-AI` 当前状态，建议使用下面的分支节奏：

### 文档治理类

- `docs/document-workflow`
- `docs/git-workflow`

### V1 收口类

- `feature/v1-closure`
- `feature/knowledge-page`
- `feature/qa-page`

### 后续演进类

- `feature/rag-access-control`
- `feature/document-list`
- `feature/kb-management`

推荐策略：

- 大主题可以开一个总分支，例如 `feature/v1-closure`
- 里面如果再拆子任务，可以继续细分更小功能分支
- 如果你不想太复杂，也可以直接一个功能一个分支，不再加总分支

## 12. 当前这次改动应该怎么提交

你现在这轮改动属于非常典型的文档治理工作，建议：

### 分支名

```bash
docs/document-workflow
```

### 提交信息

```bash
docs(workflow): add documentation structure and project workflows
```

如果已经在 `main` 上改了，也没关系。
可以直接从当前工作区切出去：

```bash
git switch -c docs/document-workflow
```

当前未提交改动会一起带到新分支。

## 13. 每次提交前的最小检查

建议以后每次提交前，至少过一遍：

1. 这次提交是不是只表达一个主题
2. 是否混入了不相关文件
3. 如果改了系统事实，文档是否同步
4. 如果做了关键决策，是否需要 ADR
5. 提交信息是否能让未来的自己一眼看懂

## 14. 最终原则

如果以后只记住几条规则，就记住下面这些：

1. `main` 尽量保持稳定
2. 一个明确主题一个分支
3. 一个提交只表达一件事
4. 系统事实变化时，代码和真源文档一起更新
5. 里程碑优先用 `tag`

对 `my-AI` 来说，最好的 Git 状态不是“分支很多”，而是：

- 你知道为什么要开这个分支
- 你能从提交历史中看出项目是怎么长起来的
- 你能把每一轮工作单独讲清楚
