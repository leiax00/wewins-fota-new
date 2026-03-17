package com.wewins.fota.infra.gateway;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.service.DeviceInfoUpdateGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDeviceInfoUpdateGateway implements DeviceInfoUpdateGateway {

    @Override
    public void accept(DeviceInfoUpdateMessage message) {
        if (message == null) {
            return;
        }
        
        log.debug("MQ 未启用，跳过设备信息更新消息: imei={}", message.getImei());
    }
}
