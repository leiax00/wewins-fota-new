package com.wewins.fota.infra.persistence.mybatis.firmware;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.infra.persistence.mybatis.firmware.mapper.FirmwareVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
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

    @InjectMocks
    private FirmwareVersionRepositoryImpl firmwareVersionRepository;

    private FirmwareVersion testFirmwareVersion;

    @BeforeEach
    void setUp() {
        testFirmwareVersion = FirmwareVersion.builder()
                .id(1L)
                .productId(100L)
                .version("Mobile.Router.B03")
                .internalVersion("ASR_YEMEN_M476_V11_B03_Build02")
                .fileUrl("fota/fw/1/test.bin")
                .fileName("test.bin")
                .fileSize(1024000L)
                .md5("abc123")
                .sha256("def456")
                .packageStatus("READY")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deletedAt(null)
                .build();
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
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(versionNumber, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(versionNumber);
            assertThat(result.get().getProductId()).isEqualTo(productId);
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("根据版本号和产品ID查询 - 未找到")
        void shouldReturnEmpty_whenNotExists() {
            // Given
            String versionNumber = "Non.Existent.Version";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndProductId(versionNumber, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
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
            verify(firmwareVersionMapper, never()).selectOne(any(LambdaQueryWrapper.class));
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
            verify(firmwareVersionMapper, never()).selectOne(any(LambdaQueryWrapper.class));
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
            verify(firmwareVersionMapper, never()).selectOne(any(LambdaQueryWrapper.class));
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
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(versionNumber);
            assertThat(result.get().getInternalVersion()).isEqualTo(internalVersion);
            assertThat(result.get().getProductId()).isEqualTo(productId);
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 未找到")
        void shouldReturnEmpty_whenNotExists() {
            // Given
            String versionNumber = "Non.Existent.Version";
            String internalVersion = "Non.Existent.Build";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 内部版本号为 null")
        void shouldQueryWithoutInternalVersion_whenInternalVersionIsNull() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = null;
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 内部版本号为空字符串")
        void shouldQueryWithoutInternalVersion_whenInternalVersionIsEmpty() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = "";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 内部版本号为空白字符串")
        void shouldQueryWithoutInternalVersion_whenInternalVersionIsBlank() {
            // Given
            String versionNumber = "Mobile.Router.B03";
            String internalVersion = "   ";
            Long productId = 100L;
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
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
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper, never()).selectOne(any(LambdaQueryWrapper.class));
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
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion, productId);

            // Then
            assertThat(result).isEmpty();
            verify(firmwareVersionMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("根据版本号、内部版本号和产品ID查询 - 处理重复版本号场景")
        void shouldHandleDuplicateVersionNumbers() {
            // Given - 模拟同一产品下有多个相同 version 但不同 internalVersion 的记录
            String versionNumber = "Mobile.Router.B03";
            String internalVersion1 = "ASR_YEMEN_M476_V11_B03_Build01";
            String internalVersion2 = "ASR_YEMEN_M476_V11_B03_Build02";
            Long productId = 100L;

            FirmwareVersion version1 = FirmwareVersion.builder()
                    .id(1L)
                    .productId(productId)
                    .version(versionNumber)
                    .internalVersion(internalVersion1)
                    .build();

            FirmwareVersion version2 = FirmwareVersion.builder()
                    .id(2L)
                    .productId(productId)
                    .version(versionNumber)
                    .internalVersion(internalVersion2)
                    .build();

            // 第一次查询返回 version1
            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(version1);

            // When
            Optional<FirmwareVersion> result1 = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            versionNumber, internalVersion1, productId);

            // Then
            assertThat(result1).isPresent();
            assertThat(result1.get().getId()).isEqualTo(1L);
            assertThat(result1.get().getInternalVersion()).isEqualTo(internalVersion1);
            verify(firmwareVersionMapper).selectOne(any(LambdaQueryWrapper.class));
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

            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

            // When - 使用 version + tag 组合查询
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            version, tag, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getVersion()).isEqualTo(version);
            assertThat(result.get().getInternalVersion()).isEqualTo(tag);
        }

        @Test
        @DisplayName("场景：升级检查时版本查询 - 无 tag 参数（向后兼容）")
        void testVersionQueryWithoutTag() {
            // Given - 模拟老设备不带 tag 参数的升级检查请求
            String productModel = "asr_yemen_m476_vsim";
            String version = "Mobile.Router.B03";
            Long productId = 100L;

            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

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

            when(firmwareVersionMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(testFirmwareVersion);

            // When
            Optional<FirmwareVersion> result = firmwareVersionRepository
                    .findByVersionNumberAndInternalVersionAndProductId(
                            version, internalVersion, productId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
        }
    }
}
