package io.github.spike.myai.knowledge.interfaces.rest;

import io.github.spike.myai.knowledge.application.usecase.ListKnowledgeBasesUseCase;
import io.github.spike.myai.knowledge.interfaces.rest.dto.KnowledgeBaseResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库查询 REST 控制器。
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

    private final ListKnowledgeBasesUseCase listKnowledgeBasesUseCase;

    public KnowledgeBaseController(ListKnowledgeBasesUseCase listKnowledgeBasesUseCase) {
        this.listKnowledgeBasesUseCase = listKnowledgeBasesUseCase;
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
        return listKnowledgeBasesUseCase.handle().stream()
                .map(item -> new KnowledgeBaseResponse(item.id(), item.name(), item.indexedDocumentCount()))
                .toList();
    }
}
