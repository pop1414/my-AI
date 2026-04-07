package io.github.spike.myai.ingest.infrastructure.parser;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 文本清洗服务。
 *
 * <p>职责：
 * <ul>
 *     <li>去除解析噪音（图片名、临时路径、分隔线等）。</li>
 *     <li>规范换行与空白，输出更稳定的 chunk 输入。</li>
 * </ul>
 */
@Component
public class TextCleaningService {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\t]]");
    private static final Pattern IMAGE_FILENAME_LINE =
            Pattern.compile("(?im)^\\s*image\\d+\\.(png|jpg|jpeg|gif|bmp|webp)\\s*$");
    private static final Pattern IMAGE_URL =
            Pattern.compile("(?im)https?://\\S+\\.(png|jpg|jpeg|gif|bmp|webp)(\\?\\S*)?");
    private static final Pattern FILE_URL = Pattern.compile("(?im)file:///\\S+");
    private static final Pattern SEPARATOR_LINE = Pattern.compile("(?m)^\\s*[-_=]{3,}\\s*$");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");

    /**
     * 对解析后的原始文本执行二次清洗。
     *
     * @param rawText 原始解析文本
     * @return 清洗后的文本
     */
    public String cleanText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String text = rawText;
        // 去除控制字符，避免污染后续分块与向量化输入。
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        // 语义去噪：图片文件名、图片 URL、本地临时路径、分隔线。
        text = IMAGE_FILENAME_LINE.matcher(text).replaceAll("");
        text = IMAGE_URL.matcher(text).replaceAll("");
        text = FILE_URL.matcher(text).replaceAll("");
        text = SEPARATOR_LINE.matcher(text).replaceAll("");

        // 格式规范化：统一换行、压缩多空格和多空行。
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }
}

