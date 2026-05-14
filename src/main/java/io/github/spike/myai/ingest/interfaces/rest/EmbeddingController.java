package io.github.spike.myai.ingest.interfaces.rest;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 向量嵌入接口控制器（Interface Layer）。
 *
 * <p>提供嵌入模型的 HTTP 端点，用于：
 * <ul>
 *   <li>测试嵌入模型是否可用（健康检查辅助端点）；</li>
 *   <li>调试阶段验证单条文本的向量化结果。</li>
 * </ul>
 *
 * <p>注意：该控制器为开发/调试用途，生产环境的批量向量化由
 * {@link io.github.spike.myai.ingest.infrastructure.vector.PgVectorDocumentVectorIndexer}
 * 直接调用嵌入模型完成，不经过此 HTTP 端点。
 *
 * @author DDY
 * @since 1.0.0
 */
@RestController
public class EmbeddingController {

    /** Spring AI 嵌入模型组件，用于将文本转换为向量表示 */
    private final EmbeddingModel embeddingModel;

    /**
     * 构造器注入嵌入模型。
     *
     * @param embeddingModel Spring AI 自动配置的嵌入模型实例
     */
    @Autowired
    public EmbeddingController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 文本向量化接口（调试端点）。
     *
     * <p>接收单条文本消息，调用嵌入模型生成对应的向量表示并返回。
     * 默认消息为 "Tell me a joke"，用于快速验证嵌入服务连通性。
     *
     * @param message 待向量化的文本，默认为 "Tell me a joke"
     * @return 包含嵌入向量的 Map，key 为 "embedding"
     */
    @GetMapping("/ai/embedding")
    public Map embed(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        // 调用 Spring AI 嵌入模型，将单条文本转换为向量
        EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of(message));
        // 封装为 Map 返回，便于客户端解析
        return Map.of("embedding", embeddingResponse);
    }
}
