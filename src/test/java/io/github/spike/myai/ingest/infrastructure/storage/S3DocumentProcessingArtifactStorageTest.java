package io.github.spike.myai.ingest.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.domain.exception.DocumentVersionArtifactTooLargeException;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentParseResult;
import io.github.spike.myai.ingest.domain.model.DocumentVersionArtifactContent;
import io.github.spike.myai.ingest.domain.port.DocumentProcessingArtifactStorage;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

class S3DocumentProcessingArtifactStorageTest {

    @Test
    @DisplayName("保存版本产物时应强制写入 cleaned.md 并按配置写入可选产物")
    void saveVersion_shouldWriteCleanedMarkdownAndConfiguredArtifacts() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentProcessingArtifactStorage storage = storage(s3Client, true, false, true);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        DocumentParseResult parseResult = new DocumentParseResult(
                "<html>raw</html>",
                "<p>cleaned</p>",
                "# title",
                "{\"schema_version\":\"v1\"}");

        storage.saveVersion("workspace-1", new DocumentId("doc-artifact-s3-1"), 2, parseResult);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, org.mockito.Mockito.times(3)).putObject(requestCaptor.capture(), any(RequestBody.class));
        List<String> keys = requestCaptor.getAllValues().stream().map(PutObjectRequest::key).toList();
        assertTrue(keys.contains("artifacts/workspace-1/documents/doc-artifact-s3-1/versions/2/cleaned.md"));
        assertTrue(keys.contains("artifacts/workspace-1/documents/doc-artifact-s3-1/versions/2/raw.xhtml"));
        assertTrue(keys.contains("artifacts/workspace-1/documents/doc-artifact-s3-1/versions/2/parse-result.json"));
        assertFalse(keys.contains("artifacts/workspace-1/documents/doc-artifact-s3-1/versions/2/cleaned.html"));
    }

    @Test
    @DisplayName("读取版本 artifact 时应先检查大小，再返回 stable key、正文和字节长度")
    void loadVersionArtifact_shouldReturnContentWithStableKey() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentProcessingArtifactStorage storage = storage(s3Client, false, false, true);
        byte[] content = "hello markdown".getBytes(StandardCharsets.UTF_8);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength((long) content.length)
                .build());
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes(content));

        DocumentVersionArtifactContent artifact = storage
                .loadVersionArtifact(
                        "workspace-1",
                        new DocumentId("doc-artifact-s3-2"),
                        3,
                        DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                        1024)
                .orElseThrow();

        assertEquals("artifacts/workspace-1/documents/doc-artifact-s3-2/versions/3/cleaned.md", artifact.key());
        assertEquals("hello markdown", artifact.content());
        assertEquals(content.length, artifact.contentLength());
    }

    @Test
    @DisplayName("artifact 缺失时应返回空且不读取正文")
    void loadVersionArtifact_shouldReturnEmpty_whenObjectMissing() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentProcessingArtifactStorage storage = storage(s3Client, false, false, true);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenThrow(notFound());

        assertFalse(storage
                .loadVersionArtifact(
                        "workspace-1",
                        new DocumentId("doc-artifact-s3-3"),
                        4,
                        DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                        1024)
                .isPresent());
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("artifact 超过最大读取字节数时应抛出稳定异常且不完整读取对象")
    void loadVersionArtifact_shouldRejectTooLargeObjectBeforeReadingBody() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentProcessingArtifactStorage storage = storage(s3Client, false, false, true);
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(2048L)
                .build());

        DocumentVersionArtifactTooLargeException exception = assertThrows(
                DocumentVersionArtifactTooLargeException.class,
                () -> storage.loadVersionArtifact(
                        "workspace-1",
                        new DocumentId("doc-artifact-s3-4"),
                        5,
                        DocumentProcessingArtifactStorage.CLEANED_MARKDOWN_ARTIFACT_NAME,
                        1024));

        assertEquals(2048L, exception.contentLength());
        assertEquals(1024L, exception.maxBytes());
        verify(s3Client, never()).getObjectAsBytes(any(GetObjectRequest.class));
    }

    @Test
    @DisplayName("删除文档 artifact 时应分页清理 artifacts prefix 且不触碰 source prefix")
    void deleteByDocumentId_shouldDeleteArtifactsPrefixWithPagination() {
        S3Client s3Client = org.mockito.Mockito.mock(S3Client.class);
        S3DocumentProcessingArtifactStorage storage = storage(s3Client, false, false, true);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder()
                                .key("artifacts/workspace-1/documents/doc-artifact-delete/versions/1/cleaned.md")
                                .build())
                        .isTruncated(true)
                        .nextContinuationToken("next-page")
                        .build())
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder()
                                .key("artifacts/workspace-1/documents/doc-artifact-delete/versions/1/parse-result.json")
                                .build())
                        .isTruncated(false)
                        .build());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());

        storage.deleteByDocumentId("workspace-1", new DocumentId("doc-artifact-delete"));

        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, org.mockito.Mockito.times(2)).listObjectsV2(listCaptor.capture());
        verify(s3Client, org.mockito.Mockito.times(2)).deleteObjects(deleteCaptor.capture());
        assertEquals(
                "artifacts/workspace-1/documents/doc-artifact-delete/",
                listCaptor.getAllValues().get(0).prefix());
        assertEquals("next-page", listCaptor.getAllValues().get(1).continuationToken());
        assertTrue(deleteCaptor.getAllValues().stream()
                .flatMap(request -> request.delete().objects().stream())
                .allMatch(identifier -> identifier.key().startsWith("artifacts/workspace-1/documents/doc-artifact-delete/")));
    }

    private static S3DocumentProcessingArtifactStorage storage(
            S3Client s3Client,
            boolean keepRawXhtml,
            boolean keepCleanedHtml,
            boolean keepParseResultJson) {
        IngestProperties properties = new IngestProperties();
        properties.getStorage().getS3().setBucket("myai-documents");
        properties.getStorage().getArtifacts().setKeepRawXhtml(keepRawXhtml);
        properties.getStorage().getArtifacts().setKeepCleanedHtml(keepCleanedHtml);
        properties.getStorage().getArtifacts().setKeepParseResultJson(keepParseResultJson);
        return new S3DocumentProcessingArtifactStorage(s3Client, properties);
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
}
