package io.github.spike.myai.ingest.infrastructure.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * S3 兼容文档资产存储基础配置。
 *
 * <p>仅在 {@code myai.ingest.storage.type=s3} 时创建 S3 client。该配置不实现 source
 * 或 artifact 的具体读写逻辑，后续 S3 storage adapter 通过注入 {@link S3Client}
 * 使用同一连接基线。
 *
 * @author Spike
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "myai.ingest.storage", name = "type", havingValue = "s3")
public class S3StorageConfiguration {

    /**
     * 创建 S3 client。
     *
     * <p>启动阶段会校验 S3 模式必填配置，避免运行到首次上传或处理时才暴露配置缺失。
     *
     * @param ingestProperties ingest 管道配置属性
     * @return S3 client
     */
    @Bean(destroyMethod = "close")
    public S3Client documentAssetS3Client(IngestProperties ingestProperties) {
        IngestProperties.S3 s3 = ingestProperties.getStorage().getS3();
        validateRequired(s3);
        return S3Client.builder()
                .endpointOverride(URI.create(s3.getEndpoint().trim()))
                .region(Region.of(s3.getRegion().trim()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKey().trim(), s3.getSecretKey().trim())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.isPathStyleAccess())
                        .build())
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
    }

    private static void validateRequired(IngestProperties.S3 s3) {
        requireText(s3.getEndpoint(), "myai.ingest.storage.s3.endpoint");
        requireText(s3.getBucket(), "myai.ingest.storage.s3.bucket");
        requireText(s3.getRegion(), "myai.ingest.storage.s3.region");
        requireText(s3.getAccessKey(), "myai.ingest.storage.s3.access-key");
        requireText(s3.getSecretKey(), "myai.ingest.storage.s3.secret-key");
    }

    private static void requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when myai.ingest.storage.type=s3");
        }
    }
}
