# 设备升级上报 API 规范

> **版本**: v1.1
> **最后更新**: 2026-03-02
> **关联 PRD**: [产品需求文档](../01-product/prd.md)
> **关联文档**: [升级检查 API](./upgrade-check-api.md)

---

## 概述

本文档定义设备升级上报 API 的请求和响应格式规范。

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/upgrade/report` | POST | 设备升级状态上报 |

### 设计原则

1. **发后即忘**: API 接收请求后立即返回 200 OK，实际处理通过 RabbitMQ 异步完成
2. **链路追踪**: 通过 `request_id` 关联检查请求和上报事件
3. **异步处理**: 请求通过 RabbitMQ 异步写入 ClickHouse

---

## 请求参数

### 请求体字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `imei` | String | ✅ | 设备 IMEI（15位数字字符串） |
| `request_id` | String | ✅ | **链路追踪 ID**（从 Check 响应获取） |
| `event` | Integer | ✅ | 事件类型代码（0-4，见下表） |
| `details` | Object | ❌ | 扩展详情（错误码、进度等） |

### 事件类型枚举 (event)

> **注意**: `event` 字段使用**数字代码**（0-4），不是字符串

| code | 枚举 | 说明 | 触发时机 |
|------|------|------|------|
| 0 | DL_START | 开始下载 | 设备开始下载固件 |
| 1 | DL_OK | 下载成功 | 设备下载固件完成 |
| 2 | DL_FAIL | 下载失败 | 设备下载固件失败 |
| 3 | UP_OK | 升级成功 | 设备安装固件成功 |
| 4 | UP_FAIL | 升级失败 | 设备安装固件失败 |

### details 字段结构

`details` 是一个 JSON 对象，可包含以下可选字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `progress` | Integer | 下载/升级进度 (0-100) |
| `error_code` | String | 错误码 |
| `error_message` | String | 错误信息 |
| `device_time` | String | 设备端时间 (ISO 8601) |
| `retry_count` | Integer | 重试次数 |
| `bytes_downloaded` | Long | 已下载字节数 |
| `duration_ms` | Long | 操作耗时（毫秒） |

---

## 链路追踪设计

### request_id 的作用
`request_id` 是链路追踪的核心字段，用于：
1. 关联设备检查请求（`/v1/upgrade/check`）和上报事件
2. 从 `device_check_logs` 表获取设备信息（device_id、product_id 等）
3. 支持端到端的问题排查和性能分析

### 数据流转
```
Check 响应 (request_id)
       ↓
设备存储 request_id
       ↓
Report 请求携带 request_id
       ↓
通过 request_id 关联 device_check_logs
       ↓
获取设备完整信息
```

---

## 请求示例

### 下载开始

```bash
curl -X POST http://localhost:8080/v1/upgrade/report \
  -H "Content-Type: application/json" \
  -d '{
    "imei": "861234567890123",
    "request_id": "550e8400e29b41d4a716446655440000",
    "event": 0
  }'
```

### 下载成功

```bash
curl -X POST http://localhost:8080/v1/upgrade/report \
  -H "Content-Type: application/json" \
  -d '{
    "imei": "861234567890123",
    "request_id": "550e8400e29b41d4a716446655440000",
    "event": 1,
    "details": {
      "bytes_downloaded": 20000000,
      "duration_ms": 45000
    }
  }'
```

### 下载失败

```bash
curl -X POST http://localhost:8080/v1/upgrade/report \
  -H "Content-Type: application/json" \
  -d '{
    "imei": "861234567890123",
    "request_id": "550e8400e29b41d4a716446655440000",
    "event": 2,
    "details": {
      "error_code": "NETWORK_TIMEOUT",
      "error_message": "Connection timed out after 30s",
      "retry_count": 3
    }
  }'
```

### 升级成功

```bash
curl -X POST http://localhost:8080/v1/upgrade/report \
  -H "Content-Type: application/json" \
  -d '{
    "imei": "861234567890123",
    "request_id": "550e8400e29b41d4a716446655440000",
    "event": 3,
    "details": {
      "duration_ms": 120000
    }
  }'
```

### 升级失败

```bash
curl -X POST http://localhost:8080/v1/upgrade/report \
  -H "Content-Type: application/json" \
  -d '{
    "imei": "861234567890123",
    "request_id": "550e8400e29b41d4a716446655440000",
    "event": 4,
    "details": {
      "error_code": "VERIFY_FAILED",
      "error_message": "Checksum verification failed"
    }
  }'
```

---

## 响应格式

### 成功响应

```json
{
  "code": 200,
  "message": "success"
}
```

> **说明**: 上报接口采用"发后即忘"模式，只要请求格式正确即返回成功，实际处理异步完成。

### 错误响应

```json
{
  "code": 400,
  "message": "参数校验失败: imei 不能为空"
}
```

---

## 实现类图

```
┌─────────────────────────────────────────────────────────────────────┐
│  Adapter/API 层 (adapter/api/device)                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeReportController                                     │   │
│  │  - 接收 POST /v1/upgrade/report 请求                         │   │
│  │  - 参数校验 (@Valid)                                         │   │
│  │  - 序列化 details 为 JSON                                    │   │
│  │  - 调用 AppService 转换并处理                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                                   ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Application 层 (application/reporting)                             │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeReportAppService                                     │   │
│  │  - toDomain(): DTO → 领域模型                                │   │
│  │  - reportUpgrade(): 调用 Gateway                             │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeReportDTO (请求 DTO)                                 │   │
│  │  @NotBlank String imei;                                      │   │
│  │  @NotBlank String requestId;                                 │   │
│  │  @NotNull Integer event;                                     │   │
│  │  Map<String, Object> details;                                │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                                   ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Domain 层 (domain/reporting)                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeReport (领域模型)                                    │   │
│  │  String imei;                                                │   │
│  │  String requestId;                                           │   │
│  │  DeviceUpgradeEventType event;                               │   │
│  │  String detailsJson;  // 已序列化                            │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  DeviceUpgradeEventType (枚举)                               │   │
│  │  DL_START(0), DL_OK(1), DL_FAIL(2), UP_OK(3), UP_FAIL(4)     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  UpgradeReportGateway (接口)                                 │   │
│  │  - accept(UpgradeReport)                                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                                   │
                                   ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Infrastructure 层 (infra/gateway)                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  RabbitMqUpgradeReportGateway                                │   │
│  │  - 构建 DeviceUpgradeEvent                                   │   │
│  │  - 发送到 RabbitMQ                                           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  DeviceUpgradeEventAppService (消费者)                       │   │
│  │  - 批量消费消息                                              │   │
│  │  - 写入 ClickHouse                                           │   │
│  │  - UP_OK 时更新 PostgreSQL 设备版本                          │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 文件位置

| 文件 | 路径 |
|------|------|
| UpgradeReportController | `adapter/api/device/UpgradeReportController.java` |
| UpgradeReportDTO | `adapter/api/device/dto/UpgradeReportDTO.java` |
| UpgradeReport | `domain/reporting/model/UpgradeReport.java` |
| DeviceUpgradeEventType | `domain/reporting/model/value/DeviceUpgradeEventType.java` |
| UpgradeReportGateway | `domain/reporting/service/UpgradeReportGateway.java` |
| DeviceUpgradeEvent | `domain/reporting/model/aggregate/DeviceUpgradeEvent.java` |
| RabbitMqUpgradeReportGateway | `infra/gateway/RabbitMqUpgradeReportGateway.java` |
| UpgradeReportAppService | `application/reporting/UpgradeReportAppService.java` |

---

## 数据关联

### 通过 request_id 关联数据

```
device_check_logs                    device_upgrade_events
┌─────────────────────┐              ┌─────────────────────┐
│ request_id (PK)     │◄────────────│ request_id (FK)     │
│ device_id           │              │ event_id (PK)       │
│ product_id          │              │ imei                │
│ policy_id           │              │ event_type          │
│ download_url        │              │ details             │
│ ...                 │              │ ...                 │
└─────────────────────┘              └─────────────────────┘
```

> **设计原则**: `device_upgrade_events` 表通过 `request_id` 关联 `device_check_logs`，> 无需存储冗余的设备信息（device_id、product_id 等），需要时可 JOIN 查询获取。

---

## 变更日志

### 2026-03-02 (v1.1)
- **重要变更**: 用 `request_id` 替换 `url` 字段用于链路追踪
- **重要变更**: `event` 字段改为数字类型（0-4）
- 移除 URL 溯源参数说明（不再从 URL 解析 pid/rid）
- 简化数据模型，通过 request_id 关联获取设备信息

### 2026-03-01 (v1.0)
- 初版：定义 API 请求和响应格式
- 字段与 PRD 定义对齐
- 新增 `UP_FAIL` 事件类型
