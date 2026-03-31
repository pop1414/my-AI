package io.github.spike.myai.ingest.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.model.UploadStatus;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.domain.port.DocumentIdGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * AcceptUploadApplicationService 的应用层单元测试。
 *
 * <p>测试目标：
 * <ul>
 *     <li>验证用例编排是否正确调用领域端口。</li>
 *     <li>验证返回票据的核心字段（documentId/status）是否正确。</li>
 * </ul>
 */
class AcceptUploadApplicationServiceTest {

    @Test
    @DisplayName("handle 应返回 ACCEPTED 且使用端口生成文档 ID")
    void handle_shouldReturnAcceptedTicket() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        when(generator.nextId()).thenReturn(new DocumentId("doc-001"));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(generator);
        AcceptUploadCommand command = new AcceptUploadCommand("a.txt", 10L, "kb-x");

        UploadTicket ticket = service.handle(command);

        assertNotNull(ticket);
        assertEquals("doc-001", ticket.documentId().value());
        assertEquals(UploadStatus.ACCEPTED, ticket.status());
        verify(generator, times(1)).nextId();
    }

    @Test
    @DisplayName("kbId 为空时，流程仍应正常执行并返回受理结果")
    void handle_shouldWork_whenKbIdIsBlank() {
        DocumentIdGenerator generator = Mockito.mock(DocumentIdGenerator.class);
        when(generator.nextId()).thenReturn(new DocumentId("doc-blank-kb"));

        AcceptUploadApplicationService service = new AcceptUploadApplicationService(generator);
        AcceptUploadCommand command = new AcceptUploadCommand("b.txt", 20L, " ");

        UploadTicket ticket = service.handle(command);

        assertEquals("doc-blank-kb", ticket.documentId().value());
        assertEquals(UploadStatus.ACCEPTED, ticket.status());
        verify(generator, times(1)).nextId();
    }
}
