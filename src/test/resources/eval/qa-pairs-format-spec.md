# retrieval-qa-pairs.json 格式规范

## 用途

这是 RAG 检索系统的评测数据集。每条记录代表一个「用户问题」+ 它应该命中的「正确文档 ID」。
系统会用这个问题去检索，然后对比返回的文档 ID 和你标注的文档 ID，计算 Recall、MRR、HitRate。

## JSON 结构

```json
[
  {
    "question": "用户会怎么问这个问题（自然语言）",
    "query_type": "PROCEDURAL",
    "relevant_doc_ids": ["文档ID-1", "文档ID-2"],
    "relevance_levels": {
      "文档ID-1": "strong",
      "文档ID-2": "weak"
    }
  }
]
```

## 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| question | string | ✅ | 用户提问，必须自然、口语化，像真人会问的 |
| query_type | enum | ✅ | 查询类型，见下方枚举 |
| relevant_doc_ids | string[] | ✅ | 这个问题的正确答案对应的文档 ID 列表。闲聊类为空数组 `[]` |
| relevance_levels | object | ✅ | 每个相关文档的相关度级别。闲聊类为空对象 `{}` |

## query_type 枚举（5 种）

| 类型 | 含义 | 示例问题 | 特点 |
|------|------|----------|------|
| FACTOID | 事实性问题 | "什么是 PGVector" | 答案明确，通常命中 1 篇 |
| PROCEDURAL | 步骤/操作类 | "如何配置 Flyway" | 可能命中多篇相关文档 |
| COMPARATIVE | 对比类 | "Dense 和 Sparse 检索的区别" | 通常需要 2+ 篇文档 |
| CHITCHAT | 闲聊 | "你好" / "谢谢" | **relevant_doc_ids 必须为空 `[]`**，不命中任何文档 |
| GENERAL | 模糊/宽泛查询 | "系统架构设计" | 关键词模糊，可能命中多篇 |

## relevance_levels 级别

| 级别 | 含义 | 用于 |
|------|------|------|
| strong | 核心相关，必须命中 | Recall@5、MRR、HitRate@5 计算 |
| weak | 边缘相关，命中算加分 | 预留 Phase 2（NDCG） |

## 要求

1. **每种 query_type 至少 4 条**（CHITCHAT 固定 4 条，question 不变）
2. **总计 20~30 条**
3. question 必须是**口语化中文**，不能是文档标题的复制粘贴
4. **relevant_doc_ids 里的 ID 必须是真实存在的文档 ID**（不是自造的假 ID）
5. 一条问题可以标注 1~3 个 relevant_doc_ids，其中至少 1 个是 strong
