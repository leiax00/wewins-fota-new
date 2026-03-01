# 设备升级上报 API 规范

> **版本**: v1.0
> **最后更新**: 2026-03-02
> **关联 PRD**: [产品需求文档](../01-product/prd.md)
> **关联 Sprint**: [Sprint 3](../05-plans/sprint-3-core-pipeline.md)

---

## 概述

本文档定义设备升级上报 API 的请求和响应格式规范。

设备在升级过程的关键节点通过此 API 上报状态，用于：
- 实时监控升级进度
- 统计升级成功率
- 分析失败原因
- 追踪固件下载溯源

### API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/v1/upgrade/report` | POST | 设备升级状态上报 |

### 设计原则

1. **发后即忘**: API 接收请求后立即返回 200 OK，不等待数据处理
2. **异步处理**: 请求通过 RabbitMQ 异步写入 ClickHouse
3. **尽力而为**: 即使消息队列不可用，也不影响设备端

---

## 请求参数

### 请求体字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `imei` | String | ✅ | 设备 IMEI（15位数字字符串） |
| `event` | String | ✅ | 事件类型（见事件类型枚举） |
| `url` | String | ✅ | 下载 URL（包含 pid, rid 参数用于溯源） |
| `details` | Object | ❌ | 扩展详情（错误码、进度等） |

### 事件类型枚举 (event)

| 值 | 说明 | 触发时机 |
|------|------|------|
| `DL_START` | 开始下载 | 设备开始下载固件 |
| `DL_OK` | 下载成功 | 设备下载固件完成 |
| `DL_FAIL` | 下载失败 | 设备下载固件失败 |
| `UP_OK` | 升级成功 | 设备安装固件成功 |
| `UP_FAIL` | 升级失败 | 设备安装固件失败 |

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

## 请求示例

### 下载开始

```bash
curl -X POST http://localhost:8080/v1/upgrade/report \
  -H "Content-Type: application/json" \
  -d '{
    "imei": "861234567890123",
    "event": "DL_START",
    "url": "https://cdn.example.com/firmware.bin?pid=101&rid=abc123"
  }'
```

### 下载成功

```bash
curl -X POST http://localhost:8080/v1/upgrade/report \
  -H "Content-Type: application/json" \
  -d '{
    "imei": "861234567890123",
    "event": "DL_OK",
    "url": "https://cdn.example.com/firmware.bin?pid=101&rid=abc123",
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
    "event": "DL_FAIL",
    "url": "https://cdn.example.com/firmware.bin?pid=101&rid=abc123",
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
    "event": "UP_OK",
    "url": "https://cdn.example.com/firmware.bin?pid=101&rid=abc123",
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
    "event": "UP_FAIL",
    "url": "https://cdn.example.com/firmware.bin?pid=101&rid=abc123",
    "details": {
      "error_code": "CHECKSUM_MISMATCH",
      "error_message": "SHA256 verification failed"
    }
  }'
```

---

## 响应格式

### 成功响应

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{}
```

> **说明**: 上报成功时返回空对象，设备端无需处理响应内容。

### 错误响应

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json
```

```json
{
  "message": "Invalid request: imei is required"
}
```

---

## 服务端处理流程

```
设备上报请求
     │
     ↓
┌─────────────────────────────────────┐
│  UpgradeReportController            │
│  - 接收请求                          │
│  - 参数校验（imei、event、url 必填） │
│  - 构建 UpgradeReport 领域模型       │
└─────────────────────────────────────┘
     │
     ↓
┌─────────────────────────────────────┐
│  UpgradeReportAppService            │
│  - 调用 Gateway 处理上报             │
└─────────────────────────────────────┘
     │
     ↓
┌─────────────────────────────────────┐
│  RabbitMqUpgradeReportGateway       │
│  - 解析 URL 中的 pid/rid 参数        │
│  - 构建 DeviceUpgradeEvent          │
│  - 发送到 RabbitMQ                   │
│  - 立即返回                          │
└─────────────────────────────────────┘
     │
     ↓ (异步)
┌─────────────────────────────────────┐
│  UpgradeReportConsumer              │
│  - 批量消费消息                      │
│  - 写入 ClickHouse                  │
│  - UP_OK 时更新 PostgreSQL 设备版本 │
└─────────────────────────────────────┘
```

---

## URL 溯源参数

下载 URL 中包含以下查询参数用于溯源：

| 参数 | 说明 | 示例 |
|------|------|------|
| `pid` | 策略 ID (Policy ID) | `101` |
| `rid` | 请求 ID (Request ID) | `abc123-def456` |

这些参数由服务端在 `/v1/upgrade/check` 响应中生成，设备端应原样上报。

### URL 解析逻辑

```java
// 从 URL 中解析 rid 参数
private String extractRequestId(String url) {
    // 解析 ?rid=xxx 中的值
    // 返回 requestId，用于关联 device_check_logs.request_id
}

// 从 URL 中解析 pid 参数
private Long extractPolicyId(String url) {
    // 解析 ?pid=xxx 中的值
    // 返回策略 ID
}
```

---

## 实现类

### 类图

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
│  │  @NotBlank @Pattern String event;                            │   │
│  │  @NotBlank String url;                                       │   │
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
│  │  String event;                                               │   │
│  │  String url;                                                 │   │
│  │  String detailsJson;  // 已序列化                            │   │
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
│  │  - 解析 URL 中的 pid/rid                                      │   │
│  │  - 构建 DeviceUpgradeEvent                                    │   │
│  │  - 发送到 RabbitMQ                                            │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 文件位置

| 文件 | 路径 |
|------|------|
| UpgradeReportController | `adapter/api/device/UpgradeReportController.java` |
| UpgradeReportDTO | `adapter/api/device/dto/UpgradeReportDTO.java` |
| UpgradeReport | `domain/reporting/model/UpgradeReport.java` |
| UpgradeReportGateway | `domain/reporting/service/UpgradeReportGateway.java` |
| DeviceUpgradeEvent | `domain/reporting/model/aggregate/DeviceUpgradeEvent.java` |
| RabbitMqUpgradeReportGateway | `infra/gateway/RabbitMqUpgradeReportGateway.java` |
| UpgradeReportAppService | `application/reporting/UpgradeReportAppService.java` |

---

## 变更日志

### 2026-03-02 (v1.0)
- 初版：定义 API 请求和响应格式
- 字段与 PRD 定义对齐
- 新增 `UP_FAIL` 事件类型
- 添加 URL 溯源参数说明
