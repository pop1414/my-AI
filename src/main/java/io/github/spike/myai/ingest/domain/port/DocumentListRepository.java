package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentListFilter;
import io.github.spike.myai.ingest.domain.model.DocumentListPage;

/**
 * 文档列表查询仓储端口（Domain Port / Read-Only Repository）。
 *
 * <p>该接口是六边形架构中的<b>领域端口</b>，专门服务于文档列表的<b>读场景</b>。
 * 与命令型仓储（{@code DocumentRepository}）分离，遵循 CQRS 原则：
 * <ul>
 *   <li>该端口仅承担分页查询，不涉及文档聚合的状态流转与命令型持久化；</li>
 *   <li>返回的是读模型 {@link DocumentListPage}，而非完整的 {@code Document} 聚合根；</li>
 *   <li>仓储实现可直接通过 SQL 投影获取所需字段，无需加载聚合根。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
public interface DocumentListRepository {

    /**
     * 根据筛选条件查询文档分页列表。
     *
     * <p>{@link DocumentListFilter} 中的字段为 {@code null} 时表示该维度不过滤，
     * 由仓储实现负责动态构建 WHERE 子句。
     *
     * @param filter 筛选与分页条件（不可为 {@code null}）
     * @return 分页查询结果（含 items / total / limit / offset）
     */
    DocumentListPage findPage(DocumentListFilter filter);
}
