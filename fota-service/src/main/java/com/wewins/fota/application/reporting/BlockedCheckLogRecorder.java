package com.wewins.fota.application.reporting;

import com.wewins.fota.application.upgrade.dto.CheckLogContext;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.service.CheckLogGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Records a check log for requests that are blocked before entering the normal upgrade flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedCheckLogRecorder {

    private final ProductRepository productRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCheckLogBuilder deviceCheckLogBuilder;
    private final CheckLogGateway checkLogGateway;

    public void recordIfKnownDevice(UpgradeCheckReqDTO request, CheckResult result, CheckLogContext logContext) {
        try {
            Product product = productRepository.findByModel(request.getProduct()).orElse(null);
            if (product == null) {
                return;
            }

            Device device = deviceRepository.findByImei(request.getImei()).orElse(null);
            if (device == null || !product.getId().equals(device.getProductId())) {
                return;
            }

            DeviceCheckLog checkLog = deviceCheckLogBuilder.build(request, device, null, result, logContext);
            checkLogGateway.accept(checkLog);
        } catch (Exception e) {
            log.warn("Failed to record blocked check log: imei={}, requestId={}",
                    request.getImei(),
                    result != null ? result.getRequestId() : null,
                    e);
        }
    }
}
