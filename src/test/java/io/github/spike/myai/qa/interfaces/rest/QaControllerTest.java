package io.github.spike.myai.qa.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.spike.myai.qa.application.result.AskQuestionResult;
import io.github.spike.myai.qa.application.result.AskReferenceResult;
import io.github.spike.myai.qa.application.result.AskStaleReferenceDocumentResult;
import io.github.spike.myai.qa.application.result.AskStaleReferenceSummaryResult;
import io.github.spike.myai.qa.application.usecase.AskQuestionUseCase;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * QaController 接口层测试。
 */
class QaControllerTest {

    private AskQuestionUseCase askQuestionUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.askQuestionUseCase = Mockito.mock(AskQuestionUseCase.class);
        QaController controller = new QaController(askQuestionUseCase);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("问答命中时应返回 200、answer、版本化 references 与 stale 汇总")
    void ask_shouldReturnAnswer_whenMatched() throws Exception {
        when(askQuestionUseCase.handle(any())).thenReturn(new AskQuestionResult(
                "这是回答",
                List.of(new AskReferenceResult(
                        "doc-1",
                        0,
                        "片段预览",
                        2,
                        Instant.parse("2026-05-09T10:00:00Z"),
                        false,
                        3,
                        "doc-1-v2.pdf")),
                new AskStaleReferenceSummaryResult(
                        true,
                        1,
                        1,
                        List.of(new AskStaleReferenceDocumentResult("doc-1", 2, 3, "doc-1-v2.pdf")))));

        mockMvc.perform(post("/api/v1/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是RAG",
                                  "kbId": "default",
                                  "topK": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("这是回答"))
                .andExpect(jsonPath("$.references[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.references[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.references[0].contentPreview").value("片段预览"))
                .andExpect(jsonPath("$.references[0].sourceVersionNumber").value(2))
                .andExpect(jsonPath("$.references[0].sourceUpdatedAt").value("2026-05-09T10:00:00Z"))
                .andExpect(jsonPath("$.references[0].isLatestVersion").value(false))
                .andExpect(jsonPath("$.references[0].latestVersionNumber").value(3))
                .andExpect(jsonPath("$.references[0].sourceFilename").value("doc-1-v2.pdf"))
                .andExpect(jsonPath("$.staleReferences.hasStaleReferences").value(true))
                .andExpect(jsonPath("$.staleReferences.staleReferenceCount").value(1))
                .andExpect(jsonPath("$.staleReferences.staleDocumentCount").value(1))
                .andExpect(jsonPath("$.staleReferences.documents[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.staleReferences.documents[0].sourceVersionNumber").value(2))
                .andExpect(jsonPath("$.staleReferences.documents[0].latestVersionNumber").value(3));
    }

    @Test
    @DisplayName("问答参数非法时应返回 400")
    void ask_shouldReturnBadRequest_whenIllegalArgument() throws Exception {
        when(askQuestionUseCase.handle(any())).thenThrow(new IllegalArgumentException("topK must be between 1 and 20"));

        mockMvc.perform(post("/api/v1/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是RAG",
                                  "topK": 99
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("问答知识库不存在时应返回 400")
    void ask_shouldReturnBadRequest_whenKnowledgeBaseMissing() throws Exception {
        when(askQuestionUseCase.handle(any())).thenThrow(new KnowledgeBaseNotFoundException("knowledge base not found: kb-missing"));

        mockMvc.perform(post("/api/v1/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是RAG",
                                  "kbId": "kb-missing"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("问答知识库停用时应返回 409")
    void ask_shouldReturnConflict_whenKnowledgeBaseInactive() throws Exception {
        when(askQuestionUseCase.handle(any())).thenThrow(new KnowledgeBaseInactiveException("knowledge base is inactive: kb-inactive"));

        mockMvc.perform(post("/api/v1/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "什么是RAG",
                                  "kbId": "kb-inactive"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("问答缺少请求体时应返回 400")
    void ask_shouldReturnBadRequest_whenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
