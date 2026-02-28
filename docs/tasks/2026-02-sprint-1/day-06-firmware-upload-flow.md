# 固件上传功能使用指南

## 概述

实现了完整的固件包上传与管理功能，支持两阶段上传流程、无包版本占位、文件类型校验等核心能力。

### 核心特性

- ✅ **两阶段上传**：先上传临时文件 → 提交时转存对象存储
- ✅ **流式哈希计算**：单次遍历计算MD5/SHA256，内存占用可控
- ✅ **原子会话消费**：Lua脚本GETDEL避免并发竞态
- ✅ **路径安全校验**：删除前检查路径是否在staging目录
- ✅ **占位版本支持**：允许创建无包版本用于版本规划
- ✅ **状态隔离**：策略校验确保只有READY版本可用
- ✅ **补传机制**：支持为无包版本后续补传固件包

---

## 数据模型

### package_status 状态机

```
NONE ──────────────────> READY
(无包版本)    (补传包 / 创建时直接上传)

     ↑                      ↑
     |                      |
   (可删除/编辑)          (可删除/编辑)
```

**状态说明**：
- `NONE` - 无固件包（占位版本号，用于版本规划）
- `UPLOADED` - 已上传临时文件（本地 staging）
- `READY` - 已转存到对象存储，可下载
- `FAILED` - 上传或转存失败

**约束规则**：
- `NONE` 状态：所有文件字段（fileUrl/fileSize/md5/sha256）必须为 NULL
- `READY` 状态：所有文件字段必须非 NULL
- 策略校验：目标版本必须是 `READY` 状态

---

## 用户操作流程

### 方式一：带固件包的新版本（正常流程）

#### 步骤1：打开新增固件弹窗
用户在「固件版本管理」页面点击「新增固件」按钮

#### 步骤2：上传固件包
1. 点击「选择固件包」按钮
2. 选择本地文件（支持 `.bin/.zip/.tar/.tar.gz/.rar`，最大200MB）
3. 系统开始上传，显示**进度条**和**百分比**
4. 上传中可点击「取消」按钮中止

#### 步骤3：上传成功后的界面
上传完成后自动显示：
- **文件名**：`firmware-v1.0.0.bin`（只读）
- **文件大小**：`15.2 MB`（只读，自动格式化）
- **状态提示**：「✓ 文件已上传，哈希校验通过」

此时可以：
- 点击「替换文件」按钮重新上传
- 继续填写其他表单字段

#### 步骤4：填写其他必填项
- **产品**：下拉选择（支持远程搜索）
- **版本号**：如 `1.0.0`
- **版本标签（可选）**：JSON格式的标签
- **扩展元数据（可选）**：changelog、多语言描述等

#### 步骤5：提交创建
点击「确定」按钮后：
- 系统将临时文件转存到对象存储
- 创建固件版本记录（`packageStatus=READY`）
- 清理临时文件和Redis会话

---

### 方式二：无固件包的占位版本（用于提前规划版本号）

#### 步骤1：打开新增固件弹窗

#### 步骤2：勾选「无固件包版本」开关
- 勾选后，上传区域**隐藏**
- 提示文字：「占位版本号，用于版本规划，暂不可用于升级策略」

#### 步骤3：仅填写基本信息
- **产品**：必填
- **版本号**：必填（如 `2.0.0`）
- 其他字段可选

#### 步骤4：提交创建
系统创建记录（`packageStatus=NONE`），文件相关字段全为NULL

---

### 方式三：为已存在的无包版本补传固件包

#### 场景
用户之前创建了占位版本（`packageStatus=NONE`），现在要补传固件包

#### 操作方式

**方式A：通过编辑弹窗**
1. 在列表中点击该版本的「编辑」按钮
2. 点击「选择固件包」上传文件
3. 上传成功后点击「确定」
4. 系统更新记录为 `packageStatus=READY`

**方式B：通过专用接口**
1. 调用 `POST /api/admin/firmware-versions/{id}/attach-package`
2. 传入 `uploadSessionId`
3. 系统更新记录为 `packageStatus=READY`

---

## 后端处理流程（技术视角）

### 创建带包版本流程图

```
用户上传文件
    │
    ├─→ 1. 文件类型校验（扩展名+Magic+MIME+大小）
    │       └─→ FirmwareFileValidator.validate()
    │
    ├─→ 2. 写入临时文件 + 流式计算MD5/SHA256
    │       ├─→ FileTransferService.createStagingFile()
    │       └─→ 16KB缓冲区单次遍历
    │
    ├─→ 3. 创建上传会话（Redis，TTL 2h）
    │       └─→ key: fota:fw:upload:sess:{sessionId}
    │
    └─→ 4. 返回 uploadSessionId 给前端

用户提交表单（携带 uploadSessionId）
    │
    ├─→ 5. 消费会话（Lua GETDEL，原子操作）
    │       └─→ FirmwareUploadAppService.consumeUploadSession()
    │
    ├─→ 6. 转存到对象存储
    │       └─→ FileTransferService.transferToStorage()
    │
    ├─→ 7. 创建数据库记录
    │       └─→ packageStatus=READY
    │
    ├─→ 8. 清理临时文件和会话
    │       └─→ 删除本地临时文件 + Redis会话
    │
    └─→ 9. 返回固件版本详情
```

---

## API接口说明

### 1. 上传固件包到临时目录

**接口**：`POST /api/admin/firmware-uploads`

**请求参数**：
- `file`（MultipartFile）：上传文件
- `productId`（Long）：关联的产品ID

**响应**：
```json
{
  "code": 0,
  "message": "OK",
  "data": {
    "sessionId": "a1b2c3d4e5f6",
    "status": "UPLOADED",
    "productId": 1001,
    "fileName": "firmware-v1.0.0.bin",
    "fileSize": 15938176,
    "md5": "d41d8cd98f00b204e9800998ecf8427e",
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "mime": "application/octet-stream"
  }
}
```

---

### 2. 查询上传会话状态

**接口**：`GET /api/admin/firmware-uploads/{sessionId}`

**响应**：
```json
{
  "code": 0,
  "data": {
    "sessionId": "a1b2c3d4e5f6",
    "status": "UPLOADED",
    "productId": 1001,
    "version": null,
    "fileName": "firmware-v1.0.0.bin",
    "fileSize": 15938176,
    "md5": "d41d8cd98f00b204e9800998ecf8427e",
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "objectKey": null,
    "createdAt": "2026-02-26T10:30:00",
    "updatedAt": "2026-02-26T10:30:00"
  }
}
```

---

### 3. 取消上传并清理临时文件

**接口**：`DELETE /api/admin/firmware-uploads/{sessionId}`

**响应**：`204 No Content`

---

### 4. 创建固件版本（支持uploadSessionId）

**接口**：`POST /api/admin/firmware-versions`

**请求体**：
```json
{
  "productId": 1001,
  "version": "1.0.0",
  "uploadSessionId": "a1b2c3d4e5f6",  // 可选，上传会话ID
  "tags": "[\"stable\", \"beta\"]",
  "meta": "{\"zh-CN\": {\"description\": \"修复蓝牙断连\"}}"
}
```

**逻辑**：
- 如果提供 `uploadSessionId`：
  - 消费会话，获取文件信息
  - 转存到对象存储
  - 设置 `packageStatus=READY`
- 如果未提供：
  - 检查手动填写的文件信息
  - 有完整文件信息 → `packageStatus=READY`
  - 无文件信息 → `packageStatus=NONE`

---

### 5. 为无包版本补传固件包

**接口**：`POST /api/admin/firmware-versions/{id}/attach-package`

**请求体**：
```json
{
  "uploadSessionId": "a1b2c3d4e5f6"
}
```

**响应**：更新后的固件版本详情（`packageStatus=READY`）

---

### 6. 查询产品固件版本（支持readyOnly过滤）

**接口**：`GET /api/admin/firmware-versions/by-product/{productId}?readyOnly=true`

**参数**：
- `readyOnly`（可选）：`true` - 仅返回 `packageStatus=READY` 的版本

**用途**：策略页面下拉列表只显示可用作目标版本的固件

---

## 文件类型校验规则

### 支持的文件类型
- `.bin` - 二进制固件（无统一magic，依赖MIME类型）
- `.zip` - ZIP压缩包
- `.tar` - TAR归档
- `.tar.gz` - GZIP压缩的TAR
- `.rar` - RAR压缩包（v4/v5）

### 校验流程（短路失败）
1. **文件大小**：≤ 200MB
2. **扩展名白名单**：仅允许上述格式
3. **Magic Number**：文件头字节识别
   - ZIP：`PK\x03\x04` / `PK\x05\x06` / `PK\x07\x08`
   - TAR：offset 257 处为 `ustar`
   - GZIP：`0x1F 0x8B`
   - RAR：`RAR\x21\x1A\x07\x00` 或 `RAR\x21\x1A\x07\x01\x00`
4. **MIME类型**：兜底校验，防止伪造

### 配置参数
在 `application.yml` 中配置：
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 200MB        # 单个文件最大大小
      max-request-size: 210MB      # 请求最大大小（略大于单文件）

server:
  tomcat:
    max-swallow-size: 220MB        # Tomcat 最大吞咽大小（处理取消上传）
```

环境变量覆盖：
```bash
export SPRING_MULTIPART_MAX_FILE_SIZE=200MB
export SPRING_MULTIPART_MAX_REQUEST_SIZE=210MB
export TOMCAT_MAX_SWALLOW_SIZE=220MB
```

---

## 安全与性能保障

### 1. 并发安全
- **原子会话消费**：Lua脚本实现GETDEL，避免重复消费
- **路径遍历防护**：删除临时文件前检查 `path.startsWith(stagingTempDir)`

### 2. 内存控制
- **流式处理**：16KB缓冲区单次遍历，边读边写边计算哈希
- **临时文件隔离**：独立目录 + 随机文件名（`fw-{productId}-{UUID}.upload`）

### 3. 会话管理
- **Redis TTL**：2小时自动过期
- **定时清理**：清理超时会话和孤儿临时文件（待实现）

### 4. 失败补偿
- **上传失败**：立即删除临时文件
- **转存失败**：删除已上传的对象存储文件
- **DB写入失败**：回滚所有操作

---

## 已修复问题

### 2026-02-26 修复

**问题1：对象存储转存缺失（CRITICAL）**
- 现象：创建带包固件版本失败，`objectKey` 为 null
- 原因：上传会话只写临时文件，未转存到对象存储
- 修复：
  - 在 `FirmwareVersionController.processUploadSessionId()` 中添加转存逻辑
  - 消费会话后立即调用 `FileTransferService.transferToStorage()`
  - 生成 objectKey 格式：`firmware/{productId}/{version}/{filename}`
  - 在 `attachPackage()` 方法中也添加相同的转存逻辑

**问题2：编辑版本时错误标记为 NONE（CRITICAL）**
- 现象：已有 READY 包的版本，编辑元数据后被错误降级为 NONE
- 原因：编辑时不带文件字段，`determinePackageStatus()` 误判为 NONE
- 修复：
  - 在 `updateFirmwareVersion()` 中先查询现有版本
  - 如果没有 `uploadSessionId`，保留原有的包状态和文件字段
  - 只有补传包时（有 uploadSessionId）才设置为 READY

**问题3：AbortController 未接入 Axios（HIGH）**
- 现象：点击"取消上传"按钮后，请求仍在继续
- 原因：`uploadFirmwarePackage()` 函数未接受 `signal` 参数
- 修复：
  - 在 `firmware.ts` 中添加 `signal?: AbortSignal` 参数
  - 将 signal 传递给 axios 配置
  - 在 `request.ts` 响应拦截器中特殊处理取消请求，不显示错误提示

**问题4：失败补偿机制不完善（MEDIUM）**
- 当前实现：转存失败时清理临时文件
- 限制：如果转存成功但 DB 写入失败，会产生孤儿对象（需要手动清理）
- 后续改进：为 `StorageClient` 添加 `deleteObject()` 方法，实现完整的事务补偿

---

## 待实现功能

### 1. 定时清理任务
- 扫描超时会话（2小时未消费）
- 清理孤儿临时文件
- 清理孤儿对象存储文件（需 StorageClient 提供 delete API）

### 2. 版本并发控制
- 实现 Redis 锁：`fota:fw:upload:lock:{productId}:{version}`
- 防止并发上传同版本号

### 3. 分片上传（v2.0）
- 支持超大文件（>500MB）
- 断点续传
- 并发分片上传

---

## 相关文档

- 数据库迁移：`fota-service/src/main/resources/db/changelog/changes/20260226_01_firmware_package_nullable_status.sql`
- 后端实现：
  - `FirmwareFileValidator.java` - 文件类型校验
  - `FirmwareUploadAppService.java` - 上传服务
  - `FirmwareUploadController.java` - 上传接口
  - `FirmwareVersionController.java` - 固件版本CRUD
- 前端实现：`fota-ui/src/views/firmware/FirmwareListView.vue`（待完善）
