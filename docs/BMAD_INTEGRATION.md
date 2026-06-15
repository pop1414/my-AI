# BMad Integration for AI-Native 文档系统

> BMad 是复杂工程工作的展开、审计和执行系统；AI-native 文档系统是长期真相源。
>
> 核心规则：BMad 负责展开、审计和执行；AI-native 文档负责裁决、沉淀和控制。

---

## 1. 适用场景

当工作只需要普通 AI-native 文档链时，不需要启用 BMad。

适合启用 BMad 的情况：

- 涉及数据库 schema、认证、权限、安全、支付、文件上传或数据迁移。
- 涉及多个 API、页面、模块或服务。
- 存在难回退架构决策。
- 交付顺序和依赖关系重要。
- 需要 readiness、review 或 retrospective 降低实现风险。

---

## 2. 真相源规则

```text
docs/ = 正式知识库和长期约束
_bmad-output/ = BMad 过程产物、质量门证据、story 执行历史
```

长期真相放在 `docs/`：

- `CONTEXT.md`：稳定领域语言和边界。
- `docs/adr/`：长期技术决策。
- `docs/features/<feature>/PRD.md`：产品范围和用户价值。
- `docs/features/<feature>/SPEC.md`：行为契约、技术契约、边界情况和验证命令。
- `docs/features/<feature>/issues/`：AI-native 实现切片，或 BMad story cycle 中的控制面板项。

BMad 产物放在 `_bmad-output/`。它们可以提出更好的结论，但结论必须晋升到 `docs/` 后，才成为正式约束。

如果 BMad 输出和 `docs/` 冲突：

1. 停止实现。
2. 判断 BMad 输出是否更正确。
3. 如果更正确，先更新 `docs/`。
4. 再继续实现。

不要让 BMad 过程产物和 `docs/` 平级竞争。

---

## 3. Context Adapter

`CONTEXT.md` 是 canonical context。

如果某个 BMad agent 或工作流需要 `project-context.md`，它只能作为 BMad adapter：

- 指向 `CONTEXT.md`、`docs/adr/`、相关 PRD 和 SPEC。
- 提供启动摘要。
- 声明冲突时以 `CONTEXT.md` 和 `docs/` 为准。

不要在 `project-context.md` 中复制完整长期真相源。否则它会和 `CONTEXT.md` 形成第二套上下文。

---

## 4. 晋升规则

| BMad 产物 | 常见来源 skill / workflow | 晋升位置 | 晋升条件 |
| --- | --- | --- | --- |
| BMad PRD | `bmad-prd` | `docs/features/<feature>/PRD.md` | 范围、用户价值、非目标、验收标准稳定 |
| Architecture | `bmad-create-architecture` | `docs/adr/` 或 `SPEC.md` | 出现长期技术取舍、接口约束、数据模型约束 |
| Epics / Stories | `bmad-create-epics-and-stories` | `SPEC.md`、BMad story cycle 或 local issues | 稳定约束进入 `SPEC.md`；执行队列按所选模式进入 stories 或 issues |
| Readiness report | `bmad-check-implementation-readiness` | `PRD.md`、`SPEC.md`、ADR 或 issue 阻塞项 | 发现实现前必须解决的缺口 |
| Sprint status | `bmad-sprint-planning` | `_bmad-output/`，必要时同步 issue 状态 | 用于执行跟踪，不直接定义功能契约 |
| Story file / Dev record | `bmad-create-story`、`bmad-dev-story` | `_bmad-output/`，必要时同步 `SPEC.md` 或 control issue | 发现 story 上下文、实现记录、验证证据或跨 story follow-up |
| Code review | `bmad-code-review` | 修复提交、issue 或 `SPEC.md` | 发现行为、质量或验证缺口 |
| Change proposal | `bmad-correct-course` | `PRD.md`、`PLAN.md`、`SPEC.md`、ADR 或 control issue | 需求、架构、UX 或交付计划需要改道 |
| Checkpoint / QA evidence | `bmad-checkpoint-preview`、`bmad-qa-generate-e2e-tests` | `SPEC.md`、issue、测试代码或 review notes | 发现验证缺口、关键路径测试缺口或需要人工确认的风险 |
| Retrospective | `bmad-retrospective` | `CONTEXT.md`、ADR、SPEC 或工作流文档 | 形成可复用经验或长期规则 |

“常见来源 skill / workflow”只说明该类 BMad 产物通常从哪里来，不改变权威顺序。只要结论要长期约束实现，就必须晋升到 `docs/`。

晋升时优先使用对应 AI-native producer skill：

| 正式产物 | 推荐 skill |
| --- | --- |
| `CONTEXT.md` / ADR | `grill-with-docs` |
| `PRD.md` | `to-prd` |
| `PLAN.md` | `to-plan` |
| `SPEC.md` | `to-spec` |
| `issues/` | `to-issues` |

---

## 5. Issue / Story 分层

PRD、Architecture、Readiness、Review 和 Retro 的冲突都可以通过“BMad 输出 -> AI-native docs 晋升”解决。

真正需要避免双主的是执行队列，因为 local issues 和 BMad stories 都可能回答：

- 下一个 agent 实现什么。
- 当前任务是什么状态。
- follow-up 放在哪里。

### 5.1 AI-native 实现模式

当使用 AI-native issues 作为执行队列：

```text
docs/features/<feature>/issues/*.md
-> tdd / diagnose / implementation agent
-> 验证
```

每个 issue 是一个可独立实现和验收的 vertical slice。

### 5.2 BMad Story Cycle 模式

当启用 BMad story cycle：

```text
执行编排层：
_bmad-output/implementation-artifacts/sprint-status.yaml
_bmad-output/implementation-artifacts/<story>.md

控制面板层：
docs/features/<feature>/issues/*.md
```

BMad `sprint-status.yaml` 和 story files 是主执行队列。local issues 不再逐 story 复制，只记录控制面板项：

- blocker（阻塞项）
- HITL decision（人工裁决项）
- cross-story risk（跨 Story 风险）
- tech debt（技术债）
- review follow-up（评审跟进项）
- documentation fix（文档修正项）

硬规则：

- 不要让 local issues 和 BMad stories 同时定义同一个功能的执行顺序。
- 不要为每个 BMad story 创建重复 issue。
- 当前 story 内可直接完成的问题，写入 story tasks 或 story follow-up。
- 影响多个 stories、需要人类裁决、改变长期契约或不适合塞进单条 story 的事项，才创建 local issue。

---

## 6. Level 3 Workflow

完整节点流程、完成标准和下一步 skill 见 `LEVEL3_BMAD_WORKFLOW.md`。

最小纪律：

- 进入实现前必须有 `SPEC.md`，且包含具体验证命令。
- Readiness 放在初步晋升之后、最终锁定之前。
- 实现阶段只保留一个执行主线：local issues 或 BMad story cycle。
- 每个节点结束时必须知道完成证据、下一步 skill 和阻塞项。
