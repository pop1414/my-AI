package io.github.spike.myai.knowledge.interfaces.rest;

import io.github.spike.myai.knowledge.application.command.CreateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.command.UpdateKnowledgeBaseCommand;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
import io.github.spike.myai.knowledge.application.usecase.ListKnowledgeBasesUseCase;
import io.github.spike.myai.knowledge.application.usecase.CreateKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.application.usecase.UpdateKnowledgeBaseUseCase;
import io.github.spike.myai.knowledge.interfaces.rest.dto.CreateKnowledgeBaseRequest;
import io.github.spike.myai.knowledge.interfaces.rest.dto.KnowledgeBaseResponse;
import io.github.spike.myai.knowledge.interfaces.rest.dto.UpdateKnowledgeBaseRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 知识库 REST 控制器。
 *
 * <p>该控制器位于接口层（interfaces），仅负责：
 * <ul>
 *   <li>接收 HTTP 请求并路由到应用层用例；</li>
 *   <li>将应用层返回结果转换为对外响应 DTO；</li>
 *   <li>维持稳定的 API 形态，不承载领域规则与持久化细节。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {

    /** 知识库列表查询用例 */
    private final ListKnowledgeBasesUseCase listKnowledgeBasesUseCase;
    /** 知识库创建用例 */
    private final CreateKnowledgeBaseUseCase createKnowledgeBaseUseCase;
    /** 知识库更新用例 */
    private final UpdateKnowledgeBaseUseCase updateKnowledgeBaseUseCase;

    /**
     * 构造控制器，注入所需的应用层用例。
     *
     * @param listKnowledgeBasesUseCase   列表查询用例
     * @param createKnowledgeBaseUseCase  创建用例
     * @param updateKnowledgeBaseUseCase  更新用例
     */
    public KnowledgeBaseController(
            ListKnowledgeBasesUseCase listKnowledgeBasesUseCase,
            CreateKnowledgeBaseUseCase createKnowledgeBaseUseCase,
            UpdateKnowledgeBaseUseCase updateKnowledgeBaseUseCase) {
        this.listKnowledgeBasesUseCase = listKnowledgeBasesUseCase;
        this.createKnowledgeBaseUseCase = createKnowledgeBaseUseCase;
        this.updateKnowledgeBaseUseCase = updateKnowledgeBaseUseCase;
    }

    /**
     * 查询知识库列表。
     *
     * <p>统计口径固定为“已完成索引”的文档数量（status = INDEXED），
     * 返回结果按知识库标识进行聚合。当前版本中，知识库名称由应用层按约定映射。
     *
     * @return 知识库列表响应，每项包含知识库标识、名称和已索引文档数
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<KnowledgeBaseResponse> listKnowledgeBases() {
        // 委托应用层用例获取领域结果，再逐项转换为对外 DTO
        return listKnowledgeBasesUseCase.handle().stream()
                .map(item -> new KnowledgeBaseResponse(
                        item.id(),
                        item.name(),
                        item.description(),
                        item.status(),
                        item.indexedDocumentCount()))
                .toList();
    }

    /**
     * 创建知识库。
     *
     * <p>由服务端生成 {@code kb_id}（UUID），客户端无需传入。
     * 请求体中 {@code name} 必填，{@code description} 和 {@code status} 可选，
     * {@code status} 默认值为 {@code ACTIVE}。
     *
     * @param request 创建请求，包含 name / description / status
     * @return 创建成功的知识库响应，包含服务端生成的 id
     * @throws ResponseStatusException 请求体为空或参数校验失败时返回 400
     */
    @PostMapping(value = {"", "/"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeBaseResponse createKnowledgeBase(@RequestBody(required = false) CreateKnowledgeBaseRequest request) {
        // 防御性校验：Spring 在 contentType 不匹配时可能注入 null
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 将 DTO 转为命令对象，委托应用层创建；kb_id 由服务端 UUID 生成
            var result = createKnowledgeBaseUseCase.handle(
                    new CreateKnowledgeBaseCommand(request.name(), request.description(), request.status()));
            // 领域结果 -> 对外响应 DTO
            return new KnowledgeBaseResponse(
                    result.id(),
                    result.name(),
                    result.description(),
                    result.status(),
                    result.indexedDocumentCount());
        } catch (IllegalArgumentException ex) {
            // 参数校验失败（如 name 为空、status 非法值），统一映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 更新知识库信息。
     *
     * <p>允许修改 {@code name}、{@code description} 和 {@code status}。
     * {@code kb_id} 不可修改，由路径参数指定目标知识库。
     * {@code status} 仅允许 {@code ACTIVE} 或 {@code INACTIVE}。
     *
     * @param kbId    知识库业务标识（UUID）
     * @param request 更新请求，包含可选的 name / description / status
     * @return 更新后的知识库响应
     * @throws ResponseStatusException 请求体为空或参数校验失败时返回 400，知识库不存在时返回 404
     */
    @PatchMapping(value = "/{kbId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeBaseResponse updateKnowledgeBase(
            @PathVariable("kbId") String kbId,
            @RequestBody(required = false) UpdateKnowledgeBaseRequest request) {
        // 防御性校验：Spring 在 contentType 不匹配时可能注入 null
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 将路径参数 kbId 与请求体合并为命令对象，委托应用层执行更新
            var result = updateKnowledgeBaseUseCase.handle(
                    new UpdateKnowledgeBaseCommand(kbId, request.name(), request.description(), request.status()));
            // 领域结果 -> 对外响应 DTO
            return new KnowledgeBaseResponse(
                    result.id(),
                    result.name(),
                    result.description(),
                    result.status(),
                    result.indexedDocumentCount());
        } catch (KnowledgeBaseNotFoundException ex) {
            // 目标知识库不存在，映射为 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数校验失败（如 status 非法值），统一映射为 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
