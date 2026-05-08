package io.github.spike.myai.qa.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.AnswerGenerationPort;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(chunkRetrievalPort, answerGenerationPort, knowledgeBaseRepository);
        when(knowledgeBaseRepository.findByKbId(eq("kb-1")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-1", "知识库1", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        when(chunkRetrievalPort.similaritySearch(eq("什么是 RAG"), anyInt()))
                .thenReturn(List.of(
                        new RetrievedChunk("doc-1", "kb-1", 0, "RAG 是检索增强生成。"),
                        new RetrievedChunk("doc-2", "kb-other", 1, "other kb"),
                        new RetrievedChunk("doc-3", "kb-1", 2, "它通过外部知识提升回答准确性。")));
        when(answerGenerationPort.generateAnswer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("RAG 是检索增强生成方案。");

        var result = service.handle(new AskQuestionCommand(" 什么是 RAG ", "kb-1", 2));

        assertEquals("RAG 是检索增强生成方案。", result.answer());
        assertEquals(2, result.references().size());
        assertEquals("doc-1", result.references().get(0).documentId());
        assertEquals(0, result.references().get(0).chunkIndex());
        assertEquals("doc-3", result.references().get(1).documentId());
        verify(answerGenerationPort).generateAnswer(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("无命中时应返回兜底回答且不调用模型")
    void handle_shouldReturnFallback_whenNoChunkMatched() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(chunkRetrievalPort, answerGenerationPort, knowledgeBaseRepository);
        when(knowledgeBaseRepository.findByKbId(eq("default")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("default", "default", "", KnowledgeBaseStatus.ACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        when(chunkRetrievalPort.similaritySearch(eq("找不到"), eq(20)))
                .thenReturn(List.of(new RetrievedChunk("doc-2", "kb-other", 1, "other kb")));

        var result = service.handle(new AskQuestionCommand("找不到", null, null));

        assertEquals("未检索到与问题相关的已入库内容，请补充文档后再试。", result.answer());
        assertEquals(0, result.references().size());
        verify(answerGenerationPort, never()).generateAnswer(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("知识库不存在时应抛出未找到异常")
    void handle_shouldThrow_whenKnowledgeBaseMissing() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(chunkRetrievalPort, answerGenerationPort, knowledgeBaseRepository);
        when(knowledgeBaseRepository.findByKbId(eq("kb-missing"))).thenReturn(java.util.Optional.empty());

        assertThrows(KnowledgeBaseNotFoundException.class, () -> service.handle(new AskQuestionCommand("问题", "kb-missing", 1)));
    }

    @Test
    @DisplayName("知识库停用时应抛出冲突异常")
    void handle_shouldThrow_whenKnowledgeBaseInactive() {
        ChunkRetrievalPort chunkRetrievalPort = Mockito.mock(ChunkRetrievalPort.class);
        AnswerGenerationPort answerGenerationPort = Mockito.mock(AnswerGenerationPort.class);
        KnowledgeBaseRepository knowledgeBaseRepository = Mockito.mock(KnowledgeBaseRepository.class);
        AskQuestionApplicationService service =
                new AskQuestionApplicationService(chunkRetrievalPort, answerGenerationPort, knowledgeBaseRepository);
        when(knowledgeBaseRepository.findByKbId(eq("kb-inactive")))
                .thenReturn(java.util.Optional.of(new KnowledgeBase("kb-inactive", "禁用库", "", KnowledgeBaseStatus.INACTIVE, java.time.Instant.now(), java.time.Instant.now())));

        assertThrows(KnowledgeBaseInactiveException.class, () -> service.handle(new AskQuestionCommand("问题", "kb-inactive", 1)));
    }
}
