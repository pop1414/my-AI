# Runbooks 导航

`docs/runbooks/` 用来放“怎么执行、怎么联调、怎么验收、怎么排障”这一类操作型文档。

和其他目录的分工如下：

- `docs/`：描述当前系统事实
- `docs/adr/`：记录关键决策和历史取舍
- `docs/learning/`：记录你的理解、复盘和面试表达
- `docs/runbooks/`：记录实际推进工作时的执行路径与操作手册

## 当前文档

- [v1-closure-plan.md](./v1-closure-plan.md)：当前 V1 闭环收口计划
- [my-ai-document-workflow.md](./my-ai-document-workflow.md)：项目文档工作流与更新规则
- [my-ai-git-workflow.md](./my-ai-git-workflow.md)：项目 Git 工作流与分支策略
- [handoffs/](./handoffs/)：按会话沉淀的交接包与阶段状态记录

## 适合放在这里的文档类型

- 本地启动与联调手册
- V1 / V1.1 / 某个里程碑的收口计划
- 会话交接包
- 演示脚本
- 排障手册
- 发布前检查清单

## 使用规则

- 这里的文档偏“怎么做”
- 如果文档内容已经上升为系统正式事实，要同步更新到对应的架构/产品文档
- 如果文档只是阶段性执行计划，完成后可以继续保留，作为版本推进的留痕
