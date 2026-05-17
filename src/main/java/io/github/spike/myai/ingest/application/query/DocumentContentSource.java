package io.github.spike.myai.ingest.application.query;

/**
 * 文档正文读取来源。
 *
 * <p>该枚举表达调用方的正文读取意图，应用层必须按来源分别选择版本、
 * 校验权限并映射错误码，不能把不同来源合并为同一条隐式读取路径。
 */
public enum DocumentContentSource {
    /** 读取当前 latest version 的正文。 */
    LATEST,

    /** 读取当前 QA 可问答基线版本的正文。 */
    ASKABLE_BASELINE,

    /** 读取调用方显式指定版本的正文。 */
    EXPLICIT_VERSION
}
