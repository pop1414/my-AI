package io.github.spike.myai.qa.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.auth.domain.model.WorkspaceRole;
import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.AskableDocumentVersionPort;
import io.github.spike.myai.qa.domain.port.AnswerGenerationPort;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

/**
 * AskQuestionApplicationService 单元测试。
 */
class AskQuestionApplicationServiceTest {

    @Test
    @DisplayName("命中分块时应返回模型回答与结构化引用")
    void handle_shouldReturnAnswerAndReferences_whenChunksMatched() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        List<AskableDocumentVersion> scope = List.of(
                new AskableDocumentVersion("doc-1", 1, 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z")),
                new AskableDocumentVersion("doc-3", 1, 1, "rag-2.pdf", Instant.parse("2026-05-08T10:05:00Z")));
        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1")))
                .thenReturn(scope);
        when(chunkRetrievalPort.similaritySearch(eq("什么是 RAG"), anyInt(), eq(scope)))
                .thenReturn(List.of(
                        new RetrievedChunk("doc-1", "kb-1", 0, "RAG 是检索增强生成。", 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z")),
                        new RetrievedChunk("doc-3", "kb-1", 2, "它通过外部知识提升回答准确性。", 1, "rag-2.pdf", Instant.parse("2026-05-08T10:05:00Z"))));
        when(answerGenerationPort.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("RAG 是检索增强生成方案。");

        var result = service.handle(new AskQuestionCommand(" 什么是 RAG ", "kb-1", 2));

        assertEquals("RAG 是检索增强生成方案。", result.answer());
        assertEquals(2, result.references().size());
        assertEquals("doc-1", result.references().get(0).documentId());
        assertEquals(0, result.references().get(0).chunkIndex());
        assertEquals(1, result.references().get(0).sourceVersionNumber());
        assertEquals("rag-1.pdf", result.references().get(0).sourceFilename());
        assertEquals(1, result.references().get(0).latestVersionNumber());
        assertEquals(true, result.references().get(0).isLatestVersion());
        assertEquals("doc-3", result.references().get(1).documentId());
        assertEquals(false, result.staleReferences().hasStaleReferences());
        assertEquals(0, result.staleReferences().staleReferenceCount());
        verify(authorizationService).requireCanAskKnowledgeBase(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1"));
        verify(authorizationService, never()).requireCanAskDocument(org.mockito.ArgumentMatchers.any(CurrentUser.class), anyString(), anyString());
        verify(answerGenerationPort).generateAnswer(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("同次问答应按每个文档独立选择可问答版本并返回陈旧引用汇总")
    void handle_shouldSelectAskableVersionPerDocumentAndReturnStaleSummary() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        List<AskableDocumentVersion> scope = List.of(
                new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", Instant.parse("2026-05-09T10:00:00Z")),
                new AskableDocumentVersion("doc-2", 4, 4, "doc-2-v4.pdf", Instant.parse("2026-05-11T10:00:00Z")));
        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1")))
                .thenReturn(scope);
        when(chunkRetrievalPort.similaritySearch(eq("版本问题"), anyInt(), eq(scope)))
                .thenReturn(List.of(
                        new RetrievedChunk("doc-1", "kb-1", 1, "doc-1 v2 已可问答", 2, "doc-1-v2.pdf", Instant.parse("2026-05-09T10:00:00Z")),
                        new RetrievedChunk("doc-2", "kb-1", 0, "doc-2 v4 最新可问答", 4, "doc-2-v4.pdf", Instant.parse("2026-05-11T10:00:00Z"))));
        when(answerGenerationPort.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("回答引用了可问答版本。");

        var result = service.handle(new AskQuestionCommand("版本问题", "kb-1", 5));

        assertEquals("回答引用了可问答版本。", result.answer());
        assertEquals(2, result.references().size());
        assertEquals("doc-1", result.references().get(0).documentId());
        assertEquals(2, result.references().get(0).sourceVersionNumber());
        assertEquals(3, result.references().get(0).latestVersionNumber());
        assertEquals(false, result.references().get(0).isLatestVersion());
        assertEquals("doc-1-v2.pdf", result.references().get(0).sourceFilename());
        assertEquals("doc-2", result.references().get(1).documentId());
        assertEquals(4, result.references().get(1).sourceVersionNumber());
        assertEquals(true, result.references().get(1).isLatestVersion());
        assertEquals(true, result.staleReferences().hasStaleReferences());
        assertEquals(1, result.staleReferences().staleReferenceCount());
        assertEquals(1, result.staleReferences().staleDocumentCount());
        assertEquals("doc-1", result.staleReferences().documents().get(0).documentId());
        assertEquals(2, result.staleReferences().documents().get(0).sourceVersionNumber());
        assertEquals(3, result.staleReferences().documents().get(0).latestVersionNumber());
    }

    @Test
    @DisplayName("无可问答版本范围时应返回兜底回答且不调用召回和模型")
    void handle_shouldReturnFallback_whenAskableVersionScopeEmpty() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("default")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("default", "workspace-a", "default", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("default")))
                .thenReturn(List.of());

        var result = service.handle(new AskQuestionCommand("找不到", null, null));

        assertEquals("未检索到与问题相关的已入库内容，请补充文档后再试。", result.answer());
        assertEquals(0, result.references().size());
        assertEquals(null, result.staleReferences());
        verify(chunkRetrievalPort, never()).similaritySearch(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyList());
        verify(answerGenerationPort, never()).generateAnswer(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("无知识库问答权限时应直接拒绝且不执行召回")
    void handle_shouldDeny_whenUserCannotAskKnowledgeBase() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));
        Mockito.doThrow(new AccessDeniedException("knowledge base ask access denied"))
                .when(authorizationService)
                .requireCanAskKnowledgeBase(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1"));

        assertThrows(AccessDeniedException.class, () -> service.handle(new AskQuestionCommand("问题", "kb-1", 2)));
        verify(chunkRetrievalPort, never()).similaritySearch(anyString(), anyInt());
        verify(chunkRetrievalPort, never()).similaritySearch(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyList());
        verify(answerGenerationPort, never()).generateAnswer(anyString());
    }

    @Test
    @DisplayName("知识库不存在时应抛出未找到异常")
    void handle_shouldThrow_whenKnowledgeBaseMissing() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-missing"))).thenReturn(java.util.Optional.empty());

        assertThrows(KnowledgeBaseNotFoundException.class, () -> service.handle(new AskQuestionCommand("问题", "kb-missing", 1)));
    }

    @Test
    @DisplayName("知识库停用时应抛出冲突异常")
    void handle_shouldThrow_whenKnowledgeBaseInactive() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-inactive")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-inactive", "workspace-a", "禁用库", "", KnowledgeBaseStatus.INACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        assertThrows(KnowledgeBaseInactiveException.class, () -> service.handle(new AskQuestionCommand("问题", "kb-inactive", 1)));
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }
}
