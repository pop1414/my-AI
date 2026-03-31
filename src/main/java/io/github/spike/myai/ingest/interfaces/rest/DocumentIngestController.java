package io.github.spike.myai.ingest.interfaces.rest;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.interfaces.rest.dto.UploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 文档入库接口控制器（Interface Layer）。
 *
 * <p>职责边界：
 * <ul>
 *     <li>处理 HTTP 协议相关内容（路由、参数绑定、状态码）。</li>
 *     <li>完成最小输入校验（例如文件不能为空）。</li>
 *     <li>将外部请求转换为应用层命令对象，并调用用例。</li>
 *     <li>将应用层返回对象转换为 REST 响应 DTO。</li>
 * </ul>
 *
 * <p>注意：控制器不直接处理领域规则，也不直接访问数据库或第三方 SDK。
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentIngestController {

    /**
     * 上传受理用例。控制器只依赖用例接口，不依赖具体实现，符合依赖倒置原则。
     */
    private final AcceptUploadUseCase acceptUploadUseCase;

    public DocumentIngestController(AcceptUploadUseCase acceptUploadUseCase) {
        this.acceptUploadUseCase = acceptUploadUseCase;
    }

    /**
     * 上传文档并受理入库请求。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：POST /api/v1/documents/upload</li>
     *     <li>请求：multipart/form-data</li>
     *     <li>参数：file（必填），kbId（可选）</li>
     *     <li>响应：UploadResponse（documentId, status）</li>
     * </ul>
     *
     * @param file 上传文件，不能为空
     * @param kbId 知识库 ID，可为空；为空时由应用层解析为默认值
     * @return 上传受理结果，当前状态固定为 ACCEPTED
     * @throws ResponseStatusException 当文件为空时抛出 400 Bad Request
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public UploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kbId", required = false) String kbId) {

        // 这一步是接口安全阀，如果检验失败，就阻止进入业务层，提前返回消息，告诉前端（用户）
        if (file.isEmpty()) {
            // 输入校验失败时，直接返回 400，避免无效请求进入应用层。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
        }

        // 将 HTTP 参数转换为应用层命令对象，隔离接口协议与用例编排。
        AcceptUploadCommand command = new AcceptUploadCommand(file.getOriginalFilename(), file.getSize(), kbId);

        UploadTicket uploadTicket = acceptUploadUseCase.handle(command);
        // 将领域返回对象映射为对外响应 DTO，避免领域对象直接暴露给 API 使用方。
        return new UploadResponse(uploadTicket.documentId().value(), uploadTicket.status().name());
    }
}
