package io.github.spike.myai.knowledge.application.usecase;

import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import java.util.List;

/**
 * 查询知识库列表用例接口。
 *
 * <p>该接口代表应用层对外暴露的“查询知识库统计”用例，
 * 接口层（如 REST Controller）通过该契约触发业务流程，
 * 以避免直接依赖具体应用服务实现。
 */
public interface ListKnowledgeBasesUseCase {

    /**
     * 执行查询所有知识库统计的用例。
     *
     * <p>当前版本返回的统计值来自已索引文档聚合结果，
     * 并以应用层结果对象形式输出，便于接口层进行二次映射。
     *
     * @return 知识库结果列表
     */
    List<KnowledgeBaseResult> handle();
}
