package io.github.spike.myai.ingest.application.command;

/**
 * 上传新版本命令（Application Command）。
 *
 * <p>该命令封装了从 REST 接口层传入的上传新版本所需全部参数。
 * 紧凑构造器中执行输入防御性校验，确保命令对象一旦创建成功即处于有效状态。
 *
 * <h3>校验规则</h3>
 * <ul>
 *   <li>{@code documentId / filename / fileHash}：不能为空或空白；</li>
 *   <li>{@code fileSize}：不能为负数；</li>
 *   <li>{@code expectedLatestVersionNumber}：必须为正整数；</li>
 *   <li>{@code sourceContent}：不能为 null 且不能为空数组。</li>
 * </ul>
 *
 * <p>{@code sourceContent} 在构造时执行防御性拷贝（{@code clone()}），
 * 防止外部修改命令内部的字节数组。
 *
 * @param documentId                   目标 document 资产 ID
 * @param filename                     来源文件名
 * @param fileSize                     文件大小（字节）
 * @param fileHash                     文件内容 SHA-256 十六进制小写
 * @param expectedLatestVersionNumber  用户页面看到的最新版本号（用于乐观锁校验）
 * @param sourceContent                源文件原始字节（防御性拷贝）
 */
public record UploadNewDocumentVersionCommand(
        String documentId,
        String filename,
        long fileSize,
        String fileHash,
        int expectedLatestVersionNumber,
        byte[] sourceContent) {

    /** 紧凑构造器：在对象创建时立即执行输入校验 */
    public UploadNewDocumentVersionCommand {
        // 文本字段非空校验：trim 后不能为空
        documentId = requireText(documentId, "documentId is required");
        filename = requireText(filename, "filename is required");
        fileHash = requireText(fileHash, "fileHash is required");
        // 文件大小不能为负数
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        // 期望版本号必须为正整数（版本号从 1 开始）
        if (expectedLatestVersionNumber < 1) {
            throw new IllegalArgumentException("expectedLatestVersionNumber must be positive");
        }
        // sourceContent 非空校验
        if (sourceContent == null) {
            throw new IllegalArgumentException("sourceContent is required");
        }
        if (sourceContent.length == 0) {
            throw new IllegalArgumentException("sourceContent must not be empty");
        }
        // 防御性拷贝：防止外部持有引用修改命令内部状态
        sourceContent = sourceContent.clone();
    }

    /**
     * 返回去除前后空白的 documentId。
     *
     * @return 规范化后的文档 ID
     */
    public String normalizedDocumentId() {
        return documentId.trim();
    }

    /**
     * 返回源文件内容的防御性拷贝。
     *
     * <p>每次调用都返回新的字节数组，防止调用方修改内部数据。
     *
     * @return 源文件字节数组的副本
     */
    @Override
    public byte[] sourceContent() {
        // 防御性拷贝：每次返回新的数组实例
        return sourceContent.clone();
    }

    /**
     * 校验文本字段非空，并返回 trim 后的值。
     *
     * @param value   待校验的文本值
     * @param message 校验失败时的异常消息
     * @return trim 后的文本值
     * @throws IllegalArgumentException 当 value 为 null 或 trim 后为空时
     */
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
