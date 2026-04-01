package io.github.spike.myai.ingest.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
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
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.acceptUploadUseCase = Mockito.mock(AcceptUploadUseCase.class);
        this.getDocumentStatusUseCase = Mockito.mock(GetDocumentStatusUseCase.class);
        DocumentIngestController controller = new DocumentIngestController(acceptUploadUseCase, getDocumentStatusUseCase);
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
    }

    @Test
    @DisplayName("上传空文件时，应返回 400 且不调用应用层")
    void upload_shouldReturnBadRequest_whenFileIsEmpty() throws Exception {
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.txt", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/v1/documents/upload").file(emptyFile))
                .andExpect(status().isBadRequest());

        verify(acceptUploadUseCase, never()).handle(any(AcceptUploadCommand.class));
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
}
