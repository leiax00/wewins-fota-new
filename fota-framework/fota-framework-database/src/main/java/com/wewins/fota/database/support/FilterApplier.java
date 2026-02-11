package com.wewins.fota.database.support;

import com.wewins.fota.common.dto.FilterCondition;
import com.wewins.fota.common.dto.FilterOperator;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 * 统一的过滤条件应用器
 * <p>
 * 封装对 FilterCondition 的解析和 operator 分发，避免重复代码
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
public final class FilterApplier {

    private FilterApplier() {
    }

    /**
     * 过滤处理器接口（函数式）
     *
     * @param <C> 列类型（String 或 SFunction）
     */
    public interface FilterHandler<C> {
        void eq(C column, Object value);

        void ne(C column, Object value);

        void like(C column, String value);

        void gt(C column, Object value);

        void lt(C column, Object value);

        void ge(C column, Object value);

        void le(C column, Object value);

        void between(C column, Object start, Object end);

        void in(C column, Collection<?> values);

        void inArray(C column, Object[] values);

        void isNull(C column);

        void isNotNull(C column);
    }

    /**
     * 应用过滤条件
     *
     * @param filters   过滤条件 Map
     * @param resolver   字段名解析器（String → 列类型）
     * @param handler    过滤处理器
     * @param <C>        列类型
     */
    public static <C> void applyFiltersIfPresent(Map<String, FilterCondition> filters,
                                          Function<String, C> resolver,
                                          FilterHandler<C> handler) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, FilterCondition> entry : filters.entrySet()) {
            String fieldName = entry.getKey();
            FilterCondition condition = entry.getValue();
            if (condition == null || condition.getOperator() == null) {
                continue;
            }
            C column = resolver.apply(fieldName);
            if (column == null) {
                continue;
            }
            FilterOperator operator = condition.getOperator();
            Object value = condition.getValue();
            switch (operator) {
                case EQ:
                    if (value != null) {
                        handler.eq(column, value);
                    }
                    break;
                case NE:
                    if (value != null) {
                        handler.ne(column, value);
                    }
                    break;
                case LIKE:
                    if (value instanceof String && StringUtils.hasText((String) value)) {
                        handler.like(column, (String) value);
                    }
                    break;
                case GT:
                    if (value != null) {
                        handler.gt(column, value);
                    }
                    break;
                case LT:
                    if (value != null) {
                        handler.lt(column, value);
                    }
                    break;
                case GE:
                    if (value != null) {
                        handler.ge(column, value);
                    }
                    break;
                case LE:
                    if (value != null) {
                        handler.le(column, value);
                    }
                    break;
                case BETWEEN:
                    if (value != null && condition.getEndValue() != null) {
                        handler.between(column, value, condition.getEndValue());
                    }
                    break;
                case IN:
                    if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
                        handler.in(column, (Collection<?>) value);
                    } else if (value != null && value.getClass().isArray()) {
                        handler.inArray(column, (Object[]) value);
                    }
                    break;
                case IS_NULL:
                    handler.isNull(column);
                    break;
                case NOT_NULL:
                    handler.isNotNull(column);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的操作符: " + operator);
            }
        }
    }
}
