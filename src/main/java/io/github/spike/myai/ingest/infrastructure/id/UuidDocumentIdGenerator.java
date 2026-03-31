package io.github.spike.myai.ingest.infrastructure.id;

import java.util.UUID;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentIdGenerator;
import org.springframework.stereotype.Component;

/**
 * 基于 UUID 的文档 ID 生成器（Infrastructure Adapter）。
 *
 * <p>这是 {@link DocumentIdGenerator} 的基础设施实现：
 * <ul>
 *     <li>使用 JDK UUID 生成全局唯一标识。</li>
 *     <li>返回领域值对象 {@link DocumentId}，由其构造函数保证合法性。</li>
 * </ul>
 */
@Component
public class UuidDocumentIdGenerator implements DocumentIdGenerator {

    /**
     * 生成文档 ID。
     *
     * @return 新生成的文档 ID
     */
    @Override
    public DocumentId nextId() {
        return new DocumentId(UUID.randomUUID().toString());
    }
}
