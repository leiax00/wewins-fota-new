package com.wewins.fota.infra.gateway;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.service.CheckLogGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 设备检查日志网关的 Noop 实现。
 *
 * <p>在 MQ 关闭场景下启用，仅记录日志，不执行实际投递。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopCheckLogGateway implements CheckLogGateway {

    /**
     * 接收检查日志但不投递。
     *
     * @param checkLog 设备检查日志领域模型
     */
    @Override
    public void accept(DeviceCheckLog checkLog) {
        log.debug("检查日志已接收，MQ 未启用: imei={}, requestId={}",
                checkLog != null ? checkLog.getImei() : null,
                checkLog != null ? checkLog.getRequestId() : null);
    }
}
