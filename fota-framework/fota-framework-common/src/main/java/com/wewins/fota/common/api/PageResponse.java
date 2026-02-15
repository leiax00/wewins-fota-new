package com.wewins.fota.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应结果
 *
 * @param <T> 记录类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    /**
     * 记录列表
     */
    private List<T> records;

    /**
     * 当前页码（从 1 开始）
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private int pages;

    public static <T> PageResponse<T> of(List<T> records, int page, int size, long total) {
        int pages = (int) Math.ceil((double) total / size);
        return PageResponse.<T>builder()
                .records(records)
                .page(page)
                .size(size)
                .total(total)
                .pages(pages)
                .build();
    }

    public static <T> PageResponse<T> empty() {
        return PageResponse.<T>builder()
                .records(List.of())
                .page(1)
                .size(20)
                .total(0)
                .pages(0)
                .build();
    }
}
