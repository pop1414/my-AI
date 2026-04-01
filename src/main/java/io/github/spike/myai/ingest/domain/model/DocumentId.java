package io.github.spike.myai.ingest.domain.model;

/**
 * 文档标识（Value Object）。
 *
 * <p>领域意义：
 * <ul>
 *     <li>用于唯一标识一个“文档资产”。</li>
 *     <li>文档资产语义：存在于某个知识库中的某份文件内容。</li>
 *     <li>当同一知识库下文件内容未变（fileHash 不变）时，documentId 保持不变（包括 reprocess）。</li>
 *     <li>通过值对象封装，避免在各层直接传递“裸 String”。</li>
 *     <li>在构造时进行不变量校验，保证领域对象始终有效。</li>
 * </ul>
 */
public record DocumentId(String value) {

    /**
     * 紧凑构造函数：校验领域不变量。
     */
    public DocumentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
    }
}
