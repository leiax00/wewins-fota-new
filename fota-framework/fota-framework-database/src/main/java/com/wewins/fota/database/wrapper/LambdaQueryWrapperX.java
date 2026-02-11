package com.wewins.fota.database.wrapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.wewins.fota.common.dto.FilterCondition;
import com.wewins.fota.common.dto.SortingField;
import com.wewins.fota.database.support.FilterApplier;
import com.wewins.fota.database.support.SortApplier;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * LambdaQueryWrapper 扩展（类型安全 API）
 * <p>
 * 提供基于 SFunction 的链式查询方法，支持编译时类型检查
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * new LambdaQueryWrapperX<>(User.class)
 *     .likeIfPresent(User::getUsername, keyword)
 *     .eqIfPresent(User::getStatus, status)
 *     .betweenIfPresent(User::getCreatedAt, startDate, endDate)
 * }</pre>
 * </p>
 *
 * @param <T> 实体类型
 * @author FOTA Team
 * @since 2026-02-11
 */
public class LambdaQueryWrapperX<T> extends LambdaQueryWrapper<T> {

    /**
     * 构造函数
     *
     * @param entityClass 实体类（兼容性参数，当前未使用）
     */
    public LambdaQueryWrapperX(@SuppressWarnings("unused") Class<T> entityClass) {
        super();
    }

    /**
     * 模糊查询（值为空时跳过）
     *
     * @param column 字段 Lambda
     * @param value  值
     * @return this
     */
    public LambdaQueryWrapperX<T> likeIfPresent(SFunction<T, ?> column, String value) {
        if (!StringUtils.hasText(value)) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) like(column, value);
    }

    /**
     * 等值查询（值为 null 时跳过）
     *
     * @param column 字段 Lambda
     * @param value  值
     * @return this
     */
    public LambdaQueryWrapperX<T> eqIfPresent(SFunction<T, ?> column, Object value) {
        if (value == null) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) eq(column, value);
    }

    /**
     * 不等查询（值为 null 时跳过）
     *
     * @param column 字段 Lambda
     * @param value  值
     * @return this
     */
    public LambdaQueryWrapperX<T> neIfPresent(SFunction<T, ?> column, Object value) {
        if (value == null) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) ne(column, value);
    }

    /**
     * 范围查询（start 或 end 为 null 时跳过）
     *
     * @param column 字段 Lambda
     * @param start  起始值
     * @param end    结束值
     * @return this
     */
    public LambdaQueryWrapperX<T> betweenIfPresent(SFunction<T, ?> column, Object start, Object end) {
        if (start == null || end == null) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) between(column, start, end);
    }

    /**
     * IN 查询（集合为空时跳过）
     *
     * @param column 字段 Lambda
     * @param values 值集合
     * @return this
     */
    public LambdaQueryWrapperX<T> inIfPresent(SFunction<T, ?> column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) in(column, values);
    }

    /**
     * 应用 filters（字段名 → SFunction 映射）
     * <p>
     * 根据 FilterCondition Map 和字段映射表应用过滤规则
     * </p>
     *
     * @param filters  过滤条件 Map
     * @param fieldMap 字段映射（字段名 → SFunction）
     * @return this
     */
    public LambdaQueryWrapperX<T> applyFiltersIfPresent(Map<String, FilterCondition> filters,
                                                        Map<String, SFunction<T, ?>> fieldMap) {
        FilterApplier.applyFiltersIfPresent(filters,
                fieldMap::get,
                new FilterApplier.FilterHandler<SFunction<T, ?>>() {
                    @Override
                    public void eq(SFunction<T, ?> c, Object v) {
                        LambdaQueryWrapperX.this.eq(c, v);
                    }

                    @Override
                    public void ne(SFunction<T, ?> c, Object v) {
                        LambdaQueryWrapperX.this.ne(c, v);
                    }

                    @Override
                    public void like(SFunction<T, ?> c, String v) {
                        LambdaQueryWrapperX.this.like(c, v);
                    }

                    @Override
                    public void gt(SFunction<T, ?> c, Object v) {
                        LambdaQueryWrapperX.this.gt(c, v);
                    }

                    @Override
                    public void lt(SFunction<T, ?> c, Object v) {
                        LambdaQueryWrapperX.this.lt(c, v);
                    }

                    @Override
                    public void ge(SFunction<T, ?> c, Object v) {
                        LambdaQueryWrapperX.this.ge(c, v);
                    }

                    @Override
                    public void le(SFunction<T, ?> c, Object v) {
                        LambdaQueryWrapperX.this.le(c, v);
                    }

                    @Override
                    public void between(SFunction<T, ?> c, Object s, Object e) {
                        LambdaQueryWrapperX.this.between(c, s, e);
                    }

                    @Override
                    public void in(SFunction<T, ?> c, Collection<?> v) {
                        LambdaQueryWrapperX.this.in(c, v);
                    }

                    @Override
                    public void inArray(SFunction<T, ?> c, Object[] v) {
                        LambdaQueryWrapperX.this.in(c, v);
                    }

                    @Override
                    public void isNull(SFunction<T, ?> c) {
                        LambdaQueryWrapperX.this.isNull(c);
                    }

                    @Override
                    public void isNotNull(SFunction<T, ?> c) {
                        LambdaQueryWrapperX.this.isNotNull(c);
                    }
                });
        return this;
    }

    /**
     * 应用排序（字段名 → SFunction 映射）
     *
     * @param sortingFields 排序字段列表
     * @param fieldMap      字段映射（字段名 → SFunction）
     * @return this
     */
    public LambdaQueryWrapperX<T> applySortingIfPresent(List<SortingField> sortingFields,
                                                        Map<String, SFunction<T, ?>> fieldMap) {
        SortApplier.applySortingIfPresent(sortingFields,
                fieldMap::get,
                new SortApplier.SortHandler<SFunction<T, ?>>() {
                    @Override
                    public void orderByAsc(SFunction<T, ?> c) {
                        LambdaQueryWrapperX.this.orderByAsc(true, c);
                    }

                    @Override
                    public void orderByDesc(SFunction<T, ?> c) {
                        LambdaQueryWrapperX.this.orderByDesc(true, c);
                    }
                });
        return this;
    }
}
