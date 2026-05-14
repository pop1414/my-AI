package io.github.spike.myai.qa.interfaces.rest;

import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.qa.application.result.AskReferenceResult;
import io.github.spike.myai.qa.application.result.AskStaleReferenceDocumentResult;
import io.github.spike.myai.qa.application.result.AskStaleReferenceSummaryResult;
import io.github.spike.myai.qa.application.usecase.AskQuestionUseCase;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.qa.interfaces.rest.dto.AskReferenceResponse;
import io.github.spike.myai.qa.interfaces.rest.dto.AskRequest;
import io.github.spike.myai.qa.interfaces.rest.dto.AskResponse;
import io.github.spike.myai.qa.interfaces.rest.dto.AskStaleReferenceDocumentResponse;
import io.github.spike.myai.qa.interfaces.rest.dto.AskStaleReferenceSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 问答接口控制器（Interface Layer）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>接收 HTTP 请求并完成参数绑定、基础校验；</li>
 *   <li>将请求 DTO 转换为应用层命令对象；</li>
 *   <li>调用问答用例并将结果映射为响应 DTO；</li>
 *   <li>将应用层输入异常转换为标准 HTTP 400 响应。</li>
 * </ul>
 *
 * <p>版本说明：V1 仅提供同步单次问答，流式（SSE）能力暂不开放。
 *
 * @author Spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/qa")
public class QaController {

    /** 问答用例接口，控制器仅依赖接口不依赖具体实现，符合依赖倒置原则 */
    private final AskQuestionUseCase askQuestionUseCase;

    /**
     * 构造器注入。
     *
     * @param askQuestionUseCase 问答用例
     */
    public QaController(AskQuestionUseCase askQuestionUseCase) {
        this.askQuestionUseCase = askQuestionUseCase;
    }

    /**
     * 文档问答（同步返回）。
     *
     * <p>接口契约：
     * <ul>
     *   <li>路径：POST /api/v1/qa/ask</li>
     *   <li>请求：application/json</li>
     *   <li>响应：application/json，包含回答文本与引用片段</li>
     * </ul>
     *
     * <p>异常映射：
     * <ul>
     *   <li>{@code request == null} → 400，请求体为空；</li>
     *   <li>{@code KnowledgeBaseNotFoundException} → 400，目标知识库不存在；</li>
     *   <li>{@code KnowledgeBaseInactiveException} → 409，知识库已停用；</li>
     *   <li>{@code IllegalArgumentException} → 400，参数校验失败。</li>
     * </ul>
     *
     * @param request 问答请求体，必须包含有效问题文本
     * @return 问答响应（answer + references）
     */
    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AskResponse ask(@RequestBody(required = false) AskRequest request) {
        // 接口安全阀：请求体为空时快速失败，避免穿透到应用层
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 将 HTTP 请求 DTO 转换为应用层命令对象，隔离接口协议与用例编排
            var result = askQuestionUseCase.handle(
                    new AskQuestionCommand(request.question(), request.kbId(), request.topK()));

            // 将领域结果映射为 REST 响应 DTO，引用列表通过方法引用转换
            return new AskResponse(
                    result.answer(),
                    result.references().stream()
                            .map(QaController::toReferenceResponse)
                            .toList(),
                    toStaleReferenceSummaryResponse(result.staleReferences()));
        } catch (KnowledgeBaseNotFoundException ex) {
            // 知识库不存在 → 400（用户输入了无效的 kbId）
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (KnowledgeBaseInactiveException ex) {
            // 知识库已停用 → 409（资源状态冲突）
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数校验失败（如问题为空、topK 越界）→ 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将应用层引用结果映射为 REST 响应项（DTO 转换）。
     *
     * <p>该映射确保应用层模型变更不影响对外 API 契约，
     * 外部调用方仅依赖 REST DTO 结构。
     *
     * @param item 应用层引用结果
     * @return REST 层的引用响应项
     */
    private static AskReferenceResponse toReferenceResponse(AskReferenceResult item) {
        return new AskReferenceResponse(
                item.documentId(),
                item.chunkIndex(),
                item.contentPreview(),
                item.sourceVersionNumber(),
                item.sourceUpdatedAt(),
                item.isLatestVersion(),
                item.latestVersionNumber(),
                item.sourceFilename());
    }

    /**
     * 将应用层陈旧引用汇总映射为 REST 响应对象。
     *
     * @param summary 应用层陈旧引用汇总；无引用时为空
     * @return REST 层陈旧引用汇总；无引用时为空
     */
    private static AskStaleReferenceSummaryResponse toStaleReferenceSummaryResponse(
            AskStaleReferenceSummaryResult summary) {
        if (summary == null) {
            return null;
        }
        return new AskStaleReferenceSummaryResponse(
                summary.hasStaleReferences(),
                summary.staleReferenceCount(),
                summary.staleDocumentCount(),
                summary.documents().stream()
                        .map(QaController::toStaleReferenceDocumentResponse)
                        .toList());
    }

    /**
     * 将应用层陈旧引用文档项映射为 REST 响应对象。
     *
     * @param item 应用层陈旧引用文档项
     * @return REST 层陈旧引用文档项
     */
    private static AskStaleReferenceDocumentResponse toStaleReferenceDocumentResponse(
            AskStaleReferenceDocumentResult item) {
        return new AskStaleReferenceDocumentResponse(
                item.documentId(),
                item.sourceVersionNumber(),
                item.latestVersionNumber(),
                item.sourceFilename());
    }
}
