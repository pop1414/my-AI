# 文档解析与清洗 Issue 拆分

日期：2026-05-13

状态：已发布 GitHub Issues；#14、#15、#16、#17、#18 已关闭

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
- 当前状态：GitHub 已关闭（CLOSED）

目标：

- 避免原生 Markdown 因无谓进入 Tika / HTML 转换链而破坏结构。
- 保持标题、代码块、表格、列表和空行结构稳定进入 `cleaned.md`。

完成同步：

- 原生 Markdown 按 `md` / `markdown` / `mdown` / `mkd` 扩展名进入最小破坏路径，跳过 Tika 与 HTML 重解析链。
- `cleaned.md` 保留标题、代码块围栏、表格、列表缩进和 Setext 标题等 Markdown 原生结构。
- Markdown 清洗仅做换行、不可见字符、明显噪音、危险 raw HTML 与独立图片/本地文件噪音规整。
- 黄金样本与边界样本已覆盖标题、代码块、表格、列表、URL/文件噪音和代码块内 HTML 示例保留。
- chunk preview 已覆盖 Markdown 标题 `sourceHint` 与结构边界回归。

### 3. 原生 HTML 正文抽取与噪音清洗路径

- GitHub Issue：[#15 原生 HTML 正文抽取与噪音清洗路径](https://github.com/pop1414/my-AI/issues/15)
- 类型：AFK
- 依赖：#13
- 覆盖用户故事：11, 12, 17, 18, 19
- 当前状态：GitHub 已关闭（CLOSED）

目标：

- 保留 HTML 主正文结构。
- 清理导航、页脚、脚本、样式、元数据等非正文噪音。

完成同步：

- 原生 HTML 按 `html` / `htm` 扩展名绕过 Tika 主链，直接进入 HTML 语义清洗与 Markdown 转换。
- `cleaned.md` 已保留 `main` 正文中的标题、段落和列表文本语义。
- `nav`、`aside`、`footer`、`script`、`style`、`meta`、`link`、`iframe` 等非正文噪音已通过行为断言排除。
- HTML 黄金样本 `html-001/support-workflow.html` 已串联真实解析与分块，覆盖正文保留、噪音排除和结构保真。
- chunk preview 已验证 HTML 清洗后的独立短标题能生成精确 `sourceHint`，并补充普通短正文不应覆盖标题上下文的负向断言。

### 4. Word 文档结构保真清洗

- GitHub Issue：[#16 Word 文档结构保真清洗](https://github.com/pop1414/my-AI/issues/16)
- 类型：AFK
- 依赖：#13
- 覆盖用户故事：5, 6, 7, 8, 17
- 当前状态：GitHub 已关闭（CLOSED）

目标：

- 保留 Word 标题样式、列表、表格、图片说明或最小图片占位。
- 避免结构化信息退化为不可检索的普通文本。

完成同步：

- HTML 转 Markdown 已改为 ATX 标题输出，避免 Word 标题样式被 setext 下划线清洗成普通正文。
- Word 常见圆点列表已规整为 Markdown 列表，保持列表顺序可读。
- Word 表格中前置的 Markdown 分隔行会修复到表头之后，避免表格退化为难以理解的连续正文。
- Word 黄金样本 `word-001/knowledge-base-review-checklist.docx` 已覆盖标题、列表、表格、图片说明和 OpenXML 包噪音排除。
- 本轮未修改 `qa.ask` 对外响应、vector metadata shape、document version 或外部 API 契约，符合 issue 边界。

### 5. 弱结构 PDF 段落修复与噪音剔除

- GitHub Issue：[#17 弱结构 PDF 段落修复与噪音剔除](https://github.com/pop1414/my-AI/issues/17)
- 类型：AFK
- 依赖：#13
- 覆盖用户故事：2, 3, 4, 17
- 当前状态：GitHub 已关闭（CLOSED）

目标：

- 修复弱结构 PDF 的幽灵换行、错误断段和标题粘连。
- 剔除明显重复的页眉、页脚、页码、热线等噪音。

完成同步：

- 弱结构 PDF 的版式软换行已在 Markdown 结构修复阶段收敛为更稳定的自然段，避免 `cleaned.md` 保留幽灵换行。
- 中文编号标题与正文粘连场景已拆分为独立标题行和正文行，chunk preview 可继续生成可解释的标题 `sourceHint`。
- 页眉、页脚、页码、热线等明显页面噪音已通过弱 PDF 黄金样本行为断言排除。
- 段落修复规则保留空行、标题、列表、表格、引用和代码块边界，避免为了视觉顺滑误合并结构块。
- 弱结构 PDF 黄金样本 `weak-pdf-001/weak-pdf-regression-sample.pdf` 已覆盖段落修复、标题边界、噪音排除和固定正文锚点。

### 6. cleaned.md 质量回归闭环

- GitHub Issue：[#18 cleaned.md 质量回归闭环](https://github.com/pop1414/my-AI/issues/18)
- 类型：AFK
- 依赖：#14, #15, #16, #17
- 覆盖用户故事：1, 13, 14, 16, 17
- 当前状态：GitHub 已关闭（CLOSED）

目标：

- 将黄金样本、chunk preview 和固定 qa.ask 问题串成统一验收闭环。
- 区分 cleaned.md 改善、chunk 边界改善和 qa.ask 偶然变化。

完成同步：

- 新增 `docs/runbooks/plans/ingest-cleaning/cleaned-md质量回归闭环.md` 作为固定验收 runbook。
- 固定完整样本审阅顺序：`weak-pdf-001 -> md-001 -> md-002 -> html-001 -> word-001`。
- 明确每个样本必须串联审阅 `cleaned.md`、`documents/chunks/preview` 和固定 `qa.ask` 结果。
- chunk preview 验收要求记录 chunk 边界、`sourceHint` 可解释性和结构上下文。
- 固定 `qa.ask` 验收要求记录预期命中位置、回答稳定性和不应出现的噪音词。
- 回归记录模板明确区分 `cleaned.md` 改善、chunk 边界改善和 `qa.ask` 偶然变化。
- 后端测试已固定 runbook 的核心验收口径，避免后续修改弱化 #18 闭环要求。

## 推荐执行顺序

1. 先完成 #13，确保黄金样本输入和验收锚点可靠。
2. #14、#15、#16、#17 已关闭。
3. #18 已关闭，统一回归闭环已形成；后续 parser / cleaner 优化按 runbook 填写实际回归记录。

## 与版本治理 Issues 的关系

当前 GitHub 上已有 #1 到 #12 主要覆盖 document / document version、上传新版本、版本回退、删除和 qa 引用版本化。

本轮 #13 到 #18 与这些 issue 的关系是 Related，而不是 Blocked by：

- #13 到 #18 负责 `cleaned.md` 文本质量与验证闭环。
- #1 到 #12 负责文档资产、版本真相、问答引用版本化和治理交互。
- 任一 issue 如果需要修改 `document` 主模型、Flyway、`qa.ask` response DTO 或 `vector metadata shape`，应先暂停并同步设计。
