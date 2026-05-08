package io.github.spike.myai.qa.application.service;

import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.qa.application.result.AskQuestionResult;
import io.github.spike.myai.qa.application.result.AskReferenceResult;
import io.github.spike.myai.qa.application.usecase.AskQuestionUseCase;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.AnswerGenerationPort;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import io.github.spike.myai.shared.workspace.WorkspaceConstants;
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
 *   <li>无检索命中时直接返回兜底文案，避免无依据生成（防止模型幻觉）；</li>
 *   <li>引用内容统一截断，控制响应体大小；</li>
 *   <li>模型返回空或空白时回退到兜底文案。</li>
 * </ul>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class AskQuestionApplicationService implements AskQuestionUseCase {

    /** 检索候选下限，避免 topK 较小时候选过少导致过滤后无结果 */
    private static final int MIN_RETRIEVAL_CANDIDATES = 20;
    /** 检索候选放大倍率：先粗召回 topK×N 条，再按 kbId 精过滤 */
    private static final int RETRIEVAL_CANDIDATE_MULTIPLIER = 4;
    /** 引用预览最大字符数，防止响应体过大（仅影响展示，不影响检索与生成） */
    private static final int PREVIEW_MAX_LENGTH = 200;
    /** 兜底回答文案：无检索命中或模型返回无效结果时使用 */
    private static final String FALLBACK_ANSWER = "未检索到与问题相关的已入库内容，请补充文档后再试。";

    /** 领域端口：向量相似度检索（语义召回） */
    private final ChunkRetrievalPort chunkRetrievalPort;
    /** 领域端口：LLM 回答生成（调用大模型） */
    private final AnswerGenerationPort answerGenerationPort;
    /** 知识库仓储端口：用于校验目标知识库的存在性与状态 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    /**
     * 构造器注入。
     *
     * @param chunkRetrievalPort      向量检索端口
     * @param answerGenerationPort    LLM 回答生成端口
     * @param knowledgeBaseRepository 知识库仓储端口
     */
    public AskQuestionApplicationService(
            ChunkRetrievalPort chunkRetrievalPort,
            AnswerGenerationPort answerGenerationPort,
            KnowledgeBaseRepository knowledgeBaseRepository) {
        this.chunkRetrievalPort = chunkRetrievalPort;
        this.answerGenerationPort = answerGenerationPort;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
    }

    /**
     * 执行问答流程（同步返回）。
     *
     * <p>处理步骤：
     * <ol>
     *   <li>规整化输入（问题文本、知识库 ID、topK）；</li>
     *   <li>校验目标知识库存在且启用；</li>
     *   <li>以放大后的 topK 进行向量相似度检索，再按 kbId 精过滤；</li>
     *   <li>无命中时直接返回兜底文案，避免模型幻觉；</li>
     *   <li>构造提示词并调用 LLM 生成回答；</li>
     *   <li>截断引用预览并组装结果返回。</li>
     * </ol>
     *
     * @param command 问答命令（含问题、知识库 ID、检索数量）
     * @return 问答结果（含回答文本与引用片段列表）
     */
    @Override
    public AskQuestionResult handle(AskQuestionCommand command) {
        // 1. 统一由命令对象完成输入规范化，避免重复逻辑
        String question = command.normalizedQuestion();
        String kbId = command.resolvedKbId();
        validateKnowledgeBase(kbId);
        int topK = command.resolvedTopK();

        // 2. 扩大召回数量后按 kbId 过滤，可提升目标知识库的命中率
        //    公式：retrievalTopK = max(MIN_CANDIDATES, topK × MULTIPLIER)
        int retrievalTopK = Math.max(MIN_RETRIEVAL_CANDIDATES, topK * RETRIEVAL_CANDIDATE_MULTIPLIER);
        List<RetrievedChunk> matchedChunks = chunkRetrievalPort.similaritySearch(question, retrievalTopK).stream()
                .filter(chunk -> kbId.equals(chunk.kbId()))  // 只保留目标知识库的检索结果
                .limit(topK)                                   // 截取实际需要的数量
                .toList();

        if (matchedChunks.isEmpty()) {
            // 3. 无依据时直接返回兜底回答，避免调用模型产生幻觉
            return new AskQuestionResult(FALLBACK_ANSWER, List.of());
        }

        // 4. 构造提示词并调用 LLM 生成回答
        String prompt = buildPrompt(question, matchedChunks);
        String answer = answerGenerationPort.generateAnswer(prompt);
        if (answer == null || answer.isBlank()) {
            // 模型返回空值时回退到兜底文案
            answer = FALLBACK_ANSWER;
        }

        // 5. 构建引用片段列表（已截断预览长度）
        List<AskReferenceResult> references = matchedChunks.stream()
                .map(chunk -> new AskReferenceResult(
                        chunk.documentId(),
                        chunk.chunkIndex(),
                        trimPreview(chunk.content())))
                .toList();

        // 6. 组装并返回最终结果
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

    /**
     * 校验知识库存在且处于启用状态。
     *
     * <p>问答操作要求目标知识库必须存在且为 {@code ACTIVE} 状态。
     * 停用的知识库不应接受问答请求，避免用户在已归档知识库上浪费时间。
     *
     * @param kbId 待校验的知识库 ID
     * @throws KnowledgeBaseNotFoundException 当知识库不存在时
     * @throws KnowledgeBaseInactiveException 当知识库处于非启用状态时
     */
    private void validateKnowledgeBase(String kbId) {
        // 1. 查找知识库，不存在则快速失败
        var knowledgeBase = knowledgeBaseRepository.findByKbId(WorkspaceConstants.DEFAULT_WORKSPACE_ID, kbId)
                .orElseThrow(() -> new KnowledgeBaseNotFoundException("knowledge base not found: " + kbId));

        // 2. 状态校验：仅 ACTIVE 状态允许问答
        if (knowledgeBase.status() != KnowledgeBaseStatus.ACTIVE) {
            throw new KnowledgeBaseInactiveException("knowledge base is inactive: " + kbId);
        }
    }
}
