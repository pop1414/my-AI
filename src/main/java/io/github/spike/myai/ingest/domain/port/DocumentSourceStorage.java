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

    /** 版本源文件路径已存在但内容不一致时使用的稳定异常消息。 */
    String VERSION_SOURCE_CONTENT_CONFLICT_MESSAGE = "version source file content conflict";

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
     * 幂等保存指定版本的源文件，并返回本次调用是否创建了新文件。
     *
     * <p>默认实现保持旧存储契约：调用 {@link #saveVersion(DocumentId, int, String, byte[])}
     * 后认为文件由本次调用创建。具备版本化路径能力的实现应覆盖该方法，
     * 在文件已存在且内容一致时返回 {@code false}，内容不一致时抛出异常，
     * 避免并发候选版本污染最终版本文件。
     *
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param filename 原始文件名
     * @param content 原始文件字节
     * @return 本次调用是否创建了新文件
     */
    default boolean saveVersionIfAbsent(DocumentId documentId, int versionNumber, String filename, byte[] content) {
        saveVersion(documentId, versionNumber, filename, content);
        return true;
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
