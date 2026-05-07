package io.github.spike.myai.knowledge.domain.port;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseSummary;
import java.util.List;
import java.util.Optional;

/**
 * 知识库主数据仓储端口（Domain Port / Repository Interface）。
 *
 * <p>该接口是六边形架构中的<b>领域端口</b>，定义领域层所需的持久化能力契约。
 * 具体实现（如 {@code JdbcKnowledgeBaseRepository}）位于基础设施层，
 * 通过依赖倒置实现领域层与基础设施层的解耦。
 *
 * <h3>职责划分</h3>
 * <ul>
 *   <li>{@link #save} —— 保存聚合根（新增或更新，由实现决定具体策略）；</li>
 *   <li>{@link #findByKbId} —— 按业务键查询单个聚合根，不存在时返回
 *       {@link Optional#empty()}；</li>
 *   <li>{@link #listKnowledgeBases} —— 查询全量知识库摘要视图（读模型），
 *       包含聚合统计字段。</li>
 * </ul>
 *
 * <p>遵循阿里巴巴 Java 开发手册中"接口类的方法不加 {@code public} 修饰符"的规范。
 *
 * @author Spike
 * @since 1.0.0
 */
public interface KnowledgeBaseRepository {

    /**
     * 保存知识库聚合根。
     *
     * <p>语义：若该知识库的业务键（{@code kbId}）已存在则更新，否则新增。
     * 具体采用 INSERT ... ON CONFLICT 还是先查后写，由实现类决定。
     *
     * @param knowledgeBase 待持久化的知识库聚合根（不可为 {@code null}）
     */
    void save(KnowledgeBase knowledgeBase);

    /**
     * 根据业务键查询知识库聚合根。
     *
     * <p>用于更新操作前的实体查找，或详情查询。
     *
     * @param kbId 知识库业务键（不可为 {@code null}）
     * @return 包含知识库聚合根的 {@link Optional}，不存在时为 {@link Optional#empty()}
     */
    Optional<KnowledgeBase> findByKbId(String kbId);

    /**
     * 查询全量知识库列表（含聚合统计）。
     *
     * <p>返回的是读模型 {@link KnowledgeBaseSummary}，而非完整聚合根。
     * 视图项包含 {@code indexedDocumentCount} 聚合字段，
     * 由仓库实现通过关联查询或缓存计算得出。
     *
     * @return 知识库摘要视图列表（可能为空列表，不会返回 {@code null}）
     */
    List<KnowledgeBaseSummary> listKnowledgeBases();
}
