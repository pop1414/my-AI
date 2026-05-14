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
     * 保存指定版本的源文件。
     *
     * <p>默认回退到旧存储契约，便于非版本化实现保持兼容。
     *
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param filename 原始文件名
     * @param content 原始文件字节
     */
    default void saveVersion(DocumentId documentId, int versionNumber, String filename, byte[] content) {
        save(documentId, filename, content);
    }

    /**
     * 读取文档源文件。
     *
     * @param documentId 文档资产 ID
     * @param filename 原始文件名
     * @return 原始文件字节，未命中时返回空
     */
    Optional<byte[]> load(DocumentId documentId, String filename);

    /**
     * 读取指定版本的源文件。
     *
     * <p>默认回退到旧读取契约，支持历史 version 1 数据继续可处理。
     *
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param filename 原始文件名
     * @return 原始文件字节，未命中时返回空
     */
    default Optional<byte[]> loadVersion(DocumentId documentId, int versionNumber, String filename) {
        return load(documentId, filename);
    }

    /**
     * 删除文档资产对应的全部源文件。
     *
     * @param documentId 文档资产 ID
     */
    void deleteByDocumentId(DocumentId documentId);
}
