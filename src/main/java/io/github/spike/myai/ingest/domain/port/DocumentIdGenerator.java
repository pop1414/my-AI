package io.github.spike.myai.ingest.domain.port;

import io.github.spike.myai.ingest.domain.model.DocumentId;

/**
 * 文档 ID 生成端口（Domain Port）。
 *
 * <p>该接口定义了领域层需要的能力：“生成一个合法的 DocumentId”。
 * 领域层只关心能力，不关心实现方式（UUID、雪花算法、数据库序列等）。
 */
public interface DocumentIdGenerator {

    /**
     * 生成下一个文档 ID。
     *
     * @return 合法且非空的文档 ID
     */
    DocumentId nextId();
}
