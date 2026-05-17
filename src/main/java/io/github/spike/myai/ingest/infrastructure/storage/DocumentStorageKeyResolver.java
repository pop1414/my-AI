package io.github.spike.myai.ingest.infrastructure.storage;

import io.github.spike.myai.ingest.domain.model.DocumentId;

/**
 * 文档对象存储逻辑 key 解析器。
 *
 * <p>该组件集中管理源文件与处理产物的 key 规则，确保二者始终使用不同 prefix。
 * 本地文件系统实现会把该逻辑 key 映射到 rootDir 下的相对路径；未来接入对象存储时，
 * 可直接复用同一套 key 规则。
 */
public class DocumentStorageKeyResolver {

    /** 源文件 prefix。 */
    public static final String SOURCE_PREFIX = "source";
    /** 处理产物 prefix。 */
    public static final String ARTIFACTS_PREFIX = "artifacts";

    /**
     * 解析版本源文件 key。
     *
     * @param workspaceId 工作区 ID
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param filename 原始文件名
     * @return 源文件逻辑 key
     */
    public String resolveSourceKey(String workspaceId, DocumentId documentId, int versionNumber, String filename) {
        validateVersionNumber(versionNumber);
        return String.join(
                "/",
                SOURCE_PREFIX,
                safeSegment(workspaceId, "workspaceId"),
                "documents",
                safeSegment(documentId.value(), "documentId"),
                "versions",
                Integer.toString(versionNumber),
                safeFilename(filename));
    }

    /**
     * 解析版本处理产物 key。
     *
     * @param workspaceId 工作区 ID
     * @param documentId 文档资产 ID
     * @param versionNumber 版本号
     * @param artifactName 产物名称，例如 cleaned.md
     * @return 处理产物逻辑 key
     */
    public String resolveArtifactKey(String workspaceId, DocumentId documentId, int versionNumber, String artifactName) {
        validateVersionNumber(versionNumber);
        return String.join(
                "/",
                ARTIFACTS_PREFIX,
                safeSegment(workspaceId, "workspaceId"),
                "documents",
                safeSegment(documentId.value(), "documentId"),
                "versions",
                Integer.toString(versionNumber),
                safeFilename(artifactName));
    }

    /**
     * 清洗可作为文件名的末端 key 片段。
     *
     * @param value 原始名称
     * @return 安全名称
     */
    public String safeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "uploaded.bin";
        }
        String replaced = value.replace('\\', '_').replace('/', '_').trim();
        if (replaced.isEmpty()) {
            return "uploaded.bin";
        }
        return replaced;
    }

    private static String safeSegment(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.replace('\\', '_').replace('/', '_').trim();
    }

    private static void validateVersionNumber(int versionNumber) {
        if (versionNumber < 1) {
            throw new IllegalArgumentException("versionNumber must be positive");
        }
    }
}
