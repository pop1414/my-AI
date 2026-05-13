package io.github.spike.myai.ingest.domain.model;

/**
 * 文档版本来源类型。
 *
 * <p>当前阶段仅区分：
 * <ul>
 *   <li>{@link #UPLOAD}：由上传产生的新版本；</li>
 *   <li>{@link #ROLLBACK}：由历史版本回退产生的新最新版本。</li>
 * </ul>
 */
public enum DocumentVersionOriginType {
    UPLOAD,
    ROLLBACK
}
