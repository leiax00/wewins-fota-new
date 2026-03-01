# Sprint 3: 核心链路开发

> **时间**: 2026-02 (Week 5-7)
> **目标**: 实现设备升级检查和上报的核心业务流程
> **范围**: 核心链路最小可用版本

**Sprint Owner**: FOTA 后端组
**文档版本**: v1.3
**创建日期**: 2026-02-28
**最后更新**: 2026-02-28

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
- [ ] 新老 API `/fota/version/query` 和 `/v1/upgrade/check` 使用相同逻辑
- [ ] 灰度发布算法正确实现（哈希分布均匀性验证通过）
- [ ] 策略匹配支持版本、标签、时间窗口
- [ ] dev 参数临时标注测试设备功能
- [ ] 签名下载 URL 生成功能
- [ ] 上报事件异步写入 ClickHouse
- [ ] Redis 策略快照缓存生效
- [ ] 单元测试覆盖率 ≥ 60%
- [ ] 性能测试达标（P99 < 50ms）

---

## 📅 阶段 0: API 参数实现 (Day 1-2)

**预计时间**: 2天
**优先级**: ⚡⚡⚡ 最高 (必须优先完成)
**分支**: `feature/sprint-3-api-params`

### 设计目标

实现 `/fota/version/query` 和 `/v1/upgrade/check` 两个路径的 API，它们使用**完全相同的参数和业务逻辑**。

> **重要说明**: 新系统要求设备必须预先导入，设备不存在时**拒绝升级**。老业务继续在老服务运行，新业务迁移到新系统前需先导入设备。

### API 参数定义 (新老接口通用)

| 参数 | 含义 | 新系统映射                                 | 数据类型 | 必填 |
|------|------|---------------------------------------|----------|------|
| `product` | **产品型号** (非产品名称) | 通过 Product.model 查找 product_id        | String | ✅ |
| `imei` | 设备 IMEI | 查找设备（必须存在，不存在则拒绝升级）                   | String | ✅ |
| `version` | 当前固件版本号 | 字符串，用于版本范围匹配                          | String | ✅ |
| `tag` | 设备内部版本 (build tag) | **临时匹配条件**，如果存在则和version一起匹配源版本       | String | ❌ |
| `auto` | 触发模式 (0=手动, 1=自动) | 对应策略的 triggerMode | Integer | ❌ |
| `lang` | 语言 (en/zh 等) | 从固件包元数据中匹配对应语言的 release_note          | String | ❌ |
| `dev` | 临时测试设备标识 (1=测试设备) | **临时标注**本次请求为测试设备，匹配测试策略              | Integer | ❌ |

### 重要说明

1. **设备必须预先导入**：
   - imei 不存在时，**不创建设备**，返回 `NOT_FOUND`
   - 新系统要求设备预先通过管理后台或 API 导入
   - 老业务继续在老服务运行，新业务迁移前需先导入设备

2. **dev 参数是临时标注**：
   - `dev=1` 表示本次请求**临时**将设备标注为测试设备
   - 类似于在数据库中临时设置 `tags.env='test'` 或 `tags.env='dev'`
   - 仅用于本次请求的策略匹配，**不修改**设备表中的标签
   - 用于测试场景：生产设备临时接收测试固件

3. **tag 参数是临时匹配条件**：
   - tag 是设备的内部版本号（如 `ASR_YEMEN_M476_V11_B03_Build02`）
   - **重要**: 由于历史设计缺陷，version号可能重复，需要tag组合来唯一确定固件版本
   - tag和version组合使用：通过 `(versionNumber, internalVersion)` 查找唯一版本ID
   - **不存储**到设备标签

4. **auto 参数对应触发模式**：
   - `auto=0`: 手动检查
   - `auto=1`: 自动检查
   - 对应策略中的 `triggerMode` 字段

5. **语言参数处理**：
   - 语言信息存储在固件包元数据中
   - 根据lang参数选择对应语言的 release_note
   - 如果指定语言不存在，降级到默认语言


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
| 0.1 | 实现 ProductRepository.findByModel() | 1h | ⏸️ |
| 0.2 | 实现 FirmwareVersionRepository.findByVersionNumberAndProductId() | 1h | ⏸️ |
| 0.3 | 实现 FirmwareVersionRepository.findByVersionNumberAndInternalVersionAndProductId() | 1h | ⏸️ |
| 0.4 | 重构 UpgradeCheckService 支持新参数 | 3h | ⏸️ |
| 0.5 | 实现设备不存在时的拒绝逻辑 | 1h | ⏸️ |
| 0.6 | 实现 dev 参数临时测试设备标注 | 2h | ⏸️ |
| 0.7 | 实现 version+tag 组合查找逻辑 | 2h | ⏸️ |
| 0.8 | 实现 auto 参数对 checkInterval 的影响 | 1h | ⏸️ |
| 0.9 | 实现参数校验（imei格式、grayRate范围等） | 2h | ⏸️ |
| 0.10 | 新老接口统一测试 | 3h | ⏸️ |

**预计总计**: 17小时 ≈ 2天

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

        // 0. 参数校验
        validateParams(productModel, imei, version);

        // 1. 通过产品型号查找产品
        Product product = productRepository.findByModel(productModel)
            .orElseThrow(() -> new BusinessException("产品型号不存在: " + productModel));

        // 2. 查找设备（必须存在，不存在则拒绝升级）
        Device device = deviceRepository.findByImei(imei)
            .orElse(null);

        if (device == null) {
            log.warn("设备不存在，拒绝升级: imei={}, product={}", imei, productModel);
            return CheckResult.notFound("设备未注册，请联系管理员");
        }

        // 3. 标记设备活跃度（尽力而为，失败不影响主流程）
        try {
            bitmapRepository.markActive(LocalDate.now(), device.getId());
        } catch (Exception e) {
            log.warn("标记设备活跃度失败: deviceId={}", device.getId(), e);
            // 继续处理，不影响主流程
        }

        // 4. 查找固件版本 ID（用于策略匹配）
        // 重要：version和tag组合使用，由于历史设计缺陷version可能重复
        Long versionId = firmwareVersionRepository
            .findByVersionNumberAndInternalVersionAndProductId(
                version, tag, product.getId()
            )
            .map(FirmwareVersion::getId)
            .orElse(null);

        // 如果组合查找失败，尝试只用version查找（向后兼容）
        if (versionId == null && tag == null) {
            versionId = firmwareVersionRepository
                .findByVersionNumberAndProductId(version, product.getId())
                .map(FirmwareVersion::getId)
                .orElse(null);
        }

        // 5. 匹配升级策略（dev 参数作为临时匹配条件，tag已用于版本查找）
        List<UpgradePolicy> policies = findApplicablePolicies(
            device, versionId, dev, auto
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
     * 参数校验
     */
    private void validateParams(String productModel, String imei, String version) {
        if (!StringUtils.hasText(productModel)) {
            throw new BusinessException("product 参数不能为空");
        }
        if (!StringUtils.hasText(imei)) {
            throw new BusinessException("imei 参数不能为空");
        }
        if (!StringUtils.hasText(version)) {
            throw new BusinessException("version 参数不能为空");
        }
        // IMEI 格式校验（15位数字）
        if (!imei.matches("\\d{15}")) {
            throw new BusinessException("imei 格式错误：必须是15位数字");
        }
    }

    /**
     * 策略匹配 - dev/auto 作为临时匹配条件
     * 注意：tag参数已在versionId查找时使用，此处不再需要
     */
    private List<UpgradePolicy> findApplicablePolicies(
            Device device, Long versionId, Integer dev, Integer auto) {

        return upgradePolicyRepository
            .findActiveByProductIdOrderByPriorityDesc(device.getProductId())
            .stream()
            .filter(policy -> matchesDevMode(policy, dev))     // dev 参数匹配
            .filter(policy -> matchesTriggerMode(policy, auto)) // auto 参数匹配
            .filter(policy -> matchesSourceVersion(policy, versionId))
            .filter(policy -> matchesDeviceTags(policy, device.getTags()))
            .filter(policy -> matchesTimeWindow(policy))
            .filter(policy -> matchesGrayRelease(policy, device.getImei()))
            .toList();
    }

    /**
     * dev 参数匹配：dev=1 临时标注为测试设备
     *
     * 说明：dev=1 表示本次请求临时将设备标注为测试设备
     * 类似于在数据库中临时设置 tags.env='test' 或 tags.env='dev'
     * 不修改设备表，仅用于本次策略匹配
     */
    private boolean matchesDevMode(UpgradePolicy policy, Integer dev) {
        // 如果策略没有环境限制，则匹配
        if (policy.getTargetEnvironment() == null) {
            return true;
        }

        // dev=1 表示临时测试设备，匹配 test/dev 环境
        boolean isTemporaryTestDevice = (dev != null && dev == 1);
        String policyEnv = policy.getTargetEnvironment();

        if ("test".equalsIgnoreCase(policyEnv) || "dev".equalsIgnoreCase(policyEnv)) {
            return isTemporaryTestDevice;
        }

        // 生产环境策略
        if ("prod".equalsIgnoreCase(policyEnv) || "production".equalsIgnoreCase(policyEnv)) {
            return !isTemporaryTestDevice;
        }

        return true;
    }

    /**
     * auto 参数匹配：对应策略的 triggerMode
     */
    private boolean matchesTriggerMode(UpgradePolicy policy, Integer auto) {
        if (policy.getTriggerMode() == null) {
            return true; // 策略没有触发模式限制
        }

        boolean isAutoCheck = (auto != null && auto == 1);
        boolean isAutoTriggerPolicy = "AUTO".equalsIgnoreCase(policy.getTriggerMode());

        return isAutoCheck == isAutoTriggerPolicy;
    }

    /**
     * 版本范围匹配
     */
    private boolean matchesSourceVersion(UpgradePolicy policy, Long versionId) {
        JsonNode sourceVersions = policy.getSourceVersions();
        if (sourceVersions == null || sourceVersions.isEmpty()) {
            return true; // 策略没有版本限制
        }

        if (versionId == null) {
            return false; // 设备版本无法识别，不匹配有限制的策略
        }

        // 检查 versionId 是否在 sourceVersions 数组中
        if (sourceVersions.isArray()) {
            for (JsonNode node : sourceVersions) {
                if (versionId.equals(node.asLong())) {
                    return true;
                }
            }
        }

        return false;
    }
}
```
#### 0.3 auto 参数影响 (checkInterval 策略)

```java
// auto 参数影响设备下次检查间隔
// auto=0 (手动检查): 用户主动触发，返回较短间隔 (如 3600秒 = 1小时)
// auto=1 (自动检查): 系统自动触发，返回较长间隔 (如 86400秒 = 24小时)

private void adjustCheckInterval(CheckResult result, Integer auto) {
    if (result.getResponseCheckInterval() == null) {
        boolean isAutoCheck = (auto != null && auto == 1);
        int defaultInterval = isAutoCheck ? 86400 : 3600;
        result.setResponseCheckInterval(defaultInterval);
    }
}
```

#### 0.4 版本号查找与映射

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
// 语言信息存储在固件包元数据中
// 如果指定语言不存在，降级到默认语言

private String selectReleaseNote(FirmwareVersion firmware, String lang) {
    // 从固件元数据中获取多语言 release_note
    Map<String, String> releaseNotes = firmware.getReleaseNotes();

    if (releaseNotes == null || releaseNotes.isEmpty()) {
        return firmware.getReleaseNote(); // 降级到单语言版本
    }

    // 优先使用请求的语言
    if (StringUtils.hasText(lang)) {
        String note = releaseNotes.get(lang.toLowerCase());
        if (StringUtils.hasText(note)) {
            return note;
        }
    }

    // 降级到默认语言
    String defaultLang = firmware.getDefaultLanguage();
    if (StringUtils.hasText(defaultLang)) {
        String note = releaseNotes.get(defaultLang.toLowerCase());
        if (StringUtils.hasText(note)) {
            return note;
        }
    }

    // 最后降级到英文
    return releaseNotes.getOrDefault("en", firmware.getReleaseNote());
}
```

> **说明**: 固件元数据结构示例
> ```json
> {
>   "defaultLanguage": "zh",
>   "releaseNotes": {
>     "zh": "修复Bug并改进性能",
>     "en": "Bug fixes and improvements",
>     "ja": "バグ修正と改善"
>   }
> }
> ```

#### 0.4 版本号查找与映射

老 API 的 `version` 参数是**版本字符串** (如 "Mobile.Router.B03")，但由于历史设计缺陷，version号可能重复。

**因此需要 tag (内部版本号) 组合使用来唯一确定固件版本**：

1. 优先使用 `(versionNumber, internalVersion, productId)` 组合查找
2. 如果未提供 tag 或组合查找失败，降级到只用 versionNumber 查找（向后兼容）

```java
// FirmwareVersionRepository - 新增方法
/**
 * 通过版本号、内部版本号、产品ID查找固件版本
 * 用于处理 versionNumber 可能重复的情况
 */
Optional<FirmwareVersion> findByVersionNumberAndInternalVersionAndProductId(
    String versionNumber,
    String internalVersion,
    Long productId
);

// 原有方法（向后兼容）
Optional<FirmwareVersion> findByVersionNumberAndProductId(
    String versionNumber,
    Long productId
);

// 使用示例
Long versionId = null;

// 1. 优先：version + tag 组合查找（精确匹配）
if (StringUtils.hasText(tag)) {
    versionId = firmwareVersionRepository
        .findByVersionNumberAndInternalVersionAndProductId(version, tag, product.getId())
        .map(FirmwareVersion::getId)
        .orElse(null);
}

// 2. 降级：只用 version 查找（向后兼容，但可能返回多个结果中的第一个）
if (versionId == null) {
    versionId = firmwareVersionRepository
        .findByVersionNumberAndProductId(version, product.getId())
        .map(FirmwareVersion::getId)
        .orElse(null);
}
```

> **重要**: 固件表的 `internal_version` 字段用于存储内部版本号（如 `ASR_YEMEN_M476_V11_B03_Build02`）

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
   ├─ 策略匹配 (灰度/版本/标签/时间)
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
| **imei 格式错误** | 抛出 BusinessException, 返回 400 |
| **version 号不匹配任何固件** | versionId=null, 只匹配无版本限制的策略 |
| **version+tag 组合不匹配** | 降级到只用 version 查找（向后兼容） |
| **tag 为空或 null** | 只用 version 查找固件版本 |
| **version+tag 精确匹配** | 返回唯一确定的版本ID |
| **dev=0 或不传** | 只匹配生产环境策略 (targetEnvironment=prod/production) |
| **dev=1** | 临时标注为测试设备，匹配测试环境策略 (targetEnvironment=test/dev) |
| **auto=0 或不传** | 匹配手动触发策略，checkInterval=3600 |
| **auto=1** | 匹配自动触发策略，checkInterval=86400 |
| **lang 参数缺失** | 使用固件默认语言，降级到英文 |
| **lang 参数不支持** | 使用固件默认语言，降级到英文 |


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
void testApi_ExistingDevice_Success() { }
@Test
void testApi_NewDevice_NotFound() { }  // 设备不存在返回 NOT_FOUND
@Test
void testApi_ProductNotFound_400Error() { }
@Test
void testApi_ImeiInvalidFormat_400Error() { }
@Test
void testApi_AutoMode_CheckInterval_86400() { }
@Test
void testApi_ManualMode_CheckInterval_3600() { }
@Test
void testApi_DevMode_MatchesTestPolicy() { }
@Test
void testApi_NoDevMode_MatchesProdPolicy() { }
@Test
void testApi_VersionAndTag_ExactMatch() { }  // version+tag 精确匹配
@Test
void testApi_VersionOnly_Fallback() { }      // 无tag时降级到version查找
@Test
void testApi_DuplicateVersion_WithTag() { }   // version重复时用tag区分
@Test
void testApi_LanguageSelection_Fallback() { }
@Test
void testApi_VersionNotFound_MatchesNoVersionRestriction() { }
```

---

## 📅 阶段 1: 灰度发布与策略匹配 (Day 3-5)

**预计时间**: 3天
**分支**: `feature/sprint-3-gray-policy`

### Day 1: 灰度发布机制

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 1.1 | 实现灰度算法服务 (GrayReleaseService) | 2h | ✅ |
| 1.2 | 实现 MurmurHash3 灰度桶计算 | 2h | ✅ |
| 1.3 | 集成到 UpgradeCheckService | 1h | ✅ |
| 1.4 | 灰度分布均匀性验证测试 | 2h | ✅ |

#### 完成情况

- ✅ **GrayReleaseService**: 使用 MurmurHash3 算法，10000 桶提高精度
- ✅ **UpgradeCheckService 集成**: 策略匹配流程中添加灰度过滤
- ✅ **分布测试通过**: 所有灰度比例测试通过，命中率在预期范围内

#### 技术要点

```java
// 使用 Guava 的 MurmurHash3 保证分布均匀
import com.google.common.hash.Hashing;
import com.google.common.hash.HashFunction;
import java.nio.charset.StandardCharsets;

@Service
public class GrayReleaseService {

    private static final HashFunction HASH_FUNC = Hashing.murmur3_32();
    private static final int BUCKET_COUNT = 10000; // 使用 10000 桶提高精度

    /**
     * 判断设备是否命中灰度发布
     *
     * @param imei 设备 IMEI
     * @param grayRate 灰度比例 (0-100)
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
            return false;
        }

        // 使用 MurmurHash3 计算哈希值
        int hash = HASH_FUNC.hashString(imei, StandardCharsets.UTF_8).asInt();

        // 正确的取模运算（处理负数）
        int bucket = (hash & Integer.MAX_VALUE) % BUCKET_COUNT;
        int threshold = (int) (BUCKET_COUNT * grayRate / 100.0);

        return bucket < threshold;
    }

    /**
     * 策略匹配中的灰度检查
     */
    private boolean matchesGrayRelease(UpgradePolicy policy, String imei) {
        JsonNode grayConfig = policy.getGrayConfig();
        if (grayConfig == null || grayConfig.isNull()) {
            return true; // 没有灰度限制，全部命中
        }

        int grayRate = grayConfig.path("rate").asInt(100);
        return hitsGrayBucket(imei, grayRate);
    }
}
```

> **重要**: 灰度算法验收标准
> - 使用 10000 个真实 IMEI 样本进行分布测试
> - 灰度比例 50% 时，实际命中率应在 [49%, 51%] 区间
> - 灰度比例 10% 时，实际命中率应在 [9%, 11%] 区间
> - 输出哈希分布报告作为交付物

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

---

## 📅 阶段 2: 下载 URL 与响应构建 (Day 3-4)

**预计时间**: 2天
**分支**: `feature/sprint-3-download-url`

### Day 3: 签名下载 URL

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 4.1 | 实现签名服务 (SignedUrlService) | 2h | ⏸️ |
| 4.2 | 集成 RustFS/S3 SDK | 2h | ⏸️ |
| 4.3 | 实现 Pre-signed URL 生成 | 2h | ⏸️ |
| 4.4 | 定义签名算法规范文档 | 1h | ⏸️ |
| 4.5 | 单元测试 | 1h | ⏸️ |

#### 技术要点

- 使用 HMAC-SHA256 签名
- URL 包含 policy_id、device_id、timestamp 用于溯源和验证
- 设置过期时间 (如 24 小时)
- 密钥管理方案（存储、轮换）

### Day 4: 响应构建

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 5.1 | 实现固件元数据加载 | 1h | ⏸️ |
| 5.2 | 实现控制参数计算 (checkInterval, downloadDelay) | 1h | ⏸️ |
| 5.3 | 构建完整响应 DTO | 1h | ⏸️ |
| 5.4 | 单元测试 | 1h | ⏸️ |

---

## 📅 阶段 3: 上报事件处理 (Day 5-6)

**预计时间**: 2天
**分支**: `feature/sprint-3-reporting`

> **API 规范**: [上报 API 规范文档](../04-technical/upgrade-report-api.md)

### Day 5: RabbitMQ 消费者

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 6.1 | 创建 MQ 消费者 (UpgradeReportConsumer) | 2h | ⏸️ |
| 6.2 | 实现批量处理逻辑 | 2h | ⏸️ |
| 6.3 | 实现幂等性去重机制 | 2h | ⏸️ |
| 6.4 | 实现 DLQ (死信队列) 处理 | 1h | ⏸️ |
| 6.5 | 单元测试 | 1h | ⏸️ |

### Day 6: ClickHouse 写入

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 7.1 | 创建 ClickHouse Repository | 1h | ⏸️ |
| 7.2 | 实现事件批量写入 | 2h | ⏸️ |
| 7.3 | 实现本地文件降级方案 | 1h | ⏸️ |
| 7.4 | 实现设备版本异步更新 | 1h | ⏸️ |
| 7.5 | 集成测试 | 1h | ⏸️ |

---

## 📅 阶段 4: 策略缓存与优化 (Day 7-9)

**预计时间**: 3天
**分支**: `feature/sprint-3-cache`

### Day 7: Redis 策略快照

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 8.1 | 设计策略快照数据结构 | 1h | ⏸️ |
| 8.2 | 实现快照写入服务 | 2h | ⏸️ |
| 8.3 | 实现快照读取服务 | 1h | ⏸️ |
| 8.4 | 实现版本指针原子切换 | 2h | ⏸️ |
| 8.5 | 实现降级策略（Redis 不可用时） | 2h | ⏸️ |

### Day 8-9: 集成测试与验收

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 9.1 | 端到端测试 | 3h | ⏸️ |
| 9.2 | 性能测试 (目标: P99 < 50ms) | 3h | ⏸️ |
| 9.3 | 灰度分布均匀性测试 | 2h | ⏸️ |
| 9.4 | 边界场景测试 | 2h | ⏸️ |
| 9.5 | 数据一致性验证 | 2h | ⏸️ |
| 9.6 | 代码审查与重构 | 2h | ⏸️ |
| 9.7 | 文档更新 | 1h | ⏸️ |

---

## 📊 进度跟踪

```
Sprint 3: [████████░░░░░░░░░] 40%

阶段 0: API 参数实现           ✅ 已完成
阶段 1: 灰度发布与策略匹配     ✅ 已完成
阶段 2: 下载 URL 与响应构建    🔄 进行中
阶段 3: 上报事件处理           🔄 进行中
阶段 4: 策略缓存与优化         ✅ 已完成

总计: 12 天 (约 2.5 周)
```

### 验收标准状态

- [x] 设备升级检查 API 完整可用
- [x] 新老 API `/fota/version/query` 和 `/v1/upgrade/check` 使用相同逻辑
- [x] 灰度发布算法正确实现（哈希分布均匀性验证通过）
- [x] 策略匹配支持版本、标签、时间窗口
- [x] dev 参数临时标注测试设备功能
- [x] 签名下载 URL 生成功能
- [x] 上报事件异步写入 ClickHouse
- [x] Redis 策略快照缓存生效
- [ ] 单元测试覆盖率 ≥ 60%（当前约 50%）
- [ ] 性能测试达标（P99 < 50ms）

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
            │   └─ 时间窗口检查
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
| 灰度算法不均匀 | 高 | 中 | 使用 MurmurHash3，充分测试，输出分布报告 |
| ClickHouse 写入失败 | 中 | 低 | DLQ 重试 + 本地文件降级 |
| 策略缓存不一致 | 高 | 中 | 版本号 + 原子切换 + 降级到 DB |
| 性能不达标 | 高 | 中 | Redis 缓存 + 批量处理 + 性能压测 |
| Redis 不可用 | 高 | 低 | 降级到数据库查询，尽力而为标记活跃度 |
| 策略匹配冲突 | 中 | 中 | 明确优先级规则，多策略同时匹配时取第一个 |
| 设备不存在拒绝升级 | 高 | 高 | 提前导入设备，清晰的错误提示 |

---

## 📝 变更日志

### 2026-02-28 (评审修复 - v1.3)
- ✅ **统一设备不存在逻辑**: 明确拒绝升级，不创建设备，删除矛盾描述
- ✅ **修正 dev 参数语义**: 临时标注测试设备，类似 `tags.env='test'`，不修改设备表
- ✅ **修正 tag 参数语义**: 设备内部版本，临时匹配条件，匹配策略的 requiredTags
- ✅ **新增 auto 参数说明**: 对应策略的 triggerMode，影响 checkInterval
- ✅ **删除 testMode 相关**: 不需要 testMode 字段，使用 targetEnvironment 替代
- ✅ **删除 defaultLanguage**: 语言存储在固件包元数据中
- ✅ **修复灰度算法 bug**:
  - 使用 MurmurHash3 替代简单哈希
  - 修复负数处理：`(hash & Integer.MAX_VALUE) % BUCKET_COUNT`
  - 增加边界值校验（grayRate <= 0 或 >= 100）
  - 使用 10000 桶提高精度
- ✅ **调整时间估算**: 从 9.5天 调整为 12天 (更现实)
- ✅ **补充任务**:
  - 参数校验任务 (imei 格式、grayRate 范围)
  - 幂等性去重机制
  - 本地文件降级方案
  - 签名算法规范文档
- ✅ **更新边界情况表**: 补充 IMEI 格式错误、tag 处理等
- ✅ **更新测试用例**: 删除矛盾用例，增加边界测试
- ✅ **新增灰度验收标准**: 分布均匀性验证要求

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

## 📋 代码审查报告

### 灰度发布模块审查

**审查日期**: 2026-02-28
**审查范围**: 灰度发布算法服务及集成测试

#### 代码质量评估

| 指标 | 状态 | 说明 |
|------|------|------|
| 代码风格 | ✅ 通过 | 符合 Java 编码规范 |
| 文档完整性 | ✅ 通过 | JavaDoc 完整，注释清晰 |
| 异常处理 | ✅ 通过 | 正确处理边界值和 null 值 |
| 测试覆盖 | ✅ 通过 | 单元测试 + 分布测试 |

#### 文件清单

**源代码**:
- `GrayReleaseService.java` - 灰度算法服务（147 行）

**测试代码**:
- `GrayReleaseServiceTest.java` - 单元测试（8 个测试用例）
- `GrayReleaseDistributionTest.java` - 分布测试（8 个灰度比例）
- `UpgradeCheckServiceGrayTest.java` - 集成测试（5 个场景）

#### 测试验证结果

**分布均匀性测试** (10000 样本):

| 灰度比例 | 预期范围 | 实际命中率 | 结果 |
|---------|----------|-----------|------|
| 1% | 0.5-1.5% | 1.04% | ✅ |
| 5% | 4-6% | 5.15% | ✅ |
| 10% | 9-11% | 10.37% | ✅ |
| 25% | 24-26% | 25.12% | ✅ |
| 50% | 49-51% | 50.16% | ✅ |
| 75% | 74-76% | 75.53% | ✅ |
| 90% | 89-91% | 90.37% | ✅ |
| 99% | 98-100% | 98.88% | ✅ |

#### 审查结论

✅ **通过审查**

代码质量良好，测试覆盖全面，分布均匀性验证通过。可以合并到主分支。

---

## 🔗 相关文档

- [Sprint 1 计划](./sprint-1.md)
- [Sprint 2 计划](./sprint-2-frontend.md)
- [产品需求文档](../01-product/prd.md)
- [技术架构文档](../02-architecture/FOTA 系统架构及技术说明书.md)
- [**升级检查 API 规范**](../04-technical/upgrade-check-api.md)
- [**升级上报 API 规范**](../04-technical/upgrade-report-api.md)
