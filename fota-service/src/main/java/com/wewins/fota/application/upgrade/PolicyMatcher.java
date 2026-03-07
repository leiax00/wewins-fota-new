package com.wewins.fota.application.upgrade;

import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.TimeWindowType;
import com.wewins.fota.domain.policy.model.vo.PolicyTimeWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;

/**
 * 策略匹配器
 * <p>
 * 提供升级策略的各种匹配判断逻辑
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Slf4j
@Component
public class PolicyMatcher {

    /**
     * 目标模式常量
     */
    private static final String TARGET_MODE_ALL = "ALL";
    private static final String TARGET_MODE_DEVICE_IDS = "DEVICE_IDS";
    private static final String TARGET_MODE_DEVICE_BATCHES = "DEVICE_BATCHES";
    private static final String TARGET_MODE_DEVICE_TAGS = "DEVICE_TAGS";

    /**
     * 目标模式匹配 - 根据策略的 targetMode 选择对应的设备筛选逻辑
     * <p>
     * 支持四种目标模式：
     * </p>
     * <ul>
     *   <li>ALL：全量设备 - 直接通过</li>
     *   <li>DEVICE_IDS：按 IMEI 列表筛选</li>
     *   <li>DEVICE_BATCHES：按批次筛选</li>
     *   <li>DEVICE_TAGS：按标签筛选</li>
     * </ul>
     *
     * @param policy        策略
     * @param deviceImei    设备 IMEI
     * @param deviceBatchId 设备导入批次 ID
     * @param deviceTags    设备标签
     * @return true 如果设备匹配策略的目标模式
     */
    public boolean matchesTargetMode(
            UpgradePolicy policy,
            String deviceImei,
            Long deviceBatchId,
            Map<String, String> deviceTags
    ) {
        String targetMode = policy.getTargetMode();
        Set<String> targetImeis = policy.getTargetImeis();
        Set<Long> targetDeviceBatchIds = policy.getTargetDeviceBatchIds();
        Map<String, Object> targetDeviceTags = policy.getTargetDeviceTags();

        // targetMode 为空时默认为 ALL
        String mode = (targetMode == null || targetMode.isBlank()) ? TARGET_MODE_ALL : targetMode.toUpperCase();

        return switch (mode) {
            case TARGET_MODE_ALL -> true;
            case TARGET_MODE_DEVICE_IDS -> matchesDeviceIds(targetImeis, deviceImei);
            case TARGET_MODE_DEVICE_BATCHES -> matchesDeviceBatches(targetDeviceBatchIds, deviceBatchId);
            case TARGET_MODE_DEVICE_TAGS -> matchesDeviceTags(targetDeviceTags, deviceTags);
            default -> {
                log.warn("未知的目标模式: {}, 默认通过", mode);
                yield true;
            }
        };
    }

    /**
     * IMEI 列表匹配
     *
     * @param targetImeis 策略指定的 IMEI 列表
     * @param deviceImei  设备 IMEI
     * @return true 如果设备 IMEI 在列表中
     */
    private boolean matchesDeviceIds(Set<String> targetImeis, String deviceImei) {
        if (targetImeis == null || targetImeis.isEmpty()) {
            log.debug("策略未指定目标 IMEI 列表，不匹配");
            return false;
        }

        if (deviceImei == null || deviceImei.isBlank()) {
            log.debug("设备 IMEI 为空，不匹配");
            return false;
        }

        if (targetImeis.contains(deviceImei)) {
            return true;
        }

        log.debug("设备 IMEI 不在目标列表中: deviceImei={}", deviceImei);
        return false;
    }

    /**
     * 批次列表匹配
     *
     * @param targetBatchIds 策略指定的批次 ID 列表
     * @param deviceBatchId  设备导入批次 ID
     * @return true 如果设备批次在列表中
     */
    private boolean matchesDeviceBatches(Set<Long> targetBatchIds, Long deviceBatchId) {
        if (targetBatchIds == null || targetBatchIds.isEmpty()) {
            log.debug("策略未指定目标批次列表，不匹配");
            return false;
        }

        if (deviceBatchId == null) {
            log.debug("设备批次 ID 为空，不匹配");
            return false;
        }

        if (targetBatchIds.contains(deviceBatchId)) {
            return true;
        }

        log.debug("设备批次不在目标列表中: deviceBatchId={}", deviceBatchId);
        return false;
    }

    /**
     * 标签匹配 - 使用 JSONB 查询
     * <p>
     * 检查设备标签是否满足策略的 targetDeviceTags 要求
     * </p>
     * <p>
     * 业务规则（AND 逻辑）：
     * </p>
     * <ul>
     *   <li>策略没有标签要求 → 匹配</li>
     *   <li>设备没有标签且策略有要求 → 不匹配</li>
     *   <li>设备标签包含策略要求的所有键值对 → 匹配</li>
     *   <li>任一要求标签不匹配 → 不匹配</li>
     * </ul>
     * <p>
     * 匹配示例：
     * </p>
     * <pre>
     * 策略要求: {"env": "test", "region": "CN"}
     * 设备标签: {"env": "test", "region": "CN", "network": "5G"}  → 匹配 ✓
     * 设备标签: {"env": "test", "region": "US"}               → 不匹配 ✗
     * 设备标签: {"env": "test"}                               → 不匹配 ✗
     * 设备标签: {"env": "prod", "region": "CN"}               → 不匹配 ✗
     * </pre>
     *
     * @param policyTargetTags 策略要求的标签（targetDeviceTags 字段）
     * @param deviceTags       设备的实际标签（tags 字段）
     * @return true 如果设备标签满足策略要求
     */
    public boolean matchesDeviceTags(Map<String, Object> policyTargetTags, Map<String, String> deviceTags) {
        // 1. 策略没有标签要求 → 匹配
        if (policyTargetTags == null || policyTargetTags.isEmpty()) {
            return true;
        }

        // 2. 设备没有标签且策略有要求 → 不匹配
        if (deviceTags == null || deviceTags.isEmpty()) {
            return false;
        }

        // 3. 遍历策略要求的每个标签，检查设备标签是否匹配
        for (Map.Entry<String, Object> entry : policyTargetTags.entrySet()) {
            String requiredKey = entry.getKey();
            Object requiredValue = entry.getValue();

            // 检查设备标签是否有这个键
            if (!deviceTags.containsKey(requiredKey)) {
                log.debug("设备标签缺少必需字段: requiredKey={}", requiredKey);
                return false;
            }

            String deviceValue = deviceTags.get(requiredKey);

            // 检查值是否匹配
            if (!valuesEqual(requiredValue, deviceValue)) {
                log.debug("设备标签值不匹配: key={}, required={}, actual={}",
                        requiredKey, requiredValue, deviceValue);
                return false;
            }
        }

        return true;
    }

    /**
     * 比较策略标签值与设备标签值是否相等
     * <p>
     * 支持类型：
     * </p>
     * <ul>
     *   <li>字符串：直接比较</li>
     *   <li>数字：比较数值</li>
     *   <li>布尔：比较布尔值</li>
     *   <li>null：视为相等</li>
     * </ul>
     *
     * @param value1 第一个值
     * @param value2 第二个值
     * @return true 如果值相等
     */
    private boolean valuesEqual(Object requiredValue, String actualValue) {
        if (requiredValue == null) {
            return actualValue == null;
        }
        if (actualValue == null) {
            return false;
        }

        if (requiredValue instanceof String value) {
            return value.equals(actualValue);
        }
        if (requiredValue instanceof Number value) {
            try {
                return Double.compare(value.doubleValue(), Double.parseDouble(actualValue)) == 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (requiredValue instanceof Boolean value) {
            return value == Boolean.parseBoolean(actualValue);
        }
        return requiredValue.toString().equals(actualValue);
    }

    /**
     * 时间窗口匹配检查
     * <p>
     * 检查当前时间是否在策略的有效时间窗口内。
     * </p>
     * <p>
     * 支持两种时间窗口类型：
     * </p>
     * <ul>
     *   <li><b>RANGE</b>: 固定范围（可跨越多天）</li>
     *   <li><b>DAILY</b>: 每日周期（约定不超过 24 小时）</li>
     * </ul>
     * <p>
     * 时间区间语义：左闭右开 [startAt, endAt)
     * </p>
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     *   <li>策略没有时间窗口 → 匹配</li>
     *   <li>时间窗口格式错误 → 匹配（容错处理）</li>
     *   <li>当前时间在窗口内 → 匹配</li>
     *   <li>当前时间在窗口外 → 不匹配</li>
     * </ul>
     *
     * <h3>RANGE 类型示例</h3>
     * <pre>
     * {
     *   "type": "RANGE",
     *   "startAt": "2026-02-01T00:00:00Z",
     *   "endAt": "2026-02-10T23:59:59Z"
     * }
     * </pre>
     *
     * <h3>DAILY 类型示例</h3>
     * <pre>
     * {
     *   "type": "DAILY",
     *   "startAt": "2026-02-01T02:00:00Z",
     *   "endAt": "2026-02-01T06:00:00Z"
     * }
     * </pre>
     * <p>
     * 注意：DAILY 类型只取时间部分（HH:mm:ss），在每日重复应用。
     * </p>
     *
     * @param timeWindow 时间窗口配置（JSONB）
     * @return true 如果当前时间在时间窗口内
     */
    public boolean matchesTimeWindow(PolicyTimeWindow timeWindow) {
        // 1. 没有时间窗口限制 → 匹配
        if (timeWindow == null || timeWindow.getType() == null) {
            return true;
        }

        try {
            TimeWindowType type = timeWindow.getType();
            if (type == TimeWindowType.UNLIMITED) {
                return true;
            }
            LocalDateTime startAt = timeWindow.getStartAt();
            LocalDateTime endAt = timeWindow.getEndAt();
            if (startAt == null || endAt == null) {
                log.debug("时间窗口缺少时间字段，默认匹配");
                return true;
            }

            // 4. 根据类型进行匹配
            return switch (type) {
                case RANGE -> matchesRangeWindow(startAt, endAt);
                case DAILY -> matchesDailyWindow(startAt, endAt);
                case UNLIMITED -> true;
                default -> {
                    log.warn("未知的时间窗口类型: {}, 默认匹配", type);
                    yield true;
                }
            };

        } catch (Exception e) {
            // 容错处理：解析失败时默认匹配，避免阻断升级流程
            log.error("时间窗口检查异常，默认匹配", e);
            return true;
        }
    }

    /**
     * 检查固定范围时间窗口
     * <p>
     * RANGE 类型：直接比较当前时间是否在 [startAt, endAt) 范围内
     * </p>
     * <p>
     * 支持的时间格式：
     * <ul>
     *   <li>ISO8601 UTC 格式：2026-02-26T16:00:00Z</li>
     *   <li>本地时间格式（视为 UTC）：2026-02-26T16:00:00</li>
     * </ul>
     * </p>
     *
     * @param startAt 开始时间
     * @param endAt   结束时间
     * @return true 如果当前时间在范围内
     */
    private boolean matchesRangeWindow(LocalDateTime startAt, LocalDateTime endAt) {
        Instant now = Instant.now();
        Instant start = toUtcInstant(startAt);
        Instant end = toUtcInstant(endAt);
        boolean inWindow = !now.isBefore(start) && now.isBefore(end);
        if (!inWindow) {
            log.debug("不在 RANGE 时间窗口内: now={}, start={}, end={}", now, start, end);
        }
        return inWindow;
    }

    /**
     * 解析时间字符串为 Instant
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>ISO8601 UTC 格式（带 Z 后缀）：2026-02-26T16:00:00Z</li>
     *   <li>本地时间格式（不带时区，视为 UTC）：2026-02-26T16:00:00</li>
     * </ul>
     * </p>
     *
     * @param dateTimeStr 时间字符串
     * @return Instant 对象
     */
    private Instant toUtcInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.of("UTC")).toInstant();
    }

    /**
     * 解析时间字符串为 ZonedDateTime（UTC 时区）
     * <p>
     * 支持两种格式：
     * <ul>
     *   <li>ISO8601 UTC 格式（带 Z 后缀）：2026-02-26T16:00:00Z</li>
     *   <li>本地时间格式（不带时区，视为 UTC）：2026-02-26T16:00:00</li>
     * </ul>
     * </p>
     *
     * @param dateTimeStr 时间字符串
     * @return ZonedDateTime 对象（UTC 时区）
     */
    /**
     * 检查每日周期时间窗口
     * <p>
     * DAILY 类型：将起止时间的 HH:mm:ss 部分应用到今天的日期，
     * 检查当前时间是否在每日的时间窗口内。
     * </p>
     * <p>
     * 示例：startAt=02:00:00, endAt=06:00:00
     * </p>
     * <p>
     * 表示每天 02:00:00 到 06:00:00 之间允许升级。
     * </p>
     * <p>
     * 支持的时间格式：
     * <ul>
     *   <li>ISO8601 UTC 格式：2026-02-26T16:00:00Z</li>
     *   <li>本地时间格式（视为 UTC）：2026-02-26T16:00:00</li>
     * </ul>
     * </p>
     *
     * @param startAt 开始时间（只取时间部分）
     * @param endAt   结束时间（只取时间部分）
     * @return true 如果当前时间在每日窗口内
     */
    private boolean matchesDailyWindow(LocalDateTime startAt, LocalDateTime endAt) {
        LocalTime startTime = startAt.toLocalTime();
        LocalTime endTime = endAt.toLocalTime();
        LocalTime nowTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).toLocalTime();

        boolean inWindow;
        if (endTime.isAfter(startTime)) {
            inWindow = !nowTime.isBefore(startTime) && nowTime.isBefore(endTime);
        } else {
            inWindow = !nowTime.isBefore(startTime) || nowTime.isBefore(endTime);
        }
        if (!inWindow) {
            log.debug("不在 DAILY 时间窗口内: now={}, start={}, end={}", nowTime, startTime, endTime);
        }
        return inWindow;
    }

    /**
     * dev 参数匹配：dev=1 临时标注为测试设备
     * <p>
     * <strong>重要说明</strong>：
     * </p>
     * <ul>
     *   <li>dev=1 表示本次请求<strong>临时</strong>将设备标注为测试设备</li>
     *   <li>类似于在数据库中临时设置 tags.env='test' 或 tags.env='dev'</li>
     *   <li><strong>不修改</strong>设备表，仅用于本次策略匹配</li>
     *   <li>用于测试场景：生产设备临时接收测试固件</li>
     * </ul>
     * <p>
     * <strong>匹配规则</strong>：
     * </p>
     * <ul>
     *   <li>策略没有环境限制（targetEnvironment=null）→ 匹配</li>
     *   <li>策略环境为 test/dev → 仅匹配 dev=1 的请求</li>
     *   <li>策略环境为 prod/production → 仅匹配 dev=0 或未传 dev 的请求</li>
     *   <li>其他环境 → 匹配所有请求</li>
     * </ul>
     * <p>
     * <strong>示例</strong>：
     * </p>
     * <pre>
     * 策略 targetEnvironment="test", dev=1  → 匹配 ✓
     * 策略 targetEnvironment="test", dev=0  → 不匹配 ✗
     * 策略 targetEnvironment="prod", dev=1  → 不匹配 ✗
     * 策略 targetEnvironment="prod", dev=0  → 匹配 ✓
     * 策略 targetEnvironment=null,  dev=1  → 匹配 ✓
     * 策略 targetEnvironment=null,  dev=0  → 匹配 ✓
     * </pre>
     *
     * @param targetEnvironment 策略的目标环境（targetEnvironment 字段）
     * @param dev               dev 参数值（1=测试设备，0 或 null=正常设备）
     * @return true 如果策略环境匹配 dev 参数设置
     */
    public boolean matchesDevMode(String targetEnvironment, Integer dev) {
        // 1. 策略没有环境限制 → 匹配所有请求
        if (targetEnvironment == null || targetEnvironment.isBlank()) {
            return true;
        }

        // 2. 判断是否为临时测试设备
        boolean isTemporaryTestDevice = (dev != null && dev == 1);
        String policyEnv = targetEnvironment.toLowerCase().trim();

        // 3. 测试环境策略：只匹配 dev=1 的请求
        if ("test".equals(policyEnv) || "dev".equals(policyEnv)) {
            boolean matches = isTemporaryTestDevice;
            if (!matches) {
                log.debug("测试环境策略不匹配: policyEnv={}, dev={}", policyEnv, dev);
            }
            return matches;
        }

        // 4. 生产环境策略：只匹配非测试设备（dev=0 或未传）
        if ("prod".equals(policyEnv) || "production".equals(policyEnv)) {
            boolean matches = !isTemporaryTestDevice;
            if (!matches) {
                log.debug("生产环境策略不匹配: policyEnv={}, dev={}", policyEnv, dev);
            }
            return matches;
        }

        // 5. 其他环境策略：匹配所有请求
        return true;
    }

}
