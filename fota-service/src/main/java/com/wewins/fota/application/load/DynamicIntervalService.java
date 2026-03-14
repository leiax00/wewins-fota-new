package com.wewins.fota.application.load;

import com.wewins.fota.common.util.TimeConstants;
import com.wewins.fota.application.load.config.LoadControlRuntimeConfigService;
import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicIntervalService {

    public static final String REASON_NORMAL = "NORMAL";
    public static final String REASON_SENTINEL_FLOW = "SENTINEL_FLOW";
    public static final String REASON_SENTINEL_DEGRADE = "SENTINEL_DEGRADE";
    public static final String REASON_SENTINEL_SYSTEM = "SENTINEL_SYSTEM";
    public static final String REASON_SENTINEL_UNKNOWN = "SENTINEL_UNKNOWN";

    private final SystemLoadIndicator loadIndicator;
    private final LoadControlRuntimeConfigService runtimeConfigService;
    private final ProductRepository productRepository;

    private static final int MIN_CHECK_INTERVAL = 30 * TimeConstants.SECONDS_PER_MINUTE;
    private static final int MAX_CHECK_INTERVAL = 2 * TimeConstants.SECONDS_PER_DAY;
    private static final int BASE_CHECK_INTERVAL = 6 * TimeConstants.SECONDS_PER_HOUR;
    private static final int BASE_DOWNLOAD_DELAY = 5 * TimeConstants.SECONDS_PER_MINUTE;

    public int calculateCheckInterval(Long productId) {
        return resolveInterval(productId, false, null).intervalSeconds();
    }

    public int calculateProtectedCheckInterval(Long productId, String blockedReason) {
        return resolveInterval(productId, true, blockedReason).intervalSeconds();
    }

    public IntervalDecision resolveInterval(Long productId, boolean protectedMode, String blockedReason) {
        LoadLevel level = loadIndicator.getLoadLevel();
        ControlParameter param = getEffectiveParameter(productId);
        int baseInterval = getBaseCheckInterval(productId);
        double loadMultiplier = getLoadMultiplier(level);
        int normalInterval = (int) Math.round(baseInterval * loadMultiplier);
        int rawInterval = protectedMode
                ? (int) Math.round(normalInterval * getProtectedMultiplier(param, blockedReason))
                : normalInterval;
        int interval = clamp(applyJitter(rawInterval), getMinCheckInterval(param), getMaxCheckInterval(param));
        double effectiveMultiplier = baseInterval > 0 ? (double) rawInterval / baseInterval : 1.0;

        return new IntervalDecision(
                interval,
                baseInterval,
                effectiveMultiplier,
                protectedMode ? toProtectedReason(blockedReason) : REASON_NORMAL
        );
    }

    public int calculateDownloadDelay(Long productId) {
        LoadLevel level = loadIndicator.getLoadLevel();
        double loadMultiplier = getLoadMultiplier(level);
        ControlParameter param = getEffectiveParameter(productId);
        double controlMultiplier = getDownloadDelayMultiplier(param);

        int delay = (int) (BASE_DOWNLOAD_DELAY * loadMultiplier * controlMultiplier);
        return Math.max(0, delay);
    }

    private double getLoadMultiplier(LoadLevel level) {
        return switch (level) {
            case LOW -> 0.9;
            case NORMAL -> 1.0;
            case HIGH -> 1.6;
            case CRITICAL -> 3.2;
        };
    }

    private double getProtectedMultiplier(ControlParameter param, String blockedReason) {
        if (param != null && param.getProtectedIntervalMultiplier() != null && param.getProtectedIntervalMultiplier() > 0) {
            return param.getProtectedIntervalMultiplier();
        }
        return switch (normalizeBlockedReason(blockedReason)) {
            case "FLOW_QPS", "FLOW_CONCURRENCY" -> 1.8;
            case "DEGRADE" -> 2.2;
            case "SYSTEM" -> 2.8;
            default -> 2.0;
        };
    }

    private double getDownloadDelayMultiplier(ControlParameter param) {
        if (param != null && param.getDownloadDelayMultiplier() != null) {
            return param.getDownloadDelayMultiplier();
        }
        return 1.0;
    }

    private int getMinCheckInterval(ControlParameter param) {
        if (param != null && param.getMinCheckIntervalSeconds() != null && param.getMinCheckIntervalSeconds() > 0) {
            return param.getMinCheckIntervalSeconds();
        }
        return MIN_CHECK_INTERVAL;
    }

    private int getMaxCheckInterval(ControlParameter param) {
        if (param != null && param.getMaxCheckIntervalSeconds() != null && param.getMaxCheckIntervalSeconds() > 0) {
            return param.getMaxCheckIntervalSeconds();
        }
        return MAX_CHECK_INTERVAL;
    }

    private ControlParameter getEffectiveParameter(Long productId) {
        return runtimeConfigService.getEffectiveGlobalControlParameter();
    }

    private int getBaseCheckInterval(Long productId) {
        if (productId != null) {
            Integer productPeriod = productRepository.findById(productId)
                    .map(Product::getCheckPeriodSeconds)
                    .orElse(null);
            if (productPeriod != null && productPeriod > 0) {
                return productPeriod;
            }
        }
        return BASE_CHECK_INTERVAL;
    }

    private int applyJitter(int interval) {
        double jitter = ThreadLocalRandom.current().nextDouble(0.95, 1.05);
        return (int) Math.round(interval * jitter);
    }

    private <T> T firstNonNull(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String toProtectedReason(String blockedReason) {
        return switch (normalizeBlockedReason(blockedReason)) {
            case "FLOW_QPS", "FLOW_CONCURRENCY" -> REASON_SENTINEL_FLOW;
            case "DEGRADE" -> REASON_SENTINEL_DEGRADE;
            case "SYSTEM" -> REASON_SENTINEL_SYSTEM;
            default -> REASON_SENTINEL_UNKNOWN;
        };
    }

    private String normalizeBlockedReason(String blockedReason) {
        return blockedReason == null ? "" : blockedReason.trim().toUpperCase();
    }

    public record IntervalDecision(
            int intervalSeconds,
            int baseIntervalSeconds,
            double effectiveMultiplier,
            String reason
    ) {
    }
}
