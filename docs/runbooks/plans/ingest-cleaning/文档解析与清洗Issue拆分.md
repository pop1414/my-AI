# 文档解析与清洗 Issue 拆分

日期：2026-05-13

状态：已发布 GitHub Issues

关联 PRD：

- `docs/runbooks/plans/ingest-cleaning/文档解析与清洗PRD.md`

## 拆分原则

本轮 issue 拆分只覆盖文档解析与清洗链路中的 `cleaned.md` 质量基线，不进入文档版本治理高冲突区。

所有 issue 默认遵守以下边界：

- 不修改 `document` / `document version` 主模型。
- 不新增或修改 Flyway schema。
- 不调整 `qa.ask` 对外响应结构。
- 不修改 `vector metadata shape`。
- 不落地父子分块或 richer node model。
- 不把 `processingMetadata` 定义为最终 RAG 节点契约。

## 已发布 Issues

### 1. 黄金样本真实输入与验收锚点

- GitHub Issue：[#13 补齐 ingest-cleaning 黄金样本真实输入与验收锚点](https://github.com/pop1414/my-AI/issues/13)
- 类型：AFK
- 依赖：None - can start immediately
- 覆盖用户故事：15, 16, 17

目标：

- 补齐弱结构 PDF 与 Word 的真实输入文件。
- 为 weak PDF、Word、Markdown、HTML 四类样本补充固定 QA 问题、预期命中章节、噪音排除词与人工审阅锚点。

### 2. 原生 Markdown 最小破坏解析路径

- GitHub Issue：[#14 原生 Markdown 最小破坏解析路径](https://github.com/pop1414/my-AI/issues/14)
- 类型：AFK
- 依赖：#13
- 覆盖用户故事：9, 10, 17, 18, 19

目标：

- 避免原生 Markdown 因无谓进入 Tika / HTML 转换链而破坏结构。
- 保持标题、代码块、表格、列表和空行结构稳定进入 `cleaned.md`。

### 3. 原生 HTML 正文抽取与噪音清洗路径

- GitHub Issue：[#15 原生 HTML 正文抽取与噪音清洗路径](https://github.com/pop1414/my-AI/issues/15)
- 类型：AFK
- 依赖：#13
- 覆盖用户故事：11, 12, 17, 18, 19

目标：

- 保留 HTML 主正文结构。
- 清理导航、页脚、脚本、样式、元数据等非正文噪音。

### 4. Word 文档结构保真清洗

- GitHub Issue：[#16 Word 文档结构保真清洗](https://github.com/pop1414/my-AI/issues/16)
- 类型：AFK
- 依赖：#13
- 覆盖用户故事：5, 6, 7, 8, 17

目标：

- 保留 Word 标题样式、列表、表格、图片说明或最小图片占位。
- 避免结构化信息退化为不可检索的普通文本。

### 5. 弱结构 PDF 段落修复与噪音剔除

- GitHub Issue：[#17 弱结构 PDF 段落修复与噪音剔除](https://github.com/pop1414/my-AI/issues/17)
- 类型：AFK
- 依赖：#13
- 覆盖用户故事：2, 3, 4, 17

目标：

- 修复弱结构 PDF 的幽灵换行、错误断段和标题粘连。
- 剔除明显重复的页眉、页脚、页码、热线等噪音。

### 6. cleaned.md 质量回归闭环

- GitHub Issue：[#18 cleaned.md 质量回归闭环](https://github.com/pop1414/my-AI/issues/18)
- 类型：AFK
- 依赖：#14, #15, #16, #17
- 覆盖用户故事：1, 13, 14, 16, 17

目标：

- 将黄金样本、chunk preview 和固定 qa.ask 问题串成统一验收闭环。
- 区分 cleaned.md 改善、chunk 边界改善和 qa.ask 偶然变化。

## 推荐执行顺序

1. 先完成 #13，确保黄金样本输入和验收锚点可靠。
2. 并行推进 #14、#15、#16、#17。
3. 最后执行 #18，形成统一回归闭环。

## 与版本治理 Issues 的关系

当前 GitHub 上已有 #1 到 #12 主要覆盖 document / document version、上传新版本、版本回退、删除和 qa 引用版本化。

本轮 #13 到 #18 与这些 issue 的关系是 Related，而不是 Blocked by：

- #13 到 #18 负责 `cleaned.md` 文本质量与验证闭环。
- #1 到 #12 负责文档资产、版本真相、问答引用版本化和治理交互。
- 任一 issue 如果需要修改 `document` 主模型、Flyway、`qa.ask` response DTO 或 `vector metadata shape`，应先暂停并同步设计。
