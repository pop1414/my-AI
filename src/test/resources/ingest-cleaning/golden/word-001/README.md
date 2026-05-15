# word-001

## 1. 样本定位

- 类型：常规 Word
- 目标：验证 `MsoTitle` / `MsoHeading` 映射、列表/表格保真与图片占位保留
- 当前状态：真实 `.docx` 输入与 Markdown 源草稿均已落地，可作为后续 parser / cleaner 回归输入

## 2. 原始文件

- 最终验收输入：`knowledge-base-review-checklist.docx`
- 源草稿：`knowledge-base-review-checklist-source.md`
- 来源说明：合成 Word 文档，真实 `.docx` 中包含 `Title`、`Heading1`、项目符号列表、表格、图片占位与图片说明段

## 3. 关键失真点

- `MsoTitle` / `MsoHeading*` 未稳定映射为 Markdown 标题
- 列表丢失层级或被压平成普通段落
- 表格被错误打散为连续文本
- 图片说明缺失或占位不稳定

## 4. 不应出现的噪音词

这些词不属于 Word 主体业务内容，不应出现在回答或 `cleaned.md` 主体正文中：

- `Codex sample generator`
- `ingest-cleaning, golden, docx`
- `docProps`
- `word/_rels`

## 5. 覆盖目标矩阵

- `(word, 标题漂移, cleaned-md)`
- `(word, 结构退化, cleaned-md)`
- `(word, 结构退化, chunks-preview)`
- `(word, 噪音混入, qa-ask)`
- `(word, 段落断裂, qa-ask)`

## 6. 固定 QA 问题与预期命中锚点

1. 文档的一级标题是什么，“上线前核对项”下面列出了哪三项内容？
   - 预期命中：`知识库文档上线前核对清单`、`上线前核对项`
   - 正文锚点：`确认知识库名称与文档主题一致`、`确认文档中的敏感信息已经脱敏`、`确认固定 QA 问题能够从正文直接定位答案`
2. “图片说明保留建议”这一节主要强调了什么要求？
   - 预期命中：`图片说明保留建议`
   - 正文锚点：`至少要保留图片的说明文字`、`保留“图片展示了什么”的最小文字描述`
3. 表格里“表格被拍平”这一风险对应的回归关注点是什么？
   - 预期命中：`回归风险对照`
   - 正文锚点：`documents/chunks/preview 是否仍能给出结构化上下文`

## 7. 人工审阅重点

- `cleaned.md`：标题层级是否稳定，列表、表格、图片占位与图片说明是否保真
- `documents/chunks/preview`：chunk 是否保留 `上线前核对项` 与 `回归风险对照` 的结构上下文，表格行列是否仍可解释
- `qa.ask`：固定 QA 问题是否命中对应结构块，而不是只命中零散正文或 OpenXML 包元数据
