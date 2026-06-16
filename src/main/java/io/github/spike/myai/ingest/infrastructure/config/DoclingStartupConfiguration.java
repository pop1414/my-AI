package io.github.spike.myai.ingest.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.docling.serve.api.DoclingServeApi;

/**
 * Docling Serve 启动校验配置。
 *
 * <p>注册 {@link DoclingStartupVerifier}，在应用启动时校验 Docling Serve 连通性。
 * 仅在 {@link DoclingServeApi} Bean 存在时生效（即 Arconia Docling Starter 已引入）。
 *
 * @author spike
 * @since 1.0.0
 */
@Configuration
@ConditionalOnBean(DoclingServeApi.class)
public class DoclingStartupConfiguration {

    @Bean
    public DoclingStartupVerifier doclingStartupVerifier(DoclingServeApi doclingServeApi) {
        return new DoclingStartupVerifier(doclingServeApi);
    }
}
