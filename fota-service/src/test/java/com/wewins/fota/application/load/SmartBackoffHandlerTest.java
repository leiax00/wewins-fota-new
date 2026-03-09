package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.BackoffResult;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SmartBackoffHandlerTest {

    @Mock
    private SystemLoadIndicator loadIndicator;

    private SmartBackoffHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SmartBackoffHandler(loadIndicator);
    }

    @Nested
    @DisplayName("calculateBackoff 方法测试")
    class CalculateBackoffTests {

        @Test
        @DisplayName("FLOW_QPS 应返回合理的退避时间")
        void calculateBackoff_flowQps_shouldReturnReasonableBackoff() {
            LoadSnapshot snapshot = LoadSnapshot.builder()
                    .totalScore(30)
                    .level(LoadLevel.NORMAL)
                    .cpuUsage(50)
                    .memoryUsage(50)
                    .qps(5000)
                    .p99Latency(20)
                    .connectionPoolUsage(50)
                    .build();

            BackoffResult result = handler.calculateBackoff("FLOW_QPS", snapshot);

            assertThat(result).isNotNull();
            assertThat(result.retryAfterSeconds()).isBetween(60, 7200);
            assertThat(result.retryAfterTime()).isAfter(Instant.now());
            assertThat(result.reason()).isEqualTo("FLOW_QPS");
        }

        @Test
        @DisplayName("DEGRADE 应返回更长的退避时间")
        void calculateBackoff_degrade_shouldReturnLongerBackoff() {
            LoadSnapshot snapshot = LoadSnapshot.builder()
                    .totalScore(30)
                    .level(LoadLevel.NORMAL)
                    .cpuUsage(50)
                    .memoryUsage(50)
                    .qps(5000)
                    .p99Latency(20)
                    .connectionPoolUsage(50)
                    .build();

            BackoffResult degradeResult = handler.calculateBackoff("DEGRADE", snapshot);
            BackoffResult qpsResult = handler.calculateBackoff("FLOW_QPS", snapshot);

            assertThat(degradeResult.retryAfterSeconds()).isGreaterThan(qpsResult.retryAfterSeconds());
        }

        @Test
        @DisplayName("CRITICAL 负载应返回更长的退避时间")
        void calculateBackoff_criticalLoad_shouldReturnLongerBackoff() {
            LoadSnapshot normalSnapshot = LoadSnapshot.builder()
                    .totalScore(30)
                    .level(LoadLevel.NORMAL)
                    .cpuUsage(50)
                    .memoryUsage(50)
                    .qps(5000)
                    .p99Latency(20)
                    .connectionPoolUsage(50)
                    .build();

            LoadSnapshot criticalSnapshot = LoadSnapshot.builder()
                    .totalScore(90)
                    .level(LoadLevel.CRITICAL)
                    .cpuUsage(95)
                    .memoryUsage(95)
                    .qps(15000)
                    .p99Latency(80)
                    .connectionPoolUsage(98)
                    .build();

            BackoffResult normalResult = handler.calculateBackoff("FLOW_QPS", normalSnapshot);
            BackoffResult criticalResult = handler.calculateBackoff("FLOW_QPS", criticalSnapshot);

            assertThat(criticalResult.retryAfterSeconds()).isGreaterThan(normalResult.retryAfterSeconds());
        }

        @Test
        @DisplayName("退避时间应在有效范围内")
        void calculateBackoff_shouldBeWithinValidRange() {
            LoadSnapshot snapshot = LoadSnapshot.builder()
                    .totalScore(50)
                    .level(LoadLevel.HIGH)
                    .cpuUsage(70)
                    .memoryUsage(70)
                    .qps(10000)
                    .p99Latency(40)
                    .connectionPoolUsage(80)
                    .build();

            BackoffResult result = handler.calculateBackoff("FLOW_QPS", snapshot);

            assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(60);
            assertThat(result.retryAfterSeconds()).isLessThanOrEqualTo(7200);
        }
    }
}
