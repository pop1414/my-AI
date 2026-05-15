package io.github.spike.myai.qa.application.result;

import java.util.List;

/**
 * 问答结果对象（应用层返回模型）。
 *
 * <p>当未命中有效上下文或模型返回空内容时，
 * {@code answer} 可能为应用层定义的兜底文案。
 *
 * @param answer 回答文本
 * @param references 引用分块列表，顺序与检索命中顺序一致
 * @param staleReferences 陈旧引用汇总；无引用时为空
 */
public record AskQuestionResult(
        String answer,
        List<AskReferenceResult> references,
        AskStaleReferenceSummaryResult staleReferences) {

    /**
     * 兼容旧调用方的简化构造器。
     *
     * @param answer 回答文本
     * @param references 引用分块列表
     */
    public AskQuestionResult(String answer, List<AskReferenceResult> references) {
        this(answer, references, null);
    }
}
