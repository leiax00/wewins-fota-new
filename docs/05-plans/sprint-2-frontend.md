# Sprint 2: 管理后台前端开发

> **时间**: 2026-02 (Week 3-4)  
> **目标**: 完成 FOTA 管理后台核心功能  
> **范围**: 核心版（基础框架 + 核心业务 + 设备管理 + 系统管理）

**Sprint Owner**: FOTA 前端组  
**文档版本**: v1.0  
**创建日期**: 2026-02-17

---

## 📋 Sprint 概览

### 目标

- 搭建 Vue 3 + Element Plus + TailwindCSS 前端框架
- 完成产品、固件、策略管理模块
- 完成设备管理模块
- 完成系统管理模块（用户、角色、权限、字典）

### 范围

- ✅ **包含**: 项目框架、核心业务、设备管理、系统管理
- ❌ **不包含**: Dashboard 统计看板（后续迭代）、移动端适配

### 验收标准

- [ ] 所有页面可正常访问
- [ ] 登录认证流程完整
- [ ] CRUD 功能完整（产品、固件、策略、设备、用户、角色、字典）
- [ ] 权限控制生效（菜单 + 按钮）
- [ ] 代码通过 ESLint 检查
- [ ] 打包构建成功

---

## 📅 阶段 1: 项目骨架 (Day 1-2)

**预计时间**: 2天  
**分支**: `feature/fota-ui-init`

### Day 1: 项目初始化

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 1.1 | 创建 Vite + Vue 3 + TS 项目 | 0.5h | ⬜ |
| 1.2 | 配置 TailwindCSS | 0.5h | ⬜ |
| 1.3 | 配置 Element Plus | 0.5h | ⬜ |
| 1.4 | 配置 Vue Router | 0.5h | ⬜ |
| 1.5 | 配置 Pinia | 0.5h | ⬜ |
| 1.6 | 配置 Axios + 拦截器 + Vite Proxy | 1h | ⬜ |
| 1.7 | 配置 ESLint + Prettier | 0.5h | ⬜ |

#### 产出文件

```
fota-ui/
├── package.json
├── vite.config.ts
├── tailwind.config.js
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
| 2.1 | 创建 AppLayout 布局组件 | 2h | ⬜ |
| 2.2 | 创建 Sidebar 侧边栏 | 2h | ⬜ |
| 2.3 | 创建 Header 顶栏 | 1h | ⬜ |
| 2.4 | 创建 TabsView 多标签页 | 2h | ⬜ |
| 2.5 | 创建登录页面 | 1.5h | ⬜ |
| 2.6 | 实现登录 API 对接后端 | 1h | ⬜ |
| 2.7 | 实现路由守卫 | 1h | ⬜ |
| 2.8 | 实现 Token 管理 | 0.5h | ⬜ |

#### 产出文件

```
src/
├── components/layout/
│   ├── AppLayout.vue
│   ├── Sidebar.vue
│   ├── Header.vue
│   ├── TabsView.vue
│   └── Breadcrumb.vue
├── views/login/
│   └── LoginView.vue
├── stores/
│   ├── user.ts
│   └── app.ts
├── utils/
│   └── auth.ts
└── api/
    └── auth.ts
```

---

## 📅 阶段 2: 核心业务 (Day 3-5)

**预计时间**: 3天  
**分支**: `feature/fota-ui-core`

### Day 3: 产品管理

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 3.1 | 创建 Product 类型定义 | 0.5h | ⬜ |
| 3.2 | 创建 Product API 对接后端 | 1h | ⬜ |
| 3.3 | 创建产品列表页 | 2h | ⬜ |
| 3.4 | 创建产品表单（新增/编辑） | 2h | ⬜ |
| 3.5 | 实现产品 CRUD 功能 | 1h | ⬜ |

#### 产出文件

```
src/
├── types/product.d.ts
├── api/product.ts
├── router/routes/product.ts
└── views/product/
    ├── ProductListView.vue
    └── components/
        └── ProductFormDialog.vue
```

### Day 4: 固件管理

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 4.1 | 创建 Firmware 类型定义 | 0.5h | ⬜ |
| 4.2 | 创建 Firmware API 对接后端 | 1h | ⬜ |
| 4.3 | 创建固件列表页 | 2h | ⬜ |
| 4.4 | 创建固件上传组件 | 2h | ⬜ |
| 4.5 | 创建固件表单（含标签编辑） | 2h | ⬜ |

#### 产出文件

```
src/
├── types/firmware.d.ts
├── api/firmware.ts
├── router/routes/firmware.ts
├── components/business/FirmwareUploader/
│   └── index.vue
└── views/firmware/
    ├── FirmwareListView.vue
    └── components/
        ├── FirmwareFormDialog.vue
        └── JsonTagsEditor.vue
```

### Day 5: 策略管理

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 5.1 | 创建 Policy 类型定义 | 0.5h | ⬜ |
| 5.2 | 创建 Policy API 对接后端 | 1h | ⬜ |
| 5.3 | 创建策略列表页 | 2h | ⬜ |
| 5.4 | 创建策略表单（灰度滑块、时间窗口） | 3h | ⬜ |
| 5.5 | 创建设备筛选组件 | 1.5h | ⬜ |

#### 产出文件

```
src/
├── types/policy.d.ts
├── api/policy.ts
├── router/routes/policy.ts
├── components/business/PolicyForm/
│   ├── index.vue
│   ├── GrayRateSlider.vue
│   ├── TimeWindowPicker.vue
│   └── DeviceFilter.vue
└── views/policy/
    ├── PolicyListView.vue
    └── components/
        └── PolicyFormDialog.vue
```

---

## 📅 阶段 3: 设备管理 (Day 6-7)

**预计时间**: 2天  
**分支**: `feature/fota-ui-device`

### Day 6: 设备列表

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 6.1 | 创建 Device 类型定义 | 0.5h | ⬜ |
| 6.2 | 创建 Device API 对接后端 | 1h | ⬜ |
| 6.3 | 创建设备列表页（含筛选） | 2.5h | ⬜ |
| 6.4 | 创建标签搜索组件 | 1.5h | ⬜ |
| 6.5 | 实现标签编辑功能 | 1h | ⬜ |

#### 产出文件

```
src/
├── types/device.d.ts
├── api/device.ts
├── router/routes/device.ts
├── components/business/DeviceTagsInput/
│   └── index.vue
└── views/device/
    ├── DeviceListView.vue
    └── components/
        ├── DeviceSearchForm.vue
        └── DeviceTagsDialog.vue
```

### Day 7: 设备详情与导入

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 7.1 | 创建设备详情页 | 2h | ⬜ |
| 7.2 | 创建升级历史时间线 | 1.5h | ⬜ |
| 7.3 | 创建设备导入页面 | 2h | ⬜ |
| 7.4 | 实现 CSV/Excel 上传解析 | 1.5h | ⬜ |

#### 产出文件

```
src/
├── views/device/
│   ├── DeviceDetailView.vue
│   ├── DeviceImportView.vue
│   └── components/
│       ├── DeviceInfoCard.vue
│       ├── UpgradeTimeline.vue
│       └── ImportPreview.vue
└── composables/
    └── useFileParse.ts
```

---

## 📅 阶段 4: 系统管理 (Day 8-9)

**预计时间**: 2天  
**分支**: `feature/fota-ui-system`

### Day 8: 用户与角色

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 8.1 | 创建系统管理类型定义 | 0.5h | ⬜ |
| 8.2 | 创建 User/Role API 对接后端 | 1h | ⬜ |
| 8.3 | 创建用户列表页 | 1.5h | ⬜ |
| 8.4 | 创建用户表单（含密码重置） | 1.5h | ⬜ |
| 8.5 | 创建角色列表页 | 1h | ⬜ |
| 8.6 | 创建角色表单（含权限分配） | 1.5h | ⬜ |

#### 产出文件

```
src/
├── types/system.d.ts
├── api/system/
│   ├── user.ts
│   └── role.ts
├── router/routes/system.ts
└── views/system/
    ├── UserListView.vue
    └── RoleListView.vue
```

### Day 9: 权限与字典

#### 任务清单

| # | 任务 | 预计 | 状态 |
|---|------|------|------|
| 9.1 | 创建权限管理页（树形） | 2h | ⬜ |
| 9.2 | 创建权限表单 | 1.5h | ⬜ |
| 9.3 | 创建字典类型列表 | 1h | ⬜ |
| 9.4 | 创建字典项管理 | 1.5h | ⬜ |
| 9.5 | 集成测试 | 1h | ⬜ |

#### 产出文件

```
src/
├── api/system/
│   ├── permission.ts
│   └── dict.ts
├── components/common/
│   └── PermissionTree.vue
└── views/system/
    ├── PermissionView.vue
    └── DictView.vue
```

---

## 📊 进度跟踪

```
Sprint 2: [░░░░░░░░░░░░░░░░] 0%

阶段 1: 项目骨架        ⬜ Day 1-2 (0/2)
阶段 2: 核心业务        ⬜ Day 3-5 (0/3)
阶段 3: 设备管理        ⬜ Day 6-7 (0/2)
阶段 4: 系统管理        ⬜ Day 8-9 (0/2)
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

---

## 📝 变更日志

### 2026-02-17
- 📝 创建 Sprint 2 计划文档
- 📝 确定技术栈和迭代计划
- 📝 细化任务清单和文件结构
- ✅ 移除 Mock 策略，改为直接对接后端 API
- ✅ 更新文件统计（~78 → ~60 文件）

---

## 🔗 相关文档

- [前端架构设计](../02-architecture/frontend-architecture.md)
- [Sprint 1 后端计划](./sprint-1.md)
- [产品需求文档](../01-product/prd.md)
