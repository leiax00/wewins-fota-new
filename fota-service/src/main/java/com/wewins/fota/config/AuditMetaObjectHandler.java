package com.wewins.fota.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充处理器
 * <p>
 * 负责自动填充：createdAt、createdBy、updatedAt、updatedBy。
 * </p>
 *
 * <p>
 * 工作原理：
 * <ul>
 *   <li>INSERT 操作：填充 createdAt、createdBy、updatedAt、updatedBy</li>
 *   <li>UPDATE 操作：填充 updatedAt、updatedBy</li>
 * </ul>
 * </p>
 *
 * <p>
 * 当前用户ID获取方式：
 * 当前实现从 ThreadLocal 上下文获取，实际项目中应替换为：
 * <ul>
 *   <li>Spring Security 的 SecurityContextHolder 获取 Authentication</li>
 *   <li>自定义的用户上下文（UserContext）</li>
 *   <li>网关透传的请求头中的用户ID</li>
 * </ul>
 * </p>
 *
 * <p>
 * 使用方式：
 * <pre>
 * // 在 Controller 或 Interceptor 中设置当前用户ID
 * UserContext.setCurrentUserId(userId);
 *
 * // MyBatis-Plus 自动填充时会从 UserContext 获取
 * productMapper.insert(product); // createdBy 和 updatedBy 自动填充
 * </pre>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 * @see UserContext
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditMetaObjectHandler.class);

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getCurrentUserId();

        log.debug("自动填充 INSERT 审计字段: currentTime={}, userId={}", now, userId);

        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, userId);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = UserContext.getCurrentUserId();

        log.debug("自动填充 UPDATE 审计字段: currentTime={}, userId={}", now, userId);

        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, userId);
    }
}
