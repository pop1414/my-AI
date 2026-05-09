package io.github.spike.myai.auth.application.usecase;

import io.github.spike.myai.auth.application.command.LoginCommand;
import io.github.spike.myai.auth.application.result.CurrentUserResult;

/**
 * 登录用例接口（入站端口）。
 *
 * <p>定义应用层登录业务的核心契约。入站适配器（控制器）仅依赖此接口，
 * 不感知具体实现细节，符合六边形架构（端口-适配器）中的端口抽象原则。
 *
 * <p>当前唯一实现为 {@link io.github.spike.myai.auth.application.service.LoginApplicationService}。
 *
 * @author spike
 * @since 1.0.0
 */
public interface LoginUseCase {

    /**
     * 执行登录业务逻辑。
     *
     * <p>典型处理流程：
     * <ol>
     *   <li>参数校验（用户名/密码非空）；</li>
     *   <li>根据用户名查找本地账户；</li>
     *   <li>校验账户状态（是否禁用、是否锁定）；</li>
     *   <li>比对密码哈希值；</li>
     *   <li>记录登录成功/失败的审计事件；</li>
     *   <li>更新失败计数器（失败时）；</li>
     *   <li>返回当前用户结果。</li>
     * </ol>
     *
     * @param command 登录命令对象，包含用户名和明文密码
     * @return 登录成功后的当前用户信息
     * @throws org.springframework.security.authentication.BadCredentialsException 凭证无效（密码错误或用户不存在）
     * @throws org.springframework.security.authentication.DisabledException      账户已禁用
     * @throws org.springframework.security.authentication.LockedException        账户已锁定（连续登录失败超限）
     */
    CurrentUserResult handle(LoginCommand command);
}
