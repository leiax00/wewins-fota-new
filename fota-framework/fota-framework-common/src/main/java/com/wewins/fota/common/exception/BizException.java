package com.wewins.fota.common.exception;

/**
 * 通用业务异常。
 *
 * <p>业务模块可直接抛出该异常，或基于该异常继续派生细分异常类型。</p>
 */
public class BizException extends BaseBizException {

    public BizException(int code, String message) {
        super(code, message);
    }

    public BizException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public BizException(ErrorCode errorCode) {
        super(errorCode.getCode(), errorCode.getMessage());
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getCode(), errorCode.getMessage(), cause);
    }
}
