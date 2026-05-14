package io.github.spike.myai.ingest.interfaces.rest.dto;

/**
 * 上传新版本 REST 响应 DTO。
 *
 * <p>该记录封装了向既有文档上传新版本后返回给客户端的完整信息，
 * 包括版本创建结果、版本号链以及当前可问答状态。
 *
 * <h3>响应语义</h3>
 * <ul>
 *   <li>{@code versionCreated = true}：成功创建新版本，{@code versionNumber} 为新版本号；</li>
 *   <li>{@code versionCreated = false}：因内容相同触发幂等复用，
 *       {@code versionResultType = "REUSED_IDENTICAL_CONTENT"}。</li>
 * </ul>
 *
 * @param documentId                文档资产 ID
 * @param versionCreated            是否创建了新版本
 * @param versionResultType         结果类型（CREATED / REUSED_IDENTICAL_CONTENT）
 * @param versionNumber             新创建的版本号，复用时为 null
 * @param previousVersionNumber     创建新版本前的最新版本号
 * @param reusedLatestVersionNumber 同内容复用时仍停留的最新版本号
 * @param latestVersionNumber       系统当前最新版本号
 * @param askableVersionNumber      当前可问答版本号，无可问答版本时为 null
 * @param canAskNow                 当前是否存在可问答版本
 * @param status                    当前最新版本状态
 * @param versionOriginType         当前最新版本来源类型（UPLOAD / ROLLBACK 等）
 */
public record DocumentVersionUploadResponse(
        String documentId,
        boolean versionCreated,
        String versionResultType,
        Integer versionNumber,
        Integer previousVersionNumber,
        Integer reusedLatestVersionNumber,
        int latestVersionNumber,
        Integer askableVersionNumber,
        boolean canAskNow,
        String status,
        String versionOriginType) {
}
