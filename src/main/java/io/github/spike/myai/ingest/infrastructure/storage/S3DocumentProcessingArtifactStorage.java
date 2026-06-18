package io.github.spike.myai.ingest.infrastructure.storage;

import io.github.spike.myai.ingest.domain.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 兼容对象存储文档处理产物存储实现。
 *
 * <p>该实现只负责 artifacts prefix 读写，不读取 source prefix，也不触发重新解析。
 * {@code cleaned.md} 是版本级正文事实，始终强制写入。
 *
 * @author Spike
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "myai.ingest.storage", name = "type", havingValue = "s3")
public class S3DocumentProcessingArtifactStorage implements DocumentProcessingArtifactStorage {

    static final String READER_MARKDOWN_FILENAME = READER_MARKDOWN_ARTIFACT_NAME;
    static final String CLEANED_MARKDOWN_FILENAME = CLEANED_MARKDOWN_ARTIFACT_NAME;
    static final String PARSE_RESULT_FILENAME = "parse-result.json";

    private final S3Client s3Client;
    private final String bucket;
    private final DocumentStorageKeyResolver keyResolver = new DocumentStorageKeyResolver();
    private final boolean keepParseResultJson;

    /**
     * 创建 S3 artifact storage adapter。
     *
     * @param s3Client S3 client
     * @param ingestProperties ingest 管道配置属性
     */
    public S3DocumentProcessingArtifactStorage(S3Client s3Client, IngestProperties ingestProperties) {
        this.s3Client = s3Client;
        this.bucket = ingestProperties.getStorage().getS3().getBucket();
        this.keepParseResultJson = ingestProperties.getStorage().getArtifacts().isKeepParseResultJson();
    }

    @Override
    public void saveVersion(String workspaceId, DocumentId documentId, int versionNumber, DocumentParseResult parseResult) {
        putText(resolveArtifactKey(workspaceId, documentId, versionNumber, READER_MARKDOWN_FILENAME), parseResult.readerMarkdown());
        putText(resolveArtifactKey(workspaceId, documentId, versionNumber, CLEANED_MARKDOWN_FILENAME), parseResult.cleanedMarkdown());
        if (keepParseResultJson
                && parseResult.processingMetadata() != null
                && !parseResult.processingMetadata().isBlank()) {
            putText(resolveArtifactKey(workspaceId, documentId, versionNumber, PARSE_RESULT_FILENAME), parseResult.processingMetadata());
        }
    }

    @Override
    public Optional<DocumentVersionArtifactContent> loadVersionArtifact(
            String workspaceId,
            DocumentId documentId,
            int versionNumber,
            String artifactName,
            long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        String key = resolveArtifactKey(workspaceId, documentId, versionNumber, artifactName);
        HeadObjectResponse headObjectResponse;
        try {
            headObjectResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception ex) {
            if (isObjectNotFound(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
        long contentLength = headObjectResponse.contentLength();
        if (contentLength > maxBytes) {
            throw new DocumentVersionArtifactTooLargeException(contentLength, maxBytes);
        }
        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
        return Optional.of(new DocumentVersionArtifactContent(
                key,
                responseBytes.asString(StandardCharsets.UTF_8),
                contentLength));
    }

    @Override
    public void deleteByDocumentId(String workspaceId, DocumentId documentId) {
        String prefix = String.join(
                "/",
                DocumentStorageKeyResolver.ARTIFACTS_PREFIX,
                workspaceId,
                "documents",
                documentId.value()) + "/";
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }
            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
            deleteObjects(response.contents());
            continuationToken = Boolean.TRUE.equals(response.isTruncated())
                    ? response.nextContinuationToken()
                    : null;
        } while (continuationToken != null);
    }

    private String resolveArtifactKey(String workspaceId, DocumentId documentId, int versionNumber, String artifactName) {
        return keyResolver.resolveArtifactKey(workspaceId, documentId, versionNumber, artifactName);
    }

    private void putText(String key, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    private void deleteObjects(List<S3Object> objects) {
        if (objects == null || objects.isEmpty()) {
            return;
        }
        List<ObjectIdentifier> identifiers = objects.stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .toList();
        s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(identifiers).build())
                .build());
    }

    private static boolean isObjectNotFound(S3Exception ex) {
        AwsErrorDetails details = ex.awsErrorDetails();
        String errorCode = details == null ? null : details.errorCode();
        if ("NoSuchBucket".equals(errorCode)) {
            return false;
        }
        return "NoSuchKey".equals(errorCode) || "NotFound".equals(errorCode) || ex.statusCode() == 404;
    }
}
