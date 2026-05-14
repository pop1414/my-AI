package io.github.spike.myai.ingest.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.command.UploadNewDocumentVersionCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteConflictException;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteFailedException;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.query.ListDocumentVersionsQuery;
import io.github.spike.myai.ingest.application.query.ListDocumentsQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunkPreviewItemResult;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.application.result.DocumentListItemResult;
import io.github.spike.myai.ingest.application.result.DocumentListPageResult;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import io.github.spike.myai.ingest.application.result.DocumentVersionHistoryItemResult;
import io.github.spike.myai.ingest.application.result.DocumentVersionHistoryResult;
import io.github.spike.myai.ingest.application.result.DocumentVersionUploadResult;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.application.usecase.DeleteDocumentUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentChunksPreviewUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.application.usecase.ListDocumentVersionsUseCase;
import io.github.spike.myai.ingest.application.usecase.ListDocumentsUseCase;
import io.github.spike.myai.ingest.application.usecase.ReprocessDocumentUseCase;
import io.github.spike.myai.ingest.application.usecase.UploadNewDocumentVersionUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.DocumentVersionOriginType;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.shared.rest.BusinessException;
import io.github.spike.myai.shared.rest.GlobalRestExceptionHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
    private ListDocumentsUseCase listDocumentsUseCase;
    private GetDocumentStatusUseCase getDocumentStatusUseCase;
    private ListDocumentVersionsUseCase listDocumentVersionsUseCase;
    private GetDocumentChunksPreviewUseCase getDocumentChunksPreviewUseCase;
    private ReprocessDocumentUseCase reprocessDocumentUseCase;
    private DeleteDocumentUseCase deleteDocumentUseCase;
    private UploadNewDocumentVersionUseCase uploadNewDocumentVersionUseCase;
    private DocumentSourceStorage documentSourceStorage;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.acceptUploadUseCase = Mockito.mock(AcceptUploadUseCase.class);
        this.listDocumentsUseCase = Mockito.mock(ListDocumentsUseCase.class);
        this.getDocumentStatusUseCase = Mockito.mock(GetDocumentStatusUseCase.class);
        this.listDocumentVersionsUseCase = Mockito.mock(ListDocumentVersionsUseCase.class);
        this.getDocumentChunksPreviewUseCase = Mockito.mock(GetDocumentChunksPreviewUseCase.class);
        this.reprocessDocumentUseCase = Mockito.mock(ReprocessDocumentUseCase.class);
        this.deleteDocumentUseCase = Mockito.mock(DeleteDocumentUseCase.class);
        this.uploadNewDocumentVersionUseCase = Mockito.mock(UploadNewDocumentVersionUseCase.class);
        this.documentSourceStorage = Mockito.mock(DocumentSourceStorage.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        DocumentIngestController controller =
                new DocumentIngestController(
                        acceptUploadUseCase,
                        listDocumentsUseCase,
                getDocumentStatusUseCase,
                listDocumentVersionsUseCase,
                getDocumentChunksPreviewUseCase,
                reprocessDocumentUseCase,
                deleteDocumentUseCase,
                uploadNewDocumentVersionUseCase,
                documentSourceStorage,
                objectMapper);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalRestExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("文档列表查询应返回分页结果与失败原因字段")
    void listDocuments_shouldReturnPagedItems() throws Exception {
        when(listDocumentsUseCase.handle(any(ListDocumentsQuery.class))).thenReturn(new DocumentListPageResult(
                java.util.List.of(
                        new DocumentListItemResult(
                                "doc-1",
                                "kb-1",
                                2,
                                "UPLOAD",
                                "alpha.txt",
                                128L,
                                "FAILED",
                                "parse failed",
                                java.time.Instant.parse("2026-05-07T10:00:00Z"),
                                java.time.Instant.parse("2026-05-07T10:05:00Z")),
                        new DocumentListItemResult(
                                "doc-2",
                                "kb-1",
                                3,
                                "ROLLBACK",
                                "beta.txt",
                                64L,
                                "INDEXED",
                                null,
                                java.time.Instant.parse("2026-05-07T09:00:00Z"),
                                java.time.Instant.parse("2026-05-07T09:10:00Z"))),
                2L,
                20,
                0));

        mockMvc.perform(get("/api/v1/documents")
                        .param("kbId", "kb-1")
                        .param("filename", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.items[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.items[0].latestVersionNumber").value(2))
                .andExpect(jsonPath("$.items[0].latestVersionOriginType").value("UPLOAD"))
                .andExpect(jsonPath("$.items[0].failureReason").value("parse failed"))
                .andExpect(jsonPath("$.items[1].documentId").value("doc-2"))
                .andExpect(jsonPath("$.items[1].latestVersionNumber").value(3))
                .andExpect(jsonPath("$.items[1].latestVersionOriginType").value("ROLLBACK"))
                .andExpect(jsonPath("$.items[1].failureReason").value(Matchers.nullValue()));

        ArgumentCaptor<ListDocumentsQuery> captor = ArgumentCaptor.forClass(ListDocumentsQuery.class);
        verify(listDocumentsUseCase).handle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("kb-1", captor.getValue().kbId());
        org.junit.jupiter.api.Assertions.assertEquals("alpha", captor.getValue().filename());
        org.junit.jupiter.api.Assertions.assertEquals(20, captor.getValue().limit());
        org.junit.jupiter.api.Assertions.assertEquals(0, captor.getValue().offset());
    }

    @Test
    @DisplayName("文档列表参数非法时应返回 400")
    void listDocuments_shouldReturnBadRequest_whenInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/documents")
                        .param("status", "INVALID_VALUE"))
                .andExpect(status().isBadRequest());

        verify(listDocumentsUseCase, never()).handle(any(ListDocumentsQuery.class));
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
    @DisplayName("上传知识库不存在时应返回 400")
    void upload_shouldReturnBadRequest_whenKnowledgeBaseMissing() throws Exception {
        when(acceptUploadUseCase.handle(any(AcceptUploadCommand.class)))
                .thenThrow(new KnowledgeBaseNotFoundException("knowledge base not found: kb-missing"));
        MockMultipartFile file =
                new MockMultipartFile("file", "demo.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/upload").file(file).param("kbId", "kb-missing"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("上传知识库停用时应返回 409")
    void upload_shouldReturnConflict_whenKnowledgeBaseInactive() throws Exception {
        when(acceptUploadUseCase.handle(any(AcceptUploadCommand.class)))
                .thenThrow(new KnowledgeBaseInactiveException("knowledge base is inactive: kb-inactive"));
        MockMultipartFile file =
                new MockMultipartFile("file", "demo.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/upload").file(file).param("kbId", "kb-inactive"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("状态查询命中时，应返回 200 和当前状态")
    void getStatus_shouldReturnStatus_whenDocumentExists() throws Exception {
        when(getDocumentStatusUseCase.handle(any(GetDocumentStatusQuery.class)))
                .thenReturn(new DocumentStatusResult(
                        new DocumentId("doc-200"),
                        4,
                        "latest.pdf",
                        DocumentVersionOriginType.UPLOAD,
                        UploadStatus.UPLOADED,
                        null));

        mockMvc.perform(get("/api/v1/documents/{documentId}/status", "doc-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-200"))
                .andExpect(jsonPath("$.latestVersionNumber").value(4))
                .andExpect(jsonPath("$.latestFilename").value("latest.pdf"))
                .andExpect(jsonPath("$.latestVersionOriginType").value("UPLOAD"))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.processingMetadata").doesNotExist());

        ArgumentCaptor<GetDocumentStatusQuery> captor = ArgumentCaptor.forClass(GetDocumentStatusQuery.class);
        verify(getDocumentStatusUseCase).handle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("doc-200", captor.getValue().documentId());
    }

    @Test
    @DisplayName("终态状态查询命中时，应返回 processingMetadata 对象")
    void getStatus_shouldReturnProcessingMetadata_whenTerminalStatusIncludesMetadata() throws Exception {
        when(getDocumentStatusUseCase.handle(any(GetDocumentStatusQuery.class)))
                .thenReturn(new DocumentStatusResult(
                        new DocumentId("doc-201"),
                        5,
                        "indexed.pdf",
                        DocumentVersionOriginType.ROLLBACK,
                        UploadStatus.INDEXED,
                        "{\"schema_version\":\"v1\",\"stable\":{\"source_file\":\"demo.pdf\"}}"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/status", "doc-201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-201"))
                .andExpect(jsonPath("$.latestVersionNumber").value(5))
                .andExpect(jsonPath("$.latestFilename").value("indexed.pdf"))
                .andExpect(jsonPath("$.latestVersionOriginType").value("ROLLBACK"))
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.processingMetadata.schema_version").value("v1"))
                .andExpect(jsonPath("$.processingMetadata.stable.source_file").value("demo.pdf"));
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
    @DisplayName("版本历史查询命中时，应返回倒序版本列表与可问答标记")
    void listVersions_shouldReturnVersionHistory_whenDocumentExists() throws Exception {
        when(listDocumentVersionsUseCase.handle(any(ListDocumentVersionsQuery.class)))
                .thenReturn(new DocumentVersionHistoryResult(
                        "doc-500",
                        "versionNumber,DESC",
                        java.util.List.of(
                                new DocumentVersionHistoryItemResult(
                                        "doc-500",
                                        3,
                                        "ROLLBACK",
                                        1,
                                        "rollback.pdf",
                                        300L,
                                         "INDEXED",
                                         null,
                                         "user-rollback",
                                         "Rollback User",
                                         java.time.Instant.parse("2026-05-08T10:00:00Z"),
                                         java.time.Instant.parse("2026-05-08T10:05:00Z"),
                                        true,
                                        true),
                                new DocumentVersionHistoryItemResult(
                                        "doc-500",
                                        2,
                                        "UPLOAD",
                                        null,
                                        "failed.pdf",
                                        200L,
                                         "FAILED",
                                         "parse failed",
                                         "user-upload",
                                         "Upload User",
                                         java.time.Instant.parse("2026-05-08T09:00:00Z"),
                                         java.time.Instant.parse("2026-05-08T09:05:00Z"),
                                        false,
                                        false))));

        mockMvc.perform(get("/api/v1/documents/{documentId}/versions", "doc-500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-500"))
                .andExpect(jsonPath("$.sort").value("versionNumber,DESC"))
                .andExpect(jsonPath("$.versions[0].documentId").value("doc-500"))
                .andExpect(jsonPath("$.versions[0].versionNumber").value(3))
                .andExpect(jsonPath("$.versions[0].versionOriginType").value("ROLLBACK"))
                .andExpect(jsonPath("$.versions[0].rollbackFromVersionNumber").value(1))
                .andExpect(jsonPath("$.versions[0].filename").value("rollback.pdf"))
                .andExpect(jsonPath("$.versions[0].fileSize").value(300))
                 .andExpect(jsonPath("$.versions[0].status").value("INDEXED"))
                .andExpect(jsonPath("$.versions[0].createdByUserId").value("user-rollback"))
                .andExpect(jsonPath("$.versions[0].createdByDisplayName").value("Rollback User"))
                 .andExpect(jsonPath("$.versions[0].createdAt").value("2026-05-08T10:00:00Z"))
                .andExpect(jsonPath("$.versions[0].updatedAt").value("2026-05-08T10:05:00Z"))
                .andExpect(jsonPath("$.versions[0].isLatestVersion").value(true))
                .andExpect(jsonPath("$.versions[0].isAskableVersion").value(true))
                .andExpect(jsonPath("$.versions[1].documentId").value("doc-500"))
                .andExpect(jsonPath("$.versions[1].versionNumber").value(2))
                .andExpect(jsonPath("$.versions[1].versionOriginType").value("UPLOAD"))
                .andExpect(jsonPath("$.versions[1].filename").value("failed.pdf"))
                .andExpect(jsonPath("$.versions[1].fileSize").value(200))
                 .andExpect(jsonPath("$.versions[1].status").value("FAILED"))
                 .andExpect(jsonPath("$.versions[1].failureReason").value("parse failed"))
                .andExpect(jsonPath("$.versions[1].createdByUserId").value("user-upload"))
                .andExpect(jsonPath("$.versions[1].createdByDisplayName").value("Upload User"))
                 .andExpect(jsonPath("$.versions[1].createdAt").value("2026-05-08T09:00:00Z"))
                .andExpect(jsonPath("$.versions[1].updatedAt").value("2026-05-08T09:05:00Z"))
                .andExpect(jsonPath("$.versions[1].isLatestVersion").value(false))
                .andExpect(jsonPath("$.versions[1].isAskableVersion").value(false));

        ArgumentCaptor<ListDocumentVersionsQuery> captor = ArgumentCaptor.forClass(ListDocumentVersionsQuery.class);
        verify(listDocumentVersionsUseCase).handle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("doc-500", captor.getValue().documentId());
    }

    @Test
    @DisplayName("版本历史查询文档不存在时，应返回 404")
    void listVersions_shouldReturnNotFound_whenDocumentMissing() throws Exception {
        when(listDocumentVersionsUseCase.handle(any(ListDocumentVersionsQuery.class)))
                .thenThrow(new DocumentNotFoundException("document not found: doc-missing"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/versions", "doc-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("版本历史查询无管理权限时，应返回 403")
    void listVersions_shouldReturnForbidden_whenManageAccessDenied() throws Exception {
        when(listDocumentVersionsUseCase.handle(any(ListDocumentVersionsQuery.class)))
                .thenThrow(new AccessDeniedException("document manage access denied"));

        mockMvc.perform(get("/api/v1/documents/{documentId}/versions", "doc-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("document manage access denied"));
    }

    @Test
    @DisplayName("上传新版本成功创建版本时，应返回版本上下文并透传文件内容给用例")
    void uploadNewVersion_shouldReturnCreatedResult() throws Exception {
        when(uploadNewDocumentVersionUseCase.handle(any(UploadNewDocumentVersionCommand.class)))
                .thenReturn(new DocumentVersionUploadResult(
                        "doc-600",
                        true,
                        "CREATED",
                        3,
                        2,
                        null,
                        3,
                        2,
                        true,
                        "UPLOADED",
                        "UPLOAD"));
        MockMultipartFile file =
                new MockMultipartFile("file", "v3.txt", MediaType.TEXT_PLAIN_VALUE, "new content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/{documentId}/versions", "doc-600")
                        .file(file)
                        .param("expectedLatestVersionNumber", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-600"))
                .andExpect(jsonPath("$.versionCreated").value(true))
                .andExpect(jsonPath("$.versionResultType").value("CREATED"))
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.previousVersionNumber").value(2))
                .andExpect(jsonPath("$.latestVersionNumber").value(3))
                .andExpect(jsonPath("$.askableVersionNumber").value(2))
                .andExpect(jsonPath("$.canAskNow").value(true))
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.versionOriginType").value("UPLOAD"));

        ArgumentCaptor<UploadNewDocumentVersionCommand> captor =
                ArgumentCaptor.forClass(UploadNewDocumentVersionCommand.class);
        verify(uploadNewDocumentVersionUseCase).handle(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("doc-600", captor.getValue().documentId());
        org.junit.jupiter.api.Assertions.assertEquals("v3.txt", captor.getValue().filename());
        org.junit.jupiter.api.Assertions.assertEquals(11L, captor.getValue().fileSize());
        org.junit.jupiter.api.Assertions.assertEquals(2, captor.getValue().expectedLatestVersionNumber());
        org.junit.jupiter.api.Assertions.assertArrayEquals("new content".getBytes(), captor.getValue().sourceContent());
        verify(documentSourceStorage, never()).saveVersion(
                any(DocumentId.class),
                org.mockito.ArgumentMatchers.anyInt(),
                any(String.class),
                any(byte[].class));
    }

    @Test
    @DisplayName("上传新版本同内容复用时，不应保存新的版本源文件")
    void uploadNewVersion_shouldNotSaveSource_whenReusedIdenticalContent() throws Exception {
        when(uploadNewDocumentVersionUseCase.handle(any(UploadNewDocumentVersionCommand.class)))
                .thenReturn(new DocumentVersionUploadResult(
                        "doc-601",
                        false,
                        "REUSED_IDENTICAL_CONTENT",
                        null,
                        2,
                        2,
                        2,
                        2,
                        true,
                        "INDEXED",
                        "UPLOAD"));
        MockMultipartFile file =
                new MockMultipartFile("file", "same.txt", MediaType.TEXT_PLAIN_VALUE, "same".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/{documentId}/versions", "doc-601")
                        .file(file)
                        .param("expectedLatestVersionNumber", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionCreated").value(false))
                .andExpect(jsonPath("$.versionResultType").value("REUSED_IDENTICAL_CONTENT"))
                .andExpect(jsonPath("$.reusedLatestVersionNumber").value(2))
                .andExpect(jsonPath("$.latestVersionNumber").value(2));

        verify(documentSourceStorage, never()).saveVersion(
                any(DocumentId.class),
                org.mockito.ArgumentMatchers.anyInt(),
                any(String.class),
                any(byte[].class));
    }

    @Test
    @DisplayName("上传新版本业务冲突时，应返回稳定业务错误码")
    void uploadNewVersion_shouldReturnBusinessErrorCode_whenUseCaseThrowsBusinessException() throws Exception {
        when(uploadNewDocumentVersionUseCase.handle(any(UploadNewDocumentVersionCommand.class)))
                .thenThrow(new BusinessException(
                        HttpStatus.CONFLICT,
                        "VERSION_CONFLICT_STALE_LATEST_VERSION",
                        "当前最新版本已变化，请刷新详情后重试"));
        MockMultipartFile file =
                new MockMultipartFile("file", "v3.txt", MediaType.TEXT_PLAIN_VALUE, "new content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/{documentId}/versions", "doc-602")
                        .file(file)
                        .param("expectedLatestVersionNumber", "2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT_STALE_LATEST_VERSION"))
                .andExpect(jsonPath("$.message").value("当前最新版本已变化，请刷新详情后重试"));
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
                .thenReturn(new DocumentStatusResult(
                        new DocumentId("doc-900"),
                        6,
                        "rollback-source.txt",
                        DocumentVersionOriginType.ROLLBACK,
                        UploadStatus.UPLOADED,
                        null));

        mockMvc.perform(post("/api/v1/documents/{documentId}/reprocess", "doc-900"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-900"))
                .andExpect(jsonPath("$.latestVersionNumber").value(6))
                .andExpect(jsonPath("$.latestFilename").value("rollback-source.txt"))
                .andExpect(jsonPath("$.latestVersionOriginType").value("ROLLBACK"))
                .andExpect(jsonPath("$.status").value("UPLOADED"));
    }

    @Test
    @DisplayName("删除文档成功时应返回 204")
    void delete_shouldReturnNoContent_whenSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{documentId}", "doc-del-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("删除文档不存在时应返回 404")
    void delete_shouldReturnNotFound_whenMissing() throws Exception {
        Mockito.doThrow(new DocumentNotFoundException("document not found: doc-missing"))
                .when(deleteDocumentUseCase)
                .handle(any());

        mockMvc.perform(delete("/api/v1/documents/{documentId}", "doc-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除文档状态冲突时应返回 409")
    void delete_shouldReturnConflict_whenConflict() throws Exception {
        Mockito.doThrow(new DocumentDeleteConflictException("conflict"))
                .when(deleteDocumentUseCase)
                .handle(any());

        mockMvc.perform(delete("/api/v1/documents/{documentId}", "doc-ingesting"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("删除执行失败时应返回 500")
    void delete_shouldReturnInternalServerError_whenDeleteFailed() throws Exception {
        Mockito.doThrow(new DocumentDeleteFailedException("failed to delete document asset", new RuntimeException()))
                .when(deleteDocumentUseCase)
                .handle(any());

        mockMvc.perform(delete("/api/v1/documents/{documentId}", "doc-del-failed"))
                .andExpect(status().isInternalServerError());
    }
}
