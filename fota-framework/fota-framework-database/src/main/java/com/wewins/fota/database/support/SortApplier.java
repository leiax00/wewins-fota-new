package com.wewins.fota.database.support;

import com.wewins.fota.common.dto.SortingField;

import java.util.List;
import java.util.function.Function;

/**
 * 统一的排序应用器
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
public final class SortApplier {

    private SortApplier() {
    }

    /**
     * 排序处理器接口（函数式）
     *
     * @param <C> 列类型（String 或 SFunction）
     */
    public interface SortHandler<C> {
        void orderByAsc(C column);

        void orderByDesc(C column);
    }

    /**
     * 应用排序
     *
     * @param sortingFields 排序字段列表
     * @param resolver      字段名解析器（String → 列类型）
     * @param handler       排序处理器
     * @param <C>           列类型
     */
    public static <C> void applySortingIfPresent(List<SortingField> sortingFields,
                                          Function<String, C> resolver,
                                          SortHandler<C> handler) {
        if (sortingFields == null || sortingFields.isEmpty()) {
            return;
        }
        for (SortingField sf : sortingFields) {
            C column = resolver.apply(sf.getField());
            if (column == null) {
                continue;
            }
            boolean asc = "ASC".equalsIgnoreCase(sf.getOrder());
            if (asc) {
                handler.orderByAsc(column);
            } else {
                handler.orderByDesc(column);
            }
        }
    }
}
