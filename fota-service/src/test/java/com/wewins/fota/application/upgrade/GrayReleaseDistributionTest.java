package com.wewins.fota.application.upgrade;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;

/**
 * 灰度发布分布测试工具
 * <p>
 * 独立验证灰度发布算法的正确性和分布均匀性
 * 不依赖 Spring 上下文，可直接运行
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
public class GrayReleaseDistributionTest {

    private static final HashFunction HASH_FUNC = Hashing.murmur3_32();
    private static final int BUCKET_COUNT = 10000;

    /**
     * 判断设备是否命中灰度发布
     */
    public static boolean hitsGrayBucket(String imei, int grayRate) {
        if (grayRate <= 0) {
            return false;
        }
        if (grayRate >= 100) {
            return true;
        }
        if (imei == null || imei.isEmpty()) {
            return false;
        }

        int hash = HASH_FUNC.hashString(imei, StandardCharsets.UTF_8).asInt();
        int bucket = (hash & Integer.MAX_VALUE) % BUCKET_COUNT;
        int threshold = (int) (BUCKET_COUNT * grayRate / 100.0);

        return bucket < threshold;
    }

    /**
     * 统计命中次数
     */
    public static int countHits(int sampleSize, int grayRate) {
        int hits = 0;
        for (int i = 0; i < sampleSize; i++) {
            String imei = String.format("354972069%010d", i);
            if (hitsGrayBucket(imei, grayRate)) {
                hits++;
            }
        }
        return hits;
    }

    /**
     * 运行分布测试
     */
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("灰度发布算法分布测试");
        System.out.println("=".repeat(60));

        int sampleSize = 10000;

        // 测试不同灰度比例的分布
        testDistribution(sampleSize, 1, "1%");
        testDistribution(sampleSize, 5, "5%");
        testDistribution(sampleSize, 10, "10%");
        testDistribution(sampleSize, 25, "25%");
        testDistribution(sampleSize, 50, "50%");
        testDistribution(sampleSize, 75, "75%");
        testDistribution(sampleSize, 90, "90%");
        testDistribution(sampleSize, 99, "99%");

        System.out.println("=".repeat(60));
        System.out.println("所有测试通过！");
        System.out.println("=".repeat(60));

        // 边界值测试
        System.out.println("\n边界值测试:");
        System.out.println("grayRate=0: " + hitsGrayBucket("123456789012345", 0)); // false
        System.out.println("grayRate=100: " + hitsGrayBucket("123456789012345", 100)); // true
        System.out.println("null IMEI: " + hitsGrayBucket(null, 50)); // false
        System.out.println("empty IMEI: " + hitsGrayBucket("", 50)); // false

        // 一致性测试
        System.out.println("\n一致性测试:");
        String imei = "354972069009027";
        boolean result1 = hitsGrayBucket(imei, 50);
        boolean result2 = hitsGrayBucket(imei, 50);
        boolean result3 = hitsGrayBucket(imei, 50);
        System.out.println("同一 IMEI 多次计算: " + result1 + " = " + result2 + " = " + result3);

        // 桶号测试
        System.out.println("\n桶号测试:");
        int hash = HASH_FUNC.hashString(imei, StandardCharsets.UTF_8).asInt();
        int bucket = (hash & Integer.MAX_VALUE) % BUCKET_COUNT;
        System.out.println("IMEI=" + imei + ", hash=" + hash + ", bucket=" + bucket);

        System.out.println("\n测试完成！");
    }

    /**
     * 测试特定灰度比例的分布
     */
    private static void testDistribution(int sampleSize, int grayRate, String label) {
        int hits = countHits(sampleSize, grayRate);
        double actualRate = (double) hits / sampleSize * 100;

        // 预期范围：灰度比例 ±1%
        double lowerBound = grayRate - 1.0;
        double upperBound = grayRate + 1.0;

        // 对于 1% 的情况，放宽到 ±0.5%
        if (grayRate == 1) {
            lowerBound = 0.5;
            upperBound = 1.5;
        }

        boolean passed = actualRate >= lowerBound && actualRate <= upperBound;

        System.out.printf("%s 灰度: 预期 %.1f%%, 实际 %.2f%% (%d/%d) %s%n",
                label, (double) grayRate, actualRate, hits, sampleSize,
                passed ? "✓" : "✗");

        if (!passed) {
            throw new AssertionError(String.format(
                    "%s 灰度测试失败: 预期 [%.1f%%, %.1f%%], 实际 %.2f%%",
                    label, lowerBound, upperBound, actualRate));
        }
    }
}
