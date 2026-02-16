# API 分层与目录边界约定

- 文档版本：v1.0
- 创建日期：2026-02-12
- 适用范围：`fota-service` 接口适配层

## 1. 目标

统一 `adapter/api/*` 目录职责，避免接口混放导致的架构漂移。

## 2. 目录职责

### 2.1 `adapter/api/admin`

- 面向平台运营后台（管理端）
- 典型路径：`/admin/**`
- 仅在 `app.mode=main` 下启用

当前示例：
- `ProductController`
- `DeviceController`
- `FirmwareVersionController`
- `UpgradePolicyController`

### 2.2 `adapter/api/device`

- 面向设备侧请求
- 典型路径：`/v1/upgrade/**`
- 在 `app.mode=main|region` 下启用

当前示例：
- `UpgradeCheckController`
- `UpgradeReportController`

### 2.3 `adapter/api/internal`

- 面向跨区域或系统内部调用
- 典型路径：`/internal/**`
- 通常仅在 `app.mode=main` 下启用（除非有明确 region 内部接口）

当前示例：
- `ConfigVersionController`
- `ConfigSnapshotController`
- `IngestController`
- `NodeRegistryController`

## 3. 放置规则

1. Controller 所在目录必须与 URL 前缀语义一致。
2. `/internal/**` Controller 必须放在 `adapter/api/internal`，不得放在 `admin`。
3. 运行模式控制优先使用 `@ConditionalOnAppMode`，避免继续引入 `app.features.*` 分支开关。
4. Controller 仅做协议适配，不承载跨模式业务编排。
5. main 与 region 的业务编排必须分别归属对应 application service，禁止相互反向依赖。

## 4. 反例

- 反例 1：`/internal/config/*` Controller 放在 `adapter/api/admin`。
- 反例 2：main 控制器直接依赖 region 专用同步服务。
- 反例 3：通过 `app.features.*` 组合开关决定模块启停。

## 5. 变更检查清单

新增 Controller 时至少自检：

1. URL 前缀是否匹配目录语义。
2. 是否使用了正确的 `@ConditionalOnAppMode`。
3. 是否把业务流程放进了 application service，而不是 controller。
4. 是否引入了跨模式反向依赖。

