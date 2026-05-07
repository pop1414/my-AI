package io.github.spike.myai.knowledge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseSummary;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ListKnowledgeBasesApplicationServiceTest {

    @Test
    @DisplayName("存在知识库主数据时应返回带统计的列表")
    void handle_shouldReturnKnowledgeBases() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        when(repository.listKnowledgeBases()).thenReturn(List.of(
                new KnowledgeBaseSummary("kb-a", "知识库A", "desc-a", KnowledgeBaseStatus.ACTIVE, 3),
                new KnowledgeBaseSummary("kb-b", "知识库B", "", KnowledgeBaseStatus.INACTIVE, 0)));
        ListKnowledgeBasesApplicationService service = new ListKnowledgeBasesApplicationService(repository);

        var result = service.handle();

        assertEquals(2, result.size());
        assertEquals("kb-a", result.get(0).id());
        assertEquals("知识库A", result.get(0).name());
        assertEquals("desc-a", result.get(0).description());
        assertEquals("ACTIVE", result.get(0).status());
        assertEquals(3, result.get(0).indexedDocumentCount());
        assertEquals("INACTIVE", result.get(1).status());
    }
}
