package com.wewins.fota.application.upgrade;

import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("版本范围匹配测试")
class UpgradeCheckServiceVersionMatchTest {

    @Nested
    @DisplayName("matchesSourceVersion 方法测试")
    class MatchesSourceVersionTests {

        @Test
        @DisplayName("策略无源版本限制 - 应匹配")
        void whenSourceVersionsIsNull_shouldReturnTrue() {
            UpgradePolicy policy = new UpgradePolicy();
            policy.setSourceVersions(null);
            assertThat(matchesSourceVersion(policy, 100L)).isTrue();
        }

        @Test
        @DisplayName("策略源版本列表为空 - 应匹配")
        void whenSourceVersionsIsEmpty_shouldReturnTrue() {
            UpgradePolicy policy = new UpgradePolicy();
            policy.setSourceVersions(Set.of());
            assertThat(matchesSourceVersion(policy, 100L)).isTrue();
        }

        @Test
        @DisplayName("设备版本为 null 且策略有版本限制 - 不应匹配")
        void whenVersionIdIsNull_andPolicyHasRestriction_shouldReturnFalse() {
            UpgradePolicy policy = new UpgradePolicy();
            policy.setSourceVersions(Set.of(100L, 101L, 102L));
            assertThat(matchesSourceVersion(policy, null)).isFalse();
        }

        @Test
        @DisplayName("版本 ID 在源版本列表中 - 应匹配")
        void whenVersionIdInSourceVersions_shouldReturnTrue() {
            UpgradePolicy policy = new UpgradePolicy();
            policy.setSourceVersions(Set.of(100L, 101L, 102L));
            assertThat(matchesSourceVersion(policy, 101L)).isTrue();
        }

        @Test
        @DisplayName("版本 ID 不在源版本列表中 - 不应匹配")
        void whenVersionIdNotInSourceVersions_shouldReturnFalse() {
            UpgradePolicy policy = new UpgradePolicy();
            policy.setSourceVersions(Set.of(100L, 101L, 102L));
            assertThat(matchesSourceVersion(policy, 999L)).isFalse();
        }
    }

    @Nested
    @DisplayName("findApplicablePolicies 集成测试")
    class FindApplicablePoliciesIntegrationTests {

        @Test
        @DisplayName("多策略按版本范围过滤")
        void whenMultiplePolicies_shouldFilterByVersion() {
            Device device = Device.builder()
                    .imei("869123456789012")
                    .productId(100L)
                    .versionParts(buildVersionParts(101L))
                    .build();
            device.setId(1L);

            UpgradePolicy policy1 = new UpgradePolicy();
            policy1.setId(1L);
            policy1.setProductId(100L);
            policy1.setSourceVersions(null);

            UpgradePolicy policy2 = new UpgradePolicy();
            policy2.setId(2L);
            policy2.setProductId(100L);
            policy2.setSourceVersions(Set.of(100L, 101L, 102L));

            UpgradePolicy policy3 = new UpgradePolicy();
            policy3.setId(3L);
            policy3.setProductId(100L);
            policy3.setSourceVersions(Set.of(200L, 201L));

            Long currentVersionId = device.getVersionParts().getPrimaryVersionId();
            List<UpgradePolicy> result = List.of(policy3, policy1, policy2).stream()
                    .filter(policy -> matchesSourceVersion(policy, currentVersionId))
                    .toList();

            assertThat(result).extracting(UpgradePolicy::getId)
                    .containsExactlyInAnyOrder(1L, 2L);
        }
    }

    private boolean matchesSourceVersion(UpgradePolicy policy, Long versionId) {
        Set<Long> sourceVersions = policy.getSourceVersions();
        if (sourceVersions == null || sourceVersions.isEmpty()) {
            return true;
        }
        if (versionId == null) {
            return false;
        }
        return sourceVersions.contains(versionId);
    }

    private DeviceVersionParts buildVersionParts(Long versionId) {
        return DeviceVersionParts.builder()
                .parts(Map.of("main", DeviceVersionPart.builder()
                        .versionId(versionId)
                        .version("v1.0.0")
                        .updatedAt(LocalDateTime.now())
                        .build()))
                .primaryPart("main")
                .build();
    }
}
