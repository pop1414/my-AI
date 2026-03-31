package io.github.spike.myai.ingest.domain.model;

/**
 * 上传状态（领域枚举）。
 *
 * <p>当前仅定义 {@code ACCEPTED}，表示请求已被系统受理。
 * 后续可按入库流程扩展状态，例如：
 * <ul>
 *     <li>INGESTING：处理中</li>
 *     <li>INDEXED：索引完成</li>
 *     <li>FAILED：处理失败</li>
 * </ul>
 */
public enum UploadStatus {
    ACCEPTED
}
