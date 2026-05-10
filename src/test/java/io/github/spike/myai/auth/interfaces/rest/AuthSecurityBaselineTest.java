package io.github.spike.myai.auth.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.auth.application.result.CurrentUserResult;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.result.KnowledgeBaseGrantResult;
import io.github.spike.myai.auth.application.result.WorkspaceMemberResult;
import io.github.spike.myai.auth.application.usecase.ListDocumentGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.ListKnowledgeBaseGrantsUseCase;
import io.github.spike.myai.auth.application.usecase.LoginUseCase;
import io.github.spike.myai.auth.application.usecase.RevokeDocumentGrantUseCase;
import io.github.spike.myai.auth.application.usecase.RevokeKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.application.usecase.ListWorkspaceMembersUseCase;
import io.github.spike.myai.auth.application.usecase.UpsertDocumentGrantUseCase;
import io.github.spike.myai.auth.application.usecase.UpsertKnowledgeBaseGrantUseCase;
import io.github.spike.myai.auth.application.usecase.UpdateWorkspaceMemberRoleUseCase;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.security.SecurityConstants;
import io.github.spike.myai.ingest.application.usecase.ListDocumentsUseCase;
import io.github.spike.myai.qa.application.usecase.AskQuestionUseCase;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthSecurityBaselineTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoginUseCase loginUseCase;
    @MockBean
    private ListDocumentsUseCase listDocumentsUseCase;
    @MockBean
    private AskQuestionUseCase askQuestionUseCase;
    @MockBean
    private ListWorkspaceMembersUseCase listWorkspaceMembersUseCase;
    @MockBean
    private UpdateWorkspaceMemberRoleUseCase updateWorkspaceMemberRoleUseCase;
    @MockBean
    private ListKnowledgeBaseGrantsUseCase listKnowledgeBaseGrantsUseCase;
    @MockBean
    private UpsertKnowledgeBaseGrantUseCase upsertKnowledgeBaseGrantUseCase;
    @MockBean
    private RevokeKnowledgeBaseGrantUseCase revokeKnowledgeBaseGrantUseCase;
    @MockBean
    private ListDocumentGrantsUseCase listDocumentGrantsUseCase;
    @MockBean
    private UpsertDocumentGrantUseCase upsertDocumentGrantUseCase;
    @MockBean
    private RevokeDocumentGrantUseCase revokeDocumentGrantUseCase;

    @Test
    @DisplayName("未登录访问当前用户接口应返回 401")
    void me_shouldReturnUnauthorized_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("未登录访问文档列表接口应返回 401")
    void documents_shouldReturnUnauthorized_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("写操作缺少 CSRF Header 应返回 403")
    void writeRequest_shouldReturnForbidden_whenCsrfHeaderMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_REQUIRED"));
    }

    @Test
    @DisplayName("登录成功后应创建 Session 并可读取当前用户")
    void login_shouldCreateSessionAndMe_shouldReturnCurrentUser() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.userId").value("user-1"))
                .andExpect(jsonPath("$.user.workspaceRole").value("WORKSPACE_ADMIN"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.workspaceId").value("default"));
    }

    @Test
    @DisplayName("登出后 Session 应失效")
    void logout_shouldInvalidateSession() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登录密码错误应返回 401")
    void login_shouldReturnUnauthorized_whenCredentialsInvalid() throws Exception {
        when(loginUseCase.handle(any())).thenThrow(new BadCredentialsException("invalid username or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "bad"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("账号锁定时登录应返回 403")
    void login_shouldReturnForbidden_whenAccountLocked() throws Exception {
        when(loginUseCase.handle(any())).thenThrow(new LockedException("account is locked"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("已登录但无文档读取权限时应返回 403")
    void documents_shouldReturnForbidden_whenAccessDenied() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_MEMBER));
        when(listDocumentsUseCase.handle(any()))
                .thenThrow(new AccessDeniedException("document read access denied"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/documents").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("未登录访问问答接口应返回 401")
    void qaAsk_shouldReturnUnauthorized_whenAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/qa/ask")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是RAG"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("已登录但无知识库问答权限时应返回 403")
    void qaAsk_shouldReturnForbidden_whenAccessDenied() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_MEMBER));
        when(askQuestionUseCase.handle(any()))
                .thenThrow(new AccessDeniedException("knowledge base ask access denied"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(post("/api/v1/qa/ask")
                        .session(session)
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是RAG",
                                  "kbId": "default"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("未登录访问成员治理接口应返回 401")
    void adminMembers_shouldReturnUnauthorized_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("普通成员访问成员治理接口应返回 403")
    void adminMembers_shouldReturnForbidden_whenAccessDenied() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_MEMBER));
        when(listWorkspaceMembersUseCase.handle())
                .thenThrow(new AccessDeniedException("workspace manage access denied"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/admin/members").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("管理员访问成员治理接口应返回成员列表")
    void adminMembers_shouldReturnWorkspaceMembers_whenAdminLoggedIn() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN));
        when(listWorkspaceMembersUseCase.handle()).thenReturn(List.of(
                new WorkspaceMemberResult("user-1", "alice", "Alice", "default", WorkspaceRole.WORKSPACE_ADMIN, "ACTIVE")));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/admin/members").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-1"))
                .andExpect(jsonPath("$[0].workspaceRole").value("WORKSPACE_ADMIN"));
    }

    @Test
    @DisplayName("未登录访问知识库授权治理接口应返回 401")
    void knowledgeBaseGrants_shouldReturnUnauthorized_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{kbId}/grants", "kb-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("普通成员访问知识库授权治理接口应返回 403")
    void knowledgeBaseGrants_shouldReturnForbidden_whenAccessDenied() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_MEMBER));
        when(listKnowledgeBaseGrantsUseCase.handle("kb-1"))
                .thenThrow(new AccessDeniedException("workspace manage access denied"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{kbId}/grants", "kb-1").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("管理员访问知识库授权治理接口应返回授权列表")
    void knowledgeBaseGrants_shouldReturnGrantList_whenAdminLoggedIn() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN));
        when(listKnowledgeBaseGrantsUseCase.handle("kb-1")).thenReturn(List.of(
                new KnowledgeBaseGrantResult("default", "kb-1", "user-2", "bob", "Bob", KnowledgeBaseRole.KB_READER, "ACTIVE")));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/admin/knowledge-bases/{kbId}/grants", "kb-1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-2"))
                .andExpect(jsonPath("$[0].role").value("KB_READER"));
    }

    @Test
    @DisplayName("未登录访问文档授权治理接口应返回 401")
    void documentGrants_shouldReturnUnauthorized_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/documents/{documentId}/grants", "doc-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("普通成员访问文档授权治理接口应返回 403")
    void documentGrants_shouldReturnForbidden_whenAccessDenied() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_MEMBER));
        when(listDocumentGrantsUseCase.handle("doc-1"))
                .thenThrow(new AccessDeniedException("workspace manage access denied"));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/admin/documents/{documentId}/grants", "doc-1").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("管理员访问文档授权治理接口应返回授权列表")
    void documentGrants_shouldReturnGrantList_whenAdminLoggedIn() throws Exception {
        when(loginUseCase.handle(any())).thenReturn(new CurrentUserResult(
                "user-1",
                "alice",
                "Alice",
                "default",
                WorkspaceRole.WORKSPACE_ADMIN));
        when(listDocumentGrantsUseCase.handle("doc-1")).thenReturn(List.of(
                new DocumentGrantResult("default", "doc-1", "user-2", "bob", "Bob", DocumentPermission.DOC_ALLOW_READ, "ACTIVE")));

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header(SecurityConstants.CSRF_HEADER_NAME, SecurityConstants.CSRF_HEADER_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        mockMvc.perform(get("/api/v1/admin/documents/{documentId}/grants", "doc-1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user-2"))
                .andExpect(jsonPath("$[0].permission").value("DOC_ALLOW_READ"));
    }
}
