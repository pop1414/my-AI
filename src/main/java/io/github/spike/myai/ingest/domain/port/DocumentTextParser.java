package io.github.spike.myai.ingest.domain.port;

/**
 * 文档文本解析端口。
 */
public interface DocumentTextParser {

    /**
     * 将原始文件字节解析为纯文本。
     *
     * @param filename 原始文件名
     * @param content 文件字节
     * @return 解析后的纯文本
     */
    String parse(String filename, byte[] content);
}

