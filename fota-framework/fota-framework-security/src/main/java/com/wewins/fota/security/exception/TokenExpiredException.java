package com.wewins.fota.security.exception;

/**
 * Token 过期异常
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
public class TokenExpiredException extends RuntimeException {

    public TokenExpiredException(String message) {
        super(message);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
