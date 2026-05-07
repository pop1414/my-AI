# 课程交付目录

`deliverables/course/` 是课程提交与展示材料的独立目录。  
它的目标不是描述系统真相，而是把仓库中的工程文档整理成老师容易理解、容易评分的交付件。

## 1. 和 `docs/` 的关系

- `docs/`：工程真源，描述当前系统事实
- `docs/learning/`：学习与面试沉淀，描述我的理解
- `deliverables/course/`：课程包装层，描述提交物和展示物

规则：

- 课程材料主要整理自 `docs/`
- 可以为了课程表达重写组织方式
- 不能新增和工程真源冲突的事实

## 2. 目录职责

- `report/`：课程说明书源稿、章节草稿、材料清单
- `assets/`：截图、答辩图、录屏封面、插图
- `demo/`：演示脚本、讲解提纲、录屏步骤
- `export/`：最终提交件，例如 PDF、DOCX、ZIP 清单

## 3. 建议工作流

1. 先在 `docs/` 中维护当前系统事实
2. 需要准备课程材料时，从 `docs/` 中抽取：
   - 需求分析
   - 总体设计
   - 详细设计
   - 测试说明
   - 架构图、流程图、ER 图
3. 结合截图和老师要求，整理到 `report/` 和 `demo/`
4. 最终提交件统一放进 `export/`

## 4. 当前可映射来源

- 需求和范围：
  - [docs/01-product-scope.md](../../docs/01-product-scope.md)
  - [docs/02-roadmap.md](../../docs/02-roadmap.md)
- 架构和模块设计：
  - [docs/03-architecture.md](../../docs/03-architecture.md)
  - [docs/architecture/diagrams/](../../docs/architecture/diagrams/)
- 接口与能力：
  - [docs/04-api-contract.yaml](../../docs/04-api-contract.yaml)
  - [README.md](../../README.md)
- 专题设计与处理流程：
  - [docs/06-ingest-acceptance-closure.md](../../docs/06-ingest-acceptance-closure.md)
  - [docs/07-ingest-processing-execution.md](../../docs/07-ingest-processing-execution.md)
- 版本变化与里程碑：
  - [docs/05-release-notes.md](../../docs/05-release-notes.md)
  - [docs/runbooks/plans/v1/v1-release-archive.md](../../docs/runbooks/plans/v1/v1-release-archive.md)

其中 `docs/runbooks/plans/v1/v1-release-archive.md` 可作为课程包装时的固定版本边界来源，用来说明：

- 当前提交材料对应哪个正式版本
- V1 已经稳定交付了哪些能力
- 哪些内容属于 V1 之后的演进方向

## 5. 交付规则

- `report/` 放过程稿与源稿
- `assets/` 放截图与展示素材
- `demo/` 放演示说明和录屏脚本
- `export/` 只放最终件，避免和草稿混在一起
