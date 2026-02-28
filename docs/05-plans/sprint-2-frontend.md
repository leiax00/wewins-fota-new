# Sprint 2: 管理后台前端开发

> **时间**: 2026-02 (Week 3-4)
> **目标**: 完成 FOTA 管理后台核心功能
> **范围**: 核心版（基础框架 + 核心业务 + 设备管理 + 系统管理）

**Sprint Owner**: FOTA 前端组
**文档版本**: v1.3
**创建日期**: 2026-02-17
**最近更新**: 2026-02-28

---

## 📋 Sprint 概览

### 目标

- 搭建 Vue 3 + Element Plus + TailwindCSS 前端框架
- 完成产品、固件、策略管理模块
- 完成设备管理模块
- 完成系统管理模块（用户、角色、权限、字典）
- 完成登录页品牌化设计与暗色模式体验优化

### 范围

- ✅ **包含**: 项目框架、核心业务、设备管理、系统管理
- ✅ **已提前实现**: Dashboard 概览页骨架、登录页多语言与主题切换入口
- ❌ **不包含**: 真实业务数据联调、复杂表单编辑器、批量导入完整流程

### 验收标准

- [x] 页面骨架可访问（登录、Dashboard、产品、固件、策略、设备、系统管理）
- [x] 登录认证流程完整（登录、登出、401 处理、路由守卫）
- [x] 基础导航框架可用（Sidebar + Header + TabsView + Breadcrumb）
- [x] CRUD 功能完整（产品、固件、策略、设备、系统管理）
- [x] 权限控制生效（路由 + 按钮级）
- [ ] 代码通过 ESLint 检查（待统一执行）
- [ ] 打包构建成功（待在具备 Node/npm 环境执行）

---

## 📌 当前实现快照（2026-02-28）

### 已完成

#### 前端工程
- 前端工程初始化：Vite + Vue 3 + TS + Element Plus + TailwindCSS + Pinia + Vue Router + Vue I18n
- 基础布局：`AppLayout`、`Sidebar`、`Header`
- 导航框架：`Sidebar + Header + TabsView + Breadcrumb` 已联动
- 主题系统：`light / dark / system`，并已接入 Header 与登录页
- 登录页：品牌化视觉背景、暗色模式重配色、多语言切换、主题切换入口
- 标签页状态：支持本地持久化；登出/Token 失效自动清理
- 布局策略：当前采用桌面优先 `min-width: 1024`，窄屏横向滚动
- 路由守卫：未登录跳转、登录后回跳、权限兜底路由

#### 业务模块（前端）
- **Dashboard**: 概览页骨架完成
- **产品管理**: ProductListView（列表、搜索、详情）
- **固件管理**: FirmwareListView（列表、分页）
- **策略管理**: PolicyListView（列表）、PolicyFormView（新增/编辑）
- **设备管理**:
  - DeviceListView（列表、搜索、筛选）
  - DeviceDetailView（设备详情）
  - DeviceImportDialog（导入对话框）
  - DeviceBatchOperationDialog（批量操作）
  - DeviceImportBatchListView（批次管理列表）
- **系统管理**:
  - UserListView（用户列表）
  - RoleListView（角色列表）
  - PermissionView（权限管理树形）
  - DictView（字典类型管理）
  - DictItemListView（字典项管理）

#### 后端 API 增强
- **固件分步上传**: FirmwareUploadController（分步上传流程）
- **设备批次管理**: DeviceImportBatchController（批次查询）
- **设备升级检查**: UpgradeCheckController（重构后的路由）
- **设备上报**: UpgradeReportController
- **动态菜单**: MenuController、MenuAppService（菜单管理、缓存失效策略）
- **权限控制**: RBAC API 鉴权机制，前后端权限码格式统一

#### 基础设施增强
- **时区处理**: 前后端统一时区处理机制，时间窗口使用 LocalDateTime
- **Redis 缓存**:
  - 设备活跃度 Bitmap 功能
  - 限流功能实现
  - 策略快照缓存
- **ClickHouse**: 设备事件记录表结构
- **文件存储**: 存储服务增强，文件验证基础设施

### 待完善

- 各业务模块表单校验全量落地
- 复杂表单编辑器（策略时间窗口、设备筛选器）
- 批量导入完整流程（CSV/Excel 解析）
- 代码 ESLint 检查和格式化
- E2E 自动化验收
- 前端打包构建验证

---

## 📅 阶段 1: 项目骨架 (Day 1-2)

**预计时间**: 2天  
**分支**: `feature/fota-ui-init`

### Day 1: 项目初始化

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 1.1 | 创建 Vite + Vue 3 + TS 项目 | 0.5h | ✅ |
| 1.2 | 配置 TailwindCSS | 0.5h | ✅ |
| 1.3 | 配置 Element Plus | 0.5h | ✅ |
| 1.4 | 配置 Vue Router | 0.5h | ✅ |
| 1.5 | 配置 Pinia | 0.5h | ✅ |
| 1.6 | 配置 Axios + 拦截器 + Vite Proxy | 1h | ✅ |
| 1.7 | 配置 ESLint + Prettier | 0.5h | ✅ |

#### 产出文件

```
fota-ui/
├── package.json
├── vite.config.ts
├── postcss.config.js
├── tsconfig.json
├── .eslintrc.cjs
├── .prettierrc
└── src/
    ├── main.ts
    ├── api/request.ts
    └── styles/main.css
```

### Day 2: 布局与认证

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 2.1 | 创建 AppLayout 布局组件 | 2h | ✅ |
| 2.2 | 创建 Sidebar 侧边栏 | 2h | ✅ |
| 2.3 | 创建 Header 顶栏 | 1h | ✅ |
| 2.4 | 创建 TabsView 多标签页 | 2h | ✅ |
| 2.5 | 创建登录页面 | 1.5h | ✅ |
| 2.6 | 实现登录 API 对接后端 | 1h | ✅ |
| 2.7 | 实现路由守卫 | 1h | ✅ |
| 2.8 | 实现 Token 管理 | 0.5h | ✅ |

#### 产出文件

```
src/
├── components/layout/
│   ├── AppLayout.vue
│   ├── Sidebar.vue
│   ├── Header.vue
│   ├── TabsView.vue
│   └── Breadcrumb.vue
├── components/login/
│   ├── LoginCard.vue
│   └── LoginHeroBackground.vue
├── views/login/
│   └── LoginView.vue
├── stores/
│   ├── user.ts
│   ├── layout.ts
│   └── theme.ts
├── utils/
│   └── auth.ts
└── api/
    └── request.ts
```

---

## 📅 阶段 2: 核心业务 (Day 3-5)

**预计时间**: 3天  
**分支**: `feature/fota-ui-core`

### Day 3: 产品管理

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 3.1 | 创建 Product 类型定义 | 0.5h | ✅ |
| 3.2 | 创建 Product API 对接后端 | 1h | ✅ |
| 3.3 | 创建产品列表页 | 2h | ✅ |
| 3.4 | 创建产品表单（新增/编辑） | 2h | ✅ |
| 3.5 | 实现产品 CRUD 功能 | 1h | ✅ |

#### 产出文件

```
src/
└── views/product/
    └── ProductListView.vue
```

### Day 4: 固件管理

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 4.1 | 创建 Firmware 类型定义 | 0.5h | ✅ |
| 4.2 | 创建 Firmware API 对接后端 | 1h | ✅ |
| 4.3 | 创建固件列表页 | 2h | ✅ |
| 4.4 | 创建固件上传组件 | 2h | ✅ |
| 4.5 | 创建固件表单（含标签编辑） | 2h | ✅ |

#### 产出文件

```
src/
└── views/firmware/
    └── FirmwareListView.vue
```

### Day 5: 策略管理

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 5.1 | 创建 Policy 类型定义 | 0.5h | ✅ |
| 5.2 | 创建 Policy API 对接后端 | 1h | ✅ |
| 5.3 | 创建策略列表页 | 2h | ✅ |
| 5.4 | 创建策略表单（灰度滑块、时间窗口） | 3h | ✅ |
| 5.5 | 创建设备筛选组件 | 1.5h | ✅ |

#### 产出文件

```
src/
└── views/policy/
    └── PolicyListView.vue
```

---

## 📅 阶段 3: 设备管理 (Day 6-7)

**预计时间**: 2天  
**分支**: `feature/fota-ui-device`

### Day 6: 设备列表

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 6.1 | 创建 Device 类型定义 | 0.5h | ✅ |
| 6.2 | 创建 Device API 对接后端 | 1h | ✅ |
| 6.3 | 创建设备列表页（含筛选） | 2.5h | ✅ |
| 6.4 | 创建标签搜索组件 | 1.5h | ✅ |
| 6.5 | 实现标签编辑功能 | 1h | ✅ |

#### 产出文件

```
src/
└── views/device/
    └── DeviceListView.vue
```

### Day 7: 设备详情与导入

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 7.1 | 创建设备详情页 | 2h | ✅ |
| 7.2 | 创建升级历史时间线 | 1.5h | ⏸️ |
| 7.3 | 创建设备导入对话框 | 2h | ✅ |
| 7.4 | 创建批量操作对话框 | 1.5h | ✅ |
| 7.5 | 创建批次管理列表 | 1h | ✅ |

#### 产出文件

```
src/
└── views/device/
    └── DeviceDetailView.vue
```

---

## 📅 阶段 4: 系统管理 (Day 8-9)

**预计时间**: 2天  
**分支**: `feature/fota-ui-system`

### Day 8: 用户与角色

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 8.1 | 创建系统管理类型定义 | 0.5h | ✅ |
| 8.2 | 创建 User/Role API 对接后端 | 1h | ✅ |
| 8.3 | 创建用户列表页 | 1.5h | ✅ |
| 8.4 | 创建用户表单（含密码重置） | 1.5h | ⏸️ |
| 8.5 | 创建角色列表页 | 1h | ✅ |
| 8.6 | 创建角色表单（含权限分配） | 1.5h | ⏸️ |

#### 产出文件

```
src/
└── views/system/
    ├── UserListView.vue
    └── RoleListView.vue
```

### Day 9: 权限与字典

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 9.1 | 创建权限管理页（树形） | 2h | ✅ |
| 9.2 | 创建权限表单 | 1.5h | ⏸️ |
| 9.3 | 创建字典类型列表 | 1h | ✅ |
| 9.4 | 创建字典项管理 | 1.5h | ✅ |
| 9.5 | 集成测试 | 1h | ⏸️ |

#### 产出文件

```
src/
└── views/system/
    ├── PermissionView.vue
    └── DictView.vue
```

---

## 📊 进度跟踪

```
Sprint 2: [████████████████░] 85%

阶段 1: 项目骨架        ✅ Day 1-2 (2/2，含 Tabs/Breadcrumb)
阶段 2: 核心业务        ✅ Day 3-5 (列表/表单/接口完成)
阶段 3: 设备管理        ✅ Day 6-7 (列表/详情/导入/批次完成)
阶段 4: 系统管理        ✅ Day 8-9 (列表/树形完成，表单待完善)
```

---

## 📁 文件统计

| 阶段 | 文件数 | 说明 |
|------|--------|------|
| 阶段 1 | ~18 | 配置 + 布局 + 认证 |
| 阶段 2 | ~18 | 产品 + 固件 + 策略 |
| 阶段 3 | ~12 | 设备列表 + 详情 + 导入 |
| 阶段 4 | ~12 | 用户 + 角色 + 权限 + 字典 |
| **总计** | **~60** | |

---

## ⚠️ 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| TailwindCSS 与 Element Plus 冲突 | 中 | 低 | 使用 prefix，避免类名冲突 |
| 后端 API 不完整 | 高 | 中 | 前端先开发 UI，后端接口补充时对接 |
| 策略表单复杂度高 | 中 | 中 | 拆分子组件，渐进式开发 |
| 时间不足 | 高 | 低 | 优先核心功能，Dashboard 后置 |
| 移动端体验后置 | 中 | 中 | 当前先锁定桌面版，后续单独迭代响应式 |

---

## 📝 变更日志

### 2026-02-28
- ✅ 更新 Sprint 2 总体进度为 85%
- ✅ 标记阶段 1-4 基本完成
- ✅ 补充设备管理功能详情（导入、批量操作、批次管理）
- ✅ 补充后端增强功能（固件分步上传、动态菜单、权限控制）
- ✅ 补充基础设施增强（时区处理、Redis Bitmap/限流、ClickHouse）
- ✅ 更新任务状态清单

### 2026-02-20
- ✅ 增加”当前实现快照”，区分已完成/进行中/待补充
- ✅ 更新阶段进度为 40%，标记框架与登录认证能力已落地
- ✅ 同步登录页视觉重构与主题切换入口实现
- ✅ 同步 TabsView 与 Breadcrumb 落地，修正 Day2 状态
- ✅ 增加”桌面优先（min-width: 1024）”阶段性策略说明

### 2026-02-17
- 📝 创建 Sprint 2 计划文档
- 📝 确定技术栈和迭代计划
- 📝 细化任务清单和文件结构
- ✅ 移除 Mock 策略，改为直接对接后端 API
- ✅ 更新文件统计（~78 → ~60 文件）

---

## 🔗 相关文档

- [前端架构设计](../02-architecture/frontend-architecture.md)
- [前端移动端策略 ADR（桌面优先）](../02-architecture/frontend-mobile-strategy-adr.md)
- [Sprint 1 后端计划](./sprint-1.md)
- [产品需求文档](../01-product/prd.md)
