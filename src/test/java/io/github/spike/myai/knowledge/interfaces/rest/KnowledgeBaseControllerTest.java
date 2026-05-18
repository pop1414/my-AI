package io.github.spike.myai.knowledge.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.application.result.KnowledgeBaseResult;
import io.github.spike.myai.knowledge.application.usecase.CreateKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.application.usecase.DeleteKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.application.usecase.ListKnowledgeBasesUseCase;
import io.github.spike.myai.knowledge.application.usecase.UpdateKnowledgeBaseUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KnowledgeBaseControllerTest {

    private ListKnowledgeBasesUseCase listKnowledgeBasesUseCase;
    private CreateKnowledgeBaseUseCase createKnowledgeBaseUseCase;
    private UpdateKnowledgeBaseUseCase updateKnowledgeBaseUseCase;
    private DeleteKnowledgeBaseUseCase deleteKnowledgeBaseUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.listKnowledgeBasesUseCase = Mockito.mock(ListKnowledgeBasesUseCase.class);
        this.createKnowledgeBaseUseCase = Mockito.mock(CreateKnowledgeBaseUseCase.class);
        this.updateKnowledgeBaseUseCase = Mockito.mock(UpdateKnowledgeBaseUseCase.class);
        this.deleteKnowledgeBaseUseCase = Mockito.mock(DeleteKnowledgeBaseUseCase.class);
        KnowledgeBaseController controller = new KnowledgeBaseController(
                listKnowledgeBasesUseCase,
                createKnowledgeBaseUseCase,
                updateKnowledgeBaseUseCase,
                deleteKnowledgeBaseUseCase);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("查询知识库列表应返回主数据字段与统计字段")
    void listKnowledgeBases_shouldReturnKnowledgeBaseSummaries() throws Exception {
        when(listKnowledgeBasesUseCase.handle()).thenReturn(List.of(
                new KnowledgeBaseResult("default", "默认知识库", "", "ACTIVE", 3),
                new KnowledgeBaseResult("kb-a", "知识库A", "desc", "INACTIVE", 1)));

        mockMvc.perform(get("/api/v1/knowledge-bases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("default"))
                .andExpect(jsonPath("$[0].description").value(""))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].indexedDocumentCount").value(3))
                .andExpect(jsonPath("$[1].status").value("INACTIVE"));
    }

    @Test
    @DisplayName("创建知识库应返回 200 与服务端生成的业务键")
    void createKnowledgeBase_shouldReturnCreatedKnowledgeBase() throws Exception {
        when(createKnowledgeBaseUseCase.handle(any())).thenReturn(
                new KnowledgeBaseResult("kb-generated", "知识库A", "desc", "ACTIVE", 0));

        mockMvc.perform(post("/api/v1/knowledge-bases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "知识库A",
                                  "description": "desc"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("kb-generated"))
                .andExpect(jsonPath("$.name").value("知识库A"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("编辑知识库应返回 200 与更新后的字段")
    void updateKnowledgeBase_shouldReturnUpdatedKnowledgeBase() throws Exception {
        when(updateKnowledgeBaseUseCase.handle(any())).thenReturn(
                new KnowledgeBaseResult("kb-1", "新名称", "新描述", "INACTIVE", 2));

        mockMvc.perform(patch("/api/v1/knowledge-bases/{kbId}", "kb-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "新名称",
                                  "description": "新描述",
                                  "status": "INACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("kb-1"))
                .andExpect(jsonPath("$.description").value("新描述"))
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    @DisplayName("编辑不存在知识库时应返回 404")
    void updateKnowledgeBase_shouldReturnNotFound_whenMissing() throws Exception {
        when(updateKnowledgeBaseUseCase.handle(any()))
                .thenThrow(new KnowledgeBaseNotFoundException("knowledge base not found: kb-missing"));

        mockMvc.perform(patch("/api/v1/knowledge-bases/{kbId}", "kb-missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "新名称"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("删除知识库成功时应返回 204")
    void deleteKnowledgeBase_shouldReturnOk() throws Exception {
        mockMvc.perform(delete("/api/v1/knowledge-bases/{kbId}", "kb-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("删除不存在知识库时应返回 404")
    void deleteKnowledgeBase_shouldReturnNotFound_whenMissing() throws Exception {
        doThrow(new KnowledgeBaseNotFoundException("knowledge base not found: kb-missing"))
                .when(deleteKnowledgeBaseUseCase)
                .handle("kb-missing");

        mockMvc.perform(delete("/api/v1/knowledge-bases/{kbId}", "kb-missing"))
                .andExpect(status().isNotFound());
    }
}
