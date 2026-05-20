# 文档导航

`docs/` 是 `my-AI` 的长期工程文档真源，主要服务于：

1. 未来继续开发这个项目的你自己
2. 任何需要快速理解当前系统事实的协作者
3. 从工程文档中整理课程材料、面试材料时的事实来源

当前文档体系采用三层分工：

- `docs/`：工程真源层，描述当前系统事实
- `docs/learning/`：学习沉淀层，记录原理理解、踩坑和面试表达
- `deliverables/course/`：课程交付层，从工程文档整理导出，不作为系统事实真源

项目内不同文档类型的职责边界、生成顺序和 AI 协作角色见 [document-system.md](./document-system.md)。

## 1. 推荐阅读顺序

如果你是隔了一段时间重新接手这个项目，建议按下面顺序看：

1. [README.md](../README.md)：项目总入口、当前能力、启动方式
2. [document-system.md](./document-system.md)：文档类型职责、生成顺序和 AI 协作角色
3. [product/scope.md](./product/scope.md)：当前版本目标和边界
4. [product/roadmap.md](./product/roadmap.md)：版本进度、下一阶段方向
5. [architecture/README.md](./architecture/README.md)：系统分层、子域、图纸入口
6. [api/openapi.yaml](./api/openapi.yaml)：接口契约
7. [releases/release-notes.md](./releases/release-notes.md)：版本变化记录与正式发布条目
8. [runbooks/plans/v1/v1-release-archive.md](./runbooks/plans/v1/v1-release-archive.md)：V1 版本归档记录与冻结范围
9. [runbooks/plans/v1-1/v1-1-plan.md](./runbooks/plans/v1-1/v1-1-plan.md)：V1.1 规划草案与优先级拆解
10. [runbooks/plans/rag-access-control/rag-access-control-plan.md](./runbooks/plans/rag-access-control/rag-access-control-plan.md)：成熟 RAG 权限体系专题计划
11. [runbooks/plans/rag-access-control/账号生命周期后端实施计划.md](./runbooks/plans/rag-access-control/账号生命周期后端实施计划.md)：账号治理后端实施方案
12. [runbooks/plans/rag-access-control/账号生命周期后端完成概览.md](./runbooks/plans/rag-access-control/账号生命周期后端完成概览.md)：账号治理后端完成状态摘要
13. [runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md)：知识库列表可见性收紧实施方案
14. [runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md)：知识库列表可见性收紧完成摘要
15. [architecture/ingest/acceptance-closure.md](./architecture/ingest/acceptance-closure.md)：上传受理闭环
16. [architecture/ingest/processing-execution.md](./architecture/ingest/processing-execution.md)：处理执行闭环
17. [runbooks/plans/document-version-chain/document-version-chain-prd.md](./runbooks/plans/document-version-chain/document-version-chain-prd.md)：文档版本链与治理基线 PRD
18. [runbooks/plans/document-version-chain/document-version-content-read-prd.md](./runbooks/plans/document-version-chain/document-version-content-read-prd.md)：文档版本正文读取专项 PRD
19. [adr/](./adr/)：关键技术决策和历史留痕（含 `ADR-0005` 权限体系基础决策、`ADR-0006` 文档版本读边界）
20. [runbooks/plans/v1/v1-closure-plan.md](./runbooks/plans/v1/v1-closure-plan.md)：V1 收口执行计划（历史记录）
21. [runbooks/workflows/my-ai-document-workflow.md](./runbooks/workflows/my-ai-document-workflow.md)：项目文档工作流
22. [runbooks/workflows/my-ai-git-workflow.md](./runbooks/workflows/my-ai-git-workflow.md)：项目 Git 工作流

## 2. 当前目录职责

### 2.1 工程真源

- [product/scope.md](./product/scope.md)：产品范围、DoD、风险、里程碑
- [product/roadmap.md](./product/roadmap.md)：版本路线图与阶段状态
- [architecture/README.md](./architecture/README.md)：架构总览与图纸索引
- [api/openapi.yaml](./api/openapi.yaml)：对外接口契约
- [releases/release-notes.md](./releases/release-notes.md)：版本变化记录
- [architecture/ingest/acceptance-closure.md](./architecture/ingest/acceptance-closure.md)：受理闭环专题设计
- [architecture/ingest/processing-execution.md](./architecture/ingest/processing-execution.md)：执行闭环专题设计
- [document-system.md](./document-system.md)：文档类型职责、生成顺序和 AI 协作角色

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
- 排障手册入口：
  - [runbooks/troubleshooting/](./runbooks/troubleshooting/)
  - [runbooks/troubleshooting/ingest-document-asset-recovery.md](./runbooks/troubleshooting/ingest-document-asset-recovery.md)：文档入库资产恢复手册
- 其中权限体系专项计划入口：
  - [runbooks/plans/rag-access-control/rag-access-control-plan.md](./runbooks/plans/rag-access-control/rag-access-control-plan.md)
  - [runbooks/plans/rag-access-control/账号生命周期后端实施计划.md](./runbooks/plans/rag-access-control/账号生命周期后端实施计划.md)
  - [runbooks/plans/rag-access-control/账号生命周期后端完成概览.md](./runbooks/plans/rag-access-control/账号生命周期后端完成概览.md)
  - [runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧实施计划.md)
  - [runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md](./runbooks/plans/rag-access-control/知识库列表授权可见性收紧完成概览.md)

## 3. 顶层收敛规则

`docs/` 顶层只保留导航、文档体系说明和少量跨目录入口文档；具体主题文档按职责进入子目录：

- `docs/product/`：产品范围、路线图、阶段目标
- `docs/architecture/`：架构总览、子域设计、图纸资产
- `docs/api/`：OpenAPI 等接口契约
- `docs/releases/`：发布说明与版本变化记录
- `docs/runbooks/`：执行计划、联调手册、演示脚本、排障手册

新增文档默认不要继续堆在 `docs/` 顶层，除非它承担跨目录导航或文档治理职责。

完整目录落位规则见 [document-system.md](./document-system.md) 的“目录落位规则”章节。

## 4. 新增文档时怎么判断放哪里

可以用下面的规则快速判断：

- 如果它描述“系统现在是什么、怎么设计、怎么运行”，按主题放入 `docs/product/`、`docs/architecture/`、`docs/api/`、`docs/runbooks/` 等子目录
- 如果它描述“我为什么这么做、学到了什么、面试怎么讲”，放 `docs/learning/`
- 如果它是“课程说明书、截图、演示脚本、最终提交件”，放 `deliverables/course/`

## 5. 写作约定

- 主文档以中文为主
- `README` 讲入口，不讲全部细节
- 架构文档讲边界、职责和关系
- Runbook 类型文档讲“怎么跑、怎么验、怎么排障”
- 学习文档允许主观复盘，但不替代正式工程事实描述
