package com.wewins.fota.application.upgrade.dto;

import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.common.util.IdGenerator;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.product.model.entity.Product;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckContext {

    private UpgradeCheckReqDTO request;
    private CheckLogContext logContext;

    private String requestId;
    private Product product;
    private Device device;
    private FirmwareVersion currentFirmware;
    private UpgradePolicy matchedPolicy;
    private RateLimitDecision rateLimitDecision;

    private CheckResult result;

    public static CheckContext create(UpgradeCheckReqDTO request, CheckLogContext logContext) {
        return CheckContext.builder()
                .requestId(IdGenerator.simpleUUID())
                .request(request)
                .logContext(logContext)
                .build();
    }

    public String imei() {
        return request != null ? request.getImei() : null;
    }

    public String productModel() {
        return request != null ? request.getProduct() : null;
    }

    public String version() {
        return request != null ? request.getVersion() : null;
    }

    public String internalVersion() {
        return request != null ? request.getTag() : null;
    }

    public Long productId() {
        return product != null ? product.getId() : null;
    }

    public Long deviceId() {
        return device != null ? device.getId() : null;
    }

    public Long currentVersionId() {
        return currentFirmware != null ? currentFirmware.getId() : null;
    }

    public boolean hasDevice() {
        return device != null;
    }

    public boolean hasResult() {
        return result != null;
    }

    public boolean isFirstOnline() {
        return device != null && device.getFirstSeenAt() == null;
    }
}
