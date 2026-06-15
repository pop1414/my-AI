# Level 3：BMad + AI-native 文档工作流

> 这份文档定义严肃项目中 BMad 与 AI-native 文档系统的节点流程。
>
> 真相源、晋升和 Issue / Story 分层规则见 `BMAD_INTEGRATION.md`。

---

## 1. 核心模型

Level 3 是在 AI-native 的 `PRD -> SPEC -> issues -> 实现 -> 验证` 前后加入 BMad 的复杂性展开、readiness 检查、story cycle 和评审闭环。

```text
BMad 展开复杂性
-> 初步晋升到 docs/
-> readiness 检查
-> 最终晋升并锁定 docs/
-> 选择 AI-native issues 或 BMad story cycle
-> review / retro 反向更新 docs/
```

BMad 不替代文档系统。BMad 负责提出、拆解、检查和执行记录；`docs/` 负责成为长期真相源。

---

## 2. 两种模式

### 2.1 轻量 Level 3

适合个人项目的大多数复杂功能。BMad 负责规划和检查，实现仍用 AI-native issues。

```text
bmad-prd
-> bmad-create-architecture
-> bmad-create-epics-and-stories
-> 初步晋升到 docs/
-> bmad-check-implementation-readiness
-> 最终晋升并锁定 docs/
-> to-issues
-> tdd / diagnose
```

### 2.2 完整 Level 3

适合高风险、长周期、架构和交付顺序都重要的项目。

```text
bmad-prd
-> bmad-create-architecture
-> bmad-create-epics-and-stories
-> 初步晋升到 docs/
-> bmad-check-implementation-readiness
-> 最终晋升并锁定 docs/
-> bmad-sprint-planning
-> bmad-create-story
-> bmad-dev-story
-> bmad-code-review
-> bmad-retrospective
```

不确定时先选轻量 Level 3。只有当 story 上下文、执行顺序、评审记录本身能明显降低风险时，才进入完整 BMad story cycle。

---

## 3. 节点总览

| 节点 | 目标 | 首选 skill | 完成标准 | 下一步 |
| --- | --- | --- | --- | --- |
| 0. 入口判断 | 确认是否需要 Level 3 | `bmad-help` 或人工判断 | 触发条件明确，feature slug 明确，模式明确 | 1 或 2 |
| 1. 探索补充 | 补足产品、市场、领域或技术未知 | research / brief 类 BMad skills | 足够进入 PRD，不再只是模糊想法 | 2 |
| 2. BMad PRD | 展开复杂产品范围 | `bmad-prd` | 范围、非目标、用户故事、验收标准清楚 | 3 |
| 3. BMad 架构 | 展开技术路线和取舍 | `bmad-create-architecture` | 架构方案、约束、风险、关键决策清楚 | 4 |
| 4. BMad Epics/Stories | 拆复杂工作和依赖 | `bmad-create-epics-and-stories` | stories 覆盖 PRD 和架构约束 | 5 |
| 5. 初步晋升 | 把稳定结论落到 docs 草案 | `to-prd`、`to-spec`、`to-issues`、`grill-with-docs` | `docs/` 中有可检查的 PRD / SPEC / ADR / issues 草案 | 6 |
| 6. Readiness | 实现前找缺口 | `bmad-check-implementation-readiness` | 无 blocker，或 blocker 已处理并回写 docs | 7 |
| 7. 最终锁定 | 固定正式真相源 | AI-native skills 或手写整理 | `docs/` 和 BMad 输出一致，SPEC 有验证命令 | 8A 或 8B |
| 8A. AI-native 实现 | 用 local issues 实现 | `tdd`、`diagnose` | issue 验收完成且验证通过 | 10 |
| 8B. BMad Sprint | 进入 story cycle | `bmad-sprint-planning` | `sprint-status.yaml` 存在且顺序可读 | 9 |
| 9. Story Cycle | 逐个 story 实现和评审 | `bmad-create-story`、`bmad-dev-story`、`bmad-code-review` | story 验收通过，评审问题关闭或记录 | 10 或下个 story |
| 10. 复盘回写 | 把经验变成系统规则 | `bmad-retrospective`、`grill-with-docs` | 长期经验写回 `CONTEXT.md`、ADR、SPEC 或工作流文档 | 结束或下个 epic |

---

## 4. 重点节点

### 节点 0：入口判断

进入 Level 3 的典型触发：

- 涉及数据库 schema、认证、权限、安全、支付、文件上传或数据迁移。
- 涉及多个 API、页面、模块或服务。
- 架构决策难以回退。
- 交付顺序重要。
- 需要正式 readiness 或 review gate。

如果只是普通功能，回到 AI-native Level 2。Level 3 不是默认流程。

完成标准：

- feature slug 明确。
- Level 3 触发条件明确。
- 选择轻量 Level 3 或完整 Level 3。
- 下一步 skill 唯一且明确。

### 节点 1：探索补充

节点 1 只在问题还不够清楚时使用。它的目标是补足进入 PRD 前的未知，不是提前写实现方案。

适合补充的未知：

- 用户问题、目标用户、使用场景不清楚。
- 业务领域或市场背景不足。
- 技术方向存在多个候选但缺少基本调研。
- 现有系统边界不清楚。

可用 skill：

- `bmad-agent-analyst`
- `bmad-product-brief`
- `bmad-market-research`
- `bmad-domain-research`
- `bmad-technical-research`
- `bmad-document-project`

完成标准：

- 已能说清问题、目标、范围和主要未知。
- 不再只是想法描述。
- 可以进入 BMad PRD。

### 节点 2：BMad PRD

BMad PRD 负责展开复杂产品范围。它不是最终真相源，最终约束仍要晋升到 `docs/features/<feature>/PRD.md`。

重点产出：

- 用户问题。
- 解决方案。
- 用户故事。
- 功能范围。
- 非目标。
- 业务验收标准。
- 明确的开放问题。

不要在 BMad PRD 中把 API、数据库结构或内部实现写成最终契约。这些内容只能作为后续 Architecture 或 Spec 的输入。

完成标准：

- 范围和非目标清楚。
- User stories 覆盖主要用户路径。
- Acceptance criteria 可以被后续 Spec 细化。
- 未决问题被显式列出。

### 节点 3：BMad 架构

BMad 架构负责展开技术路线、约束、风险和关键取舍。

重点产出：

- 系统边界和模块关系。
- 数据模型或状态模型约束。
- API、集成、权限、安全、存储等技术方向。
- 风险和缓解措施。
- 难回退技术取舍。

晋升规则：

- 难回退且有真实取舍的决策进入 ADR。
- 行为、接口、数据、状态、错误处理等实现契约进入 `SPEC.md`。
- 只影响执行顺序的内容进入 Plan 或 stories。
- 仍未裁决的问题进入 readiness blocker 或 local control issue。

完成标准：

- 架构方案能支撑 PRD 范围。
- 关键风险和取舍可见。
- 哪些内容进 ADR、哪些进 SPEC 已经可判断。

### 节点 4：BMad Epics / Stories

节点 4 把复杂工作拆成 epics 和 stories，并暴露依赖关系。

它最关键的判断是后续执行主线：

- 如果 stories 只是帮助理解拆分，后续可走 AI-native issues。
- 如果需要 story 上下文、执行顺序和评审记录来降低风险，后续走 BMad story cycle。

完成标准：

- Stories 覆盖 PRD 和架构约束。
- 依赖顺序可读。
- 每个 story 有可验收边界。
- 已决定后续使用 AI-native issues 还是 BMad story cycle。

注意：此时不要急着把每个 BMad story 复制成 local issue。执行主线要到节点 7 锁定后再确定。

### 节点 5：初步晋升

目标是把 `_bmad-output/` 的稳定结论整理成 `docs/` 草案，让 readiness 能同时检查 BMad 材料和正式文档系统。

| 正式文档 | 推荐方式 | 来源 |
| --- | --- | --- |
| `PRD.md` | `to-prd` | BMad PRD |
| `SPEC.md` | `to-spec` | BMad PRD、Architecture、Epics/Stories |
| `issues/` | `to-issues` | AI-native 实现时创建 vertical slices；BMad story cycle 中只创建控制面板 issues |
| `CONTEXT.md` | `grill-with-docs` 或手写 | 稳定领域语言 |
| ADR | `grill-with-docs` 或手写 | 难回退技术取舍 |

完成标准：

- `PRD.md`、`SPEC.md`、必要 ADR 已存在或有清晰草案。
- 如果选择 AI-native 实现，候选 issues 已能表达 vertical slices。
- 如果选择 BMad story cycle，控制面板 issues 的使用边界已明确。

### 节点 6：Readiness

`bmad-check-implementation-readiness` 必须放在初步晋升之后、最终锁定之前。

```text
先初步晋升
再 readiness
再最终晋升
```

原因：

- 不先初步晋升，readiness 只能检查 BMad 自己的材料，无法发现它和正式文档是否冲突。
- 最终锁定后才 readiness，发现问题时返工成本更高。

如果有 blocker，按类型回写：

- 产品范围问题回到 `PRD.md`。
- 架构问题回到 ADR 或 `SPEC.md`。
- 验收问题回到 `SPEC.md`。
- 拆分问题回到 BMad stories。
- 跨 story 阻塞、人工决策或长期风险进入 local issues。

### 节点 7：最终锁定

最终锁定后，`docs/` 是实现依据：

- `PRD.md` 回答为什么做、做什么、不做什么。
- `SPEC.md` 回答行为、边界、接口、数据、错误场景和验证命令。
- ADR 回答为什么选择这个架构。
- issues 在 AI-native 实现路径中表达可独立实现和验证的 vertical slices；在 BMad story cycle 中只作为控制面板。

如果 BMad 输出和 `docs/` 冲突，以 `docs/` 为准；如果 BMad 更正确，先更新 `docs/`，再实现。

完成标准：

- `docs/` 与 BMad 输出一致。
- `SPEC.md` 有可执行验证命令。
- 实现主线已确定：AI-native issues 或 BMad story cycle。

### 节点 8A：AI-native 实现

适合轻量 Level 3。

```text
to-issues
-> 选择一个 ready-for-agent issue
-> tdd / diagnose 实现
-> 运行 SPEC / issue 中的验证命令
-> 更新 issue 状态
```

完成标准：

- issue acceptance criteria 全部满足。
- 验证通过。
- 如果实现中发现文档问题，已更新 `SPEC.md`、ADR 或 issue。

### 节点 8B/9：BMad Story Cycle

适合完整 Level 3。

```text
bmad-sprint-planning
-> bmad-create-story
-> bmad-dev-story
-> bmad-code-review
-> 下一个 story 或 retrospective
```

完成标准：

- `sprint-status.yaml` 存在，能识别下一个 backlog story。
- story 文件包含 acceptance criteria、tasks/subtasks、Dev Notes 和相关上下文。
- `bmad-dev-story` 完成代码、测试、验证和 story 记录。
- `bmad-code-review` 的 P0/P1 问题已修复或明确阻塞。

进入 BMad story cycle 后，只保留一个执行主线：

```text
_bmad-output/implementation-artifacts/sprint-status.yaml
-> _bmad-output/implementation-artifacts/<story>.md
-> bmad-dev-story
-> bmad-code-review
```

local issues 只作为控制面板。影响多个 stories、需要人类裁决、改变长期契约或不适合打断当前 story 时，才创建 local issue。

### 节点 10：复盘回写

节点 10 把一次 epic、story cycle 或重要实现中的经验沉淀回文档系统。

适合回写的内容：

- 新的稳定领域术语或边界进入 `CONTEXT.md`。
- 难回退技术取舍进入 ADR。
- 新发现的行为规则、验证命令或边界情况进入 `SPEC.md`。
- 工作流层面的规则进入文档系统说明。
- 未完成但独立存在的后续事项进入 local issues。

完成标准：

- BMad retrospective 中的长期结论已进入正确文档层。
- 临时过程记录没有被误当成长期真相源。
- 下一轮 epic 或 feature 的入口明确。

---

## 5. 可选节点

| 节点 | 使用条件 | 首选 skill |
| --- | --- | --- |
| QA Generate E2E Tests | 已实现功能需要补 API / E2E 自动化测试，尤其是关键路径 | `bmad-qa-generate-e2e-tests` |
| Checkpoint Preview | 人类想快速理解一组改动、PR 前检查或中途检查 | `bmad-checkpoint-preview` |
| Correct Course | 实现中发现需求、架构、UX 或 story 拆分需要重大调整 | `bmad-correct-course` |

这些节点不替代 `bmad-code-review`。如果问题改变了契约或架构，结论必须回写 `SPEC.md` 或 ADR。

---

## 6. 节点完成检查

每个节点结束时问：

```text
这个节点是否产生了它应该产生的文档或状态？
这个产物是否进入了正确位置？
是否还有 blocker？
下一步 skill 是否唯一且明确？
如果新开一个 AI 会话，它是否能只读文档继续？
```

如果最后一个问题答案是否定的，说明当前节点没完成。

---

## 7. 最小纪律

- BMad 输出不是正式真相源，`docs/` 才是。
- 晋升时优先使用 `to-prd`、`to-spec`、`to-issues`、`grill-with-docs`。
- `bmad-check-implementation-readiness` 放在初步晋升之后、最终锁定之前。
- 进入实现前必须有 `SPEC.md`，且包含具体可执行的验证命令。
- 实现阶段只保留一个执行主线：local issues 或 BMad story cycle。
- 启用 BMad story cycle 时，不要把每个 BMad story 复制成 local issue。
- 每个节点结束时必须知道完成证据、下一步 skill 和阻塞项。
