package io.github.spike.myai.knowledge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.knowledge.application.command.CreateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseIdGenerator;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CreateKnowledgeBaseApplicationServiceTest {

    @Test
    @DisplayName("创建知识库时应生成 kbId 并持久化")
    void handle_shouldCreateKnowledgeBase() {
        KnowledgeBaseIdGenerator generator = Mockito.mock(KnowledgeBaseIdGenerator.class);
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        when(generator.nextKbId()).thenReturn("kb-generated");
        CreateKnowledgeBaseApplicationService service =
                new CreateKnowledgeBaseApplicationService(generator, repository);

        var result = service.handle(new CreateKnowledgeBaseCommand("知识库A", "描述", null));

        assertEquals("kb-generated", result.id());
        assertEquals("知识库A", result.name());
        assertEquals("描述", result.description());
        assertEquals("ACTIVE", result.status());
        verify(repository).save(any());

        var captor = ArgumentCaptor.forClass(io.github.spike.myai.knowledge.domain.model.KnowledgeBase.class);
        verify(repository).save(captor.capture());
        assertEquals("kb-generated", captor.getValue().kbId());
    }
}
