package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.command.UploadNewDocumentVersionCommand;
import io.github.spike.myai.ingest.application.result.DocumentVersionUploadResult;
import io.github.spike.myai.shared.rest.BusinessException;

/**
 * 上传既有 document 新版本用例。
 *
 * <p>该用例负责：
 * <ol>
 *   <li>校验调用方对目标文档的管理权限；</li>
 *   <li>检查 expectedLatestVersionNumber 避免版本冲突；</li>
 *   <li>检查文档当前状态是否允许上传新版本（仅 INDEXED / FAILED 允许）；</li>
 *   <li>对同内容上传做幂等复用（fileHash 一致时返回 REUSED_IDENTICAL_CONTENT）；</li>
 *   <li>创建新版本事实并持久化源文件。</li>
 * </ol>
 */
public interface UploadNewDocumentVersionUseCase {

    /**
     * 处理上传新版本命令。
     *
     * @param command 上传新版本命令（含 documentId、fileHash、expectedLatestVersionNumber 等）
     * @return 版本上传结果（含新版本号、可问答版本号等）
     * @throws BusinessException 当权限不足、版本冲突或状态不允许时抛出
     */
    DocumentVersionUploadResult handle(UploadNewDocumentVersionCommand command);
}
