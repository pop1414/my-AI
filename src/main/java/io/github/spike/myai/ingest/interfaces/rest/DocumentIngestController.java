package io.github.spike.myai.ingest.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.application.command.DeleteDocumentCommand;
import io.github.spike.myai.ingest.application.command.ReprocessDocumentCommand;
import io.github.spike.myai.ingest.application.command.RollbackDocumentVersionCommand;
import io.github.spike.myai.ingest.application.command.UploadNewDocumentVersionCommand;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteConflictException;
import io.github.spike.myai.ingest.application.exception.DocumentDeleteFailedException;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.DocumentContentSource;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.query.GetDocumentContentQuery;
import io.github.spike.myai.ingest.application.query.GetDocumentStatusQuery;
import io.github.spike.myai.ingest.application.query.ListDocumentVersionsQuery;
import io.github.spike.myai.ingest.application.query.ListDocumentsQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunkPreviewItemResult;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.application.result.DocumentContentResult;
import io.github.spike.myai.ingest.application.result.DocumentListItemResult;
import io.github.spike.myai.ingest.application.result.DocumentListPageResult;
import io.github.spike.myai.ingest.application.result.DocumentVersionRollbackResult;
import io.github.spike.myai.ingest.application.result.DocumentVersionUploadResult;
import io.github.spike.myai.ingest.application.usecase.AcceptUploadUseCase;
import io.github.spike.myai.ingest.application.usecase.DeleteDocumentUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentChunksPreviewUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentContentUseCase;
import io.github.spike.myai.ingest.application.usecase.GetDocumentStatusUseCase;
import io.github.spike.myai.ingest.application.usecase.ListDocumentVersionsUseCase;
import io.github.spike.myai.ingest.application.usecase.ListDocumentsUseCase;
import io.github.spike.myai.ingest.application.usecase.ReprocessDocumentUseCase;
import io.github.spike.myai.ingest.application.usecase.RollbackDocumentVersionUseCase;
import io.github.spike.myai.ingest.application.usecase.UploadNewDocumentVersionUseCase;
import io.github.spike.myai.ingest.application.result.DocumentStatusResult;
import org.springframework.http.ResponseEntity;
import io.github.spike.myai.ingest.domain.model.UploadTicket;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentChunkPreviewItemResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentChunksPreviewResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentContentResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentListItemResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentListPageResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentStatusResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentVersionHistoryItemResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentVersionHistoryResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentVersionRollbackResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.DocumentVersionUploadResponse;
import io.github.spike.myai.ingest.interfaces.rest.dto.UploadResponse;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseInactiveException;
import io.github.spike.myai.knowledge.application.exception.KnowledgeBaseNotFoundException;
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
 *
 * @author Spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentIngestController {

    /**
     * 上传受理用例。控制器只依赖用例接口，不依赖具体实现，符合依赖倒置原则。
     */
    private final AcceptUploadUseCase acceptUploadUseCase;
    /**
     * 文档列表查询用例。
     */
    private final ListDocumentsUseCase listDocumentsUseCase;
    /**
     * 文档状态查询用例。
     */
    private final GetDocumentStatusUseCase getDocumentStatusUseCase;
    /**
     * 文档版本历史查询用例。
     */
    private final ListDocumentVersionsUseCase listDocumentVersionsUseCase;
    /**
     * 文档分块预览查询用例。
     */
    private final GetDocumentChunksPreviewUseCase getDocumentChunksPreviewUseCase;
    /**
     * 文档 latest 正文读取用例。
     */
    private final GetDocumentContentUseCase getDocumentContentUseCase;
    /**
     * 文档重处理用例。
     */
    private final ReprocessDocumentUseCase reprocessDocumentUseCase;
    /**
     * 文档删除用例。
     */
    private final DeleteDocumentUseCase deleteDocumentUseCase;
    /**
     * 上传新版本用例。
     */
    private final UploadNewDocumentVersionUseCase uploadNewDocumentVersionUseCase;
    /**
     * 版本回退用例。
     */
    private final RollbackDocumentVersionUseCase rollbackDocumentVersionUseCase;
    /**
     * JSON 映射器：用于解析 processing_metadata 字段。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入（Spring 推荐方式）。
     *
     * <p>控制器仅依赖用例接口而非具体实现，
     * 符合依赖倒置原则（DIP），便于单元测试时注入 Mock。
     *
     * @param acceptUploadUseCase            上传受理用例
     * @param listDocumentsUseCase           文档列表查询用例
     * @param getDocumentStatusUseCase       文档状态查询用例
     * @param listDocumentVersionsUseCase    文档版本历史查询用例
     * @param getDocumentChunksPreviewUseCase 文档分块预览用例
     * @param getDocumentContentUseCase      文档 latest 正文读取用例
     * @param reprocessDocumentUseCase       文档重处理用例
     * @param deleteDocumentUseCase          文档删除用例
     * @param uploadNewDocumentVersionUseCase 上传新版本用例
     * @param rollbackDocumentVersionUseCase 版本回退用例
     * @param objectMapper                   Jackson JSON 映射器
     */
    public DocumentIngestController(
            AcceptUploadUseCase acceptUploadUseCase,
            ListDocumentsUseCase listDocumentsUseCase,
            GetDocumentStatusUseCase getDocumentStatusUseCase,
            ListDocumentVersionsUseCase listDocumentVersionsUseCase,
            GetDocumentChunksPreviewUseCase getDocumentChunksPreviewUseCase,
            GetDocumentContentUseCase getDocumentContentUseCase,
            ReprocessDocumentUseCase reprocessDocumentUseCase,
            DeleteDocumentUseCase deleteDocumentUseCase,
            UploadNewDocumentVersionUseCase uploadNewDocumentVersionUseCase,
            RollbackDocumentVersionUseCase rollbackDocumentVersionUseCase,
            ObjectMapper objectMapper) {
        this.acceptUploadUseCase = acceptUploadUseCase;
        this.listDocumentsUseCase = listDocumentsUseCase;
        this.getDocumentStatusUseCase = getDocumentStatusUseCase;
        this.listDocumentVersionsUseCase = listDocumentVersionsUseCase;
        this.getDocumentChunksPreviewUseCase = getDocumentChunksPreviewUseCase;
        this.getDocumentContentUseCase = getDocumentContentUseCase;
        this.reprocessDocumentUseCase = reprocessDocumentUseCase;
        this.deleteDocumentUseCase = deleteDocumentUseCase;
        this.uploadNewDocumentVersionUseCase = uploadNewDocumentVersionUseCase;
        this.rollbackDocumentVersionUseCase = rollbackDocumentVersionUseCase;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询文档分页列表。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：GET /api/v1/documents</li>
     *     <li>参数：kbId（可选，精确匹配）</li>
     *     <li>参数：status（可选，默认排除 DELETED）</li>
     *     <li>参数：filename（可选，模糊匹配）</li>
     *     <li>参数：limit（可选，默认20，范围1~100）</li>
     *     <li>参数：offset（可选，默认0，必须大于等于0）</li>
     * </ul>
     */
    @GetMapping(value = {"", "/"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentListPageResponse listDocuments(
            @RequestParam(value = "kbId", required = false) String kbId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {
        try {
            DocumentListPageResult result = listDocumentsUseCase.handle(
                    new ListDocumentsQuery(kbId, status, filename, limit, offset));
            return new DocumentListPageResponse(
                    result.items().stream().map(DocumentIngestController::toDocumentListItemResponse).toList(),
                    result.total(),
                    result.limit(),
                    result.offset());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
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
        // 调用应用层的处理逻辑处理上传命令。
        // 异常映射：领域异常 → HTTP 状态码，控制器负责语义转换
        UploadTicket uploadTicket;
        try {
            // 将 HTTP 参数转换为应用层命令对象，隔离接口协议与用例编排。
            AcceptUploadCommand command = new AcceptUploadCommand(
                    file.getOriginalFilename(),
                    file.getSize(),
                    kbId,
                    fileHash,
                    file.getBytes());
            uploadTicket = acceptUploadUseCase.handle(command);
        } catch (KnowledgeBaseNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (KnowledgeBaseInactiveException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
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
            return toDocumentStatusResponse(result);
        } catch (DocumentNotFoundException ex) {
            // 捕获未找到文档异常，向前端转化为 404 NOT FOUND 状态码。
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }

    /**
     * 查询文档版本历史。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：GET /api/v1/documents/{documentId}/versions</li>
     *     <li>排序：versionNumber DESC</li>
     *     <li>权限：当前用户必须对目标文档具备管理权限</li>
     * </ul>
     *
     * @param documentId 文档资产 ID
     * @return 文档版本历史
     */
    @GetMapping(value = "/{documentId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentVersionHistoryResponse listVersions(@PathVariable("documentId") String documentId) {
        try {
            var result = listDocumentVersionsUseCase.handle(new ListDocumentVersionsQuery(documentId));
            return new DocumentVersionHistoryResponse(
                    result.documentId(),
                    result.sort(),
                    result.versions().stream()
                            .map(item -> new DocumentVersionHistoryItemResponse(
                                    item.documentId(),
                                    item.versionNumber(),
                                    item.versionOriginType(),
                                    item.rollbackFromVersionNumber(),
                                    item.filename(),
                                    item.fileSize(),
                                    item.status(),
                                    item.failureReason(),
                                    item.createdByUserId(),
                                    item.createdByDisplayName(),
                                    item.createdAt(),
                                    item.updatedAt(),
                                    item.isLatestVersion(),
                                    item.isAskableVersion()))
                            .toList());
        } catch (DocumentNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 查询文档正文。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：GET /api/v1/documents/{documentId}/content?source=LATEST|ASKABLE_BASELINE|EXPLICIT_VERSION</li>
     *     <li>{@code LATEST}：固定读取当前 latest version 的 {@code cleaned.md}</li>
     *     <li>{@code ASKABLE_BASELINE}：读取当前 QA 可问答基线版本的 {@code cleaned.md}</li>
     *     <li>{@code EXPLICIT_VERSION}：读取 versionNumber 指定历史版本的 {@code cleaned.md}</li>
     * </ul>
     *
     * @param documentId    文档资产 ID
     * @param source        正文来源
     * @param versionNumber 显式版本读取时的目标版本号
     * @return 正文响应
     */
    @GetMapping(value = "/{documentId}/content", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentContentResponse getContent(
            @PathVariable("documentId") String documentId,
            @RequestParam("source") String source,
            @RequestParam(value = "versionNumber", required = false) Integer versionNumber) {
        DocumentContentSource contentSource = parseDocumentContentSource(source);
        if (contentSource == DocumentContentSource.EXPLICIT_VERSION && versionNumber == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "versionNumber is required when source is EXPLICIT_VERSION");
        }
        if (contentSource == DocumentContentSource.EXPLICIT_VERSION && versionNumber <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "versionNumber must be positive");
        }
        if (contentSource != DocumentContentSource.EXPLICIT_VERSION && versionNumber != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "versionNumber is only allowed when source is EXPLICIT_VERSION");
        }
        DocumentContentResult result = getDocumentContentUseCase.handle(
                new GetDocumentContentQuery(documentId, contentSource, versionNumber));
        return toDocumentContentResponse(result);
    }

    /**
     * 解析正文来源参数。
     *
     * @param source HTTP 查询参数
     * @return 应用层正文来源枚举
     * @throws ResponseStatusException 当 source 非法时返回 400
     */
    private static DocumentContentSource parseDocumentContentSource(String source) {
        try {
            return DocumentContentSource.valueOf(source);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "source must be LATEST, ASKABLE_BASELINE or EXPLICIT_VERSION",
                    ex);
        }
    }

    /**
     * 针对既有 document 上传新版本。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：POST /api/v1/documents/{documentId}/versions</li>
     *     <li>请求：multipart/form-data</li>
     *     <li>参数：file（必填），expectedLatestVersionNumber（必填）</li>
     * </ul>
     */
    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentVersionUploadResponse uploadNewVersion(
            @PathVariable("documentId") String documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("expectedLatestVersionNumber") int expectedLatestVersionNumber) {
        // 安全阀：文件为空时直接拦截，避免无效请求进入应用层
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
        }

        // 计算文件 SHA-256 哈希，供应用层做同内容幂等复用判断
        String fileHash = calculateFileHash(file);
        try {
            // 组装命令对象并委派给应用层用例处理
            DocumentVersionUploadResult result = uploadNewDocumentVersionUseCase.handle(
                    new UploadNewDocumentVersionCommand(
                            documentId,
                            file.getOriginalFilename(),
                            file.getSize(),
                            fileHash,
                            expectedLatestVersionNumber,
                            file.getBytes()));
            // 将应用层结果映射为 REST 响应 DTO
            return toDocumentVersionUploadResponse(result);
        } catch (IllegalArgumentException ex) {
            // 参数校验异常 → 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IOException ex) {
            // 文件读取异常 → 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read upload file", ex);
        }
    }

    /**
     * 将指定历史版本回退为新的最新版本。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：POST /api/v1/documents/{documentId}/versions/{versionNumber}/rollback</li>
     *     <li>参数：expectedLatestVersionNumber（必填）</li>
     *     <li>语义：创建 ROLLBACK 来源的新 latest 版本，不回拨历史版本指针</li>
     * </ul>
     *
     * @param documentId                  文档资产 ID
     * @param versionNumber               回退目标历史版本号
     * @param expectedLatestVersionNumber 调用方页面看到的最新版本号
     * @return 版本回退结果
     */
    @PostMapping(value = "/{documentId}/versions/{versionNumber}/rollback", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentVersionRollbackResponse rollbackVersion(
            @PathVariable("documentId") String documentId,
            @PathVariable("versionNumber") int versionNumber,
            @RequestParam("expectedLatestVersionNumber") int expectedLatestVersionNumber) {
        try {
            DocumentVersionRollbackResult result = rollbackDocumentVersionUseCase.handle(
                    new RollbackDocumentVersionCommand(
                            documentId,
                            versionNumber,
                            expectedLatestVersionNumber));
            return toDocumentVersionRollbackResponse(result);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
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
     * 将应用层文档列表项结果映射为 REST 响应 DTO。
     *
     * <p>该映射为纯字段对字段的一一映射，不涉及业务逻辑计算，
     * 仅做 DTO 隔离，确保领域模型不直接暴露给 API 消费者。
     *
     * @param item 应用层文档列表项结果
     * @return REST 响应 DTO
     */
    private static DocumentListItemResponse toDocumentListItemResponse(DocumentListItemResult item) {
        // DTO 映射：保持对外结构稳定，内部字段可演进
        return new DocumentListItemResponse(
                item.documentId(),
                item.kbId(),
                item.latestVersionNumber(),
                item.latestVersionOriginType(),
                item.filename(),
                item.fileSize(),
                item.status(),
                item.failureReason(),
                item.createdAt(),
                item.updatedAt());
    }

    /**
     * 将应用层正文读取结果映射为 REST 响应 DTO。
     *
     * @param result 应用层正文读取结果
     * @return REST 正文响应
     */
    private static DocumentContentResponse toDocumentContentResponse(DocumentContentResult result) {
        return new DocumentContentResponse(
                result.documentId(),
                result.versionNumber(),
                result.latestVersionNumber(),
                result.isLatestVersion(),
                result.isAskableVersion(),
                result.source(),
                result.status(),
                result.filename(),
                result.createdAt(),
                result.updatedAt(),
                result.contentMarkdown(),
                result.contentLength(),
                result.truncated());
    }

    /**
     * 将应用层版本上传结果映射为 REST 响应 DTO。
     *
     * <p>该映射方法将 {@link DocumentVersionUploadResult} 中的域字段
     * 一一平迁到 {@link DocumentVersionUploadResponse}，
     * 确保领域模型不直接暴露给 API 消费者。
     *
     * @param result 应用层版本上传结果
     * @return REST 响应 DTO
     */
    private static DocumentVersionUploadResponse toDocumentVersionUploadResponse(DocumentVersionUploadResult result) {
        return new DocumentVersionUploadResponse(
                result.documentId(),
                result.versionCreated(),
                result.versionResultType(),
                result.versionNumber(),
                result.previousVersionNumber(),
                result.reusedLatestVersionNumber(),
                result.latestVersionNumber(),
                result.askableVersionNumber(),
                result.canAskNow(),
                result.status(),
                result.versionOriginType());
    }

    /**
     * 将应用层版本回退结果映射为 REST 响应 DTO。
     *
     * @param result 应用层版本回退结果
     * @return REST 响应 DTO
     */
    private static DocumentVersionRollbackResponse toDocumentVersionRollbackResponse(DocumentVersionRollbackResult result) {
        return new DocumentVersionRollbackResponse(
                result.documentId(),
                result.versionNumber(),
                result.rollbackFromVersionNumber(),
                result.latestVersionNumber(),
                result.askableVersionNumber(),
                result.canAskNow(),
                result.status(),
                result.versionOriginType());
    }

    /**
     * 将应用层状态查询结果转换为 REST 响应 DTO。
     *
     * <p>该映射方法负责：
     * <ol>
     *   <li>提取 documentId 和 status 基础字段；</li>
     *   <li>调用 {@link #parseProcessingMetadata(String)} 将 JSON 字符串解析为结构化对象，
     *       确保 API 响应中 processingMetadata 以 JSON 对象形式呈现而非原始字符串。</li>
     * </ol>
     *
     * @param result 应用层状态查询结果
     * @return REST 响应 DTO
     */
    private DocumentStatusResponse toDocumentStatusResponse(DocumentStatusResult result) {
        return new DocumentStatusResponse(
                result.documentId().value(),
                result.kbId(),
                result.latestVersionNumber(),
                result.latestFilename(),
                result.latestVersionOriginType().name(),
                result.status().name(),
                parseProcessingMetadata(result.processingMetadata()));
    }

    /**
     * 将 processing_metadata JSON 字符串解析为 Jackson {@link JsonNode} 树结构。
     *
     * <p>解析策略：
     * <ul>
     *   <li>当字符串为 {@code null} 或空白时，返回 {@code null}（JSON 序列化时该字段不会被包含）；</li>
     *   <li>正常 JSON 字符串通过 {@link ObjectMapper#readTree(String)} 解析为结构化节点；</li>
     *   <li>解析失败时抛出 {@link IllegalStateException}，由上层统一异常处理转换为 500 响应。</li>
     * </ul>
     *
     * @param processingMetadata 数据库中的 processing_metadata JSON 字符串
     * @return 解析后的 JSON 树节点，可能为 null
     * @throws IllegalStateException 当 JSON 格式非法时
     */
    private JsonNode parseProcessingMetadata(String processingMetadata) {
        // 空值或空白字符串直接返回 null，不在响应体中输出该字段。
        if (processingMetadata == null || processingMetadata.isBlank()) {
            return null;
        }
        try {
            // 使用 Jackson 将 JSON 字符串解析为树结构，便于后续序列化。
            return objectMapper.readTree(processingMetadata);
        } catch (IOException ex) {
            // JSON 解析失败说明数据库中存在脏数据，属于严重异常，直接抛出。
            throw new IllegalStateException("invalid processing metadata json", ex);
        }
    }

    /**
     * 触发文档重处理。
     * 对于处于失败状态或需要重新切片的文档，调用此接口重新触发整套入库解析流程。
     *
     * <p>接口契约：
     * <ul>
     *     <li>路径：POST /api/v1/documents/{documentId}/reprocess</li>
     *     <li>参数：expectedLatestVersionNumber（可选，传入时用于识别过期页面请求）</li>
     * </ul>
     */
    @PostMapping(value = "/{documentId}/reprocess", produces = MediaType.APPLICATION_JSON_VALUE)
    public DocumentStatusResponse reprocess(
            @PathVariable("documentId") String documentId,
            @RequestParam(value = "expectedLatestVersionNumber", required = false) Integer expectedLatestVersionNumber) {
        try {
            // 重处理只修改状态并进入队列，不在接口层做同步向量重建。
            DocumentStatusResult result = reprocessDocumentUseCase.handle(
                    new ReprocessDocumentCommand(documentId, expectedLatestVersionNumber));
            return toDocumentStatusResponse(result);
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
     *   <li>参数：expectedLatestVersionNumber（可选，传入时用于识别过期页面请求）</li>
     *   <li>响应：204 No Content（删除成功或幂等删除）</li>
     * </ul>
     *
     * <p>异常映射策略：
     * <ul>
     *   <li>{@code DocumentNotFoundException} → 404，资源不存在；</li>
     *   <li>{@code DocumentDeleteConflictException} → 409，文档状态冲突（如处理中不可删除）；</li>
     *   <li>{@code DocumentDeleteFailedException} → 500，基础设施层删除失败；</li>
     *   <li>{@code IllegalArgumentException} → 400，参数校验失败。</li>
     * </ul>
     *
     * @param documentId 文档资产 ID
     * @return 204 No Content（无响应体）
     */
    @DeleteMapping(value = "/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable("documentId") String documentId,
            @RequestParam(value = "expectedLatestVersionNumber", required = false) Integer expectedLatestVersionNumber) {
        try {
            // 委派给应用层用例处理删除逻辑（含源文件清理、向量删除、状态检查）
            deleteDocumentUseCase.handle(new DeleteDocumentCommand(documentId, expectedLatestVersionNumber));
            // 删除成功返回 204，无响应体
            return ResponseEntity.noContent().build();
        } catch (DocumentNotFoundException ex) {
            // 文档不存在 → 404
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (DocumentDeleteConflictException ex) {
            // 文档状态冲突（如正在处理中不可删除）→ 409
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (DocumentDeleteFailedException ex) {
            // 基础设施层删除失败（存储异常等）→ 500
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            // 参数校验失败 → 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    /**
     * 计算上传文件的 SHA-256 哈希值。
     *
     * <p>采用流式分块读取策略，避免将整个文件加载到内存：
     * <ol>
     *   <li>获取 {@link MessageDigest} SHA-256 实例；</li>
     *   <li>以 8KB 缓冲区流式读取文件输入流；</li>
     *   <li>每次读取后调用 {@code digest.update()} 增量更新哈希；</li>
     *   <li>全部读取完毕后调用 {@code digest.digest()} 获取最终哈希；</li>
     *   <li>使用 {@link HexFormat} 将字节数组转为十六进制小写字符串。</li>
     * </ol>
     *
     * <p>缓冲区大小 {@code 8192}（8KB）是经验值，
     * 在内存占用与系统调用次数之间取得平衡。
     *
     * @param file 上传的文件
     * @return SHA-256 哈希值的十六进制字符串（64 字符，小写）
     */
    private static String calculateFileHash(MultipartFile file) {
        try {
            // 1. 获取 SHA-256 消息摘要实例
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // 2. 流式读取文件，避免全量加载到内存
            try (InputStream inputStream = file.getInputStream()) {
                // 8KB 缓冲区，平衡内存占用与 I/O 次数
                byte[] buffer = new byte[8192];
                int bytesRead;

                // 3. 循环读取并增量更新哈希值
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);  // 仅处理实际读取的字节数
                }
            } // try-with-resources 自动关闭输入流

            // 4. 计算最终哈希并格式化为十六进制小写字符串
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            // 文件读取失败（如流中断、文件被删除）→ 400
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read upload file", ex);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 算法不可用（JVM 配置异常，正常情况不会发生）→ 500
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "sha-256 not available", ex);
        }
    }
}
