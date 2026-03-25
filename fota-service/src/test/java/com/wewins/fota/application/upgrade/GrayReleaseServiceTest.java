package com.wewins.fota.application.upgrade;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 灰度发布算法服务单元测试
 * <p>
 * 验证灰度发布算法的正确性和分布均匀性
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@DisplayName("灰度发布算法测试")
class GrayReleaseServiceTest {

    private GrayReleaseService grayReleaseService;

    @BeforeEach
    void setUp() {
        grayReleaseService = new GrayReleaseService();
    }

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTests {

        @Test
        @DisplayName("灰度比例 0% 应该全部不命中")
        void testGrayRateZero() {
            String imei = "123456789012345";
            assertFalse(grayReleaseService.hitsGrayBucket(imei, 0));
            assertFalse(grayReleaseService.hitsGrayBucket(imei, -1));
            assertFalse(grayReleaseService.hitsGrayBucket(imei, -100));
        }

        @Test
        @DisplayName("灰度比例 100% 应该全部命中")
        void testGrayRateHundred() {
            String imei = "123456789012345";
            assertTrue(grayReleaseService.hitsGrayBucket(imei, 100));
            assertTrue(grayReleaseService.hitsGrayBucket(imei, 101));
            assertTrue(grayReleaseService.hitsGrayBucket(imei, 200));
        }

        @Test
        @DisplayName("空 IMEI 应该返回不命中")
        void testNullImei() {
            assertFalse(grayReleaseService.hitsGrayBucket(null, 50));
            assertFalse(grayReleaseService.hitsGrayBucket("", 50));
        }

        @Test
        @DisplayName("安全方法应该正确处理 null 值")
        void testSafeMethodWithNull() {
            assertFalse(grayReleaseService.hitsGrayBucketSafe(null, 50));
            assertFalse(grayReleaseService.hitsGrayBucketSafe("", 50));
        }
    }

    @Nested
    @DisplayName("一致性测试")
    class ConsistencyTests {

        @Test
        @DisplayName("同一 IMEI 多次计算结果应该一致")
        void testConsistency() {
            String imei = "123456789012345";
            int grayRate = 50;

            boolean result1 = grayReleaseService.hitsGrayBucket(imei, grayRate);
            boolean result2 = grayReleaseService.hitsGrayBucket(imei, grayRate);
            boolean result3 = grayReleaseService.hitsGrayBucket(imei, grayRate);

            assertEquals(result1, result2);
            assertEquals(result2, result3);
        }

        @Test
        @DisplayName("不同 IMEI 应该有不同的哈希值")
        void testDifferentImei() {
            String imei1 = "123456789012345";
            String imei2 = "123456789012346";

            int bucket1 = grayReleaseService.getBucketNumber(imei1);
            int bucket2 = grayReleaseService.getBucketNumber(imei2);

            assertNotEquals(bucket1, bucket2);
        }
    }

    @Nested
    @DisplayName("桶号计算测试")
    class BucketTests {

        @Test
        @DisplayName("桶号应该在 [0, 10000) 范围内")
        void testBucketRange() {
            String imei = "123456789012345";
            int bucket = grayReleaseService.getBucketNumber(imei);

            assertTrue(bucket >= 0);
            assertTrue(bucket < 10000);
        }

        @Test
        @DisplayName("桶数量应该为 10000")
        void testBucketCount() {
            assertEquals(10000, grayReleaseService.getBucketCount());
        }

        @Test
        @DisplayName("空 IMEI 获取桶号应该抛出异常")
        void testGetBucketNumberWithNull() {
            assertThrows(IllegalArgumentException.class, () -> {
                grayReleaseService.getBucketNumber(null);
            });
            assertThrows(IllegalArgumentException.class, () -> {
                grayReleaseService.getBucketNumber("");
            });
        }
    }

    @Nested
    @DisplayName("分布均匀性测试")
    class DistributionTests {

        @Test
        @DisplayName("灰度比例 50% 时，命中率应该在 [49%, 51%] 区间")
        void testDistribution50Percent() {
            int sampleSize = 10000;
            int grayRate = 50;
            int hits = countHits(sampleSize, grayRate);

            double actualRate = (double) hits / sampleSize * 100;
            assertTrue(actualRate >= 49.0 && actualRate <= 51.0,
                    "实际命中率 " + actualRate + "% 不在预期区间 [49%, 51%]");
        }

        @Test
        @DisplayName("灰度比例 10% 时，命中率应该在 [9%, 11%] 区间")
        void testDistribution10Percent() {
            int sampleSize = 10000;
            int grayRate = 10;
            int hits = countHits(sampleSize, grayRate);

            double actualRate = (double) hits / sampleSize * 100;
            assertTrue(actualRate >= 9.0 && actualRate <= 11.0,
                    "实际命中率 " + actualRate + "% 不在预期区间 [9%, 11%]");
        }

        @Test
        @DisplayName("灰度比例 1% 时，命中率应该在 [0.5%, 1.5%] 区间")
        void testDistribution1Percent() {
            int sampleSize = 10000;
            int grayRate = 1;
            int hits = countHits(sampleSize, grayRate);

            double actualRate = (double) hits / sampleSize * 100;
            assertTrue(actualRate >= 0.5 && actualRate <= 1.5,
                    "实际命中率 " + actualRate + "% 不在预期区间 [0.5%, 1.5%]");
        }

        @Test
        @DisplayName("灰度比例 25% 时，命中率应该在 [24%, 26%] 区间")
        void testDistribution25Percent() {
            int sampleSize = 10000;
            int grayRate = 25;
            int hits = countHits(sampleSize, grayRate);

            double actualRate = (double) hits / sampleSize * 100;
            assertTrue(actualRate >= 24.0 && actualRate <= 26.0,
                    "实际命中率 " + actualRate + "% 不在预期区间 [24%, 26%]");
        }

        @Test
        @DisplayName("灰度比例 75% 时，命中率应该在 [74%, 76%] 区间")
        void testDistribution75Percent() {
            int sampleSize = 10000;
            int grayRate = 75;
            int hits = countHits(sampleSize, grayRate);

            double actualRate = (double) hits / sampleSize * 100;
            assertTrue(actualRate >= 74.0 && actualRate <= 76.0,
                    "实际命中率 " + actualRate + "% 不在预期区间 [74%, 76%]");
        }

        /**
         * 统计命中次数
         *
         * @param sampleSize 样本数量
         * @param grayRate   灰度比例
         * @return 命中次数
         */
        private int countHits(int sampleSize, int grayRate) {
            int hits = 0;
            for (int i = 0; i < sampleSize; i++) {
                String imei = String.format("354972069%010d", i); // 模拟真实 IMEI 格式
                if (grayReleaseService.hitsGrayBucket(imei, grayRate)) {
                    hits++;
                }
            }
            return hits;
        }
    }

    @Nested
    @DisplayName("真实场景测试")
    class RealWorldTests {

        @Test
        @DisplayName("真实 IMEI 格式测试")
        void testRealImeiFormat() {
            // 真实 IMEI 格式：15 位数字
            String[] realImeis = {
                    "354972069009027",
                    "354972069009028",
                    "354972069009029",
                    "359272069009027",
                    "359272069009028"
            };

            for (String imei : realImeis) {
                int bucket = grayReleaseService.getBucketNumber(imei);
                assertTrue(bucket >= 0 && bucket < 10000,
                        "IMEI " + imei + " 的桶号 " + bucket + " 不在有效范围内");
            }
        }

        @Test
        @DisplayName("真实 IMEI 样本分布测试")
        void testRealImeiDistribution() {
            // 使用真实 IMEI 前缀生成样本
            int sampleSize = 10000;
            int grayRate = 30;
            int hits = 0;

            for (int i = 0; i < sampleSize; i++) {
                // 模拟华为、小米等厂商的 IMEI 前缀
                String prefix = i % 2 == 0 ? "354972069" : "359272069";
                String imei = prefix + String.format("%06d", i % 1000000);
                if (grayReleaseService.hitsGrayBucket(imei, grayRate)) {
                    hits++;
                }
            }

            double actualRate = (double) hits / sampleSize * 100;
            assertTrue(actualRate >= 29.0 && actualRate <= 31.0,
                    "实际命中率 " + actualRate + "% 不在预期区间 [29%, 31%]");
        }
    }
}
