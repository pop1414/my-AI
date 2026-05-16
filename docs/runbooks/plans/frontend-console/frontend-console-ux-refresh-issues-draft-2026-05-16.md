# 本地 Issue 草案：前端控制台体验重构与双视角收束

## 生成信息

- 生成日期：2026-05-16
- 来源 PRD：`docs/runbooks/plans/frontend-console/frontend-console-ux-refresh-prd.md`
- 当前状态：已上传 GitHub Issues
- 目标标签：后续上传 GitHub 时建议使用 `ready-for-agent`

## 拆分原则

- 按 tracer bullet 思路拆分，每个 issue 都交付一条可验证的窄路径。
- 优先使用 AFK slices，避免把一次性 shell 重构、正文读取专项和 E2E 混在同一条实现线上。
- 每个 slice 都应穿过当前页面的布局、权限显隐、交互状态和测试层，而不是只做样式或只做组件抽象。
- 本草案不直接创建 GitHub issue；后续上传时需要把本地编号替换成真实 issue 编号，并按依赖顺序发布。
- 本轮严格排除 `#26-30` 正文读取专项，只处理前端控制台外层体验、双视角结构和治理工作台收束。

## 总览

| 本地编号 | GitHub Issue | 标题 | 类型 | 标签 | 阻塞 |
| -------- | ------------ | ---- | ---- | ---- | ---- |
| FCUX-01 | [#33](https://github.com/pop1414/my-AI/issues/33) | 控制台外层导航与共享页面骨架基线 | AFK | `ready-for-agent` | 无 |
| FCUX-02 | [#34](https://github.com/pop1414/my-AI/issues/34) | 问答控制台核对工作台重构 | AFK | `ready-for-agent` | #33 |
| FCUX-03 | [#35](https://github.com/pop1414/my-AI/issues/35) | 知识库任务入口与治理增强重构 | AFK | `ready-for-agent` | #33 |
| FCUX-04 | [#36](https://github.com/pop1414/my-AI/issues/36) | 文档列表核对优先与治理入口重构 | AFK | `ready-for-agent` | #33 |
| FCUX-05 | [#37](https://github.com/pop1414/my-AI/issues/37) | 文档上传工作流闭环重构 | AFK | `ready-for-agent` | #33, #36 |
| FCUX-06 | [#38](https://github.com/pop1414/my-AI/issues/38) | 文档详情双视角外层工作台重构 | AFK | `ready-for-agent` | #33, #36 |
| FCUX-07 | [#39](https://github.com/pop1414/my-AI/issues/39) | 文档工具页收纳与详情联动收口 | AFK | `ready-for-agent` | #37, #38 |
| FCUX-08 | [#40](https://github.com/pop1414/my-AI/issues/40) | 系统管理导航与治理工作台基线重构 | AFK | `ready-for-agent` | #33 |
| FCUX-09 | [#41](https://github.com/pop1414/my-AI/issues/41) | 授权矩阵与账号治理体验收口 | AFK | `ready-for-agent` | #40 |
| FCUX-10 | [#42](https://github.com/pop1414/my-AI/issues/42) | 共享状态与角色显隐一致性收口 | AFK | `ready-for-agent` | #34, #35, #36, #37, #38, #39, #40, #41 |

## 建议执行顺序

1. 先完成 FCUX-01，建立模块型导航、任务型页面编排和共享页面骨架基线。
2. 再完成 FCUX-02、FCUX-03、FCUX-04，分别收口问答、知识库和文档主入口三条共享路径。
3. 之后完成 FCUX-05 与 FCUX-06，形成上传 -> 列表 -> 详情的连续文档工作流。
4. 再完成 FCUX-07，把状态查询、重处理、删除和分块预览从割裂工具页收纳回文档工作台周边。
5. 然后完成 FCUX-08、FCUX-09，收口系统治理区域。
6. 最后执行 FCUX-10，对共享状态、角色显隐、空态和错误态做统一收口。

## Issue 草案

### FCUX-01 控制台外层导航与共享页面骨架基线

GitHub Issue：[#33](https://github.com/pop1414/my-AI/issues/33)

Type：AFK

Blocked by：None - can start immediately

User stories covered：17、18、19、20、21、29

#### What to build

重构前端控制台的外层导航和共享页面骨架，把当前页面体系收敛为“外层模块型导航 + 页面内任务型编排”的稳定基线。该 slice 需要把 `ConsoleLayout`、页头、摘要区、主工作区和共享状态区整理成一套可复用模式，并在至少一条真实页面路径上落地验证，而不是只停留在抽象组件。

该 issue 的目标不是一次性重构所有业务页面，而是为后续问答、知识库、文档和治理页面建立统一壳层、统一操作区和统一状态表达方式。

#### Acceptance criteria

- [ ] 一级导航明确按稳定模块组织，至少覆盖 `文档`、`知识库`、`问答`、`系统管理` 四类入口。
- [ ] 页面内共享骨架固定为页头、上下文/摘要区、主工作区和状态区，不同角色下保持同一骨架。
- [ ] 至少一条真实页面路径完成新骨架落地验证，而不是只新增未接入的抽象组件。
- [ ] 共享骨架下的 loading、empty、error 基本状态有统一表现方式。
- [ ] 前端测试覆盖共享骨架渲染和基础状态分支。

#### Blocked by

None - can start immediately

### FCUX-02 问答控制台核对工作台重构

GitHub Issue：[#34](https://github.com/pop1414/my-AI/issues/34)

Type：AFK

Blocked by：FCUX-01

User stories covered：2、3、5、6、15、17、26、29、30

#### What to build

把问答页从“原始测试面板”重构为共享核对工作台。页面主叙事应围绕“提问 -> 回答 -> 核对引用”组织，并持续显式表达当前 `knowledge base` 上下文。高权限用户可以获得治理增强，但不能打断 `KB_READER` 的主路径。

该 slice 不接入 `#27` askable baseline 正文读取，只重构外层结构、引用区和角色增强方式。

#### Acceptance criteria

- [ ] 问答页首先围绕提问、回答和引用核对组织，不再以接口说明或技术化调试信息作为主叙事。
- [ ] 当前 `knowledge base` 上下文在问答流程中持续清晰可见。
- [ ] `KB_READER` 的主路径保持简洁，不被治理动作噪音打断。
- [ ] 具备更高权限的用户可看到更深治理信息或治理入口，但仍处于同一页面骨架内。
- [ ] 前端测试覆盖问答页的共享核对主叙事和角色增强显隐。

#### Blocked by

- #33

### FCUX-03 知识库任务入口与治理增强重构

GitHub Issue：[#35](https://github.com/pop1414/my-AI/issues/35)

Type：AFK

Blocked by：FCUX-01

User stories covered：1、13、15、17、23、27、29、30

#### What to build

重构知识库页面，使其从“原始主数据表”转为任务入口页。页面需要优先帮助普通成员选择 `knowledge base` 并进入问答或核对任务，再对管理者自然展开治理动作，如编辑和授权管理。

该 slice 不处理新治理语义，只重构知识库页面的进入体验、摘要表达和治理增强层级。

#### Acceptance criteria

- [ ] 知识库页优先表达“选择 `knowledge base` 并进入下一任务”的主叙事。
- [ ] 普通成员能够直接理解哪些 `knowledge base` 可进入问答或核对，而不必先穿过治理操作。
- [ ] 管理者在同一页面骨架上获得编辑、状态控制或授权管理等增强入口。
- [ ] 读者视角和治理视角不分裂成两套页面。
- [ ] 前端测试覆盖知识库页的入口行为和治理增强显隐。

#### Blocked by

- #33

### FCUX-04 文档列表核对优先与治理入口重构

GitHub Issue：[#36](https://github.com/pop1414/my-AI/issues/36)

Type：AFK

Blocked by：FCUX-01

User stories covered：7、8、14、17、21、22、28、29、30

#### What to build

重构文档列表页，使其优先服务普通成员的文档选择与核对任务，同时为治理角色提供清晰的结构化治理入口。页面需要减少“工具页跳转集合”的割裂感，强化文档适合核对/适合问答的可读信息。

该 slice 要完成文档列表主叙事、主要列信息、角色感知操作区和基础状态表现的重构。

#### Acceptance criteria

- [ ] 文档列表页首先表达文档当前是否适合核对和问答，而不是首先强调治理台账字段。
- [ ] `KB_READER` 能以低噪声方式选择和进入目标文档。
- [ ] 更高权限用户在列表中看到结构化治理入口，而不是零散的胶水按钮。
- [ ] 列表页的空态、错误态和筛选上下文在新骨架下保持一致。
- [ ] 前端测试覆盖读者优先的列表行为和治理入口显隐。

#### Blocked by

- #33

### FCUX-05 文档上传工作流闭环重构

GitHub Issue：[#37](https://github.com/pop1414/my-AI/issues/37)

Type：AFK

Blocked by：FCUX-01、FCUX-04

User stories covered：8、17、21、28、29

#### What to build

把上传页从独立技术表单重构为连续文档工作流的一部分。上传页需要明确当前 `knowledge base` 选择、上传结果和后续流转，把上传后的去向稳定地接回文档列表或文档详情，而不是只停留在瞬时消息和弱结果提示。

该 slice 不改上传后端契约，只收口前端上传页面和上传后的下一步动作。

#### Acceptance criteria

- [ ] 上传页在共享骨架下清晰表达当前 `knowledge base` 选择与上传任务目标。
- [ ] 上传结果提供稳定的后续动作或流转，而不是只依赖瞬时 `message`。
- [ ] 上传页与文档列表工作流衔接稳定，形成“上传 -> 列表/详情”的连续体验。
- [ ] 读者无权进入时保持正确边界，治理角色看到适合自己的后续动作。
- [ ] 前端测试覆盖上传结果后的主流转行为。

#### Blocked by

- #33
- #36

### FCUX-06 文档详情双视角外层工作台重构

GitHub Issue：[#38](https://github.com/pop1414/my-AI/issues/38)

Type：AFK

Blocked by：FCUX-01、FCUX-04

User stories covered：4、9、10、11、12、14、17、20、22、24、28、30

#### What to build

重构文档详情页外层工作台，把 `document` 在前端心智上拆成“文档核对 / 文档治理”两个视角，但保持同一页面骨架。详情页需要形成稳定的四段式叙事：概览区、核对主区、版本/问答上下文区、治理区。

该 slice 只处理外层工作台与视角结构，不接入 `#26` latest 正文读取，也不接入 `#28` 历史正文读取。

#### Acceptance criteria

- [ ] 文档详情页形成稳定的四段式外层骨架。
- [ ] `KB_READER` 首先看到文档核对主叙事，而不是治理元数据堆叠。
- [ ] 具备治理能力的用户可在同一详情页骨架中展开或切换到治理视角。
- [ ] 管理者不会感觉自己在使用“普通页面 + 补丁按钮”的界面。
- [ ] 前端测试覆盖详情页的双视角外层结构和角色差异。

#### Blocked by

- #33
- #36

### FCUX-07 文档工具页收纳与详情联动收口

GitHub Issue：[#39](https://github.com/pop1414/my-AI/issues/39)

Type：AFK

Blocked by：FCUX-05、FCUX-06

User stories covered：6、8、10、12、14、21、22、28

#### What to build

把当前文档相关工具页重新定位到文档工作台周边，减少“状态查询 / 分块预览 / 重处理 / 删除”各自独立跳转造成的割裂感。需要明确哪些能力继续保留独立页，哪些能力应回收到详情页主流程中。

该 slice 要收口工具页定位、详情联动和页面返回路径，而不是改变其后端语义。

#### Acceptance criteria

- [ ] 文档相关工具页的角色和入口定位在前端主流程中清晰收口。
- [ ] 分块预览、状态查询、重处理和删除与文档详情/列表形成稳定联动关系。
- [ ] 不再把每个工具页都当作平级主页面使用。
- [ ] 返回路径和上下文保留符合新的文档工作流。
- [ ] 前端测试覆盖文档主流程与工具页之间的关键联动。

#### Blocked by

- #37
- #38

### FCUX-08 系统管理导航与治理工作台基线重构

GitHub Issue：[#40](https://github.com/pop1414/my-AI/issues/40)

Type：AFK

Blocked by：FCUX-01

User stories covered：15、16、17、23、29

#### What to build

重构系统管理入口与治理工作台基线，把成员、账号、审计等纯治理页面收拢成一致的治理工作区。该 slice 的重点是治理区导航、页头、标签页/分区结构和治理工作台的一致性，而不是细化每个矩阵式页面的具体交互。

#### Acceptance criteria

- [ ] 系统管理区域呈现为一致的治理工作区，而不是共享页面骨架的变体拼接。
- [ ] 成员、账号、审计等治理入口在系统管理区域内组织稳定。
- [ ] 系统治理页面的页头、工具栏、状态区和主体区表达统一。
- [ ] 不把普通成员任务叙事强行套到纯治理页面上。
- [ ] 前端测试覆盖系统管理区域的入口和基本结构行为。

#### Blocked by

- #33

### FCUX-09 授权矩阵与账号治理体验收口

GitHub Issue：[#41](https://github.com/pop1414/my-AI/issues/41)

Type：AFK

Blocked by：FCUX-08

User stories covered：12、15、16、17、23、29

#### What to build

在系统治理工作台基线之上，进一步收口成员授权、知识库授权、文档授权和账号治理页面的矩阵体验。目标是让高频治理操作具备稳定的工具栏、状态反馈、批量操作反馈和页面心智，而不是只存在一组可运行但较粗糙的表格。

#### Acceptance criteria

- [ ] 授权矩阵和账号治理页面在治理工作台框架下具备一致的工具栏、状态反馈和批量操作表达。
- [ ] 矩阵式页面的操作不再显得像裸表格上贴按钮。
- [ ] 成员、知识库、文档授权与账号治理的主操作路径更易理解和执行。
- [ ] 治理角色在这些页面中的操作反馈保持一致。
- [ ] 前端测试覆盖关键治理页面的主操作和状态反馈行为。

#### Blocked by

- #40

### FCUX-10 共享状态与角色显隐一致性收口

GitHub Issue：[#42](https://github.com/pop1414/my-AI/issues/42)

Type：AFK

Blocked by：FCUX-02、FCUX-03、FCUX-04、FCUX-05、FCUX-06、FCUX-07、FCUX-08、FCUX-09

User stories covered：5、6、17、18、19、20、29、30

#### What to build

对本轮前端重构结果做统一收口，确保问答、知识库、文档和治理页面在 loading、empty、error、success、permission hidden 等状态上保持一致，也确保角色显隐逻辑与 `CONTEXT.md` 中已确认的前端视角规则一致。

该 issue 不引入正文读取专项，也不做新的后端语义实现；只处理共享前端规则的一致性收口。

#### Acceptance criteria

- [ ] 共享页面的 loading、empty、error、success 状态在主要页面上保持一致且符合任务叙事。
- [ ] 角色显隐逻辑符合 `KB_READER`、`KB_CONTRIBUTOR / KB_MANAGER`、`WORKSPACE_ADMIN / WORKSPACE_OWNER` 三层体验规则。
- [ ] 管理者增强不再表现为“普通页面 + 零散补丁”。
- [ ] 主要共享页面和治理页面都完成一致性走查与测试收口。
- [ ] 本轮收口不引入正文读取专项或其他超范围语义。

#### Blocked by

- #34
- #35
- #36
- #37
- #38
- #39
- #40
- #41

## 审阅提示

- 先审阅总览表，确认粒度是否合适：过粗则难并行，过细则会退化成碎片化样式工单。
- 检查依赖关系是否符合这次讨论的实施顺序：先 shell，再共享页面，再文档工作流，再治理页，最后一致性收口。
- 重点检查 FCUX-06：文档详情双视角是否足够独立成一条 vertical slice，而不是跟正文读取专项混在一起。
- 重点检查 FCUX-07：工具页收纳是否需要继续细拆，或者是否已经足够窄且可独立验证。
- 重点检查 FCUX-10：它应只做一致性收口，不夹带新的业务语义或正文读取专项。
- GitHub Issues 已按总览表顺序发布；后续执行时以真实 issue 编号为协作入口，本地 `FCUX-*` 编号仅作为专题内排序辅助。
