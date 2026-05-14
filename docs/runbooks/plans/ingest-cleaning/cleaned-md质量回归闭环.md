# cleaned.md 质量回归闭环

日期：2026-05-14

状态：#18 已完成；作为后续 parser / cleaner 优化的固定验收 runbook

关联文档：

- `docs/runbooks/plans/ingest-cleaning/黄金样本与验收说明.md`
- `docs/runbooks/plans/ingest-cleaning/文档解析与清洗Issue拆分.md`
- `src/test/resources/ingest-cleaning/golden/`

## 1. 目的

本文件定义 `cleaned.md` 质量回归的统一审阅顺序、记录模板和结论口径。

回归闭环只回答一个问题：

parser / cleaner 的改动，是否真实改善了进入 chunking、retrieval 和 `qa.ask` 前的文本基底质量。

本闭环不修改：

- `qa.ask` 对外响应结构
- `vector metadata shape`
- document version 语义
- 外部 API 契约
- 父子分块或 richer node model

## 2. 固定样本顺序

每轮 parser / cleaner 回归按以下顺序审阅，顺序不可随意跳过。

| 顺序 | 样本 | 类型 | 主风险 |
| --- | --- | --- | --- |
| 1 | `weak-pdf-001` | 弱结构 PDF | 段落断裂、标题粘连、页眉页脚噪音 |
| 2 | `md-001` | 原生 Markdown | 标题、代码块、表格、列表被无谓降级 |
| 3 | `md-002` | Markdown 边界样本 | 代码块误删、Setext 标题退化、URL 噪音边界 |
| 4 | `html-001` | 原生 HTML | 正文抽取降级、导航页脚噪音混入 |
| 5 | `word-001` | 常规 Word | 标题样式、列表、表格、图片说明退化 |

若只改动某一类 parser，可以先跑对应样本做快速反馈；但合并前仍需按完整顺序补齐记录。

## 3. 统一审阅顺序

每个样本都按同一条路径审阅：

1. 阅读样本目录下的 `README.md`
2. 上传原始文件并等待文档进入 `INDEXED`
3. 审阅该文档本次生成的 `cleaned.md`
4. 调用 `GET /api/v1/documents/{documentId}/chunks/preview`
5. 按样本 `README.md` 中的固定问题调用 `POST /api/v1/qa/ask`
6. 填写本文件的回归记录模板
7. 给出三类结论：`cleaned.md` 改善、chunk 边界改善、`qa.ask` 偶然变化

## 4. cleaned.md 审阅点

`cleaned.md` 是本轮唯一正式中间文本产物，审阅时只看外部可观察质量。

必须记录：

- 标题层级是否稳定
- 段落边界是否可信
- 列表、表格、代码块等结构块是否保真
- 样本 `README.md` 中的不应出现的噪音词是否仍被排除
- 预期正文锚点是否以可读文本进入 `cleaned.md`

不能只记录“看起来更干净”。必须指出改善发生在哪个样本、哪个章节或哪个正文锚点。

## 5. chunk preview 审阅点

chunk preview 只用于验证清洗结果是否更适合后续分块，不升级 chunk、retrieval 或 reference 契约。

必须记录：

- chunk 边界是否围绕真实标题、段落或结构块切分
- 相邻 chunk 是否仍保留足够结构上下文
- `sourceHint` 是否能解释 chunk 来源
- 标题上下文是否比基线更准确
- 是否出现“`cleaned.md` 变好但 chunk 仍切坏”的情况

chunk preview 的改善不能自动等价为 `qa.ask` 改善；两者需要分开记录。

## 6. 固定 qa.ask 审阅点

`qa.ask` 在本闭环中只作为最终回归面，不要求真实模型回答逐字一致。

每个固定问题必须记录：

- 问题文本
- 预期命中位置，至少到章节或正文锚点
- 实际引用或回答是否靠近预期命中位置
- 回答稳定性：稳定、轻微漂移、明显漂移
- 不应出现的噪音词是否进入回答或引用

如果回答内容变化但引用位置仍稳定，应标记为 `qa.ask` 偶然变化，而不是 parser / cleaner 质量改善。

## 7. 回归记录模板

每次验收建议复制下面模板，追加到对应 issue、PR 描述或本地交付记录中。

```md
## cleaned.md 质量回归记录

- 日期：
- 分支：
- 变更范围：
- 样本顺序：weak-pdf-001 -> md-001 -> md-002 -> html-001 -> word-001

### {sampleId}

- 原始文件：
- documentId：
- 处理状态：

#### 1. cleaned.md

- 预期正文锚点：
- 实际观察：
- 噪音词检查：
- 结论：改善 / 持平 / 回退 / 待复核

#### 2. chunks/preview

- 重点 chunk：
- chunk 边界观察：
- sourceHint 观察：
- 结构上下文观察：
- 结论：改善 / 持平 / 回退 / 待复核

#### 3. qa.ask

| 固定问题 | 预期命中位置 | 实际命中位置 | 回答稳定性 | 噪音词 | 结论 |
| --- | --- | --- | --- | --- | --- |
|  |  |  | 稳定 / 轻微漂移 / 明显漂移 | 无 / 有 | 改善 / 偶然变化 / 回退 / 待复核 |

#### 4. 分类结论

- cleaned.md 改善：
- chunk 边界改善：
- qa.ask 偶然变化：
- 是否允许宣称本轮完成：
```

## 8. 结论分类规则

### cleaned.md 改善

满足以下至少一项，且没有引入新噪音或结构回退：

- 固定正文锚点更完整
- 标题和正文边界更清晰
- 幽灵换行、错误断段或错误拼接减少
- 列表、表格、代码块等结构块更可读
- 样本噪音词继续被排除

### chunk 边界改善

满足以下至少一项，且没有改变对外契约：

- chunk 更接近标题、段落或结构块边界
- `sourceHint` 更接近真实章节上下文
- 相邻 chunk 的结构上下文更可解释
- 不再把页眉页脚、导航、OpenXML 元数据等噪音当作来源上下文

### qa.ask 偶然变化

出现以下情况时，只能记录为偶然变化：

- 回答措辞变好，但引用位置没有更接近期望锚点
- 模型回答更完整，但召回 chunk 没有变化
- topK、模型随机性或提示词导致回答变化，无法回溯到 `cleaned.md` 或 chunk 边界改善
- 单个问题变好，但同一样本其他固定问题出现回退

## 9. 完成门槛

只有同时满足以下条件，才能宣称本轮 `cleaned.md` 质量回归闭环通过：

- 每类黄金样本都按固定顺序审阅了 `cleaned.md`、chunk preview 和固定 `qa.ask` 结果
- chunk preview 记录了 chunk 边界、`sourceHint` 可解释性和结构上下文
- 固定 `qa.ask` 记录了预期命中位置、回答稳定性和不应出现的噪音词
- 回归记录明确区分 `cleaned.md` 改善、chunk 边界改善和 `qa.ask` 偶然变化
- 若优化不能改善 `cleaned.md`、chunk preview 或固定 `qa.ask` 稳定性，则不能宣称完成

## 10. 推荐审阅证据

审阅时至少保留这些证据：

- `cleaned.md` 中与样本 README 对应的正文锚点片段
- chunk preview 中最能说明边界问题或改善的 chunk
- `sourceHint` 原始值
- 每个固定问题的 `qa.ask` 请求和响应摘要
- 噪音词检查结果

证据只需足够支撑判断，不要求保存完整模型回答全文。
