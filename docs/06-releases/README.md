# 版本发布指南

本文档描述 FOTA 项目的版本发布流程和相关工具。

## 版本规范

项目采用 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/) 规范：

- **主版本号 (MAJOR)**：不兼容的 API 变更
- **次版本号 (MINOR)**：向后兼容的功能新增
- **修订号 (PATCH)**：向后兼容的问题修复

## 版本更新脚本

项目提供了自动化脚本来更新前后端版本号。

### 脚本位置

```
scripts/update-version.sh
```

### 使用方法

```bash
# 更新为指定版本
./scripts/update-version.sh 0.2.0

# 带后缀的版本号
./scripts/update-version.sh 0.2.0-SNAPSHOT

# 仅更新后端
./scripts/update-version.sh 0.2.0 --no-frontend

# 仅更新前端
./scripts/update-version.sh 0.2.0 --no-backend
```

### 修改的文件

| 文件 | 说明 |
|------|------|
| `pom.xml` | 后端 Maven 版本号 |
| `fota-ui/package.json` | 前端 NPM 版本号 |

---

## 发布流程

### 1. 准备发布

```bash
# 确保在 develop 分支
git checkout develop
git pull origin develop

# 确保工作区干净
git status
```

### 2. 更新版本号

```bash
# 例如：发布 0.2.0 版本
./scripts/update-version.sh 0.2.0
```

### 3. 检查修改

```bash
git diff
```

### 4. 提交版本更新

```bash
git add .
git commit -m "chore: bump version to 0.2.0"
```

### 5. 创建发布分支

```bash
git checkout -b release/v0.2.0
```

### 6. 更新 CHANGELOG

1. 创建版本目录：`docs/06-releases/v0.2.0/`
2. 编写 `release-notes.md`
3. 更新 `docs/06-releases/CHANGELOG.md`
4. 更新 `docs/00-index.md`

### 7. 合并到 main

```bash
git checkout main
git merge release/v0.2.0
```

### 8. 打标签

```bash
git tag -a v0.2.0 -m "Release v0.2.0"
```

### 9. 推送

```bash
git push origin main --tags
```

### 10. 合并回 develop

```bash
git checkout develop
git merge main
git push origin develop
```

---

## 版本目录结构

```
docs/06-releases/
├── CHANGELOG.md          # 版本汇总与概览
├── README.md             # 本文件
├── v0.2.0/
│   └── release-notes.md  # 详细发布说明
└── v0.1.0/
    └── release-notes.md
```

---

## 模板：release-notes.md

```markdown
# v{VERSION} 发布说明

**发布日期**: YYYY-MM-DD
**版本类型**: Major / Minor / Patch
**状态**: ✅ 稳定

---

## 概述

简要描述本版本的主要内容和目标。

---

## 新增功能

### 功能模块 1

- 功能点 1
- 功能点 2

---

## 改进

- 改进点 1
- 改进点 2

---

## 修复

- 修复问题 1

---

## 破坏性变更

> ⚠️ 本版本包含以下破坏性变更，升级前请注意

- 变更说明 1
- 变更说明 2

---

## 升级指南

### 从 v{PREV_VERSION} 升级

1. 步骤 1
2. 步骤 2

---

## 已知问题

- 问题 1

---

## 下版本计划

- 功能 1
- 功能 2

---
```

---

**最后更新**: 2026-03-17
