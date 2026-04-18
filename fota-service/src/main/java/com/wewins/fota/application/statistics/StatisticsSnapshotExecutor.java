package com.wewins.fota.application.statistics;

import com.wewins.fota.infra.persistence.mybatis.mapper.statistics.StatVersionDeviceCountMapper;
import com.wewins.fota.infra.persistence.mybatis.po.StatVersionDeviceCountPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsSnapshotExecutor {
    private final StatVersionDeviceCountMapper statVersionDeviceCountMapper;

    @Transactional(rollbackFor = Exception.class)
    public void refreshProductVersionCounts() {
        LocalDateTime statTime = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);

        List<Map<String, Object>> counts = statVersionDeviceCountMapper.getAllVersionDeviceCounts();
        if (counts.isEmpty()) {
            log.info("固件版本设备数统计跳过: no_data, statTime={}", statTime);
            return;
        }

        log.info("开始刷新固件版本设备数统计: versionCount={}, statTime={}", counts.size(), statTime);

        for (Map<String, Object> row : counts) {
            Object versionIdObj = row.get("versionId");
            if (versionIdObj == null) {
                continue;
            }

            StatVersionDeviceCountPO po = new StatVersionDeviceCountPO();
            po.setProductId(asLong(row.get("productId")));
            po.setVersionId(asLong(versionIdObj));
            po.setDeviceCount(asLong(row.get("deviceCount"), 0L));
            po.setStatTime(statTime);
            statVersionDeviceCountMapper.upsert(po);
        }

        log.info("固件版本设备数统计刷新完成: versionCount={}, statTime={}", counts.size(), statTime);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private long asLong(Object value, long defaultValue) {
        Long parsed = asLong(value);
        return parsed == null ? defaultValue : parsed;
    }
}
