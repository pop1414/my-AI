package io.github.spike.myai.auth.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.auth.application.result.AuditEventItemResult;
import io.github.spike.myai.auth.application.result.AuditEventPageResult;
import io.github.spike.myai.auth.application.usecase.ListAuditEventsUseCase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditEventAdminControllerTest {

    private ListAuditEventsUseCase listAuditEventsUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.listAuditEventsUseCase = Mockito.mock(ListAuditEventsUseCase.class);
        AuditEventAdminController controller = new AuditEventAdminController(
                listAuditEventsUseCase,
                new ObjectMapper());
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("查询审计事件分页列表应返回分页结果")
    void listAuditEvents_shouldReturnPage() throws Exception {
        when(listAuditEventsUseCase.handle(any())).thenReturn(new AuditEventPageResult(
                List.of(new AuditEventItemResult(
                        1001L,
                        "default",
                        "user-admin",
                        "alice",
                        "DOCUMENT_GRANT_UPSERTED",
                        "DOCUMENT_GRANT",
                        "doc-1:user-2",
                        "SUCCESS",
                        "",
                        "{\"permission\":\"DOC_ALLOW_READ\"}",
                        Instant.parse("2026-05-10T03:00:00Z"))),
                1L,
                20,
                0));

        mockMvc.perform(get("/api/v1/admin/audit-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].auditEventId").value(1001))
                .andExpect(jsonPath("$.items[0].eventType").value("DOCUMENT_GRANT_UPSERTED"))
                .andExpect(jsonPath("$.items[0].metadata.permission").value("DOC_ALLOW_READ"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("查询审计事件参数非法时应返回 400")
    void listAuditEvents_shouldReturnBadRequest_whenQueryInvalid() throws Exception {
        when(listAuditEventsUseCase.handle(any()))
                .thenThrow(new IllegalArgumentException("limit must be between 1 and 100"));

        mockMvc.perform(get("/api/v1/admin/audit-events").param("limit", "200"))
                .andExpect(status().isBadRequest());
    }
}
