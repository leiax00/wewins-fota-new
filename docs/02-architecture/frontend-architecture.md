# FOTA 管理后台前端架构设计

> **版本**: v1.0  
> **创建日期**: 2026-02-17  
> **状态**: Accepted

---

## 1. 概述

### 1.1 项目背景

FOTA 管理后台是千万级设备 OTA 管理平台的 Web 控制台，为运营人员提供产品管理、固件版本管理、升级策略配置、设备监控等核心功能。

### 1.2 设计目标

- **现代简约**: 清爽界面，适合长时间操作
- **高效开发**: TailwindCSS + Element Plus 混用，快速迭代
- **独立部署**: 前后端分离，独立 Nginx 部署
- **易于维护**: TypeScript 类型安全，模块化架构

---

## 2. 技术选型

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 框架 | Vue 3 | ^3.4 | Composition API + `<script setup>` |
| 语言 | TypeScript | ^5.3 | 严格模式 |
| 构建 | Vite | ^5.1 | 开发服务器 + 打包 |
| UI 组件 | Element Plus | ^2.5 | 企业级组件库 |
| CSS | TailwindCSS | ^3.4 | 原子化 CSS |
| 路由 | Vue Router | ^4.2 | 路由管理 |
| 状态 | Pinia | ^2.1 | 状态管理 |
| HTTP | Axios | ^1.6 | 请求封装 |
| 图表 | ECharts | ^5.4 | 数据可视化 |
| 国际化 | Vue I18n | ^9.9 | 多语言支持 |
| 工具 | dayjs | ^1.11 | 日期处理 |

---

## 3. 项目结构

```
fota-ui/
├── index.html
├── package.json
├── tsconfig.json
├── tsconfig.node.json
├── vite.config.ts
├── tailwind.config.js
├── postcss.config.js
├── .env.development
├── .env.production
├── .eslintrc.cjs
├── .prettierrc
│
└── src/
    ├── main.ts                    # 入口文件
    ├── App.vue                    # 根组件
    ├── env.d.ts                   # 环境类型声明
    │
    ├── api/                       # API 接口层
    │   ├── request.ts             # Axios 实例和拦截器
    │   ├── types.ts               # API 通用类型
    │   ├── auth.ts                # 认证接口
    │   ├── product.ts             # 产品接口
    │   ├── firmware.ts            # 固件接口
    │   ├── policy.ts              # 策略接口
    │   ├── device.ts              # 设备接口
    │   └── system/                # 系统管理接口
    │       ├── user.ts
    │       ├── role.ts
    │       ├── permission.ts
    │       └── dict.ts
    │
    ├── router/                    # 路由配置
    │   ├── index.ts               # 路由入口
    │   └── routes/                # 路由模块
    │       ├── product.ts
    │       ├── firmware.ts
    │       ├── policy.ts
    │       ├── device.ts
    │       └── system.ts
    │
    ├── stores/                    # Pinia 状态管理
    │   ├── user.ts                # 用户状态
    │   ├── app.ts                 # 应用状态
    │   └── permission.ts          # 权限状态
    │
    ├── locales/                   # 国际化语言包
    │   ├── index.ts               # i18n 配置
    │   ├── zh-CN.ts               # 简体中文
    │   └── en-US.ts               # 英文
    │
    ├── styles/                    # 全局样式
    │   ├── main.css               # Tailwind 入口
    │   └── variables.css          # CSS 变量
    │
    ├── components/                # 组件
    │   ├── layout/                # 布局组件
    │   │   ├── AppLayout.vue      # 主布局
    │   │   ├── Sidebar.vue        # 侧边栏
    │   │   ├── Header.vue         # 顶栏
    │   │   ├── TabsView.vue       # 多标签页
    │   │   └── Breadcrumb.vue     # 面包屑
    │   ├── common/                # 通用业务组件
    │   │   ├── PageHeader.vue     # 页面头部
    │   │   ├── SearchForm.vue     # 搜索表单
    │   │   ├── TableSelect.vue    # 表格选择器
    │   │   └── JsonEditor.vue     # JSON 编辑器
    │   └── business/              # 业务组件
    │       ├── PolicyForm/        # 策略表单
    │       ├── DeviceTagsInput/   # 设备标签输入
    │       └── FirmwareUploader/  # 固件上传
    │
    ├── composables/               # 组合式函数
    │   ├── useTable.ts            # 表格通用逻辑
    │   ├── useForm.ts             # 表单通用逻辑
    │   ├── usePermission.ts       # 权限检查
    │   └── useLoading.ts          # 加载状态
    │
    ├── types/                     # TypeScript 类型
    │   ├── global.d.ts            # 全局类型
    │   ├── api.d.ts               # API 类型
    │   ├── product.d.ts           # 产品类型
    │   ├── firmware.d.ts          # 固件类型
    │   ├── policy.d.ts            # 策略类型
    │   ├── device.d.ts            # 设备类型
    │   └── system.d.ts            # 系统管理类型
    │
    ├── utils/                     # 工具函数
    │   ├── auth.ts                # Token 管理
    │   ├── storage.ts             # 本地存储
    │   ├── format.ts              # 格式化工具
    │   └── validate.ts            # 验证工具
    │
    └── views/                     # 页面视图
        ├── login/
        │   └── LoginView.vue
        ├── dashboard/
        │   └── DashboardView.vue
        ├── product/
        │   ├── ProductListView.vue
        │   └── ProductFormView.vue
        ├── firmware/
        │   ├── FirmwareListView.vue
        │   └── FirmwareFormView.vue
        ├── policy/
        │   ├── PolicyListView.vue
        │   └── PolicyFormView.vue
        ├── device/
        │   ├── DeviceListView.vue
        │   └── DeviceDetailView.vue
        └── system/
            ├── UserListView.vue
            ├── RoleListView.vue
            ├── PermissionView.vue
            └── DictView.vue
```

---

## 4. 设计规范

### 4.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件文件 | PascalCase | `UserList.vue` |
| 组合式函数 | camelCase + use 前缀 | `useTable.ts` |
| API 文件 | camelCase | `product.ts` |
| 类型文件 | camelCase + .d.ts | `product.d.ts` |
| Store 文件 | camelCase | `user.ts` |
| CSS 类名 | Tailwind utilities | `flex items-center` |

### 4.2 组件规范

```vue
<!-- 推荐：使用 <script setup lang="ts"> -->
<script setup lang="ts">
import { ref, computed } from 'vue'
import type { Product } from '@/types/product'

interface Props {
  product: Product
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update', value: Product): void
}>()
</script>

<template>
  <!-- Tailwind + Element Plus 混用 -->
  <div class="p-4 bg-white rounded-lg shadow">
    <el-button type="primary" @click="emit('update', props.product)">
      编辑
    </el-button>
  </div>
</template>
```

### 4.3 TailwindCSS + Element Plus 混用策略

| 场景 | 优先选择 | 原因 |
|------|---------|------|
| 布局 | Tailwind | `flex` `grid` `gap-*` 更简洁 |
| 间距 | Tailwind | `p-4` `m-2` 比 CSS 更快 |
| 复杂组件 | Element Plus | 表单、表格、对话框等 |
| 主题色 | Element Plus | 通过 CSS 变量统一管理 |
| 自定义样式 | Tailwind `@apply` | 保持一致性 |

### 4.4 API 规范

```typescript
// api/request.ts - 统一请求封装
import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
})

// 请求拦截器 - 添加 Token
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器 - 统一错误处理
request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      // 跳转登录
    }
    return Promise.reject(error)
  }
)
```

### 4.5 后端 API 对接

后端 API 位于 `fota-service` 模块，前端直接对接：

| 模块 | 后端路径 | 前端 API 文件 |
|------|---------|--------------|
| 认证 | `/api/sys/auth/*` | `api/auth.ts` |
| 产品 | `/api/products/*` | `api/product.ts` |
| 固件 | `/api/firmware/*` | `api/firmware.ts` |
| 策略 | `/api/policies/*` | `api/policy.ts` |
| 设备 | `/api/devices/*` | `api/device.ts` |
| 用户 | `/api/sys/users/*` | `api/system/user.ts` |
| 角色 | `/api/sys/roles/*` | `api/system/role.ts` |
| 权限 | `/api/sys/permissions/*` | `api/system/permission.ts` |
| 字典 | `/api/sys/dict-*` | `api/system/dict.ts` |

开发环境通过 Vite proxy 代理到后端：

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
```

---

## 5. 核心模块设计

### 5.1 布局系统

```
┌────────────────────────────────────────────────────┐
│  Header (用户信息 / 面包屑 / 全局操作)              │
├──────────┬─────────────────────────────────────────┤
│          │  TabsView (多标签页)                    │
│ Sidebar  ├─────────────────────────────────────────┤
│ (可折叠) │                                         │
│          │  Main Content (路由视图)                │
│          │                                         │
└──────────┴─────────────────────────────────────────┘
```

**布局参数**:
- 侧边栏宽度: `210px`（展开）/ `64px`（折叠）
- 顶栏高度: `50px`
- 内容区背景: `#F5F7FA`
- 最大内容宽度: `1440px`

### 5.2 权限系统

- **路由守卫**: 未登录跳转登录页
- **菜单权限**: 根据 `permissions` 过滤侧边栏菜单
- **按钮权限**: `v-permission` 指令控制按钮显隐
- **API 权限**: 后端校验，前端仅做 UI 隐藏

### 5.3 状态管理

| Store | 职责 |
|-------|------|
| `user` | 用户信息、Token、登录状态 |
| `app` | 侧边栏折叠、标签页列表、主题 |
| `permission` | 用户权限列表、菜单权限 |

---

## 6. 页面规划

### 6.1 页面清单

| 模块 | 页面 | 路由 | 功能 |
|------|------|------|------|
| **认证** | 登录 | `/login` | 用户名密码登录 |
| **Dashboard** | 概览 | `/dashboard` | 统计卡片、趋势图 |
| **产品** | 列表 | `/product` | 产品 CRUD |
| **固件** | 列表 | `/firmware` | 固件版本 CRUD、上传 |
| **策略** | 列表 | `/policy` | 策略 CRUD、灰度配置 |
| **设备** | 列表 | `/device` | 设备查询、标签管理 |
| **设备** | 详情 | `/device/:id` | 设备信息、升级历史 |
| **用户** | 列表 | `/system/user` | 用户 CRUD |
| **角色** | 列表 | `/system/role` | 角色 CRUD、权限分配 |
| **权限** | 树形 | `/system/permission` | 权限树管理 |
| **字典** | 列表 | `/system/dict` | 字典类型、字典项 |

### 6.2 菜单结构

```
📊 Dashboard
📦 产品管理
   └─ 产品列表
💿 固件管理
   └─ 固件版本
📋 策略管理
   └─ 升级策略
📱 设备管理
   ├─ 设备列表
   └─ 设备导入
⚙️ 系统管理
   ├─ 用户管理
   ├─ 角色管理
   ├─ 权限管理
   └─ 字典管理
```

---

## 7. 设计风格

### 7.1 配色方案

| 名称 | 色值 | 用途 |
|------|------|------|
| 主色 | `#409EFF` | 按钮、链接、高亮 |
| 成功 | `#67C23A` | 成功状态 |
| 警告 | `#E6A23C` | 警告状态 |
| 危险 | `#F56C6C` | 错误、删除 |
| 信息 | `#909399` | 次要信息 |
| 背景 | `#F5F7FA` | 页面背景 |
| 侧边栏 | `#304156` | 侧边栏背景 |
| 文字 | `#303133` | 主文字 |

### 8.2 字体

| 类型 | 字体 | 大小 |
|------|------|------|
| 标题 | `-apple-system, BlinkMacSystemFont` | 16-20px |
| 正文 | `Helvetica Neue, Helvetica, PingFang SC` | 14px |
| 代码 | `Monaco, Menlo, Consolas` | 13px |

---

## 8. 部署配置

### 8.1 开发环境

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 访问地址
http://localhost:5173
```

### 8.2 生产环境

```bash
# 构建生产版本
npm run build

# 输出目录
dist/

# 预览构建结果
npm run preview
```

### 8.3 Nginx 配置示例

```nginx
server {
    listen 80;
    server_name fota-admin.example.com;
    
    root /var/www/fota-ui/dist;
    index index.html;
    
    # 静态资源缓存
    location /assets {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    
    # SPA 路由支持
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    # API 代理
    location /api {
        proxy_pass http://fota-backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 9. 相关文档

- [Sprint 2 前端迭代计划](../05-plans/sprint-2-frontend.md)
- [后端架构文档](./fota-architecture.md)
- [产品需求文档](../01-product/prd.md)

---

## 10. 变更日志

### 2026-02-17
- 📝 创建前端架构设计文档
- ✅ 确定技术栈：Vue 3 + Element Plus + TailwindCSS
- ✅ 确定部署方式：独立部署
- ✅ 移除 Mock 策略，改为直接对接后端 API
