package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.util.Optional;

/**
 * 文档源文件存储端口。
 *
 * <p>职责：
 * <ul>
 *     <li>在上传受理后持久化原始文件内容。</li>
 *     <li>在处理执行阶段按文档资产 ID 读取原始文件内容。</li>
 * </ul>
 */
public interface DocumentSourceStorage {

    /**
     * 保存文档源文件。
     *
     * @param documentId 文档资产 ID
     * @param filename 原始文件名
     * @param content 原始文件字节
     */
    void save(DocumentId documentId, String filename, byte[] content);

    /**
     * 读取文档源文件。
     *
     * @param documentId 文档资产 ID
     * @param filename 原始文件名
     * @return 原始文件字节，未命中时返回空
     */
    Optional<byte[]> load(DocumentId documentId, String filename);
}

