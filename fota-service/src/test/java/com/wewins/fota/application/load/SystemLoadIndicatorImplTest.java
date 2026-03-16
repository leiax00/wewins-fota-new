package com.wewins.fota.application.load;

import com.wewins.fota.application.load.config.LoadControlRuntimeConfigService;
import com.wewins.fota.application.load.config.LoadControlDefaults;
import com.wewins.fota.application.load.config.LoadScoringConfig;
import com.wewins.fota.application.load.config.LoadScoringMetricConfig;
import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import com.wewins.fota.infra.metrics.NodeIdentity;
import com.wewins.fota.infra.metrics.PrometheusClient;
import com.wewins.fota.infra.sentinel.config.SentinelRuleManager;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SystemLoadIndicatorImplTest {

    private SystemLoadIndicator loadIndicator;
    private MeterRegistry meterRegistry;

    @Mock
    private PrometheusClient prometheusClient;

    @Mock
    private SentinelRuleManager sentinelRuleManager;

    @Mock
    private LoadControlRuntimeConfigService runtimeConfigService;

    @Mock
    private NodeIdentity nodeIdentity;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        when(nodeIdentity.hostCode()).thenReturn("test-host");
        when(nodeIdentity.regionCode()).thenReturn("test-region");
        when(nodeIdentity.monitoringInstanceLabel()).thenReturn("test-region-test-host-test-instance");
        when(prometheusClient.getInstanceCheckQps(anyString(), anyString())).thenReturn(-1.0);
        when(prometheusClient.getInstanceReportQps(anyString(), anyString())).thenReturn(-1.0);
        when(prometheusClient.getRegionCheckQps(anyString())).thenReturn(-1.0);
        when(prometheusClient.getRegionReportQps(anyString())).thenReturn(-1.0);
        when(prometheusClient.getRegionInstanceCount(anyString())).thenReturn(1);
        when(sentinelRuleManager.getFlowThreshold(anyString(), anyDouble())).thenAnswer(invocation -> invocation.getArgument(1));
        when(runtimeConfigService.getEffectiveScoringMetricMap()).thenReturn(defaultMetricMap());
        loadIndicator = new SystemLoadIndicatorImpl(
                meterRegistry,
                prometheusClient,
                sentinelRuleManager,
                runtimeConfigService,
                nodeIdentity
        );
    }

    private Map<String, LoadScoringMetricConfig> defaultMetricMap() {
        LoadScoringConfig config = LoadControlDefaults.defaultScoringConfig();
        Map<String, LoadScoringMetricConfig> metrics = new LinkedHashMap<>();
        config.getInstanceMetrics().forEach(metric -> metrics.put(metric.getMetricKey(), metric));
        config.getHostMetrics().forEach(metric -> metrics.put(metric.getMetricKey(), metric));
        config.getRegionMetrics().forEach(metric -> metrics.put(metric.getMetricKey(), metric));
        return metrics;
    }

    @Nested
    @DisplayName("getSnapshot 方法测试")
    class GetSnapshotTests {

        @Test
        @DisplayName("应返回有效的负载快照")
        void getSnapshot_shouldReturnValidSnapshot() {
            LoadSnapshot snapshot = loadIndicator.getSnapshot();

            assertThat(snapshot).isNotNull();
            assertThat(snapshot.timestamp()).isNotNull();
            assertThat(snapshot.totalScore()).isBetween(0, 100);
            assertThat(snapshot.level()).isNotNull();
        }

        @Test
        @DisplayName("连续调用应返回缓存的结果")
        void getSnapshot_shouldReturnCachedResult() {
            LoadSnapshot snapshot1 = loadIndicator.getSnapshot();
            LoadSnapshot snapshot2 = loadIndicator.getSnapshot();

            assertThat(snapshot1.timestamp()).isEqualTo(snapshot2.timestamp());
        }
    }

    @Nested
    @DisplayName("getLoadLevel 方法测试")
    class GetLoadLevelTests {

        @Test
        @DisplayName("应返回有效的负载级别")
        void getLoadLevel_shouldReturnValidLevel() {
            LoadLevel level = loadIndicator.getLoadLevel();

            assertThat(level).isNotNull();
            assertThat(level).isIn(LoadLevel.LOW, LoadLevel.NORMAL, LoadLevel.HIGH, LoadLevel.CRITICAL);
        }
    }

    @Nested
    @DisplayName("isOverloaded 方法测试")
    class IsOverloadedTests {

        @Test
        @DisplayName("应返回布尔值")
        void isOverloaded_shouldReturnBoolean() {
            boolean overloaded = loadIndicator.isOverloaded();

            assertThat(overloaded).isNotNull();
        }
    }
}
