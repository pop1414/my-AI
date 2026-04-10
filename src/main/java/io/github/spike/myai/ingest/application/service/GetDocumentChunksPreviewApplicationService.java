package io.github.spike.myai.ingest.application.service;

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
 * 获取文档分块预览的处理类。
 * 负责根据查询条件从存储库中检索文档分块，并转换为预览结果。
 */
@Service
public class GetDocumentChunksPreviewApplicationService implements GetDocumentChunksPreviewUseCase {

    private final DocumentRepository documentRepository;
    private final DocumentChunkPreviewRepository documentChunkPreviewRepository;

    /**
     * 构造函数。
     *
     * @param documentRepository           文档存储库接口
     * @param documentChunkPreviewRepository 文档分块预览存储库接口
     */
    public GetDocumentChunksPreviewApplicationService(
            DocumentRepository documentRepository,
            DocumentChunkPreviewRepository documentChunkPreviewRepository) {
        this.documentRepository = documentRepository;
        this.documentChunkPreviewRepository = documentChunkPreviewRepository;
    }

    /**
     * 处理获取文档分块预览的查询。
     *
     * @param query 包含文档 ID、限制数量、偏移量和预览字符数的查询对象
     * @return 包含分块预览列表和统计信息的 DocumentChunksPreviewResult
     * @throws DocumentNotFoundException 当指定的文档不存在时抛出
     */
    @Override
    public DocumentChunksPreviewResult handle(GetDocumentChunksPreviewQuery query) {
        DocumentId documentId = new DocumentId(query.documentId());
        // 根据 ID 查找文档，如果不存在则抛出异常。
        var document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            throw new DocumentNotFoundException("document not found: " + documentId.value());
        }

        // 始终使用文档当前 splitVersion，确保预览与当前向量版本一致。
        String splitVersion = document.splitVersion();

        // 先查询该版本下的分块总数，便于前端进行分页或抽样展示的UI计算。
        int totalChunks = documentChunkPreviewRepository.countByDocumentId(documentId, splitVersion);

        // 根据分页参数（limit, offset）检索分块数据，并映射为结果项。
        List<DocumentChunkPreviewItemResult> items = documentChunkPreviewRepository
                .findByDocumentId(documentId, splitVersion, query.limit(), query.offset())
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
                blankToNull(chunk.sourceHint()));
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
