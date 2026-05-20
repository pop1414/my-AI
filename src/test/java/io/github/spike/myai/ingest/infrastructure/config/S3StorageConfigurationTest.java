package io.github.spike.myai.ingest.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.infrastructure.storage.LocalDocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.infrastructure.storage.LocalDocumentSourceStorage;
import io.github.spike.myai.ingest.infrastructure.storage.S3DocumentSourceStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;

class S3StorageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(StorageModeTestConfiguration.class);

    @Test
    @DisplayName("默认存储模式应为 local，并只装配本地存储实现")
    void defaultStorageMode_shouldUseLocalStorage() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IngestProperties.class);
            assertThat(context.getBean(IngestProperties.class).getStorage().getType())
                    .isEqualTo(IngestProperties.StorageType.LOCAL);
            assertThat(context).hasSingleBean(DocumentSourceStorage.class);
            assertThat(context).hasSingleBean(DocumentProcessingArtifactStorage.class);
            assertThat(context).hasSingleBean(LocalDocumentSourceStorage.class);
            assertThat(context).hasSingleBean(LocalDocumentProcessingArtifactStorage.class);
            assertThat(context).doesNotHaveBean(S3Client.class);
        });
    }

    @Test
    @DisplayName("s3 存储模式应创建 S3Client，并关闭本地存储实现")
    void s3StorageMode_shouldCreateS3ClientAndUseS3SourceStorage() {
        contextRunner
                .withPropertyValues(
                        "myai.ingest.storage.type=s3",
                        "myai.ingest.storage.s3.endpoint=http://localhost:9000",
                        "myai.ingest.storage.s3.bucket=myai-documents",
                        "myai.ingest.storage.s3.region=us-east-1",
                        "myai.ingest.storage.s3.access-key=admin",
                        "myai.ingest.storage.s3.secret-key=Admin@123",
                        "myai.ingest.storage.s3.path-style-access=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IngestProperties.class).getStorage().getType())
                            .isEqualTo(IngestProperties.StorageType.S3);
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(DocumentSourceStorage.class);
                    assertThat(context).hasSingleBean(S3DocumentSourceStorage.class);
                    assertThat(context).doesNotHaveBean(DocumentProcessingArtifactStorage.class);
                    assertThat(context).doesNotHaveBean(LocalDocumentSourceStorage.class);
                    assertThat(context).doesNotHaveBean(LocalDocumentProcessingArtifactStorage.class);
                });
    }

    @Test
    @DisplayName("s3 存储模式缺少必填配置时应在启动阶段失败")
    void s3StorageMode_shouldFailFast_whenRequiredConfigurationMissing() {
        contextRunner
                .withPropertyValues(
                        "myai.ingest.storage.type=s3",
                        "myai.ingest.storage.s3.bucket=myai-documents",
                        "myai.ingest.storage.s3.region=us-east-1",
                        "myai.ingest.storage.s3.access-key=admin",
                        "myai.ingest.storage.s3.secret-key=Admin@123")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("myai.ingest.storage.s3.endpoint");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IngestProperties.class)
    @Import({
            S3StorageConfiguration.class,
            LocalDocumentSourceStorage.class,
            LocalDocumentProcessingArtifactStorage.class,
            S3DocumentSourceStorage.class
    })
    static class StorageModeTestConfiguration {
    }
}
