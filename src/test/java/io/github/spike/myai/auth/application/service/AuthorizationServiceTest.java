package io.github.spike.myai.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuthorizationGrantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/**
 * {@link AuthorizationService} 的单元测试。
 *
 * <p>覆盖授权服务的三级权限模型的核心路径与边界条件：
 * <ul>
 *   <li><strong>工作区级放行：</strong>OWNER / ADMIN 无需查询数据库，
 *       直接通过所有权限检查；</li>
 *   <li><strong>知识库级拒绝：</strong>普通 MEMBER 无显式授权时被拒绝；</li>
 *   <li><strong>未认证拦截：</strong>未登录时抛出认证异常而非授权异常；</li>
 *   <li><strong>DOC_DENY 最高优先级：</strong>文档级拒绝覆盖知识库级允许，
 *       且不触发知识库查询（短路判定）；</li>
 *   <li><strong>知识库回退：</strong>文档无显式权限时，回退到知识库角色判断。</li>
 * </ul>
 *
 * <p>使用 Mockito 模拟 {@link CurrentUserProvider} 和
 * {@link AuthorizationGrantRepository}，隔离外部依赖。
 *
 * @author spike
 * @since 1.0.0
 */
class AuthorizationServiceTest {

    /** 模拟的当前用户提供器 */
    private CurrentUserProvider currentUserProvider;

    /** 模拟的授权 grant 仓储 */
    private AuthorizationGrantRepository grantRepository;

    /** 待测授权服务实例 */
    private AuthorizationService service;

    /**
     * 每个测试方法执行前初始化 Mock 对象和待测服务。
     *
     * <p>两个依赖（currentUserProvider、grantRepository）均使用 Mockito 模拟，
     * 不依赖真实的 Spring 上下文或数据库连接。
     */
    @BeforeEach
    void setUp() {
        this.currentUserProvider = Mockito.mock(CurrentUserProvider.class);
        this.grantRepository = Mockito.mock(AuthorizationGrantRepository.class);
        // 手动构造服务（不使用 Spring DI），实现纯单元测试
        this.service = new AuthorizationService(currentUserProvider, grantRepository);
    }

    /**
     * 验证工作区级别放行规则：OWNER 和 ADMIN 直接通过所有知识库权限检查，
     * 且不查询知识库授权表（数据库零交互）。
     */
    @Test
    @DisplayName("工作区所有者与管理员在知识库级判断中应直接放行")
    void requireCanManageKnowledgeBase_shouldAllowOwnerAndAdmin() {
        // 模拟当前用户为 WORKSPACE_OWNER
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_OWNER));
        // 期望：不抛出异常（直接放行）
        assertDoesNotThrow(() -> service.requireCanManageKnowledgeBase("kb-1"));

        // 模拟当前用户为 WORKSPACE_ADMIN
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_ADMIN));
        // 期望：不抛出异常（直接放行）
        assertDoesNotThrow(() -> service.requireCanManageKnowledgeBase("kb-1"));

        // 验证：工作区级放行不应触发知识库授权查询
        verify(grantRepository, never()).findKnowledgeBaseRole("default", "kb-1", "user-1");
    }

    /**
     * 验证工作区管理权限：仅 OWNER / ADMIN 可执行工作区级管理操作。
     */
    @Test
    @DisplayName("工作区所有者与管理员应具备工作区管理权限")
    void requireCanManageWorkspace_shouldAllowOwnerAndAdmin() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_OWNER));
        assertDoesNotThrow(service::requireCanManageWorkspace);

        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_ADMIN));
        assertDoesNotThrow(service::requireCanManageWorkspace);
    }

    /**
     * 验证工作区管理拒绝规则：普通 MEMBER 不能执行工作区级管理操作。
     */
    @Test
    @DisplayName("工作区普通成员不应具备工作区管理权限")
    void requireCanManageWorkspace_shouldDenyMember() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));

        assertThrows(AccessDeniedException.class, service::requireCanManageWorkspace);
    }

    /**
     * 验证知识库管理授权：普通 MEMBER 拥有 KB_MANAGER grant 时可管理知识库。
     */
    @Test
    @DisplayName("KB_MANAGER 应允许工作区成员管理知识库")
    void requireCanManageKnowledgeBase_shouldAllowKbManager() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));
        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_MANAGER));

        assertDoesNotThrow(() -> service.requireCanManageKnowledgeBase("kb-1"));
    }

    /**
     * 验证知识库管理拒绝：KB_READER 只能读取，不能管理知识库。
     */
    @Test
    @DisplayName("KB_READER 不应允许工作区成员管理知识库")
    void requireCanManageKnowledgeBase_shouldDenyKbReader() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));
        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_READER));

        assertThrows(AccessDeniedException.class, () -> service.requireCanManageKnowledgeBase("kb-1"));
    }

    @Test
    @DisplayName("工作区管理员应允许管理文档且不查询授权表")
    void requireCanManageDocument_shouldAllowWorkspaceAdminForExplicitVersionContent() {
        CurrentUser user = currentUser(WorkspaceRole.WORKSPACE_ADMIN);

        assertDoesNotThrow(() -> service.requireCanManageDocument(user, "doc-1", "kb-1"));
        verify(grantRepository, never()).findDocumentPermission("default", "doc-1", "user-1");
        verify(grantRepository, never()).findKnowledgeBaseRole("default", "kb-1", "user-1");
    }

    @Test
    @DisplayName("KB_MANAGER 应允许成员管理文档")
    void requireCanManageDocument_shouldAllowKbManagerForExplicitVersionContent() {
        CurrentUser user = currentUser(WorkspaceRole.WORKSPACE_MEMBER);
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1")).thenReturn(Optional.empty());
        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_MANAGER));

        assertDoesNotThrow(() -> service.requireCanManageDocument(user, "doc-1", "kb-1"));
    }

    @Test
    @DisplayName("DOC_ALLOW_MANAGE 应允许成员管理文档")
    void requireCanManageDocument_shouldAllowDocumentManageGrantForExplicitVersionContent() {
        CurrentUser user = currentUser(WorkspaceRole.WORKSPACE_MEMBER);
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1"))
                .thenReturn(Optional.of(DocumentPermission.DOC_ALLOW_MANAGE));

        assertDoesNotThrow(() -> service.requireCanManageDocument(user, "doc-1", "kb-1"));
        verify(grantRepository, never()).findKnowledgeBaseRole("default", "kb-1", "user-1");
    }

    @Test
    @DisplayName("DOC_DENY 应覆盖文档管理授权回退")
    void requireCanManageDocument_shouldDenyWhenDocumentGrantIsDeny() {
        CurrentUser user = currentUser(WorkspaceRole.WORKSPACE_MEMBER);
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1"))
                .thenReturn(Optional.of(DocumentPermission.DOC_DENY));

        assertThrows(AccessDeniedException.class, () -> service.requireCanManageDocument(user, "doc-1", "kb-1"));
        verify(grantRepository, never()).findKnowledgeBaseRole("default", "kb-1", "user-1");
    }

    /**
     * 验证知识库级拒绝规则：普通 MEMBER 在无显式知识库授权时，
     * 读取知识库应被拒绝（抛出 AccessDeniedException）。
     */
    @Test
    @DisplayName("工作区成员没有资源授权时应拒绝访问")
    void requireCanReadKnowledgeBase_shouldDenyMemberWithoutGrant() {
        // 模拟当前用户为普通 WORKSPACE_MEMBER
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));
        // 模拟：知识库授权表中无此用户的 ACTIVE 授权记录
        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1")).thenReturn(Optional.empty());

        // 期望：抛出 AccessDeniedException（权限不足）
        assertThrows(AccessDeniedException.class, () -> service.requireCanReadKnowledgeBase("kb-1"));
    }

    /**
     * 验证未认证拦截：未登录用户应在调用
     * {@code CurrentUserProvider#requireCurrentUser()} 时就被拦截，
     * 抛出认证异常而非授权异常。
     */
    @Test
    @DisplayName("未登录时应抛出认证异常")
    void requireCanAskKnowledgeBase_shouldRejectUnauthenticatedUser() {
        // 模拟：未认证用户（requireCurrentUser 直接抛出 AuthenticationCredentialsNotFoundException）
        when(currentUserProvider.requireCurrentUser())
                .thenThrow(new AuthenticationCredentialsNotFoundException("authentication is required"));

        // 期望：抛出 AuthenticationCredentialsNotFoundException（而非 AccessDeniedException）
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.requireCanAskKnowledgeBase("kb-1"));
    }

    /**
     * 验证文档级 DOC_DENY 的最高优先级：即使存在知识库级允许，
     * 只要文档权限为 DOC_DENY，立即拒绝且不查询知识库授权表
     * （短路判定，减少数据库查询）。
     */
    @Test
    @DisplayName("文档级 DOC_DENY 应覆盖知识库允许")
    void requireCanReadDocument_shouldDenyWhenDocumentGrantIsDeny() {
        // 模拟当前用户为普通 WORKSPACE_MEMBER
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));
        // 模拟：文档权限为 DOC_DENY（最高优先级拒绝）
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1"))
                .thenReturn(Optional.of(DocumentPermission.DOC_DENY));

        // 期望：DOC_DENY 立即拒绝，不继续回退
        assertThrows(AccessDeniedException.class, () -> service.requireCanReadDocument("doc-1", "kb-1"));

        // 验证：DOC_DENY 短路后不应再查询知识库授权表
        verify(grantRepository, never()).findKnowledgeBaseRole("default", "kb-1", "user-1");
    }

    /**
     * 验证知识库回退规则：文档无显式权限覆盖时，
     * 回退到知识库级角色判定。KB_READER 角色应允许读取文档。
     */
    @Test
    @DisplayName("知识库读取授权应允许成员读取文档")
    void requireCanReadDocument_shouldFallbackToKnowledgeBaseGrant() {
        // 模拟当前用户为普通 WORKSPACE_MEMBER
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));
        // 模拟：文档无显式权限覆盖
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1")).thenReturn(Optional.empty());
        // 模拟：回退到知识库级，具有 KB_READER 角色
        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_READER));

        // 期望：KB_READER 角色允许读取文档，不抛出异常
        assertDoesNotThrow(() -> service.requireCanReadDocument("doc-1", "kb-1"));
    }

    @Test
    @DisplayName("工作区管理员应允许读取 askable baseline 正文且不查询授权表")
    void requireCanReadDocument_shouldAllowWorkspaceAdminForAskableBaselineContent() {
        CurrentUser user = currentUser(WorkspaceRole.WORKSPACE_ADMIN);

        assertDoesNotThrow(() -> service.requireCanReadDocument(user, "doc-1", "kb-1"));
        verify(grantRepository, never()).findDocumentPermission("default", "doc-1", "user-1");
        verify(grantRepository, never()).findKnowledgeBaseRole("default", "kb-1", "user-1");
    }

    @Test
    @DisplayName("KB_MANAGER、KB_CONTRIBUTOR、KB_READER 应允许读取 askable baseline 正文")
    void requireCanReadDocument_shouldAllowReadableKnowledgeBaseRolesForAskableBaselineContent() {
        CurrentUser user = currentUser(WorkspaceRole.WORKSPACE_MEMBER);
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1")).thenReturn(Optional.empty());

        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_MANAGER));
        assertDoesNotThrow(() -> service.requireCanReadDocument(user, "doc-1", "kb-1"));

        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_CONTRIBUTOR));
        assertDoesNotThrow(() -> service.requireCanReadDocument(user, "doc-1", "kb-1"));

        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_READER));
        assertDoesNotThrow(() -> service.requireCanReadDocument(user, "doc-1", "kb-1"));
    }

    @Test
    @DisplayName("KB_ASKER 只能问答，不能读取 askable baseline 正文")
    void requireCanReadDocument_shouldDenyKbAskerForAskableBaselineContent() {
        CurrentUser user = currentUser(WorkspaceRole.WORKSPACE_MEMBER);
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1")).thenReturn(Optional.empty());
        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_ASKER));

        assertThrows(AccessDeniedException.class, () -> service.requireCanReadDocument(user, "doc-1", "kb-1"));
    }

    @Test
    @DisplayName("KB_ASKER 应允许成员在问答场景访问文档")
    void requireCanAskDocument_shouldFallbackToKnowledgeBaseAskGrant() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1")).thenReturn(Optional.empty());
        when(grantRepository.findKnowledgeBaseRole("default", "kb-1", "user-1"))
                .thenReturn(Optional.of(KnowledgeBaseRole.KB_ASKER));

        assertDoesNotThrow(() -> service.requireCanAskDocument("doc-1", "kb-1"));
    }

    @Test
    @DisplayName("问答场景下文档级 DOC_DENY 应覆盖 KB_ASKER")
    void requireCanAskDocument_shouldDenyWhenDocumentGrantIsDeny() {
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser(WorkspaceRole.WORKSPACE_MEMBER));
        when(grantRepository.findDocumentPermission("default", "doc-1", "user-1"))
                .thenReturn(Optional.of(DocumentPermission.DOC_DENY));

        assertThrows(AccessDeniedException.class, () -> service.requireCanAskDocument("doc-1", "kb-1"));
        verify(grantRepository, never()).findKnowledgeBaseRole("default", "kb-1", "user-1");
    }

    /**
     * 构造测试用的 {@link CurrentUser} 实例。
     *
     * <p>固定使用以下测试数据：
     * <ul>
     *   <li>userId = {@code "user-1"}</li>
     *   <li>username = {@code "alice"}</li>
     *   <li>workspaceId = {@code "default"}</li>
     * </ul>
     * 仅角色参数可变，覆盖 OWNER / ADMIN / MEMBER 三种场景。
     *
     * @param workspaceRole 工作区角色
     * @return 预填充的测试用户对象
     */
    private static CurrentUser currentUser(WorkspaceRole workspaceRole) {
        return new CurrentUser("user-1", "alice", "default", workspaceRole);
    }
}
