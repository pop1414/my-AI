# PRD Quality Review — RAG 检索链路优化

## Overall verdict

**这是一份高质量的技术能力型 PRD。** 决策链路清晰（D3/D4/D5/D6/D17 全部锁定并有论文依据），功能需求颗粒度精确到文件路径和验收条件，Non-Goals 和假设索引完备，对下游 story 创建和架构设计的支撑力很强。唯一需要补强的是 User Journeys 缺少命名角色和叙事结构（模板要求的 entry state → path → climax → resolution），以及少量 Mechanical 细节。

---

## 1. Decision-readiness — ⭐ strong

PRD 的决策质量很高：

- **§1 愿景** 的论文依据表（消融实验数据 + 项目决策）是亮点——每个选择都有量化代价/收益支撑，不是拍脑袋。
- **§5 非目标** 列出 9 项，每项有理由，尤其是 NG-1（HyDE 换 8 倍延迟）和 NG-2（Reranking 边际收益最低）的排除逻辑清晰。
- **§8 开放问题** 仅 1 项（OQ-1：hybridSearch 方法挂载方式），合理——说明大部分决策已在 discovery 阶段锁定。
- **反指标**（§7）明确指出"Recall@5 低于纯 Dense 基线则回退"，这是真实的决策承诺，不是装饰。

### Findings

- **medium** OQ-1 缺乏倾向性建议 (§8) — "待 architecture 阶段确定"过于中立。建议补充倾向方向："考虑到 `ChunkRetrievalPort` 已有现有实现者，倾向在同接口新增方法（需验证 Spring AI 的接口约束）"，给下游架构决策一个起点。 *Fix:* 在 OQ-1 行尾加一行 `[倾向: 新增方法，理由: ...]`。

---

## 2. Substance over theater — ⭐ strong

零膨胀感。每一段都有功能：

- **JTBD**（§2.1）5 条全部对应具体决策（D3/D5/D17/D6/D4），没有"用户希望系统好用"这种空话。
- **NFR**（横切关注点）三条都是产品特定的：200ms 延迟增量、零新依赖、六边形合规——不是"系统应可扩展安全可靠"的样板。
- **论文依据表**（§1）把"为什么选这个方案"和"为什么不选那个方案"放在同一张表里，高效。
- **推出计划**（末尾）有工时估算和依赖关系，不是空洞的里程碑列表。

### Findings

无重大发现。

---

## 3. Strategic coherence — ⭐ strong

PRD 有一个清晰的论文：

> "从'单路盲检'升级为'多路融合 + 智能路由 + 数据驱动调优'"

功能分组围绕这条主线展开：

1. 基础准备（让数据可观察）→ 2. 查询分类（智能路由）→ 3. 混合检索（多路融合）→ 4. 评估体系（数据驱动）

依赖图（§4 开头的 ASCII 图）精确反映了这个逻辑链。MVP 切分合理——FR-12/13 推迟到 Phase 2 有理有据（"依赖 Layer 1 基线稳定后"）。

### Findings

无重大发现。

---

## 4. Done-ness clarity — ⭐ strong

这是 PRD 最强的维度。每个 FR 都有：

- **验收标准**（checkbox 格式，可直接转化为 story 的 acceptance criteria）
- **影响文件**（精确到 `src/main/java/...` 路径 + 变更类型）
- 具体的可测试条件，如 "包含 'Flyway' 关键词的查询能检索到含 'Flyway' 的 chunk"

### Findings

- **low** FR-5 验收标准缺少边界 case (§4.2 FR-5) — "每种 QueryType 至少 3 个测试用例"很好，但没有覆盖混合意图的场景（如 "如何对比 Spring Boot 和 Quarkus 的性能" 同时包含 PROCEDURAL 和 COMPARATIVE 特征）。 *Fix:* 加一条验收标准："混合意图查询按优先级匹配（PROCEDURAL > COMPARATIVE > FACTOID > GENERAL），有对应测试用例"。
- **low** UJ-2 延迟数字需标注估算性质 (§2.2 UJ-2) — "~2s"和"~0.5s"是估算还是实测？如果是估算，标注 `[估算，待实测校准]`，避免被下游直接引用为 SLA。

---

## 5. Scope honesty — ⭐ strong

PRD 在范围管理上做得非常扎实：

- **§5 非目标** 9 项，每项有理由和决策编号回溯。
- **§6.2** 明确标注 Phase 2 推迟项及推迟理由。
- **§9 假设索引** 3 项，都有"如果错误的影响"列。
- **与 docling-upgrade 的并行策略** 额外说明了跨项目边界的风险，超出一般 PRD 的范围管理。

### Findings

- **medium** 假设 A-1 应在 §4 FR-1 内联标记 (§9 → §4) — 模板要求 `[ASSUMPTION: ...]` 内联标记，当前假设只出现在 §9 索引中，FR-1 正文没有回溯。下游读者只看 §4 时会漏掉这个风险。 *Fix:* 在 FR-1 的 "Dense 检索路径：score = 1 - cosine_distance" 行后加 `[ASSUMPTION: Spring AI similaritySearch 返回值可直接用于 cosine similarity 计算，见 A-1]`。
- **low** A-2 应在 FR-7/FR-8 内联标记 (§9 → §4) — 同理，`'simple'` 分词质量假设应标记在 FR-7 和 FR-8 中。 *Fix:* 在 FR-7 的 "'simple' 文本搜索配置" 后加 `[ASSUMPTION: 'simple' 配置对英文技术术语分词足够，见 A-2]`。

---

## 6. Downstream usability — ⭐ adequate

PRD 对下游 story 创建和架构设计的支撑力很强，但 UJ 格式不符合模板规范：

**强项：**
- **术语表**（§3）12 个术语，定义清晰，后续 FR 引用时用词一致。
- **FR ID** 连续（FR-1 到 FR-13），无空缺无重复。
- **影响文件** 列表是下游 story 创建的直接输入——开发者可以立刻知道改哪些文件。
- **级联变更范围** 表额外提供了文件 ↔ FR 的反向映射。

**弱项：**
- **UJ 格式** 与模板要求有显著差距（见下方 Findings）。

### Findings

- **high** UJ 缺少命名角色和叙事结构 (§2.2) — 模板要求每个 UJ 有：命名角色（persona + context）、入口状态（entry state）、路径（3-5 个具体步骤）、高潮（climax — 价值交付时刻）、结局（resolution）。当前 UJ 是系统视角的技术流程描述（"系统通过 QueryClassifier 判定为…"），不是用户视角的叙事。这对当前的内部工具 PRD 影响有限（Shape fit 维度会详述），但如果 PRD 未来要喂给 UX 工作流则需要改造。 *Fix:* 至少给每个 UJ 加一个命名角色。例如 UJ-1: "后端开发者 spike 在调试 RAG 检索时输入精确技术术语…"，UJ-2: "新用户小明第一次使用系统，随手打了句'你好'…"。无需完全按模板的 entry state → climax → resolution 展开（内部工具可以轻量化），但角色命名是底线。
- **low** Glossary "BM25" 定义含近似声明 (§3) — "本项目使用 ts_rank 近似实现"这个声明很重要，但术语表定义本身应简洁。建议将实现细节移到 FR-8 描述中，术语表保留："BM25 — 经典稀疏检索算法，用于关键词相关性评分。" *Fix:* 拆分定义和实现说明。

---

## 7. Shape fit — ⭐ strong

PRD 的形状完全匹配产品类型：

- **内部工具 + 单操作者**（spike 既是开发者也是用户）→ 能力规范型 PRD，UJ 可以轻量化。
- **Brownfield** → 所有文件路径引用都是真实路径，架构约束与 `project-context.md` 一致（domain 零注解、JdbcTemplate、record 数据对象等）。
- **Chain-top**（喂给 architecture → epics/stories）→ 影响文件和级联变更表为下游提供了直接输入。

PRD 没有强行加入不适用的消费者产品要素（美学、信息架构、定价等），也没有为 regulated domain 添加空洞的合规声明。

### Findings

无重大发现。

---

## Mechanical notes

### 术语一致性
- ✅ Dense/Sparse/BM25/RRF/QueryType 全文用法一致，与 Glossary 一致。
- ✅ "score" 全文统一使用，没有混用"分数""置信度""相似度"。

### ID 连续性
- ✅ FR-1 到 FR-13 连续无空缺。
- ✅ UJ-1 到 UJ-3 连续。
- ✅ SM-1 到 SM-4 连续。
- ✅ NG-1 到 NG-9 连续。
- ✅ A-1 到 A-3 连续。
- ⚠️ OQ 只有 OQ-1，不算问题但值得注意——如果 architecture 阶段产出新问题，需回来追加。

### 交叉引用
- ✅ FR → 决策编号（D3/D4/D5/D6/D17）回溯完整。
- ✅ 推出计划 → FR 编号 → 功能组 → 依赖关系，链条闭合。
- ⚠️ UJ → FR 的正向引用缺失 — UJ 中提到 "Hybrid Search""QueryClassifier" 等组件但没有标注对应的 FR 编号。建议在 UJ 叙述中加注 `→ FR-9` 等反向链接。

### 假设索引 roundtrip
- ⚠️ §9 的 A-1/A-2/A-3 在正文中没有 `[ASSUMPTION]` 内联标记（§5 Findings 已述）。

### 前置文档引用
- ✅ §0 明确列出了 3 个前置文档及其路径。
- ✅ 论文路径 `docs/reference/RAG/` 与决策 Register 路径一致。

### Frontmatter
- ✅ 含 title, status, created, updated。
- ⚠️ 缺少 `type` 字段（模板未强制，但标记为 `type: prd` 可帮助下游工具自动识别）。

---

## 优先修复建议

| 优先级 | 编号 | 修复内容 | 工作量 | 状态 |
|--------|------|---------|--------|------|
| 🔴 高 | F-1 | UJ 加命名角色（至少 persona 名字 + 一句话 context） | 15 分钟 | ✅ 已修复 |
| 🟡 中 | F-2 | §9 假设在 §4 FR 中内联标记 `[ASSUMPTION: ...]` | 10 分钟 | ✅ 已修复 |
| 🟡 中 | F-3 | OQ-1 补充倾向方向 | 5 分钟 | ✅ 已修复 |
| 🟢 低 | F-4 | FR-5 补充混合意图验收标准 | 5 分钟 | ✅ 已修复 |
| 🟢 低 | F-5 | UJ-2 延迟数字标注估算性质 | 2 分钟 | ✅ 已修复（随 F-1 一起） |
| 🟢 低 | F-6 | Glossary BM25 拆分定义与实现说明 | 3 分钟 | ✅ 已修复 |
| 🟢 低 | F-7 | UJ 补充 FR 反向链接 | 10 分钟 | ✅ 已修复 |
