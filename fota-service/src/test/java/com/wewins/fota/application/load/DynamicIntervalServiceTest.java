package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicIntervalServiceTest {

    @Mock
    private SystemLoadIndicator loadIndicator;

    private DynamicIntervalService service;

    @BeforeEach
    void setUp() {
        service = new DynamicIntervalService(loadIndicator);
    }

    @Nested
    @DisplayName("calculateCheckInterval 方法测试")
    class CalculateCheckIntervalTests {

        @Test
        @DisplayName("LOW 负载应返回较短间隔")
        void calculateCheckInterval_lowLoad_shouldReturnShorterInterval() {
            when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.LOW);

            int interval = service.calculateCheckInterval(1L, true);

            assertThat(interval).isBetween(1800, 172800);
        }

        @Test
        @DisplayName("CRITICAL 负载应返回较长间隔")
        void calculateCheckInterval_criticalLoad_shouldReturnLongerInterval() {
            when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.CRITICAL);

            int interval = service.calculateCheckInterval(1L, true);

            assertThat(interval).isBetween(1800, 172800);
        }

        @Test
        @DisplayName("自动模式应使用更长的基础间隔")
        void calculateCheckInterval_autoMode_shouldUseLongerBaseInterval() {
            when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.NORMAL);

            int autoInterval = service.calculateCheckInterval(1L, true);
            int manualInterval = service.calculateCheckInterval(1L, false);

            assertThat(autoInterval).isGreaterThanOrEqualTo(manualInterval);
        }

        @Test
        @DisplayName("间隔应在有效范围内")
        void calculateCheckInterval_shouldBeWithinValidRange() {
            when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.NORMAL);

            int interval = service.calculateCheckInterval(1L, true);

            assertThat(interval).isGreaterThanOrEqualTo(1800);
            assertThat(interval).isLessThanOrEqualTo(172800);
        }
    }

    @Nested
    @DisplayName("calculateDownloadDelay 方法测试")
    class CalculateDownloadDelayTests {

        @Test
        @DisplayName("LOW 负载应返回较短延迟")
        void calculateDownloadDelay_lowLoad_shouldReturnShorterDelay() {
            when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.LOW);

            int delay = service.calculateDownloadDelay(1L);

            assertThat(delay).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("CRITICAL 负载应返回较长延迟")
        void calculateDownloadDelay_criticalLoad_shouldReturnLongerDelay() {
            when(loadIndicator.getLoadLevel()).thenReturn(LoadLevel.CRITICAL);

            int delay = service.calculateDownloadDelay(1L);

            assertThat(delay).isGreaterThanOrEqualTo(0);
        }
    }
}
