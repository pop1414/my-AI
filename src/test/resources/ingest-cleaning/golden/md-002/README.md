# md-002

## 1. 样本定位

- 类型：原生 Markdown 边界样本
- 目标：验证原生 Markdown 最小破坏清洗不会误伤代码块、Setext 标题、表格、引用、嵌套列表和正文 URL
- 当前状态：原始 Markdown 输入已落地，可直接用于回归

## 2. 原始文件

- 文件名：`markdown-edge-cases.md`
- 来源说明：合成 Markdown 文档，集中覆盖代码块内 HTML、代码块外 raw HTML、Setext 标题、表格转义管道、缩进代码、独立噪音 URL 与正文 URL 边界

## 3. 关键失真点

- 代码块内的 `<script>`、`<iframe>` 示例被危险 HTML 清理误删
- 代码块外的危险 raw HTML 没有被清理
- Setext 标题分隔线被当成装饰线删除
- 表格中的转义管道和行内代码被破坏
- 嵌套引用、列表缩进和缩进代码被拍平
- 独立图片 URL、图片文件名和 file URL 噪音残留
- 正文中的解释性 URL 被误删

## 4. 不应出现的噪音词

以下词来自其他样本或危险 raw HTML 噪音，不应出现在回答或 `cleaned.md` 主体正文中：

- `remove script noise`
- `https://noise.example.com`
- `image42.png`
- `file:///tmp/tika-cache.html`
- `内部评审稿`
- `质检热线`
- `控制台首页`
- `页脚热线`

## 5. 覆盖目标矩阵

- `(markdown, 代码块误删, cleaned-md)`
- `(markdown, raw-html 噪音残留, cleaned-md)`
- `(markdown, setext 标题退化, cleaned-md)`
- `(markdown, 表格转义退化, cleaned-md)`
- `(markdown, 列表缩进退化, chunks-preview)`
- `(markdown, URL 边界误删, cleaned-md)`

## 6. 固定 QA 问题与预期命中锚点

1. 代码块中的 HTML 示例应该如何处理？
   - 预期命中：`代码块中的 HTML 示例`
   - 正文锚点：`keep script example`、`iframe`
2. Setext 标题下面的分隔线代表什么？
   - 预期命中：`Setext 二级标题`
   - 正文锚点：`标题语义`
3. URL 噪音边界要求是什么？
   - 预期命中：`URL 噪音边界`
   - 正文锚点：`正文中的图片链接`、`解释性正文`

## 7. 人工审阅重点

- `cleaned.md`：代码块内 HTML 示例是否保留，代码块外危险 raw HTML 是否清理，Setext 标题、表格和正文 URL 是否仍可读
- `documents/chunks/preview`：chunk 是否围绕 `代码块中的 HTML 示例`、`表格与转义管道`、`URL 噪音边界` 等标题切分
- `qa.ask`：固定 QA 问题是否命中边界样本正文，而不是被外部噪音或 raw HTML 噪音污染
