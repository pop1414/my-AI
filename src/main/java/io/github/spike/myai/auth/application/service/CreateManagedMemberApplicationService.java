package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.CreateManagedMemberCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedAccountUsernameConflictException;
import io.github.spike.myai.auth.application.result.ManagedAccountResult;
import io.github.spike.myai.auth.application.usecase.CreateManagedMemberUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.ManagedAccount;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.KnowledgeBaseGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.ManagedAccountRepository;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建工作区成员并初始化知识库授权应用服务。
 *
 * <p>实现 {@link CreateManagedMemberUseCase} 用例，提供"开户即授权"的一站式成员创建：
 * <ol>
 *   <li>权限校验：确保操作者为工作区管理员，并通过治理边界守卫</li>
 *   <li>冲突检测：检查用户名是否已被占用</li>
 *   <li>参数去重：对初始授权列表按 kbId 去重（保留首次出现的角色赋值）</li>
 *   <li>知识库校验：验证所有授权的目标知识库在当前工作区中存在</li>
 *   <li>事务持久化：
 *     <ul>
 *       <li>创建用户基础信息 + 本地凭据 + 工作区成员关系</li>
 *       <li>逐条写入初始知识库授权</li>
 *     </ul>
 *   </li>
 *   <li>审计记录：记录 MANAGE_MEMBER_PROVISIONED 审计事件</li>
 * </ol>
 *
 * <p>整个 {@code handle} 方法使用 {@link Transactional} 注解，确保
 * 用户创建与授权写入在同一数据库事务中原子完成。
 */
@Service
public class CreateManagedMemberApplicationService implements CreateManagedMemberUseCase {

    private final AuthorizationService authorizationService;
    private final WorkspaceGovernanceGuard workspaceGovernanceGuard;
    private final ManagedAccountRepository managedAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseGrantManagementRepository knowledgeBaseGrantManagementRepository;
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造函数注入依赖。
     *
     * @param authorizationService                 授权服务
     * @param workspaceGovernanceGuard             工作区治理边界守卫
     * @param managedAccountRepository             托管账号仓储
     * @param passwordEncoder                      密码编码器（BCrypt）
     * @param knowledgeBaseRepository              知识库仓储（用于校验知识库是否存在）
     * @param knowledgeBaseGrantManagementRepository 知识库授权管理仓储
     * @param auditEventRepository                 审计事件仓储
     */
    public CreateManagedMemberApplicationService(
            AuthorizationService authorizationService,
            WorkspaceGovernanceGuard workspaceGovernanceGuard,
            ManagedAccountRepository managedAccountRepository,
            PasswordEncoder passwordEncoder,
            KnowledgeBaseRepository knowledgeBaseRepository,
            KnowledgeBaseGrantManagementRepository knowledgeBaseGrantManagementRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.workspaceGovernanceGuard = workspaceGovernanceGuard;
        this.managedAccountRepository = managedAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.knowledgeBaseGrantManagementRepository = knowledgeBaseGrantManagementRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行成员创建 + 初始授权操作。
     *
     * <p>在一个事务中完成用户开户和知识库授权，保证原子性。
     *
     * @param command 创建成员命令
     * @return 创建成功的账号信息
     * @throws ManagedAccountUsernameConflictException 如果用户名已存在
     * @throws IllegalArgumentException              如果知识库不存在或授权列表为空
     */
    @Override
    @Transactional
    public ManagedAccountResult handle(CreateManagedMemberCommand command) {
        // 1. 权限校验：确保当前用户是工作区管理员
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        // 1.1 治理边界校验：确保操作者有权创建 MEMBER 角色账号（ADMIN 可创建 MEMBER）
        workspaceGovernanceGuard.requireCanCreateManagedAccount(
                currentUser,
                WorkspaceRole.WORKSPACE_MEMBER);

        // 2. 冲突检测：检查用户名是否已被占用
        if (managedAccountRepository.existsUsername(command.normalizedUsername())) {
            throw new ManagedAccountUsernameConflictException("username already exists: " + command.normalizedUsername());
        }

        // 3. 参数去重：按 kbId 去重，保留首次出现的角色赋值
        Map<String, CreateManagedMemberCommand.InitialKnowledgeBaseGrantCommand> normalizedAssignments =
                new LinkedHashMap<>();
        for (CreateManagedMemberCommand.InitialKnowledgeBaseGrantCommand assignment : command.initialKnowledgeBaseGrants()) {
            normalizedAssignments.put(assignment.normalizedKbId(), assignment);
        }
        if (normalizedAssignments.isEmpty()) {
            throw new IllegalArgumentException("initialKnowledgeBaseGrants is required");
        }

        // 4. 知识库校验：验证每个授权目标知识库在当前工作区中存在
        String workspaceId = currentUser.workspaceId();
        normalizedAssignments.values().forEach(assignment -> {
            if (knowledgeBaseRepository.findByKbId(workspaceId, assignment.normalizedKbId()).isEmpty()) {
                throw new IllegalArgumentException("knowledge base not found: " + assignment.normalizedKbId());
            }
        });

        // 5. 事务持久化：创建用户基础信息 + 本地凭据 + 工作区成员关系
        Instant now = Instant.now();
        ManagedAccount created = managedAccountRepository.createAccount(
                workspaceId,
                command.normalizedUsername(),
                command.normalizedDisplayName(),
                passwordEncoder.encode(command.normalizedPassword()),
                WorkspaceRole.WORKSPACE_MEMBER,
                now);

        // 6. 逐条写入初始知识库授权 grant
        normalizedAssignments.values().forEach(assignment ->
                knowledgeBaseGrantManagementRepository.saveGrant(
                        workspaceId,
                        assignment.normalizedKbId(),
                        created.userId(),
                        assignment.resolvedRole(),
                        now));

        // 7. 审计：记录成员开户事件，包含授权数量元数据
        auditEventRepository.save(new AuditEvent(
                workspaceId,
                currentUser.userId(),
                currentUser.username(),
                "MANAGED_MEMBER_PROVISIONED",
                "MANAGED_ACCOUNT",
                created.userId(),
                "SUCCESS",
                "",
                """
                {"targetUserId":%s,"targetUsername":%s,"initialKnowledgeBaseGrantCount":%s}
                """.formatted(
                        toJsonString(created.userId()),
                        toJsonString(created.username()),
                        String.valueOf(normalizedAssignments.size())),
                now));

        return ListManagedAccountsApplicationService.toResult(created);
    }

    /**
     * 将字符串包装为 JSON 字符串值（转义特殊字符）。
     *
     * @param value 原始字符串
     * @return JSON 字符串值（含双引号包裹）
     */
    private static String toJsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
