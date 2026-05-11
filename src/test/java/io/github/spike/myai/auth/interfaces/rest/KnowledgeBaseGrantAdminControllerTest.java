package io.github.spike.myai.auth.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.auth.application.exception.KnowledgeBaseGrantNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedKnowledgeBaseNotFoundException;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.usecase.ListKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ReplaceKnowledgeBaseMemberGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.RevokeKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.application.usecase.UpsertKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeBaseGrantAdminControllerTest {

    private ListKnowledgeBaseGrantsUseCase listKnowledgeBaseGrantsUseCase;
    private UpsertKnowledgeBaseGrantUseCase upsertKnowledgeBaseGrantUseCase;
    private ReplaceKnowledgeBaseMemberGrantsUseCase replaceKnowledgeBaseMemberGrantsUseCase;
    private RevokeKnowledgeBaseGrantUseCase revokeKnowledgeBaseGrantUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.listKnowledgeBaseGrantsUseCase = Mockito.mock(ListKnowledgeBaseGrantsUseCase.class);
        this.upsertKnowledgeBaseGrantUseCase = Mockito.mock(UpsertKnowledgeBaseGrantUseCase.class);
        this.replaceKnowledgeBaseMemberGrantsUseCase = Mockito.mock(ReplaceKnowledgeBaseMemberGrantsUseCase.class);
        this.revokeKnowledgeBaseGrantUseCase = Mockito.mock(RevokeKnowledgeBaseGrantUseCase.class);
        KnowledgeBaseGrantAdminController controller = new KnowledgeBaseGrantAdminController(
                listKnowledgeBaseGrantsUseCase,
                upsertKnowledgeBaseGrantUseCase,
                replaceKnowledgeBaseMemberGrantsUseCase,
                revokeKnowledgeBaseGrantUseCase);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("查询知识库授权列表应返回 grant 结果")
    void listKnowledgeBaseGrants_shouldReturnGrantResults() throws Exception {
        when(listKnowledgeBaseGrantsUseCase.handle("kb-1")).thenReturn(List.of(
                new KnowledgeBaseGrantResult("default", "kb-1", "user-2", "bob", "Bob", KnowledgeBaseRole.KB_READER, "ACTIVE")));

        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{kbId}/grants", "kb-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-2"))
                .andExpect(jsonPath("$[0].role").value("KB_READER"));
    }

    @Test
    @DisplayName("知识库不存在时查询授权列表应返回 404")
    void listKnowledgeBaseGrants_shouldReturnNotFound_whenKnowledgeBaseMissing() throws Exception {
        when(listKnowledgeBaseGrantsUseCase.handle("kb-missing"))
                .thenThrow(new ManagedKnowledgeBaseNotFoundException("knowledge base not found: kb-missing"));

        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{kbId}/grants", "kb-missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("授予知识库授权应返回更新后的 grant")
    void upsertKnowledgeBaseGrant_shouldReturnGrantResult() throws Exception {
        when(upsertKnowledgeBaseGrantUseCase.handle(any())).thenReturn(
                new KnowledgeBaseGrantResult("default", "kb-1", "user-2", "bob", "Bob", KnowledgeBaseRole.KB_MANAGER, "ACTIVE"));

        mockMvc.perform(put("/api/v1/admin/knowledge-bases/{kbId}/grants/{userId}", "kb-1", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "role": "KB_MANAGER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("KB_MANAGER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("回收知识库授权应返回 204")
    void revokeKnowledgeBaseGrant_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/knowledge-bases/{kbId}/grants/{userId}", "kb-1", "user-2"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("回收不存在授权时应返回 404")
    void revokeKnowledgeBaseGrant_shouldReturnNotFound_whenGrantMissing() throws Exception {
        Mockito.doThrow(new KnowledgeBaseGrantNotFoundException("knowledge base grant not found: kb-1/user-2"))
                .when(revokeKnowledgeBaseGrantUseCase)
                .handle(any());

        mockMvc.perform(delete("/api/v1/admin/knowledge-bases/{kbId}/grants/{userId}", "kb-1", "user-2"))
                .andExpect(status().isNotFound());
    }
}
