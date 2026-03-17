package com.wewins.fota.application.upgrade;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 灰度发布算法服务
 * <p>
 * 使用 MurmurHash3 算法实现设备灰度发布判定
 * </p>
 * <p>
 * 算法原理：
 * </p>
 * <ul>
 *   <li>使用 MurmurHash3 对设备 IMEI 计算哈希值</li>
 *   <li>将哈希值映射到 10000 个桶中</li>
 *   <li>根据灰度比例判定设备是否命中灰度</li>
 * </ul>
 * <p>
 * 特点：
 * </p>
 * <ul>
 *   <li>分布均匀：同一设备多次计算结果一致</li>
 *   <li>高性能：哈希计算速度快</li>
 *   <li>精度高：使用 10000 桶，最小粒度 0.01%</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Service
public class GrayReleaseService {

    /**
     * MurmurHash3 哈希函数
     * <p>
     * Guava 的 MurmurHash3 实现，具有良好的分布性和性能
     * </p>
     */
    private static final HashFunction HASH_FUNC = Hashing.murmur3_32();

    /**
     * 桶数量
     * <p>
     * 使用 10000 个桶提高精度，最小粒度可达 0.01%
     * </p>
     */
    private static final int BUCKET_COUNT = 10000;

    /**
     * 判断设备是否命中灰度发布
     * <p>
     * 算法流程：
     * </p>
     * <ol>
     *   <li>边界值校验：grayRate &le; 0 返回 false，&ge; 100 返回 true</li>
     *   <li>空值保护：imei 为 null 或空返回 false</li>
     *   <li>哈希计算：使用 MurmurHash3 计算设备 IMEI 的哈希值</li>
     *   <li>桶映射：将哈希值映射到 [0, 10000) 桶中</li>
     *   <li>灰度判定：桶号 &lt; 阈值则命中</li>
     * </ol>
     *
     * @param imei     设备 IMEI，不能为 null
     * @param grayRate 灰度比例 (0-100)，0 表示全量不发布，100 表示全量发布
     * @return true=命中灰度，false=未命中
     */
    public boolean hitsGrayBucket(String imei, int grayRate) {
        // 边界值校验
        if (grayRate <= 0) {
            return false;
        }
        if (grayRate >= 100) {
            return true;
        }

        // 空值保护
        if (imei == null || imei.isEmpty()) {
            log.debug("IMEI 为空，返回未命中灰度");
            return false;
        }

        // 使用 MurmurHash3 计算哈希值
        int hash = HASH_FUNC.hashString(imei, StandardCharsets.UTF_8).asInt();

        // 正确的取模运算（处理负数）
        // 使用 & Integer.MAX_VALUE 确保哈希值为非负数
        int bucket = (hash & Integer.MAX_VALUE) % BUCKET_COUNT;

        // 计算阈值
        int threshold = (int) (BUCKET_COUNT * grayRate / 100.0);

        boolean hits = bucket < threshold;
        log.debug("灰度判定: imei={}, grayRate={}, bucket={}, threshold={}, hits={}",
                imei, grayRate, bucket, threshold, hits);

        return hits;
    }

    /**
     * 判断设备是否命中灰度发布（支持 null 值）
     * <p>
     * 当 imei 为 null 时返回 false，不会抛出异常
     * </p>
     *
     * @param imei     设备 IMEI，可以为 null
     * @param grayRate 灰度比例 (0-100)
     * @return true=命中灰度，false=未命中
     */
    public boolean hitsGrayBucketSafe(String imei, int grayRate) {
        if (imei == null || imei.isEmpty()) {
            return false;
        }
        return hitsGrayBucket(imei, grayRate);
    }

    /**
     * 获取设备所在的灰度桶号
     * <p>
     * 用于调试和监控，可以查看设备的分布情况
     * </p>
     *
     * @param imei 设备 IMEI，不能为 null
     * @return 桶号 [0, 10000)
     */
    public int getBucketNumber(String imei) {
        if (imei == null || imei.isEmpty()) {
            throw new IllegalArgumentException("IMEI 不能为空");
        }
        int hash = HASH_FUNC.hashString(imei, StandardCharsets.UTF_8).asInt();
        return (hash & Integer.MAX_VALUE) % BUCKET_COUNT;
    }

    /**
     * 获取桶数量
     *
     * @return 桶数量，固定为 10000
     */
    public int getBucketCount() {
        return BUCKET_COUNT;
    }
}
