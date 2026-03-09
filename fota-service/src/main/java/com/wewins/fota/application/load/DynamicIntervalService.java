package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.entity.ControlParameter;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
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
        double loadMultiplier = getLoadMultiplier(level);
        double controlMultiplier = getControlCheckIntervalMultiplier(productId);

        int baseInterval = Boolean.TRUE.equals(autoMode) ? BASE_AUTO_INTERVAL : BASE_MANUAL_INTERVAL;
        int interval = (int) (baseInterval * loadMultiplier * controlMultiplier);

        return clamp(interval, MIN_CHECK_INTERVAL, MAX_CHECK_INTERVAL);
    }

    public int calculateDownloadDelay(Long productId) {
        LoadLevel level = loadIndicator.getLoadLevel();
        double loadMultiplier = getLoadMultiplier(level);
        double controlMultiplier = getControlDownloadDelayMultiplier(productId);

        int delay = (int) (BASE_DOWNLOAD_DELAY * loadMultiplier * controlMultiplier);
        return Math.max(0, delay);
    }

    public boolean isForceMaintenance(Long productId) {
        ControlParameter param = getEffectiveParameter(productId);
        return param != null && Boolean.TRUE.equals(param.getForceMaintenance());
    }

    private double getLoadMultiplier(LoadLevel level) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (level) {
            case LOW -> 0.8 + random.nextDouble(0.2);
            case NORMAL -> 1.0 + random.nextDouble(0.5);
            case HIGH -> 1.5 + random.nextDouble(1.0);
            case CRITICAL -> 2.5 + random.nextDouble(1.5);
        };
    }

    private double getControlCheckIntervalMultiplier(Long productId) {
        ControlParameter param = getEffectiveParameter(productId);
        if (param != null && param.getCheckIntervalMultiplier() != null) {
            return param.getCheckIntervalMultiplier();
        }
        return 1.0;
    }

    private double getControlDownloadDelayMultiplier(Long productId) {
        ControlParameter param = getEffectiveParameter(productId);
        if (param != null && param.getDownloadDelayMultiplier() != null) {
            return param.getDownloadDelayMultiplier();
        }
        return 1.0;
    }

    private ControlParameter getEffectiveParameter(Long productId) {
        if (productId != null) {
            Optional<ControlParameter> productParam = controlParameterRepository.getByProduct(productId);
            if (productParam.isPresent()) {
                return productParam.get();
            }
        }
        return controlParameterRepository.getGlobal().orElse(null);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
