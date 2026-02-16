package com.wewins.fota.common.exception;

import lombok.Getter;

/**
 * 通用业务异常基类。
 */
@Getter
public class BaseBizException extends RuntimeException {

    private final int code;

    public BaseBizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseBizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
