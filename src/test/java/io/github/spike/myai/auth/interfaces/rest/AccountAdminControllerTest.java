package io.github.spike.myai.auth.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.auth.application.exception.ManagedAccountNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedAccountUsernameConflictException;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import io.github.spike.myai.auth.application.usecase.CreateManagedAccountUseCase;
import io.github.spike.myai.auth.application.usecase.ListManagedAccountsUseCase;
import io.github.spike.myai.auth.application.usecase.RemoveManagedAccountMembershipUseCase;
import io.github.spike.myai.auth.application.usecase.ResetManagedAccountPasswordUseCase;
import io.github.spike.myai.auth.application.usecase.UpdateManagedAccountStatusUseCase;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountAdminControllerTest {

    private ListManagedAccountsUseCase listManagedAccountsUseCase;
    private CreateManagedAccountUseCase createManagedAccountUseCase;
    private UpdateManagedAccountStatusUseCase updateManagedAccountStatusUseCase;
    private ResetManagedAccountPasswordUseCase resetManagedAccountPasswordUseCase;
    private RemoveManagedAccountMembershipUseCase removeManagedAccountMembershipUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.listManagedAccountsUseCase = Mockito.mock(ListManagedAccountsUseCase.class);
        this.createManagedAccountUseCase = Mockito.mock(CreateManagedAccountUseCase.class);
        this.updateManagedAccountStatusUseCase = Mockito.mock(UpdateManagedAccountStatusUseCase.class);
        this.resetManagedAccountPasswordUseCase = Mockito.mock(ResetManagedAccountPasswordUseCase.class);
        this.removeManagedAccountMembershipUseCase = Mockito.mock(RemoveManagedAccountMembershipUseCase.class);
        AccountAdminController controller = new AccountAdminController(
                listManagedAccountsUseCase,
                createManagedAccountUseCase,
                updateManagedAccountStatusUseCase,
                resetManagedAccountPasswordUseCase,
                removeManagedAccountMembershipUseCase);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("查询账号列表应返回完整账号治理信息")
    void listAccounts_shouldReturnManagedAccounts() throws Exception {
        when(listManagedAccountsUseCase.handle()).thenReturn(List.of(
                new ManagedAccountResult(
                        "user-1", "alice", "Alice", "ACTIVE", "default", WorkspaceRole.WORKSPACE_ADMIN, "ACTIVE", 0, null)));

        mockMvc.perform(get("/api/v1/admin/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].userStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("创建账号成功时应返回新账号")
    void createAccount_shouldReturnCreatedAccount() throws Exception {
        when(createManagedAccountUseCase.handle(any())).thenReturn(new ManagedAccountResult(
                "user-2", "bob", "Bob", "ACTIVE", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE", 0, null));

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bob",
                                  "displayName": "Bob",
                                  "password": "secret123",
                                  "workspaceRole": "WORKSPACE_MEMBER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-2"))
                .andExpect(jsonPath("$.workspaceRole").value("WORKSPACE_MEMBER"));
    }

    @Test
    @DisplayName("创建账号用户名冲突时应返回 409")
    void createAccount_shouldReturnConflict_whenUsernameExists() throws Exception {
        when(createManagedAccountUseCase.handle(any()))
                .thenThrow(new ManagedAccountUsernameConflictException("username already exists: bob"));

        mockMvc.perform(post("/api/v1/admin/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "bob",
                                  "displayName": "Bob",
                                  "password": "secret123",
                                  "workspaceRole": "WORKSPACE_MEMBER"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("更新账号状态成功时应返回最新状态")
    void updateStatus_shouldReturnUpdatedAccount() throws Exception {
        when(updateManagedAccountStatusUseCase.handle(any())).thenReturn(new ManagedAccountResult(
                "user-2", "bob", "Bob", "DISABLED", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE", 0, null));

        mockMvc.perform(patch("/api/v1/admin/accounts/{userId}/status", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userStatus": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userStatus").value("DISABLED"));
    }

    @Test
    @DisplayName("重置密码成功时应返回账号信息")
    void resetPassword_shouldReturnManagedAccount() throws Exception {
        when(resetManagedAccountPasswordUseCase.handle(any())).thenReturn(new ManagedAccountResult(
                "user-2", "bob", "Bob", "ACTIVE", "default", WorkspaceRole.WORKSPACE_MEMBER, "ACTIVE", 0, null));

        mockMvc.perform(post("/api/v1/admin/accounts/{userId}/password/reset", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "new-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-2"));
    }

    @Test
    @DisplayName("移除成员关系成功时应返回 204")
    void removeMembership_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/accounts/{userId}/membership", "user-2"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("目标账号不存在时应返回 404")
    void updateStatus_shouldReturnNotFound_whenAccountMissing() throws Exception {
        when(updateManagedAccountStatusUseCase.handle(any()))
                .thenThrow(new ManagedAccountNotFoundException("managed account not found: user-2"));

        mockMvc.perform(patch("/api/v1/admin/accounts/{userId}/status", "user-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userStatus": "DISABLED"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
