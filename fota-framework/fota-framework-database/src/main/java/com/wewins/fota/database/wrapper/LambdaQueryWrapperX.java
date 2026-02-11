package com.wewins.fota.database.wrapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wewins.fota.common.dto.FilterCondition;
import com.wewins.fota.common.dto.FilterOperator;
import com.wewins.fota.common.dto.SortingField;
import com.wewins.fota.database.resolver.ColumnResolver;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * QueryWrapper 扩展
 * <p>
 * 提供链式调用的条件拼装方法，支持 *IfPresent 语义
 * </p>
 * <p>
 * 统一使用实体字段名（驼峰命名），通过 ColumnResolver 自动转换为数据库列名
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * new LambdaQueryWrapperX<>(User.class)
 *     .likeAnyIfPresent(keyword, "username", "displayName", "email")
 *     .applyFiltersIfPresent(filters)
 *     .applySortingIfPresent(sortingFields)
 * }</pre>
 * </p>
 *
 * @param <T> 实体类型
 * @author FOTA Team
 * @since 2026-02-10
 */
public class LambdaQueryWrapperX<T> extends QueryWrapper<T> {

    /**
     * 实体类
     */
    private final Class<T> entityClass;

    /**
     * 构造函数
     *
     * @param entityClass 实体类，不能为 null
     * @throws IllegalArgumentException 如果 entityClass 为 null
     */
    public LambdaQueryWrapperX(Class<T> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("实体类不能为空");
        }
        this.entityClass = entityClass;
    }

    /**
     * 模糊查询（值为空时跳过）
     * <p>
     * 当 value 为空（null 或空字符串）时，不添加此条件
     * </p>
     *
     * @param column 字段名
     * @param value  值
     * @return this
     */
    public LambdaQueryWrapperX<T> likeIfPresent(String column, String value) {
        if (!StringUtils.hasText(value)) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) like(column, value);
    }

    /**
     * 等值查询（值为 null 时跳过）
     * <p>
     * 当 value 为 null 时，不添加此条件
     * </p>
     *
     * @param column 字段名
     * @param value  值
     * @return this
     */
    public LambdaQueryWrapperX<T> eqIfPresent(String column, Object value) {
        if (value == null) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) eq(column, value);
    }

    /**
     * 不等查询（值为 null 时跳过）
     * <p>
     * 当 value 为 null 时，不添加此条件
     * </p>
     *
     * @param column 字段名
     * @param value  值
     * @return this
     */
    public LambdaQueryWrapperX<T> neIfPresent(String column, Object value) {
        if (value == null) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) ne(column, value);
    }

    /**
     * 范围查询（start 或 end 为 null 时跳过）
     * <p>
     * 当 start 或 end 任一为 null 时，不添加此条件
     * </p>
     *
     * @param column 字段名
     * @param start  起始值
     * @param end    结束值
     * @return this
     */
    public LambdaQueryWrapperX<T> betweenIfPresent(String column, Object start, Object end) {
        if (start == null || end == null) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) between(column, start, end);
    }

    /**
     * IN 查询（集合为空时跳过）
     * <p>
     * 当 values 为 null 或空集合时，不添加此条件
     * </p>
     *
     * @param column 字段名
     * @param values 值集合
     * @return this
     */
    public LambdaQueryWrapperX<T> inIfPresent(String column, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        return (LambdaQueryWrapperX<T>) in(column, values);
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
    public LambdaQueryWrapperX<T> likeAnyIfPresent(String keyword, String... fieldNames) {
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
    public LambdaQueryWrapperX<T> applySortingIfPresent(List<SortingField> sortingFields) {
        if (sortingFields == null || sortingFields.isEmpty()) {
            return this;
        }

        for (SortingField sf : sortingFields) {
            String columnName = ColumnResolver.resolve(entityClass, sf.getField());
            if ("ASC".equalsIgnoreCase(sf.getOrder())) {
                orderByAsc(true, columnName);
            } else {
                orderByDesc(true, columnName);
            }
        }

        return this;
    }

    /**
     * 应用过滤条件
     * <p>
     * 根据 FilterCondition Map 应用过滤规则。
     * 当 filters 为空时，不添加任何过滤条件。
     * 通过 ColumnResolver 将实体字段名（驼峰）自动转换为数据库列名（下划线）
     * </p>
     * <p>
     * 支持的操作符：
     * <ul>
     *   <li>EQ: 等于</li>
     *   <li>NE: 不等于</li>
     *   <li>LIKE: 模糊匹配</li>
     *   <li>GT: 大于</li>
     *   <li>LT: 小于</li>
     *   <li>GE: 大于等于</li>
     *   <li>LE: 小于等于</li>
     *   <li>BETWEEN: 范围查询（需要 value 和 endValue）</li>
     *   <li>IN: IN 查询</li>
     *   <li>IS_NULL: 为空</li>
     *   <li>NOT_NULL: 不为空</li>
     * </ul>
     * </p>
     *
     * @param filters 过滤条件 Map（Key: 实体字段名, Value: 过滤条件）
     * @return this
     * @throws IllegalArgumentException 如果字段不存在或不是持久化字段
     */
    public LambdaQueryWrapperX<T> applyFiltersIfPresent(Map<String, FilterCondition> filters) {
        if (filters == null || filters.isEmpty()) {
            return this;
        }

        for (Map.Entry<String, FilterCondition> entry : filters.entrySet()) {
            String fieldName = entry.getKey();
            FilterCondition condition = entry.getValue();

            if (condition == null || condition.getOperator() == null) {
                continue;
            }

            String columnName = ColumnResolver.resolve(entityClass, fieldName);
            FilterOperator operator = condition.getOperator();
            Object value = condition.getValue();

            switch (operator) {
                case EQ:
                    if (value != null) {
                        eq(columnName, value);
                    }
                    break;
                case NE:
                    if (value != null) {
                        ne(columnName, value);
                    }
                    break;
                case LIKE:
                    if (value instanceof String && StringUtils.hasText((String) value)) {
                        like(columnName, value);
                    }
                    break;
                case GT:
                    if (value != null) {
                        gt(columnName, value);
                    }
                    break;
                case LT:
                    if (value != null) {
                        lt(columnName, value);
                    }
                    break;
                case GE:
                    if (value != null) {
                        ge(columnName, value);
                    }
                    break;
                case LE:
                    if (value != null) {
                        le(columnName, value);
                    }
                    break;
                case BETWEEN:
                    if (value != null && condition.getEndValue() != null) {
                        between(columnName, value, condition.getEndValue());
                    }
                    break;
                case IN:
                    if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
                        in(columnName, (Collection<?>) value);
                    } else if (value != null && value.getClass().isArray()) {
                        // 处理数组
                        in(columnName, (Object[]) value);
                    }
                    break;
                case IS_NULL:
                    isNull(columnName);
                    break;
                case NOT_NULL:
                    isNotNull(columnName);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的操作符: " + operator);
            }
        }

        return this;
    }
}
