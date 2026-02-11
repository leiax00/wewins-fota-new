package com.wewins.fota.system.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 *
 * @author FOTA Team
 * @since 2026-02-09
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 通用错误码 (10xxx)
    SUCCESS(0, "OK"),
    BAD_REQUEST(40000, "Bad Request"),
    UNAUTHORIZED(40100, "Unauthorized"),
    FORBIDDEN(40300, "Forbidden"),
    NOT_FOUND(40400, "Not Found"),
    CONFLICT(40900, "Conflict"),
    INTERNAL_ERROR(50000, "Internal Server Error"),

    // 认证错误码 (401xx)
    TOKEN_INVALID(40101, "Invalid Token"),
    TOKEN_EXPIRED(40102, "Token Expired"),
    USERNAME_PASSWORD_ERROR(40103, "Username or Password Error"),
    ACCOUNT_DISABLED(40104, "Account Disabled"),
    ACCOUNT_LOCKED(40105, "Account Locked"),

    // 权限错误码 (403xx)
    ACCESS_DENIED(40301, "Access Denied"),
    PERMISSION_INSUFFICIENT(40302, "Permission Insufficient"),

    // 用户错误码 (410xx)
    USER_NOT_FOUND(41001, "User Not Found"),
    USERNAME_EXISTS(41002, "Username Already Exists"),
    EMAIL_EXISTS(41003, "Email Already Exists"),
    PHONE_EXISTS(41004, "Phone Already Exists"),
    PASSWORD_INVALID(41005, "Invalid Password"),

    // 角色错误码 (420xx)
    ROLE_NOT_FOUND(42001, "Role Not Found"),
    ROLE_CODE_EXISTS(42002, "Role Code Already Exists"),
    ROLE_IN_USE(42003, "Role Is In Use"),

    // 权限错误码 (430xx)
    PERMISSION_NOT_FOUND(43001, "Permission Not Found"),
    PERMISSION_CODE_EXISTS(43002, "Permission Code Already Exists"),
    PERMISSION_HAS_CHILDREN(43003, "Permission Has Children"),
    PERMISSION_IN_USE(43004, "Permission Is In Use"),
    PERMISSION_CIRCULAR_REF(43005, "Permission Circular Reference"),

    // 字典错误码 (440xx)
    DICT_TYPE_NOT_FOUND(44001, "Dictionary Type Not Found"),
    DICT_TYPE_CODE_EXISTS(44002, "Dictionary Type Code Already Exists"),
    DICT_TYPE_IN_USE(44003, "Dictionary Type Is In Use"),
    DICT_ITEM_NOT_FOUND(44004, "Dictionary Item Not Found"),
    DICT_ITEM_VALUE_EXISTS(44005, "Dictionary Item Value Already Exists"),
    DICT_ITEM_LABEL_EXISTS(44006, "Dictionary Item Label Already Exists");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;
}
