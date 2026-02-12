package com.wewins.fota.module.system.exception;

import com.wewins.fota.module.system.dto.Response;
import com.wewins.fota.security.exception.TokenExpiredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * <p>
 * 统一处理系统异常，返回标准响应格式
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Response<Object>> handleBizException(BizException e) {
        if (log.isDebugEnabled()) {
            log.debug("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理参数校验异常（@Valid）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        if (log.isDebugEnabled()) {
            log.debug("参数校验失败: {}", message);
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.error(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Response<Object>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        if (log.isDebugEnabled()) {
            log.debug("参数绑定失败: {}", message);
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.error(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Response<Object>> handleAuthenticationException(AuthenticationException e) {
        if (log.isDebugEnabled()) {
            log.debug("认证失败: {}", e.getMessage());
        }

        // 根据具体异常类型返回不同的错误码
        if (e instanceof BadCredentialsException) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Response.error(ErrorCode.USERNAME_PASSWORD_ERROR.getCode(),
                            ErrorCode.USERNAME_PASSWORD_ERROR.getMessage()));
        } else if (e instanceof DisabledException) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Response.error(ErrorCode.ACCOUNT_DISABLED.getCode(),
                            ErrorCode.ACCOUNT_DISABLED.getMessage()));
        } else if (e instanceof LockedException) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Response.error(ErrorCode.ACCOUNT_LOCKED.getCode(),
                            ErrorCode.ACCOUNT_LOCKED.getMessage()));
        }

        // 其他认证异常
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Response.error(ErrorCode.UNAUTHORIZED.getCode(), e.getMessage()));
    }

    /**
     * 处理 Token 过期异常
     */
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<Response<Object>> handleTokenExpiredException(TokenExpiredException e) {
        if (log.isDebugEnabled()) {
            log.debug("Token 已过期: {}", e.getMessage());
        }

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Response.error(ErrorCode.TOKEN_EXPIRED.getCode(),
                        ErrorCode.TOKEN_EXPIRED.getMessage()));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        if (log.isDebugEnabled()) {
            log.debug("非法参数: {}", e.getMessage());
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Response.error(ErrorCode.BAD_REQUEST.getCode(), e.getMessage()));
    }

    /**
     * 处理未知异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Object>> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Response.error(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage()));
    }
}
