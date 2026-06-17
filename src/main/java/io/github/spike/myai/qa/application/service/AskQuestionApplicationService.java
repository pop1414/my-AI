package io.github.spike.myai.qa.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.qa.application.result.AskQuestionResult;
import io.github.spike.myai.qa.application.result.AskReferenceResult;
import io.github.spike.myai.qa.application.result.AskStaleReferenceDocumentResult;
import io.github.spike.myai.qa.application.result.AskStaleReferenceSummaryResult;
import io.github.spike.myai.qa.application.usecase.AskQuestionUseCase;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.domain.model.KnowledgeBaseStatus;
import io.github.spike.myai.knowledge.domain.port.KnowledgeBaseRepository;
import io.github.spike.myai.qa.domain.model.AskableDocumentVersion;
import io.github.spike.myai.qa.domain.model.QueryType;
import io.github.spike.myai.qa.domain.model.RetrievedChunk;
import io.github.spike.myai.qa.domain.port.AskableDocumentVersionPort;
import io.github.spike.myai.qa.domain.port.AnswerGenerationPort;
import io.github.spike.myai.qa.domain.port.ChunkRetrievalPort;
import io.github.spike.myai.qa.domain.port.QueryClassifierPort;
import io.github.spike.myai.qa.domain.port.RerankingPort;
import io.github.spike.myai.qa.infrastructure.config.QaRetrievalProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 文档问答应用服务（同步返回版）。
 *
 * <p>该服务编排完整问答流程：
 * <ol>
 *   <li>解析并规范化输入命令；</li>
 *   <li>通过查询分类端口判断查询意图（CHITCHAT 跳过检索直接调用 LLM）；</li>
 *   <li>执行语义检索并按知识库过滤；</li>
 *   <li>通过可插拔重排序端口对候选结果进行二次排序；</li>
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

    /** 引用预览最大字符数，防止响应体过大（仅影响展示，不影响检索与生成） */
    private static final int PREVIEW_MAX_LENGTH = 200;
    /** 兜底回答文案：无检索命中或模型返回无效结果时使用 */
    private static final String FALLBACK_ANSWER = "未检索到与问题相关的已入库内容，请补充文档后再试。";

    /** 领域端口：查询意图分类（决定是否走检索流程） */
    private final QueryClassifierPort queryClassifierPort;
    /** 领域端口：向量相似度检索（语义召回） */
    private final ChunkRetrievalPort chunkRetrievalPort;
    /** 领域端口：检索结果重排序（可插拔，默认透传） */
    private final RerankingPort rerankingPort;
    /** 领域端口：LLM 回答生成（调用大模型） */
    private final AnswerGenerationPort answerGenerationPort;
    /** 知识库仓储端口：用于校验目标知识库的存在性与状态 */
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    /** 当前用户上下文提供器：用于限定工作区和读取登录态 */
    private final CurrentUserProvider currentUserProvider;
    /** 授权服务：用于知识库问答权限和文档级覆盖过滤 */
    private final AuthorizationService authorizationService;
    /** 问答可用版本查询端口：用于按文档独立决定当前可问答版本 */
    private final AskableDocumentVersionPort askableDocumentVersionPort;
    /** 检索参数配置：候选下限与放大倍率 */
    private final QaRetrievalProperties properties;

    /**
     * 构造器注入。
     *
     * @param queryClassifierPort     查询意图分类端口（决定是否走检索流程）
     * @param chunkRetrievalPort      向量检索端口（语义召回）
     * @param rerankingPort          检索结果重排序端口（可插拔扩展点，默认透传）
     * @param answerGenerationPort    LLM 回答生成端口（调用大模型）
     * @param knowledgeBaseRepository 知识库仓储端口（校验存在性与状态）
     * @param currentUserProvider     当前用户上下文提供器（获取登录态与工作区）
     * @param authorizationService    授权服务（知识库问答权限与文档级覆盖过滤）
     * @param askableDocumentVersionPort 问答可用版本查询端口（按文档选择可问答版本）
     * @param properties             检索参数配置（候选下限与放大倍率）
     */
    public AskQuestionApplicationService(
            QueryClassifierPort queryClassifierPort,
            ChunkRetrievalPort chunkRetrievalPort,
            RerankingPort rerankingPort,
            AnswerGenerationPort answerGenerationPort,
            KnowledgeBaseRepository knowledgeBaseRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService,
            AskableDocumentVersionPort askableDocumentVersionPort,
            QaRetrievalProperties properties) {
        this.queryClassifierPort = queryClassifierPort;
        this.chunkRetrievalPort = chunkRetrievalPort;
        this.rerankingPort = rerankingPort;
        this.answerGenerationPort = answerGenerationPort;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.askableDocumentVersionPort = askableDocumentVersionPort;
        this.properties = properties;
    }

    /**
     * 执行问答流程（同步返回）。
     *
     * <p>处理步骤：
     * <ol>
     *   <li>规整化输入（问题文本、知识库 ID、topK）；</li>
     *   <li>校验目标知识库存在且启用；</li>
     *   <li>查询意图分类 — CHITCHAT 跳过检索，直接调用 LLM 返回；</li>
     *   <li>以放大后的 topK 进行向量相似度检索，再按 kbId 精过滤；</li>
     *   <li>通过可插拔重排序端口对候选结果进行二次排序（默认透传）；</li>
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
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        String question = command.normalizedQuestion();
        String kbId = command.resolvedKbId();
        validateKnowledgeBase(currentUser, kbId);
        authorizationService.requireCanAskKnowledgeBase(currentUser, kbId);
        int topK = command.resolvedTopK();

        // 2. 查询意图分类：CHITCHAT 跳过检索直接调用 LLM
        QueryType queryType = queryClassifierPort.classify(question);
        if (queryType == QueryType.CHITCHAT) {
            String answer = answerGenerationPort.generateAnswer(question);
            if (answer == null || answer.isBlank()) {
                answer = FALLBACK_ANSWER;
            }
            return new AskQuestionResult(answer, List.of());
        }

        List<AskableDocumentVersion> askableVersionScope =
                askableDocumentVersionPort.findAskableVersionsForQuestion(currentUser, kbId);
        if (askableVersionScope.isEmpty()) {
            return new AskQuestionResult(FALLBACK_ANSWER, List.of(), null);
        }
        Map<String, AskableDocumentVersion> askableVersions = askableVersionScope.stream()
                .collect(Collectors.toUnmodifiableMap(AskableDocumentVersion::documentId, java.util.function.Function.identity()));

        // 2. 扩大召回数量后在检索端按“已授权 + 当前可问答版本”范围过滤。
        //    公式：retrievalTopK = max(minCandidates, topK × candidateMultiplier)
        int retrievalTopK = Math.max(properties.getMinCandidates(), topK * properties.getCandidateMultiplier());
        List<RetrievedChunk> matchedChunks = chunkRetrievalPort.similaritySearch(question, retrievalTopK, askableVersionScope)
                .stream()
                .limit(topK)                                   // 截取实际需要的数量
                .toList();

        // 3. 通过可插拔重排序端口对候选结果进行二次排序（默认透传，预留扩展点）
        matchedChunks = rerankingPort.rerank(matchedChunks, question, topK);

        if (matchedChunks.isEmpty()) {
            // 4. 无依据时直接返回兜底回答，避免调用模型产生幻觉
            return new AskQuestionResult(FALLBACK_ANSWER, List.of(), null);
        }

        // 5. 构造提示词并调用 LLM 生成回答
        String prompt = buildPrompt(question, matchedChunks);
        String answer = answerGenerationPort.generateAnswer(prompt);
        if (answer == null || answer.isBlank()) {
            // 模型返回空值时回退到兜底文案
            answer = FALLBACK_ANSWER;
        }

        // 6. 构建引用片段列表（已截断预览长度）
        List<AskReferenceResult> references = matchedChunks.stream()
                .map(chunk -> toReferenceResult(chunk, askableVersions.get(chunk.documentId())))
                .toList();

        // 7. 组装并返回最终结果
        return new AskQuestionResult(answer, references, buildStaleReferenceSummary(references));
    }

    /**
     * 构造最小可用提示词。
     *
     * <p>通过显式约束“仅基于参考片段回答”，降低模型幻觉风险。
     *
     * @param question 用户原始问题
     * @param chunks   检索命中的参考片段列表
     * @return 格式化后的提示词字符串
     */
    private static String buildPrompt(String question, List<RetrievedChunk> chunks) {
        // 将每个参考片段格式化为 "[文档ID#片段序号] 内容预览" 的形式，换行拼接
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
     *
     * @param content 原始内容文本
     * @return 截断后的预览文本；空值返回空字符串
     */
    private static String trimPreview(String content) {
        // 空值或空白内容直接返回空串，避免 NPE
        if (content == null || content.isBlank()) {
            return "";
        }
        // 长度未超限则原样返回
        if (content.length() <= PREVIEW_MAX_LENGTH) {
            return content;
        }
        // 超出上限则截取前 PREVIEW_MAX_LENGTH 个字符
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
    private void validateKnowledgeBase(CurrentUser currentUser, String kbId) {
        // 1. 查找知识库，不存在则快速失败
        var knowledgeBase = knowledgeBaseRepository.findByKbId(currentUser.workspaceId(), kbId)
                .orElseThrow(() -> new KnowledgeBaseNotFoundException("knowledge base not found: " + kbId));

        // 2. 状态校验：仅 ACTIVE 状态允许问答
        if (knowledgeBase.status() != KnowledgeBaseStatus.ACTIVE) {
            throw new KnowledgeBaseInactiveException("knowledge base is inactive: " + kbId);
        }
    }

    /**
     * 将可用分块与版本事实合并为引用结果。
     *
     * @param chunk 已确认属于当前可问答版本的分块
     * @param askableVersion 文档当前可问答版本事实
     * @return 引用结果
     */
    private static AskReferenceResult toReferenceResult(RetrievedChunk chunk, AskableDocumentVersion askableVersion) {
        return new AskReferenceResult(
                chunk.documentId(),
                chunk.chunkIndex(),
                trimPreview(chunk.content()),
                askableVersion.askableVersionNumber(),
                askableVersion.sourceUpdatedAt(),
                askableVersion.isLatestVersion(),
                askableVersion.latestVersionNumber(),
                askableVersion.sourceFilename());
    }

    /**
     * 构造顶层陈旧引用汇总。
     *
     * <p>无引用时返回 {@code null}，确保无依据回答不会展示版本提示。
     * 有引用但全部来自最新版本时，仍返回空汇总对象，便于调用方获得稳定的响应语义。</p>
     *
     * @param references 引用结果列表
     * @return 陈旧引用汇总；无引用时为空
     */
    private static AskStaleReferenceSummaryResult buildStaleReferenceSummary(List<AskReferenceResult> references) {
        if (references.isEmpty()) {
            return null;
        }

        List<AskReferenceResult> staleReferences = references.stream()
                .filter(reference -> !reference.isLatestVersion())
                .toList();
        Map<String, AskStaleReferenceDocumentResult> staleDocuments = new LinkedHashMap<>();
        for (AskReferenceResult reference : staleReferences) {
            staleDocuments.putIfAbsent(
                    reference.documentId(),
                    new AskStaleReferenceDocumentResult(
                            reference.documentId(),
                            reference.sourceVersionNumber(),
                            reference.latestVersionNumber(),
                            reference.sourceFilename()));
        }
        return new AskStaleReferenceSummaryResult(
                !staleReferences.isEmpty(),
                staleReferences.size(),
                staleDocuments.size(),
                List.copyOf(staleDocuments.values()));
    }
}
