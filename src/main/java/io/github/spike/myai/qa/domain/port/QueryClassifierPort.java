package io.github.spike.myai.qa.domain.port;

import io.github.spike.myai.qa.domain.model.QueryType;

/**
 * 查询分类端口（Domain Port）。
 *
 * <p>定义问答流程中对用户查询进行意图分类的能力。
 * 应用层根据分类结果决定检索策略（如 CHITCHAT 跳过检索直接调用 LLM）。
 *
 * <p>实现方应保证分类结果的确定性：相同输入始终返回相同类型。
 *
 * @author spike
 * @since 1.0.0
 */
public interface QueryClassifierPort {

    /**
     * 对用户查询进行意图分类。
     *
     * @param question 用户输入的问题文本
     * @return 分类后的查询类型
     */
    QueryType classify(String question);
}
