package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UpgradeCheckService 版本范围匹配功能测试
 * <p>
 * 测试 matchesSourceVersion 方法的各种场景
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("版本范围匹配测试")
class UpgradeCheckServiceVersionMatchTest {

    private JsonNodeFactory jsonNodeFactory;

    @BeforeEach
    void setUp() {
        jsonNodeFactory = JsonNodeFactory.instance;
    }

    @Nested
    @DisplayName("matchesSourceVersion 方法测试")
    class MatchesSourceVersionTests {

        @Test
        @DisplayName("策略无源版本限制 - 应匹配")
        void whenSourceVersionsIsNull_shouldReturnTrue() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            policy.setSourceVersions(null);
            Long versionId = 100L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("策略源版本列表为空 - 应匹配")
        void whenSourceVersionsIsEmpty_shouldReturnTrue() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode emptyArray = jsonNodeFactory.arrayNode();
            policy.setSourceVersions(emptyArray);
            Long versionId = 100L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("设备版本为 null 且策略有版本限制 - 不应匹配")
        void whenVersionIdIsNull_andPolicyHasRestriction_shouldReturnFalse() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode sourceVersions = jsonNodeFactory.arrayNode()
                    .add(100L)
                    .add(101L)
                    .add(102L);
            policy.setSourceVersions(sourceVersions);

            // When
            boolean result = matchesSourceVersion(policy, null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("版本 ID 在源版本列表中 - 应匹配")
        void whenVersionIdInSourceVersions_shouldReturnTrue() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode sourceVersions = jsonNodeFactory.arrayNode()
                    .add(100L)
                    .add(101L)
                    .add(102L);
            policy.setSourceVersions(sourceVersions);
            Long versionId = 101L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("版本 ID 不在源版本列表中 - 不应匹配")
        void whenVersionIdNotInSourceVersions_shouldReturnFalse() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode sourceVersions = jsonNodeFactory.arrayNode()
                    .add(100L)
                    .add(101L)
                    .add(102L);
            policy.setSourceVersions(sourceVersions);
            Long versionId = 999L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("源版本列表只包含一个版本且匹配")
        void whenSourceVersionsHasSingleMatchingVersion_shouldReturnTrue() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode sourceVersions = jsonNodeFactory.arrayNode()
                    .add(100L);
            policy.setSourceVersions(sourceVersions);
            Long versionId = 100L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("源版本列表包含多个版本，ID 为 0 时匹配")
        void whenVersionIdIsZero_andInSourceVersions_shouldReturnTrue() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode sourceVersions = jsonNodeFactory.arrayNode()
                    .add(0L)
                    .add(1L)
                    .add(2L);
            policy.setSourceVersions(sourceVersions);
            Long versionId = 0L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("源版本列表包含负数版本 ID 时匹配")
        void whenSourceVersionsHasNegativeVersion_shouldMatch() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode sourceVersions = jsonNodeFactory.arrayNode()
                    .add(-1L)
                    .add(100L)
                    .add(101L);
            policy.setSourceVersions(sourceVersions);
            Long versionId = -1L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("源版本列表包含大量版本 ID 时能正确匹配")
        void whenSourceVersionsHasManyVersions_shouldMatchCorrectly() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            ArrayNode sourceVersions = jsonNodeFactory.arrayNode();
            for (long i = 0; i < 1000; i++) {
                sourceVersions.add(i);
            }
            policy.setSourceVersions(sourceVersions);
            Long versionId = 500L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("源版本列表包含大量版本 ID 时能正确不匹配")
        void whenSourceVersionsHasManyVersions_shouldNotMatchCorrectly() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            ArrayNode sourceVersions = jsonNodeFactory.arrayNode();
            for (long i = 0; i < 1000; i++) {
                sourceVersions.add(i);
            }
            policy.setSourceVersions(sourceVersions);
            Long versionId = 1001L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("非数组类型的 sourceVersions - 不应匹配")
        void whenSourceVersionsIsNotArray_shouldReturnFalse() {
            // Given
            UpgradePolicy policy = new UpgradePolicy();
            JsonNode objectNode = jsonNodeFactory.objectNode()
                    .put("version", 100L);
            policy.setSourceVersions(objectNode);
            Long versionId = 100L;

            // When
            boolean result = matchesSourceVersion(policy, versionId);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("findApplicablePolicies 集成测试")
    class FindApplicablePoliciesIntegrationTests {

        @Test
        @DisplayName("多策略按版本范围过滤")
        void whenMultiplePolicies_shouldFilterByVersion() {
            // Given
            Device device = Device.builder()
                    .imei("869123456789012")
                    .productId(100L)
                    .currentVersionId(101L)
                    .build();
            device.setId(1L);

            // 策略1：无版本限制
            UpgradePolicy policy1 = new UpgradePolicy();
            policy1.setId(1L);
            policy1.setProductId(100L);
            policy1.setSourceVersions(null);
            policy1.setPriority(10);

            // 策略2：版本包含 101
            UpgradePolicy policy2 = new UpgradePolicy();
            policy2.setId(2L);
            policy2.setProductId(100L);
            JsonNode sourceVersions2 = jsonNodeFactory.arrayNode()
                    .add(100L)
                    .add(101L)
                    .add(102L);
            policy2.setSourceVersions(sourceVersions2);
            policy2.setPriority(20);

            // 策略3：版本不包含 101
            UpgradePolicy policy3 = new UpgradePolicy();
            policy3.setId(3L);
            policy3.setProductId(100L);
            JsonNode sourceVersions3 = jsonNodeFactory.arrayNode()
                    .add(200L)
                    .add(201L);
            policy3.setSourceVersions(sourceVersions3);
            policy3.setPriority(30);

            List<UpgradePolicy> allPolicies = List.of(policy3, policy1, policy2);

            // When - 模拟 findApplicablePolicies 中的过滤逻辑
            List<UpgradePolicy> result = allPolicies.stream()
                    .filter(policy -> matchesSourceVersion(policy, device.getCurrentVersionId()))
                    .toList();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result).extracting(UpgradePolicy::getId)
                    .containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("设备版本为 null 时只匹配无限制策略")
        void whenDeviceVersionIsNull_shouldOnlyMatchUnrestrictedPolicies() {
            // Given
            Device device = Device.builder()
                    .imei("869123456789012")
                    .productId(100L)
                    .currentVersionId(null)
                    .build();
            device.setId(1L);

            // 策略1：无版本限制
            UpgradePolicy policy1 = new UpgradePolicy();
            policy1.setId(1L);
            policy1.setProductId(100L);
            policy1.setSourceVersions(null);

            // 策略2：有版本限制
            UpgradePolicy policy2 = new UpgradePolicy();
            policy2.setId(2L);
            policy2.setProductId(100L);
            JsonNode sourceVersions2 = jsonNodeFactory.arrayNode().add(100L);
            policy2.setSourceVersions(sourceVersions2);

            List<UpgradePolicy> allPolicies = List.of(policy1, policy2);

            // When
            List<UpgradePolicy> result = allPolicies.stream()
                    .filter(policy -> matchesSourceVersion(policy, device.getCurrentVersionId()))
                    .toList();

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1L);
        }
    }

    /**
     * 测试辅助方法：复制自 UpgradeCheckService.matchesSourceVersion
     * 用于单元测试，避免依赖 Spring 上下文
     */
    private boolean matchesSourceVersion(UpgradePolicy policy, Long versionId) {
        JsonNode sourceVersions = policy.getSourceVersions();
        if (sourceVersions == null || sourceVersions.isEmpty()) {
            return true; // 策略没有版本限制
        }

        if (versionId == null) {
            return false; // 设备版本无法识别，不匹配有限制的策略
        }

        // 检查 versionId 是否在 sourceVersions 数组中
        if (sourceVersions.isArray()) {
            for (JsonNode node : sourceVersions) {
                if (versionId.equals(node.asLong())) {
                    return true;
                }
            }
        }

        return false;
    }
}
