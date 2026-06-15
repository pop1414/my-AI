# Plans 导航

`docs/runbooks/plans/` 用来放“某个阶段准备怎么做、做到什么程度、如何验收”的规划型文档。

这里和其他 runbooks 子目录的分工如下：

- `plans/`：版本规划、主题实施方案、收口计划、版本归档
- `workflows/`：长期稳定的方法论和项目工作流
- `handoffs/`：会话交接包和阶段状态快照

## 当前结构

- [document-version-chain/](./document-version-chain/)：文档版本链与版本治理前端/后端规划
- [v1/](./v1/)：V1 收口与归档相关文档
- [v1-1/](./v1-1/)：V1.1 规划与后续主题拆解
- [rag-access-control/](./rag-access-control/)：RAG 权限体系专项规划

## 当前文件

- [document-version-chain/document-version-chain-prd.md](./document-version-chain/document-version-chain-prd.md)：文档版本链与治理基线 PRD
- [document-version-chain/document-detail-version-history-interaction-confirmation.md](./document-version-chain/document-detail-version-history-interaction-confirmation.md)：文档详情页版本历史交互确认
- [document-version-chain/github-open-issues-snapshot-2026-05-13.md](./document-version-chain/github-open-issues-snapshot-2026-05-13.md)：文档版本链 GitHub issues 状态快照与后续执行顺序
- [v1/v1-closure-plan.md](./v1/v1-closure-plan.md)：V1 闭环收口计划（历史记录）
- [v1/v1-release-archive.md](./v1/v1-release-archive.md)：V1 正式归档记录
- [v1-1/v1-1-plan.md](./v1-1/v1-1-plan.md)：V1.1 总规划草案
- [rag-access-control/rag-access-control-plan.md](./rag-access-control/rag-access-control-plan.md)：成熟 RAG 权限体系专题计划
- [rag-access-control/账号生命周期后端实施计划.md](./rag-access-control/账号生命周期后端实施计划.md)：账号治理后端实施计划
- [rag-access-control/账号生命周期后端完成概览.md](./rag-access-control/账号生命周期后端完成概览.md)：账号治理后端完成摘要
- [rag-access-control/知识库列表授权可见性收紧实施计划.md](./rag-access-control/知识库列表授权可见性收紧实施计划.md)：知识库列表可见性收紧实施计划
- [rag-access-control/知识库列表授权可见性收紧完成概览.md](./rag-access-control/知识库列表授权可见性收紧完成概览.md)：知识库列表可见性收紧完成摘要

## 使用规则

- 版本总规划放在 `plans/<version>/`
- 某个主题需要细化时，继续放在同版本目录下
- 跨版本、但需要长期独立演进的专题，可以单独建立主题目录
- 计划执行完成后可以继续保留，作为版本推进的留痕
- 如果内容已经变成“当前系统事实”，要同步到 `docs/` 正式文档，而不是只留在计划文档里
