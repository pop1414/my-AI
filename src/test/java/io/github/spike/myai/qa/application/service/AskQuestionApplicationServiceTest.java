package io.github.spike.myai.qa.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
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
import io.github.spike.myai.qa.domain.port.RerankingPort;
import io.github.spike.myai.qa.infrastructure.config.QaRetrievalProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        QaRetrievalProperties properties = new QaRetrievalProperties();
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        List<AskableDocumentVersion> scope = List.of(
                new AskableDocumentVersion("doc-1", 1, 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z")),
                new AskableDocumentVersion("doc-3", 1, 1, "rag-2.pdf", Instant.parse("2026-05-08T10:05:00Z")));
        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1")))
                .thenReturn(scope);
        when(chunkRetrievalPort.similaritySearch(eq("什么是 RAG"), anyInt(), eq(scope)))
                .thenReturn(List.of(
                        new RetrievedChunk("doc-1", "kb-1", 0, "RAG 是检索增强生成。", 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z"), 0.0),
                        new RetrievedChunk("doc-3", "kb-1", 2, "它通过外部知识提升回答准确性。", 1, "rag-2.pdf", Instant.parse("2026-05-08T10:05:00Z"), 0.0)));
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
        verify(rerankingPort).rerank(anyList(), eq("什么是 RAG"), eq(2));
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
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        QaRetrievalProperties properties = new QaRetrievalProperties();
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        List<AskableDocumentVersion> scope = List.of(
                new AskableDocumentVersion("doc-1", 3, 2, "doc-1-v2.pdf", Instant.parse("2026-05-09T10:00:00Z")),
                new AskableDocumentVersion("doc-2", 4, 4, "doc-2-v4.pdf", Instant.parse("2026-05-11T10:00:00Z")));
        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1")))
                .thenReturn(scope);
        when(chunkRetrievalPort.similaritySearch(eq("版本问题"), anyInt(), eq(scope)))
                .thenReturn(List.of(
                        new RetrievedChunk("doc-1", "kb-1", 1, "doc-1 v2 已可问答", 2, "doc-1-v2.pdf", Instant.parse("2026-05-09T10:00:00Z"), 0.0),
                        new RetrievedChunk("doc-2", "kb-1", 0, "doc-2 v4 最新可问答", 4, "doc-2-v4.pdf", Instant.parse("2026-05-11T10:00:00Z"), 0.0)));
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
        verify(rerankingPort).rerank(anyList(), eq("版本问题"), eq(5));
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
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        QaRetrievalProperties properties = new QaRetrievalProperties();
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("default")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("default", "workspace-a", "default", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("default")))
                .thenReturn(List.of());

        var result = service.handle(new AskQuestionCommand("找不到", null, null));

        assertEquals("未检索到与问题相关的已入库内容，请补充文档后再试。", result.answer());
        assertEquals(0, result.references().size());
        assertEquals(null, result.staleReferences());
        verify(chunkRetrievalPort, never()).similaritySearch(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyList());
        verify(rerankingPort, never()).rerank(anyList(), anyString(), anyInt());
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
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        QaRetrievalProperties properties = new QaRetrievalProperties();
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));
        Mockito.doThrow(new AccessDeniedException("knowledge base ask access denied"))
                .when(authorizationService)
                .requireCanAskKnowledgeBase(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1"));

        assertThrows(AccessDeniedException.class, () -> service.handle(new AskQuestionCommand("问题", "kb-1", 2)));
        verify(chunkRetrievalPort, never()).similaritySearch(anyString(), anyInt());
        verify(chunkRetrievalPort, never()).similaritySearch(anyString(), anyInt(), org.mockito.ArgumentMatchers.anyList());
        verify(rerankingPort, never()).rerank(anyList(), anyString(), anyInt());
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
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        QaRetrievalProperties properties = new QaRetrievalProperties();
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
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
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        QaRetrievalProperties properties = new QaRetrievalProperties();
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-inactive")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-inactive", "workspace-a", "禁用库", "", KnowledgeBaseStatus.INACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        assertThrows(KnowledgeBaseInactiveException.class, () -> service.handle(new AskQuestionCommand("问题", "kb-inactive", 1)));
    }

    @Test
    @DisplayName("自定义检索配置应影响候选集放大计算")
    void handle_shouldUseCustomRetrievalProperties_whenConfigured() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        // 自定义配置：minCandidates=10, candidateMultiplier=2
        QaRetrievalProperties properties = new QaRetrievalProperties();
        properties.setMinCandidates(10);
        properties.setCandidateMultiplier(2);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));
        List<AskableDocumentVersion> scope = List.of(
                new AskableDocumentVersion("doc-1", 1, 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z")));
        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1")))
                .thenReturn(scope);
        // topK=3, retrievalTopK = max(10, 3*2) = 10
        when(chunkRetrievalPort.similaritySearch(eq("测试问题"), eq(10), eq(scope)))
                .thenReturn(List.of(
                        new RetrievedChunk("doc-1", "kb-1", 0, "测试内容", 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z"), 0.0)));
        when(answerGenerationPort.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("测试回答。");

        var result = service.handle(new AskQuestionCommand("测试问题", "kb-1", 3));

        assertEquals("测试回答。", result.answer());
        // 使用 ArgumentCaptor 验证公式计算的 retrievalTopK，避免硬编码耦合公式
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(chunkRetrievalPort).similaritySearch(eq("测试问题"), topKCaptor.capture(), eq(scope));
        assertEquals(Integer.valueOf(10), topKCaptor.getValue(), "minCandidates=10,topK=3,multiplier=2 → max(10,6)=10");
    }

    @Test
    @DisplayName("放大倍率主导时 retrievalTopK 应为 topK×multiplier")
    void handle_shouldUseMultiplierDrivenTopK_whenProductExceedsMinCandidates() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        CurrentUserProvider currentUserProvider = currentUserProvider();
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        AskableDocumentVersionPort askableDocumentVersionPort = Mockito.mock(AskableDocumentVersionPort.class);
        RerankingPort rerankingPort = Mockito.mock(RerankingPort.class);
        when(rerankingPort.rerank(anyList(), anyString(), anyInt()))
                .thenAnswer(inv -> inv.getArgument(0));
        // 默认配置 minCandidates=20, candidateMultiplier=4 → topK=10 时 retrievalTopK = max(20, 40) = 40
        QaRetrievalProperties properties = new QaRetrievalProperties();
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(
                        chunkRetrievalPort,
                        rerankingPort,
                        answerGenerationPort,
                        knowledgeBaseRepository,
                        currentUserProvider,
                        authorizationService,
                        askableDocumentVersionPort,
                        properties);
        when(knowledgeBaseRepository.findByKbId(eq("workspace-a"), eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "workspace-a", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));
        List<AskableDocumentVersion> scope = List.of(
                new AskableDocumentVersion("doc-1", 1, 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z")));
        when(askableDocumentVersionPort.findAskableVersionsForQuestion(org.mockito.ArgumentMatchers.any(CurrentUser.class), eq("kb-1")))
                .thenReturn(scope);
        // topK=10, retrievalTopK = max(20, 10*4) = 40
        when(chunkRetrievalPort.similaritySearch(eq("乘数测试"), eq(40), eq(scope)))
                .thenReturn(List.of(
                        new RetrievedChunk("doc-1", "kb-1", 0, "乘数测试内容", 1, "rag-1.pdf", Instant.parse("2026-05-08T10:00:00Z"), 0.0)));
        when(answerGenerationPort.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("乘数回答。");

        var result = service.handle(new AskQuestionCommand("乘数测试", "kb-1", 10));

        assertEquals("乘数回答。", result.answer());
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(chunkRetrievalPort).similaritySearch(eq("乘数测试"), topKCaptor.capture(), eq(scope));
        assertEquals(Integer.valueOf(40), topKCaptor.getValue(), "minCandidates=20,topK=10,multiplier=4 → max(20,40)=40");
    }

    private static CurrentUserProvider currentUserProvider() {
        CurrentUserProvider provider = Mockito.mock(CurrentUserProvider.class);
        when(provider.requireCurrentUser()).thenReturn(
                new CurrentUser("user-1", "alice", "workspace-a", WorkspaceRole.WORKSPACE_MEMBER));
        return provider;
    }
}
