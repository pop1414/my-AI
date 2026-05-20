package io.github.spike.myai.ingest.domain.exception;

/**
 * 文档源文件内容冲突异常。
 *
 * <p>当同一文档版本的源文件已经存在，但新写入内容与现有内容不一致时抛出。
 * 上层通过该稳定异常类型区分并发版本冲突与普通基础设施失败。
 */
public class DocumentSourceContentConflictException extends RuntimeException {

    /**
     * 创建源文件内容冲突异常。
     */
    public DocumentSourceContentConflictException() {
        super("document source content conflict");
    }
}
