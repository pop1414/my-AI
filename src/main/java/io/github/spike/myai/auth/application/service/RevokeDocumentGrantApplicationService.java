package io.github.spike.myai.auth.application.service;

import io.github.spike.myai.auth.application.command.RevokeDocumentGrantCommand;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.exception.DocumentGrantNotFoundException;
import io.github.spike.myai.auth.application.exception.ManagedDocumentNotFoundException;
import io.github.spike.myai.auth.application.usecase.RevokeDocumentGrantUseCase;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.model.DocumentGrant;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.auth.domain.port.DocumentGrantManagementRepository;
import io.github.spike.myai.ingest.domain.model.Document;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 回收文档授权应用服务。
 * <p>
 * 实现 {@link RevokeDocumentGrantUseCase} 用例，核心职责包括：
 * <ol>
 *   <li>权限校验：确保调用方具备工作区管理权限</li>
 *   <li>文档存在性校验：确认目标文档在当前工作区中存在</li>
 *   <li>授权查找：定位目标活跃授权记录</li>
 *   <li>软删除执行：将授权状态从 {@code ACTIVE} 变更为 {@code DISABLED}</li>
 *   <li>审计追踪：记录回收操作的审计事件及被回收权限的元数据</li>
 * </ol>
 *
 * @author spike
 * @since 1.0.0
 */
@Service
public class RevokeDocumentGrantApplicationService implements RevokeDocumentGrantUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;
    /** 文档仓储，用于校验文档存在性 */
    private final DocumentRepository documentRepository;
    /** 文档授权治理仓储 */
    private final DocumentGrantManagementRepository grantRepository;
    /** 审计事件持久化仓储 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造器注入所需依赖。
     *
     * @param authorizationService 授权服务
     * @param documentRepository   文档仓储
     * @param grantRepository      文档授权治理仓储
     * @param auditEventRepository 审计事件仓储
     */
    public RevokeDocumentGrantApplicationService(
            AuthorizationService authorizationService,
            DocumentRepository documentRepository,
            DocumentGrantManagementRepository grantRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.documentRepository = documentRepository;
        this.grantRepository = grantRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行回收文档授权用例。
     * <p>
     * 完整处理流程如下：
     * <ol>
     *   <li>校验当前用户是否具备工作区管理权限</li>
     *   <li>加载目标文档（含 kbId 等关联信息）</li>
     *   <li>查找目标活跃授权记录，不存在则快速失败</li>
     *   <li>执行软删除（状态变更为 DISABLED）</li>
     *   <li>若更新失败（并发场景下记录已被回收），抛出异常</li>
     *   <li>记录审计事件，持久化被回收授权的元数据（含文档 ID 和所属知识库 ID）</li>
     * </ol>
     *
     * @param command 回收命令，包含文档 ID 和目标用户 ID
     * @throws DocumentGrantNotFoundException     当目标授权不存在或已被回收时抛出
     * @throws ManagedDocumentNotFoundException   当文档不存在时抛出
     */
    @Override
    public void handle(RevokeDocumentGrantCommand command) {
        // Step 1: 校验当前用户的工作区管理权限
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();

        // Step 2: 加载目标文档，确认存在并获取关联信息（如 kbId）
        Document document = documentRepository.findById(
                        currentUser.workspaceId(),
                        new DocumentId(command.normalizedDocumentId()))
                .orElseThrow(() -> new ManagedDocumentNotFoundException(
                        "document not found: " + command.normalizedDocumentId()));

        // Step 3: 查找目标活跃授权记录，不存在则快速失败
        DocumentGrant existingGrant = grantRepository.findActiveGrant(
                        currentUser.workspaceId(),
                        command.normalizedDocumentId(),
                        command.normalizedUserId())
                .orElseThrow(() -> new DocumentGrantNotFoundException(
                        "document grant not found: " + command.normalizedDocumentId() + "/" + command.normalizedUserId()));

        // Step 4: 获取当前时间戳
        Instant now = Instant.now();

        // Step 5: 执行软删除
        boolean updated = grantRepository.disableGrant(
                currentUser.workspaceId(),
                command.normalizedDocumentId(),
                command.normalizedUserId(),
                now);
        // 更新失败（如并发回收），抛出异常
        if (!updated) {
            throw new DocumentGrantNotFoundException(
                    "document grant not found: " + command.normalizedDocumentId() + "/" + command.normalizedUserId());
        }

        // Step 6: 持久化审计事件，含文档 ID 和所属知识库 ID
        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "DOCUMENT_GRANT_REVOKED",
                "DOCUMENT_GRANT",
                command.normalizedDocumentId() + ":" + command.normalizedUserId(),
                "SUCCESS",
                "",
                buildRevokeMetadata(document, existingGrant),
                now));
    }

    /**
     * 构建回收文档授权审计元数据 JSON 字符串。
     * <p>
     * 包含文档 ID、所属知识库 ID、被回收用户 ID、用户名及被回收权限五个字段，
     * 各字段值经过 JSON 字符串转义处理。
     *
     * @param document      目标文档对象
     * @param existingGrant 回收前的授权记录
     * @return JSON 格式的元数据字符串
     */
    private static String buildRevokeMetadata(Document document, DocumentGrant existingGrant) {
        return """
                {"documentId":%s,"kbId":%s,"targetUserId":%s,"targetUsername":%s,"revokedPermission":%s}
                """.formatted(
                toJsonString(document.documentId().value()),
                toJsonString(document.kbId()),
                toJsonString(existingGrant.userId()),
                toJsonString(existingGrant.username()),
                toJsonString(existingGrant.permission().name()));
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
        // 先转义反斜杠，再转义双引号（顺序不可颠倒，否则会产生多余转义）
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }
}
