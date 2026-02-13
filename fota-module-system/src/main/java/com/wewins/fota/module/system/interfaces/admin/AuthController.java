package com.wewins.fota.module.system.interfaces.admin;

import com.wewins.fota.common.context.UserContext;
import com.wewins.fota.security.jwt.JwtUtil;
import com.wewins.fota.security.jwt.SysUserDetails;
import com.wewins.fota.module.system.dto.LoginReqDTO;
import com.wewins.fota.module.system.dto.LoginRespDTO;
import com.wewins.fota.module.system.dto.Response;
import com.wewins.fota.module.system.domain.entity.user.User;
import com.wewins.fota.module.system.application.exception.ErrorCode;
import com.wewins.fota.module.system.application.UserAppService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证 Controller
 * <p>
 * 处理登录、登录等认证相关操作
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Slf4j
@ConditionalOnProperty(name = "app.features.admin", havingValue = "true")
@RestController
@RequestMapping("/api/sys/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserAppService userService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserAppService userService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    /**
     * 登录
     *
     * @param request 登录请求
     * @return 登录响应（包含 JWT Token）
     */
    @PostMapping("/login")
    public Response<LoginRespDTO> login(@Valid @RequestBody LoginReqDTO request) {
        String username = request.getUsername();

        if (log.isDebugEnabled()) {
            log.debug("用户登录: username={}", username);
        }

        // 使用 Spring Security 进行认证
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        username,
                        request.getPassword()
                )
        );

        // 获取用户详情
        SysUserDetails userDetails = (SysUserDetails) auth.getPrincipal();

        // 生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userDetails.getUserId());
        String token = jwtUtil.generateToken(username, claims);

        // 查询用户详细信息
        User user = userService.getUserById(userDetails.getUserId());

        // 构建响应
        LoginRespDTO response = LoginRespDTO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();

        log.info("用户登录成功: username={}, userId={}", username, user.getId());

        return Response.success(response);
    }

    /**
     * 登出
     *
     * @return 登出响应
     */
    @PostMapping("/logout")
    public Response<Void> logout() {
        Long userId = UserContext.getCurrentUserId();

        if (userId == null) {
            log.warn("登出请求未携带有效用户信息");
            return Response.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("用户登出请求: userId={}", userId);
        }

        log.info("用户登出请求已处理: userId={}", userId);

        return Response.success();
    }

    /**
     * 当前用户信息
     *
     * @return 当前用户信息
     */
    @GetMapping("/current")
    public Response<User> current() {
        Long userId = UserContext.getCurrentUserId();

        if (userId == null) {
            log.warn("获取当前用户信息：未登录");
            return Response.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取当前用户信息: userId={}", userId);
        }

        User user = userService.getUserById(userId);

        if (user == null) {
            log.warn("获取当前用户信息：用户不存在, userId={}", userId);
            return Response.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }

        // 安全处理：返回前清空密码哈希
        user.setPasswordHash(null);

        return Response.success(user);
    }
}
