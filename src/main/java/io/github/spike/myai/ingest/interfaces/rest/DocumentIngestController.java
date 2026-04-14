package io.github.spike.myai.ingest.interfaces.rest;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;
import io.github.spike.myai.ingest.application.command.ReprocessDocumentCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteConflictException;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteFailedException;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunkPreviewItemResult;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.application.usecase.DeleteDocumentUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentChunksPreviewUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.application.usecase.ReprocessDocumentUseCase;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import org.springframework.http.ResponseEntity;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.domain.port.DocumentSourceStorage;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentChunkPreviewItemResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentChunksPreviewResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentStatusResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.UploadResponse;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    /**
     * 文档状态查询用例。
     */
    private final GetDocumentStatusUseCase getDocumentStatusUseCase;
    /**
     * 文档分块预览查询用例。
     */
    private final GetDocumentChunksPreviewUseCase getDocumentChunksPreviewUseCase;
    /**
     * 文档重处理用例。
     */
    private final ReprocessDocumentUseCase reprocessDocumentUseCase;
    /**
     * 文档删除用例。
     */
    private final DeleteDocumentUseCase deleteDocumentUseCase;
    /**
     * 文档源文件存储端口。
     */
    private final DocumentSourceStorage documentSourceStorage;

    public DocumentIngestController(
            AcceptUploadUseCase acceptUploadUseCase,
            GetDocumentStatusUseCase getDocumentStatusUseCase,
            GetDocumentChunksPreviewUseCase getDocumentChunksPreviewUseCase,
            ReprocessDocumentUseCase reprocessDocumentUseCase,
            DeleteDocumentUseCase deleteDocumentUseCase,
            DocumentSourceStorage documentSourceStorage) {
        this.acceptUploadUseCase = acceptUploadUseCase;
        this.getDocumentStatusUseCase = getDocumentStatusUseCase;
        this.getDocumentChunksPreviewUseCase = getDocumentChunksPreviewUseCase;
        this.reprocessDocumentUseCase = reprocessDocumentUseCase;
        this.deleteDocumentUseCase = deleteDocumentUseCase;
        this.documentSourceStorage = documentSourceStorage;
    }

    /**
     * 上传文档并受理入库请求。
     * 接收客户端的上传文件并将其转化为内部命令以开始入库流程。
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
        // 校验上传的文件内容是否为空，为空则直接抛异常拦截。
        if (file.isEmpty()) {
            // 输入校验失败时，直接返回 400，避免无效请求进入应用层。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
        }

        // 计算上传文件的内容哈希（SHA-256），用于后续判断是否重复文件或者走秒传逻辑。
        String fileHash = calculateFileHash(file);
        // 将 HTTP 参数转换为应用层命令对象，隔离接口协议与用例编排。
        AcceptUploadCommand command = new AcceptUploadCommand(file.getOriginalFilename(), file.getSize(), kbId, fileHash);

        // 调用应用层的处理逻辑处理上传命令。
        UploadTicket uploadTicket = acceptUploadUseCase.handle(command);
        try {
            // 受理成功后立即持久化源文件，供异步处理链路（解析/分块/向量化）读取。
            // 当命中幂等复用既有 documentId 时，这里会走存储端口的幂等写入（存在则不覆盖）。
            documentSourceStorage.save(uploadTicket.documentId(), file.getOriginalFilename(), file.getBytes());
        } catch (IOException ex) {
            // IO 异常处理，抛出 BAD_REQUEST 返回前端。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read upload file", ex);
        }
        // 将领域返回对象映射为对外响应 DTO，避免领域对象直接暴露给 API 使用方。
        return new UploadResponse(uploadTicket.documentId().value(), uploadTicket.status().name());
    }

    /**
     * 查询文档当前处理状态。
     * 供前端轮询或者回调触发查询当前文档的分析入库状态（比如提取进度，是否完成了向量化等）。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：GET /api/v1/documents/{documentId}/status</li>
     *     <li>响应：DocumentStatusResponse（documentId, status）</li>
     * </ul>
     *
     * @param documentId 文档资产 ID
     * @return 状态查询结果
     */
    @GetMapping(value = "/{documentId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentStatusResponse getStatus(@PathVariable("documentId") String documentId) {
        try {
            // 委派给应用层服务进行状态查询逻辑。
            DocumentStatusResult result =
                    getDocumentStatusUseCase.handle(new GetDocumentStatusQuery(documentId));
            return new DocumentStatusResponse(result.documentId().value(), result.status().name());
        } catch (DocumentNotFoundException ex) {
            // 捕获未找到文档异常，向前端转化为 404 NOT FOUND 状态码。
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    /**
     * 查询文档分块预览（调试接口）。
     * 在处理文档提取完成后，用此接口可以查看知识库如何将文档文本进行切片（Chunk）分割的详细内容。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：GET /api/v1/documents/{documentId}/chunks/preview</li>
     *     <li>参数：limit（可选，默认20，最大200）</li>
     *     <li>参数：offset（可选，默认0）</li>
     *     <li>参数：previewChars（可选，默认200，范围20~2000）</li>
     * </ul>
     *
     * @param documentId 文档资产 ID
     * @param limit 最大返回条数
     * @param previewChars 每个分块的预览字符数
     * @return 分块预览结果
     */
    @GetMapping(value = "/{documentId}/chunks/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentChunksPreviewResponse getChunksPreview(
            @PathVariable("documentId") String documentId,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "previewChars", defaultValue = "200") int previewChars) {
        try {
            // 统一由应用层校验 limit/offset/previewChars 的范围，控制器只负责参数转发。
            DocumentChunksPreviewResult result = getDocumentChunksPreviewUseCase.handle(
                    new GetDocumentChunksPreviewQuery(documentId, limit, offset, previewChars));
            return new DocumentChunksPreviewResponse(
                    result.documentId().value(),
                    result.chunkCount(),
                    result.totalChunks(),
                    result.limit(),
                    result.offset(),
                    result.previewChars(),
                    result.chunks().stream().map(DocumentIngestController::toChunkPreviewResponse).toList());
        } catch (DocumentNotFoundException ex) {
            // 如果文档不存在则返回 404。
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 如果请求参数不合法（如 offset 过大，limit 超过最大限制等）则返回 400。
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 将业务层返回的分块预览领域对象（Result）映射到 REST API 返回对象（Response）。
     */
    private static DocumentChunkPreviewItemResponse toChunkPreviewResponse(DocumentChunkPreviewItemResult item) {
        // DTO 映射：保持对外结构稳定，内部字段可演进。
        return new DocumentChunkPreviewItemResponse(
                item.chunkIndex(),
                item.contentLength(),
                item.contentPreview(),
                item.truncated(),
                item.sourceFile(),
                item.contentHash(),
                item.splitVersion(),
                item.sourceHint());
    }

    /**
     * 触发文档重处理。
     * 对于处于失败状态或需要重新切片的文档，调用此接口重新触发整套入库解析流程。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：POST /api/v1/documents/{documentId}/reprocess</li>
     * </ul>
     */
    @PostMapping(value = "/{documentId}/reprocess", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentStatusResponse reprocess(@PathVariable("documentId") String documentId) {
        try {
            // 重处理只修改状态并进入队列，不在接口层做同步向量重建。
            DocumentStatusResult result = reprocessDocumentUseCase.handle(new ReprocessDocumentCommand(documentId));
            return new DocumentStatusResponse(result.documentId().value(), result.status().name());
        } catch (DocumentNotFoundException ex) {
            // 如果找不到该文档标识，返回 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数或者基础校验失败返回 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            // 比如正在处理中的文档不可以重处理，这种属于当前状态非法（冲突），返回 409 CONFLICT
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * 删除文档资产（源文件 + 向量）。
     *
     * <p>接口契约：
     * <ul>
     *   <li>路径：DELETE /api/v1/documents/{documentId}</li>
     *   <li>响应：204 No Content（删除成功或幂等删除）</li>
     * </ul>
     */
    @DeleteMapping(value = "/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable("documentId") String documentId) {
        try {
            deleteDocumentUseCase.handle(new DeleteDocumentCommand(documentId));
            return ResponseEntity.noContent().build();
        } catch (DocumentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (DocumentDeleteConflictException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (DocumentDeleteFailedException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 计算上传文件的 SHA-256 哈希。
     * 多次读取利用 java.security.MessageDigest 计算分块哈希值。
     */
    private static String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read upload file", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "sha-256 not available", ex);
        }
    }
}
