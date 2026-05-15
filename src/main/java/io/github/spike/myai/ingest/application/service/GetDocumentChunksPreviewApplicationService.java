package io.github.spike.myai.ingest.application.service;

import io.github.spike.myai.auth.application.context.CurrentUser;
import io.github.spike.myai.auth.application.context.CurrentUserProvider;
import io.github.spike.myai.auth.application.service.AuthorizationService;
import io.github.spike.myai.ingest.application.exception.DocumentNotFoundException;
import io.github.spike.myai.ingest.application.query.GetDocumentChunksPreviewQuery;
import io.github.spike.myai.ingest.application.result.DocumentChunkPreviewItemResult;
import io.github.spike.myai.ingest.application.result.DocumentChunksPreviewResult;
import io.github.spike.myai.ingest.application.usecase.GetDocumentChunksPreviewUseCase;
import io.github.spike.myai.ingest.domain.model.DocumentChunkPreview;
import io.github.spike.myai.ingest.domain.model.DocumentId;
import io.github.spike.myai.ingest.domain.port.DocumentChunkPreviewRepository;
import io.github.spike.myai.ingest.domain.port.DocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 文档分块预览查询应用服务（Application Service）。
 *
 * <p>该服务实现 {@link GetDocumentChunksPreviewUseCase} 用例接口，
 * 负责根据查询条件检索文档分块数据并转换为预览结果。
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>校验文档是否存在；</li>
 *   <li>获取文档当前的 {@code splitVersion}，确保预览与向量版本一致；</li>
 *   <li>查询分块总数（用于前端分页计算）；</li>
 *   <li>分页查询分块数据并按预览字符数截断；</li>
 *   <li>封装为应用层结果返回。</li>
 * </ol>
 *
 * @author Spike
 * @since 1.0.0
 */
@Service
public class GetDocumentChunksPreviewApplicationService implements GetDocumentChunksPreviewUseCase {

    /**
     * 文档仓储端口：用于校验文档存在性与获取当前 splitVersion。
     *
     * <p>splitVersion 用于确保预览的分块与当前向量索引版本一致。
     */
    private final DocumentRepository documentRepository;

    /** 分块预览仓储端口：用于按文档 ID + splitVersion 分页查询分块数据 */
    private final DocumentChunkPreviewRepository documentChunkPreviewRepository;

    /** 当前用户上下文提供器：用于获取工作区标识 */
    private final CurrentUserProvider currentUserProvider;

    /** 授权服务：用于校验当前用户是否可读取该文档 */
    private final AuthorizationService authorizationService;

    /**
     * 构造器注入。
     *
     * @param documentRepository            文档仓储（领域端口）
     * @param documentChunkPreviewRepository 文档分块预览仓储（领域端口）
     * @param currentUserProvider           当前用户上下文提供器（应用层端口）
     * @param authorizationService          授权服务（应用层）
     */
    public GetDocumentChunksPreviewApplicationService(
            DocumentRepository documentRepository,
            DocumentChunkPreviewRepository documentChunkPreviewRepository,
            CurrentUserProvider currentUserProvider,
            AuthorizationService authorizationService) {
        this.documentRepository = documentRepository;
        this.documentChunkPreviewRepository = documentChunkPreviewRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
    }

    /**
     * 处理文档分块预览查询。
     *
     * @param query 包含文档 ID、limit、offset 和预览字符数的查询对象
     * @return 包含分块预览列表和统计信息的结果
     * @throws DocumentNotFoundException 当指定文档不存在时
     */
    @Override
    public DocumentChunksPreviewResult handle(GetDocumentChunksPreviewQuery query) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        DocumentId documentId = new DocumentId(query.documentId());
        String workspaceId = currentUser.workspaceId();
        // 根据 ID 查找文档，如果不存在则抛出异常。
        var document = documentRepository.findById(workspaceId, documentId).orElse(null);
        if (document == null) {
            throw new DocumentNotFoundException("document not found: " + documentId.value());
        }
        authorizationService.requireCanReadDocument(currentUser, documentId.value(), document.kbId());

        // 始终使用文档当前 splitVersion，确保预览与当前向量版本一致。
        String splitVersion = document.splitVersion();

        // 先查询该版本下的分块总数，便于前端进行分页或抽样展示的UI计算。
        int totalChunks = documentChunkPreviewRepository.countByDocumentId(workspaceId, documentId, splitVersion);

        // 根据分页参数（limit, offset）检索分块数据，并映射为结果项。
        List<DocumentChunkPreviewItemResult> items = documentChunkPreviewRepository
                .findByDocumentId(workspaceId, documentId, splitVersion, query.limit(), query.offset())
                .stream()
                .map(chunk -> toItemResult(chunk, query.previewChars()))
                .toList();

        // 返回分块预览的封装结果。
        return new DocumentChunksPreviewResult(
                documentId,
                items.size(),
                totalChunks,
                query.limit(),
                query.offset(),
                query.previewChars(),
                items);
    }

    /**
     * 将领域模型转换为结果传输对象（DTO）。
     *
     * @param chunk        分块领域模型
     * @param previewChars 需要截取的预览字符数
     * @return 转换后的预览项结果
     */
    private static DocumentChunkPreviewItemResult toItemResult(DocumentChunkPreview chunk, int previewChars) {
        // 统一在后端应用截断规则，避免前端重复实现相同的展示逻辑。
        String preview = truncateForPreview(chunk.content(), previewChars);
        // 标记内容是否已被截断。
        boolean truncated = chunk.content() != null && chunk.content().length() > previewChars;

        return new DocumentChunkPreviewItemResult(
                chunk.chunkIndex(),
                chunk.contentLength(),
                preview,
                truncated,
                chunk.sourceFile(),
                chunk.contentHash(),
                chunk.splitVersion(),
                blankToNull(chunk.sourceHint().toStorageValue()));
    }

    /**
     * 执行文本截断逻辑。
     *
     * @param content      原始内容
     * @param previewChars 允许的最大字符数
     * @return 截断后的字符串，末尾附带省略号
     */
    private static String truncateForPreview(String content, int previewChars) {
        if (content == null) {
            return "";
        }
        if (content.length() <= previewChars) {
            return content;
        }
        return content.substring(0, previewChars) + "...";
    }

    /**
     * 将空白字符串转换为 null，以便 API 输出更加整洁。
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
