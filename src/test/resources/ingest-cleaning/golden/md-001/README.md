# md-001

## 1. 样本定位

- 类型：原生 Markdown
- 目标：验证原生 Markdown 在当前链路中走最小破坏路径，不被重解析无谓降级
- 当前状态：原始文件已落地，待补基线产物

## 2. 原始文件

- 文件名：`project-handoff-checklist.md`
- 来源说明：合成 Markdown 文档，包含标题、列表、表格、代码块与嵌套列表，用于验证最小破坏路径

## 3. 关键失真点

- 标题层级被改写
- 代码块围栏丢失或缩进错乱
- 表格列对齐退化
- 列表层级或空行被不必要修改

## 4. 覆盖目标矩阵

- `(markdown, 结构退化, cleaned-md)`
- `(markdown, 结构退化, chunks-preview)`
- `(markdown, 标题漂移, cleaned-md)`
- `(markdown, 噪音混入, qa-ask)`
- `(markdown, 段落断裂, qa-ask)`

## 5. 固定 QA 问题

1. 这份文档的主要主题是什么？
2. 代码块中的 `curl` 命令主要用来检查什么？
3. 表格里“标题层级稳定”这一项的通过标准和常见失真分别是什么？

## 6. 人工审阅重点

- `cleaned.md` 与原始 Markdown 在语义结构上是否基本等价
- 代码块、表格、列表是否仍可直接阅读
- `documents/chunks/preview` 是否仍能给出可解释的结构边界
- 固定 QA 问题是否能稳定引用原始 Markdown 中对应结构块
