package io.github.spike.myai.ingest.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunkPreviewItemResult;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentChunksPreviewUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.application.usecase.ReprocessDocumentUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * DocumentIngestController 的接口层测试。
 *
 * <p>测试目标：
 * <ul>
 *     <li>验证 HTTP 入参 -> 用例命令对象的映射是否正确。</li>
 *     <li>验证接口返回结构和状态码是否符合契约。</li>
 *     <li>验证空文件时是否返回 400，并阻止调用应用层。</li>
 * </ul>
 */
class DocumentIngestControllerTest {

    private AcceptUploadUseCase acceptUploadUseCase;
    private GetDocumentStatusUseCase getDocumentStatusUseCase;
    private GetDocumentChunksPreviewUseCase getDocumentChunksPreviewUseCase;
    private ReprocessDocumentUseCase reprocessDocumentUseCase;
    private DocumentSourceStorage documentSourceStorage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.acceptUploadUseCase = Mockito.mock(AcceptUploadUseCase.class);
        this.getDocumentStatusUseCase = Mockito.mock(GetDocumentStatusUseCase.class);
        this.getDocumentChunksPreviewUseCase = Mockito.mock(GetDocumentChunksPreviewUseCase.class);
        this.reprocessDocumentUseCase = Mockito.mock(ReprocessDocumentUseCase.class);
        this.documentSourceStorage = Mockito.mock(DocumentSourceStorage.class);
        DocumentIngestController controller =
                new DocumentIngestController(
                        acceptUploadUseCase,
                        getDocumentStatusUseCase,
                        getDocumentChunksPreviewUseCase,
                        reprocessDocumentUseCase,
                        documentSourceStorage);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("上传非空文件时，应返回 200 且状态为 ACCEPTED")
    void upload_shouldReturnAccepted_whenFileIsValid() throws Exception {
        when(acceptUploadUseCase.handle(any(AcceptUploadCommand.class)))
                .thenReturn(new UploadTicket(new DocumentId("doc-123"), UploadStatus.ACCEPTED));

        MockMultipartFile file =
                new MockMultipartFile("file", "demo.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/upload").file(file).param("kbId", "kb-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-123"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        ArgumentCaptor<AcceptUploadCommand> captor = ArgumentCaptor.forClass(AcceptUploadCommand.class);
        verify(acceptUploadUseCase).handle(captor.capture());
        AcceptUploadCommand captured = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("demo.txt", captured.filename());
        org.junit.jupiter.api.Assertions.assertEquals(5L, captured.fileSize());
        org.junit.jupiter.api.Assertions.assertEquals("kb-a", captured.kbId());
        org.junit.jupiter.api.Assertions.assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                captured.fileHash());
        verify(documentSourceStorage).save(any(DocumentId.class), any(String.class), any(byte[].class));
    }

    @Test
    @DisplayName("上传空文件时，应返回 400 且不调用应用层")
    void upload_shouldReturnBadRequest_whenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/documents/upload").file(emptyFile))
                .andExpect(status().isBadRequest());

        verify(acceptUploadUseCase, never()).handle(any(AcceptUploadCommand.class));
        verify(documentSourceStorage, never()).save(any(DocumentId.class), any(String.class), any(byte[].class));
    }

    @Test
    @DisplayName("状态查询命中时，应返回 200 和当前状态")
    void getStatus_shouldReturnStatus_whenDocumentExists() throws Exception {
        when(getDocumentStatusUseCase.handle(any(GetDocumentStatusQuery.class)))
                .thenReturn(new DocumentStatusResult(new DocumentId("doc-200"), UploadStatus.UPLOADED));

        mockMvc.perform(get("/api/v1/documents/{documentId}/status", "doc-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-200"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));

        ArgumentCaptor<GetDocumentStatusQuery> captor = ArgumentCaptor.forClass(GetDocumentStatusQuery.class);
        verify(getDocumentStatusUseCase).handle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("doc-200", captor.getValue().documentId());
    }

    @Test
    @DisplayName("状态查询未命中时，应返回 404")
    void getStatus_shouldReturnNotFound_whenDocumentMissing() throws Exception {
        when(getDocumentStatusUseCase.handle(any(GetDocumentStatusQuery.class)))
                .thenThrow(new DocumentNotFoundException("document not found: doc-missing"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/status", "doc-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("分块预览命中时，应返回 200 与分块列表")
    void getChunksPreview_shouldReturnPreview_whenFound() throws Exception {
        when(getDocumentChunksPreviewUseCase.handle(any(GetDocumentChunksPreviewQuery.class)))
                .thenReturn(new DocumentChunksPreviewResult(
                        new DocumentId("doc-300"),
                        1,
                        4,
                        10,
                        0,
                        120,
                        java.util.List.of(new DocumentChunkPreviewItemResult(
                                0,
                                200,
                                "这是预览文本",
                                false,
                                "demo.txt",
                                "hash-chunk-1",
                                "v1",
                                "{\"heading\":\"Intro\"}"))));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/preview", "doc-300")
                        .param("limit", "10")
                        .param("offset", "0")
                        .param("previewChars", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-300"))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andExpect(jsonPath("$.totalChunks").value(4))
                .andExpect(jsonPath("$.chunks[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.chunks[0].contentLength").value(200))
                .andExpect(jsonPath("$.chunks[0].contentPreview").value("这是预览文本"))
                .andExpect(jsonPath("$.chunks[0].sourceFile").value("demo.txt"))
                .andExpect(jsonPath("$.chunks[0].contentHash").value("hash-chunk-1"))
                .andExpect(jsonPath("$.chunks[0].splitVersion").value("v1"));
    }

    @Test
    @DisplayName("分块预览文档不存在时，应返回 404")
    void getChunksPreview_shouldReturnNotFound_whenDocumentMissing() throws Exception {
        when(getDocumentChunksPreviewUseCase.handle(any(GetDocumentChunksPreviewQuery.class)))
                .thenThrow(new DocumentNotFoundException("document not found: doc-missing"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/preview", "doc-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("分块预览参数非法时，应返回 400")
    void getChunksPreview_shouldReturnBadRequest_whenInvalidParam() throws Exception {
        when(getDocumentChunksPreviewUseCase.handle(any(GetDocumentChunksPreviewQuery.class)))
                .thenThrow(new IllegalArgumentException("limit must be between 1 and 200"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/chunks/preview", "doc-400")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("重处理触发成功时，应返回 200")
    void reprocess_shouldReturnAccepted_whenAllowed() throws Exception {
        when(reprocessDocumentUseCase.handle(any()))
                .thenReturn(new DocumentStatusResult(new DocumentId("doc-900"), UploadStatus.UPLOADED));

        mockMvc.perform(post("/api/v1/documents/{documentId}/reprocess", "doc-900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-900"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }
}
