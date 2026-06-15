# 文档解析与清洗 PRD

日期：2026-05-14

状态：纯文字阶段已按 #14 - #18 完成；本文作为需求真源与后续增强边界参考

关联文档：

- `docs/runbooks/plans/ingest-cleaning/RAG 文档解析与清洗方案.md`
- `docs/runbooks/plans/ingest-cleaning/黄金样本与验收说明.md`
- `docs/runbooks/plans/ingest-cleaning/并行开发边界约定-文档版本治理与RAG优化.md`
- `docs/runbooks/plans/ingest-cleaning/文档解析与清洗Issue拆分.md`

## 问题陈述

当前 ingest 主链已经能完成“源文件读取 -> 文件类型路由 -> 解析与清洗 -> cleaned.md 落盘 -> 分块 -> 向量化 -> 入库与状态收口”。本 PRD 对应的纯文字阶段已经完成首轮实现与回归闭环，后续更真实的 RAG 使用场景将主要继续挑战图片、表格、OCR、复杂版式和 richer node model。

纯文字阶段曾经要解决的问题包括：

- 弱结构 PDF 可能出现幽灵换行、错误断段、标题和正文粘连、页眉页脚噪音混入。
- 常规 Word 文档可能出现标题样式丢失、列表层级退化、表格被拍平成普通文本、图片说明缺失。
- 原生 Markdown 可能被无谓重解析，导致代码块、表格、列表和空行结构被破坏。
- 原生 HTML 可能把导航、页脚、边栏等非正文内容带入 cleaned.md，或在转换过程中丢失正文语义结构。
- cleaned.md 一旦不稳定，后续 chunk preview 与 qa.ask 即使逻辑正确，也会被坏输入放大，表现为召回位置漂移、引用噪音变多、问答结果不稳定。

本 PRD 要解决的是“进入 chunking 前的文本基底质量”问题，而不是重定义检索、引用、版本治理或节点契约。当前这些纯文字问题已经通过文件类型路由、HTML/Markdown 清洗、结构修复、黄金样本与回归闭环收口。

## 解决方案

本轮以高质量 `cleaned.md` 为唯一正式中间文本产物，优化文档解析与清洗路径，使不同文件类型进入合适的处理路线，并通过黄金样本、chunk preview 和固定 qa.ask 问题建立可回归的质量验证闭环。当前实现已完成这一目标。

用户最终应获得的效果是：

- 上传常见纯文字文档后，系统能产出更接近人类可读正文的 cleaned.md。
- 标题、段落、列表、表格、代码块等结构在 cleaned.md 中保持稳定。
- 页眉页脚、导航、脚本样式、重复块等噪音明显减少。
- chunk preview 的边界更容易沿着真实语义结构切分。
- 固定 qa.ask 问题可作为最终回归面，帮助区分 parser / cleaner 改善和模型偶然变化。

本轮不升级 qa.ask 对外响应，不升级 vector metadata shape，不引入父子分块，不落地 richer node model，也不修改 document 版本治理主模型。

## 用户故事

1. 作为知识库维护者，我希望上传文档后能产出稳定的 cleaned.md，以便信任进入 chunking 和 retrieval 的文本基底。
2. 作为知识库维护者，我希望弱结构 PDF 中由版式换行造成的段落断裂能被修复，以便问答结果不会引用破碎句子。
3. 作为知识库维护者，我希望弱结构 PDF 中明显重复的页眉页脚能被清理，以便 qa.ask 不会围绕运维噪音作答。
4. 作为知识库维护者，我希望 PDF 的标题和正文能保持边界清晰，以便 chunk 的 sourceHint 仍然可解释。
5. 作为知识库维护者，我希望 Word 的标题样式能转换成 Markdown 标题，以便文档结构在入库后仍然可见。
6. 作为知识库维护者，我希望 Word 的列表顺序和层级能被保留，以便清单类文档仍然适合检索。
7. 作为知识库维护者，我希望 Word 的表格在清洗后仍可阅读，以便风险项、对照项和结构化信息仍能被引用。
8. 作为知识库维护者，我希望图片说明或最小图片占位能被保留，以便重要上下文不会被静默丢弃。
9. 作为 Markdown 文档作者，我希望原生 Markdown 避免不必要的 Tika 和 HTML 转换，以便代码块、表格、列表和标题不被降级。
10. 作为 Markdown 文档作者，我希望 Markdown 只做最小的不可见字符和噪音规整，以便原始写作结构仍然可识别。
11. 作为 HTML 文档作者，我希望系统能保留主要正文结构，以便章节标题、段落和列表仍然有清晰语义。
12. 作为 HTML 文档作者，我希望导航、页脚、脚本、样式和元数据噪音能被移除，以便非正文 UI 文本不会污染检索。
13. 作为支持值班人员，我希望 chunk preview 能展示更稳定的切分边界，以便快速判断 parser 和 cleaner 输出是否可用。
14. 作为支持值班人员，我希望 sourceHint 在清洗后仍然可解释，以便把 chunk 追溯到对应的章节上下文。
15. 作为评审者，我希望弱结构 PDF、Word、Markdown 和 HTML 都有固定黄金样本，以便一致地评估 parser 和 cleaner 变更。
16. 作为评审者，我希望每个黄金样本都有固定 QA 问题，以便 qa.ask 回归问题能被看见，而不是依赖主观印象。
17. 作为评审者，我希望验证重点落在 cleaned.md 结构、preview 边界和回答引用等外部行为上，以便测试不会变成脆弱的实现快照。
18. 作为开发者，我希望 parser routing 规则按来源类型显式定义，以便后续改动不会误把 Markdown 或 HTML 送入破坏性转换路径。
19. 作为开发者，我希望 cleaning 行为封装在简单的 parser-facing interface 后面，以便 application service 不依赖具体清洗细节。
20. 作为开发者，我希望失败行为继续使用现有 ingest 状态，以便解析质量优化不会发明新的生命周期状态。
21. 作为开发者，我希望质量较弱但仍可消费的解析结果在需要时通过 processingMetadata 表达，以便 document 状态语义保持稳定。
22. 作为开发者，我希望本轮不改变 qa.ask response DTO，以便 RAG 质量优化可以避开文档版本治理的冲突区。
23. 作为开发者，我希望本轮不改变 vector metadata shape，以便现有 retrieval、preview 和幂等行为保持稳定。
24. 作为后续维护者，我希望未来 node model 想法与本轮工作分开记录，以便 richer retrieval 设计不会提前泄漏到 cleaned.md 基线工作中。

## 实现决策

- 本轮主目标是提升 `cleaned.md` 质量，`cleaned.md` 继续作为 ingest 主链的正式中间文本产物。
- 解析入口继续通过现有文档文本解析端口返回结构化解析结果，上层 application service 不感知具体文件类型清洗细节。
- 文件类型路由需要区分复杂格式、原生 Markdown 和原生 HTML。
- PDF、Word 等复杂格式继续采用 Tika 输出 XHTML，再进入 HTML 语义清洗与 Markdown 转换。
- 原生 Markdown 应优先走最小破坏路径，只做换行、不可见字符、明显噪音和安全性规整。
- 原生 HTML 可以绕过 Tika，直接进入 HTML 语义清洗与 Markdown 转换，避免无谓语义降级。
- 清洗能力应作为 parser adapter 内部的 deep module 演进，对上游暴露简单稳定的调用面。
- 清洗规则优先覆盖脚本样式删除、HTML 注释删除、导航页脚噪音删除、Word 标题样式映射、图片占位保留、空块清理和 Markdown 规整。
- 幽灵换行修复应以弱结构 PDF 为主要对象，避免为了视觉顺滑而错误拼接本应分开的段落。
- cleaned.md 的验收以标题层级稳定、段落边界可信、结构块保真、噪音可控、对后续分块友好为核心。
- processingMetadata 仍是文档级处理结果元数据，不升级为最终 RAG 节点契约。
- 若 parser 无法产出可继续消费的 cleaned.md，仍按现有 ingest 主链收口为处理失败。
- 若文本可继续消费但质量较弱，可以通过处理元数据表达质量观察信息，但不新增文档生命周期状态。
- 本轮允许小幅优化结构优先分块器的确定性行为，但仅限于服务 cleaned.md 验证，不改变 chunk、retrieval 或 reference 对外契约。
- 本轮不修改 document 主模型，不新增 Flyway 迁移，不调整版本治理接口。
- 本轮不修改 qa.ask 对外响应结构，不新增版本字段、页码字段或 richer reference 字段。
- 本轮不修改 vector metadata shape，不把质量标签、标题路径或未来 node 字段提前写入稳定向量契约。
- 父子分块、nodeId、parentNodeId、displayContent、indexContent 等 richer node model 保持未来草案，不进入本轮实现范围。
- 黄金样本已经覆盖弱结构 PDF、Word、Markdown、Markdown 边界样本和 HTML 输入。
- weak PDF 与 Word 已补真实 `.pdf` 和 `.docx` 样本；源草稿仅作为样本生成和审阅辅助，不作为最终验收输入。

## 测试决策

- 测试应优先验证外部可观察行为，而不是锁死具体内部实现步骤。
- parser route 测试应验证不同文件类型进入合适处理路径，并产出非空、结构稳定的 cleaned.md。
- cleaner 单元测试应覆盖 HTML 噪音删除、Word 标题映射、图片占位、Markdown 空白规整、代码块保护、表格和列表保真。
- Markdown 样本测试应验证原生 Markdown 的标题、代码块、表格、列表在清洗后语义等价。
- HTML 样本测试应验证主正文保留，导航、页脚、脚本样式和元数据不进入 cleaned.md。
- weak PDF 回归测试应重点验证幽灵换行修复、标题边界和页眉页脚噪音清理。
- Word 回归测试应重点验证标题样式、列表、表格和图片说明。
- chunk preview 验证应作为辅助验收，关注 chunk 边界、sourceHint 可解释性和结构上下文是否改善。
- 固定 qa.ask 验证应作为最终回归面，关注是否更容易召回正确 chunk、回答是否更稳定、引用是否落在预期正文位置。
- 不建议使用全文 snapshot 作为唯一验收方式，因为轻微格式变化可能造成大量低价值失败。
- 可以使用局部断言、结构断言、噪音排除断言和固定问题期望锚点组合验收。
- 既有测试先例包括 parser 单元测试、cleaner 单元测试、处理应用服务测试和 chunk preview 应用服务测试。
- 本轮测试不要求真实模型回答完全一致；qa.ask 回归应优先记录固定问题、预期命中位置和不应出现的噪音词。

## 非目标范围

- 不落地父子分块。
- 不落地 richer node model。
- 不引入 nodeId、parentNodeId、childNodeIds。
- 不升级 RetrievedChunk。
- 不升级 AskReferenceResponse。
- 不升级 qa.ask 对外契约。
- 不调整 vector metadata shape。
- 不为引用结果新增页码、版本号、标题路径等对外字段。
- 不修改 document / document version 模型。
- 不新增或修改 Flyway schema。
- 不调整文档版本治理语义。
- 不把 processingMetadata 定义为最终节点契约。
- 不要求页码保留作为本轮硬性通过条件。
- 不要求关键词抽取稳定作为本轮硬性通过条件。
- 不引入 OCR 或复杂扫描件稳定支持承诺。
- 不引入图片理解或表格结构化节点承诺。

## 补充说明

- 本 PRD 已拆分并落地为 #14 - #18，后续不应再按“待拆分草稿”理解。
- 当前最适合在后端 parser / cleaner 与测试资源基础上继续补多模态或复杂版式样本，不应为了纯文字清洗回归同步推进前端改动。
- 如后续发现必须修改向量元数据、问答引用响应或版本治理模型，应暂停本 PRD 的实现推进，先更新并行开发边界或补充 ADR。
- 验收时不要只看 cleaned.md 是否更美观，还要确认 chunk preview 和固定 qa.ask 没有因为清洗变得更差。
- 如果某次优化不能在黄金样本上改善 cleaned.md、chunk preview 或固定 qa.ask 的稳定性，就不应宣称本轮完成。
