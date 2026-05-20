package io.github.spike.myai.ingest.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.domain.model.AuditEvent;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ingest 审计事件工厂。
 *
 * <p>该类集中构造文档上传、上传新版本、版本回退、删除与重处理的审计事件，
 * 保证事件类型、目标类型和扩展元数据字段命名稳定。
 */
final class IngestAuditEvents {

    /** 文档上传请求审计事件类型 */
    static final String DOCUMENT_UPLOAD_REQUESTED = "DOCUMENT_UPLOAD_REQUESTED";
    /** 上传新版本请求审计事件类型 */
    static final String DOCUMENT_VERSION_UPLOAD_REQUESTED = "DOCUMENT_VERSION_UPLOAD_REQUESTED";
    /** 版本回退请求审计事件类型 */
    static final String DOCUMENT_VERSION_ROLLBACK_REQUESTED = "DOCUMENT_VERSION_ROLLBACK_REQUESTED";
    /** 文档删除请求审计事件类型 */
    static final String DOCUMENT_DELETE_REQUESTED = "DOCUMENT_DELETE_REQUESTED";
    /** 文档重处理请求审计事件类型 */
    static final String DOCUMENT_REPROCESS_REQUESTED = "DOCUMENT_REPROCESS_REQUESTED";

    /** 文档目标类型 */
    private static final String TARGET_DOCUMENT = "DOCUMENT";
    /** 上传请求目标类型 */
    private static final String TARGET_DOCUMENT_UPLOAD = "DOCUMENT_UPLOAD";
    /** 文档版本目标类型 */
    private static final String TARGET_DOCUMENT_VERSION = "DOCUMENT_VERSION";
    /** 成功结果 */
    private static final String OUTCOME_SUCCESS = "SUCCESS";
    /** 失败结果 */
    private static final String OUTCOME_FAILURE = "FAILURE";
    /** 成功原因占位 */
    private static final String EMPTY_REASON = "";
    /** JSON 序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private IngestAuditEvents() {
    }

    /**
     * 构造文档上传请求审计事件。
     *
     * @param currentUser 当前用户
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @param versionNumber 版本号
     * @param filename 文件名
     * @param fileSize 文件大小
     * @param fileHash 文件哈希
     * @param versionResultType 版本结果类型
     * @param expectedLatestVersionNumber 请求期望的 latest 版本号
     * @param observedLatestVersionNumber 服务端校验时观察到的 latest 版本号
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    static AuditEvent documentUploadRequested(
            CurrentUser currentUser,
            DocumentId documentId,
            String kbId,
            int versionNumber,
            String filename,
            long fileSize,
            String fileHash,
            String versionResultType,
            Instant occurredAt) {
        Map<String, Object> metadata = baseDocumentMetadata(documentId, kbId);
        metadata.put("versionNumber", versionNumber);
        metadata.put("filename", filename);
        metadata.put("fileSize", fileSize);
        metadata.put("fileHash", fileHash);
        metadata.put("versionOriginType", "UPLOAD");
        metadata.put("versionResultType", versionResultType);
        return success(currentUser, DOCUMENT_UPLOAD_REQUESTED, TARGET_DOCUMENT, documentId.value(), metadata, occurredAt);
    }

    /**
     * 构造文档上传受理失败审计事件。
     *
     * @param currentUser 当前用户
     * @param documentId 文档资产 ID，业务校验失败时可为空
     * @param kbId 知识库 ID
     * @param filename 文件名
     * @param fileSize 文件大小
     * @param fileHash 文件哈希
     * @param failureCategory 失败分类
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    static AuditEvent documentUploadFailed(
            CurrentUser currentUser,
            DocumentId documentId,
            String kbId,
            String filename,
            long fileSize,
            String fileHash,
            String failureCategory,
            String errorCode,
            String errorMessage,
            Instant occurredAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "documentId", documentId == null ? null : documentId.value());
        metadata.put("kbId", kbId);
        metadata.put("filename", filename);
        metadata.put("fileSize", fileSize);
        metadata.put("fileHash", fileHash);
        metadata.put("versionOriginType", "UPLOAD");
        metadata.put("versionResultType", "FAILED");
        metadata.put("failureCategory", failureCategory);
        metadata.put("errorCode", errorCode);
        metadata.put("errorMessage", errorMessage);
        String targetType = documentId == null ? TARGET_DOCUMENT_UPLOAD : TARGET_DOCUMENT;
        String targetId = documentId == null ? fileHash : documentId.value();
        return failure(currentUser, DOCUMENT_UPLOAD_REQUESTED, targetType, targetId, failureCategory, metadata, occurredAt);
    }

    /**
     * 构造上传新版本请求审计事件。
     *
     * @param currentUser 当前用户
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @param versionNumber 新版本号；复用场景为当前 latest 版本号
     * @param previousVersionNumber 上传前的 latest 版本号
     * @param filename 文件名
     * @param fileSize 文件大小
     * @param fileHash 文件哈希
     * @param versionResultType 版本结果类型
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    static AuditEvent documentVersionUploadRequested(
            CurrentUser currentUser,
            DocumentId documentId,
            String kbId,
            int versionNumber,
            int previousVersionNumber,
            String filename,
            long fileSize,
            String fileHash,
            String versionResultType,
            int expectedLatestVersionNumber,
            int observedLatestVersionNumber,
            Instant occurredAt) {
        Map<String, Object> metadata = baseDocumentMetadata(documentId, kbId);
        metadata.put("versionNumber", versionNumber);
        metadata.put("previousVersionNumber", previousVersionNumber);
        metadata.put("expectedLatestVersionNumber", expectedLatestVersionNumber);
        metadata.put("observedLatestVersionNumber", observedLatestVersionNumber);
        metadata.put("filename", filename);
        metadata.put("fileSize", fileSize);
        metadata.put("fileHash", fileHash);
        metadata.put("versionOriginType", "UPLOAD");
        metadata.put("versionResultType", versionResultType);
        return success(
                currentUser,
                DOCUMENT_VERSION_UPLOAD_REQUESTED,
                TARGET_DOCUMENT_VERSION,
                versionTargetId(documentId, versionNumber),
                metadata,
                occurredAt);
    }

    /**
     * 构造版本回退请求审计事件。
     *
     * @param currentUser 当前用户
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @param versionNumber 回退生成的新版本号
     * @param targetVersionNumber 回退目标版本号
     * @param expectedLatestVersionNumber 请求期望的 latest 版本号
     * @param observedLatestVersionNumber 服务端校验时观察到的 latest 版本号
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    static AuditEvent documentVersionRollbackRequested(
            CurrentUser currentUser,
            DocumentId documentId,
            String kbId,
            int versionNumber,
            int targetVersionNumber,
            int expectedLatestVersionNumber,
            int observedLatestVersionNumber,
            Instant occurredAt) {
        Map<String, Object> metadata = baseDocumentMetadata(documentId, kbId);
        metadata.put("versionNumber", versionNumber);
        metadata.put("targetVersionNumber", targetVersionNumber);
        metadata.put("expectedLatestVersionNumber", expectedLatestVersionNumber);
        metadata.put("observedLatestVersionNumber", observedLatestVersionNumber);
        metadata.put("latestVersionNumber", observedLatestVersionNumber);
        metadata.put("versionOriginType", "ROLLBACK");
        metadata.put("versionResultType", "CREATED");
        return success(
                currentUser,
                DOCUMENT_VERSION_ROLLBACK_REQUESTED,
                TARGET_DOCUMENT_VERSION,
                versionTargetId(documentId, versionNumber),
                metadata,
                occurredAt);
    }

    /**
     * 构造文档版本治理失败审计事件。
     *
     * @param currentUser 当前用户
     * @param eventType 版本治理事件类型
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @param versionNumber 相关版本号
     * @param targetVersionNumber 目标版本号，非回退场景可为空
     * @param expectedLatestVersionNumber 请求期望的 latest 版本号
     * @param observedLatestVersionNumber 服务端观察到的 latest 版本号
     * @param versionOriginType 版本来源类型
     * @param versionResultType 版本结果类型
     * @param errorCode 业务错误码
     * @param errorMessage 服务端错误消息
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    static AuditEvent documentVersionGovernanceFailed(
            CurrentUser currentUser,
            String eventType,
            DocumentId documentId,
            String kbId,
            Integer versionNumber,
            Integer targetVersionNumber,
            Integer expectedLatestVersionNumber,
            int observedLatestVersionNumber,
            String versionOriginType,
            String versionResultType,
            String errorCode,
            String errorMessage,
            Instant occurredAt) {
        Map<String, Object> metadata = baseDocumentMetadata(documentId, kbId);
        putIfPresent(metadata, "versionNumber", versionNumber);
        putIfPresent(metadata, "targetVersionNumber", targetVersionNumber);
        putIfPresent(metadata, "expectedLatestVersionNumber", expectedLatestVersionNumber);
        metadata.put("observedLatestVersionNumber", observedLatestVersionNumber);
        metadata.put("latestVersionNumber", observedLatestVersionNumber);
        metadata.put("versionOriginType", versionOriginType);
        metadata.put("versionResultType", versionResultType);
        metadata.put("errorCode", errorCode);
        metadata.put("errorMessage", errorMessage);
        String targetId = versionNumber == null ? documentId.value() : versionTargetId(documentId, versionNumber);
        return failure(
                currentUser,
                eventType,
                TARGET_DOCUMENT_VERSION,
                targetId,
                errorCode,
                metadata,
                occurredAt);
    }

    /**
     * 构造文档级治理成功审计事件。
     *
     * @param currentUser 当前用户
     * @param eventType 治理事件类型
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @param versionNumber 当前 latest 版本号
     * @param expectedLatestVersionNumber 请求期望的 latest 版本号，可为空
     * @param resultType 治理结果类型
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    static AuditEvent documentGovernanceSucceeded(
            CurrentUser currentUser,
            String eventType,
            DocumentId documentId,
            String kbId,
            int versionNumber,
            Integer expectedLatestVersionNumber,
            String resultType,
            Instant occurredAt) {
        Map<String, Object> metadata = baseDocumentMetadata(documentId, kbId);
        metadata.put("versionNumber", versionNumber);
        putIfPresent(metadata, "expectedLatestVersionNumber", expectedLatestVersionNumber);
        metadata.put("observedLatestVersionNumber", versionNumber);
        metadata.put("latestVersionNumber", versionNumber);
        metadata.put("versionResultType", resultType);
        return success(currentUser, eventType, TARGET_DOCUMENT, documentId.value(), metadata, occurredAt);
    }

    /**
     * 构造文档级治理失败审计事件。
     *
     * @param currentUser 当前用户
     * @param eventType 治理事件类型
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @param versionNumber 当前 latest 版本号
     * @param expectedLatestVersionNumber 请求期望的 latest 版本号，可为空
     * @param resultType 治理结果类型
     * @param errorCode 业务错误码
     * @param errorMessage 服务端错误消息
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    static AuditEvent documentGovernanceFailed(
            CurrentUser currentUser,
            String eventType,
            DocumentId documentId,
            String kbId,
            int versionNumber,
            Integer expectedLatestVersionNumber,
            String resultType,
            String errorCode,
            String errorMessage,
            Instant occurredAt) {
        Map<String, Object> metadata = baseDocumentMetadata(documentId, kbId);
        metadata.put("versionNumber", versionNumber);
        putIfPresent(metadata, "expectedLatestVersionNumber", expectedLatestVersionNumber);
        metadata.put("observedLatestVersionNumber", versionNumber);
        metadata.put("latestVersionNumber", versionNumber);
        metadata.put("versionResultType", resultType);
        metadata.put("errorCode", errorCode);
        metadata.put("errorMessage", errorMessage);
        return failure(currentUser, eventType, TARGET_DOCUMENT, documentId.value(), errorCode, metadata, occurredAt);
    }

    /**
     * 构造成功审计事件。
     *
     * @param currentUser 当前用户
     * @param eventType 事件类型
     * @param targetType 目标类型
     * @param targetId 目标 ID
     * @param metadata 扩展元数据
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    private static AuditEvent success(
            CurrentUser currentUser,
            String eventType,
            String targetType,
            String targetId,
            Map<String, Object> metadata,
            Instant occurredAt) {
        return new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                eventType,
                targetType,
                targetId,
                OUTCOME_SUCCESS,
                EMPTY_REASON,
                toJson(metadata),
                occurredAt);
    }

    /**
     * 构造失败审计事件。
     *
     * @param currentUser 当前用户
     * @param eventType 事件类型
     * @param targetType 目标类型
     * @param targetId 目标 ID
     * @param reason 失败原因
     * @param metadata 扩展元数据
     * @param occurredAt 事件发生时间
     * @return 审计事件
     */
    private static AuditEvent failure(
            CurrentUser currentUser,
            String eventType,
            String targetType,
            String targetId,
            String reason,
            Map<String, Object> metadata,
            Instant occurredAt) {
        return new AuditEvent(
                currentUser.workspaceId(),
                currentUser.userId(),
                currentUser.username(),
                eventType,
                targetType,
                targetId,
                OUTCOME_FAILURE,
                reason,
                toJson(metadata),
                occurredAt);
    }

    /**
     * 构造文档级基础元数据。
     *
     * @param documentId 文档资产 ID
     * @param kbId 知识库 ID
     * @return 元数据映射
     */
    private static Map<String, Object> baseDocumentMetadata(DocumentId documentId, String kbId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentId", documentId.value());
        metadata.put("kbId", kbId);
        return metadata;
    }

    /**
     * 构造文档版本目标 ID。
     *
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @return 文档版本目标 ID
     */
    private static String versionTargetId(DocumentId documentId, int versionNumber) {
        return documentId.value() + ":" + versionNumber;
    }

    /**
     * 非空值才写入元数据。
     *
     * @param metadata 元数据映射
     * @param key 字段名
     * @param value 字段值
     */
    private static void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    /**
     * 将元数据序列化为 JSON。
     *
     * @param metadata 元数据映射
     * @return JSON 字符串
     */
    private static String toJson(Map<String, Object> metadata) {
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("audit metadata json serialization failed", ex);
        }
    }
}
