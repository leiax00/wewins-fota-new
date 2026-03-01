# 设备升级检查 API 规范

> **版本**: v1.0
> **最后更新**: 2026-03-01
> **关联 PRD**: [产品需求文档](../01-product/prd.md)

---

## 概述

本文档定义设备升级检查 API 的请求和响应格式规范。

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/upgrade/check` | GET/POST | 设备升级检查（新标准路径） |
| `/fota/version/query` | GET/POST | 设备升级检查（兼容老设备） |

> **说明**: 两个端点使用相同的参数和业务逻辑，返回格式一致。

---

## 请求参数

### 必填参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `product` | String | 产品型号（对应 Product.model） |
| `imei` | String | 设备 IMEI（15位数字） |
| `version` | String | 当前固件版本号 |

### 可选参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `tag` | String | - | 设备内部版本号（build tag） |
| `auto` | Integer | 1 | 触发模式：0=手动, 1=自动 |
| `lang` | String | en | 语言代码（en/zh 等） |
| `dev` | Integer | 0 | 临时测试设备标识：1=测试设备 |

### 请求示例

```bash
GET /v1/upgrade/check?product=asr_yemen_m476_vsim&imei=354972069009027&version=Mobile.Router.B03&auto=0&lang=en&tag=ASR_YEMEN_M476_V11_B03&dev=1
```

---

## 响应格式

### 响应字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 决策码（见下表） |
| `release_start_date` | String | 发布开始日期（ISO 8601） |
| `release_note` | String | 发布说明 |
| `new_firmware` | String | 新固件版本号 |
| `download_url` | String | 签名下载 URL |
| `file_size` | Long | 文件大小（字节） |
| `file_size_text` | String | 文件大小文本（如 "19MB"） |
| `checksum` | String | 校验和 |
| `checksum_type` | String | 校验和类型（sha256/md5） |
| `control` | Object | 控制参数 |
| `control.check_interval` | Integer | 下次检查间隔（秒） |
| `control.download_delay` | Integer | 下载延迟（秒） |

### 决策码 (code)

| code | 枚举 | 含义 | 说明 |
|------|------|------|------|
| 0 | UPDATE | 有可用更新 | 设备应下载并安装固件 |
| 1 | NO_UPDATE | 无更新 | 设备当前已是最新版本 |
| 2 | RATE_LIMITED | 请求被限流 | 请求过于频繁，需等待后重试 |
| 3 | DEVICE_NOT_FOUND | 设备不存在 | 设备未在系统中注册 |
| 4 | ERROR | 错误 | 处理过程中发生错误 |

---

## 响应示例

### 有更新 (code=0)

```json
{
   "code": 0,
   "release_start_date": "2026-02-03T10:28:56Z",
   "release_note": "修复Bug并改进性能",
   "new_firmware": "v2.0.0",
   "download_url": "https://cdn.xxx.com/pkg.bin?pid=101&did=1001&expire=1709222400&sig=xxx",
   "file_size": 20000000,
   "file_size_text": "19.1MB",
   "checksum": "a1b2c3d4e5f6...",
   "checksum_type": "sha256",
   "control": {
      "check_interval": 86400,
      "download_delay": 300
   }
}
```

### 无更新 (code=1)

```json
{
   "code": 1,
   "control": {
      "check_interval": 86400,
      "download_delay": 0
   }
}
```

### 请求被限流 (code=2)

```json
{
   "code": 2,
   "control": {
      "check_interval": 60,
      "download_delay": 60
   }
}
```

### 设备不存在 (code=3)

```json
{
   "code": 3,
   "control": {
      "check_interval": 3600,
      "download_delay": 0
   }
}
```

### 错误 (code=4)

```json
{
   "code": 4,
   "control": {
      "check_interval": 3600,
      "download_delay": 0
   }
}
```

---

## 设备端处理建议

### 根据 code 的处理逻辑

```
if (code == 0) {
    // 有更新
    sleep(control.download_delay);  // 随机延迟下载
    download(download_url);
    verifyChecksum(checksum, checksum_type);
    install();
    scheduleNextCheck(control.check_interval);
} else if (code == 1) {
    // 无更新
    scheduleNextCheck(control.check_interval);
} else if (code == 2) {
    // 被限流
    scheduleNextCheck(control.check_interval);  // 使用返回的间隔
} else if (code == 3) {
    // 设备未注册
    log("设备未注册，请联系管理员");
    scheduleNextCheck(control.check_interval);
} else if (code == 4) {
    // 错误
    log("服务器错误");
    scheduleNextCheck(control.check_interval);
}
```

---

## 实现类

### 类图

```
┌─────────────────────────────────────────────────────────────────────┐
│  Adapter/API 层 (adapter/api/device)                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeCheckController                                      │   │
│  │  - 接收请求，调用 Service                                     │   │
│  │  - 将 CheckResult 转换为 UpgradeCheckRespDTO                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│  ┌────────────────────────────┴────────────────────────────────┐   │
│  │  UpgradeDecision (枚举)                                      │   │
│  │  UPDATE(0), NO_UPDATE(1), RATE_LIMITED(2),                   │   │
│  │  DEVICE_NOT_FOUND(3), ERROR(4)                               │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeCheckRespDTO (响应 DTO)                              │   │
│  │  - 严格按本文档定义，字段使用 snake_case                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                                   ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Application 层 (application/upgrade)                               │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeCheckService                                         │   │
│  │  - 业务逻辑：限流、策略匹配、灰度检查等                        │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  CheckResult (内部 DTO)                                      │   │
│  │  - 包含业务决策信息和固件元数据                               │   │
│  │  - decision 字段使用 UpgradeDecision 枚举                    │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 文件位置

| 文件 | 路径 |
|------|------|
| UpgradeDecision | `adapter/api/device/dto/UpgradeDecision.java` |
| UpgradeCheckRespDTO | `adapter/api/device/dto/UpgradeCheckRespDTO.java` |
| UpgradeCheckReqDTO | `application/upgrade/dto/UpgradeCheckReqDTO.java` |
| CheckResult | `application/upgrade/dto/CheckResult.java` |
| UpgradeCheckController | `adapter/api/device/UpgradeCheckController.java` |
| UpgradeCheckService | `application/upgrade/UpgradeCheckService.java` |

---

## 变更日志

### 2026-03-01 (v1.0)
- 初版：定义 API 请求和响应格式
- 新增 `code` 决策码字段
- 统一使用 snake_case 命名风格
