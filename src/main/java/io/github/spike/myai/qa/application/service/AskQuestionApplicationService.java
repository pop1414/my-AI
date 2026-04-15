package io.github.spike.myai.qa.application.service;

import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.qa.application.result.AskQuestionResult;
import io.github.spike.myai.qa.application.result.AskReferenceResult;
import io.github.spike.myai.qa.application.usecase.AskQuestionUseCase;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.AnswerGenerationPort;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 文档问答应用服务（同步返回版）。
 *
 * <p>该服务编排完整问答流程：
 * <ol>
 *   <li>解析并规范化输入命令；</li>
 *   <li>执行语义检索并按知识库过滤；</li>
 *   <li>构造提示词并调用回答生成端口；</li>
 *   <li>组装回答与引用片段返回接口层。</li>
 * </ol>
 *
 * <p>关键策略：
 * <ul>
 *   <li>检索阶段扩大候选集，再做业务过滤，减少 kb 过滤后的空结果概率；</li>
 *   <li>无检索命中时直接返回兜底文案，避免无依据生成；</li>
 *   <li>引用内容统一截断，控制响应体大小。</li>
 * </ul>
 */
@Service
public class AskQuestionApplicationService implements AskQuestionUseCase {

    /** 检索候选下限，避免 topK 较小时候选过少导致过滤后无结果。 */
    private static final int MIN_RETRIEVAL_CANDIDATES = 20;
    /** 检索候选放大倍率，先粗召回再精过滤。 */
    private static final int RETRIEVAL_CANDIDATE_MULTIPLIER = 4;
    /** 引用预览最大字符数，防止响应体过大。 */
    private static final int PREVIEW_MAX_LENGTH = 200;
    private static final String FALLBACK_ANSWER = "未检索到与问题相关的已入库内容，请补充文档后再试。";

    private final ChunkRetrievalPort chunkRetrievalPort;
    private final AnswerGenerationPort answerGenerationPort;

    public AskQuestionApplicationService(
            ChunkRetrievalPort chunkRetrievalPort,
            AnswerGenerationPort answerGenerationPort) {
        this.chunkRetrievalPort = chunkRetrievalPort;
        this.answerGenerationPort = answerGenerationPort;
    }

    @Override
    public AskQuestionResult handle(AskQuestionCommand command) {
        // 统一由命令对象完成输入规范化，避免重复逻辑。
        String question = command.normalizedQuestion();
        String kbId = command.resolvedKbId();
        int topK = command.resolvedTopK();

        // 扩大召回数量后按 kb 过滤，可提升目标知识库的命中率。
        int retrievalTopK = Math.max(MIN_RETRIEVAL_CANDIDATES, topK * RETRIEVAL_CANDIDATE_MULTIPLIER);
        List<RetrievedChunk> matchedChunks = chunkRetrievalPort.similaritySearch(question, retrievalTopK).stream()
                .filter(chunk -> kbId.equals(chunk.kbId()))
                .limit(topK)
                .toList();

        if (matchedChunks.isEmpty()) {
            // 无依据时直接返回兜底回答，避免调用模型产生幻觉。
            return new AskQuestionResult(FALLBACK_ANSWER, List.of());
        }

        String prompt = buildPrompt(question, matchedChunks);
        String answer = answerGenerationPort.generateAnswer(prompt);
        if (answer == null || answer.isBlank()) {
            answer = FALLBACK_ANSWER;
        }

        List<AskReferenceResult> references = matchedChunks.stream()
                .map(chunk -> new AskReferenceResult(
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        trimPreview(chunk.content())))
                .toList();

        return new AskQuestionResult(answer, references);
    }

    /**
     * 构造最小可用提示词。
     *
     * <p>通过显式约束“仅基于参考片段回答”，降低模型幻觉风险。
     */
    private static String buildPrompt(String question, List<RetrievedChunk> chunks) {
        String context = chunks.stream()
                .map(chunk -> "[" + chunk.documentId() + "#" + chunk.chunkIndex() + "] " + trimPreview(chunk.content()))
                .collect(Collectors.joining("\n"));

        return """
                你是知识库问答助手，请仅基于给定“参考片段”回答问题。
                如果参考片段不足以回答，请明确说明信息不足。

                问题：
                %s

                参考片段：
                %s

                请使用中文给出简洁回答。
                """.formatted(question, context);
    }

    /**
     * 统一截断引用预览长度。
     *
     * <p>该方法仅用于输出展示，不改变检索和生成阶段使用的原始内容。
     */
    private static String trimPreview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        if (content.length() <= PREVIEW_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, PREVIEW_MAX_LENGTH);
    }
}
