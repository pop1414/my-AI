package io.github.spike.myai.ingest.application.usecase;

import io.github.spike.myai.ingest.application.command.AcceptUploadCommand;
import io.github.spike.myai.ingest.domain.model.UploadTicket;

/**
 * 受理上传用例接口（Application Layer UseCase）。
 *
 * <p>作用：
 * <ul>
 *     <li>定义系统能力，而不是技术实现细节。</li>
 *     <li>供接口层（Controller）调用，隔离具体实现。</li>
 *     <li>便于后续替换实现、增加事务或编排逻辑而不影响调用方。</li>
 * </ul>
 */
public interface AcceptUploadUseCase {

    /**
     * 执行“受理上传”用例。
     *
     * @param command 用例输入参数
     * @return 上传受理票据（包含 documentId 与当前状态）
     */
    UploadTicket handle(AcceptUploadCommand command);
}
