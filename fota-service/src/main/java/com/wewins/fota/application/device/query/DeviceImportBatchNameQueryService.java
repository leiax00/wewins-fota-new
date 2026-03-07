package com.wewins.fota.application.device.query;

import com.wewins.fota.domain.device.model.entity.DeviceImportBatch;
import com.wewins.fota.domain.device.repository.DeviceImportBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceImportBatchNameQueryService {

    private final DeviceImportBatchRepository deviceImportBatchRepository;

    public Map<Long, String> resolveImportBatchNames(Set<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<DeviceImportBatch> batches = deviceImportBatchRepository.listByIds(List.copyOf(batchIds));
            return batches.stream().collect(Collectors.toMap(DeviceImportBatch::getId, DeviceImportBatch::getBatchName));
        } catch (Exception e) {
            log.error("批量查询设备导入批次名称失败: batchIds={}", batchIds, e);
            return Collections.emptyMap();
        }
    }
}
