package com.wewins.fota.cache.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 策略配额服务单元测试
 * <p>
 * 测试配额检查和递增的核心逻辑
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@ExtendWith(MockitoExtension.class)
class RedisPolicyQuotaServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private DefaultRedisScript<Long> quotaCheckAndIncrementScript;

    private RedisPolicyQuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new RedisPolicyQuotaService(redisTemplate, quotaCheckAndIncrementScript);
    }

    @Test
    void testCheckAndIncrementQuota_Success_WhenQuotaAvailable() {
        // Arrange: 配额可用
        when(redisTemplate.execute(
                eq(quotaCheckAndIncrementScript),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(1L);

        // Act
        boolean result = quotaService.checkAndIncrementQuota(101L, 100);

        // Assert
        assertTrue(result);
        verify(redisTemplate).execute(
                eq(quotaCheckAndIncrementScript),
                anyList(),
                eq("100"),
                anyString()
        );
    }

    @Test
    void testCheckAndIncrementQuota_Failed_WhenQuotaExhausted() {
        // Arrange: 配额已用尽
        when(redisTemplate.execute(
                eq(quotaCheckAndIncrementScript),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(0L);

        // Act
        boolean result = quotaService.checkAndIncrementQuota(101L, 100);

        // Assert
        assertFalse(result);
    }

    @Test
    void testCheckAndIncrementQuota_Failed_WhenPolicyIdIsNull() {
        // Act
        boolean result = quotaService.checkAndIncrementQuota(null, 100);

        // Assert
        assertFalse(result);
        verify(redisTemplate, never()).execute(any(), anyList(), anyString(), anyString());
    }

    @Test
    void testCheckAndIncrementQuota_Failed_WhenMaxQuotaIsInvalid() {
        // Act & Assert
        assertFalse(quotaService.checkAndIncrementQuota(101L, 0));
        assertFalse(quotaService.checkAndIncrementQuota(101L, -10));

        verify(redisTemplate, never()).execute(any(), anyList(), anyString(), anyString());
    }

    @Test
    void testCheckAndIncrementQuota_Degraded_WhenRedisThrowsException() {
        // Arrange: Redis 异常
        when(redisTemplate.execute(
                eq(quotaCheckAndIncrementScript),
                anyList(),
                anyString(),
                anyString()
        )).thenThrow(new RuntimeException("Redis connection failed"));

        // Act: 异常降级，返回 true（允许通过）
        boolean result = quotaService.checkAndIncrementQuota(101L, 100);

        // Assert: 异常降级，允许请求通过
        assertTrue(result);
    }

    @Test
    void testCheckAndIncrementQuota_Degraded_WhenRedisReturnsNull() {
        // Arrange: Redis 返回 null
        when(redisTemplate.execute(
                eq(quotaCheckAndIncrementScript),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(null);

        // Act
        boolean result = quotaService.checkAndIncrementQuota(101L, 100);

        // Assert
        assertFalse(result);
    }

    @Test
    void testGetCurrentUsage_Success() {
        // Arrange
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(contains("101"))).thenReturn("42");

        // Act
        long usage = quotaService.getCurrentUsage(101L);

        // Assert
        assertEquals(42L, usage);
    }

    @Test
    void testGetCurrentUsage_ReturnsZero_WhenKeyNotExists() {
        // Arrange
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        // Act
        long usage = quotaService.getCurrentUsage(101L);

        // Assert
        assertEquals(0L, usage);
    }

    @Test
    void testGetCurrentUsage_ReturnsZero_WhenPolicyIdIsNull() {
        // Act
        long usage = quotaService.getCurrentUsage(null);

        // Assert
        assertEquals(0L, usage);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void testResetQuota_Success() {
        // Act
        quotaService.resetQuota(101L);

        // Assert
        verify(redisTemplate).delete(contains("101"));
    }

    @Test
    void testResetQuota_DoesNothing_WhenPolicyIdIsNull() {
        // Act
        quotaService.resetQuota(null);

        // Assert
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void testQuotaKeyFormat() {
        // Arrange
        ArgumentCaptor<List<String>> keyCaptor = ArgumentCaptor.forClass(List.class);
        when(redisTemplate.execute(
                eq(quotaCheckAndIncrementScript),
                keyCaptor.capture(),
                anyString(),
                anyString()
        )).thenReturn(1L);

        // Act
        quotaService.checkAndIncrementQuota(101L, 100);

        // Assert: 验证 key 格式为 fota:quota:policy:101:yyyyMMdd
        List<String> keys = keyCaptor.getValue();
        assertEquals(1, keys.size());
        String key = keys.get(0);
        assertTrue(key.startsWith("fota:quota:policy:101:"));
        assertTrue(key.matches("fota:quota:policy:101:\\d{8}"));
    }
}
