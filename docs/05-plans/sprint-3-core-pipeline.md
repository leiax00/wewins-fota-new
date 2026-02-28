# Sprint 3: 核心链路开发

> **时间**: 2026-02 (Week 5-7)
> **目标**: 实现设备升级检查和上报的核心业务流程
> **范围**: 核心链路最小可用版本

**Sprint Owner**: FOTA 后端组
**文档版本**: v1.0
**创建日期**: 2026-02-28

---

## 📋 Sprint 概览

### 目标

- 实现设备升级检查完整流程 (`/v1/upgrade/check`)
- 实现设备上报异步处理 (`/v1/upgrade/report`)
- 实现灰度发布机制
- 实现 Redis 策略快照缓存
- 实现 ClickHouse 事件记录

### 范围

- ✅ **包含**: 核心升级链路、灰度发布、策略缓存、事件记录
- ❌ **不包含**: 跨区域同步、CDN 预热、高级监控

### 验收标准

- [ ] 设备升级检查 API 完整可用
- [ ] **老 API `/fota/version/query` 完全兼容**
- [ ] 灰度发布算法正确实现
- [ ] 策略匹配支持版本、标签、时间窗口、配额
- [ ] 签名下载 URL 生成功能
- [ ] 上报事件异步写入 ClickHouse
- [ ] Redis 策略快照缓存生效
- [ ] 单元测试覆盖率 ≥ 60%

---

## 📅 阶段 0: API 参数实现 (Day 0.5)

**预计时间**: 0.5天
**优先级**: ⚡⚡⚡ 最高 (必须优先完成)
**分支**: `feature/sprint-3-api-params`

### 设计目标

实现 `/fota/version/query` 和 `/v1/upgrade/check` 两个路径的 API，它们使用**完全相同的参数和业务逻辑**。设备侧可以使用任一路径进行升级检查。

### API 参数定义 (新老接口通用)

| 参数 | 含义 | 新系统映射 | 数据类型 | 必填 |
|------|------|-----------|----------|------|
| `product` | **产品型号** (非产品名称) | 通过 Product.model 查找 product_id | String | ✅ |
| `imei` | 设备 IMEI | 查找设备（必须存在，不存在则拒绝升级） | String | ✅ |
| `version` | 当前固件版本号 | 字符串，用于版本匹配 | String | ✅ |
| `tag` | 设备内部版本 (build tag) | 存入 tags.internal_version | String | ❌ |
| `auto` | 触发模式 (0=手动, 1=自动) | 影响 checkInterval 返回值 | Integer | ❌ |
| `lang` | 语言 (en/zh 等) | 影响 release_note 语言 | String | ❌ |
| `dev` | 环境标识 (1=开发环境) | 存入 tags.env = 'dev' | Integer | ❌ |

### 重要说明

1. **设备必须预先导入**：imei 不存在时，**不创建设备**，直接返回无更新/错误
2. **dev 参数不存储**：dev=1 仅用于本次请求的策略匹配判断，不更新到设备标签
3. **tag 参数不存储**：tag 也是策略匹配的条件，不存储到设备标签
4. **策略匹配条件**：
  - 设备的当前版本号 (version)
  - 设备的内部版本 (tag)
  - 是否测试设备 (dev)
  - 设备的标签 (tags - 已存储在设备表中的标签)


### API 接口对比

| 接口 | 路径 | 方法 | 参数 | 说明 |
|------|------|------|------|------|
| **老接口** | `/fota/version/query` | GET/POST | 见上表 | 兼容老设备，路径保持不变 |
| **新接口** | `/v1/upgrade/check` | GET/POST | 见上表 | 新标准路径，参数与老接口完全一致 |

### 请求示例 (新老接口通用)

```bash
# 实际的 curl 请求示例
curl --location --request GET \
'/fota/version/query?product=asr_yemen_m476_vsim&imei=354972069009027&version=Mobile.Router.B03&auto=0&lang=en&tag=ASR_YEMEN_M476_M483_V11_B03_Build02&dev=1'

# 参数解析
product = "asr_yemen_m476_vsim"        # 产品型号 (对应 Product.model)
imei    = "354972069009027"            # 设备 IMEI
version = "Mobile.Router.B03"          # 当前固件版本号
auto    = 0                            # 手动检查
lang    = "en"                         # 英文
tag     = "ASR_YEMEN_M476_M483_V11_B03_Build02"  # 内部版本
dev     = 1                            # 开发环境
```

### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 0.1 | 实现 ProductRepository.findByModel() | 0.5h | ⏸️ |
| 0.2 | 实现 FirmwareVersionRepository.findByVersionNumberAndProductId() | 0.5h | ⏸️ |
| 0.3 | 统一新老接口的 UpgradeCheckService 参数处理 | 1.5h | ⏸️ |
| 0.4 | 实现设备不存在时的拒绝逻辑 | 0.5h | ⏸️ |
| 0.5 | 实现 dev/tag 参数的策略匹配判断 | 1h | ⏸️ |
| 0.6 | 新老接口统一测试 | 1h | ⏸️ |

### 技术要点

#### 0.1 产品型号查询

```java
// ProductRepository.java - 新增方法
/**
 * 根据产品型号查询产品
 * <p>
 * 用于兼容老 API，通过产品型号查找产品
 * </p>
 *
 * @param model 产品型号
 * @return 产品信息（如果存在且未删除）
 */
Optional<Product> findByModel(String model);

// ProductRepositoryImpl.java - 实现
@Override
public Optional<Product> findByModel(String model) {
    return Optional.ofNullable(
        productMapper.selectOne(
            new LambdaQueryWrapper<Product>()
                .eq(Product::getModel, model)
                .isNull(Product::getDeletedAt)
        )
    );
}
```

#### 0.2 UpgradeCheckService 实现

```java
// UpgradeCheckService.java - 两个 API 路径共用
@Service
@RequiredArgsConstructor
public class UpgradeCheckService {

    public CheckResult checkUpgrade(
            String productModel,
            String imei,
            String version,
            String tag,
            Integer auto,
            String lang,
            Integer dev) {

        // 1. 通过产品型号查找产品
        Product product = productRepository.findByModel(productModel)
            .orElseThrow(() -> new BusinessException("产品型号不存在: " + productModel));

        // 2. 查找设备（必须存在，不存在则拒绝升级）
        Device device = deviceRepository.findByImei(imei)
            .orElse(null);

        if (device == null) {
            log.warn("设备不存在，拒绝升级: imei={}", imei);
            return CheckResult.notFound("设备未注册");
        }

        // 3. 标记设备活跃度
        bitmapRepository.markActive(LocalDate.now(), device.getId());

        // 4. 查找固件版本 ID（用于策略匹配）
        Long versionId = firmwareVersionRepository
            .findByVersionNumberAndProductId(version, product.getId())
            .map(FirmwareVersion::getId)
            .orElse(null);

        // 5. 匹配升级策略（dev 和 tag 参数用于匹配）
        List<UpgradePolicy> policies = findApplicablePolicies(
            device, versionId, tag, dev
        );

        if (policies.isEmpty()) {
            return CheckResult.noUpdate();
        }

        // 6. 构建响应并调整 checkInterval
        CheckResult result = buildCheckResult(policies.get(0), lang);
        adjustCheckInterval(result, auto);

        return result;
    }

    /**
     * 策略匹配 - dev 参数用于判断测试/生产策略
     */
    private List<UpgradePolicy> findApplicablePolicies(
            Device device, Long versionId, String tag, Integer dev) {

        return upgradePolicyRepository
            .findActiveByProductIdOrderByPriorityDesc(device.getProductId())
            .stream()
            .filter(policy -> matchesDev(policy, dev))  // dev 参数匹配
            .filter(policy -> matchesTag(policy, tag))  // tag 参数匹配
            .filter(policy -> matchesVersion(policy, versionId))
            .filter(policy -> matchesDeviceTags(policy, device.getTags()))
            .toList();
    }

    /**
     * dev 参数匹配：dev=1 只匹配测试策略
     */
    private boolean matchesDev(UpgradePolicy policy, Integer dev) {
        boolean isTestDevice = (dev != null && dev == 1);
        boolean isTestPolicy = policy.getTestMode();
        return isTestDevice == isTestPolicy;
    }

    /**
     * tag 参数匹配：用于策略过滤
     */
    private boolean matchesTag(UpgradePolicy policy, String tag) {
        if (policy.getRequiredTags() == null || policy.getRequiredTags().isEmpty()) {
            return true;
        }
        return policy.getRequiredTags().contains(tag);
    }
}
```
#### 0.3 auto 参数影响 (checkInterval 策略)

```java
// auto 参数影响设备下次检查间隔
// auto=0 (手动检查): 用户主动触发，不影响检测周期，后面根据系统负载直接给出间隔即可
// auto=1 (自动检查): 系统自动触发，返回较长间隔，如 86400 (24小时)

private void adjustCheckInterval(CheckResult result, Integer auto) {
    if (result.getResponseCheckInterval() == null) {
        int defaultInterval = (auto != null && auto == 1) ? 86400 : 3600;
        result.setResponseCheckInterval(defaultInterval);
    }
}
```

#### 0.4 版本号查找与映射

老 API 的 `version` 参数是**版本字符串** (如 "Mobile.Router.B03")，需要：
1. 查找对应的 FirmwareVersion 记录
2. 获取 version_id 用于策略匹配

```java
// FirmwareVersionRepository
Optional<FirmwareVersion> findByVersionNumberAndProductId(
    String versionNumber,
    Long productId
);

// 使用示例
FirmwareVersion firmwareVersion = firmwareVersionRepository
    .findByVersionNumberAndProductId(version, product.getId())
    .orElse(null);

Long versionId = firmwareVersion != null ? firmwareVersion.getId() : null;
```

#### 0.5 语言参数处理

```java
// lang 参数影响返回的 release_note 语言
// 默认支持: en (英文), zh (中文)
// 如果未指定，使用产品默认语言

private String determineLanguage(String lang, Product product) {
    if (!StringUtils.hasText(lang)) {
        return product.getDefaultLanguage(); // 从产品配置获取默认语言
    }
    return lang.toLowerCase(); // en, zh, etc.
}
```

---

## 📐 老 API 兼容性设计详解

### 完整数据流

```
1. 老 API 请求
   GET /fota/version/query?product=xxx&imei=xxx&version=xxx&tag=xxx&auto=0&lang=en&dev=1
              ↓
2. UpgradeCheckController.checkUpgradeOld()
              ↓
3. LegacyApiCompatService.handleLegacyRequest()
   ├─ 通过 product (型号) 查找 Product → product_id
   ├─ 通过 imei 查找 Device → 不存在则创建
   ├─ 补充设备标签:
   │  ├─ tags.internal_version = tag
   │  └─ tags.env = (dev == 1) ? "dev" : null
   ├─ 通过 version 查找 FirmwareVersion → version_id
   ↓
4. UpgradeCheckService.checkUpgrade()
   ├─ 限流检查
   ├─ 标记活跃度 (Redis Bitmap)
   ├─ 策略匹配 (灰度/版本/标签/时间/配额)
   └─ 构建响应
   ↓
5. 调整 checkInterval (根据 auto 参数)
   ↓
6. 返回 CheckResult
```

### 边界情况处理

| 情况 | 处理策略 |
|------|----------|
| **product 型号不存在** | 抛出 BusinessException, 返回 400 |
| **imei 不存在** | 返回 NOT_FOUND, **不创建设备** |
| **version 号不匹配任何固件** | versionId=null, 仍可检查更新 |
| **tag 为空** | 不过滤 tag, 匹配所有策略 |
| **dev=0 或不传** | 只匹配生产策略 (testMode=false) |
| **dev=1** | 只匹配测试策略 (testMode=true) |
| **auto 参数缺失** | 默认按手动模式 (auto=0) 处理 |
| **lang 参数缺失** | 使用产品默认语言 |


### 响应格式兼容

老 API 返回格式需要保持兼容:

```json
{
  "hasUpdate": true,
  "decision": "UPDATE",
  "targetVersionId": 123,
  "targetVersion": "v2.0.0",
  "policyId": 456,
  "responseCheckInterval": 3600,
  "downloadDelay": 300,
  "releaseStartDate": "2026-02-03T10:28:56",
  "releaseNote": "Bug fixes and improvements",
  "downloadUrl": "https://cdn.example.com/pkg.bin?sig=xxx",
  "fileSize": 20000000,
  "fileSizeText": "19MB",
  "control": {
    "checkInterval": 3600,
    "downloadDelay": 300
  }
}
```

### API 路径说明

| 路径 | 方法 | 说明 |
|------|------|------|
| `/fota/version/query` | GET/POST | 设备升级检查接口 |
| `/v1/upgrade/check` | GET/POST | 设备升级检查接口（新路径） |

> **说明**: 两个路径使用相同的参数和业务逻辑，设备可以使用任一路径。

### 测试用例

```java
// 测试用例清单
@Test
void testLegacyApi_ExistingDevice() { }
@Test
void testLegacyApi_NewDevice_Created() { }
@Test
void testLegacyApi_ProductNotFound() { }
@Test
void testLegacyApi_AutoMode_CheckInterval() { }
@Test
void testLegacyApi_ManualMode_CheckInterval() { }
@Test
void testLegacyApi_DevMode_EnvTag() { }
@Test
void testLegacyApi_TagInternalVersion() { }
@Test
void testLegacyApi_LanguageSelection() { }
```

---

## 📅 阶段 1: 灰度发布与策略匹配 (Day 1-3)

**预计时间**: 3天
**分支**: `feature/sprint-3-gray-policy`

### Day 1: 灰度发布机制

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 1.1 | 实现灰度算法服务 (GrayReleaseService) | 2h | ⏸️ |
| 1.2 | 实现 Hash(imei) 灰度桶计算 | 1h | ⏸️ |
| 1.3 | 集成到 UpgradeCheckService | 1h | ⏸️ |
| 1.4 | 单元测试 | 1h | ⏸️ |

#### 技术要点

```java
// 灰度算法
public boolean hitsGrayBucket(String imei, int grayRate) {
    int bucket = Math.abs(hash(imei) % 100);
    return bucket < grayRate;
}
```

### Day 2: 策略匹配增强

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 2.1 | 实现版本范围匹配 | 2h | ⏸️ |
| 2.2 | 实现标签匹配 (JSONB) | 2h | ⏸️ |
| 2.3 | 实现时间窗口检查 | 2h | ⏸️ |
| 2.4 | 单元测试 | 1h | ⏸️ |

#### 技术要点

- **版本范围**: 支持多个当前版本号
- **标签匹配**: PostgreSQL JSONB 查询
- **时间窗口**: LocalDateTime 比较

### Day 3: 配额限制

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 3.1 | 实现 Redis 配额计数器 | 2h | ⏸️ |
| 3.2 | 实现分布式锁 | 1h | ⏸️ |
| 3.3 | 集成配额检查到升级流程 | 1h | ⏸️ |
| 3.4 | 单元测试 | 1h | ⏸️ |

---

## 📅 阶段 2: 下载 URL 与响应构建 (Day 4-5)

**预计时间**: 2天
**分支**: `feature/sprint-3-download-url`

### Day 4: 签名下载 URL

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 4.1 | 实现签名服务 (SignedUrlService) | 2h | ⏸️ |
| 4.2 | 集成 RustFS/S3 SDK | 2h | ⏸️ |
| 4.3 | 实现 Pre-signed URL 生成 | 2h | ⏸️ |
| 4.4 | 单元测试 | 1h | ⏸️ |

#### 技术要点

- 使用 HMAC-SHA256 签名
- URL 包含 policy_id 用于日志溯源
- 设置过期时间 (如 24 小时)

### Day 5: 响应构建

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 5.1 | 实固件元数据加载 | 1h | ⏸️ |
| 5.2 | 实现控制参数计算 (checkInterval, downloadDelay) | 1h | ⏸️ |
| 5.3 | 构建完整响应 DTO | 1h | ⏸️ |
| 5.4 | 单元测试 | 1h | ⏸️ |

---

## 📅 阶段 3: 上报事件处理 (Day 6-7)

**预计时间**: 2天
**分支**: `feature/sprint-3-reporting`

### Day 6: RabbitMQ 消费者

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 6.1 | 创建 MQ 消费者 (UpgradeReportConsumer) | 2h | ⏸️ |
| 6.2 | 实现批量处理逻辑 | 2h | ⏸️ |
| 6.3 | 实现 DLQ (死信队列) 处理 | 1h | ⏸️ |
| 6.4 | 单元测试 | 1h | ⏸️ |

### Day 7: ClickHouse 写入

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 7.1 | 创建 ClickHouse Repository | 1h | ⏸️ |
| 7.2 | 实现事件批量写入 | 2h | ⏸️ |
| 7.3 | 实现设备版本异步更新 | 1h | ⏸️ |
| 7.4 | 集成测试 | 1h | ⏸️ |

---

## 📅 阶段 4: 策略缓存与优化 (Day 8-9)

**预计时间**: 2天
**分支**: `feature/sprint-3-cache`

### Day 8: Redis 策略快照

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 8.1 | 设计策略快照数据结构 | 1h | ⏸️ |
| 8.2 | 实现快照写入服务 | 2h | ⏸️ |
| 8.3 | 实现快照读取服务 | 1h | ⏸️ |
| 8.4 | 版本指针原子切换 | 1h | ⏸️ |

### Day 9: 集成测试与验收

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 9.1 | 端到端测试 | 2h | ⏸️ |
| 9.2 | 性能测试 (目标: 50ms P99) | 2h | ⏸️ |
| 9.3 | 代码审查与重构 | 1h | ⏸️ |
| 9.4 | 文档更新 | 1h | ⏸️ |

---

## 📊 进度跟踪

```
Sprint 3: [░░░░░░░░░░░░░░░░░] 0%

阶段 0: 老 API 兼容            ⏸️ Day 0.5 (优先级最高)
阶段 1: 灰度发布与策略匹配    ⏸️ Day 1-3
阶段 2: 下载 URL 与响应构建    ⏸️ Day 4-5
阶段 3: 上报事件处理          ⏸️ Day 6-7
阶段 4: 策略缓存与优化        ⏸️ Day 8-9
```

---

## 🏗️ 架构设计

### 核心流程

```
设备请求 → UpgradeCheckController
         → UpgradeCheckService
            ├─ 限流检查 (Redis)
            ├─ 设备加载 (Redis Cache → PostgreSQL)
            ├─ 活跃度标记 (Redis Bitmap)
            ├─ 策略匹配 (Redis Cache → PostgreSQL)
            │   ├─ 灰度检查 (Hash 算法)
            │   ├─ 版本范围匹配
            │   ├─ 标签匹配 (JSONB)
            │   ├─ 时间窗口检查
            │   └─ 配额检查 (Redis Counter)
            └─ 响应构建
               ├─ 签名下载 URL (RustFS/S3)
               └─ 控制参数计算
         → 返回响应
```

### 异步上报流程

```
设备上报 → UpgradeReportController
         → UpgradeReportAppService
         → RabbitMQ (发后即忘)
         → UpgradeReportConsumer (批量消费)
            ├─ ClickHouse (事件记录)
            └─ PostgreSQL (设备版本更新, 仅 UP_OK)
         → 返回 200 OK
```

---

## ⚠️ 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 灰度算法不均匀 | 高 | 中 | 使用一致性哈希,充分测试 |
| 配额超卖 | 中 | 中 | 使用 Redis 分布式锁 |
| ClickHouse 写入失败 | 中 | 低 | DLQ 重试机制 |
| 策略缓存不一致 | 高 | 中 | 版本号 + 原子切换 |
| 性能不达标 | 高 | 中 | Redis 缓存 + 批量处理 |

---

## 📝 变更日志

### 2026-02-28 (简化 - v1.2)
- ✅ **简化文档定位**: 不再区分"老 API"和"新 API"，统一为 API 实现
- ✅ **修正设备不存在逻辑**: 返回 NOT_FOUND，不创建设备
- ✅ **修正 dev 参数用途**: 用于策略匹配（dev=1 匹配测试策略）
- ✅ **修正 tag 参数用途**: 用于策略匹配（策略的 requiredTags）
- ✅ **更新边界情况处理表**: 设备不存在返回 NOT_FOUND
- ✅ **更新任务清单**: 删除设备自动创建，新增拒绝逻辑和策略匹配任务

### 2026-02-28 (完善 - v1.1)
- ✅ **修正参数定义**: `product` 参数是**产品型号** (非产品名称)
- ✅ **补充完整示例**: 添加真实 curl 请求示例
- ✅ **新增兼容性设计章节**:
  - 完整数据流图
  - 边界情况处理表
  - 设备自动创建逻辑
  - 响应格式兼容说明
  - 老 vs 新 API 对比
  - 测试用例清单
- ✅ **细化技术要点**:
  - ProductRepository.findByModel() 方法
  - 版本号查找与映射逻辑
  - 语言参数处理策略
  - auto 参数对 checkInterval 的影响
- ✅ **更新任务清单**: 添加"设备自动创建"任务

### 2026-02-28 (初版 - v1.0)
- ✅ **新增阶段 0**: 老 API `/fota/version/query` 兼容（优先级最高）
- ✅ 添加参数映射表 (product, tag, auto, lang, dev)
- ✅ 设计 LegacyApiCompatService 兼容层
- 📝 创建 Sprint 3 计划文档
- 📝 确定核心链路实施步骤
- 📝 细化任务清单和时间估算

---

## 🔗 相关文档

- [Sprint 1 计划](./sprint-1.md)
- [Sprint 2 计划](./sprint-2-frontend.md)
- [产品需求文档](../01-product/prd.md)
- [技术架构文档](../02-architecture/FOTA 系统架构及技术说明书.md)
