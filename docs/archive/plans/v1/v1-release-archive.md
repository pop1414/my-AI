# V1 版本归档记录

状态：Archived（2026-05-07）  
归档版本：`1.0.0`  
归档 tag：`v1.0.0`  
归档基线分支：`main`

## 1. 归档目标

本次归档用于把 `my-AI` 的 V1 阶段从“功能收口完成”推进到“版本可回看、可讲述、可复用”的正式里程碑。

归档完成后，这个版本应同时满足：

1. 有清晰的功能边界
2. 有明确的正式版本号与 tag
3. 有可追溯的说明文档与发布记录
4. 能作为课程交付、演示复盘和后续 V1.1 规划的稳定基线

## 2. 本次冻结范围

本次 V1 归档冻结的是一个最小但完整的演示闭环，覆盖三个子域：

- `ingest`：上传、状态查询、分块预览、重处理、删除
- `knowledge`：知识库统计列表
- `qa`：单轮问答、引用展示、无命中兜底

对应的产品体验闭环为：

`上传 -> 状态查询 -> 知识库统计 -> 单轮问答`

## 3. 归档依据

本次版本归档以以下事实源为准：

- [README.md](../../README.md)
- [docs/product/scope.md](../../../product/scope.md)
- [docs/product/roadmap.md](../../../product/roadmap.md)
- [docs/architecture/README.md](../../../architecture/README.md)
- [docs/api/openapi.yaml](../../../api/openapi.yaml)
- [docs/releases/release-notes.md](../../../releases/release-notes.md)
- [docs/runbooks/plans/v1/v1-closure-plan.md](./v1-closure-plan.md)
- [docs/runbooks/handoffs/2026-05-07-session-handoff-v1-closure.md](./handoffs/2026-05-07-session-handoff-v1-closure.md)

其中：

- `README / architecture / api-contract` 描述当前系统事实
- `roadmap / release-notes` 描述版本状态与历史变化
- `v1-closure-plan / handoff` 描述本轮收口的执行过程与验证记录

## 4. 验证记录

依据 `2026-05-07` 的 V1 收口交接包，本版本归档前已完成以下验证：

- 前端构建：`npm run build`
- 后端测试：`.\\mvnw.cmd "-Dtest=!MyAiApplicationTests" test`
- 后端测试结果：`69` 个测试全部通过

这意味着当前归档不是“文档先行宣布完成”，而是建立在已完成联调与验证的版本基线上。

## 5. 归档产物

本次归档完成后，仓库内应形成以下留痕：

- `README`：声明当前已归档的 V1.0.0 能力边界
- `docs/product/scope.md`：标记 M3 已完成
- `docs/product/roadmap.md`：把 V1 发布归档转入已完成状态
- `docs/releases/release-notes.md`：形成正式 `1.0.0` 发布条目
- 本文档：作为 V1 版本归档总记录
- Git 注解 tag：`v1.0.0`

## 6. 不在本次归档范围内

以下内容明确不属于本次 V1 归档范围：

- OCR 与复杂版式增强
- 用户与权限系统
- 多轮会话历史
- 完整知识库管理台
- 多租户、计费与企业级运营能力

这些能力应在后续 `V1.1 / V2` 阶段按新范围重新立项，不回写到本次归档版本定义中。

## 7. 归档后的建议动作

完成本次归档后，建议按下面顺序继续推进：

1. 从本版本基线整理课程交付材料
2. 补齐 `deliverables/course/` 下的报告、演示脚本与截图素材
3. 以 `v1.0.0` 为对照基线启动 V1.1 范围设计

## 8. 一句话结论

`my-AI` 的 V1 已经不再只是“功能基本做完”，而是已经形成了一个有边界、有留痕、有版本号、可稳定回看的正式归档基线。
