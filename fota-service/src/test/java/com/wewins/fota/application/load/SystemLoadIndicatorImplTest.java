package com.wewins.fota.application.load;

import com.wewins.fota.domain.load.model.enums.LoadLevel;
import com.wewins.fota.domain.load.model.vo.LoadSnapshot;
import com.wewins.fota.domain.load.service.SystemLoadIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemLoadIndicatorImplTest {

    private SystemLoadIndicator loadIndicator;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        loadIndicator = new SystemLoadIndicatorImpl(meterRegistry);
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
