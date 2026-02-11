package com.wewins.fota.database.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.wewins.fota.common.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充处理器
 * <p>
 * 统一处理 MyBatis-Plus 实体的审计字段自动填充：
 * <ul>
 *   <li>时间字段：createdAt、updatedAt（始终填充）</li>
 *   <li>审计字段：createdBy、updatedBy（字段存在且用户ID非空时填充）</li>
 * </ul>
 * </p>
 *
 * <p>
 * 工作原理：
 * <ul>
 *   <li>INSERT 操作：填充 createdAt、updatedAt，可选填充 createdBy、updatedBy</li>
 *   <li>UPDATE 操作：填充 updatedAt，可选填充 updatedBy</li>
 * </ul>
 * </p>
 *
 * <p>
 * 当前用户ID从 {@link UserContext#getCurrentUserId()} 获取。
 * 实际项目中应通过拦截器或过滤器从 JWT Token、Session 或请求头中设置用户ID。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 * @see UserContext
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getCurrentUserId();

        log.debug("自动填充 INSERT 审计字段: currentTime={}, userId={}", now, userId);

        // 始终填充时间字段
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);

        // 可选填充审计字段（仅当字段存在且用户ID非空时）
        if (userId != null) {
            if (metaObject.hasSetter("createdBy")) {
                this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
            }
            if (metaObject.hasSetter("updatedBy")) {
                this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getCurrentUserId();

        log.debug("自动填充 UPDATE 审计字段: currentTime={}, userId={}", now, userId);

        // 始终填充更新时间
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);

        // 可选填充更新用户（仅当字段存在且用户ID非空时）
        if (userId != null && metaObject.hasSetter("updatedBy")) {
            this.strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
        }
    }
}
