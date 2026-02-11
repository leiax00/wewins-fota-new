package com.wewins.fota.database.wrapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wewins.fota.common.dto.FilterCondition;
import com.wewins.fota.common.dto.FilterOperator;
import com.wewins.fota.common.dto.SortingField;
import com.wewins.fota.database.support.ColumnResolver;
import com.wewins.fota.database.support.FilterApplier;
import com.wewins.fota.database.support.SortApplier;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * QueryWrapper 扩展（字符串列名 API）
 * <p>
 * 提供基于字符串列名的链式查询方法，支持通用过滤和排序
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * new QueryWrapperX<>(User.class)
 *     .likeIfPresent("username", keyword)
 *     .eqIfPresent("status", status)
 *     .applyFiltersIfPresent(filters)
 *     .applySortingIfPresent(sortingFields)
 * }</pre>
 * </p>
 *
 * @param <T> 实体类型
 * @author FOTA Team
 * @since 2026-02-11
 */
public class QueryWrapperX<T> extends QueryWrapper<T> {

    private final Class<T> entityClass;

    /**
     * 构造函数
     *
     * @param entityClass 实体类，不能为 null
     * @throws IllegalArgumentException 如果 entityClass 为 null
     */
    public QueryWrapperX(Class<T> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("实体类不能为空");
        }
        this.entityClass = entityClass;
    }

    /**
     * 模糊查询（值为空时跳过）
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public QueryWrapperX<T> likeIfPresent(String column, String value) {
        if (!StringUtils.hasText(value)) {
            return this;
        }
        return (QueryWrapperX<T>) like(column, value);
    }

    /**
     * 等值查询（值为 null 时跳过）
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public QueryWrapperX<T> eqIfPresent(String column, Object value) {
        if (value == null) {
            return this;
        }
        return (QueryWrapperX<T>) eq(column, value);
    }

    /**
     * 不等查询（值为 null 时跳过）
     *
     * @param column 列名
     * @param value  值
     * @return this
     */
    public QueryWrapperX<T> neIfPresent(String column, Object value) {
        if (value == null) {
            return this;
        }
        return (QueryWrapperX<T>) ne(column, value);
    }

    /**
     * 范围查询（start 或 end 为 null 时跳过）
     *
     * @param column 列名
     * @param start  起始值
     * @param end    结束值
     * @return this
     */
    public QueryWrapperX<T> betweenIfPresent(String column, Object start, Object end) {
        if (start == null || end == null) {
            return this;
        }
        return (QueryWrapperX<T>) between(column, start, end);
    }

    /**
     * IN 查询（集合为空时跳过）
     *
     * @param column 列名
     * @param values 值集合
     * @return this
     */
    public QueryWrapperX<T> inIfPresent(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        return (QueryWrapperX<T>) in(column, values);
    }

    /**
     * 多字段 OR 模糊搜索
     * <p>
     * 当 keyword 为空或 fieldNames 为空时，不添加此条件。
     * 生成的 SQL 类似：WHERE (col1 LIKE '%keyword%' OR col2 LIKE '%keyword%')
     * </p>
     * <p>
     * 通过 ColumnResolver 将实体字段名（驼峰）自动转换为数据库列名（下划线）
     * </p>
     *
     * @param keyword    关键词
     * @param fieldNames 实体字段名数组（驼峰命名）
     * @return this
     * @throws IllegalArgumentException 如果字段不存在或不是持久化字段
     */
    public QueryWrapperX<T> likeAnyIfPresent(String keyword, String... fieldNames) {
        if (!StringUtils.hasText(keyword) || fieldNames == null || fieldNames.length == 0) {
            return this;
        }

        // 将字段名转换为列名
        List<String> validColumns = new ArrayList<>();
        for (String fieldName : fieldNames) {
            if (fieldName != null && !fieldName.isEmpty()) {
                String columnName = ColumnResolver.resolve(entityClass, fieldName);
                validColumns.add(columnName);
            }
        }

        if (validColumns.isEmpty()) {
            return this;
        }

        and(wrapper -> {
            boolean first = true;
            for (String column : validColumns) {
                if (first) {
                    wrapper.like(column, keyword);
                    first = false;
                } else {
                    wrapper.or().like(column, keyword);
                }
            }
        });

        return this;
    }

    /**
     * 应用排序
     * <p>
     * 根据 SortingField 列表应用排序规则。
     * 当 sortingFields 为空时，不添加排序（使用数据库默认排序）。
     * 通过 ColumnResolver 将实体字段名（驼峰）自动转换为数据库列名（下划线）
     * </p>
     *
     * @param sortingFields 排序字段列表
     * @return this
     * @throws IllegalArgumentException 如果字段不存在或不是持久化字段
     */
    public QueryWrapperX<T> applySortingIfPresent(List<SortingField> sortingFields) {
        SortApplier.applySortingIfPresent(sortingFields,
                fieldName -> ColumnResolver.resolve(entityClass, fieldName),
                new SortApplier.SortHandler<String>() {
                    @Override
                    public void orderByAsc(String c) {
                        QueryWrapperX.this.orderByAsc(true, c);
                    }

                    @Override
                    public void orderByDesc(String c) {
                        QueryWrapperX.this.orderByDesc(true, c);
                    }
                });
        return this;
    }

    /**
     * 应用过滤条件
     * <p>
     * 根据 FilterCondition Map 应用过滤规则。
     * 当 filters 为空时，不添加任何过滤条件。
     * 通过 ColumnResolver 将实体字段名（驼峰）自动转换为数据库列名（下划线）
     * </p>
     *
     * @param filters 过滤条件 Map（Key: 实体字段名, Value: 过滤条件）
     * @return this
     * @throws IllegalArgumentException 如果字段不存在或不是持久化字段
     */
    public QueryWrapperX<T> applyFiltersIfPresent(Map<String, FilterCondition> filters) {
        FilterApplier.applyFiltersIfPresent(filters,
                fieldName -> ColumnResolver.resolve(entityClass, fieldName),
                new FilterApplier.FilterHandler<String>() {
                    @Override
                    public void eq(String c, Object v) {
                        QueryWrapperX.this.eq(c, v);
                    }

                    @Override
                    public void ne(String c, Object v) {
                        QueryWrapperX.this.ne(c, v);
                    }

                    @Override
                    public void like(String c, String v) {
                        QueryWrapperX.this.like(c, v);
                    }

                    @Override
                    public void gt(String c, Object v) {
                        QueryWrapperX.this.gt(c, v);
                    }

                    @Override
                    public void lt(String c, Object v) {
                        QueryWrapperX.this.lt(c, v);
                    }

                    @Override
                    public void ge(String c, Object v) {
                        QueryWrapperX.this.ge(c, v);
                    }

                    @Override
                    public void le(String c, Object v) {
                        QueryWrapperX.this.le(c, v);
                    }

                    @Override
                    public void between(String c, Object s, Object e) {
                        QueryWrapperX.this.between(c, s, e);
                    }

                    @Override
                    public void in(String c, Collection<?> v) {
                        QueryWrapperX.this.in(c, v);
                    }

                    @Override
                    public void inArray(String c, Object[] v) {
                        QueryWrapperX.this.in(c, v);
                    }

                    @Override
                    public void isNull(String c) {
                        QueryWrapperX.this.isNull(c);
                    }

                    @Override
                    public void isNotNull(String c) {
                        QueryWrapperX.this.isNotNull(c);
                    }
                });
        return this;
    }
}
