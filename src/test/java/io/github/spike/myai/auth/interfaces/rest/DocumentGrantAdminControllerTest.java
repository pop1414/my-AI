package io.github.spike.myai.auth.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.auth.application.exception.DocumentGrantNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.ListDocumentGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ReplaceDocumentMemberGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.RevokeDocumentGrantUseCase;
import io.github.spike.myai.auth.application.usecase.UpsertDocumentGrantUseCase;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DocumentGrantAdminControllerTest {

    private ListDocumentGrantsUseCase listDocumentGrantsUseCase;
    private UpsertDocumentGrantUseCase upsertDocumentGrantUseCase;
    private ReplaceDocumentMemberGrantsUseCase replaceDocumentMemberGrantsUseCase;
    private RevokeDocumentGrantUseCase revokeDocumentGrantUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.listDocumentGrantsUseCase = Mockito.mock(ListDocumentGrantsUseCase.class);
        this.upsertDocumentGrantUseCase = Mockito.mock(UpsertDocumentGrantUseCase.class);
        this.replaceDocumentMemberGrantsUseCase = Mockito.mock(ReplaceDocumentMemberGrantsUseCase.class);
        this.revokeDocumentGrantUseCase = Mockito.mock(RevokeDocumentGrantUseCase.class);
        DocumentGrantAdminController controller = new DocumentGrantAdminController(
                listDocumentGrantsUseCase,
                upsertDocumentGrantUseCase,
                replaceDocumentMemberGrantsUseCase,
                revokeDocumentGrantUseCase);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("查询文档授权列表应返回 grant 结果")
    void listDocumentGrants_shouldReturnGrantResults() throws Exception {
        when(listDocumentGrantsUseCase.handle("doc-1")).thenReturn(List.of(
                new DocumentGrantResult("default", "doc-1", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_READ, "ACTIVE")));

        mockMvc.perform(get("/api/v1/admin/documents/{documentId}/grants", "doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-2"))
                .andExpect(jsonPath("$[0].permission").value("DOC_ALLOW_READ"));
    }

    @Test
    @DisplayName("文档不存在时查询授权列表应返回 404")
    void listDocumentGrants_shouldReturnNotFound_whenDocumentMissing() throws Exception {
        when(listDocumentGrantsUseCase.handle("doc-missing"))
                .thenThrow(new ManagedDocumentNotFoundException("document not found: doc-missing"));

        mockMvc.perform(get("/api/v1/admin/documents/{documentId}/grants", "doc-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("授予文档授权应返回更新后的 grant")
    void upsertDocumentGrant_shouldReturnGrantResult() throws Exception {
        when(upsertDocumentGrantUseCase.handle(any())).thenReturn(
                new DocumentGrantResult("default", "doc-1", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_MANAGE, "ACTIVE"));

        mockMvc.perform(put("/api/v1/admin/documents/{documentId}/grants/{userId}", "doc-1", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "permission": "DOC_ALLOW_MANAGE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("DOC_ALLOW_MANAGE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("回收文档授权应返回 204")
    void revokeDocumentGrant_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/documents/{documentId}/grants/{userId}", "doc-1", "user-2"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("回收不存在授权时应返回 404")
    void revokeDocumentGrant_shouldReturnNotFound_whenGrantMissing() throws Exception {
        Mockito.doThrow(new DocumentGrantNotFoundException("document grant not found: doc-1/user-2"))
                .when(revokeDocumentGrantUseCase)
                .handle(any());

        mockMvc.perform(delete("/api/v1/admin/documents/{documentId}/grants/{userId}", "doc-1", "user-2"))
                .andExpect(status().isNotFound());
    }
}
