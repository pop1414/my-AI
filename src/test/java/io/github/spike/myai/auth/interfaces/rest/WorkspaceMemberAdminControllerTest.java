package io.github.spike.myai.auth.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;
import io.github.spike.myai.auth.application.usecase.ListMemberDocumentGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ListMemberKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ListWorkspaceMembersUseCase;
import io.github.spike.myai.auth.application.usecase.ReplaceMemberDocumentGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ReplaceMemberKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.UpdateWorkspaceMemberRoleUseCase;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkspaceMemberAdminControllerTest {

    private ListWorkspaceMembersUseCase listWorkspaceMembersUseCase;
    private ListMemberKnowledgeBaseGrantsUseCase listMemberKnowledgeBaseGrantsUseCase;
    private ReplaceMemberKnowledgeBaseGrantsUseCase replaceMemberKnowledgeBaseGrantsUseCase;
    private ListMemberDocumentGrantsUseCase listMemberDocumentGrantsUseCase;
    private ReplaceMemberDocumentGrantsUseCase replaceMemberDocumentGrantsUseCase;
    private UpdateWorkspaceMemberRoleUseCase updateWorkspaceMemberRoleUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.listWorkspaceMembersUseCase = Mockito.mock(ListWorkspaceMembersUseCase.class);
        this.listMemberKnowledgeBaseGrantsUseCase = Mockito.mock(ListMemberKnowledgeBaseGrantsUseCase.class);
        this.replaceMemberKnowledgeBaseGrantsUseCase = Mockito.mock(ReplaceMemberKnowledgeBaseGrantsUseCase.class);
        this.listMemberDocumentGrantsUseCase = Mockito.mock(ListMemberDocumentGrantsUseCase.class);
        this.replaceMemberDocumentGrantsUseCase = Mockito.mock(ReplaceMemberDocumentGrantsUseCase.class);
        this.updateWorkspaceMemberRoleUseCase = Mockito.mock(UpdateWorkspaceMemberRoleUseCase.class);
        WorkspaceMemberAdminController controller = new WorkspaceMemberAdminController(
                listWorkspaceMembersUseCase,
                listMemberKnowledgeBaseGrantsUseCase,
                replaceMemberKnowledgeBaseGrantsUseCase,
                listMemberDocumentGrantsUseCase,
                replaceMemberDocumentGrantsUseCase,
                updateWorkspaceMemberRoleUseCase);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("查询成员列表应返回成员主数据")
    void listMembers_shouldReturnWorkspaceMembers() throws Exception {
        when(listWorkspaceMembersUseCase.handle()).thenReturn(List.of(
                new WorkspaceMemberResult("user-1", "alice", "Alice", "default", WorkspaceRole.WORKSPACE_ADMIN, "ACTIVE"),
                new WorkspaceMemberResult("user-2", "bob", "Bob", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE")));

        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-1"))
                .andExpect(jsonPath("$[0].workspaceRole").value("WORKSPACE_ADMIN"))
                .andExpect(jsonPath("$[1].membershipStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("调整成员角色应返回更新后的成员信息")
    void updateMemberRole_shouldReturnUpdatedMember() throws Exception {
        when(updateWorkspaceMemberRoleUseCase.handle(any())).thenReturn(
                new WorkspaceMemberResult("user-2", "bob", "Bob", "default", WorkspaceRole.WORKSPACE_ADMIN, "ACTIVE"));

        mockMvc.perform(patch("/api/v1/admin/members/{userId}/role", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceRole": "WORKSPACE_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-2"))
                .andExpect(jsonPath("$.workspaceRole").value("WORKSPACE_ADMIN"));
    }

    @Test
    @DisplayName("调整成员角色请求体为空时应返回 400")
    void updateMemberRole_shouldReturnBadRequest_whenBodyMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/members/{userId}/role", "user-2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("调整不存在成员角色时应返回 404")
    void updateMemberRole_shouldReturnNotFound_whenMemberMissing() throws Exception {
        when(updateWorkspaceMemberRoleUseCase.handle(any()))
                .thenThrow(new WorkspaceMemberNotFoundException("workspace member not found: user-missing"));

        mockMvc.perform(patch("/api/v1/admin/members/{userId}/role", "user-missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceRole": "WORKSPACE_ADMIN"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("调整成员角色参数非法时应返回 400")
    void updateMemberRole_shouldReturnBadRequest_whenRoleInvalid() throws Exception {
        when(updateWorkspaceMemberRoleUseCase.handle(any()))
                .thenThrow(new IllegalArgumentException("invalid workspaceRole: OWNER"));

        mockMvc.perform(patch("/api/v1/admin/members/{userId}/role", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceRole": "OWNER"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
