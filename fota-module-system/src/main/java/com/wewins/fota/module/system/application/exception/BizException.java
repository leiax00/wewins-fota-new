package com.wewins.fota.module.system.application.exception;

import com.wewins.fota.common.exception.BaseBizException;

/**
 * 业务异常基类
 *
 * @author FOTA Team
 * @since 2026-02-09
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
