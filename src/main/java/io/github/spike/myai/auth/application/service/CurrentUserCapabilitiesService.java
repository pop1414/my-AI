package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.result.CurrentUserResult;
import io.github.spike.myai.auth.application.result.CurrentUserCapabilitiesResult;
import io.github.spike.myai.auth.application.usecase.GetCurrentUserCapabilitiesUseCase;
import io.github.spike.myai.auth.domain.model.KnowledgeBaseRole;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuthorizationGrantRepository;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 当前用户能力位解析服务。
 *
 * <p>根据用户的角色和知识库授权 grant 信息，动态解析前端菜单渲染
 * 所需的能力位（capabilities）。能力位决定了用户可见的一级菜单、
 * 登录后的默认落点以及受限访问时的提示页。
 *
 * <h3>能力位解析规则</h3>
 * <ul>
 *   <li><strong>管理员（OWNER / ADMIN）：</strong>拥有全部五项能力位；</li>
 *   <li><strong>普通成员（MEMBER）：</strong>根据知识库授权角色判定：
 *     <ul>
 *       <li>{@code KB_MANAGER} / {@code KB_CONTRIBUTOR}：可列表、上传、知识库、问答</li>
 *       <li>{@code KB_READER}：可列表、知识库、问答（不可上传）</li>
 *       <li>{@code KB_ASKER}：仅知识库和问答（不可列表、上传）</li>
 *       <li>无任何 grant：全部能力位为 false</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * @see CurrentUserCapabilitiesResult
 * @see AuthorizationGrantRepository#listGrantedKnowledgeBaseRoles
 */
@Service
public class CurrentUserCapabilitiesService implements GetCurrentUserCapabilitiesUseCase {

    private final AuthorizationGrantRepository authorizationGrantRepository;

    /**
     * 构造函数注入授权仓储。
     *
     * @param authorizationGrantRepository 授权 grant 仓储
     */
    public CurrentUserCapabilitiesService(AuthorizationGrantRepository authorizationGrantRepository) {
        this.authorizationGrantRepository = authorizationGrantRepository;
    }

    /**
     * 从登录结果解析当前用户能力位。
     *
     * <p>便捷方法，从 {@link CurrentUserResult} 中提取所需参数后
     * 委托给 {@link #resolve(String, String, WorkspaceRole)}。
     *
     * @param result 登录用例返回的用户结果
     * @return 当前用户能力位
     */
    public CurrentUserCapabilitiesResult resolve(CurrentUserResult result) {
        return resolve(result.userId(), result.workspaceId(), result.workspaceRole());
    }

    /**
     * 根据用户身份和授权信息解析能力位。
     *
     * <p>核心解析逻辑，按以下优先级判定：
     * <ol>
     *   <li>若为 OWNER 或 ADMIN，直接返回全部能力位为 true；</li>
     *   <li>否则查询用户的 ACTIVE 知识库授权角色集合；</li>
     *   <li>按角色粒度逐项判定每个能力位的开关状态。</li>
     * </ol>
     *
     * @param userId        用户唯一标识
     * @param workspaceId   工作区 ID
     * @param workspaceRole 工作区角色
     * @return 当前用户能力位
     */
    public CurrentUserCapabilitiesResult resolve(String userId, String workspaceId, WorkspaceRole workspaceRole) {
        // 管理员（OWNER / ADMIN）拥有全部能力位，无需查询 grant 表
        if (workspaceRole == WorkspaceRole.WORKSPACE_OWNER || workspaceRole == WorkspaceRole.WORKSPACE_ADMIN) {
            return new CurrentUserCapabilitiesResult(true, true, true, true, true);
        }

        // 查询普通成员在当前工作区的 ACTIVE 知识库授权角色集合
        Set<KnowledgeBaseRole> roles = authorizationGrantRepository.listGrantedKnowledgeBaseRoles(workspaceId, userId);

        // 存在任意授权即可访问知识库和问答
        boolean hasAnyGrant = !roles.isEmpty();

        // 文档列表权限：MANAGER、CONTRIBUTOR、READER 均可查看文档列表
        boolean canAccessDocumentList = roles.contains(KnowledgeBaseRole.KB_MANAGER)
                || roles.contains(KnowledgeBaseRole.KB_CONTRIBUTOR)
                || roles.contains(KnowledgeBaseRole.KB_READER);

        // 文档上传权限：仅 MANAGER 和 CONTRIBUTOR 可上传
        boolean canUploadDocument = roles.contains(KnowledgeBaseRole.KB_MANAGER)
                || roles.contains(KnowledgeBaseRole.KB_CONTRIBUTOR);

        // 普通成员永远不具备管理后台访问权限
        return new CurrentUserCapabilitiesResult(
                canAccessDocumentList,
                canUploadDocument,
                hasAnyGrant,
                hasAnyGrant,
                false);
    }
}
