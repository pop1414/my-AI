package io.github.spike.myai.qa.application.command;

/**
 * 问答用例输入命令对象。
 *
 * <p>该对象位于应用层，用于承接接口层入参并集中完成输入合法性校验、
 * 默认值解析和规范化处理，避免这些逻辑散落在控制器或服务中。
 *
 * @param question 用户问题，必填且不可为空白
 * @param kbId 知识库 ID，可选；为空时回退到 {@code default}
 * @param topK 返回引用条数，可选；默认 5，取值范围 1~20
 */
public record AskQuestionCommand(String question, String kbId, Integer topK) {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 20;

    public AskQuestionCommand {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        if (topK != null && (topK < MIN_TOP_K || topK > MAX_TOP_K)) {
            throw new IllegalArgumentException("topK must be between 1 and 20");
        }
    }

    /**
     * 返回规范化后的问题文本。
     *
     * <p>通过去除首尾空白，降低语义检索时的噪声输入。
     */
    public String normalizedQuestion() {
        return question.trim();
    }

    /**
     * 解析知识库 ID，空值回退到默认知识库。
     *
     * @return 有效知识库 ID
     */
    public String resolvedKbId() {
        if (kbId == null || kbId.isBlank()) {
            return "default";
        }
        return kbId.trim();
    }

    /**
     * 解析引用条数，未传时使用默认值。
     *
     * @return 最终用于问答流程的引用条数
     */
    public int resolvedTopK() {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return topK;
    }
}
