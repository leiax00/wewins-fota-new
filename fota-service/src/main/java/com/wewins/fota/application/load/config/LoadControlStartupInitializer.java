package com.wewins.fota.application.load.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时确保运行态总快照存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoadControlStartupInitializer {

    private final LoadControlRuntimeConfigService runtimeConfigService;
    private final LoadControlDictionaryPublishService publishService;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent.class)
    public void initializeSnapshotIfMissing() {
        if (runtimeConfigService.getPublishedConfig().isPresent()) {
            return;
        }
        publishService.publishFromDictionary("startup-initializer");
        log.info("Initialized load-control snapshot from dictionary on startup");
    }
}
