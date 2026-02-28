package com.wewins.fota.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * <p>
 * 配置用于执行异步任务的线程池，主要用于：
 * <ul>
 *   <li>设备版本异步更新</li>
 *   <li>其他后台任务</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncTaskConfig {

    /**
     * FOTA 异步任务执行器
     * <p>
     * 线程池配置：
     * <ul>
     *   <li>核心线程数：4</li>
     *   <li>最大线程数：16</li>
     *   <li>队列容量：1000</li>
     *   <li>拒绝策略：CallerRunsPolicy（调用者执行）</li>
     * </ul>
     * </p>
     */
    @Bean("fotaTaskExecutor")
    public Executor fotaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(4);

        // 最大线程数
        executor.setMaxPoolSize(16);

        // 队列容量
        executor.setQueueCapacity(1000);

        // 线程名前缀
        executor.setThreadNamePrefix("fota-async-");

        // 线程存活时间（秒）
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待任务完成后再关闭
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 最大等待时间
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("FOTA 异步任务执行器初始化完成: coreSize={}, maxSize={}, queueCapacity={}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }
}
