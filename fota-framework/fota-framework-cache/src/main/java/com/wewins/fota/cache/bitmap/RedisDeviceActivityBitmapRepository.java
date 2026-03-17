package com.wewins.fota.cache.bitmap;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备活跃度 Bitmap Redis 实现
 * <p>
 * 基于 Redis Bitmap 实现设备活跃度跟踪：
 * <ul>
 *   <li>使用 SETBIT 标记设备活跃</li>
 *   <li>使用 GETBIT 检查设备活跃状态</li>
 *   <li>使用 BITCOUNT 统计活跃设备数</li>
 *   <li>使用 BITOP OR 进行多日合并统计</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-17
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisDeviceActivityBitmapRepository implements DeviceActivityBitmapRepository {

    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    /**
     * 日期格式化器（yyyyMMdd）
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public void markActive(LocalDate day, long deviceId) {
        String key = buildBitmapKey(day);
        try {
            redisTemplate.opsForValue().setBit(key, deviceId, true);
            log.debug("标记设备活跃: day={}, deviceId={}, key={}", day, deviceId, key);
        } catch (Exception e) {
            log.error("标记设备活跃失败: day={}, deviceId={}, key={}", day, deviceId, key, e);
            // 降级处理：记录错误但不中断主流程
        }
    }

    @Override
    public boolean isActive(LocalDate day, long deviceId) {
        String key = buildBitmapKey(day);
        try {
            Boolean isActive = redisTemplate.opsForValue().getBit(key, deviceId);
            return Boolean.TRUE.equals(isActive);
        } catch (Exception e) {
            log.error("检查设备活跃状态失败: day={}, deviceId={}, key={}", day, deviceId, key, e);
            return false;
        }
    }

    @Override
    public long countActive(LocalDate day) {
        String key = buildBitmapKey(day);
        return bitCount(key, "day=" + day);
    }

    private long bitCount(String key, String context) {
        try {
            return redisTemplate.execute((RedisCallback<Long>) connection -> {
                byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
                return connection.bitCount(keyBytes);
            });
        } catch (Exception e) {
            log.error("统计活跃设备数失败: {}, key={}", context, key, e);
            return 0L;
        }
    }

    @Override
    public long countActiveUnion(LocalDate endDay, int days) {
        if (days <= 0) {
            return 0L;
        }

        try {
            // 构建多日 bitmap 键列表
            List<byte[]> keys = new ArrayList<>(days);
            for (int i = days - 1; i >= 0; i--) {
                LocalDate day = endDay.minusDays(i);
                keys.add(buildBitmapKey(day).getBytes(StandardCharsets.UTF_8));
            }

            // 生成临时键名称
            String tempKey = buildBitOpTempKey("union", endDay, days);
            byte[] tempKeyBytes = tempKey.getBytes(StandardCharsets.UTF_8);

            // 执行 BITOP OR 操作并统计
            Long count = redisTemplate.execute((RedisCallback<Long>) connection -> {
                // 执行 BITOP OR
                connection.bitOp(RedisStringCommands.BitOperation.OR,
                        tempKeyBytes,
                        keys.toArray(new byte[0][0]));

                // 设置临时键 TTL
                connection.expire(tempKeyBytes,
                        RedisKeyConstants.BITOP_TEMP_TTL_SECONDS);

                // 统计并集的活跃设备数
                return connection.bitCount(tempKeyBytes);
            });

            log.debug("统计多日活跃设备: endDay={}, days={}, tempKey={}, count={}",
                    endDay, days, tempKey, count);

            return count != null ? count : 0L;

        } catch (Exception e) {
            log.error("统计多日活跃设备失败: endDay={}, days={}", endDay, days, e);
            return 0L;
        }
    }

    @Override
    public long countSilentDevices(LocalDate endDay, int silentDays) {
        // 当前实现无法获取设备总数，返回 -1 表示不可用
        // 后续可通过以下方式改进：
        // 1. 从数据库查询设备总数
        // 2. 在 Redis 中维护设备总数计数器
        // 3. 通过业务逻辑传入设备总数参数

        log.warn("统计沉默设备功能暂未实现: endDay={}, silentDays={}", endDay, silentDays);
        return -1L;
    }

    /**
     * 构建 Bitmap Key
     * <p>
     * 格式：fota:active:yyyyMMdd
     * </p>
     *
     * @param day 日期
     * @return Redis Key
     */
    private String buildBitmapKey(LocalDate day) {
        return String.format(RedisKeyConstants.ACTIVE_BITMAP_KEY_TEMPLATE,
                day.format(DATE_FORMATTER));
    }

    /**
     * 构建 BITOP 临时键
     * <p>
     * 格式：fota:tmp:bitop:{operation}:{endDay}_{days}
     * </p>
     *
     * @param operation 操作类型（union、intersect、diff）
     * @param endDay    结束日期
     * @param days      天数
     * @return 临时键名称
     */
    private String buildBitOpTempKey(String operation, LocalDate endDay, int days) {
        String identifier = String.format("%s_%d", endDay.format(DATE_FORMATTER), days);
        return String.format(RedisKeyConstants.BITOP_TEMP_KEY_TEMPLATE, operation, identifier);
    }
}
