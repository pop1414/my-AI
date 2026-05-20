package io.github.spike.myai.ingest.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.spike.myai.ingest.domain.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * RustFS 真实服务 smoke test。
 *
 * <p>该测试默认不进入 Maven 回归。只有显式设置
 * {@code MYAI_RUSTFS_SMOKE_TEST=true} 时才会访问真实 S3 兼容服务，用于 RFS-05
 * 验证 source、artifact、正文读取大小限制和删除清理闭环。
 */
@Tag("rustfs-smoke")
@EnabledIfEnvironmentVariable(named = "MYAI_RUSTFS_SMOKE_TEST", matches = "true")
class S3DocumentStorageSmokeTest {

    private static final String DEFAULT_ENDPOINT = "http://localhost:9000";
    private static final String DEFAULT_BUCKET = "myai-documents";
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_ACCESS_KEY = "admin";
    private static final String DEFAULT_SECRET_KEY = "Admin@123";

    @Test
    @DisplayName("RustFS smoke：source、cleaned.md、过大正文和删除清理应形成闭环")
    void rustfsSmoke_shouldVerifySourceArtifactContentLimitAndDeleteLifecycle() {
        SmokeConfig config = SmokeConfig.fromEnvironment();
        try (S3Client s3Client = createS3Client(config)) {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(config.bucket()).build());
            IngestProperties properties = createProperties(config.bucket());
            S3DocumentSourceStorage sourceStorage = new S3DocumentSourceStorage(s3Client, properties);
            S3DocumentProcessingArtifactStorage artifactStorage =
                    new S3DocumentProcessingArtifactStorage(s3Client, properties);

            DocumentId documentId = new DocumentId("rfs05-smoke-" + UUID.randomUUID());
            String workspaceId = "default";
            String filename = "rfs05-smoke.txt";
            String sourceKey = "source/default/documents/" + documentId.value() + "/versions/1/" + filename;
            String cleanedKey = "artifacts/default/documents/" + documentId.value() + "/versions/1/cleaned.md";
            String largeKey = "artifacts/default/documents/" + documentId.value() + "/versions/1/large.md";

            try {
                byte[] sourceBytes = "RFS05 source".getBytes(StandardCharsets.UTF_8);
                sourceStorage.save(documentId, filename, sourceBytes);

                ResponseBytes<GetObjectResponse> storedSource = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(config.bucket())
                        .key(sourceKey)
                        .build());
                assertThat(storedSource.asByteArray()).isEqualTo(sourceBytes);

                DocumentParseResult parseResult = new DocumentParseResult(
                        "<html><body>RFS05</body></html>",
                        "<p>RFS05</p>",
                        "# RFS05 cleaned",
                        "{\"schema_version\":\"v1\"}");
                artifactStorage.saveVersion(workspaceId, documentId, 1, parseResult);

                DocumentVersionArtifactContent cleaned = artifactStorage
                        .loadVersionArtifact(
                                workspaceId,
                                documentId,
                                1,
                                DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                                1024)
                        .orElseThrow();
                assertThat(cleaned.key()).isEqualTo(cleanedKey);
                assertThat(cleaned.content()).isEqualTo("# RFS05 cleaned");
                assertThat(cleaned.contentLength()).isEqualTo("# RFS05 cleaned".getBytes(StandardCharsets.UTF_8).length);

                assertThat(artifactStorage.loadVersionArtifact(workspaceId, documentId, 1, "missing.md", 1024))
                        .isEmpty();

                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(config.bucket())
                                .key(largeKey)
                                .build(),
                        RequestBody.fromBytes("too-large".getBytes(StandardCharsets.UTF_8)));
                assertThatThrownBy(() -> artifactStorage.loadVersionArtifact(workspaceId, documentId, 1, "large.md", 4))
                        .isInstanceOf(DocumentVersionArtifactTooLargeException.class);
            } finally {
                sourceStorage.deleteByDocumentId(documentId);
                artifactStorage.deleteByDocumentId(workspaceId, documentId);
            }

            assertObjectMissing(s3Client, config.bucket(), sourceKey);
            assertObjectMissing(s3Client, config.bucket(), cleanedKey);
            assertObjectMissing(s3Client, config.bucket(), largeKey);
        }
    }

    private static IngestProperties createProperties(String bucket) {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().getS3().setBucket(bucket);
        properties.getStorage().getArtifacts().setKeepParseResultJson(true);
        return properties;
    }

    private static S3Client createS3Client(SmokeConfig config) {
        return S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.pathStyleAccess())
                        .build())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .apiCallTimeout(Duration.ofSeconds(10))
                        .build())
                .build();
    }

    private static void assertObjectMissing(S3Client s3Client, String bucket, String key) {
        assertThatThrownBy(() -> s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()))
                .isInstanceOfSatisfying(S3Exception.class, exception ->
                        assertThat(isObjectNotFound(exception)).isTrue());
    }

    private static boolean isObjectNotFound(S3Exception exception) {
        return exception instanceof NoSuchKeyException || exception.statusCode() == 404;
    }

    private record SmokeConfig(
            String endpoint,
            String bucket,
            String region,
            String accessKey,
            String secretKey,
            boolean pathStyleAccess) {

        static SmokeConfig fromEnvironment() {
            return new SmokeConfig(
                    environment("INGEST_STORAGE_S3_ENDPOINT", DEFAULT_ENDPOINT),
                    environment("INGEST_STORAGE_S3_BUCKET", DEFAULT_BUCKET),
                    environment("INGEST_STORAGE_S3_REGION", DEFAULT_REGION),
                    environment("INGEST_STORAGE_S3_ACCESS_KEY", DEFAULT_ACCESS_KEY),
                    environment("INGEST_STORAGE_S3_SECRET_KEY", DEFAULT_SECRET_KEY),
                    Boolean.parseBoolean(environment("INGEST_STORAGE_S3_PATH_STYLE_ACCESS", "true")));
        }

        private static String environment(String name, String defaultValue) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }
    }
}
