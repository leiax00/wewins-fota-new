package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

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
            JsonNode deviceTags
    ) {
        String targetMode = policy.getTargetMode();
        JsonNode targetImeis = policy.getTargetImeis();
        JsonNode targetDeviceBatchIds = policy.getTargetDeviceBatchIds();
        JsonNode targetDeviceTags = policy.getTargetDeviceTags();

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
    private boolean matchesDeviceIds(JsonNode targetImeis, String deviceImei) {
        if (targetImeis == null || !targetImeis.isArray() || targetImeis.isEmpty()) {
            log.debug("策略未指定目标 IMEI 列表，不匹配");
            return false;
        }

        if (deviceImei == null || deviceImei.isBlank()) {
            log.debug("设备 IMEI 为空，不匹配");
            return false;
        }

        for (JsonNode node : targetImeis) {
            if (deviceImei.equals(node.asText())) {
                return true;
            }
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
    private boolean matchesDeviceBatches(JsonNode targetBatchIds, Long deviceBatchId) {
        if (targetBatchIds == null || !targetBatchIds.isArray() || targetBatchIds.isEmpty()) {
            log.debug("策略未指定目标批次列表，不匹配");
            return false;
        }

        if (deviceBatchId == null) {
            log.debug("设备批次 ID 为空，不匹配");
            return false;
        }

        for (JsonNode node : targetBatchIds) {
            if (deviceBatchId.equals(node.asLong())) {
                return true;
            }
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
    public boolean matchesDeviceTags(JsonNode policyTargetTags, JsonNode deviceTags) {
        // 1. 策略没有标签要求 → 匹配
        if (policyTargetTags == null || policyTargetTags.isEmpty() || policyTargetTags instanceof NullNode) {
            return true;
        }

        // 2. 设备没有标签且策略有要求 → 不匹配
        if (deviceTags == null || deviceTags.isEmpty() || deviceTags instanceof NullNode) {
            return false;
        }

        // 3. 遍历策略要求的每个标签，检查设备标签是否匹配
        for (String requiredKey : getFieldNames(policyTargetTags)) {
            JsonNode requiredValue = policyTargetTags.get(requiredKey);

            // 检查设备标签是否有这个键
            if (!deviceTags.has(requiredKey)) {
                log.debug("设备标签缺少必需字段: requiredKey={}", requiredKey);
                return false;
            }

            JsonNode deviceValue = deviceTags.get(requiredKey);

            // 检查值是否匹配
            if (!valuesEqual(requiredValue, deviceValue)) {
                log.debug("设备标签值不匹配: key={}, required={}, actual={}",
                        requiredKey, requiredValue.asText(), deviceValue.asText());
                return false;
            }
        }

        return true;
    }

    /**
     * 获取 JsonNode 的所有字段名
     *
     * @param node JSON 节点
     * @return 字段名集合
     */
    private java.util.Set<String> getFieldNames(JsonNode node) {
        java.util.Set<String> fieldNames = new java.util.HashSet<>();
        if (node != null && node.isObject()) {
            node.fieldNames().forEachRemaining(fieldNames::add);
        }
        return fieldNames;
    }

    /**
     * 比较两个 JsonNode 的值是否相等
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
    private boolean valuesEqual(JsonNode value1, JsonNode value2) {
        // 处理 null 值
        if (value1 == null || value1.isNull()) {
            return value2 == null || value2.isNull();
        }
        if (value2 == null || value2.isNull()) {
            return false;
        }

        // 字符串比较（最常见场景）
        if (value1.isTextual() && value2.isTextual()) {
            return value1.asText().equals(value2.asText());
        }

        // 数值比较
        if (value1.isNumber() && value2.isNumber()) {
            return value1.asDouble() == value2.asDouble();
        }

        // 布尔比较
        if (value1.isBoolean() && value2.isBoolean()) {
            return value1.asBoolean() == value2.asBoolean();
        }

        // 其他类型使用 JsonNode 的 equals 方法
        return value1.equals(value2);
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
    public boolean matchesTimeWindow(JsonNode timeWindow) {
        // 1. 没有时间窗口限制 → 匹配
        if (timeWindow == null || timeWindow.isEmpty() || timeWindow instanceof NullNode) {
            return true;
        }

        try {
            // 2. 获取时间窗口类型
            String type = nullSafeText(timeWindow.path("type"));
            if (type == null) {
                log.debug("时间窗口缺少类型字段，默认匹配");
                return true;
            }

            // 3. 获取起止时间
            String startAt = nullSafeText(timeWindow.path("startAt"));
            String endAt = nullSafeText(timeWindow.path("endAt"));

            if (startAt == null || endAt == null) {
                log.debug("时间窗口缺少时间字段，默认匹配");
                return true;
            }

            // 4. 根据类型进行匹配
            return switch (type.toUpperCase()) {
                case "RANGE" -> matchesRangeWindow(startAt, endAt);
                case "DAILY" -> matchesDailyWindow(startAt, endAt);
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
     *
     * @param startAt 开始时间（ISO8601 UTC 格式）
     * @param endAt   结束时间（ISO8601 UTC 格式）
     * @return true 如果当前时间在范围内
     */
    private boolean matchesRangeWindow(String startAt, String endAt) {
        try {
            Instant now = Instant.now();
            Instant start = Instant.parse(startAt);
            Instant end = Instant.parse(endAt);

            // 检查是否在时间窗口内：[start, end)
            boolean inWindow = !now.isBefore(start) && now.isBefore(end);

            if (!inWindow) {
                log.debug("不在 RANGE 时间窗口内: now={}, start={}, end={}",
                        now, start, end);
            }

            return inWindow;

        } catch (DateTimeParseException e) {
            log.warn("RANGE 时间窗口解析失败: startAt={}, endAt={}", startAt, endAt, e);
            return true; // 容错处理
        }
    }

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
     *
     * @param startAt 开始时间（ISO8601 格式，只取时间部分）
     * @param endAt   结束时间（ISO8601 格式，只取时间部分）
     * @return true 如果当前时间在每日窗口内
     */
    private boolean matchesDailyWindow(String startAt, String endAt) {
        try {
            // 解析时间窗口（获取 HH:mm:ss 部分）
            ZonedDateTime startZoned = ZonedDateTime.parse(startAt)
                    .withZoneSameInstant(ZoneId.of("UTC"));
            ZonedDateTime endZoned = ZonedDateTime.parse(endAt)
                    .withZoneSameInstant(ZoneId.of("UTC"));

            // 获取当前 UTC 日期
            ZonedDateTime nowUtc = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));

            // 将时间窗口应用到今天的 UTC 日期
            ZonedDateTime todayStart = nowUtc
                    .withHour(startZoned.getHour())
                    .withMinute(startZoned.getMinute())
                    .withSecond(startZoned.getSecond())
                    .withNano(0);

            ZonedDateTime todayEnd = nowUtc
                    .withHour(endZoned.getHour())
                    .withMinute(endZoned.getMinute())
                    .withSecond(endZoned.getSecond())
                    .withNano(0);

            // 处理跨天情况（如 23:00:00 到 02:00:00）
            if (todayEnd.isBefore(todayStart)) {
                // 结束时间在第二天
                boolean inWindow = !nowUtc.isBefore(todayStart) || nowUtc.isBefore(todayEnd.plusDays(1));

                if (!inWindow) {
                    log.debug("不在 DAILY 时间窗口内（跨天）: now={}, start={}, end={}",
                            nowUtc, todayStart, todayEnd.plusDays(1));
                }

                return inWindow;
            } else {
                // 正常情况：起止时间在同一天
                boolean inWindow = !nowUtc.isBefore(todayStart) && nowUtc.isBefore(todayEnd);

                if (!inWindow) {
                    log.debug("不在 DAILY 时间窗口内: now={}, start={}, end={}",
                            nowUtc, todayStart, todayEnd);
                }

                return inWindow;
            }

        } catch (DateTimeParseException e) {
            log.warn("DAILY 时间窗口解析失败: startAt={}, endAt={}", startAt, endAt, e);
            return true; // 容错处理
        }
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

    /**
     * 安全的空文本处理
     *
     * @param text 文本
     * @return 去除空白后的文本，null 或空白时返回 null
     */
    private String nullSafeText(JsonNode text) {
        if (text == null || text.isNull()) {
            return null;
        }
        String result = text.asText();
        return ("null".equals(result) || result.isBlank()) ? null : result;
    }
}
