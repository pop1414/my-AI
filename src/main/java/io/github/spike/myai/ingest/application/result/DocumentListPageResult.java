package io.github.spike.myai.ingest.application.result;

import java.util.List;

/**
 * 文档列表分页结果。
 */
public record DocumentListPageResult(
        List<DocumentListItemResult> items,
        long total,
        int limit,
        int offset) {
}
