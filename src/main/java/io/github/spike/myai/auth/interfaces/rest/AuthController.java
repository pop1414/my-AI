package io.github.spike.myai.auth.interfaces.rest;

import io.github.spike.myai.auth.application.command.LoginCommand;
import io.github.spike.myai.auth.application.result.CurrentUserResult;
import io.github.spike.myai.auth.application.result.CurrentUserCapabilitiesResult;
import io.github.spike.myai.auth.application.usecase.GetCurrentUserCapabilitiesUseCase;
import io.github.spike.myai.auth.application.usecase.LoginUseCase;
import io.github.spike.myai.auth.interfaces.rest.dto.CurrentUserCapabilitiesResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.CurrentUserResponse;
import io.github.spike.myai.auth.interfaces.rest.dto.LoginRequest;
import io.github.spike.myai.auth.interfaces.rest.dto.LoginResponse;
import io.github.spike.myai.auth.security.MyAiPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 认证接口 REST 控制器。
 *
 * <p>提供登录、登出、获取当前用户信息的 HTTP 端点，
 * 位于 {@code /api/v1/auth} 路径下。
 * 本控制器是认证领域（auth）的入站适配器，
 * 负责将 HTTP 请求转换为应用层用例调用，
 * 并将结果封装为 HTTP 响应返回客户端。
 *
 * <p>职责边界：
 * <ul>
 *   <li>请求参数校验（如空 body 检查）；</li>
 *   <li>调用领域用例（LoginUseCase）；</li>
 *   <li>管理 Spring Security 上下文（登录后写入、登出时清除）；</li>
 *   <li>异常到 HTTP 状态码的映射（认证异常 → 401/403）；</li>
 *   <li>领域对象到 DTO 的转换。</li>
 * </ul>
 *
 * @author spike
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** 登录用例，由应用层提供 */
    private final LoginUseCase loginUseCase;

    /** 当前用户能力位解析服务 */
    private final GetCurrentUserCapabilitiesUseCase currentUserCapabilitiesUseCase;

    /** 安全上下文持久化仓库，用于将认证信息写入 HttpSession */
    private final SecurityContextRepository securityContextRepository;

    /** 安全上下文持有器策略，统一管理当前请求线程的 SecurityContext */
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * 构造器注入。
     *
     * @param loginUseCase                    登录用例
     * @param currentUserCapabilitiesUseCase  当前用户能力位解析服务
     * @param securityContextRepository       安全上下文存储
     */
    public AuthController(
            LoginUseCase loginUseCase,
            GetCurrentUserCapabilitiesUseCase currentUserCapabilitiesUseCase,
            SecurityContextRepository securityContextRepository) {
        this.loginUseCase = loginUseCase;
        this.currentUserCapabilitiesUseCase = currentUserCapabilitiesUseCase;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * 用户登录接口。
     *
     * <p>处理流程：
     * <ol>
     *   <li>校验请求体非空；</li>
     *   <li>调用登录用例进行身份验证；</li>
     *   <li>认证成功后构建 {@link Authentication} 对象；</li>
     *   <li>将认证信息写入 Spring Security 上下文并持久化到 HttpSession；</li>
     *   <li>返回包含当前用户信息的登录响应。</li>
     * </ol>
     *
     * <p>异常映射：
     * <ul>
     *   <li>账户锁定/禁用 → HTTP 403 Forbidden；</li>
     *   <li>其他认证失败（如密码错误）→ HTTP 401 Unauthorized。</li>
     * </ul>
     *
     * @param request          登录请求体（JSON），含用户名和密码
     * @param servletRequest   原生 HTTP 请求，用于 Session 操作
     * @param servletResponse  原生 HTTP 响应，用于 Cookie 写入等
     * @return 登录成功响应，包含当前用户信息
     * @throws ResponseStatusException 当请求体为空或认证失败时抛出
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse login(
            @RequestBody(required = false) LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        // 校验请求体不能为空，required = false 不会自动拒绝 null，需要手动判空
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        }
        try {
            // 步骤1：调用登录用例执行认证逻辑
            CurrentUserResult result = loginUseCase.handle(new LoginCommand(request.username(), request.password()));
            // 步骤2：将领域结果转换为 Spring Security 的 Authentication 对象
            Authentication authentication = toAuthentication(result);
            // 步骤3：创建空的安全上下文，存入认证信息
            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            // 步骤4：将上下文绑定到当前线程，并持久化到 HttpSession
            securityContextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, servletRequest, servletResponse);
            // 步骤5：返回登录成功响应
            return new LoginResponse(toResponse(result));
        } catch (LockedException | DisabledException ex) {
            // 账户被锁定或禁用，返回 403 Forbidden（认证通过但无权限）
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        } catch (AuthenticationException ex) {
            // 其他认证异常（密码错误、用户不存在等），返回 401 Unauthorized
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        }
    }

    /**
     * 用户登出接口。
     *
     * <p>处理流程：
     * <ol>
     *   <li>创建一个空的安全上下文覆盖当前上下文，清除认证信息；</li>
     *   <li>将空上下文持久化到 HttpSession（覆盖旧的认证信息）；</li>
     *   <li>如果存在 Session，则显式调用 {@code invalidate()} 销毁；</li>
     *   <li>返回 HTTP 204 No Content。</li>
     * </ol>
     *
     * @param request  原生 HTTP 请求
     * @param response 原生 HTTP 响应
     * @return 空响应体，HTTP 204
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 创建空的安全上下文，用于清除当前认证状态
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        // 将空上下文设置到当前线程，覆盖原有的认证信息
        securityContextHolderStrategy.setContext(context);
        // 将空上下文持久化到 HttpSession，覆盖 Session 中的旧认证数据
        securityContextRepository.saveContext(context, request, response);
        // 显式销毁 Session，彻底清除会话数据
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        // 返回 204 No Content，表示操作成功但无响应体
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取当前登录用户信息接口。
     *
     * <p>从 Spring Security 上下文中提取已认证的用户主体（{@link MyAiPrincipal}），
     * 并返回其基本身份信息。如果未认证或主体类型不匹配，返回 401。
     *
     * @param authentication Spring Security 自动注入的当前认证对象
     * @return 当前用户信息
     * @throws ResponseStatusException 当用户未认证时抛出，HTTP 401
     */
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public CurrentUserResponse me(Authentication authentication) {
        // 校验认证对象存在且主体类型为 MyAiPrincipal
        if (authentication == null || !(authentication.getPrincipal() instanceof MyAiPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication is required");
        }
        // 从主体中提取用户身份信息并封装为响应 DTO
        return toResponse(new CurrentUserResult(
                principal.userId(),
                principal.username(),
                principal.displayName(),
                principal.workspaceId(),
                principal.workspaceRole()));
    }

    /**
     * 将应用层登录结果转换为 Spring Security 的 {@link Authentication} 对象。
     *
     * <p>转换过程中会构建：
     * <ul>
     *   <li>{@link MyAiPrincipal} —— 自定义用户主体，携带业务身份信息；</li>
     *   <li>{@code ROLE_} 前缀的权限标识 —— 基于工作空间角色映射到 Spring Security 权限体系。</li>
     * </ul>
     *
     * @param result 登录用例返回的用户结果
     * @return Spring Security 认证令牌对象
     */
    private static Authentication toAuthentication(CurrentUserResult result) {
        // 构建自定义用户主体
        MyAiPrincipal principal = new MyAiPrincipal(
                result.userId(),
                result.username(),
                result.displayName(),
                result.workspaceId(),
                result.workspaceRole());
        // 构建 UsernamePasswordAuthenticationToken：
        // - principal: 自定义主体
        // - credentials: null（已认证状态下无需密码）
        // - authorities: 将工作空间角色映射为 ROLE_ 前缀的权限
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + result.workspaceRole().name())));
    }

    /**
     * 将应用层登录结果转换为 API 响应 DTO。
     *
     * <p>便捷重载，从 {@link CurrentUserResult} 中提取字段后委托给
     * {@link #toResponse(CurrentUserResult)} toResponse(String, String, String, String, String)}，
     * 确保登录和获取当前用户接口使用统一的转换逻辑。
     *
     * @param result 登录用例返回的用户结果
     * @return 面向客户端的 {@link CurrentUserResponse}，含能力位
     */
    private CurrentUserResponse toResponse(CurrentUserResult result) {
        // 将登录结果直接交给能力位用例，确保 login / me 复用同一套解析逻辑
        CurrentUserCapabilitiesResult capabilitiesResult = currentUserCapabilitiesUseCase.resolve(result);
        // 组装含能力位的响应 DTO
        return new CurrentUserResponse(
                result.userId(),
                result.username(),
                result.displayName(),
                result.workspaceId(),
                result.workspaceRole().name(),
                new CurrentUserCapabilitiesResponse(
                        capabilitiesResult.canAccessDocumentList(),
                        capabilitiesResult.canUploadDocument(),
                        capabilitiesResult.canAccessKnowledge(),
                        capabilitiesResult.canAskQuestion(),
                        capabilitiesResult.canAccessAdmin()));
    }
}
