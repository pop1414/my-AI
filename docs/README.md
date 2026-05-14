# 文档导航

`docs/` 是 `my-AI` 的长期工程文档真源，主要服务于：

1. 未来继续开发这个项目的你自己
2. 任何需要快速理解当前系统事实的协作者
3. 从工程文档中整理课程材料、面试材料时的事实来源

当前文档体系采用三层分工：

- `docs/`：工程真源层，描述当前系统事实
- `docs/learning/`：学习沉淀层，记录原理理解、踩坑和面试表达
- `deliverables/course/`：课程交付层，从工程文档整理导出，不作为系统事实真源

## 1. 推荐阅读顺序

如果你是隔了一段时间重新接手这个项目，建议按下面顺序看：

1. [README.md](../README.md)：项目总入口、当前能力、启动方式
2. [01-product-scope.md](./01-product-scope.md)：当前版本目标和边界
3. [02-roadmap.md](./02-roadmap.md)：版本进度、下一阶段方向
4. [03-architecture.md](./03-architecture.md)：系统分层、子域、图纸入口
5. [04-api-contract.yaml](./04-api-contract.yaml)：接口契约
6. [05-release-notes.md](./05-release-notes.md)：版本变化记录与正式发布条目
7. [runbooks/plans/v1/v1-release-archive.md](./runbooks/plans/v1/v1-release-archive.md)：V1 版本归档记录与冻结范围
8. [runbooks/plans/v1-1/v1-1-plan.md](./runbooks/plans/v1-1/v1-1-plan.md)：V1.1 规划草案与优先级拆解
9. [runbooks/plans/rag-access-control/rag-access-control-plan.md](./runbooks/plans/rag-access-control/rag-access-control-plan.md)：成熟 RAG 权限体系专题计划
10. [runbooks/plans/rag-access-control/账号生命周期后端实施计划.md](./runbooks/plans/rag-access-control/账号生命周期后端实施计划.md)：账号治理后端实施方案
11. [runbooks/plans/rag-access-control/账号生命周期后端完成概览.md](./runbooks/plans/rag-access-control/账号生命周期后端完成概览.md)：账号治理后端完成状态摘要
12. [runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md)：知识库列表可见性收紧实施方案
13. [runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md)：知识库列表可见性收紧完成摘要
14. [06-ingest-acceptance-closure.md](./06-ingest-acceptance-closure.md)：上传受理闭环
15. [07-ingest-processing-execution.md](./07-ingest-processing-execution.md)：处理执行闭环
16. [runbooks/plans/document-version-chain/document-version-chain-prd.md](./runbooks/plans/document-version-chain/document-version-chain-prd.md)：文档版本链与治理基线 PRD
17. [runbooks/plans/document-version-chain/document-delete-list-version-semantics-backend-closure.md](./runbooks/plans/document-version-chain/document-delete-list-version-semantics-backend-closure(#8).md)：删除与列表页版本语义后端收口说明
18. [adr/](./adr/)：关键技术决策和历史留痕（含 `ADR-0005` 权限体系基础决策、`ADR-0006` 文档版本读边界）
19. [runbooks/plans/v1/v1-closure-plan.md](./runbooks/plans/v1/v1-closure-plan.md)：V1 收口执行计划（历史记录）
20. [runbooks/workflows/my-ai-document-workflow.md](./runbooks/workflows/my-ai-document-workflow.md)：项目文档工作流
21. [runbooks/workflows/my-ai-git-workflow.md](./runbooks/workflows/my-ai-git-workflow.md)：项目 Git 工作流

## 2. 当前目录职责

### 2.1 工程真源

- [01-product-scope.md](./01-product-scope.md)：产品范围、DoD、风险、里程碑
- [02-roadmap.md](./02-roadmap.md)：版本路线图与阶段状态
- [03-architecture.md](./03-architecture.md)：架构总览与图纸索引
- [04-api-contract.yaml](./04-api-contract.yaml)：对外接口契约
- [05-release-notes.md](./05-release-notes.md)：版本变化记录
- [06-ingest-acceptance-closure.md](./06-ingest-acceptance-closure.md)：受理闭环专题设计
- [07-ingest-processing-execution.md](./07-ingest-processing-execution.md)：执行闭环专题设计

### 2.2 决策留痕

- [adr/](./adr/)：架构决策记录（ADR，包含 `ADR-0005` RAG 权限体系基础决策）

规则：

- 当前系统事实以 `docs/` 为准
- 版本演进、取舍和历史变化通过 ADR、Roadmap、Release Notes 留痕
- 不在 `docs/` 中堆积同一主题的多个“最终版/修正版”副本

### 2.3 图纸与参考资料

- [architecture/diagrams/](./architecture/diagrams/)：可编辑架构图、时序图、状态机、边界图
- [reference/](./reference/)：外部技术参考、对照资料，不描述当前系统事实

### 2.4 学习沉淀

- [learning/](./learning/)：原理理解、设计复盘、踩坑总结、面试表达材料

### 2.5 执行与联调手册

- [runbooks/](./runbooks/)：执行计划、联调手册、演示脚本、排障手册
- 其中权限体系专项计划入口：
  - [runbooks/plans/rag-access-control/rag-access-control-plan.md](./runbooks/plans/rag-access-control/rag-access-control-plan.md)
  - [runbooks/plans/rag-access-control/账号生命周期后端实施计划.md](./runbooks/plans/rag-access-control/账号生命周期后端实施计划.md)
  - [runbooks/plans/rag-access-control/账号生命周期后端完成概览.md](./runbooks/plans/rag-access-control/账号生命周期后端完成概览.md)
  - [runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md)
  - [runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md)

## 3. 未来迁移方向

为了减少一次性大搬家，当前 `01~07` 根文档暂时保留。后续逐步迁移到更稳定的结构：

- `docs/product/`
- `docs/architecture/`
- `docs/runbooks/`

在迁移完成前，本文件就是推荐入口。

## 4. 新增文档时怎么判断放哪里

可以用下面的规则快速判断：

- 如果它描述“系统现在是什么、怎么设计、怎么运行”，放 `docs/`
- 如果它描述“我为什么这么做、学到了什么、面试怎么讲”，放 `docs/learning/`
- 如果它是“课程说明书、截图、演示脚本、最终提交件”，放 `deliverables/course/`

## 5. 写作约定

- 主文档以中文为主
- `README` 讲入口，不讲全部细节
- 架构文档讲边界、职责和关系
- Runbook 类型文档讲“怎么跑、怎么验、怎么排障”
- 学习文档允许主观复盘，但不替代正式工程事实描述
