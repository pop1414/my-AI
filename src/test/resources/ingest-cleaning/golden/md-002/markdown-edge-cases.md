# Markdown 边界清洗样本

这份样本用于覆盖原生 Markdown 清洗中最容易被误伤的边界情况。

Setext 二级标题
---

Setext 标题下面的分隔线是标题语义，不应被当成普通装饰线删除。

## 1. 代码块中的 HTML 示例

下面的代码块故意包含危险 HTML 标签。它们是文档示例，不是正文噪音。

```html
<script>
alert("keep script example");
</script>
<iframe src="https://example.com/embed"></iframe>
```

代码块外的危险 HTML 应被清理：

<script>alert("remove script noise")</script>

<iframe src="https://noise.example.com"></iframe>

## 2. 表格与转义管道

| 场景 | 输入 | 预期 |
| --- | --- | --- |
| 转义管道 | `a \| b` | 保留单元格内容 |
| 行内代码 | `Map<String, Object>` | 不应被 HTML 清理误删 |

## 3. 引用、嵌套列表与缩进代码

> 值班备注：
> - 首先检查 cleaned.md
>   - 再检查 chunks preview
> - 最后检查 qa.ask 引用

    SELECT  *
    FROM   ingest_documents
    WHERE  status = 'INGESTED';

## 4. URL 噪音边界

image42.png
https://static.example.com/diagram.png
file:///tmp/tika-cache.html

正文中的图片链接 https://static.example.com/manual.png 应保留，因为它是解释性正文的一部分。

正文中的本地路径提示 file:///docs/local-note.md 也应保留，因为它不是独立噪音行。

## 5. 验收说明

这份样本的验收重点是区分“正文示例”和“文件噪音”：代码块里的示例必须保留，代码块外的危险 raw HTML 和独立资源行必须清理。
