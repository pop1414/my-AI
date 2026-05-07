package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.query.ListDocumentsQuery;
import io.github.spike.myai.ingest.application.result.DocumentListPageResult;

/**
 * 文档列表查询用例（Use Case / Application Port）。
 *
 * <p>该接口是六边形架构中的应用层用例入口，
 * 定义按筛选条件分页查询文档列表的契约。
 * 控制器通过调用该接口完成文档列表的读取操作。
 *
 * @author Spike
 * @since 1.0.0
 */
public interface ListDocumentsUseCase {

    /**
     * 根据筛选条件查询分页文档列表。
     *
     * <p>Query 对象已在构造阶段完成参数校验与规整化，
     * 实现类可直接使用其中的规范化字段进行数据查询。
     *
     * @param query 文档列表查询条件（含筛选与分页参数）
     * @return 分页查询结果（含 items / total / limit / offset）
     */
    DocumentListPageResult handle(ListDocumentsQuery query);
}
