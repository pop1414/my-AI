# word-001

## 1. 样本定位

- 类型：常规 Word
- 目标：验证 `MsoTitle` / `MsoHeading` 映射、列表/表格保真与图片占位保留
- 当前状态：原始内容草稿已落地，待转换为 `.docx` 与补基线产物

## 2. 原始文件

- 文件名：`knowledge-base-review-checklist-source.md`
- 来源说明：合成 Word 原始内容草稿，后续可直接转为 `.docx`；内容包含标题、列表、表格与图片说明段

## 3. 关键失真点

- `MsoTitle` / `MsoHeading*` 未稳定映射为 Markdown 标题
- 列表丢失层级或被压平成普通段落
- 表格被错误打散为连续文本
- 图片说明缺失或占位不稳定

## 4. 覆盖目标矩阵

- `(word, 标题漂移, cleaned-md)`
- `(word, 结构退化, cleaned-md)`
- `(word, 结构退化, chunks-preview)`
- `(word, 噪音混入, qa-ask)`
- `(word, 段落断裂, qa-ask)`

## 5. 固定 QA 问题

1. 文档的一级标题是什么，“上线前核对项”下面列出了哪三项内容？
2. “图片说明保留建议”这一节主要强调了什么要求？
3. 表格里“表格被拍平”这一风险对应的回归关注点是什么？

## 6. 人工审阅重点

- `cleaned.md` 中标题层级是否稳定
- 列表、表格、图片占位是否保真
- `documents/chunks/preview` 是否仍能保留足够结构上下文
- 固定 QA 问题是否能命中对应结构块，而不是只命中零散正文
