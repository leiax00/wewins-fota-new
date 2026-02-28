#!/bin/bash

# 独立测试灰度发布算法
# 使用 Java 直接运行，不依赖 Maven

# 创建测试源码目录
mkdir -p /tmp/gray-test/src

# 创建测试类
cat > /tmp/gray-test/src/GrayReleaseDistributionTest.java << 'EOF'
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

/**
 * 灰度发布分布测试工具
 */
public class GrayReleaseDistributionTest {

    private static final HashFunction HASH_FUNC = Hashing.murmur3_32();
    private static final int BUCKET_COUNT = 10000;

    public static boolean hitsGrayBucket(String imei, int grayRate) {
        if (grayRate <= 0) return false;
        if (grayRate >= 100) return true;
        if (imei == null || imei.isEmpty()) return false;

        int hash = HASH_FUNC.hashString(imei, StandardCharsets.UTF_8).asInt();
        int bucket = (hash & Integer.MAX_VALUE) % BUCKET_COUNT;
        int threshold = (int) (BUCKET_COUNT * grayRate / 100.0);

        return bucket < threshold;
    }

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

    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("灰度发布算法分布测试");
        System.out.println("============================================================");

        int sampleSize = 10000;
        boolean allPassed = true;

        allPassed &= testDistribution(sampleSize, 1, "1%");
        allPassed &= testDistribution(sampleSize, 5, "5%");
        allPassed &= testDistribution(sampleSize, 10, "10%");
        allPassed &= testDistribution(sampleSize, 25, "25%");
        allPassed &= testDistribution(sampleSize, 50, "50%");
        allPassed &= testDistribution(sampleSize, 75, "75%");
        allPassed &= testDistribution(sampleSize, 90, "90%");
        allPassed &= testDistribution(sampleSize, 99, "99%");

        System.out.println("============================================================");
        if (allPassed) {
            System.out.println("所有测试通过！");
        } else {
            System.out.println("部分测试失败！");
            System.exit(1);
        }
        System.out.println("============================================================");

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
        if (result1 != result2 || result2 != result3) {
            System.out.println("一致性测试失败！");
            System.exit(1);
        }

        System.out.println("\n测试完成！");
    }

    private static boolean testDistribution(int sampleSize, int grayRate, String label) {
        int hits = countHits(sampleSize, grayRate);
        double actualRate = (double) hits / sampleSize * 100;

        double lowerBound = grayRate - 1.0;
        double upperBound = grayRate + 1.0;

        if (grayRate == 1) {
            lowerBound = 0.5;
            upperBound = 1.5;
        }

        boolean passed = actualRate >= lowerBound && actualRate <= upperBound;

        System.out.printf("%s 灰度: 预期 %.1f%%, 实际 %.2f%% (%d/%d) %s%n",
                label, (double) grayRate, actualRate, hits, sampleSize,
                passed ? "✓" : "✗");

        return passed;
    }
}
EOF

# 获取 Guava jar
cd /tmp/gray-test
mvn dependency:get -Dartifact=com.google.guava:guava:33.3.1-jre -q 2>/dev/null || true

# 尝试从本地 Maven 仓库找 Guava
GUAVA_JAR=""
for path in \
    ~/.m2/repository/com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar \
    ~/.m2/repository/com/google/guava/guava/*/guava-*.jar; do
    if [ -f "$path" ]; then
        GUAVA_JAR="$path"
        echo "找到 Guava: $GUAVA_JAR"
        break
    fi
done

if [ -z "$GUAVA_JAR" ]; then
    echo "正在下载 Guava..."
    # 使用 curl 下载 Guava
    mkdir -p /tmp/gray-test/lib
    curl -sL -o /tmp/gray-test/lib/guava.jar https://repo1.maven.org/maven2/com/google/guava/guava/33.3.1-jre/guava-33.3.1-jre.jar
    GUAVA_JAR="/tmp/gray-test/lib/guava.jar"
fi

# 编译并运行
echo "编译测试类..."
javac -cp "$GUAVA_JAR" src/GrayReleaseDistributionTest.java

echo "运行测试..."
java -cp "$GUAVA_JAR:src" GrayReleaseDistributionTest

echo ""
echo "测试完成！"
