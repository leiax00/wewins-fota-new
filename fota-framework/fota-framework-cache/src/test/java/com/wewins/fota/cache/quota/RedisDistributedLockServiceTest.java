package com.wewins.fota.cache.quota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 分布式锁服务单元测试
 * <p>
 * 测试分布式锁的获取、释放和回调执行逻辑
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@ExtendWith(MockitoExtension.class)
class RedisDistributedLockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private DefaultRedisScript<Long> releaseLockScript;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisDistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new RedisDistributedLockService(redisTemplate, releaseLockScript);
    }

    @Test
    void testTryLock_Success() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // Act
        DistributedLockService.LockResult result = lockService.tryLock("test_lock");

        // Assert
        assertTrue(result.acquired());
        assertNotNull(result.token());
        assertFalse(result.token().isEmpty());
    }

    @Test
    void testTryLock_Failed_WhenLockHeldByOthers() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        // Act
        DistributedLockService.LockResult result = lockService.tryLock("test_lock");

        // Assert
        assertFalse(result.acquired());
        assertNull(result.token());
    }

    @Test
    void testTryLock_Failed_WhenLockNameIsEmpty() {
        // Act
        DistributedLockService.LockResult result = lockService.tryLock("");

        // Assert
        assertFalse(result.acquired());
        assertNull(result.token());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void testTryLock_WithCustomExpireTime() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(60L), any(TimeUnit.class)))
                .thenReturn(true);

        // Act
        DistributedLockService.LockResult result = lockService.tryLock("test_lock", 60);

        // Assert
        assertTrue(result.acquired());
        verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void testTryLock_Degraded_WhenRedisThrowsException() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection failed"));

        // Act
        DistributedLockService.LockResult result = lockService.tryLock("test_lock");

        // Assert
        assertFalse(result.acquired());
        assertNull(result.token());
    }

    @Test
    void testReleaseLock_Success() {
        // Arrange
        when(redisTemplate.execute(
                eq(releaseLockScript),
                anyList(),
                eq("test_token")
        )).thenReturn(1L);

        // Act
        boolean result = lockService.releaseLock("test_lock", "test_token");

        // Assert
        assertTrue(result);
    }

    @Test
    void testReleaseLock_Failed_WhenTokenMismatch() {
        // Arrange
        when(redisTemplate.execute(
                eq(releaseLockScript),
                anyList(),
                eq("wrong_token")
        )).thenReturn(0L);

        // Act
        boolean result = lockService.releaseLock("test_lock", "wrong_token");

        // Assert
        assertFalse(result);
    }

    @Test
    void testReleaseLock_Failed_WhenLockNameIsEmpty() {
        // Act
        boolean result = lockService.releaseLock("", "test_token");

        // Assert
        assertFalse(result);
        verify(redisTemplate, never()).execute(any(), anyList(), anyString());
    }

    @Test
    void testReleaseLock_Failed_WhenTokenIsEmpty() {
        // Act
        boolean result = lockService.releaseLock("test_lock", "");

        // Assert
        assertFalse(result);
        verify(redisTemplate, never()).execute(any(), anyList(), anyString());
    }

    @Test
    void testExecuteWithLock_Success_WhenCallbackExecuted() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(redisTemplate.execute(eq(releaseLockScript), anyList(), anyString()))
                .thenReturn(1L);

        Runnable callback = mock(Runnable.class);

        // Act
        boolean result = lockService.executeWithLock("test_lock", callback);

        // Assert
        assertTrue(result);
        verify(callback).run();
        verify(redisTemplate).execute(eq(releaseLockScript), anyList(), anyString());
    }

    @Test
    void testExecuteWithLock_Failed_WhenLockNotAcquired() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        Runnable callback = mock(Runnable.class);

        // Act
        boolean result = lockService.executeWithLock("test_lock", callback);

        // Assert
        assertFalse(result);
        verify(callback, never()).run();
    }

    @Test
    void testExecuteWithLock_WithReturnValue_Success() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(redisTemplate.execute(eq(releaseLockScript), anyList(), anyString()))
                .thenReturn(1L);

        java.util.function.Supplier<String> callback = mock(java.util.function.Supplier.class);
        when(callback.get()).thenReturn("success");

        // Act
        Optional<String> result = lockService.executeWithLock("test_lock", callback);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("success", result.get());
        verify(callback).get();
    }

    @Test
    void testExecuteWithLock_WithReturnValue_Failed_WhenLockNotAcquired() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        java.util.function.Supplier<String> callback = mock(java.util.function.Supplier.class);

        // Act
        Optional<String> result = lockService.executeWithLock("test_lock", callback);

        // Assert
        assertFalse(result.isPresent());
        verify(callback, never()).get();
    }

    @Test
    void testLockTokenUniqueness() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // Act
        DistributedLockService.LockResult result1 = lockService.tryLock("test_lock");
        DistributedLockService.LockResult result2 = lockService.tryLock("test_lock");

        // Assert: 每次获取锁都生成不同的 token
        assertNotEquals(result1.token(), result2.token());
    }

    @Test
    void testLockKeyFormat() {
        // Arrange
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // Act
        lockService.tryLock("test_lock");

        // Assert: 验证 key 使用固定格式（不含时间戳）
        verify(valueOperations).setIfAbsent(
                eq("fota:lock:test_lock"),
                anyString(),
                anyLong(),
                any(TimeUnit.class)
        );
    }
}
