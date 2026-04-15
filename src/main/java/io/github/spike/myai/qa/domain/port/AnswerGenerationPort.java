package io.github.spike.myai.qa.domain.port;

/**
 * 问答回答生成端口（Domain Port）。
 *
 * <p>抽象“根据提示词生成回答”的能力，隔离具体大模型 SDK 与配置差异。
 */
public interface AnswerGenerationPort {

    /**
     * 根据拼装后的提示词生成回答。
     *
     * @param prompt 由应用层构造的完整提示词
     * @return 回答文本；实现方可返回空值，调用方需自行兜底
     */
    String generateAnswer(String prompt);
}
