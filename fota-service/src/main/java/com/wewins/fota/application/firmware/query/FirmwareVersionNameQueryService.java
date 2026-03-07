package com.wewins.fota.application.firmware.query;

import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
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
public class FirmwareVersionNameQueryService {

    private final FirmwareVersionRepository firmwareVersionRepository;

    public Map<Long, String> resolveFirmwareVersionNames(Set<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<FirmwareVersion> versions = firmwareVersionRepository.listByIds(List.copyOf(versionIds));
            return versions.stream().collect(Collectors.toMap(FirmwareVersion::getId, FirmwareVersion::getVersion));
        } catch (Exception e) {
            log.error("批量查询固件版本名称失败: versionIds={}", versionIds, e);
            return Collections.emptyMap();
        }
    }
}
