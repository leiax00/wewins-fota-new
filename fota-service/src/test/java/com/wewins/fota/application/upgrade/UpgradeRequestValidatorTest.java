package com.wewins.fota.application.upgrade;

import com.wewins.fota.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UpgradeRequestValidator 单元测试
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("UpgradeRequestValidator 单元测试")
class UpgradeRequestValidatorTest {

    private UpgradeRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new UpgradeRequestValidator();
    }

    @Nested
    @DisplayName("validateProductModel 方法测试")
    class ValidateProductModelTests {

        @Test
        @DisplayName("有效的产品型号 - 应该通过")
        void shouldPass_whenProductModelIsValid() {
            // Given
            String productModel = "asr_yemen_m476_vsim";

            // When & Then
            validator.validateProductModel(productModel);
        }

        @Test
        @DisplayName("产品型号为 null - 应该抛出异常")
        void shouldThrow_whenProductModelIsNull() {
            // Given
            String productModel = null;

            // When & Then
            assertThatThrownBy(() -> validator.validateProductModel(productModel))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("product 参数不能为空");
        }

        @Test
        @DisplayName("产品型号为空字符串 - 应该抛出异常")
        void shouldThrow_whenProductModelIsEmpty() {
            // Given
            String productModel = "";

            // When & Then
            assertThatThrownBy(() -> validator.validateProductModel(productModel))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("product 参数不能为空");
        }

        @Test
        @DisplayName("产品型号为空白字符串 - 应该抛出异常")
        void shouldThrow_whenProductModelIsBlank() {
            // Given
            String productModel = "   ";

            // When & Then
            assertThatThrownBy(() -> validator.validateProductModel(productModel))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("product 参数不能为空");
        }
    }

    @Nested
    @DisplayName("validateImei 方法测试")
    class ValidateImeiTests {

        @Test
        @DisplayName("有效的 IMEI - 应该通过")
        void shouldPass_whenImeiIsValid() {
            // Given
            String imei = "354972069009027";

            // When & Then
            validator.validateImei(imei);
        }

        @Test
        @DisplayName("IMEI 为 null - 应该抛出异常")
        void shouldThrow_whenImeiIsNull() {
            // Given
            String imei = null;

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 参数不能为空");
        }

        @Test
        @DisplayName("IMEI 为空字符串 - 应该抛出异常")
        void shouldThrow_whenImeiIsEmpty() {
            // Given
            String imei = "";

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 参数不能为空");
        }

        @Test
        @DisplayName("IMEI 少于 15 位 - 应该抛出异常")
        void shouldThrow_whenImeiIsLessThan15Digits() {
            // Given
            String imei = "35497206900902";

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 格式错误：必须是15位数字");
        }

        @Test
        @DisplayName("IMEI 多于 15 位 - 应该抛出异常")
        void shouldThrow_whenImeiIsMoreThan15Digits() {
            // Given
            String imei = "3549720690090271";

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 格式错误：必须是15位数字");
        }

        @Test
        @DisplayName("IMEI 包含非数字字符 - 应该抛出异常")
        void shouldThrow_whenImeiContainsNonDigits() {
            // Given
            String imei = "35497206900902a";

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 格式错误：必须是15位数字");
        }

        @Test
        @DisplayName("IMEI 包含空格 - 应该抛出异常")
        void shouldThrow_whenImeiContainsSpaces() {
            // Given
            String imei = "354972069009027 ";

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 格式错误：必须是15位数字");
        }

        @Test
        @DisplayName("IMEI 包含连字符 - 应该抛出异常")
        void shouldThrow_whenImeiContainsHyphen() {
            // Given
            String imei = "354972-069009027";

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 格式错误：必须是15位数字");
        }
    }

    @Nested
    @DisplayName("validateVersion 方法测试")
    class ValidateVersionTests {

        @Test
        @DisplayName("有效的版本号 - 应该通过")
        void shouldPass_whenVersionIsValid() {
            // Given
            String version = "Mobile.Router.B03";

            // When & Then
            validator.validateVersion(version);
        }

        @Test
        @DisplayName("版本号为 null - 应该抛出异常")
        void shouldThrow_whenVersionIsNull() {
            // Given
            String version = null;

            // When & Then
            assertThatThrownBy(() -> validator.validateVersion(version))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("version 参数不能为空");
        }

        @Test
        @DisplayName("版本号为空字符串 - 应该抛出异常")
        void shouldThrow_whenVersionIsEmpty() {
            // Given
            String version = "";

            // When & Then
            assertThatThrownBy(() -> validator.validateVersion(version))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("version 参数不能为空");
        }

        @Test
        @DisplayName("版本号为空白字符串 - 应该抛出异常")
        void shouldThrow_whenVersionIsBlank() {
            // Given
            String version = "   ";

            // When & Then
            assertThatThrownBy(() -> validator.validateVersion(version))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("version 参数不能为空");
        }
    }

    @Nested
    @DisplayName("validateGrayRate 方法测试")
    class ValidateGrayRateTests {

        @Test
        @DisplayName("灰度比例为 null - 应该通过")
        void shouldPass_whenGrayRateIsNull() {
            // Given
            Integer grayRate = null;

            // When & Then
            validator.validateGrayRate(grayRate);
        }

        @Test
        @DisplayName("灰度比例在有效范围内 - 应该通过")
        void shouldPass_whenGrayRateIsInValidRange() {
            // Given
            Integer grayRate = 50;

            // When & Then
            validator.validateGrayRate(grayRate);
        }

        @Test
        @DisplayName("灰度比例边界值 0 - 应该通过")
        void shouldPass_whenGrayRateIsZero() {
            // Given
            Integer grayRate = 0;

            // When & Then
            validator.validateGrayRate(grayRate);
        }

        @Test
        @DisplayName("灰度比例边界值 100 - 应该通过")
        void shouldPass_whenGrayRateIsHundred() {
            // Given
            Integer grayRate = 100;

            // When & Then
            validator.validateGrayRate(grayRate);
        }

        @Test
        @DisplayName("灰度比例小于 0 - 应该抛出异常")
        void shouldThrow_whenGrayRateIsNegative() {
            // Given
            Integer grayRate = -1;

            // When & Then
            assertThatThrownBy(() -> validator.validateGrayRate(grayRate))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("grayRate 参数必须在 [0, 100] 范围内");
        }

        @Test
        @DisplayName("灰度比例大于 100 - 应该抛出异常")
        void shouldThrow_whenGrayRateIsOverHundred() {
            // Given
            Integer grayRate = 101;

            // When & Then
            assertThatThrownBy(() -> validator.validateGrayRate(grayRate))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("grayRate 参数必须在 [0, 100] 范围内");
        }
    }

    @Nested
    @DisplayName("validateAuto 方法测试")
    class ValidateAutoTests {

        @Test
        @DisplayName("auto 为 null - 应该通过")
        void shouldPass_whenAutoIsNull() {
            // Given
            Integer auto = null;

            // When & Then
            validator.validateAuto(auto);
        }

        @Test
        @DisplayName("auto 为 0（手动检查）- 应该通过")
        void shouldPass_whenAutoIsZero() {
            // Given
            Integer auto = 0;

            // When & Then
            validator.validateAuto(auto);
        }

        @Test
        @DisplayName("auto 为 1（自动检查）- 应该通过")
        void shouldPass_whenAutoIsOne() {
            // Given
            Integer auto = 1;

            // When & Then
            validator.validateAuto(auto);
        }

        @Test
        @DisplayName("auto 小于 0 - 应该抛出异常")
        void shouldThrow_whenAutoIsNegative() {
            // Given
            Integer auto = -1;

            // When & Then
            assertThatThrownBy(() -> validator.validateAuto(auto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("auto 参数只能是 0（手动检查）或 1（自动检查）");
        }

        @Test
        @DisplayName("auto 大于 1 - 应该抛出异常")
        void shouldThrow_whenAutoIsOverOne() {
            // Given
            Integer auto = 2;

            // When & Then
            assertThatThrownBy(() -> validator.validateAuto(auto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("auto 参数只能是 0（手动检查）或 1（自动检查）");
        }
    }

    @Nested
    @DisplayName("validateRequiredParams 方法测试")
    class ValidateRequiredParamsTests {

        @Test
        @DisplayName("所有必填参数有效 - 应该通过")
        void shouldPass_whenAllParamsAreValid() {
            // Given
            String productModel = "asr_yemen_m476_vsim";
            String imei = "354972069009027";
            String version = "Mobile.Router.B03";

            // When & Then
            validator.validateRequiredParams(productModel, imei, version);
        }

        @Test
        @DisplayName("产品型号无效 - 应该抛出异常")
        void shouldThrow_whenProductModelIsInvalid() {
            // Given
            String productModel = "";
            String imei = "354972069009027";
            String version = "Mobile.Router.B03";

            // When & Then
            assertThatThrownBy(() -> validator.validateRequiredParams(productModel, imei, version))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("product 参数不能为空");
        }

        @Test
        @DisplayName("IMEI 无效 - 应该抛出异常")
        void shouldThrow_whenImeiIsInvalid() {
            // Given
            String productModel = "asr_yemen_m476_vsim";
            String imei = "123";
            String version = "Mobile.Router.B03";

            // When & Then
            assertThatThrownBy(() -> validator.validateRequiredParams(productModel, imei, version))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("imei 格式错误");
        }

        @Test
        @DisplayName("版本号无效 - 应该抛出异常")
        void shouldThrow_whenVersionIsInvalid() {
            // Given
            String productModel = "asr_yemen_m476_vsim";
            String imei = "354972069009027";
            String version = "";

            // When & Then
            assertThatThrownBy(() -> validator.validateRequiredParams(productModel, imei, version))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("version 参数不能为空");
        }
    }

    @Nested
    @DisplayName("validateAllParams 方法测试")
    class ValidateAllParamsTests {

        @Test
        @DisplayName("所有参数有效 - 应该通过")
        void shouldPass_whenAllParamsAreValid() {
            // Given
            String productModel = "asr_yemen_m476_vsim";
            String imei = "354972069009027";
            String version = "Mobile.Router.B03";
            Integer auto = 0;

            // When & Then
            validator.validateAllParams(productModel, imei, version, auto);
        }

        @Test
        @DisplayName("必填参数有效，auto 为 null - 应该通过")
        void shouldPass_whenRequiredParamsValidAndAutoIsNull() {
            // Given
            String productModel = "asr_yemen_m476_vsim";
            String imei = "354972069009027";
            String version = "Mobile.Router.B03";
            Integer auto = null;

            // When & Then
            validator.validateAllParams(productModel, imei, version, auto);
        }

        @Test
        @DisplayName("auto 参数无效 - 应该抛出异常")
        void shouldThrow_whenAutoIsInvalid() {
            // Given
            String productModel = "asr_yemen_m476_vsim";
            String imei = "354972069009027";
            String version = "Mobile.Router.B03";
            Integer auto = 2;

            // When & Then
            assertThatThrownBy(() -> validator.validateAllParams(productModel, imei, version, auto))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("auto 参数只能是");
        }
    }

    @Nested
    @DisplayName("实际场景测试")
    class RealWorldScenarioTests {

        @Test
        @DisplayName("场景：真实设备升级检查请求 - 参数有效")
        void testRealUpgradeRequestWithValidParams() {
            // Given - 真实请求参数
            String productModel = "asr_yemen_m476_vsim";
            String imei = "354972069009027";
            String version = "Mobile.Router.B03";

            // When & Then - 不应该抛出异常
            validator.validateRequiredParams(productModel, imei, version);
        }

        @Test
        @DisplayName("场景：缺失产品型号的请求")
        void testRequestMissingProductModel() {
            // Given
            String productModel = null;
            String imei = "354972069009027";
            String version = "Mobile.Router.B03";

            // When & Then
            assertThatThrownBy(() -> validator.validateRequiredParams(productModel, imei, version))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("场景：IMEI 格式错误的请求")
        void testRequestWithInvalidImeiFormat() {
            // Given - IMEI 包含字母
            String imei = "35497206900902A";

            // When & Then
            assertThatThrownBy(() -> validator.validateImei(imei))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("必须是15位数字");
        }
    }
}
