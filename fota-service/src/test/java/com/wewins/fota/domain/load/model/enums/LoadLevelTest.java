package com.wewins.fota.domain.load.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadLevelTest {

    @Test
    @DisplayName("fromScore - 低分应返回 LOW")
    void fromScore_lowScore_shouldReturnLow() {
        assertThat(LoadLevel.fromScore(0)).isEqualTo(LoadLevel.LOW);
        assertThat(LoadLevel.fromScore(10)).isEqualTo(LoadLevel.LOW);
        assertThat(LoadLevel.fromScore(24)).isEqualTo(LoadLevel.LOW);
    }

    @Test
    @DisplayName("fromScore - 中等分数应返回 NORMAL")
    void fromScore_mediumScore_shouldReturnNormal() {
        assertThat(LoadLevel.fromScore(25)).isEqualTo(LoadLevel.NORMAL);
        assertThat(LoadLevel.fromScore(40)).isEqualTo(LoadLevel.NORMAL);
        assertThat(LoadLevel.fromScore(49)).isEqualTo(LoadLevel.NORMAL);
    }

    @Test
    @DisplayName("fromScore - 高分应返回 HIGH")
    void fromScore_highScore_shouldReturnHigh() {
        assertThat(LoadLevel.fromScore(50)).isEqualTo(LoadLevel.HIGH);
        assertThat(LoadLevel.fromScore(60)).isEqualTo(LoadLevel.HIGH);
        assertThat(LoadLevel.fromScore(74)).isEqualTo(LoadLevel.HIGH);
    }

    @Test
    @DisplayName("fromScore - 极高分应返回 CRITICAL")
    void fromScore_criticalScore_shouldReturnCritical() {
        assertThat(LoadLevel.fromScore(75)).isEqualTo(LoadLevel.CRITICAL);
        assertThat(LoadLevel.fromScore(90)).isEqualTo(LoadLevel.CRITICAL);
        assertThat(LoadLevel.fromScore(100)).isEqualTo(LoadLevel.CRITICAL);
    }

    @Test
    @DisplayName("fromScore - 无效分数应抛出异常")
    void fromScore_invalidScore_shouldThrowException() {
        assertThatThrownBy(() -> LoadLevel.fromScore(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoadLevel.fromScore(101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isOverloaded - LOW 和 NORMAL 应返回 false")
    void isOverloaded_lowAndNormal_shouldReturnFalse() {
        assertThat(LoadLevel.LOW.isOverloaded()).isFalse();
        assertThat(LoadLevel.NORMAL.isOverloaded()).isFalse();
    }

    @Test
    @DisplayName("isOverloaded - HIGH 和 CRITICAL 应返回 true")
    void isOverloaded_highAndCritical_shouldReturnTrue() {
        assertThat(LoadLevel.HIGH.isOverloaded()).isTrue();
        assertThat(LoadLevel.CRITICAL.isOverloaded()).isTrue();
    }

    @Test
    @DisplayName("isCritical - 仅 CRITICAL 应返回 true")
    void isCritical_onlyCritical_shouldReturnTrue() {
        assertThat(LoadLevel.LOW.isCritical()).isFalse();
        assertThat(LoadLevel.NORMAL.isCritical()).isFalse();
        assertThat(LoadLevel.HIGH.isCritical()).isFalse();
        assertThat(LoadLevel.CRITICAL.isCritical()).isTrue();
    }
}
