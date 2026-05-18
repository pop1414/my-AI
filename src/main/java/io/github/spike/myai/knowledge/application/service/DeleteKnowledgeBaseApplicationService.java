package io.github.spike.myai.knowledge.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.auth.domain.port.AuditEventRepository;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.application.usecase.DeleteKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 删除知识库应用服务。
 *
 * <p>该服务负责将知识库标记为 {@link KnowledgeBaseStatus#DELETED}，
 * 并写入删除审计。删除后知识库默认从列表隐藏，普通业务查询不会再返回该知识库。
 */
@Service
public class DeleteKnowledgeBaseApplicationService implements DeleteKnowledgeBaseUseCase {

    /** 授权服务，用于校验工作区管理权限 */
    private final AuthorizationService authorizationService;

    /** 知识库仓储，用于读取和保存知识库聚合根 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /** 审计事件仓储，用于记录知识库删除操作 */
    private final AuditEventRepository auditEventRepository;

    /**
     * 构造删除知识库应用服务。
     *
     * @param authorizationService    授权服务
     * @param knowledgeBaseRepository 知识库仓储
     * @param auditEventRepository    审计事件仓储
     */
    public DeleteKnowledgeBaseApplicationService(
            AuthorizationService authorizationService,
            KnowledgeBaseRepository knowledgeBaseRepository,
            AuditEventRepository auditEventRepository) {
        this.authorizationService = authorizationService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 执行知识库软删除。
     *
     * <p>删除知识库属于工作区级治理操作，仅工作区 OWNER / ADMIN 可执行。
     * 已经处于 {@code DELETED} 的知识库按幂等成功处理，不重复写审计。
     *
     * @param kbId 知识库业务键
     * @throws KnowledgeBaseNotFoundException 当知识库不存在时抛出
     */
    @Override
    public void handle(String kbId) {
        String normalizedKbId = requireKbId(kbId);
        CurrentUser currentUser = authorizationService.requireCanManageWorkspace();
        KnowledgeBase current = knowledgeBaseRepository
                .findByKbIdIncludingDeleted(currentUser.workspaceId(), normalizedKbId)
                .orElseThrow(() -> new KnowledgeBaseNotFoundException("knowledge base not found: " + normalizedKbId));

        if (current.status() == KnowledgeBaseStatus.DELETED) {
            return;
        }

        Instant now = Instant.now();
        KnowledgeBase deleted = current.update(
                current.name(),
                current.description(),
                KnowledgeBaseStatus.DELETED,
                now);
        knowledgeBaseRepository.save(deleted);

        auditEventRepository.save(new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                "KNOWLEDGE_BASE_DELETED",
                "KNOWLEDGE_BASE",
                current.kbId(),
                "SUCCESS",
                "",
                """
                {"kbId":%s,"name":%s,"previousStatus":%s}
                """.formatted(
                        toJsonString(current.kbId()),
                        toJsonString(current.name()),
                        toJsonString(current.status().name())),
                now));
    }

    /**
     * 校验并规整知识库 ID。
     *
     * @param kbId 原始知识库 ID
     * @return 去除首尾空格后的知识库 ID
     */
    private static String requireKbId(String kbId) {
        if (kbId == null || kbId.isBlank()) {
            throw new IllegalArgumentException("kbId must not be blank");
        }
        return kbId.trim();
    }

    /**
     * 将字符串包装为 JSON 字符串值。
     *
     * @param value 原始字符串
     * @return 已转义的 JSON 字符串值
     */
    private static String toJsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
