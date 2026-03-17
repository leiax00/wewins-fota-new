package com.wewins.fota.cache.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TTL 随机化工具
 * <p>
 * 用于防止缓存雪崩，对 TTL 添加随机偏移
 * </p>
 */
public final class RandomizedTtlUtil {

    private static final double DEFAULT_RANDOM_FACTOR = 0.1;

    private RandomizedTtlUtil() {
    }

    /**
     * 获取随机化 TTL（默认 10% 随机偏移）
     *
     * @param baseTtl 基础 TTL（秒）
     * @return 随机化后的 TTL（秒），范围：baseTtl * 0.9 ~ baseTtl * 1.1
     */
    public static long getRandomizedTtl(long baseTtl) {
        return getRandomizedTtl(baseTtl, DEFAULT_RANDOM_FACTOR);
    }

    /**
     * 获取随机化 TTL
     *
     * @param baseTtl       基础 TTL（秒）
     * @param randomFactor  随机偏移因子（0.1 表示 ±10%）
     * @return 随机化后的 TTL（秒）
     */
    public static long getRandomizedTtl(long baseTtl, double randomFactor) {
        if (baseTtl <= 0) {
            return 1;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long randomOffset = (long) (baseTtl * randomFactor * (random.nextDouble() - 0.5) * 2);
        return Math.max(1, baseTtl + randomOffset);
    }
}
