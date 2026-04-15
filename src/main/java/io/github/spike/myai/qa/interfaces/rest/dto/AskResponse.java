package io.github.spike.myai.qa.interfaces.rest.dto;

import java.util.List;

/**
 * 问答接口响应体（REST DTO）。
 *
 * <p>返回值由两部分组成：
 * <ul>
 *   <li>{@code answer}：模型最终回答（可能为兜底文案）；</li>
 *   <li>{@code references}：回答所依据的引用片段列表。</li>
 * </ul>
 *
 * @param answer 回答文本
 * @param references 引用片段集合，顺序与应用层检索命中顺序一致
 */
public record AskResponse(String answer, List<AskReferenceResponse> references) {
}
