package io.github.spike.myai.knowledge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseDocumentCount;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseQueryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * ListKnowledgeBasesApplicationService 单元测试。
 */
class ListKnowledgeBasesApplicationServiceTest {

    @Test
    @DisplayName("存在 INDEXED 聚合数据时应返回知识库列表")
    void handle_shouldReturnKnowledgeBases_whenIndexedDocumentsExist() {
        KnowledgeBaseQueryRepository repository = Mockito.mock(KnowledgeBaseQueryRepository.class);
        when(repository.listIndexedKnowledgeBases())
                .thenReturn(List.of(
                        new KnowledgeBaseDocumentCount("kb-a", 3),
                        new KnowledgeBaseDocumentCount("kb-b", 1)));
        ListKnowledgeBasesApplicationService service = new ListKnowledgeBasesApplicationService(repository);

        var result = service.handle();

        assertEquals(2, result.size());
        assertEquals("kb-a", result.get(0).id());
        assertEquals("kb-a", result.get(0).name());
        assertEquals(3, result.get(0).indexedDocumentCount());
        assertEquals("kb-b", result.get(1).id());
    }

    @Test
    @DisplayName("无 INDEXED 文档时应返回空列表")
    void handle_shouldReturnEmptyList_whenNoIndexedDocuments() {
        KnowledgeBaseQueryRepository repository = Mockito.mock(KnowledgeBaseQueryRepository.class);
        when(repository.listIndexedKnowledgeBases()).thenReturn(List.of());
        ListKnowledgeBasesApplicationService service = new ListKnowledgeBasesApplicationService(repository);

        var result = service.handle();

        assertEquals(0, result.size());
    }
}
