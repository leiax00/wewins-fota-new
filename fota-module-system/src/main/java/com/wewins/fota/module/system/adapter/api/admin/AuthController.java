package com.wewins.fota.module.system.adapter.api.admin;

import com.wewins.fota.common.api.ApiResponse;
import com.wewins.fota.common.condition.ConditionalOnAppMode;
import com.wewins.fota.common.context.UserContext;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.module.system.application.MenuAppService;
import com.wewins.fota.module.system.application.UserAppService;
import com.wewins.fota.module.system.application.assembler.AdminApiAssembler;
import com.wewins.fota.module.system.dto.LoginReqDTO;
import com.wewins.fota.module.system.dto.LoginRespDTO;
import com.wewins.fota.module.system.dto.menu.UserMenuNodeDTO;
import com.wewins.fota.module.system.dto.UserRespDTO;
import com.wewins.fota.security.jwt.JwtUtil;
import com.wewins.fota.security.jwt.SysUserDetails;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证 Controller
 */
@Slf4j
@ConditionalOnAppMode("main")
@RestController
@RequestMapping("/api/sys/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserAppService userService;
    private final AdminApiAssembler adminApiAssembler;
    private final MenuAppService menuAppService;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          UserAppService userService,
                          AdminApiAssembler adminApiAssembler,
                          MenuAppService menuAppService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.adminApiAssembler = adminApiAssembler;
        this.menuAppService = menuAppService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginRespDTO> login(@Valid @RequestBody LoginReqDTO request) {
        String username = request.getUsername();

        if (log.isDebugEnabled()) {
            log.debug("用户登录: username={}", username);
        }

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.getPassword())
        );

        SysUserDetails userDetails = (SysUserDetails) auth.getPrincipal();

        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userDetails.getUserId());
        String token = jwtUtil.generateToken(username, claims);

        var user = userService.getUserById(userDetails.getUserId());

        LoginRespDTO response = LoginRespDTO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .build();

        log.info("用户登录成功: username={}, userId={}", username, user.getId());
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        Long userId = UserContext.getCurrentUserId();

        if (userId == null) {
            log.warn("登出请求未携带有效用户信息");
            return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("用户登出请求: userId={}", userId);
        }

        log.info("用户登出请求已处理: userId={}", userId);
        return ApiResponse.success();
    }

    @GetMapping("/current")
    public ApiResponse<UserRespDTO> current() {
        Long userId = UserContext.getCurrentUserId();

        if (userId == null) {
            log.warn("获取当前用户信息：未登录");
            return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取当前用户信息: userId={}", userId);
        }

        var user = userService.getUserById(userId);
        if (user == null) {
            log.warn("获取当前用户信息：用户不存在, userId={}", userId);
            return ApiResponse.error(ErrorCode.USER_NOT_FOUND.getCode(), ErrorCode.USER_NOT_FOUND.getMessage());
        }

        // 获取角色和权限编码
        List<String> roles = userService.getUserRoleCodes(userId);
        List<String> permissions = userService.getUserPermissionCodes(userId);

        UserRespDTO dto = adminApiAssembler.toUserResp(user);
        dto.setRoles(roles);
        dto.setPermissions(permissions);

        return ApiResponse.success(dto);
    }

    @GetMapping("/user-menu")
    public ApiResponse<List<UserMenuNodeDTO>> userMenu() {
        Long userId = UserContext.getCurrentUserId();

        if (userId == null) {
            log.warn("获取用户菜单：未登录");
            return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        }

        if (log.isDebugEnabled()) {
            log.debug("获取用户菜单: userId={}", userId);
        }

        List<UserMenuNodeDTO> menus = menuAppService.getUserMenuTree(userId);

        if (log.isDebugEnabled()) {
            log.debug("获取用户菜单成功: userId={}, menuCount={}", userId, menus.size());
        }
        return ApiResponse.success(menus);
    }
}
