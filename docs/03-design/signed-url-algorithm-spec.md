# 签名下载 URL 算法规范

> **版本**: v1.0
> **创建日期**: 2026-02-28
> **作者**: FOTA Team

---

## 1. 概述

本文档定义 FOTA 系统中固件下载 URL 的签名算法规范。签名 URL 用于：

- **溯源**: 记录 policy_id、device_id、timestamp 等信息
- **安全**: 通过 HMAC-SHA256 签名防止 URL 被伪造
- **过期控制**: 设置 URL 有效期，限制下载时间窗口

---

## 2. URL 格式

### 2.1 完整 URL 结构

```
{cdnBaseUrl}/{firmwarePath}?policy={policyId}&device={deviceId}&expire={expireTime}&sig={signature}
```

### 2.2 URL 参数说明

| 参数 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `cdnBaseUrl` | String | CDN 基础地址 | `https://cdn.example.com` |
| `firmwarePath` | String | 固件在对象存储中的路径 | `fota/fw/123/uuid.zip` |
| `policy` | Long | 升级策略 ID（用于溯源） | `100` |
| `device` | Long | 设备 ID（用于溯源） | `1000` |
| `expire` | Long | 过期时间戳（Unix 秒） | `1709222400` |
| `sig` | String | HMAC-SHA256 签名（64 位十六进制） | `a1b2c3d4e5f6...` |

### 2.3 URL 示例

```
https://cdn.example.com/fota/fw/123/550e8400-e29b-41d4-a716-446655440000.zip
  ?policy=100
  &device=1000
  &expire=1709222400
  &sig=3a7b8f9c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8
```

---

## 3. 签名算法

### 3.1 签名载荷构建

签名载荷格式（使用 `|` 分隔符）：

```
{firmwarePath}|{policyId}|{deviceId}|{expireTime}
```

**示例**：
```
fota/fw/123/550e8400-e29b-41d4-a716-446655440000.zip|100|1000|1709222400
```

### 3.2 HMAC-SHA256 签名生成

1. **算法**: HMAC-SHA256
2. **密钥**: 配置的 `secret-key`（至少 32 字节）
3. **输入**: 签名载荷字符串（UTF-8 编码）
4. **输出**: 256 位哈希值，转换为 64 位十六进制字符串（小写）

### 3.3 Java 实现示例

```java
private String hmacSha256(String payload) {
    String secretKey = properties.getSecretKey();

    Mac mac = Mac.getInstance("HmacSHA256");
    SecretKeySpec secretKeySpec = new SecretKeySpec(
        secretKey.getBytes(StandardCharsets.UTF_8),
        "HmacSHA256"
    );
    mac.init(secretKeySpec);
    byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

    return bytesToHex(hash);
}

private String bytesToHex(byte[] bytes) {
    StringBuilder hexString = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
            hexString.append('0');
        }
        hexString.append(hex);
    }
    return hexString.toString();
}
```

### 3.4 签名验证流程

1. 检查 `expire` 参数是否大于当前时间
2. 使用相同参数重新构建签名载荷
3. 使用相同的密钥计算 HMAC-SHA256
4. 比对计算出的签名与 URL 中的签名是否一致

---

## 4. 安全要求

### 4.1 密钥管理

- 密钥长度至少 **32 字节**（256 位）
- 密钥应通过环境变量或配置中心注入，**不应硬编码**
- 密钥应定期轮换（建议每 90 天）
- 不同环境（dev/test/prod）使用不同密钥

### 4.2 过期时间限制

- 最小过期时间: **1 秒**
- 最大过期时间: **604800 秒**（7 天）
- 默认过期时间: **86400 秒**（24 小时）

### 4.3 参数校验

- `firmwarePath`: 非空字符串
- `policyId`: 正整数
- `deviceId`: 正整数
- `expireTime`: 当前时间 + [1, 604800] 秒

---

## 5. 错误处理

### 5.1 参数验证失败

| 错误场景 | HTTP 状态码 | 错误信息 |
|----------|-------------|----------|
| firmwarePath 为空 | 400 | `firmwarePath 不能为空` |
| policyId 无效 | 400 | `policyId 必须为正数` |
| deviceId 无效 | 400 | `deviceId 必须为正数` |
| expireSeconds 超出范围 | 400 | `expireSeconds 必须在 1-604800 秒之间` |
| 密钥未配置 | 500 | `签名密钥未配置` |

### 5.2 签名验证失败

| 错误场景 | HTTP 状态码 | 处理方式 |
|----------|-------------|----------|
| 签名不匹配 | 403 | 拒绝下载，返回 `FORBIDDEN` |
| URL 已过期 | 403 | 拒绝下载，返回 `URL_EXPIRED` |

---

## 6. 配置示例

### 6.1 application.yml

```yaml
app:
  firmware:
    signed-url:
      # 签名密钥（生产环境必须通过环境变量注入）
      secret-key: ${FOTA_SIGNED_URL_SECRET_KEY}
      # 默认过期时间（秒）
      default-expire-seconds: 86400
      # CDN 基础 URL
      cdn-base-url: https://cdn.example.com
      # 是否启用签名（测试环境可禁用）
      enabled: true
```

### 6.2 环境变量

```bash
export FOTA_SIGNED_URL_SECRET_KEY="your-secret-key-at-least-32-bytes-long"
```

---

## 7. 溯源用途

生成的 URL 包含的溯源信息可用于：

1. **下载日志分析**: 记录哪个设备通过哪个策略下载了固件
2. **费用分摊**: 根据策略 ID 统计不同产品线的下载流量
3. **安全审计**: 追踪异常下载行为

---

## 8. 实现文件

| 文件 | 说明 |
|------|------|
| `SignedUrlService.java` | 签名服务接口 |
| `SignedUrlServiceImpl.java` | 签名服务实现 |
| `SignedUrlProperties.java` | 配置属性类 |
| `SignedUrlServiceImplTest.java` | 单元测试 |

---

## 9. 变更历史

| 版本 | 日期 | 变更内容 | 作者 |
|------|------|----------|------|
| v1.0 | 2026-02-28 | 初始版本 | FOTA Team |
