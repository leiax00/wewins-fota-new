package com.wewins.fota.infra.persistence.clickhouse.reporting.fallback;

import com.wewins.fota.domain.reporting.model.aggregate.DeviceUpgradeEvent;
import com.wewins.fota.infra.persistence.clickhouse.reporting.mapper.DeviceUpgradeEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 降级事件写入器
 * <p>
 * 专门用于重放服务写入 ClickHouse
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackEventWriter {

    private final DeviceUpgradeEventMapper deviceUpgradeEventMapper;

    /**
     * 写入 ClickHouse（由重放服务调用）
     *
     * @param events 事件列表
     */
    public void writeToClickHouse(List<DeviceUpgradeEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        // 使用 Mapper 直接写入，不触发降级逻辑（避免循环）
        deviceUpgradeEventMapper.insertBatch(events);

        log.debug("重放事件写入 ClickHouse 成功: count={}", events.size());
    }
}
