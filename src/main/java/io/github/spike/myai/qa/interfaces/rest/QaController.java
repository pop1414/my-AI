package io.github.spike.myai.qa.interfaces.rest;

import io.github.spike.myai.qa.application.command.AskQuestionCommand;
import io.github.spike.myai.qa.application.result.AskReferenceResult;
import io.github.spike.myai.qa.application.usecase.AskQuestionUseCase;
import io.github.spike.myai.qa.interfaces.rest.dto.AskReferenceResponse;
import io.github.spike.myai.qa.interfaces.rest.dto.AskRequest;
import io.github.spike.myai.qa.interfaces.rest.dto.AskResponse;
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
 */
@RestController
@RequestMapping("/api/v1/qa")
public class QaController {

    private final AskQuestionUseCase askQuestionUseCase;

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
     * @param request 问答请求体，必须包含有效问题文本
     * @return 问答响应（answer + references）
     */
    @PostMapping(value = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AskResponse ask(@RequestBody(required = false) AskRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            var result = askQuestionUseCase.handle(new AskQuestionCommand(request.question(), request.kbId(), request.topK()));
            return new AskResponse(
                    result.answer(),
                    result.references().stream().map(QaController::toReferenceResponse).toList());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将应用层引用结果映射为 REST 响应项，避免应用模型直接暴露给接口层。
     */
    private static AskReferenceResponse toReferenceResponse(AskReferenceResult item) {
        return new AskReferenceResponse(item.documentId(), item.chunkIndex(), item.contentPreview());
    }
}
