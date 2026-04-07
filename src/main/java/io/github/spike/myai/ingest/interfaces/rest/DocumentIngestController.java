package io.github.spike.myai.ingest.interfaces.rest;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunkPreviewItemResult;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentChunksPreviewUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
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
     * 文档源文件存储端口。
     */
    private final DocumentSourceStorage documentSourceStorage;

    public DocumentIngestController(
            AcceptUploadUseCase acceptUploadUseCase,
            GetDocumentStatusUseCase getDocumentStatusUseCase,
            GetDocumentChunksPreviewUseCase getDocumentChunksPreviewUseCase,
            DocumentSourceStorage documentSourceStorage) {
        this.acceptUploadUseCase = acceptUploadUseCase;
        this.getDocumentStatusUseCase = getDocumentStatusUseCase;
        this.getDocumentChunksPreviewUseCase = getDocumentChunksPreviewUseCase;
        this.documentSourceStorage = documentSourceStorage;
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

        String fileHash = calculateFileHash(file);
        // 将 HTTP 参数转换为应用层命令对象，隔离接口协议与用例编排。
        AcceptUploadCommand command = new AcceptUploadCommand(file.getOriginalFilename(), file.getSize(), kbId, fileHash);

        UploadTicket uploadTicket = acceptUploadUseCase.handle(command);
        try {
            // 受理成功后立即持久化源文件，供异步处理链路（解析/分块/向量化）读取。
            // 当命中幂等复用既有 documentId 时，这里会走存储端口的幂等写入（存在则不覆盖）。
            documentSourceStorage.save(uploadTicket.documentId(), file.getOriginalFilename(), file.getBytes());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read upload file", ex);
        }
        // 将领域返回对象映射为对外响应 DTO，避免领域对象直接暴露给 API 使用方。
        return new UploadResponse(uploadTicket.documentId().value(), uploadTicket.status().name());
    }

    /**
     * 查询文档当前处理状态。
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
            DocumentStatusResult result =
                    getDocumentStatusUseCase.handle(new GetDocumentStatusQuery(documentId));
            return new DocumentStatusResponse(result.documentId().value(), result.status().name());
        } catch (DocumentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    /**
     * 查询文档分块预览（调试接口）。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：GET /api/v1/documents/{documentId}/chunks/preview</li>
     *     <li>参数：limit（可选，默认20，最大200）</li>
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
            @RequestParam(value = "previewChars", defaultValue = "200") int previewChars) {
        try {
            DocumentChunksPreviewResult result = getDocumentChunksPreviewUseCase.handle(
                    new GetDocumentChunksPreviewQuery(documentId, limit, previewChars));
            return new DocumentChunksPreviewResponse(
                    result.documentId().value(),
                    result.chunkCount(),
                    result.chunks().stream().map(DocumentIngestController::toChunkPreviewResponse).toList());
        } catch (DocumentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private static DocumentChunkPreviewItemResponse toChunkPreviewResponse(DocumentChunkPreviewItemResult item) {
        return new DocumentChunkPreviewItemResponse(
                item.chunkIndex(),
                item.contentPreview(),
                item.sourceFile(),
                item.contentHash(),
                item.splitVersion());
    }

    /**
     * 计算上传文件的 SHA-256 哈希。
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
