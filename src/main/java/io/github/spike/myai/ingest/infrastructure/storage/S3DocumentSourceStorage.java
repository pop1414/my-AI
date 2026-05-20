package io.github.spike.myai.ingest.infrastructure.storage;

import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.exception.DocumentSourceContentConflictException;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.util.Arrays;
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
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 兼容对象存储文档源文件存储实现。
 *
 * <p>该实现只负责 {@link DocumentSourceStorage} 端口的 source prefix 读写，不处理
 * artifacts prefix。对象 key 由 {@link DocumentStorageKeyResolver} 统一生成，避免
 * application 或 domain 层感知对象存储路径细节。
 *
 * @author Spike
 * @since 1.0.0
 */
@Component
@ConditionalOnProperty(prefix = "myai.ingest.storage", name = "type", havingValue = "s3")
public class S3DocumentSourceStorage implements DocumentSourceStorage {

    private final S3Client s3Client;
    private final String bucket;
    private final DocumentStorageKeyResolver keyResolver = new DocumentStorageKeyResolver();

    /**
     * 创建 S3 source storage adapter。
     *
     * @param s3Client S3 client
     * @param ingestProperties ingest 管道配置属性
     */
    public S3DocumentSourceStorage(S3Client s3Client, IngestProperties ingestProperties) {
        this.s3Client = s3Client;
        this.bucket = ingestProperties.getStorage().getS3().getBucket();
    }

    @Override
    public void save(DocumentId documentId, String filename, byte[] content) {
        saveVersion(documentId, 1, filename, content);
    }

    @Override
    public void saveVersion(DocumentId documentId, int versionNumber, String filename, byte[] content) {
        saveVersionIfAbsent(documentId, versionNumber, filename, content);
    }

    /**
     * 幂等保存指定版本源文件。
     *
     * <p>首期不依赖对象存储条件写入能力。对象已存在时读取并比对内容：一致返回
     * {@code false}，不一致抛出稳定冲突异常。
     */
    @Override
    public boolean saveVersionIfAbsent(DocumentId documentId, int versionNumber, String filename, byte[] content) {
        String key = resolveSourceKey(documentId, versionNumber, filename);
        if (objectExists(key)) {
            byte[] existingContent = loadObject(key)
                    .orElseThrow(() -> new IllegalStateException("source object disappeared during idempotency check"));
            if (!Arrays.equals(existingContent, content)) {
                throw new DocumentSourceContentConflictException();
            }
            return false;
        }
        putObject(key, content);
        return true;
    }

    @Override
    public Optional<byte[]> load(DocumentId documentId, String filename) {
        return loadVersion(documentId, 1, filename);
    }

    @Override
    public Optional<byte[]> loadVersion(DocumentId documentId, int versionNumber, String filename) {
        return loadObject(resolveSourceKey(documentId, versionNumber, filename));
    }

    /**
     * 删除指定文档在 source prefix 下的全部对象。
     *
     * <p>分页列出并批量删除当前可见对象，只清理 source prefix，不触碰 artifacts prefix。
     */
    @Override
    public void deleteByDocumentId(DocumentId documentId) {
        String prefix = String.join(
                "/",
                DocumentStorageKeyResolver.SOURCE_PREFIX,
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
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

    private String resolveSourceKey(DocumentId documentId, int versionNumber, String filename) {
        return keyResolver.resolveSourceKey(
                WorkspaceConstants.DEFAULT_WORKSPACE_ID,
                documentId,
                versionNumber,
                filename);
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (S3Exception ex) {
            if (isObjectNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private Optional<byte[]> loadObject(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return Optional.of(response.asByteArray());
        } catch (S3Exception ex) {
            if (isObjectNotFound(ex)) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private void putObject(String key, byte[] content) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                RequestBody.fromBytes(content));
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
