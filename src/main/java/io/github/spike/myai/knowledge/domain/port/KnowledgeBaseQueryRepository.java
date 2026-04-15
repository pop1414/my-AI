package io.github.spike.myai.knowledge.domain.port;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseDocumentCount;
import java.util.List;

/**
 * 知识库查询仓储端口（Domain Port）。
 *
 * <p>该接口定义领域层对“知识库统计查询能力”的抽象，
 * 领域与应用层仅依赖此契约，不直接依赖 JDBC、ORM 或具体数据库实现。
 * 通过端口/适配器模式，可以在不影响上层业务代码的前提下替换持久化实现。
 */
public interface KnowledgeBaseQueryRepository {

    /**
     * 查询所有知识库的已索引文档统计。
     *
     * <p>实现方需保证统计口径一致（仅统计 INDEXED 状态文档），
     * 并按每个知识库输出一条聚合结果。
     *
     * @return 知识库统计列表，每项包含知识库标识及对应已索引文档数量
     */
    List<KnowledgeBaseDocumentCount> listIndexedKnowledgeBases();
}
