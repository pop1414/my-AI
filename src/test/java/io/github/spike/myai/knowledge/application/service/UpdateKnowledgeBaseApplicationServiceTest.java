package io.github.spike.myai.knowledge.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.knowledge.application.command.UpdateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBase;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseSummary;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UpdateKnowledgeBaseApplicationServiceTest {

    @Test
    @DisplayName("编辑知识库时应更新名称描述和状态")
    void handle_shouldUpdateKnowledgeBase() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        when(repository.findByKbId(eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("kb-1"))).thenReturn(Optional.of(
                new KnowledgeBase("kb-1", "旧名称", "旧描述", KnowledgeBaseStatus.ACTIVE, Instant.now(), Instant.now())));
        when(repository.listKnowledgeBases(eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID))).thenReturn(List.of(
                new KnowledgeBaseSummary("kb-1", "新名称", "新描述", KnowledgeBaseStatus.INACTIVE, 2)));
        UpdateKnowledgeBaseApplicationService service = new UpdateKnowledgeBaseApplicationService(repository);

        var result = service.handle(new UpdateKnowledgeBaseCommand("kb-1", "新名称", "新描述", KnowledgeBaseStatus.INACTIVE));

        assertEquals("新名称", result.name());
        assertEquals("新描述", result.description());
        assertEquals("INACTIVE", result.status());
        assertEquals(2L, result.indexedDocumentCount());
        verify(repository).save(any());
    }

    @Test
    @DisplayName("编辑不存在知识库时应抛出未找到异常")
    void handle_shouldThrow_whenKnowledgeBaseMissing() {
        KnowledgeBaseRepository repository = Mockito.mock(KnowledgeBaseRepository.class);
        when(repository.findByKbId(eq(WorkspaceConstants.DEFAULT_WORKSPACE_ID), eq("kb-missing"))).thenReturn(Optional.empty());
        UpdateKnowledgeBaseApplicationService service = new UpdateKnowledgeBaseApplicationService(repository);

        assertThrows(
                KnowledgeBaseNotFoundException.class,
                () -> service.handle(new UpdateKnowledgeBaseCommand("kb-missing", "新名称", null, null)));
    }
}
