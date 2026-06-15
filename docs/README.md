# 文档导航

`docs/` 是 `my-AI` 的长期工程文档真源，主要服务于：

1. 未来继续开发这个项目的你自己
2. 任何需要快速理解当前系统事实的协作者
3. 从工程文档中整理课程材料、面试材料时的事实来源

## 文档体系

本项目采用 AI-native 文档体系，详见 [AI_DOCUMENT_SYSTEM.md](./AI_DOCUMENT_SYSTEM.md)。

核心分层：

```text
CONTEXT.md         -> 稳定领域语言、核心实体、项目边界
docs/adr/          -> 长期技术取舍及后果
docs/features/     -> PRD → SPEC → issues 功能文档链
docs/plans/        -> 跨 feature 或版本级阶段规划
docs/architecture/ -> 系统分层、子域设计、图纸资产
docs/archive/      -> 历史 runbooks、plans、handoffs 归档
```

如果启用了 BMad Level 3 扩展，另见 [BMAD_INTEGRATION.md](./BMAD_INTEGRATION.md) 和 [LEVEL3_BMAD_WORKFLOW.md](./LEVEL3_BMAD_WORKFLOW.md)。

## 1. 推荐阅读顺序

如果你是隔了一段时间重新接手这个项目，建议按下面顺序看：

1. [README.md](../README.md)：项目总入口、当前能力、启动方式
2. [AI_DOCUMENT_SYSTEM.md](./AI_DOCUMENT_SYSTEM.md)：AI-native 文档体系说明
3. [CONTEXT.md](../CONTEXT.md)：稳定领域语言、核心实体、项目边界
4. [product/scope.md](./product/scope.md)：当前版本目标和边界
5. [product/roadmap.md](./product/roadmap.md)：版本进度、下一阶段方向
6. [architecture/README.md](./architecture/README.md)：系统分层、子域、图纸入口
7. [architecture/ingest/acceptance-closure.md](./architecture/ingest/acceptance-closure.md)：上传受理闭环
8. [architecture/ingest/processing-execution.md](./architecture/ingest/processing-execution.md)：处理执行闭环
9. [api/openapi.yaml](./api/openapi.yaml)：接口契约
10. [adr/](./adr/)：关键技术决策记录
11. [releases/release-notes.md](./releases/release-notes.md)：版本变化记录
12. [features/](./features/)：各功能 PRD / SPEC / issues
13. [plans/](./plans/)：跨 feature 或版本级阶段规划
14. [archive/](./archive/)：历史执行计划、联调手册、handoff 记录

## 2. 当前目录职责

### 2.1 AI-native 文档链

- [features/](./features/)：功能文档，每个子目录按 `PRD.md → SPEC.md → issues/` 组织
- [plans/](./plans/)：跨 feature 或版本级阶段规划；单 feature 规划优先放 `docs/features/<feature>/PLAN.md`
- [AI_DOCUMENT_SYSTEM.md](./AI_DOCUMENT_SYSTEM.md)：文档体系治理文件

### 2.2 产品与架构

- [product/scope.md](./product/scope.md)：产品范围、DoD、风险、里程碑
- [product/roadmap.md](./product/roadmap.md)：版本路线图与阶段状态
- [architecture/README.md](./architecture/README.md)：架构总览与图纸索引
- [architecture/ingest/acceptance-closure.md](./architecture/ingest/acceptance-closure.md)：受理闭环专题设计
- [architecture/ingest/processing-execution.md](./architecture/ingest/processing-execution.md)：执行闭环专题设计
- [api/openapi.yaml](./api/openapi.yaml)：对外接口契约
- [releases/release-notes.md](./releases/release-notes.md)：版本变化记录

### 2.3 决策留痕

- [adr/](./adr/)：架构决策记录

规则：

- 当前系统事实以 `docs/` 为准
- 版本演进、取舍和历史变化通过 ADR、Roadmap、Release Notes 留痕
- 不在 `docs/` 中堆积同一主题的多个”最终版/修正版”副本

### 2.4 图纸与参考资料

- [architecture/diagrams/](./architecture/diagrams/)：可编辑架构图、时序图、状态机、边界图
- [reference/](./reference/)：外部技术参考、对照资料，不描述当前系统事实

### 2.5 学习沉淀

- [learning/](./learning/)：原理理解、设计复盘、踩坑总结、面试表达材料

### 2.6 历史归档

- [archive/](./archive/)：历史 runbooks、plans、handoffs、operations 文档，不再作为当前系统事实

### 2.7 Agent 配置

- [agents/](./agents/)：engineering skills 的仓库级配置（domain、issue tracker、triage labels、document system）

## 3. 顶层收敛规则

`docs/` 顶层只保留导航、文档体系说明和少量跨目录入口文档；具体主题文档按职责进入子目录：

- `docs/features/`：功能 PRD、SPEC、issues
- `docs/plans/`：跨 feature 或版本级阶段规划
- `docs/product/`：产品范围、路线图、阶段目标
- `docs/architecture/`：架构总览、子域设计、图纸资产
- `docs/api/`：OpenAPI 等接口契约
- `docs/adr/`：架构决策记录
- `docs/releases/`：发布说明与版本变化记录
- `docs/archive/`：历史文档归档

新增文档默认不要继续堆在 `docs/` 顶层，除非它承担跨目录导航或文档治理职责。

完整目录落位规则见 [AI_DOCUMENT_SYSTEM.md](./AI_DOCUMENT_SYSTEM.md)。

## 4. 新增文档时怎么判断放哪里

- 功能需求、验收标准 → `docs/features/<feature>/PRD.md`
- 行为契约、技术契约、验证命令 → `docs/features/<feature>/SPEC.md`
- 可执行任务切片 → `docs/features/<feature>/issues/`
- 跨功能阶段规划 → `docs/plans/<plan>.md`
- 长期技术取舍 → `docs/adr/<NNNN>-<slug>.md`
- 系统设计、边界 → `docs/architecture/`
- 学习复盘 → `docs/learning/`
- 外部参考资料 → `docs/reference/`

## 5. 写作约定

- 主文档以中文为主
- `README` 讲入口，不讲全部细节
- 架构文档讲边界、职责和关系
- 学习文档允许主观复盘，但不替代正式工程事实描述
