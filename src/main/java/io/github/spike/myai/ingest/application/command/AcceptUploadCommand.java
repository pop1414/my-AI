package io.github.spike.myai.ingest.application.command;

/**
 * “受理上传”用例的输入命令对象（Application Layer DTO）。
 *
 * <p>说明：
 * <ul>
 *     <li>该对象用于承载应用层执行用例所需的最小输入数据。</li>
 *     <li>它是应用层内部协议，不等同于 REST 请求对象。</li>
 *     <li>通过命令对象可避免应用层直接依赖 Web 框架类型。</li>
 * </ul>
 *
 * @param filename 原始文件名（可为空，取决于客户端上传行为）
 * @param fileSize 文件大小（字节）
 * @param kbId 知识库 ID（可为空，默认值由应用服务解析）
 */
public record AcceptUploadCommand(String filename, long fileSize, String kbId) {
}
