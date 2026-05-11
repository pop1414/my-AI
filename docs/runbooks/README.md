# Runbooks 导航

`docs/runbooks/` 用来放“怎么执行、怎么联调、怎么验收、怎么排障”这一类操作型文档。

和其他目录的分工如下：

- `docs/`：描述当前系统事实
- `docs/adr/`：记录关键决策和历史取舍
- `docs/learning/`：记录你的理解、复盘和面试表达
- `docs/runbooks/`：记录实际推进工作时的执行路径与操作手册

## 当前文档

- [plans/README.md](./plans/README.md)：阶段规划、版本收口、版本归档与主题实施方案
- [workflows/README.md](./workflows/README.md)：长期有效的项目工作流与更新规则
- [handoffs/](./handoffs/)：按会话沉淀的交接包与阶段状态记录

当前权限专项新增主题文档：

- [plans/rag-access-control/账号生命周期后端实施计划.md](./plans/rag-access-control/账号生命周期后端实施计划.md)
- [plans/rag-access-control/账号生命周期后端完成概览.md](./plans/rag-access-control/账号生命周期后端完成概览.md)
- [handoffs/rag-access-control/2026-05-11-auth-account-lifecycle-backend-handoff.md](./handoffs/rag-access-control/2026-05-11-auth-account-lifecycle-backend-handoff.md)

## 适合放在这里的文档类型

- `plans/`：V1 / V1.1 / 某个里程碑的收口计划、主题实施方案、版本归档
- `workflows/`：文档工作流、Git 工作流、未来稳定下来的演示/排障规范
- `handoffs/`：会话交接包、阶段状态记录

## 当前结构

```text
docs/runbooks/
  README.md
  plans/README.md
  plans/
    v1/
    v1-1/
  workflows/README.md
  workflows/
  handoffs/
```

## 使用规则

- 这里的文档偏“怎么做”
- 如果文档内容已经上升为系统正式事实，要同步更新到对应的架构/产品文档
- 如果文档只是阶段性执行计划，完成后可以继续保留，作为版本推进的留痕
