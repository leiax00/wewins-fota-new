package com.wewins.fota.common.context;

import lombok.extern.slf4j.Slf4j;

/**
 * 用户上下文
 * <p>
 * 使用 ThreadLocal 存储当前请求的用户ID，供审计字段自动填充使用。
 * </p>
 *
 * <p>
 * 使用场景：
 * <ul>
 *   <li>在拦截器或过滤器中，从 JWT Token 或 Session 中解析用户ID并设置到上下文</li>
 *   <li>在业务代码中，通过 UserContext.getCurrentUserId() 获取当前用户ID</li>
 *   <li>在异步线程中，通过 runWithUser() 方法设置用户上下文</li>
 *   <li>在请求结束后，清理 ThreadLocal 防止内存泄漏</li>
 * </ul>
 * </p>
 *
 * <p>
 * 注意事项：
 * <ul>
 *   <li>必须在请求结束时调用 clear() 方法清理 ThreadLocal</li>
 *   <li>建议在过滤器的 finally 块中调用 clear()</li>
 *   <li>如果没有设置用户ID，getCurrentUserId() 返回 null</li>
 * </ul>
 * </p>
 *
 * <p>
 * 与 Spring Security 集成：
 * </p>
 * <p>
 * 在 Web 应用中，{@link com.wewins.fota.web.jwt.JwtAuthenticationFilter} 会自动从 JWT Token
 * 中解析用户ID并设置到 UserContext，并在请求结束时自动清理。
 * </p>
 * <p>
 * 在业务代码中，直接使用 {@code UserContext.getCurrentUserId()} 即可获取当前用户ID。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 * @see com.wewins.fota.web.jwt.JwtAuthenticationFilter
 */
@Slf4j
public class UserContext {

    /**
     * 使用 ThreadLocal 存储用户ID，确保线程安全
     */
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * 私有构造函数，防止实例化
     */
    private UserContext() {
    }

    /**
     * 设置当前用户ID
     *
     * @param userId 用户ID
     */
    public static void setCurrentUserId(Long userId) {
        if (log.isDebugEnabled()) {
            log.debug("设置当前用户ID: {}", userId);
        }
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前用户ID
     *
     * @return 当前用户ID，如果未设置则返回 null
     */
    public static Long getCurrentUserId() {
        Long userId = USER_ID_HOLDER.get();
        if (log.isDebugEnabled()) {
            log.debug("获取当前用户ID: {}", userId);
        }
        return userId;
    }

    /**
     * 获取当前用户ID，如果未设置则返回默认值
     *
     * @param defaultValue 默认值
     * @return 当前用户ID，如果未设置则返回默认值
     */
    public static Long getCurrentUserIdOrDefault(Long defaultValue) {
        Long userId = USER_ID_HOLDER.get();
        return userId != null ? userId : defaultValue;
    }

    /**
     * 清除当前用户ID
     * <p>
     * 建议在请求结束时调用，防止 ThreadLocal 内存泄漏
     * </p>
     */
    public static void clear() {
        if (log.isDebugEnabled()) {
            log.debug("清除当前用户ID");
        }
        USER_ID_HOLDER.remove();
    }

    /**
     * 在指定用户上下文中执行任务，确保执行完成后清理上下文
     * <p>
     * 适用场景：
     * <ul>
     *   <li>异步线程（@Async、线程池）</li>
     *   <li>MQ 消费者</li>
     *   <li>定时任务</li>
     * </ul>
     * </p>
     *
     * @param userId 用户ID
     * @param task  要执行的任务
     */
    public static void runWithUser(Long userId, Runnable task) {
        try {
            setCurrentUserId(userId);
            task.run();
        } finally {
            clear();
        }
    }

    /**
     * 在指定用户上下文中执行任务，确保执行完成后清理上下文（带返回值）
     *
     * @param userId 用户ID
     * @param task   要执行的任务
     * @param <T>    返回值类型
     * @return 任务执行结果
     */
    public static <T> T runWithUser(Long userId, java.util.concurrent.Callable<T> task) {
        try {
            setCurrentUserId(userId);
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("任务执行失败", e);
        } finally {
            clear();
        }
    }
}
