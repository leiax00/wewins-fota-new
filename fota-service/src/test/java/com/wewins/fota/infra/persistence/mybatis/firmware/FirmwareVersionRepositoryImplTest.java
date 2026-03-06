package com.wewins.fota.infra.persistence.mybatis.firmware;

import com.wewins.fota.domain.firmware.repository.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.infra.persistence.converter.FirmwareVersionConverter;
import com.wewins.fota.infra.persistence.mybatis.mapper.FirmwareVersionMapper;
import com.wewins.fota.infra.persistence.mybatis.po.FirmwareVersionPO;
import com.wewins.fota.infra.persistence.mybatis.repository.FirmwareVersionRepositoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FirmwareVersionRepositoryImpl 单元测试
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FirmwareVersionRepositoryImpl 单元测试")
class FirmwareVersionRepositoryImplTest {

    @Mock
    private FirmwareVersionMapper firmwareVersionMapper;

    @Mock
    private FirmwareCacheRepository firmwareCacheRepository;

    @Mock
    private FirmwareVersionConverter firmwareVersionConverter;

    private FirmwareVersionRepositoryImpl firmwareVersionRepository;

    private FirmwareVersion testFirmwareVersion;
    private FirmwareVersionPO testFirmwareVersionPO;

    @BeforeEach
    void setUp() {
        firmwareVersionRepository = new FirmwareVersionRepositoryImpl(
                firmwareVersionMapper,
                firmwareCacheRepository,
                firmwareVersionConverter
        );

        testFirmwareVersion = FirmwareVersion.builder()
                .productId(100L)
                .version("Mobile.Router.B03")
                .internalVersion("ASR_YEMEN_M476_V11_B03_Build02")
                .fileUrl("fota/fw/1/test.bin")
                .fileName("test.bin")
                .fileSize(1024000L)
                .md5("abc123")
                .sha256("def456")
                .packageStatus("READY")
                .deletedAt(null)
                .build();
        testFirmwareVersion.setId(1L);
        testFirmwareVersion.setCreatedAt(LocalDateTime.now());
        testFirmwareVersion.setUpdatedAt(LocalDateTime.now());

        testFirmwareVersionPO = new FirmwareVersionPO();
        testFirmwareVersionPO.setId(testFirmwareVersion.getId());
        testFirmwareVersionPO.setProductId(testFirmwareVersion.getProductId());
        testFirmwareVersionPO.setVersion(testFirmwareVersion.getVersion());
        testFirmwareVersionPO.setInternalVersion(testFirmwareVersion.getInternalVersion());
        testFirmwareVersionPO.setFileUrl(testFirmwareVersion.getFileUrl());
        testFirmwareVersionPO.setFileName(testFirmwareVersion.getFileName());
        testFirmwareVersionPO.setFileSize(testFirmwareVersion.getFileSize());
        testFirmwareVersionPO.setMd5(testFirmwareVersion.getMd5());
        testFirmwareVersionPO.setSha256(testFirmwareVersion.getSha256());
        testFirmwareVersionPO.setPackageStatus(testFirmwareVersion.getPackageStatus());
        testFirmwareVersionPO.setDeletedAt(testFirmwareVersion.getDeletedAt());
        testFirmwareVersionPO.setCreatedAt(testFirmwareVersion.getCreatedAt());
        testFirmwareVersionPO.setUpdatedAt(testFirmwareVersion.getUpdatedAt());

        lenient().when(firmwareVersionConverter.toDomain(any(FirmwareVersionPO.class)))
                .thenAnswer(invocation -> toDomain(invocation.getArgument(0)));
        lenient().when(firmwareVersionConverter.toDomain(null)).thenReturn(null);
    }

    private FirmwareVersion toDomain(FirmwareVersionPO po) {
        if (po == null) {
            return null;
        }
        FirmwareVersion firmware = FirmwareVersion.builder()
                .productId(po.getProductId())
                .version(po.getVersion())
                .internalVersion(po.getInternalVersion())
                .fileUrl(po.getFileUrl())
                .fileName(po.getFileName())
                .fileSize(po.getFileSize())
                .md5(po.getMd5())
                .sha256(po.getSha256())
                .packageStatus(po.getPackageStatus())
                .deletedAt(po.getDeletedAt())
                .build();
        firmware.setId(po.getId());
        firmware.setCreatedAt(po.getCreatedAt());
        firmware.setCreatedBy(po.getCreatedBy());
        firmware.setUpdatedAt(po.getUpdatedAt());
        firmware.setUpdatedBy(po.getUpdatedBy());
        return firmware;
    }

    @Nested
    @DisplayName("findByVersionNumberAndProductId 方法测试")
    class FindByVersionNumberAndProductIdTests {

        @Test
        @DisplayName("根据版本号和产品ID查询 - 成功找到")
        void shouldReturnFirmwareVersion_whenExists() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(versionNumber, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(versionNumber);
            assertThat(result.get().getProductId()).isEqualTo(productId);
            verify(firmwareVersionMapper).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号和产品ID查询 - 未找到")
        void shouldReturnEmpty_whenNotExists() {
            // Given
            String versionNumber = "Non.Existent.Version";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(null);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(versionNumber, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号和产品ID查询 - null 版本号参数")
        void shouldReturnEmpty_whenVersionNumberIsNull() {
            // Given
            String versionNumber = null;
            Long productId = 100L;

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(versionNumber, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号和产品ID查询 - null 产品ID参数")
        void shouldReturnEmpty_whenProductIdIsNull() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            Long productId = null;

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(versionNumber, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号和产品ID查询 - 两个参数都为 null")
        void shouldReturnEmpty_whenBothParamsAreNull() {
            // Given
            String versionNumber = null;
            Long productId = null;

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(versionNumber, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper, never()).selectOne(any());
        }
    }

    @Nested
    @DisplayName("findByVersionNumberAndInternalVersionAndProductId 方法测试")
    class FindByVersionNumberAndInternalVersionAndProductIdTests {

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 成功找到")
        void shouldReturnFirmwareVersion_whenAllParamsMatch() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = "ASR_YEMEN_M476_V11_B03_Build02";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(versionNumber);
            assertThat(result.get().getInternalVersion()).isEqualTo(internalVersion);
            assertThat(result.get().getProductId()).isEqualTo(productId);
            verify(firmwareVersionMapper).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 未找到")
        void shouldReturnEmpty_whenNotExists() {
            // Given
            String versionNumber = "Non.Existent.Version";
            String internalVersion = "Non.Existent.Build";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(null);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 内部版本号为 null")
        void shouldQueryWithoutInternalVersion_whenInternalVersionIsNull() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = null;
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            verify(firmwareVersionMapper).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 内部版本号为空字符串")
        void shouldQueryWithoutInternalVersion_whenInternalVersionIsEmpty() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = "";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            verify(firmwareVersionMapper).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 内部版本号为空白字符串")
        void shouldQueryWithoutInternalVersion_whenInternalVersionIsBlank() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = "   ";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            verify(firmwareVersionMapper).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - null 版本号参数")
        void shouldReturnEmpty_whenVersionNumberIsNull() {
            // Given
            String versionNumber = null;
            String internalVersion = "ASR_YEMEN_M476_V11_B03_Build02";
            Long productId = 100L;

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - null 产品ID参数")
        void shouldReturnEmpty_whenProductIdIsNull() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = "ASR_YEMEN_M476_V11_B03_Build02";
            Long productId = null;

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper, never()).selectOne(any());
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 处理重复版本号场景")
        void shouldHandleDuplicateVersionNumbers() {
            // Given - 模拟同一产品下有多个相同 version 但不同 internalVersion 的记录
            String versionNumber = "Mobile.Router.B03";
            String internalVersion1 = "ASR_YEMEN_M476_V11_B03_Build01";
            String internalVersion2 = "ASR_YEMEN_M476_V11_B03_Build02";
            Long productId = 100L;

            FirmwareVersionPO version1 = new FirmwareVersionPO();
            version1.setId(1L);
            version1.setProductId(productId);
            version1.setVersion(versionNumber);
            version1.setInternalVersion(internalVersion1);

            FirmwareVersionPO version2 = new FirmwareVersionPO();
            version2.setId(2L);
            version2.setProductId(productId);
            version2.setVersion(versionNumber);
            version2.setInternalVersion(internalVersion2);

            // 第一次查询返回 version1
            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(version1);

            // When
            Optional<FirmwareVersion> result1 = firmwareVersionRepository
                    .findByUniqueKey(
                            versionNumber, internalVersion1, productId);

            // Then
            assertThat(result1).isPresent();
            assertThat(result1.get().getId()).isEqualTo(1L);
            assertThat(result1.get().getInternalVersion()).isEqualTo(internalVersion1);
            verify(firmwareVersionMapper).selectOne(any());
        }
    }

    @Nested
    @DisplayName("实际场景测试")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("场景：升级检查时版本查询 - 有 tag 参数")
        void testVersionQueryWithTag() {
            // Given - 模拟设备上报升级检查请求，带有 tag 参数
            String productModel = "asr_yemen_m476_vsim";
            String version = "Mobile.Router.B03";
            String tag = "ASR_YEMEN_M476_M483_V11_B03_Build02";
            Long productId = 100L;

            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When - 使用 version + tag 组合查询
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            version, tag, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(version);
            assertThat(result.get().getInternalVersion()).isEqualTo("ASR_YEMEN_M476_V11_B03_Build02");
        }

        @Test
        @DisplayName("场景：升级检查时版本查询 - 无 tag 参数（向后兼容）")
        void testVersionQueryWithoutTag() {
            // Given - 模拟老设备不带 tag 参数的升级检查请求
            String productModel = "asr_yemen_m476_vsim";
            String version = "Mobile.Router.B03";
            Long productId = 100L;

            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When - 只使用 version 查询（向后兼容）
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(version, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(version);
        }

        @Test
        @DisplayName("场景：灰度发布时版本匹配 - 精确匹配版本")
        void testGrayReleaseVersionMatch() {
            // Given - 灰度发布策略需要精确匹配特定版本
            String version = "Mobile.Router.B03";
            String internalVersion = "ASR_YEMEN_M476_V11_B03_Build02";
            Long productId = 100L;

            when(firmwareVersionMapper.selectOne(any()))
                    .thenReturn(testFirmwareVersionPO);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByUniqueKey(
                            version, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
        }
    }
}
