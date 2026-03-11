package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.enums.ProductPriority;
import com.wewins.fota.domain.load.repository.ControlParameterRepository;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicIntervalService {

    private final SystemLoadIndicator loadIndicator;
    private final ControlParameterRepository controlParameterRepository;

    private static final int MIN_CHECK_INTERVAL = 1800;
    private static final int MAX_CHECK_INTERVAL = 172800;
    private static final int BASE_AUTO_INTERVAL = 86400;
    private static final int BASE_MANUAL_INTERVAL = 3600;
    private static final int BASE_DOWNLOAD_DELAY = 300;

    public int calculateCheckInterval(Long productId, Boolean autoMode) {
        LoadLevel level = loadIndicator.getLoadLevel();
        ControlParameter param = getEffectiveParameter(productId);
        double loadMultiplier = getLoadMultiplier(level);
        double controlMultiplier = getCheckIntervalMultiplier(param);
        double biasMultiplier = getIntervalBias(param) * getPriorityBias(param);

        int baseInterval = Boolean.TRUE.equals(autoMode) ? BASE_AUTO_INTERVAL : BASE_MANUAL_INTERVAL;
        int interval = (int) Math.round(baseInterval * loadMultiplier * controlMultiplier * biasMultiplier);
        interval = applyJitter(interval);

        return clamp(interval, getMinCheckInterval(param), getMaxCheckInterval(param));
    }

    public int calculateDownloadDelay(Long productId) {
        LoadLevel level = loadIndicator.getLoadLevel();
        double loadMultiplier = getLoadMultiplier(level);
        ControlParameter param = getEffectiveParameter(productId);
        double controlMultiplier = getDownloadDelayMultiplier(param);

        int delay = (int) (BASE_DOWNLOAD_DELAY * loadMultiplier * controlMultiplier);
        return Math.max(0, delay);
    }

    public boolean isForceMaintenance(Long productId) {
        ControlParameter param = getEffectiveParameter(productId);
        return param != null && Boolean.TRUE.equals(param.getForceMaintenance());
    }

    private double getLoadMultiplier(LoadLevel level) {
        return switch (level) {
            case LOW -> 0.9;
            case NORMAL -> 1.0;
            case HIGH -> 1.6;
            case CRITICAL -> 3.2;
        };
    }

    private double getCheckIntervalMultiplier(ControlParameter param) {
        if (param != null && param.getCheckIntervalMultiplier() != null) {
            return param.getCheckIntervalMultiplier();
        }
        return 1.0;
    }

    private double getDownloadDelayMultiplier(ControlParameter param) {
        if (param != null && param.getDownloadDelayMultiplier() != null) {
            return param.getDownloadDelayMultiplier();
        }
        return 1.0;
    }

    private double getIntervalBias(ControlParameter param) {
        if (param != null && param.getIntervalBias() != null && param.getIntervalBias() > 0) {
            return param.getIntervalBias();
        }
        return 1.0;
    }

    private double getPriorityBias(ControlParameter param) {
        ProductPriority priority = param != null && param.getPriority() != null
                ? param.getPriority()
                : ProductPriority.NORMAL;
        return priority.getIntervalBias();
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
        ControlParameter global = controlParameterRepository.getGlobal().orElse(ControlParameter.createGlobalDefault());
        if (productId != null) {
            Optional<ControlParameter> productParam = controlParameterRepository.getByProduct(productId);
            if (productParam.isPresent()) {
                return merge(global, productParam.get());
            }
        }
        return global;
    }

    private ControlParameter merge(ControlParameter global, ControlParameter product) {
        if (global == null) {
            return product;
        }
        if (product == null) {
            return global;
        }
        return ControlParameter.builder()
                .productId(product.getProductId())
                .checkIntervalMultiplier(firstNonNull(product.getCheckIntervalMultiplier(), global.getCheckIntervalMultiplier()))
                .downloadDelayMultiplier(firstNonNull(product.getDownloadDelayMultiplier(), global.getDownloadDelayMultiplier()))
                .intervalBias(firstNonNull(product.getIntervalBias(), global.getIntervalBias()))
                .minCheckIntervalSeconds(firstNonNull(product.getMinCheckIntervalSeconds(), global.getMinCheckIntervalSeconds()))
                .maxCheckIntervalSeconds(firstNonNull(product.getMaxCheckIntervalSeconds(), global.getMaxCheckIntervalSeconds()))
                .priority(firstNonNull(product.getPriority(), global.getPriority()))
                .hotspotProtectionEnabled(firstNonNull(product.getHotspotProtectionEnabled(), global.getHotspotProtectionEnabled()))
                .forceMaintenance(firstNonNull(product.getForceMaintenance(), global.getForceMaintenance()))
                .maintenanceMessage(firstNonNull(product.getMaintenanceMessage(), global.getMaintenanceMessage()))
                .updatedAt(firstNonNull(product.getUpdatedAt(), global.getUpdatedAt()))
                .updatedBy(firstNonNull(product.getUpdatedBy(), global.getUpdatedBy()))
                .build();
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
}
