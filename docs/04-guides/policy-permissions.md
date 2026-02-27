# 策略权限控制指南

## 概述

本文档描述了 FOTA 系统中升级策略的权限控制规则，包括状态切换、策略修改和策略删除的权限要求。

## 策略状态

系统支持以下策略状态：

| 状态 | 代码 | 描述 |
|------|------|------|
| 草稿 | `DRAFT` | 策略创建后的初始状态 |
| 测试中 | `TESTING` | 策略正在测试中 |
| 已验证 | `VERIFIED` | 策略已通过测试验证 |
| 生产中 | `ACTIVE` | 策略已发布到生产环境 |
| 暂停 | `PAUSED` | 策略已暂停（仅发布人员可操作） |
| 过期 | `EXPIRED` | 策略已过期 |

## 用户角色权限

### 测试人员

**权限**：
- `fota:policy:read` - 查看策略
- `fota:policy:create` - 创建策略
- `fota:policy:update` - 修改策略
- `fota:policy:delete` - 删除策略
- `fota:policy:start_test` - 开始测试（可选，实际通过 update 状态实现）
- `fota:policy:verify` - 验证通过（可选，实际通过 update 状态实现）

**约束**：
- 无法操作生产相关状态（ACTIVE、PAUSED）
- 无法删除测试中和暂停的策略

### 发布人员

**权限**：包含测试人员所有权限，加上：
- `fota:policy:release` - 发布到生产、暂停/恢复策略

**能力**：
- 可以执行所有状态切换操作
- 可以修改生产中的策略
- 可以删除更多状态的策略

## 权限矩阵

### 状态切换权限

| 切换操作 | 测试人员 | 发布人员 |
|----------|---------|---------|
| DRAFT ↔ TESTING | ✅ | ✅ |
| DRAFT ↔ VERIFIED | ✅ | ✅ |
| TESTING ↔ VERIFIED | ✅ | ✅ |
| 切换到 ACTIVE | ❌ | ✅ |
| 从 ACTIVE 切换出 | ❌ | ✅ |
| 切换到 PAUSED | ❌ | ✅ |
| 从 PAUSED 切换出 | ❌ | ✅ |
| EXPIRED 任意切换 | ✅ | ✅ |

**规则说明**：
- 测试人员只能在预发布状态（DRAFT、TESTING、VERIFIED）之间自由切换
- 涉及 ACTIVE 或 PAUSED 的任何状态切换都需要 `release` 权限

### 策略修改权限

| 当前状态 | 测试人员 | 发布人员 |
|----------|---------|---------|
| DRAFT | ✅ | ✅ |
| TESTING | ✅ | ✅ |
| VERIFIED | ✅ | ✅ |
| ACTIVE | ❌ | ✅ |
| PAUSED | ❌ | ✅ |
| EXPIRED | ✅ | ✅ |

**规则说明**：
- ACTIVE, PAUSED 状态的策略修改需要 `release` 权限
- 其他状态的策略只要有 `update` 权限即可修改

### 策略删除权限

| 当前状态 | 测试人员 | 发布人员 |
|----------|---------|---------|
| DRAFT | ✅ | ✅ |
| TESTING | ✅ | ✅ |
| VERIFIED | ✅ | ✅ |
| ACTIVE | ❌ | ❌ |
| PAUSED | ❌ | ✅ |
| EXPIRED | ✅ | ✅ |

**规则说明**：
- ACTIVE 状态的策略任何人都无法直接删除，必须先切换到其他状态
- PAUSED 状态的删除需要 `release` 权限
- DRAFT、TESTING、VERIFIED、EXPIRED 状态有 `delete` 权限即可删除

## API 接口

### 状态切换

```http
PUT /api/admin/policies/{id}/status
Content-Type: application/json

{
  "status": "ACTIVE"
}
```

**权限**：`fota:policy:update`

**动态权限检查**：
- 如果目标状态或当前状态涉及 ACTIVE/PAUSED，会额外检查 `fota:policy:release` 权限

### 策略修改

```http
PUT /api/admin/policies/{id}
Content-Type: application/json

{
  "name": "新策略名称",
  "grayRate": 50,
  ...
}
```

**权限**：`fota:policy:update`

**动态权限检查**：
- 如果策略当前状态为 ACTIVE, PAUSED，会额外检查 `fota:policy:release` 权限

### 策略删除

```http
DELETE /api/admin/policies/{id}
```

**权限**：`fota:policy:delete`

**动态权限检查**：
- ACTIVE 状态：拒绝删除（任何人）
- PAUSED 状态：检查 `fota:policy:release` 权限
- 其他状态：允许删除

## 前端实现建议

### 状态切换组件

```typescript
// 根据当前状态和用户权限显示可用的目标状态
const getAvailableTargetStatuses = (currentStatus: PolicyStatus, userPermissions: string[]): PolicyStatus[] => {
  const hasRelease = userPermissions.includes('fota:policy:release');

  // 测试人员只能看到预发布状态
  if (!hasRelease) {
    return ['DRAFT', 'TESTING', 'VERIFIED', 'EXPIRED'].filter(s => s !== currentStatus);
  }

  // 发布人员可以看到所有状态
  return ALL_STATUSES.filter(s => s !== currentStatus);
};
```

### 删除按钮显示逻辑

```typescript
const canDeletePolicy = (status: PolicyStatus, userPermissions: string[]): boolean => {
  const hasRelease = userPermissions.includes('fota:policy:release');
  const hasDelete = userPermissions.includes('fota:policy:delete');

  if (!hasDelete) return false;
  if (status === 'ACTIVE') return false;
  if (status === 'PAUSED' && !hasRelease) return false;

  return true;
};
```

## 安全考虑

### 并发控制

所有状态切换操作都使用乐观锁机制（带状态检查的 UPDATE），防止并发冲突：

```java
// Repository 层实现
UpgradePolicy updateWithStatusCheck(Long id, String expectedStatus, UpgradePolicy policy);
```

### 审计日志

所有状态切换、修改、删除操作都会记录详细日志：

```java
log.info("策略状态更新成功: policyId={}, from={}, to={}", id, from, to);
```

### 权限检查位置

权限检查在 Service 层进行，确保：
- Controller 层的 `@PreAuthorize` 提供基础权限门槛
- Service 层根据业务状态进行细粒度权限检查
- 防止绕过前端的直接 API 调用

## 常见问题

### Q: 为什么 ACTIVE 状态的策略不能删除？

A: 生产中的策略有严格的生命周期管理要求，必须先切换到其他状态（如 PAUSED）后才能删除，确保操作的可追溯性。

### Q: 测试人员想快速发布策略到生产怎么办？

A: 测试人员可以将策略状态设置为 VERIFIED，然后由有发布权限的人员切换到 ACTIVE。

## 更新历史

| 日期 | 版本 | 变更内容 |
|------|------|---------|
| 2026-02-27 | 1.0 | 初始版本，定义策略权限控制规则 |
