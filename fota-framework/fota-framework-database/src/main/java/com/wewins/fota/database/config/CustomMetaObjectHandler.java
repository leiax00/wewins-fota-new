package com.wewins.fota.database.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动填充处理器
 * <p>
 * 自动填充实体类中的时间字段：
 * - createdAt: 插入时自动填充
 * - updatedAt: 插入和更新时自动填充
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Slf4j
@Component
public class CustomMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时自动填充
     * <p>
     * 填充字段：
     * - createdAt: 当前时间
     * - updatedAt: 当前时间
     * </p>
     *
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("[MetaObjectHandler] 开始插入填充");

        LocalDateTime now = LocalDateTime.now();

        // 填充创建时间
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);

        // 填充更新时间
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);

        log.debug("[MetaObjectHandler] 插入填充完成: createdAt={}, updatedAt={}", now, now);
    }

    /**
     * 更新时自动填充
     * <p>
     * 填充字段：
     * - updatedAt: 当前时间
     * </p>
     *
     * @param metaObject 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("[MetaObjectHandler] 开始更新填充");

        LocalDateTime now = LocalDateTime.now();

        // 填充更新时间
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);

        log.debug("[MetaObjectHandler] 更新填充完成: updatedAt={}", now);
    }
}
