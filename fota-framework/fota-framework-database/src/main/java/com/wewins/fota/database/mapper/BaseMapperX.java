package com.wewins.fota.database.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.common.dto.PageParam;

import java.util.function.Consumer;

/**
 * 增强 Mapper 基础接口
 * <p>
 * 在 MyBatis-Plus BaseMapper 基础上提供通用的分页查询方法
 * </p>
 * <p>
 * 使用示例：
 * <pre>{@code
 * public interface UserMapper extends BaseMapperX<User> {
 *     // 自动继承 selectPage(PageParam, Consumer<LambdaQueryWrapper<User>>) 方法
 * }
 *
 * // Service 层调用
 * Page<User> page = userMapper.selectPage(param, wrapper -> {
 *     wrapper.eq(User::getStatus, "enabled");
 * });
 * }</pre>
 * </p>
 *
 * @param <T> 实体类型
 * @author FOTA Team
 * @since 2026-02-10
 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    /**
     * 分页查询
     * <p>
     * 自动处理分页参数校验，通过 Consumer 回调自定义查询条件
     * </p>
     *
     * @param pageParam      分页参数（自动校验并修正，为 null 时使用默认值）
     * @param queryCustomizer 查询条件自定义器（可以为 null）
     * @return 分页结果
     */
    default IPage<T> selectPage(PageParam pageParam,
                                Consumer<LambdaQueryWrapper<T>> queryCustomizer) {
        if (pageParam == null) {
            pageParam = new PageParam();
        }
        pageParam.validate();
        Page<T> page = new Page<>(pageParam.getPage(), pageParam.getSize());
        LambdaQueryWrapper<T> wrapper = new LambdaQueryWrapper<>();

        if (queryCustomizer != null) {
            queryCustomizer.accept(wrapper);
        }

        return selectPage(page, wrapper);
    }
}
