package io.github.spike.myai.qa.infrastructure.eval;

/**
 * 评测相关度级别枚举。
 *
 * <p>用于标注检索评测中参考文档的相关程度。
 * strong 表示强相关（核心命中），weak 表示弱相关（边缘参考）。
 * 基础指标（Recall@5、MRR、HitRate@5）仅统计 strong，weak 预留用于 NDCG 加权。</p>
 *
 * @author spike
 * @since 1.0.0
 */
enum RelevanceLevel {

    /** 强相关 — 核心命中文档 */
    STRONG,

    /** 弱相关 — 边缘参考文档，NDCG 加权预留 */
    WEAK
}
