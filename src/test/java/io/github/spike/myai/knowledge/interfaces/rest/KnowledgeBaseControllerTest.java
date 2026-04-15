package io.github.spike.myai.knowledge.interfaces.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.ListKnowledgeBasesUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * KnowledgeBaseController 接口层测试。
 */
class KnowledgeBaseControllerTest {

    private ListKnowledgeBasesUseCase listKnowledgeBasesUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.listKnowledgeBasesUseCase = Mockito.mock(ListKnowledgeBasesUseCase.class);
        KnowledgeBaseController controller = new KnowledgeBaseController(listKnowledgeBasesUseCase);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("查询知识库列表应返回 200 与 INDEXED 聚合结果")
    void listKnowledgeBases_shouldReturnIndexedAggregation() throws Exception {
        when(listKnowledgeBasesUseCase.handle()).thenReturn(List.of(
                new KnowledgeBaseResult("default", "default", 3),
                new KnowledgeBaseResult("kb-a", "kb-a", 1)));

        mockMvc.perform(get("/api/v1/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("default"))
                .andExpect(jsonPath("$[0].indexedDocumentCount").value(3))
                .andExpect(jsonPath("$[1].id").value("kb-a"));
    }
}
