package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.UpsertDocumentGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.exception.WorkspaceMemberNotFoundException;
import io.github.spike.myai.auth.application.result.DocumentGrantResult;
import io.github.spike.myai.auth.application.usecase.UpsertDocumentGrantUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.DocumentGrant;
import io.github.spike.myai.auth.domain.model.DocumentPermission;
import io.github.spike.myai.auth.domain.model.WorkspaceMember;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.auth.domain.port.WorkspaceMemberRepository;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 授予或更新文档授权应用服务。
 * <p>
 * 实现 {@link UpsertDocumentGrantUseCase} 用例，核心职责包括：
 * <ol>
 *   <li>权限校验：确保调用方具备工作区管理权限</li>
 *   <li>文档存在性校验：确认目标文档在当前工作区中存在</li>
 *   <li>成员校验：确认目标用户为当前工作区的活跃成员</li>
 *   <li>幂等处理：若已存在授权且权限未变更，直接返回</li>
 *   <li>Upsert 执行：通过仓储层执行插入或更新（INSERT ON CONFLICT DO UPDATE）</li>
 *   <li>审计追踪：记录授权变更的审计事件，包含变更前后权限及所属知识库元数据</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class UpsertDocumentGrantApplicationService implements UpsertDocumentGrantUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    /** 文档仓储，用于校验文档存在性并获取关联信息 */
    private final DocumentRepository documentRepository;
    /** 工作区成员仓储，用于校验目标用户是否为活跃成员 */
    private final WorkspaceMemberRepository workspaceMemberRepository;
    /** 文档授权治理仓储 */
    private final DocumentGrantManagementRepository grantRepository;
    /** 审计事件持久化仓储 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService      授权服务
     * @param documentRepository        文档仓储
     * @param workspaceMemberRepository 工作区成员仓储
     * @param grantRepository           文档授权治理仓储
     * @param auditEventRepository      审计事件仓储
     */
    public UpsertDocumentGrantApplicationService(
            AuthorizationService authorizationService,
            DocumentRepository documentRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            DocumentGrantManagementRepository grantRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.documentRepository = documentRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.grantRepository = grantRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行授予或更新文档授权用例。
     * <p>
     * 完整处理流程如下：
     * <ol>
     *   <li>校验当前用户是否具备工作区管理权限</li>
     *   <li>加载目标文档，确认存在并获取关联信息（如 kbId）</li>
     *   <li>校验目标用户是否为活跃工作区成员</li>
     *   <li>解析目标权限枚举值</li>
     *   <li>查询现有授权记录</li>
     *   <li>幂等判断：若已有授权且权限一致，直接返回</li>
     *   <li>执行 Upsert 操作（INSERT ON CONFLICT DO UPDATE）</li>
     *   <li>记录审计事件（含文档所属知识库 ID）</li>
     *   <li>返回包含新权限信息的结果对象</li>
     * </ol>
     *
     * @param command 授权命令，包含文档 ID、用户 ID 和目标权限
     * @return 操作后的授权结果
     * @throws WorkspaceMemberNotFoundException   当目标用户不是活跃工作区成员时抛出
     * @throws ManagedDocumentNotFoundException   当文档不存在时抛出
     * @throws IllegalArgumentException           当命令中权限无效时抛出
     */
    @Override
    public DocumentGrantResult handle(UpsertDocumentGrantCommand command) {
        // Step 1: 校验当前用户的工作区管理权限
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // Step 2: 加载目标文档，确认存在并获取关联信息（如 kbId）
        Document document = documentRepository.findById(
                        currentUser.workspaceId(),
                        new DocumentId(command.normalizedDocumentId()))
                .orElseThrow(() -> new ManagedDocumentNotFoundException(
                        "document not found: " + command.normalizedDocumentId()));

        // Step 3: 校验目标用户是否为活跃工作区成员
        WorkspaceMember member = workspaceMemberRepository.findActiveMember(
                        currentUser.workspaceId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new WorkspaceMemberNotFoundException(
                        "workspace member not found: " + command.normalizedUserId()));

        // Step 4: 解析目标权限枚举值
        DocumentPermission targetPermission = command.resolvedPermission();

        // Step 5: 查询现有授权记录（可能为空）
        Optional<DocumentGrant> existingGrant = grantRepository.findActiveGrant(
                currentUser.workspaceId(),
                command.normalizedDocumentId(),
                member.userId());

        // Step 6: 幂等检查——已有授权且权限未变更，直接返回
        if (existingGrant.filter(grant -> grant.permission() == targetPermission).isPresent()) {
            return toResult(existingGrant.get());
        }

        // Step 7: 获取当前时间戳
        Instant now = Instant.now();

        // Step 8: 执行 Upsert（存在则更新权限，不存在则插入新记录）
        grantRepository.saveGrant(
                currentUser.workspaceId(),
                command.normalizedDocumentId(),
                member.userId(),
                targetPermission,
                now);

        // Step 9: 持久化审计事件（含文档所属知识库 ID）
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "DOCUMENT_GRANT_UPSERTED",
                "DOCUMENT_GRANT",
                command.normalizedDocumentId() + ":" + member.userId(),
                "SUCCESS",
                "",
                buildUpsertMetadata(document, member, existingGrant, targetPermission),
                now));

        // Step 10: 构造并返回结果（状态固定为 ACTIVE）
        return new DocumentGrantResult(
                currentUser.workspaceId(),
                command.normalizedDocumentId(),
                member.userId(),
                member.username(),
                member.displayName(),
                targetPermission,
                "ACTIVE");
    }

    /**
     * 将领域模型 {@link DocumentGrant} 转换为用例层结果。
     *
     * @param grant 文档授权领域对象
     * @return 用例层结果对象
     */
    private static DocumentGrantResult toResult(DocumentGrant grant) {
        return new DocumentGrantResult(
                grant.workspaceId(),
                grant.documentId(),
                grant.userId(),
                grant.username(),
                grant.displayName(),
                grant.permission(),
                grant.status());
    }

    /**
     * 构建 Upsert 文档授权审计元数据 JSON 字符串。
     * <p>
     * 包含文档 ID、所属知识库 ID、目标用户 ID、用户名、变更前权限（可能为 {@code null} 表示新建）
     * 及变更后权限六个字段，各字段值经过 JSON 字符串转义处理。
     *
     * @param document        目标文档对象
     * @param member          目标工作区成员
     * @param existingGrant   变更前的授权记录（可能为空，表示新建授权）
     * @param targetPermission 目标权限
     * @return JSON 格式的元数据字符串
     */
    private static String buildUpsertMetadata(
            Document document,
            WorkspaceMember member,
            Optional<DocumentGrant> existingGrant,
            DocumentPermission targetPermission) {
        return """
                {"documentId":%s,"kbId":%s,"targetUserId":%s,"targetUsername":%s,"previousPermission":%s,"newPermission":%s}
                """.formatted(
                toJsonString(document.documentId().value()),
                toJsonString(document.kbId()),
                toJsonString(member.userId()),
                toJsonString(member.username()),
                // 若无现有授权（新建场景），previousPermission 为 null（JSON 字面量）
                existingGrant.map(DocumentGrant::permission).map(DocumentPermission::name).map(UpsertDocumentGrantApplicationService::toJsonString).orElse("null"),
                toJsonString(targetPermission.name()));
    }

    /**
     * 将字符串包装为合法的 JSON 字符串值（含双引号并转义特殊字符）。
     * <p>
     * 先转义反斜杠，再转义双引号，顺序不可颠倒。
     *
     * @param value 原始字符串值
     * @return 带双引号且已转义的 JSON 字符串值
     */
    private static String toJsonString(String value) {
        // 先转义反斜杠，再转义双引号（顺序不可颠倒）
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
