package io.github.spike.myai.qa.domain.model;

import java.time.Instant;

/**
 * 向量检索命中的分块快照（领域查询模型）。
 *
 * <p>该对象表示检索层返回的最小必要信息，供应用层进行知识库过滤、
 * 引用组装与提示词拼接，不绑定具体向量数据库实现细节。
 *
 * @author spike
 * @since 1.0.0
 * @param documentId 文档资产 ID
 * @param kbId 知识库 ID
 * @param chunkIndex 分块序号
 * @param content 分块正文
 * @param sourceVersionNumber 分块来源的文档版本号；历史向量未写入该字段时可为空
 * @param sourceFilename 分块来源文件名；历史向量未写入该字段时可为空
 * @param sourceUpdatedAt 分块来源版本更新时间；历史向量未写入该字段时可为空
 * @param score 检索置信度；Dense 路径 = cosine similarity，Sparse 路径 = ts_rank，RRF 路径 = 融合分；默认 0.0
 */
public record RetrievedChunk(
        String documentId,
        String kbId,
        int chunkIndex,
        String content,
        Integer sourceVersionNumber,
        String sourceFilename,
        Instant sourceUpdatedAt,
        double score) {

    /**
     * 兼容旧测试与历史调用方的简化构造器。
     *
     * <p>旧向量数据没有写入版本元数据，上层会结合版本事实表决定是否允许使用。
     * score 默认 0.0，后续由检索适配器按路径填充实际值。</p>
     *
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @param chunkIndex 分块序号
     * @param content 分块正文
     */
    public RetrievedChunk(String documentId, String kbId, int chunkIndex, String content) {
        this(documentId, kbId, chunkIndex, content, null, null, null, 0.0);
    }
}
