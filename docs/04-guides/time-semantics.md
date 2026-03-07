# 时间语义规范（Time Semantics Specification）

**版本**：v1.0
**日期**：2026-02-04
**维护者**：FOTA 架构组

---

## 核心原则

### 1. 数据面 UTC-only

**设备 API（`/v1/upgrade/check`、`/v1/upgrade/report`）完全使用 UTC 时间，不涉及时区转换。**

- ✅ 设备**不需要上报时间**（或设备时间仅作诊断字段）
- ✅ 所有事件时间使用**服务端接收时刻**（UTC）
- ✅ 设备与时间/时区**完全剥离**

**理由**：
- 简化设备端实现
- 服务端时间是可信的统一时间源
- 防止设备伪造时间
- 避免时区混乱

### 2. 控制面时区感知

**管理后台需要时区感知，以提供良好的运营体验。**

- ✅ 策略时间窗口：运营人员输入本地时区时间 → 系统转换为 UTC 存储
- ✅ 统计查询：按 `timezone` 参数展示（UTC 转换为本地时区）
- ✅ 时间展示：按用户偏好时区格式化显示

---

## 时间存储规范

### 数据库字段规范

#### PostgreSQL（主存储）

```sql
-- 策略表：时区相关字段
CREATE TABLE policies (
    id BIGSERIAL PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,

    -- 时间窗口（UTC）
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,

    -- 时区配置
    policy_timezone VARCHAR(50) NOT NULL DEFAULT 'UTC',  -- IANA ZoneId，如 'Asia/Shanghai'
    time_mode VARCHAR(20) NOT NULL DEFAULT 'UTC_FIXED',  -- 'UTC_FIXED' | 'DEVICE_LOCAL'(预留)

    -- 其他字段...
);

-- 设备表：时间字段
CREATE TABLE devices (
    id BIGSERIAL PRIMARY KEY,
    imei BIGINT NOT NULL UNIQUE,
    last_seen TIMESTAMPTZ,  -- 最后活跃时间（UTC）
    -- 其他字段...
);
```

#### ClickHouse（事件存储）

```sql
CREATE TABLE upgrade_events (
    -- 主时间：服务端接收时刻（UTC）
    event_time_utc DateTime64(3, 'UTC') NOT NULL,

    -- 诊断字段（可选）
    device_reported_at Nullable(DateTime64(3, 'UTC')),  -- 设备上报时间
    network_delay_ms Nullable(UInt32),                   -- 网络延迟
    ingest_delay_ms Nullable(UInt32),                    -- 补报延迟
    is_offline_batch UInt8 DEFAULT 0,                    -- 是否离线补报

    -- 业务字段
    imei UInt64 NOT NULL,
    event_type LowCardinality(String) NOT NULL,  -- DL_START, DL_OK, DL_FAIL, UP_OK
    product_id LowCardinality(String),
    policy_id UInt32,
    region LowCardinality(String),
    details String,

    -- 索引
    INDEX idx_event_time event_time_utc TYPE minmax GRANULARITY 1
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time_utc)
ORDER BY (event_time_utc, imei, event_type);
```

---

## API 接口规范

### 设备 API：不涉及时区

#### `/v1/upgrade/check`（检查更新）

**请求**：
```http
GET /v1/upgrade/check?product=test&imei=123456789012345&version=v1.0.0
```

**响应**：
```json
{
  "new_firmware": "v2.0.0",
  "download_url": "https://cdn.xxx.com/pkg.bin?p_id=101&sig=...",
  "control": {
    "check_interval": 86400,    // 秒
    "download_delay": 300       // 秒
  }
}
```

**说明**：
- ✅ 不涉及时区
- ✅ 使用相对时间（秒数），避免时区问题

#### `/v1/upgrade/report`（上报事件）

**请求**：
```json
{
  "imei": 861234567890123,
  "url": "http://foid-dl.xxx.com/xxx.bin?p_id=101&sig=...",
  "event": "DL_OK",
  "details": {
    // 设备时间（可选，仅用于诊断）
    "device_time": "2024-01-01T10:30:00+08:00",
    "firmware_size": 20000000,
    "download_duration_ms": 5000
  }
}
```

**服务端处理**：
```java
@PostMapping("/v1/upgrade/report")
public ResponseEntity<?> report(@RequestBody ReportRequest request) {
    // 主时间：服务端 UTC
    Instant eventTimeUtc = clock.instant();

    // 诊断时间：设备上报（可为 null）
    Instant deviceReportedAt = request.getDetails().getDeviceTime();

    // 网络延迟计算
    long networkDelayMs = deviceReportedAt != null
        ? Duration.between(deviceReportedAt, eventTimeUtc).toMillis()
        : 0;

    // 构造事件
    UpgradeEvent event = UpgradeEvent.builder()
        .imei(request.getImei())
        .eventType(request.getEvent())
        .eventTimeUtc(eventTimeUtc)  // 主时间
        .deviceReportedAt(deviceReportedAt)  // 诊断时间
        .networkDelayMs(networkDelayMs)
        .build();

    // 发送到 MQ
    rabbitTemplate.convertAndSend("q.upgrade_events", event);

    return ResponseEntity.ok().build();
}
```

### 管理 API：时区感知

#### 策略创建/更新

**请求**：
```json
{
  "product_id": "test_product",
  "policy_timezone": "Asia/Shanghai",  // IANA ZoneId
  "start_time": "2024-01-01T00:00:00",  // 本地时间
  "end_time": "2024-01-31T23:59:59",    // 本地时间
  "gray_rate": 50,
  "max_limit": 10000
}
```

**服务端处理**：
```java
@PostMapping("/api/v1/admin/policies")
public ResponseEntity<Policy> createPolicy(@RequestBody PolicyCreateRequest request) {
    // 获取策略时区
    ZoneId policyZoneId = ZoneId.of(request.getPolicyTimeZone());  // 如 "Asia/Shanghai"

    // 转换为 UTC 存储
    ZonedDateTime startZoned = ZonedDateTime.parse(request.getStartTime())
        .withZoneSameInstant(policyZoneId);
    ZonedDateTime endZoned = ZonedDateTime.parse(request.getEndTime())
        .withZoneSameInstant(policyZoneId);

    Instant startUtc = startZoned.toInstant();
    Instant endUtc = endZoned.toInstant();

    // 存储到数据库
    Policy policy = Policy.builder()
        .productId(request.getProductId())
        .policyTimeZone(request.getPolicyTimeZone())
        .startTimeUtc(startUtc)
        .endTimeUtc(endUtc)
        .grayRate(request.getGrayRate())
        .maxLimit(request.getMaxLimit())
        .build();

    policyRepository.save(policy);
    return ResponseEntity.ok(policy);
}
```

#### 统计查询（时区参数）

**请求**：
```http
GET /api/v1/admin/stats/active-trend?product=test&timezone=Asia/Shanghai&date=2024-01-01
```

**服务端处理**：
```java
@GetMapping("/api/v1/admin/stats/active-trend")
public ResponseEntity<ActiveTrendStats> getActiveTrend(
    @RequestParam String product,
    @RequestParam String timezone,  // 如 "Asia/Shanghai"
    @RequestParam LocalDate date    // 2024-01-01
) {
    ZoneId zoneId = ZoneId.of(timezone);

    // 转换为 UTC 范围
    ZonedDateTime startOfDay = date.atStartOfDay(zoneId);
    ZonedDateTime endOfDay = date.plusDays(1).atStartOfDay(zoneId);

    Instant startUtc = startOfDay.toInstant();
    Instant endUtc = endOfDay.toInstant();

    // 查询 ClickHouse（UTC 时间范围）
    List<DeviceActiveRecord> records = clickHouseRepository
        .findByProductAndTimeRange(product, startUtc, endUtc);

    return ResponseEntity.ok(new ActiveTrendStats(records));
}
```

---

## 代码规范

### 时间操作规范

#### ✅ 推荐做法

```java
// 1. 获取当前时间（统一注入 Clock）
@Inject  // 或 @Autowired
private Clock clock;  // 生产环境：Clock.systemUTC()，测试环境：Clock.fixed(...)

// 使用
Instant now = clock.instant();

// 2. 时间存储（一律 UTC）
entity.setEventTimeUtc(Instant.now(clock));

// 3. 时间展示（按用户时区转换）
ZoneId userZoneId = ZoneId.of("Asia/Shanghai");
ZonedDateTime userTime = eventTimeUtc.atZone(userZoneId);

// 4. 策略窗口判断（UTC 比较）
Instant now = clock.instant();
Instant startUtc = policy.getStartTimeUtc();
Instant endUtc = policy.getEndTimeUtc();
boolean inWindow = !now.isBefore(startUtc) && !now.isAfter(endUtc);
```

#### ❌ 禁止做法

```java
// 1. 禁止直接使用 LocalDateTime.now()
LocalDateTime now = LocalDateTime.now();  // ❌ 使用系统默认时区

// 2. 禁止使用 Date（旧 API）
Date now = new Date();  // ❌ 使用 Instant 代替

// 3. 禁止直接使用 Calendar
Calendar now = Calendar.getInstance();  // ❌ 使用 Instant 代替

// 4. 禁止在数据面做时区转换
ZonedDateTime deviceTime = request.getDeviceTime();  // ❌ 设备时间不参与业务逻辑
```

### 测试规范

```java
// 单元测试：使用固定时间
@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {
    @Mock
    private Clock clock;

    @Test
    void testPolicyWindowCheck() {
        // 固定时间：2024-01-01 12:00:00 UTC
        Clock fixedClock = Clock.fixed(
            Instant.parse("2024-01-01T12:00:00Z"),
            ZoneOffset.UTC
        );

        when(clock.instant()).thenReturn(Instant.parse("2024-01-01T12:00:00Z"));

        // 测试逻辑
        boolean inWindow = policyService.isInPolicyWindow(policy, clock);
        assertTrue(inWindow);
    }
}
```

---

## 风险与处理

### 风险 1：网络延迟偏差

**描述**：事件真实发生时间与服务端接收时间有偏差

**处理**：
- ✅ 主口径用服务端时间（保证一致性）
- ✅ 可选保留 `device_reported_at` 供离线分析
- ✅ 计算 `network_delay_ms` 用于监控
- ✅ 监控 P99 延迟，设置告警阈值（如 >5s）

### 风险 2：离线补报场景

**描述**：设备断网后批量补报会"挤在同一时段"

**处理**：
- ✅ 增加 `ingest_delay_ms` 字段（事件发生到上报的延迟）
- ✅ 增加 `is_offline_batch` 标记（是否离线批量补报）
- ✅ 分析层可基于此字段做数据清洗
- ✅ 统计时排除异常补报数据

### 风险 3：跨区域时钟漂移

**描述**：不同 region 机器时间不一致（分钟桶对不齐）

**处理**：
- ✅ NTP 强校时（所有机器同步到时间服务器）
- ✅ 时钟漂移告警（>100ms 触发告警）
- ✅ 分钟桶对齐到整点（如 10:00:00、10:05:00）
- ✅ 定期校验时钟同步状态

### 风险 4：DST 切换

**描述**：夏令时切换导致时间窗口计算错误

**处理**：
- ✅ 统一使用 UTC 存储（避免 DST 影响）
- ✅ 策略窗口在服务端用 UTC 判断
- ✅ 展示层按用户时区转换时正确处理 DST
- ✅ 文档化 DST 边界测试用例

---

## 附录：IANA 时区 ID 列表

**常用时区**：

| 时区 ID | UTC 偏移 | 地区 |
|---------|----------|------|
| UTC | +00:00 | 协调世界时 |
| Asia/Shanghai | +08:00 | 中国（北京） |
| Asia/Tokyo | +09:00 | 日本（东京） |
| Asia/Seoul | +09:00 | 韩国（首尔） |
| Europe/London | +00:00/+01:00 | 英国（伦敦） |
| Europe/Paris | +01:00/+02:00 | 法国（巴黎） |
| Europe/Berlin | +01:00/+02:00 | 德国（柏林） |
| America/New_York | -05:00/-04:00 | 美国（纽约） |
| America/Los_Angeles | -08:00/-07:00 | 美国（洛杉矶） |

**使用方式**：
```java
ZoneId zoneId = ZoneId.of("Asia/Shanghai");
ZonedDateTime beijingTime = Instant.now().atZone(zoneId);
```

---

## 变更历史

| 版本 | 日期 | 变更内容 | 变更人 |
|------|------|----------|--------|
| v1.0 | 2026-02-04 | 首版：明确数据面 UTC-only、控制面时区感知、服务端时间作为唯一时间源 | FOTA 架构组 |
