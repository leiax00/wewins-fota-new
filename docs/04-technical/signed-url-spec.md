# 签名下载 URL 技术规范

> **版本**: v1.0
> **创建日期**: 2026-02-28
> **作者**: FOTA 后端组
> **状态**: 设计中

---

## 1. 概述

### 1.1 目的

本规范定义了 FOTA 系统中签名下载 URL 的生成和验证机制，确保：

- **防篡改**: URL 被篡改后无法通过验证
- **防滥用**: URL 具有时效性，过期自动失效
- **可溯源**: URL 中包含策略和设备信息，便于审计
- **高性能**: 验证过程轻量，不影响下载性能

### 1.2 适用场景

- 设备通过 `/v1/upgrade/check` 获取固件下载链接
- 固件包托管在 RustFS/S3 兼容存储
- CDN 边缘节点或存储服务自动验证签名

---

## 2. 签名算法

### 2.1 算法选择

采用 **HMAC-SHA256** 签名算法：

```
signature = HMAC-SHA256(secret_key, string_to_sign)
```

### 2.2 待签字符串构造

待签字符串按以下顺序拼接（不包含分隔符）：

```
string_to_sign = policy_id + device_id + expire_ts + firmware_id
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `policy_id` | Long | 策略/任务 ID |
| `device_id` | Long | 设备自增 ID（非 IMEI） |
| `expire_ts` | Long | 过期时间戳（秒级 Unix 时间） |
| `firmware_id` | Long | 固件包 ID |

**示例**：

```
policy_id = 12345
device_id = 67890
expire_ts = 1709222400
firmware_id = 111

string_to_sign = "12345678901709222400111"
```

### 2.3 签名生成

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public static String generateSignature(String secretKey, long policyId,
                                       long deviceId, long expireTs, long firmwareId) {
    String stringToSign = String.format("%d%d%d%d", policyId, deviceId, expireTs, firmwareId);

    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
            secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);

        byte[] signatureBytes = mac.doFinal(
            stringToSign.getBytes(StandardCharsets.UTF_8));

        // URL-safe Base64 编码
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(signatureBytes);
    } catch (Exception e) {
        throw new RuntimeException("签名生成失败", e);
    }
}
```

### 2.4 签名编码

使用 **URL-safe Base64** 编码：

- 去除尾部 `=` 填充
- `+` 替换为 `-`
- `/` 替换为 `_`

**示例签名**：

```
原始签名 (Base64): "AbCdEf1234567890XyZ=="
URL-safe 签名:    "AbCdEf1234567890XyZ"
```

---

## 3. URL 参数规范

### 3.1 完整 URL 格式

```
https://cdn.example.com/firmware/{firmware_id}.bin?
  policy={policy_id}&
  device={device_id}&
  expire={expire_ts}&
  sig={signature}
```

### 3.2 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `policy` | Long | 是 | 策略 ID，用于审计和日志溯源 |
| `device` | Long | 是 | 设备自增 ID，用于防滥用统计 |
| `expire` | Long | 是 | 过期时间戳（秒），验证时检查 |
| `sig` | String | 是 | HMAC-SHA256 签名，URL-safe Base64 编码 |

### 3.3 URL 示例

```
https://cdn.example.com/firmware/111.bin?policy=12345&device=67890&expire=1709222400&sig=AbCdEf1234567890XyZ
```

---

## 4. 验证流程

### 4.1 验证步骤

```
1. 解析 URL 参数
   ├─ 提取 policy, device, expire, sig
   ├─ 从固件 ID 获取 firmware_id

2. 时效性检查
   ├─ 当前时间 > expire? → 拒绝 (403 Forbidden)

3. 重新计算签名
   ├─ 根据 secret_key 构造待签字符串
   ├─ HMAC-SHA256 计算签名

4. 签名比对
   ├─ 计算签名 == URL 中的 sig? → 通过 / 拒绝

5. 返回文件
   ├─ 签名有效 → 返回固件包
   ├─ 签名无效 → 返回 403 Forbidden
```

### 4.2 验证伪代码

```java
public boolean validateSignature(String url, String secretKey) {
    // 1. 解析参数
    SignedUrlParams params = parseUrl(url);
    long now = System.currentTimeMillis() / 1000;

    // 2. 时效性检查
    if (now > params.expire) {
        return false;
    }

    // 3. 重新计算签名
    String expectedSig = generateSignature(
        secretKey,
        params.policyId,
        params.deviceId,
        params.expire,
        params.firmwareId
    );

    // 4. 常量时间比较（防计时攻击）
    return MessageDigest.isEqual(
        expectedSig.getBytes(StandardCharsets.UTF_8),
        params.signature.getBytes(StandardCharsets.UTF_8)
    );
}
```

### 4.3 常量时间比较

使用 `MessageDigest.isEqual()` 进行签名比较，防止计时攻击：

```java
// 正确 ✅
boolean valid = MessageDigest.isEqual(
    expectedSig.getBytes(StandardCharsets.UTF_8),
    providedSig.getBytes(StandardCharsets.UTF_8)
);

// 错误 ❌
boolean valid = expectedSig.equals(providedSig);  // 易受计时攻击
```

---

## 5. 密钥管理方案

### 5.1 密钥存储

| 存储位置 | 说明 |
|----------|------|
| **数据库** | `products.signing_secret` 字段，每产品独立密钥 |
| **Redis 缓存** | `prod:secret:{product_id}`，TTL 1 小时 |

### 5.2 密钥格式

- **长度**: 32 字节 (256 位)
- **编码**: Base64 URL-safe
- **生成**: 安全随机数生成器

```java
import java.security.SecureRandom;
import java.util.Base64;

public static String generateSecretKey() {
    byte[] keyBytes = new byte[32];
    new SecureRandom().nextBytes(keyBytes);
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(keyBytes);
}
```

### 5.3 密钥轮换策略

#### 轮换触发条件

- 定期轮换：每 90 天
- 泄露事件：立即轮换
- 产品下线：密钥失效

#### 轮换流程

```
1. 生成新密钥
   ├─ secret_new = generateSecretKey()

2. 双写期 (24 小时)
   ├─ 旧密钥: secret_old (继续验证)
   ├─ 新密钥: secret_new (用于生成新 URL)
   ├─ 验证时: 尝试 secret_old → secret_new

3. 切换期
   ├─ 所有 URL 生成使用 secret_new
   ├─ 验证时仅使用 secret_new

4. 清理期
   ├─ 删除 secret_old
```

#### 双写期验证逻辑

```java
public boolean validateWithRotation(String url,
                                     String currentSecret,
                                     String oldSecret) {
    // 先尝试验证旧密钥（兼容性）
    if (validateSignature(url, oldSecret)) {
        return true;
    }
    // 再验证新密钥
    return validateSignature(url, currentSecret);
}
```

---

## 6. 安全考虑

### 6.1 防重放攻击

| 机制 | 说明 |
|------|------|
| **过期时间** | URL 默认 24 小时后失效 |
| **单次使用** | 可选：设备下载后立即标记 URL 已使用（Redis） |
| **设备绑定** | URL 绑定 device_id，无法跨设备共享 |

### 6.2 防篡改攻击

- URL 中任何参数被修改都会导致签名验证失败
- 签名覆盖所有关键参数（policy、device、expire、firmware_id）
- 使用 HMAC 算法，密钥不公开传输

### 6.3 防暴力破解

| 措施 | 说明 |
|------|------|
| **密钥强度** | 256 位随机密钥 |
| **签名长度** | 43 字符（URL-safe Base64） |
| **尝试限制** | 单 IP 每分钟最多 100 次验证失败（Redis） |

### 6.4 时间同步问题

- 使用 Unix 时间戳（秒级），容忍 ±30 秒时钟偏差
- 客户端和服务器时间不同步导致的过期问题由客户端重试处理

---

## 7. 性能优化

### 7.1 签名生成优化

- **密钥缓存**: Redis 缓存产品密钥，避免频繁查询数据库
- **线程安全**: `Mac` 实例线程不安全，每次使用创建新实例或使用 ThreadLocal

### 7.2 签名验证优化

- **短路径校验**: 先检查时效性（简单比较），再计算签名
- **CDN 边缘验证**: 使用 CDN 边缘计算功能，回源前验证签名
- **失败限流**: 验证失败时记录 IP，超过阈值返回 429

---

## 8. 错误处理

### 8.1 错误码定义

| 错误码 | HTTP 状态 | 说明 |
|--------|-----------|------|
| `URL_EXPIRED` | 403 | URL 已过期 |
| `INVALID_SIGNATURE` | 403 | 签名验证失败 |
| `MISSING_PARAMS` | 400 | 缺少必需参数 |
| `FIRMWARE_NOT_FOUND` | 404 | 固件包不存在 |

### 8.2 错误响应格式

```json
{
  "error": "URL_EXPIRED",
  "message": "下载链接已过期，请重新检查更新",
  "expire_at": "2026-02-28T12:00:00Z"
}
```

---

## 9. 监控与审计

### 9.1 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| 签名验证失败率 | 失败次数 / 总次数 | > 1% |
| URL 过期率 | 过期请求 / 总请求 | > 5% |
| 签名生成耗时 | P99 延迟 | > 10ms |
| 密钥轮换状态 | 当前密钥年龄 | > 90 天 |

### 9.2 审计日志

每次签名验证记录以下信息到 ClickHouse：

```sql
CREATE TABLE download_url_events (
    timestamp DateTime,
    product_id UInt64,
    policy_id UInt64,
    device_id UInt64,
    firmware_id UInt64,
    success Bool,
    failure_reason String,
    client_ip String,
    user_agent String
) ENGINE = MergeTree()
ORDER BY (timestamp, product_id);
```

---

## 10. 实现检查清单

### 10.1 签名生成

- [ ] 实现 `SignedUrlService.generateUrl()`
- [ ] 集成 RustFS/S3 Pre-signed URL 生成
- [ ] 密钥从 Redis/数据库加载
- [ ] 单元测试覆盖所有签名场景

### 10.2 签名验证

- [ ] 实现 CDN 边缘验证（如支持）
- [ ] 实现服务端验证降级
- [ ] 常量时间比较实现
- [ ] 验证失败限流

### 10.3 密钥管理

- [ ] 密钥生成接口
- [ ] 密钥轮换定时任务
- [ ] 双写期验证逻辑
- [ ] 密钥过期告警

---

## 11. 附录

### 11.1 测试向量

用于验证实现的正确性：

| 测试用例 | secret_key | string_to_sign | 期望签名 |
|----------|------------|----------------|----------|
| 用例 1 | `test_secret_key_12345678901234567890` | `12345678901709222400111` | `fRz8vN3kXqA7mP2wL6hJ9cR4dS1uY5tB8` |
| 用例 2 | `another_secret_key_for_testing_purposes` | `99998888170931312000222` | `aB3cX7yZ9qW2eR5tT8uV1pL4kJ7hG0nM` |

> **注意**: 以上为示例值，实际实现时需使用真实测试向量

### 11.2 参考文档

- [RFC 2104: HMAC](https://datatracker.ietf.org/doc/html/rfc2104)
- [RFC 4648: Base64](https://datatracker.ietf.org/doc/html/rfc4648)
- [AWS S3 Signature V2](https://docs.aws.amazon.com/AmazonS3/latest/userguide/RESTAuthentication.html)
- [RFC 9110: HTTP Semantics](https://datatracker.ietf.org/doc/html/rfc9110)

---

## 变更日志

### v1.0 (2026-02-28)
- 初始版本
- 定义签名算法和 URL 参数规范
- 定义密钥管理和验证流程
- 定义安全和性能考虑
