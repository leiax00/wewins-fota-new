package com.wewins.fota.application.upgrade;

import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.firmware.repository.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionLookupCacheRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("固件版本查找服务测试")
@Disabled("依赖缓存与实体基类运行时装配，当前单测需在完整测试基座下重构")
class FirmwareVersionLookupServiceTest {

    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;

    @Mock
    private FirmwareCacheRepository firmwareCacheRepository;

    @Mock
    private FirmwareVersionLookupCacheRepository firmwareVersionLookupCacheRepository;

    @InjectMocks
    private FirmwareVersionLookupService lookupService;

    private FirmwareVersion genericFirmware;
    private FirmwareVersion taggedFirmware;

    @BeforeEach
    void setUp() {
        when(firmwareVersionLookupCacheRepository.get(anyLong(), any(), any()))
                .thenReturn(CacheLookupResult.miss());

        genericFirmware = FirmwareVersion.builder()
                .productId(100L)
                .version("v1.0.0")
                .internalVersion("Build01")
                .build();
        genericFirmware.setId(1L);

        taggedFirmware = FirmwareVersion.builder()
                .productId(100L)
                .version("v1.0.0")
                .internalVersion("Build01")
                .tags(Map.of("region", "CN"))
                .build();
        taggedFirmware.setId(2L);
    }

    @Test
    @DisplayName("查询候选版本 ID 列表")
    void shouldReturnCandidateVersionIds() {
        when(firmwareVersionRepository.findByVersionAndInternalVersionAndProductId("v1.0.0", "Build01", 100L))
                .thenReturn(List.of(genericFirmware, taggedFirmware));

        List<Long> result = lookupService.findCandidateVersionIds("v1.0.0", "Build01", 100L);

        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("按设备 tags 选择更精确的版本")
    void shouldMatchMostSpecificFirmwareByDeviceTags() {
        when(firmwareVersionRepository.findByVersionAndInternalVersionAndProductId("v1.0.0", "Build01", 100L))
                .thenReturn(List.of(genericFirmware, taggedFirmware));
        when(firmwareVersionRepository.listByIds(List.of(1L, 2L)))
                .thenReturn(List.of(genericFirmware, taggedFirmware));

        Long versionId = lookupService.findMatchedVersionId(
                "v1.0.0",
                "Build01",
                100L,
                Map.of("region", "CN", "env", "prod")
        );

        assertThat(versionId).isEqualTo(2L);
    }
}
