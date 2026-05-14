package io.github.spike.myai.qa.domain.model;

import java.time.Instant;

/**
 * 问答场景下单个文档的当前可问答版本事实。
 *
 * <p>该模型把 document 的 latest projection 与最近一个已 INDEXED 版本放在一起，
 * 供 QA 应用层判断某条召回分块是否可用于回答，并生成引用版本提示。</p>
 *
 * @param documentId 文档资产 ID
 * @param latestVersionNumber 当前最新版本号
 * @param askableVersionNumber 当前可问答版本号，即最近一个 INDEXED 版本
 * @param sourceFilename 可问答版本对应的来源文件名
 * @param sourceUpdatedAt 可问答版本最近更新时间
 */
public record AskableDocumentVersion(
        String documentId,
        int latestVersionNumber,
        int askableVersionNumber,
        String sourceFilename,
        Instant sourceUpdatedAt) {

    public AskableDocumentVersion {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId must not be blank");
        }
        if (latestVersionNumber < 1) {
            throw new IllegalArgumentException("latestVersionNumber must be positive");
        }
        if (askableVersionNumber < 1) {
            throw new IllegalArgumentException("askableVersionNumber must be positive");
        }
        if (askableVersionNumber > latestVersionNumber) {
            throw new IllegalArgumentException("askableVersionNumber must not exceed latestVersionNumber");
        }
        if (sourceFilename == null || sourceFilename.isBlank()) {
            throw new IllegalArgumentException("sourceFilename must not be blank");
        }
        if (sourceUpdatedAt == null) {
            throw new IllegalArgumentException("sourceUpdatedAt must not be null");
        }
    }

    /**
     * 判断可问答版本是否就是当前最新版本。
     *
     * @return {@code true} 表示引用未落后于最新版本
     */
    public boolean isLatestVersion() {
        return askableVersionNumber == latestVersionNumber;
    }
}
