package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuthorizationGrantRepository;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * 应用层授权服务。
 *
 * <p>集中封装工作区角色、知识库授权与文档权限覆盖的判断规则，
 * 作为整个 auth 模块的授权入口。知识库、文档、问答等业务应用服务
 * 应依赖此服务进行权限判断，避免授权规则散落在控制器或业务服务中。
 *
 * <p><strong>三级授权模型（优先级从高到低）：</strong>
 * <ol>
 *   <li><strong>工作区级：</strong>OWNER / ADMIN 拥有全量权限，直接放行，
 *       不再查询知识库或文档授权表；</li>
 *   <li><strong>文档级：</strong>若存在 DOC_DENY 覆盖，立即拒绝（最高优先级）；
 *       若存在显式 DOC_ALLOW_*，按允许的操作放行；</li>
 *   <li><strong>知识库级：</strong>文档无显式授权时，回退到知识库角色判断
 *       （KB_MANAGER / KB_CONTRIBUTOR / KB_READER / KB_ASKER）。</li>
 * </ol>
 *
 * <p>设计原则：
 * <ul>
 *   <li>所有公开方法遵循 {@code requireCanXxx} 命名约定——权限不足时抛出
 *       {@link AccessDeniedException}，而非返回布尔值；</li>
 *   <li>使用 {@link Predicate} 作为策略参数，通过方法引用传递角色/权限匹配规则，
 *       让核心判断骨架（{@code requireKnowledgeBaseAccess} /
 *       {@code requireDocumentAccess}）保持通用；</li>
 *   <li>静态权限判定方法（{@code canManageKnowledgeBase} 等）不含副作用，
 *       可被组合和复用。</li>
 * </ul>
 *
 * <p>当前阶段先接入知识库业务接口，文档与问答接口按后续计划分批接入。
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class AuthorizationService {

    /** 当前登录用户上下文提供器，用于获取已认证的用户身份 */
    private final CurrentUserProvider currentUserProvider;

    /** 授权 grant 读取端口（出站端口），查询知识库角色和文档权限 */
    private final AuthorizationGrantRepository grantRepository;

    /**
     * 构造器注入。
     *
     * @param currentUserProvider 当前用户提供器
     * @param grantRepository     授权 grant 仓储
     */
    public AuthorizationService(
            CurrentUserProvider currentUserProvider,
            AuthorizationGrantRepository grantRepository) {
        this.currentUserProvider = currentUserProvider;
        this.grantRepository = grantRepository;
    }

    /**
     * 要求当前用户具备工作区管理权限。
     *
     * <p>当前仅 {@link WorkspaceRole#WORKSPACE_OWNER} 与
     * {@link WorkspaceRole#WORKSPACE_ADMIN} 可执行工作区级管理操作，
     * 例如创建知识库。
     *
     * @return 当前用户上下文，便于调用方继续使用其中的工作区标识
     * @throws AccessDeniedException 权限不足时抛出
     */
    public CurrentUser requireCanManageWorkspace() {
        // 获取当前登录用户（未认证则抛异常）
        CurrentUser user = currentUserProvider.requireCurrentUser();
        // 仅 WORKSPACE_OWNER 或 WORKSPACE_ADMIN 可执行工作区级管理操作
        if (!hasWorkspaceWideAccess(user.workspaceRole())) {
            throw new AccessDeniedException("workspace manage access denied");
        }
        // 返回 CurrentUser 便于调用方（如 CreateKnowledgeBase）直接使用其中的 workspaceId
        return user;
    }

    /**
     * 要求当前用户可管理指定知识库。
     *
     * <p>管理权限对应 {@link KnowledgeBaseRole#KB_MANAGER}，
     * 可执行增删改查、成员管理等全部操作。
     *
     * @param kbId 知识库标识
     * @throws AccessDeniedException 权限不足时抛出
     */
    public void requireCanManageKnowledgeBase(String kbId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        // 委托知识库级权限校验，角色匹配器为 KB_MANAGER
        requireKnowledgeBaseAccess(user, kbId, AuthorizationService::canManageKnowledgeBase, "knowledge base manage access denied");
    }

    /**
     * 要求当前用户可向指定知识库贡献内容。
     *
     * <p>贡献权限对应 {@link KnowledgeBaseRole#KB_MANAGER}
     * 或 {@link KnowledgeBaseRole#KB_CONTRIBUTOR}。
     *
     * @param kbId 知识库标识
     * @throws AccessDeniedException 权限不足时抛出
     */
    public void requireCanContributeKnowledgeBase(String kbId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        // 委托知识库级权限校验，角色匹配器为 KB_MANAGER 或 KB_CONTRIBUTOR
        requireKnowledgeBaseAccess(user, kbId, AuthorizationService::canContributeKnowledgeBase, "knowledge base contribute access denied");
    }

    /**
     * 要求当前用户可读取指定知识库。
     *
     * <p>读取权限对应 {@link KnowledgeBaseRole#KB_MANAGER}、
     * {@link KnowledgeBaseRole#KB_CONTRIBUTOR} 或
     * {@link KnowledgeBaseRole#KB_READER}。
     *
     * @param kbId 知识库标识
     * @throws AccessDeniedException 权限不足时抛出
     */
    public void requireCanReadKnowledgeBase(String kbId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        // 委托知识库级权限校验，角色匹配器为 KB_MANAGER / KB_CONTRIBUTOR / KB_READER
        requireKnowledgeBaseAccess(user, kbId, AuthorizationService::canReadKnowledgeBase, "knowledge base read access denied");
    }

    /**
     * 要求当前用户可在问答场景使用指定知识库。
     *
     * <p>问答权限对应 {@link KnowledgeBaseRole#KB_MANAGER}、
     * {@link KnowledgeBaseRole#KB_CONTRIBUTOR}、
     * {@link KnowledgeBaseRole#KB_READER} 或
     * {@link KnowledgeBaseRole#KB_ASKER}（权限层级最低的角色）。
     *
     * @param kbId 知识库标识
     * @throws AccessDeniedException 权限不足时抛出
     */
    public void requireCanAskKnowledgeBase(String kbId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        // 委托知识库级权限校验，角色匹配器为 KB_MANAGER / KB_CONTRIBUTOR / KB_READER / KB_ASKER
        requireKnowledgeBaseAccess(user, kbId, AuthorizationService::canAskKnowledgeBase, "knowledge base ask access denied");
    }

    /**
     * 要求当前用户可管理指定文档。
     *
     * <p>文档级 {@link DocumentPermission#DOC_DENY} 拥有最高优先级——
     * 一旦存在 DENY 覆盖，即使知识库角色是 KB_MANAGER 也会被拒绝。
     * 文档无显式允许时回退检查知识库管理权限。
     *
     * @param documentId 文档标识
     * @param kbId       文档所属知识库标识
     * @throws AccessDeniedException 权限不足时抛出
     */
    public void requireCanManageDocument(String documentId, String kbId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        // 委托文档级权限校验，文档匹配器为 DOC_ALLOW_MANAGE，回退匹配器为 KB_MANAGER
        requireDocumentAccess(
                user,
                documentId,
                kbId,
                AuthorizationService::canManageDocument,
                AuthorizationService::canManageKnowledgeBase,
                "document manage access denied");
    }

    /**
     * 要求当前用户可读取指定文档。
     *
     * <p>文档级 {@link DocumentPermission#DOC_DENY} 拥有最高优先级——
     * 一旦存在 DENY 覆盖，即使有知识库读取权限也会被拒绝。
     * 文档无显式允许时回退检查知识库读取权限。
     *
     * @param documentId 文档标识
     * @param kbId       文档所属知识库标识
     * @throws AccessDeniedException 权限不足时抛出
     */
    public void requireCanReadDocument(String documentId, String kbId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        // 委托文档级权限校验，文档匹配器为 DOC_ALLOW_MANAGE / DOC_ALLOW_READ，回退匹配器为 KB_READER 及以上
        requireDocumentAccess(
                user,
                documentId,
                kbId,
                AuthorizationService::canReadDocument,
                AuthorizationService::canReadKnowledgeBase,
                "document read access denied");
    }

    /**
     * 文档级权限校验核心骨架（三级判定）。
     *
     * <p>判定顺序严格按优先级递减：
     * <ol>
     *   <li><strong>工作区级放行：</strong>OWNER / ADMIN 直接通过，
     *       不查询数据库；</li>
     *   <li><strong>DOC_DENY 检查：</strong>查询文档权限表，
     *       若存在 DOC_DENY 记录，立即拒绝（最高优先级）；</li>
     *   <li><strong>文档显式允许：</strong>若存在匹配的 DOC_ALLOW_* 记录，放行；</li>
     *   <li><strong>知识库回退：</strong>以上均不满足时，回退到知识库级角色校验。</li>
     * </ol>
     *
     * @param user                      当前用户
     * @param documentId                文档标识
     * @param kbId                      文档所属知识库标识
     * @param documentPermissionMatcher 文档权限匹配器（如 {@code canReadDocument}）
     * @param knowledgeBaseRoleMatcher  知识库角色匹配器（如 {@code canReadKnowledgeBase}）
     * @param deniedMessage             拒绝时的异常消息
     * @throws AccessDeniedException 任一判定失败时抛出
     */
    private void requireDocumentAccess(
            CurrentUser user,
            String documentId,
            String kbId,
            Predicate<DocumentPermission> documentPermissionMatcher,
            Predicate<KnowledgeBaseRole> knowledgeBaseRoleMatcher,
            String deniedMessage) {
        // 第一级：工作区 OWNER / ADMIN 拥有全量权限，直接放行，无需查询数据库
        if (hasWorkspaceWideAccess(user.workspaceRole())) {
            return;
        }

        // 第二级：查询文档权限覆盖
        Optional<DocumentPermission> documentPermission = grantRepository.findDocumentPermission(
                user.workspaceId(),
                documentId,
                user.userId());
        // DOC_DENY 为最高优先级拒绝——一旦存在，立即抛出异常，不继续后续判定
        if (documentPermission.filter(DocumentPermission.DOC_DENY::equals).isPresent()) {
            throw new AccessDeniedException(deniedMessage);
        }
        // 文档存在显式允许的权限覆盖，且匹配当前操作，直接放行
        if (documentPermission.filter(documentPermissionMatcher).isPresent()) {
            return;
        }

        // 第三级：文档无显式授权 → 回退到知识库级角色校验
        requireKnowledgeBaseAccess(user, kbId, knowledgeBaseRoleMatcher, deniedMessage);
    }

    /**
     * 知识库级权限校验核心骨架（二级判定）。
     *
     * <p>判定顺序：
     * <ol>
     *   <li><strong>工作区级放行：</strong>OWNER / ADMIN 直接通过；</li>
     *   <li><strong>知识库角色匹配：</strong>查询知识库授权表，
     *       若存在 ACTIVE 授权且角色满足匹配器条件，放行；
     *       否则抛出 {@link AccessDeniedException}。</li>
     * </ol>
     *
     * @param user         当前用户
     * @param kbId         知识库标识
     * @param roleMatcher  角色匹配器
     * @param deniedMessage 拒绝时的异常消息
     * @throws AccessDeniedException 权限不足时抛出
     */
    private void requireKnowledgeBaseAccess(
            CurrentUser user,
            String kbId,
            Predicate<KnowledgeBaseRole> roleMatcher,
            String deniedMessage) {
        // 工作区 OWNER / ADMIN 直接放行
        if (hasWorkspaceWideAccess(user.workspaceRole())) {
            return;
        }

        // 查询知识库授权表，检查是否存在 ACTIVE 且角色匹配的授权
        boolean granted = grantRepository.findKnowledgeBaseRole(user.workspaceId(), kbId, user.userId())
                .filter(roleMatcher)
                .isPresent();
        // 无匹配授权则拒绝
        if (!granted) {
            throw new AccessDeniedException(deniedMessage);
        }
    }

    /**
     * 判断工作区角色是否为全量权限角色。
     *
     * <p>OWNER（所有者）和 ADMIN（管理员）拥有工作区内所有资源的
     * 完整访问权，无需逐条查询知识库或文档授权表。
     *
     * @param role 工作区角色
     * @return {@code true} 拥有全量工作区权限
     */
    private static boolean hasWorkspaceWideAccess(WorkspaceRole role) {
        return role == WorkspaceRole.WORKSPACE_OWNER || role == WorkspaceRole.WORKSPACE_ADMIN;
    }

    /**
     * 判断知识库角色是否具有管理权限。
     *
     * <p>仅 {@link KnowledgeBaseRole#KB_MANAGER} 可管理知识库
     * （增删改成员、修改设置等）。
     *
     * @param role 知识库角色
     * @return {@code true} 可管理知识库
     */
    private static boolean canManageKnowledgeBase(KnowledgeBaseRole role) {
        return role == KnowledgeBaseRole.KB_MANAGER;
    }

    /**
     * 判断知识库角色是否具有内容贡献权限。
     *
     * <p>{@link KnowledgeBaseRole#KB_MANAGER} 和
     * {@link KnowledgeBaseRole#KB_CONTRIBUTOR} 均可上传和编辑文档。
     *
     * @param role 知识库角色
     * @return {@code true} 可贡献内容
     */
    private static boolean canContributeKnowledgeBase(KnowledgeBaseRole role) {
        return role == KnowledgeBaseRole.KB_MANAGER || role == KnowledgeBaseRole.KB_CONTRIBUTOR;
    }

    /**
     * 判断知识库角色是否具有读取权限。
     *
     * <p>{@link KnowledgeBaseRole#KB_MANAGER}、
     * {@link KnowledgeBaseRole#KB_CONTRIBUTOR} 和
     * {@link KnowledgeBaseRole#KB_READER} 均可读取知识库内容。
     *
     * @param role 知识库角色
     * @return {@code true} 可读取知识库
     */
    private static boolean canReadKnowledgeBase(KnowledgeBaseRole role) {
        return role == KnowledgeBaseRole.KB_MANAGER
                || role == KnowledgeBaseRole.KB_CONTRIBUTOR
                || role == KnowledgeBaseRole.KB_READER;
    }

    /**
     * 判断知识库角色是否具有问答权限。
     *
     * <p>所有知识库角色（KB_MANAGER / KB_CONTRIBUTOR / KB_READER /
     * KB_ASKER）均可向知识库提问。KB_ASKER 是权限最低的角色，
     * 只能提问不能查看原始文档。
     *
     * @param role 知识库角色
     * @return {@code true} 可向知识库提问
     */
    private static boolean canAskKnowledgeBase(KnowledgeBaseRole role) {
        return role == KnowledgeBaseRole.KB_MANAGER
                || role == KnowledgeBaseRole.KB_CONTRIBUTOR
                || role == KnowledgeBaseRole.KB_READER
                || role == KnowledgeBaseRole.KB_ASKER;
    }

    /**
     * 判断文档权限是否允许管理文档。
     *
     * <p>仅 {@link DocumentPermission#DOC_ALLOW_MANAGE} 允许
     * 对文档执行管理操作（删除、移动、修改权限等）。
     *
     * @param permission 文档权限枚举
     * @return {@code true} 可管理文档
     */
    private static boolean canManageDocument(DocumentPermission permission) {
        return permission == DocumentPermission.DOC_ALLOW_MANAGE;
    }

    /**
     * 判断文档权限是否允许读取文档。
     *
     * <p>{@link DocumentPermission#DOC_ALLOW_MANAGE} 和
     * {@link DocumentPermission#DOC_ALLOW_READ} 均可读取文档内容。
     *
     * @param permission 文档权限枚举
     * @return {@code true} 可读取文档
     */
    private static boolean canReadDocument(DocumentPermission permission) {
        return permission == DocumentPermission.DOC_ALLOW_MANAGE
                || permission == DocumentPermission.DOC_ALLOW_READ;
    }
}
