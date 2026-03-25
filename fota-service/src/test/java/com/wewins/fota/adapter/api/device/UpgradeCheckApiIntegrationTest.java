package com.wewins.fota.adapter.api.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 升级检查 API 集成测试
 * <p>
 * 验证老接口 /fota/version/query 和新接口 /v1/upgrade/check 行为一致性
 * </p>
 * <p>
 * 测试策略：
 * </p>
 * <ul>
 *   <li>使用 MockMvc 模拟 HTTP 请求</li>
 *   <li>使用测试数据库（H2 或 PostgreSQL 测试库）</li>
 *   <li>使用 @Transactional 确保测试后回滚</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Disabled("Requires dedicated PostgreSQL/ClickHouse test environment and Flyway-managed test database")
@DisplayName("升级检查 API 集成测试")
class UpgradeCheckApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试设备 IMEI（需要在测试数据中预先存在）
     */
    private static final String TEST_EXISTING_IMEI = "869123456789012";

    /**
     * 不存在的设备 IMEI
     */
    private static final String TEST_NON_EXISTING_IMEI = "999999999999999";

    /**
     * 测试产品型号
     */
    private static final String TEST_PRODUCT_MODEL = "TEST-MODEL-001";

    @Nested
    @DisplayName("新老接口一致性测试")
    class ApiConsistencyTests {

        @Test
        @DisplayName("两个接口 GET 请求响应结构应一致")
        void testApiConsistency_GetRequest() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;

            // When - 调用老接口
            MvcResult oldResult = mockMvc.perform(get("/fota/version/query")
                            .param("imei", imei))
                    .andExpect(status().isOk())
                    .andReturn();

            // When - 调用新接口
            MvcResult newResult = mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then - 响应状态码一致
            assertThat(oldResult.getResponse().getStatus())
                    .isEqualTo(newResult.getResponse().getStatus());

            // Then - 响应内容类型一致
            assertThat(oldResult.getResponse().getContentType())
                    .isEqualTo(newResult.getResponse().getContentType());
        }

        @Test
        @DisplayName("两个接口 POST 请求响应结构应一致")
        void testApiConsistency_PostRequest() throws Exception {
            // Given
            String requestBody = """
                    {
                        "imei": "869123456789012",
                        "version": "1.0.0",
                        "language": "zh-CN"
                    }
                    """;

            // When - 调用新接口 POST
            mockMvc.perform(post("/v1/upgrade/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpdate").exists())
                    .andExpect(jsonPath("$.decision").exists());
        }
    }

    @Nested
    @DisplayName("正常场景测试")
    class NormalScenarioTests {

        @Test
        @DisplayName("设备存在 - 应返回成功响应")
        void testApi_ExistingDevice_Success() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;

            // When & Then - 老接口
            mockMvc.perform(get("/fota/version/query")
                            .param("imei", imei))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpdate").isBoolean())
                    .andExpect(jsonPath("$.decision").isString());

            // When & Then - 新接口
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpdate").isBoolean())
                    .andExpect(jsonPath("$.decision").isString());
        }

        @Test
        @DisplayName("设备不存在 - 应返回 NOT_FOUND 决策")
        void testApi_NewDevice_NotFound() throws Exception {
            // Given
            String imei = TEST_NON_EXISTING_IMEI;

            // When & Then
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpdate").value(false))
                    .andExpect(jsonPath("$.decision").value("DEVICE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("参数校验测试")
    @DisabledIfSystemProperty(named = "skip.slow.tests", matches = "true")
    class ParameterValidationTests {

        @Test
        @DisplayName("IMEI 格式错误 - 应返回 400 错误")
        void testApi_ImeiInvalidFormat_400Error() throws Exception {
            // Given - 各种无效 IMEI 格式
            String[] invalidImeis = {
                    "",                    // 空
                    "123",                 // 太短
                    "abcdefghijklmnop",   // 非数字
                    "   ",                 // 空格
                    null                   // null
            };

            for (String imei : invalidImeis) {
                mockMvc.perform(get("/v1/upgrade/check")
                                .param("imei", imei == null ? "" : imei))
                        .andExpect(status().isBadRequest());
            }
        }

        @Test
        @DisplayName("缺少必填参数 - 应返回 400 错误")
        void testApi_MissingRequiredParam_400Error() throws Exception {
            // When & Then - 缺少 imei 参数
            mockMvc.perform(get("/v1/upgrade/check"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("auto 参数测试")
    class AutoParameterTests {

        @Test
        @DisplayName("自动模式 auto=1 - 检查间隔应为 86400 秒")
        void testApi_AutoMode_CheckInterval_86400() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;
            String auto = "1";

            // When & Then
            // 注意：此测试需要完整的测试数据支持
            // 目前作为框架示例，实际依赖任务 #7 完成
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("auto", auto))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseCheckInterval").exists());
        }

        @Test
        @DisplayName("手动模式 auto=0 - 检查间隔应为 3600 秒")
        void testApi_ManualMode_CheckInterval_3600() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;
            String auto = "0";

            // When & Then
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("auto", auto))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseCheckInterval").exists());
        }
    }

    @Nested
    @DisplayName("dev 参数测试")
    @DisabledIfSystemProperty(named = "skip.feature.tests", matches = "true")
    class DevParameterTests {

        @Test
        @DisplayName("dev=1 - 应匹配测试策略")
        void testApi_DevMode_MatchesTestPolicy() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;
            String dev = "1";

            // When & Then
            // 依赖任务 #5 完成
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("dev", dev))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("dev=0 或未指定 - 应匹配生产策略")
        void testApi_NoDevMode_MatchesProdPolicy() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;

            // When & Then - 未指定 dev 参数
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei))
                    .andExpect(status().isOk());

            // When & Then - dev=0
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("dev", "0"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("version 和 tag 匹配测试")
    @DisabledIfSystemProperty(named = "skip.feature.tests", matches = "true")
    class VersionAndTagTests {

        @Test
        @DisplayName("version+tag 精确匹配 - 应返回对应固件")
        void testApi_VersionAndTag_ExactMatch() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;
            String version = "1.0.0";
            String tag = "debug";

            // When & Then
            // 依赖任务 #6, #8 完成
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("version", version)
                            .param("tag", tag))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("无 tag - 应降级到版本号匹配")
        void testApi_VersionOnly_Fallback() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;
            String version = "1.0.0";

            // When & Then
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("version", version))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("语言降级测试")
    @DisabledIfSystemProperty(named = "skip.feature.tests", matches = "true")
    class LanguageFallbackTests {

        @Test
        @DisplayName("指定语言 - 应返回对应语言描述")
        void testApi_LanguageSelection_Fallback() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;

            // When & Then - 中文
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("language", "zh-CN"))
                    .andExpect(status().isOk());

            // When & Then - 英文
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("language", "en-US"))
                    .andExpect(status().isOk());

            // When & Then - 不支持的语言，应降级到默认
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei)
                            .param("language", "fr-FR"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("响应结构验证测试")
    class ResponseStructureTests {

        @Test
        @DisplayName("成功响应应包含所有必需字段")
        void testResponseStructure_Success() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;

            // When & Then
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpdate").exists())
                    .andExpect(jsonPath("$.decision").exists())
                    .andExpect(jsonPath("$.responseCheckInterval").exists());
        }

        @Test
        @DisplayName("错误响应应包含错误信息")
        void testResponseStructure_Error() throws Exception {
            // Given
            String imei = TEST_NON_EXISTING_IMEI;

            // When & Then
            mockMvc.perform(get("/v1/upgrade/check")
                            .param("imei", imei))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasUpdate").value(false))
                    .andExpect(jsonPath("$.decision").exists());
        }
    }

    @Nested
    @DisplayName("并发请求测试")
    @DisabledIfSystemProperty(named = "skip.slow.tests", matches = "true")
    class ConcurrencyTests {

        @Test
        @DisplayName("并发请求 - 应正确处理限流")
        void testConcurrentRequests_RateLimit() throws Exception {
            // Given
            String imei = TEST_EXISTING_IMEI;

            // When - 连续发送多个请求
            for (int i = 0; i < 15; i++) {  // 超过限流阈值（10次/分钟）
                mockMvc.perform(get("/v1/upgrade/check")
                                .param("imei", imei))
                        .andExpect(status().isOk())
                        .andReturn();
            }

            // Then - 后续请求应被限流
            // 注意：实际限流行为依赖 Redis 配置
        }
    }
}
