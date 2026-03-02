# 设备升级检查 API 规范

> **版本**: v1.1
> **最后更新**: 2026-03-02
> **关联 PRD**: [产品需求文档](../01-product/prd.md)

---

## 概述

本文档定义设备升级检查 API 的请求和响应格式规范。

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/upgrade/check` | GET/POST | 设备升级检查（新标准路径） |
| `/fota/version/query` | GET | 设备升级检查（兼容老设备） |

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
| `request_id` | String | **链路追踪 ID**（设备需在后续上报中携带此 ID） |
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

> **重要**: `request_id` 字段用于链路追踪，设备需在后续的升级上报请求中携带此 ID，> 以便关联检查请求和上报事件。

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

> **注意**: 所有响应（无论成功还是失败）都会包含 `request_id` 字段。

### 有更新 (code=0)

```json
{
   "code": 0,
   "request_id": "550e8400e29b41d4a716446655440000",
   "release_start_date": "2026-02-03T10:28:56Z",
   "release_note": "修复Bug并改进性能",
   "new_firmware": "v2.0.0",
   "download_url": "https://cdn.xxx.com/pkg.bin?sig=xxx",
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
   "request_id": "550e8400e29b41d4a716446655440001",
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
   "request_id": "550e8400e29b41d4a716446655440002",
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
   "request_id": "550e8400e29b41d4a716446655440003",
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
   "request_id": "550e8400e29b41d4a716446655440004",
   "control": {
      "check_interval": 3600,
      "download_delay": 0
   }
}
```

---

## 错误处理

> **重要**: 即使发生错误，响应也会包含 `request_id` 字段，用于问题排查和链路追踪。

---

## 变更日志

### 2026-03-02 (v1.1)
- **重要变更**: 响应新增 `request_id` 字段用于链路追踪
- 设备需在后续上报请求中携带此 ID

### 2026-03-01 (v1.0)
- 初版：定义 API 请求和响应格式
- 字段与 PRD 定义对齐
