package com.wewins.fota.system.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果
 *
 * @param <T> 数据类型
 * @author FOTA Team
 * @since 2026-02-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Response<T> {

    /**
     * 响应码（0 表示成功）
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 成功响应（无数据）
     */
    public static <T> Response<T> success() {
        return Response.<T>builder()
                .code(0)
                .message("OK")
                .build();
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(0)
                .message("OK")
                .data(data)
                .build();
    }

    /**
     * 成功响应（带消息和数据）
     */
    public static <T> Response<T> success(String message, T data) {
        return Response.<T>builder()
                .code(0)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 失败响应
     */
    public static <T> Response<T> error(int code, String message) {
        return Response.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    /**
     * 失败响应（带数据）
     */
    public static <T> Response<T> error(int code, String message, T data) {
        return Response.<T>builder()
                .code(code)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return code == 0;
    }
}
