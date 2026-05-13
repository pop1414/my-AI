package io.github.spike.myai.ingest.infrastructure.chunking;

import io.github.spike.myai.ingest.domain.model.DocumentChunk;
import io.github.spike.myai.ingest.domain.port.DocumentChunker;
import io.github.spike.myai.ingest.infrastructure.config.IngestProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 结构优先 + 长度兜底的确定性分块器。
 *
 * <p>新增能力：
 * <ul>
 *   <li>提取标题上下文，写入 sourceHint（JSON 字符串）。</li>
 *   <li>支持 Markdown 标题与常见中文标题模式。</li>
 * </ul>
 */
@Component
public class StructuredFallbackDocumentChunker implements DocumentChunker {

    private final int chunkSize;
    private final int overlapSize;
    // Markdown 标题：# / ## / ###
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    // 中文标题：第X章/节/篇
    private static final Pattern CHINESE_HEADING = Pattern.compile("^(第[\\p{IsHan}0-9]+[章节篇])\\s*(.*)$");
    // 数字标题：1. / 1.1 / 1.1.1
    private static final Pattern NUMBER_HEADING = Pattern.compile("^\\d+(?:\\.\\d+)*\\s+.+$");
    // HTML 清洗后常见的独立短标题：无 Markdown #，但独占一段。
    private static final Pattern PLAIN_STANDALONE_HEADING =
            Pattern.compile("^[\\p{IsHan}A-Za-z0-9][\\p{IsHan}A-Za-z0-9\\s/（）()：:-]{1,47}$");

    public StructuredFallbackDocumentChunker(IngestProperties ingestProperties) {
        this.chunkSize = ingestProperties.getChunk().getChunkSize();
        this.overlapSize = ingestProperties.getChunk().getOverlapSize();
    }

    @Override
    public List<DocumentChunk> chunk(String text) {
        // 先归一化换行，保证相同输入在不同平台下分块结果稳定。
        String normalized = normalize(text);
        // 结构优先：先按“段落空行”切成语义片段，再做长度兜底。
        List<String> segments = splitByStructure(normalized);
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> currentTokens = new ArrayList<>();
        // 记录“当前标题上下文”，用于写入 sourceHint。
        String currentHeading = null;
        String currentChunkHeading = null;

        for (String segment : segments) {
            String heading = extractHeading(segment);
            if (heading != null) {
                // 标题出现时，优先刷出当前 chunk，保证标题边界稳定。
                if (!currentTokens.isEmpty()) {
                    currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                }
                currentHeading = heading;
                currentChunkHeading = currentHeading;
            } else if (currentTokens.isEmpty()) {
                // 无标题段落，沿用上一段标题上下文。
                currentChunkHeading = currentHeading;
            }
            List<String> segmentTokens = tokenize(segment);
            if (segmentTokens.isEmpty()) {
                continue;
            }

            // 单段过长时，不与其它段混排，直接走窗口切分，保证边界可预测。
            if (segmentTokens.size() > chunkSize) {
                if (!currentTokens.isEmpty()) {
                    currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                }
                // 过长段落单独切分，避免跨段混排。
                chunkLongSegment(chunks, segmentTokens, currentHeading);
                continue;
            }

            // 当前 chunk 还能容纳时继续拼接，尽量保留段落完整性。
            if (currentTokens.size() + segmentTokens.size() <= chunkSize) {
                if (!currentTokens.isEmpty()) {
                    // 这里人为加一个分隔 token，避免相邻段落无缝粘连。
                    currentTokens.add("\n");
                }
                currentTokens.addAll(segmentTokens);
            } else {
                // 放不下则先刷出当前 chunk，再以当前段作为新 chunk 起点。
                currentChunkHeading = flushChunk(chunks, currentTokens, currentChunkHeading);
                currentTokens.addAll(segmentTokens);
                currentChunkHeading = currentHeading;
            }
        }

        if (!currentTokens.isEmpty()) {
            String content = renderTokens(currentTokens);
            // 末尾 chunk 同样写入 sourceHint。
            chunks.add(new DocumentChunk(content, toSourceHintJson(currentChunkHeading)));
        }
        return chunks;
    }

    /**
     * 对超长段落执行滑动窗口切分。
     *
     * <p>当单段 token 数超过 chunkSize 时，使用固定步长（chunkSize - overlapSize）
     * 的滑动窗口将其切分为多个 chunk。窗口重叠保证相邻 chunk 之间的语义连续性，
     * 避免关键信息落在 chunk 边界附近被截断。
     *
     * @param chunks         目标 chunk 列表（输出参数）
     * @param segmentTokens  超长段落的 token 序列
     * @param heading        当前标题上下文，用于写入 sourceHint
     */
    private void chunkLongSegment(List<DocumentChunk> chunks, List<String> segmentTokens, String heading) {
        // 滑动窗口步长 = chunkSize - overlapSize，确保相邻 chunk 有语义重叠。
        int step = Math.max(1, chunkSize - overlapSize);
        for (int start = 0; start < segmentTokens.size(); start += step) {
            int end = Math.min(segmentTokens.size(), start + chunkSize);
            List<String> window = segmentTokens.subList(start, end);
            String content = renderTokens(window);
            // 长段切分时同样保留标题上下文。
            chunks.add(new DocumentChunk(content, toSourceHintJson(heading)));
            if (end == segmentTokens.size()) {
                break;
            }
        }
    }

    /**
     * 将当前累积的 tokens 刷出为一个 chunk，并保留尾部 overlap 作为下一 chunk 前缀。
     *
     * <p>刷出后清空 currentTokens 并回填 overlap tokens，确保相邻 chunk
     * 之间存在语义重叠（overlap），返回当前标题上下文供调用方继续使用。
     *
     * @param chunks         目标 chunk 列表（输出参数）
     * @param currentTokens  当前累积的 token 列表（会被清空并回填 overlap）
     * @param currentHeading 当前标题上下文
     * @return 返回原标题上下文（供调用方继续传递给后续 chunk）
     */
    private String flushChunk(List<DocumentChunk> chunks, List<String> currentTokens, String currentHeading) {
        String chunk = renderTokens(currentTokens);
        // flush 时写入 sourceHint，保证 chunk 级别可追踪。
        chunks.add(new DocumentChunk(chunk, toSourceHintJson(currentHeading)));

        // 刷出后保留尾部 overlap tokens，作为下一个 chunk 的前缀上下文。
        List<String> overlapTokens = takeTail(currentTokens, overlapSize);
        currentTokens.clear();
        currentTokens.addAll(overlapTokens);
        return currentHeading;
    }

    /**
     * 从 token 列表尾部截取指定数量的元素。
     *
     * <p>用于在刷出 chunk 后保留尾部 overlap tokens，作为下一个 chunk 的语义前缀。
     *
     * @param tokens   源 token 列表
     * @param tailSize 需保留的尾部 token 数量
     * @return 尾部 token 子列表；列表为空时返回空列表
     */
    private static List<String> takeTail(List<String> tokens, int tailSize) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, tokens.size() - tailSize);
        return new ArrayList<>(tokens.subList(from, tokens.size()));
    }

    /**
     * 统一文本换行符为 LF 并去除首尾空白。
     *
     * <p>将 Windows (CRLF) 和旧版 Mac (CR) 风格的换行符归一化为 Unix (LF) 风格，
     * 保证不同平台下的输入能产生一致的 chunk 结果。
     *
     * @param text 原始文本
     * @return 归一化换行符并 trim 后的文本
     */
    private static String normalize(String text) {
        // 统一 CRLF/CR 为 LF，降低平台差异对分块边界的影响。
        return text.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    /**
     * 按段落空行将文本切分为语义片段。
     *
     * <p>以连续两个及以上换行符（即段落间空行）作为分割标志，
     * 将文本拆分为独立的段落级片段，每个片段内部保持语义完整性。
     *
     * @param text 归一化后的文本
     * @return 段落级片段列表（已过滤空段）
     */
    private static List<String> splitByStructure(String text) {
        return Arrays.stream(text.split("\\n\\s*\\n"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .toList();
    }

    /**
     * 将文本按空白字符切分为 token 序列。
     *
     * <p>使用 {@code \s+} 作为分隔符，将文本拆分为单词/字符级的 token 列表，
     * 作为 chunk 组装的基本单位。空 token 会被过滤。
     *
     * @param text 待分词的文本片段
     * @return token 序列
     */
    private static List<String> tokenize(String text) {
        return Arrays.stream(text.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 将 token 序列渲染为纯文本字符串。
     *
     * <p>使用单个空格连接所有非空 token，生成连续的可读文本。
     * 此渲染方式保证了 chunk 内容在不同分块场景下的一致性。
     *
     * @param tokens token 序列
     * @return 以空格连接的文本内容
     */
    private static String renderTokens(List<String> tokens) {
        return tokens.stream()
                .filter(token -> !token.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
    }

    /**
     * 从文本片段中提取标题行。
     *
     * <p>按优先级依次匹配以下标题模式：
     * <ol>
     *   <li>Markdown 标题（# / ## / ### 等）；</li>
     *   <li>中文标题（第X章 / 第X节 / 第X篇 等）；</li>
     *   <li>数字标题（1. / 1.1 / 1.1.1 等）。</li>
     * </ol>
     * 取片段首行进行匹配，匹配成功则返回标题文本，否则返回 {@code null}。
     *
     * @param segment 段落级文本片段
     * @return 提取到的标题文本，或 {@code null}
     */
    private static String extractHeading(String segment) {
        String firstLine = firstLine(segment);
        Matcher markdown = MARKDOWN_HEADING.matcher(firstLine);
        if (markdown.matches()) {
            return markdown.group(1).trim();
        }
        Matcher chinese = CHINESE_HEADING.matcher(firstLine);
        if (chinese.matches()) {
            return firstLine.trim();
        }
        Matcher number = NUMBER_HEADING.matcher(firstLine);
        if (number.matches()) {
            return firstLine.trim();
        }
        if (isPlainStandaloneHeading(segment, firstLine)) {
            return firstLine.trim();
        }
        return null;
    }

    private static boolean isPlainStandaloneHeading(String segment, String firstLine) {
        String trimmed = segment.strip();
        if (!trimmed.equals(firstLine) || firstLine.length() > 48) {
            return false;
        }
        if (firstLine.startsWith("* ")
                || firstLine.startsWith("- ")
                || firstLine.startsWith("|")
                || firstLine.startsWith("```")
                || firstLine.endsWith("。")
                || firstLine.endsWith("！")
                || firstLine.endsWith("？")
                || firstLine.endsWith(".")
                || firstLine.endsWith(",")) {
            return false;
        }
        return PLAIN_STANDALONE_HEADING.matcher(firstLine).matches();
    }

    /**
     * 获取文本片段的首行内容。
     *
     * <p>取片段中第一个换行符之前的内容并去除首尾空白，
     * 用于标题提取和内容预览。
     *
     * @param segment 文本片段
     * @return 片段的首行内容（已 trim）
     */
    private static String firstLine(String segment) {
        String trimmed = segment.strip();
        int idx = trimmed.indexOf('\n');
        if (idx >= 0) {
            return trimmed.substring(0, idx).trim();
        }
        return trimmed;
    }

    /**
     * 将标题文本编码为 sourceHint JSON 字符串。
     *
     * <p>输出格式为 {@code {"heading":"xxx"}}，方便前端/下游做结构化展示。
     * 标题为空时返回 {@code null}，表示无标题上下文。
     *
     * @param heading 标题文本
     * @return sourceHint JSON 字符串，或 {@code null}
     */
    private static String toSourceHintJson(String heading) {
        if (heading == null || heading.isBlank()) {
            return null;
        }
        // 统一用 JSON 字符串，方便前端做结构化展示。
        return "{\"heading\":\"" + escapeJson(heading) + "\"}";
    }

    /**
     * 对 JSON 字符串中的特殊字符进行转义。
     *
     * <p>当前实现仅转义双引号（{@code "}）和反斜杠（{@code \}），
     * 保证生成的 JSON 字符串在嵌套场景下语法合法。
     *
     * @param text 原始文本
     * @return 转义后的安全 JSON 值
     */
    private static String escapeJson(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
