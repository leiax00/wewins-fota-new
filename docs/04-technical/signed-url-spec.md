# 签名下载 URL 技术规范

> **版本**: v2.0
> **创建日期**: 2026-02-28
> **最后更新**: 2026-03-01
> **作者**: FOTA 后端组
> **状态**: 已实现

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

### 1.3 签名模式

系统支持两种签名模式，通过配置 `app.firmware.download.sign-mode` 切换：

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| `self-signed` | HMAC-SHA256 自签名 | CDN 分发、需要服务端验证 |
| `s3-presigned` | S3 SDK 预签名 | 直连 S3 存储 |

---

## 2. 签名算法

### 2.1 自签名模式 (self-signed)

采用 **HMAC-SHA256** 签名算法：

```
signature = HMAC-SHA256(secret_key, string_to_sign)
```

#### 2.1.1 待签字符串构造

待签字符串按以下顺序拼接（使用 `|` 分隔）：

```
string_to_sign = firmware_path|policy_id|device_id|expire_ts
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `firmware_path` | String | 固件文件路径 |
| `policy_id` | Long | 策略/任务 ID |
| `device_id` | Long | 设备自增 ID（非 IMEI） |
| `expire_ts` | Long | 过期时间戳（秒级 Unix 时间） |

**示例**：

```
firmware_path = "fota/fw/1/test.bin"
policy_id = 12345
device_id = 67890
expire_ts = 1709222400

string_to_sign = "fota/fw/1/test.bin|12345|67890|1709222400"
```

### 2.2 S3 预签名模式 (s3-presigned)

使用 AWS SDK 的 `presignGetObject` 生成预签名 URL，溯源参数通过 `overrideConfiguration` 加入签名计算。

---

## 3. URL 参数规范

### 3.1 完整 URL 格式

```
https://cdn.example.com/firmware/{firmware_path}?
  pid={policy_id}&
  did={device_id}&
  expire={expire_ts}&
  sig={signature}
```

### 3.2 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pid` | Long | 是 | 策略 ID，用于审计和日志溯源 |
| `did` | Long | 是 | 设备自增 ID，用于防滥用统计 |
| `expire` | Long | 是 | 过期时间戳（秒），验证时检查 |
| `sig` | String | 是 | HMAC-SHA256 签名，十六进制编码 |

### 3.3 URL 示例

**自签名模式**：
```
https://cdn.example.com/fota/fw/1/test.bin?pid=12345&did=67890&expire=1709222400&sig=abc123def456...
```

**S3 预签名模式**：
```
https://bucket.s3.amazonaws.com/fota/fw/1/test.bin?
  pid=12345&
  did=67890&
  X-Amz-Algorithm=AWS4-HMAC-SHA256&
  X-Amz-Credential=...&
  X-Amz-Date=...&
  X-Amz-Expires=3600&
  X-Amz-Signature=...
```

---

## 4. 配置规范

### 4.1 配置结构

```yaml
app:
  firmware:
    download:
      # 签名模式：self-signed 或 s3-presigned
      sign-mode: self-signed

      # RustFS/CDN 基础域名（自签名模式使用）
      base-url: https://rustfs.example.com

      # 自签名配置
      self-signed:
        secret-key: ${FIRMWARE_SIGN_SECRET}
        default-expire-seconds: 86400

      # S3 预签名配置
      s3-presigned:
        default-expire-seconds: 3600
```

### 4.2 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `sign-mode` | `self-signed` | 签名模式 |
| `base-url` | S3 endpoint | 下载 URL 基础域名 |
| `self-signed.secret-key` | - | HMAC 签名密钥（至少 32 字节） |
| `self-signed.default-expire-seconds` | 86400 | 默认过期时间（24 小时） |
| `s3-presigned.default-expire-seconds` | 3600 | S3 预签名过期时间（1 小时） |

---

## 5. 实现类

### 5.1 类图

```
┌─────────────────────────────────────────────────────────────────────┐
│  SignedUrlService (接口)                                            │
│  - generateSignedUrl(firmwarePath, policyId, deviceId)              │
│  - verifySignature(firmwarePath, policyId, deviceId, expire, sig)   │
└─────────────────────────────────────────────────────────────────────┘
                                   △
                                   │
              ┌────────────────────┴────────────────────┐
              │                                         │
┌─────────────────────────────────┐   ┌─────────────────────────────────┐
│  SelfSignedUrlServiceImpl       │   │  S3PresignedUrlServiceImpl      │
│  - HMAC-SHA256 签名              │   │  - S3 SDK presignGetObject      │
│  - 使用 baseUrl 拼接完整 URL     │   │  - 溯源参数纳入签名计算          │
└─────────────────────────────────┘   └─────────────────────────────────┘
```

### 5.2 文件位置

| 文件 | 路径 |
|------|------|
| SignedUrlService | `application/firmware/download/SignedUrlService.java` |
| FirmwareDownloadProperties | `application/firmware/download/FirmwareDownloadProperties.java` |
| SelfSignedUrlServiceImpl | `application/firmware/download/impl/SelfSignedUrlServiceImpl.java` |
| S3PresignedUrlServiceImpl | `application/firmware/download/impl/S3PresignedUrlServiceImpl.java` |
| SignedUrlServiceConfiguration | `application/firmware/download/config/SignedUrlServiceConfiguration.java` |

---

## 6. 验证流程

### 6.1 验证步骤

```
1. 解析 URL 参数
   ├─ 提取 pid, did, expire, sig

2. 时效性检查
   ├─ 当前时间 > expire? → 拒绝 (403 Forbidden)

3. 签名验证
   ├─ 自签名模式：重新计算签名并比对
   ├─ S3 预签名模式：由 S3 服务端验证

4. 返回文件
   ├─ 签名有效 → 返回固件包
   ├─ 签名无效 → 返回 403 Forbidden
```

---

## 7. 安全考虑

### 7.1 防重放攻击

| 机制 | 说明 |
|------|------|
| **过期时间** | URL 默认 24 小时后失效 |
| **设备绑定** | URL 绑定 device_id，无法跨设备共享 |

### 7.2 防篡改攻击

- URL 中任何参数被修改都会导致签名验证失败
- 签名覆盖所有关键参数（pid、did、expire、firmware_path）
- 使用 HMAC 算法，密钥不公开传输

### 7.3 密钥安全

- **长度**: 至少 32 字节 (256 位)
- **存储**: 环境变量或密钥管理服务
- **轮换**: 支持定期轮换

---

## 变更日志

### v2.0 (2026-03-01)
- 重构签名模式，支持 self-signed 和 s3-presigned 两种模式
- 溯源参数改为 pid（策略ID）和 did（设备ID）
- S3 预签名模式支持将溯源参数纳入签名计算
- 统一配置到 `app.firmware.download.*`

### v1.0 (2026-02-28)
- 初始版本
- 定义签名算法和 URL 参数规范
- 定义密钥管理和验证流程
