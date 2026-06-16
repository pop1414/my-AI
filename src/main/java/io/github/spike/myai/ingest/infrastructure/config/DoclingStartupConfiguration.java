package io.github.spike.myai.ingest.infrastructure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.docling.serve.api.DoclingServeApi;

/**
 * Docling Serve 启动校验配置。
 *
 * <p>注册 {@link DoclingStartupVerifier}，在应用启动时校验 Docling Serve 连通性。
 * 通过 {@link ObjectProvider} 延迟注入 {@link DoclingServeApi}，避免
 * {@code @ConditionalOnBean} 在组件扫描配置类上的处理顺序问题。
 *
 * @author spike
 * @since 1.0.0
 */
@Configuration
public class DoclingStartupConfiguration {

    @Bean
    public DoclingStartupVerifier doclingStartupVerifier(ObjectProvider<DoclingServeApi> doclingServeApiProvider) {
        return new DoclingStartupVerifier(doclingServeApiProvider.getObject());
    }
}
