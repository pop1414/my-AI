package io.github.spike.myai.ingest.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.exception.DocumentSourceContentConflictException;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

class S3DocumentSourceStorageTest {

    @Test
    @DisplayName("首次幂等保存版本源文件时应写入 source prefix 并返回 true")
    void saveVersionIfAbsent_shouldPutObjectAndReturnTrue_whenObjectMissing() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentSourceStorage storage = storage(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFound());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        boolean created = storage.saveVersionIfAbsent(
                new DocumentId("doc-source-s3-1"),
                2,
                "origin.pdf",
                "source".getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertTrue(created);
        assertEquals("myai-documents", requestCaptor.getValue().bucket());
        assertEquals(
                "source/default/documents/doc-source-s3-1/versions/2/origin.pdf",
                requestCaptor.getValue().key());
    }

    @Test
    @DisplayName("同一版本同名源文件内容一致时应返回幂等命中")
    void saveVersionIfAbsent_shouldReturnFalse_whenExistingContentIsSame() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentSourceStorage storage = storage(s3Client);
        byte[] content = "same".getBytes(StandardCharsets.UTF_8);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes(content));

        boolean created = storage.saveVersionIfAbsent(new DocumentId("doc-source-s3-2"), 1, "same.txt", content);

        assertFalse(created);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("同一版本同名源文件内容不一致时应抛出稳定冲突")
    void saveVersionIfAbsent_shouldRejectDifferentContent_whenObjectExists() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentSourceStorage storage = storage(s3Client);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(responseBytes("first".getBytes(StandardCharsets.UTF_8)));

        assertThrows(DocumentSourceContentConflictException.class, () -> storage.saveVersionIfAbsent(
                new DocumentId("doc-source-s3-3"),
                1,
                "same.txt",
                "second".getBytes(StandardCharsets.UTF_8)));

        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("读取指定版本源文件时应按 source key 返回完整字节")
    void loadVersion_shouldReturnBytesFromSourceKey() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentSourceStorage storage = storage(s3Client);
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes(content));

        byte[] loaded = storage.loadVersion(new DocumentId("doc-source-s3-4"), 3, "origin.txt").orElseThrow();

        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(requestCaptor.capture());
        assertArrayEquals(content, loaded);
        assertEquals(
                "source/default/documents/doc-source-s3-4/versions/3/origin.txt",
                requestCaptor.getValue().key());
    }

    @Test
    @DisplayName("源文件缺失时应返回空且不回退本地文件系统")
    void loadVersion_shouldReturnEmpty_whenObjectMissing() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentSourceStorage storage = storage(s3Client);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(notFound());

        assertFalse(storage.loadVersion(new DocumentId("doc-source-s3-5"), 1, "missing.txt").isPresent());
    }

    @Test
    @DisplayName("S3 读取异常不应伪装为源文件缺失")
    void loadVersion_shouldPropagateS3Failure_whenStorageUnavailable() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentSourceStorage storage = storage(s3Client);
        S3Exception unavailable = noSuchBucket();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(unavailable);

        S3Exception exception = assertThrows(
                S3Exception.class,
                () -> storage.loadVersion(new DocumentId("doc-source-s3-unavailable"), 1, "missing.txt"));

        assertEquals(unavailable, exception);
    }

    @Test
    @DisplayName("删除文档源文件时应分页清理 source prefix 且不触碰 artifacts prefix")
    void deleteByDocumentId_shouldDeleteSourcePrefixWithPagination() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentSourceStorage storage = storage(s3Client);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder()
                                .key("source/default/documents/doc-source-s3-delete/versions/1/a.pdf")
                                .build())
                        .isTruncated(true)
                        .nextContinuationToken("next-page")
                        .build())
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder()
                                .key("source/default/documents/doc-source-s3-delete/versions/2/a.pdf")
                                .build())
                        .isTruncated(false)
                        .build());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        storage.deleteByDocumentId(new DocumentId("doc-source-s3-delete"));

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, org.mockito.Mockito.times(2)).listObjectsV2(listCaptor.capture());
        verify(s3Client, org.mockito.Mockito.times(2)).deleteObjects(deleteCaptor.capture());
        assertEquals(
                "source/default/documents/doc-source-s3-delete/",
                listCaptor.getAllValues().get(0).prefix());
        assertEquals("next-page", listCaptor.getAllValues().get(1).continuationToken());
        assertTrue(deleteCaptor.getAllValues().stream()
                .flatMap(request -> request.delete().objects().stream())
                .allMatch(identifier -> identifier.key().startsWith("source/default/documents/doc-source-s3-delete/")));
    }

    private static S3DocumentSourceStorage storage(S3Client s3Client) {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().getS3().setBucket("myai-documents");
        return new S3DocumentSourceStorage(s3Client, properties);
    }

    private static ResponseBytes<GetObjectResponse> responseBytes(byte[] content) {
        return ResponseBytes.fromByteArray(
                GetObjectResponse.builder().contentLength((long) content.length).build(),
                content);
    }

    private static S3Exception notFound() {
        return NoSuchKeyException.builder()
                .statusCode(404)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build())
                .build();
    }

    private static S3Exception noSuchBucket() {
        return NoSuchBucketException.builder()
                .statusCode(404)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build())
                .build();
    }
}
