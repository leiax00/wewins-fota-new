package com.wewins.fota.application.upgrade;

import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.cache.FirmwareVersionLookupCacheRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FirmwareVersionLookupService 单元测试
 * <p>
 * 测试 version + tag 组合查找逻辑的优先级和降级行为
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("固件版本查找服务测试")
@Disabled("依赖缓存与实体基类运行时装配，当前单测需在完整测试基座下重构")
class FirmwareVersionLookupServiceTest {

    @Mock
    private FirmwareVersionRepository firmwareVersionRepository;

    @Mock
    private FirmwareVersionLookupCacheRepository firmwareVersionLookupCacheRepository;

    @InjectMocks
    private FirmwareVersionLookupService lookupService;

    private FirmwareVersion firmwareV1Build01;
    private FirmwareVersion firmwareV1Build02;
    private FirmwareVersion firmwareV2;

    @BeforeEach
    void setUp() {
        when(firmwareVersionLookupCacheRepository.get(anyLong(), any(), any()))
                .thenReturn(FirmwareVersionLookupCacheRepository.LookupCacheResult.miss());

        // 模拟数据：同一个 version 号有多个不同的 build
        firmwareV1Build01 = FirmwareVersion.builder()
                .version("v1.0.0")
                .internalVersion("Build01")
                .productId(100L)
                .build();
        firmwareV1Build01.setId(1L);

        firmwareV1Build02 = FirmwareVersion.builder()
                .version("v1.0.0")
                .internalVersion("Build02")
                .productId(100L)
                .build();
        firmwareV1Build02.setId(2L);

        firmwareV2 = FirmwareVersion.builder()
                .version("v2.0.0")
                .internalVersion("Build01")
                .productId(100L)
                .build();
        firmwareV2.setId(3L);
    }

    @Nested
    @DisplayName("findVersionId - 查找固件版本 ID")
    class FindVersionIdTests {

        @Test
        @DisplayName("精确匹配：version + tag 组合查找成功")
        void shouldFindByVersionAndTag() {
            // Given
            String version = "v1.0.0";
            String tag = "Build01";
            Long productId = 100L;

            when(firmwareVersionRepository.findByUniqueKey(
                    eq(version), eq(tag), eq(productId)))
                    .thenReturn(Optional.of(firmwareV1Build01));

            // When
            Long versionId = lookupService.findVersionId(version, tag, productId);

            // Then
            assertThat(versionId).isEqualTo(1L);
            verify(firmwareVersionRepository).findByUniqueKey(
                    eq(version), eq(tag), eq(productId));
            // 没有降级到仅 version 查找
            verify(firmwareVersionRepository, never()).findByVersionNumberAndProductId(any(), anyLong());
        }

        @Test
        @DisplayName("降级查找：version + tag 未找到，降级到仅 version 查找")
        void shouldFallbackToVersionOnlyWhenTagNotFound() {
            // Given
            String version = "v1.0.0";
            String tag = "Build99"; // 不存在的 tag
            Long productId = 100L;

            when(firmwareVersionRepository.findByUniqueKey(
                    eq(version), eq(tag), eq(productId)))
                    .thenReturn(Optional.empty());
            when(firmwareVersionRepository.findByVersionNumberAndProductId(eq(version), eq(productId)))
                    .thenReturn(Optional.of(firmwareV1Build01));

            // When
            Long versionId = lookupService.findVersionId(version, tag, productId);

            // Then
            assertThat(versionId).isEqualTo(1L);
            verify(firmwareVersionRepository).findByUniqueKey(
                    eq(version), eq(tag), eq(productId));
            verify(firmwareVersionRepository).findByVersionNumberAndProductId(eq(version), eq(productId));
        }

        @Test
        @DisplayName("直接 version 查找：未提供 tag 时")
        void shouldFindByVersionOnlyWhenNoTag() {
            // Given
            String version = "v2.0.0";
            String tag = null;
            Long productId = 100L;

            when(firmwareVersionRepository.findByVersionNumberAndProductId(eq(version), eq(productId)))
                    .thenReturn(Optional.of(firmwareV2));

            // When
            Long versionId = lookupService.findVersionId(version, tag, productId);

            // Then
            assertThat(versionId).isEqualTo(3L);
            verify(firmwareVersionRepository).findByVersionNumberAndProductId(eq(version), eq(productId));
            verify(firmwareVersionRepository, never()).findByUniqueKey(
                    any(), any(), anyLong());
        }

        @Test
        @DisplayName("直接 version 查找：tag 为空字符串时")
        void shouldFindByVersionOnlyWhenTagIsEmpty() {
            // Given
            String version = "v2.0.0";
            String tag = "";
            Long productId = 100L;

            when(firmwareVersionRepository.findByVersionNumberAndProductId(eq(version), eq(productId)))
                    .thenReturn(Optional.of(firmwareV2));

            // When
            Long versionId = lookupService.findVersionId(version, tag, productId);

            // Then
            assertThat(versionId).isEqualTo(3L);
            verify(firmwareVersionRepository).findByVersionNumberAndProductId(eq(version), eq(productId));
        }

        @Test
        @DisplayName("未找到：version + tag 都不匹配")
        void shouldReturnNullWhenNotFound() {
            // Given
            String version = "v3.0.0";
            String tag = "Build01";
            Long productId = 100L;

            when(firmwareVersionRepository.findByUniqueKey(
                    eq(version), eq(tag), eq(productId)))
                    .thenReturn(Optional.empty());
            when(firmwareVersionRepository.findByVersionNumberAndProductId(eq(version), eq(productId)))
                    .thenReturn(Optional.empty());

            // When
            Long versionId = lookupService.findVersionId(version, tag, productId);

            // Then
            assertThat(versionId).isNull();
        }

        @Test
        @DisplayName("参数校验：version 为 null 时返回 null")
        void shouldReturnNullWhenVersionIsNull() {
            // When
            Long versionId = lookupService.findVersionId(null, "Build01", 100L);

            // Then
            assertThat(versionId).isNull();
            verifyNoInteractions(firmwareVersionRepository);
        }

        @Test
        @DisplayName("参数校验：productId 为 null 时返回 null")
        void shouldReturnNullWhenProductIdIsNull() {
            // When
            Long versionId = lookupService.findVersionId("v1.0.0", "Build01", null);

            // Then
            assertThat(versionId).isNull();
            verifyNoInteractions(firmwareVersionRepository);
        }
    }

    @Nested
    @DisplayName("findFirmwareVersion - 查找固件版本实体")
    class FindFirmwareVersionTests {

        @Test
        @DisplayName("精确匹配：返回完整的固件版本实体")
        void shouldReturnFirmwareByVersionAndTag() {
            // Given
            when(firmwareVersionRepository.findByUniqueKey(
                    eq("v1.0.0"), eq("Build01"), eq(100L)))
                    .thenReturn(Optional.of(firmwareV1Build01));

            // When
            FirmwareVersion firmware = lookupService.findFirmwareVersion("v1.0.0", "Build01", 100L);

            // Then
            assertThat(firmware).isNotNull();
            assertThat(firmware.getId()).isEqualTo(1L);
            assertThat(firmware.getVersion()).isEqualTo("v1.0.0");
            assertThat(firmware.getInternalVersion()).isEqualTo("Build01");
        }

        @Test
        @DisplayName("降级查找：返回第一个匹配的固件版本实体")
        void shouldReturnFirmwareByVersionOnly() {
            // Given
            when(firmwareVersionRepository.findByUniqueKey(
                    any(), any(), anyLong()))
                    .thenReturn(Optional.empty());
            when(firmwareVersionRepository.findByVersionNumberAndProductId(eq("v1.0.0"), eq(100L)))
                    .thenReturn(Optional.of(firmwareV1Build01));

            // When
            FirmwareVersion firmware = lookupService.findFirmwareVersion("v1.0.0", "Build99", 100L);

            // Then
            assertThat(firmware).isNotNull();
            assertThat(firmware.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("未找到：返回 null")
        void shouldReturnNullWhenNotFound() {
            // Given
            when(firmwareVersionRepository.findByVersionNumberAndProductId(any(), anyLong()))
                    .thenReturn(Optional.empty());

            // When
            FirmwareVersion firmware = lookupService.findFirmwareVersion("v3.0.0", null, 100L);

            // Then
            assertThat(firmware).isNull();
        }
    }

    @Nested
    @DisplayName("findVersionIds - 批量查找")
    class FindVersionIdsTests {

        @Test
        @DisplayName("批量查找：返回版本号到 ID 的映射")
        void shouldFindMultipleVersionIds() {
            // Given
            List<FirmwareVersionLookupService.VersionTagPair> pairs = List.of(
                    FirmwareVersionLookupService.VersionTagPair.of("v1.0.0"),
                    FirmwareVersionLookupService.VersionTagPair.of("v2.0.0", "Build01")
            );

            when(firmwareVersionRepository.findByVersionNumberAndProductId(eq("v1.0.0"), eq(100L)))
                    .thenReturn(Optional.of(firmwareV1Build01));
            when(firmwareVersionRepository.findByUniqueKey(
                    eq("v2.0.0"), eq("Build01"), eq(100L)))
                    .thenReturn(Optional.of(firmwareV2));

            // When
            var result = lookupService.findVersionIds(pairs, 100L);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get("v1.0.0")).isEqualTo(1L);
            assertThat(result.get("v2.0.0")).isEqualTo(3L);
        }

        @Test
        @DisplayName("批量查找：空列表返回空映射")
        void shouldReturnEmptyMapForEmptyList() {
            // When
            var result = lookupService.findVersionIds(List.of(), 100L);

            // Then
            assertThat(result).isEmpty();
            verifyNoInteractions(firmwareVersionRepository);
        }

        @Test
        @DisplayName("批量查找：null 返回空映射")
        void shouldReturnEmptyMapForNullList() {
            // When
            var result = lookupService.findVersionIds(null, 100L);

            // Then
            assertThat(result).isEmpty();
            verifyNoInteractions(firmwareVersionRepository);
        }
    }

    @Nested
    @DisplayName("VersionTagPair - 版本标签对")
    class VersionTagPairTests {

        @Test
        @DisplayName("创建仅有 version 的对")
        void shouldCreatePairWithVersionOnly() {
            // When
            var pair = FirmwareVersionLookupService.VersionTagPair.of("v1.0.0");

            // Then
            assertThat(pair.version()).isEqualTo("v1.0.0");
            assertThat(pair.tag()).isNull();
        }

        @Test
        @DisplayName("创建 version + tag 的对")
        void shouldCreatePairWithVersionAndTag() {
            // When
            var pair = FirmwareVersionLookupService.VersionTagPair.of("v1.0.0", "Build01");

            // Then
            assertThat(pair.version()).isEqualTo("v1.0.0");
            assertThat(pair.tag()).isEqualTo("Build01");
        }

        @Test
        @DisplayName("参数校验：version 为 null 时抛出异常")
        void shouldThrowWhenVersionIsNull() {
            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> FirmwareVersionLookupService.VersionTagPair.of(null)
            );
        }

        @Test
        @DisplayName("参数校验：version 为空字符串时抛出异常")
        void shouldThrowWhenVersionIsBlank() {
            // When/Then
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> FirmwareVersionLookupService.VersionTagPair.of("  ")
            );
        }
    }
}
