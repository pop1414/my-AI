package io.github.spike.myai.auth.application.result;

/**
 * 当前用户能力位结果对象。
 */
public record CurrentUserCapabilitiesResult(
        boolean canAccessDocumentList,
        boolean canUploadDocument,
        boolean canAccessKnowledge,
        boolean canAskQuestion,
        boolean canAccessAdmin) {
}
