package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 动态间隔计算服务
 * <p>
 * 根据系统负载动态调整设备检查间隔和下载延迟
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicIntervalService {

    private final SystemLoadIndicator loadIndicator;

    private static final int MIN_CHECK_INTERVAL = 1800;
    private static final int MAX_CHECK_INTERVAL = 172800;
    private static final int BASE_AUTO_INTERVAL = 86400;
    private static final int BASE_MANUAL_INTERVAL = 3600;
    private static final int BASE_DOWNLOAD_DELAY = 300;

    public int calculateCheckInterval(Long productId, Boolean autoMode) {
        LoadLevel level = loadIndicator.getLoadLevel();
        double multiplier = getIntervalMultiplier(level);

        int baseInterval = Boolean.TRUE.equals(autoMode) ? BASE_AUTO_INTERVAL : BASE_MANUAL_INTERVAL;
        int interval = (int) (baseInterval * multiplier);

        return clamp(interval, MIN_CHECK_INTERVAL, MAX_CHECK_INTERVAL);
    }

    public int calculateDownloadDelay(Long productId) {
        LoadLevel level = loadIndicator.getLoadLevel();
        double multiplier = getIntervalMultiplier(level);

        int delay = (int) (BASE_DOWNLOAD_DELAY * multiplier);
        return Math.max(0, delay);
    }

    private double getIntervalMultiplier(LoadLevel level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (level) {
            case LOW -> 0.8 + random.nextDouble(0.2);
            case NORMAL -> 1.0 + random.nextDouble(0.5);
            case HIGH -> 1.5 + random.nextDouble(1.0);
            case CRITICAL -> 2.5 + random.nextDouble(1.5);
        };
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
