package com.wewins.fota.task.config;

import com.wewins.fota.task.application.service.SseEmitterManager;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 任务模块自动配置。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@AutoConfiguration
@EnableConfigurationProperties(TaskProperties.class)
@EnableAsync
@MapperScan(basePackages = "com.wewins.fota.task.infra.persistence.mybatis.mapper")
public class TaskAutoConfiguration {

    /**
     * SSE 推送线程池
     */
    @Bean("ssePushExecutor")
    @ConditionalOnMissingBean(name = "ssePushExecutor")
    public Executor ssePushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("sse-push-");
        executor.setRejectedExecutionHandler((r, e) -> {
            // 队列满时丢弃任务
        });
        executor.initialize();
        return executor;
    }

    /**
     * 默认的 SSE 连接管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public SseEmitterManager sseEmitterManager(TaskProperties properties, Executor ssePushExecutor) {
        return new SseEmitterManager(properties, ssePushExecutor);
    }

    /**
     * 默认的异步任务执行器
     * <p>
     * 如果应用层未定义 fotaTaskExecutor，则使用此默认配置
     * </p>
     */
    @Bean("fotaTaskExecutor")
    @ConditionalOnMissingBean(name = "fotaTaskExecutor")
    public Executor fotaTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("fota-task-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
