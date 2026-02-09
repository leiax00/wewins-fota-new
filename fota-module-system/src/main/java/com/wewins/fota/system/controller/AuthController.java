package com.wewins.fota.system.controller;

import com.wewins.fota.system.dto.LoginRequest;
import com.wewins.fota.system.dto.LoginResponse;
import com.wewins.fota.system.dto.Response;
import com.wewins.fota.system.entity.User;
import com.wewins.fota.security.jwt.JwtUtil;
import com.wewins.fota.security.jwt.SysUserDetails;
import com.wewins.fota.system.service.IUserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
@RestController
@RequestMapping("/api/sys/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final IUserService userService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          IUserService userService) {
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
    public Response<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
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
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();

        log.info("用户登录成功: username={}, userId={}", username, user.getId());

        return Response.success(response);
    }
}
