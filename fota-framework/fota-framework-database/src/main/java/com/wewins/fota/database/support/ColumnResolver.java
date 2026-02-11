package com.wewins.fota.database.support;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库字段名解析器
 * <p>
 * 基于 MyBatis-Plus TableInfo 解析实体字段到数据库列名的映射。
 * 支持自定义列名（@TableField）、主键（@TableId）等复杂场景。
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 获取列名
 * String column = ColumnResolver.resolve(User.class, "userName");
 * // 返回: "user_name" 或 @TableField 定义的自定义列名
 * }</pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-10
 */
@Slf4j
public class ColumnResolver {

    /**
     * 字段映射缓存
     * <p>
     * Key: 实体类
     * Value: 字段名 -> 列名映射
     * </p>
     */
    private static final Map<Class<?>, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    /**
     * 解析实体字段对应的数据库列名
     * <p>
     * 从缓存中获取映射，如果缓存不存在则从 TableInfo 构建
     * </p>
     *
     * @param entityClass 实体类
     * @param fieldName   字段名（驼峰命名）
     * @return 数据库列名（下划线命名）
     * @throws IllegalArgumentException 如果字段不存在或不是持久化字段
     */
    public static String resolve(Class<?> entityClass, String fieldName) {
        if (entityClass == null) {
            throw new IllegalArgumentException("实体类不能为空");
        }
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("字段名不能为空");
        }

        Map<String, String> fieldMap = CACHE.computeIfAbsent(entityClass, ColumnResolver::buildFieldMap);

        String columnName = fieldMap.get(fieldName);
        if (columnName == null) {
            throw new IllegalArgumentException(
                    String.format("字段不存在或不是持久化字段: %s.%s",
                            entityClass.getSimpleName(), fieldName)
            );
        }

        return columnName;
    }

    /**
     * 解析实体字段对应的数据库列名（可选）
     * <p>
     * 与 {@link #resolve(Class, String)} 不同，此方法在字段不存在时返回 null 而非抛出异常
     * </p>
     *
     * @param entityClass 实体类
     * @param fieldName   字段名（驼峰命名）
     * @return 数据库列名（下划线命名），如果字段不存在或不是持久化字段则返回 null
     */
    public static String resolveOrNull(Class<?> entityClass, String fieldName) {
        if (entityClass == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        Map<String, String> fieldMap = CACHE.computeIfAbsent(entityClass, ColumnResolver::buildFieldMap);
        return fieldMap.get(fieldName);
    }

    /**
     * 检查字段是否存在且是持久化字段
     *
     * @param entityClass 实体类
     * @param fieldName   字段名
     * @return 如果字段存在且是持久化字段返回 true，否则返回 false
     */
    public static boolean exists(Class<?> entityClass, String fieldName) {
        return resolveOrNull(entityClass, fieldName) != null;
    }

    /**
     * 清除指定实体类的缓存
     * <p>
     * 一般不需要手动调用，除非在运行时动态修改了实体类定义
     * </p>
     *
     * @param entityClass 实体类
     */
    public static void invalidate(Class<?> entityClass) {
        if (entityClass != null) {
            CACHE.remove(entityClass);
            log.debug("清除字段映射缓存: {}", entityClass.getSimpleName());
        }
    }

    /**
     * 清除所有缓存
     */
    public static void invalidateAll() {
        CACHE.clear();
        log.debug("清除所有字段映射缓存");
    }

    /**
     * 构建实体类的字段映射表
     * <p>
     * 从 MyBatis-Plus TableInfo 获取字段信息，包括：
     * <ul>
     *   <li>主键字段（@TableId）</li>
     *   <li>普通字段（@TableField，仅包括 select=true 的字段）</li>
     * </ul>
     * </p>
     * <p>
     * 不包括：
     * <ul>
     *   <li>exist=false 的字段</li>
     *   <li>transient 字段</li>
     *   <li>静态字段</li>
     * </ul>
     * </p>
     *
     * @param entityClass 实体类
     * @return 不可变的字段名 -> 列名映射
     */
    private static Map<String, String> buildFieldMap(Class<?> entityClass) {
        log.debug("构建字段映射缓存: {}", entityClass.getName());

        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityClass);
        if (tableInfo == null) {
            log.error("未找到 MyBatis-Plus TableInfo，实体类可能未配置 @TableName 或未被扫描: {}",
                    entityClass.getName());
            return Collections.emptyMap();
        }

        Map<String, String> fieldMap = new HashMap<>();

        // 处理主键字段
        if (tableInfo.getKeyProperty() != null) {
            fieldMap.put(tableInfo.getKeyProperty(), tableInfo.getKeyColumn());
        }

        // 处理普通字段，并检测重复字段
        for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
            // 只包含持久化字段（select=true，默认就是 true）
            if (fieldInfo.isSelect()) {
                String property = fieldInfo.getProperty();
                String existingColumn = fieldMap.get(property);

                if (existingColumn != null) {
                    // 检测到重复字段
                    log.warn("检测到重复字段，后者将覆盖前者: {}.{}, 旧列名={}, 新列名={}",
                            entityClass.getSimpleName(), property, existingColumn, fieldInfo.getColumn());
                }

                fieldMap.put(property, fieldInfo.getColumn());
            }
        }

        log.debug("字段映射构建完成: {} -> {} 个字段",
                entityClass.getSimpleName(), fieldMap.size());

        return Collections.unmodifiableMap(fieldMap);
    }

    private ColumnResolver() {
        // 工具类，禁止实例化
    }
}
