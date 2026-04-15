package io.github.spike.myai.qa.interfaces.rest.dto;

/**
 * 问答接口请求体（REST DTO）。
 *
 * <p>该对象仅描述外部 API 入参，不承载业务逻辑。
 * 字段的规范化与最终校验由应用层命令对象负责。
 *
 * @param question 用户问题，必填；空白字符串会在应用层判定为非法
 * @param kbId 目标知识库 ID，可选；为空时回退到默认知识库
 * @param topK 希望返回的引用条数，可选；默认 5，合法范围 1~20
 */
public record AskRequest(String question, String kbId, Integer topK) {
}
