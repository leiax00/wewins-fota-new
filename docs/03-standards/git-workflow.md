# Git Flow 工作流规范

## 分支模型

本项目采用 **Git Flow** 分支管理模型，确保代码质量和发布稳定性。

```
main (发布分支)
  ↑ 合并（发布时）
  │
develop (开发分支)
  ↑ 合并（功能完成时）
  │
feature/* (功能分支)
```

## 分支说明

### 1. main 分支
- **用途**: 发布分支，只包含稳定版本
- **保护**: ⚠️ **受保护分支，禁止直接推送**
- **合并来源**: 仅接受来自 `develop` 的合并
- **版本标签**: 每次合并后打 tag（如 `v0.1.0`, `v0.2.0`）
- **提交要求**: 必须通过代码审查和测试

### 2. develop 分支
- **用途**: 开发分支，集成的功能开发主线
- **保护**: ⚠️ **受保护分支，禁止直接推送**
- **合并来源**: 仅接受来自 `feature/*` 的合并
- **更新频率**: 功能完成后随时合并
- **稳定性**: 相对稳定，但可能包含未发布的功能

### 3. feature/* 分支
- **用途**: 功能开发分支，从 `develop` 创建
- **命名规范**: `feature/功能描述` 或 `feature/issue-编号`
- **生命周期**: 短期，功能完成后合并到 `develop` 并删除
- **示例**:
  - `feature/database-schema`
  - `feature/device-api`
  - `feature/upgrade-policy`

### 4. hotfix/* 分支（未来）
- **用途**: 紧急修复分支，从 `main` 创建
- **命名规范**: `hotfix/问题描述`
- **生命周期**: 修复后同时合并到 `main` 和 `develop`
- **示例**: `hotfix/security-vulnerability`

---

## 合并策略

### ✅ 推荐使用 `--no-ff`（no fast-forward）

**所有 feature 分支合并到 develop 必须使用 `--no-ff`**

#### 为什么使用 `--no-ff`？

| 特性 | `--no-ff` ✅ | `fast-forward` ❌ |
|------|-------------|------------------|
| 保留功能历史 | ✅ 是 | ❌ 否 |
| 清晰的功能边界 | ✅ 是 | ❌ 否 |
| 易于回滚 | ✅ 是 | ❌ 否 |
| 提交历史线性度 | ⚠️ 稍复杂 | ✅ 简洁 |
| 符合 Git Flow | ✅ 是 | ⚠️ 不推荐 |

#### 何时使用 `--no-ff`？

✅ **必须使用 `--no-ff` 的场景**：
- feature 分支合并到 develop
- hotfix 分支合并到 main/develop
- release 分支合并到 main/develop

⚠️ **可以使用 fast-forward 的场景**：
- 小的 typo 修复
- 文档更新
- 配置文件微调

---

## 标准工作流程

### 开发新功能

```bash
# 1. 从 develop 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feature/your-feature-name

# 2. 开发并提交（常规提交）
git add .
git commit -m "feat: 添加某个功能"

# 3. 功能完成后，切换到 develop
git checkout develop
git pull origin develop

# 4. 合并功能分支（使用 --no-ff）
git merge --no-ff feature/your-feature-name -m "Merge branch 'feature/your-feature-name' into develop

完成功能描述

主要变更：
- 变更点1
- 变更点2

合并提交数：X 个提交

查看详细变更：feature/your-feature-name 分支"

# 5. 推送到远程（如果可推送）
git push origin develop

# 6. 删除已合并的功能分支
git branch -d feature/your-feature-name
# 远程删除（如果需要）
# git push origin --delete feature/your-feature-name
```

### 发布新版本

```bash
# 1. 从 develop 创建 release 分支
git checkout develop
git checkout -b release/v0.1.0

# 2. 发布准备（版本号更新、文档更新等）
vim pom.xml  # 更新版本号
git commit -m "chore: 准备发布 v0.1.0"

# 3. 合并到 main
git checkout main
git merge --no-ff release/v0.1.0 -m "Release v0.1.0"

# 4. 打标签
git tag -a v0.1.0 -m "Release v0.1.0: 功能描述"

# 5. 合并回 develop
git checkout develop
git merge --no-ff release/v0.1.0 -m "Merge release/v0.1.0 back to develop"

# 6. 推送
git push origin main --tags
git push origin develop

# 7. 删除 release 分支
git branch -d release/v0.1.0
```

---

## 提交信息规范

### 提交信息格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Type 类型

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(database): 添加设备表` |
| `fix` | Bug 修复 | `fix(auth): 修复 JWT 解析错误` |
| `refactor` | 重构 | `refactor(maven): 优化模块结构` |
| `docs` | 文档 | `docs(api): 更新 API 文档` |
| `style` | 代码格式 | `style: 修正缩进` |
| `perf` | 性能优化 | `perf(cache): 优化 Redis 查询` |
| `test` | 测试 | `test(mapper): 添加单元测试` |
| `chore` | 构建/工具 | `chore(pom): 升级依赖版本` |

### Scope 范围

常用 scope：
- `database`: 数据库相关
- `api`: API 接口
- `cache`: 缓存相关
- `mq`: 消息队列
- `security`: 安全相关
- `ui`: 前端界面
- `deploy`: 部署相关

### 示例

```bash
# 简单提交
git commit -m "feat(database): 添加设备表结构"

# 详细提交
git commit -m "fix(api): 修复设备查询接口空指针异常

修复前未检查设备是否存在，导致空指针。

修复方案：
- 添加设备存在性检查
- 返回 404 当设备不存在

Closes #123"

# 合并提交
git merge --no-ff feature/database-schema -m "Merge branch 'feature/database-schema' into develop

完成数据库架构搭建

主要变更：
- 创建核心表结构（产品、设备、版本、策略）
- Liquibase changelog 配置
- MyBatis-Plus Mapper 基类

合并提交数：5 个提交

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## 分支保护规则

### main 分支
- ❌ 禁止直接推送
- ✅ 只接受 Pull Request / Merge Request
- ✅ 必须通过代码审查
- ✅ 必须通过 CI/CD 测试

### develop 分支
- ❌ 禁止直接推送
- ✅ 只接受 Pull Request / Merge Request
- ✅ 建议通过 CI/CD 测试

### feature/* 分支
- ✅ 开发者可直接推送
- ✅ 建议定期同步 develop 的最新变更

---

## 版本命名规范

### 版本号格式

采用 **语义化版本**（Semantic Versioning）：

```
主版本号.次版本号.修订号 (MAJOR.MINOR.PATCH)
```

- **MAJOR**: 不兼容的 API 变更
- **MINOR**: 向下兼容的功能性新增
- **PATCH**: 向下兼容的 Bug 修复

### 示例

- `v0.1.0`: 初始版本
- `v0.2.0`: 新增设备管理功能
- `v0.2.1`: 修复设备查询 Bug
- `v1.0.0`: 第一个稳定发布版

---

## 常见问题

### Q1: 如何查看合并历史？

```bash
# 查看图形化历史
git log --graph --oneline --all

# 查看合并提交
git log --merges --oneline
```

### Q2: 如何回滚一个合并？

```bash
# 回滚到合并前的状态（保留历史）
git revert -m 1 <merge-commit-hash>

# 或者重置到合并前（危险！会丢失历史）
# git reset --hard <commit-before-merge>
```

### Q3: 如何解决合并冲突？

```bash
# 1. 合并时遇到冲突
git merge --no-ff feature/your-feature

# 2. 解决冲突
# 编辑冲突文件，解决冲突标记

# 3. 标记冲突已解决
git add <resolved-files>

# 4. 完成合并
git commit  # 或 git merge --continue
```

### Q4: feature 分支可以多人协作吗？

✅ 可以：
```bash
# 开发者 A
git checkout -b feature/database-schema develop

# 推送到远程
git push -u origin feature/database-schema

# 开发者 B 拉取并协作
git checkout feature/database-schema
git pull origin feature/database-schema
```

---

## 最佳实践

### ✅ 推荐做法

1. **功能分支粒度适中**：一个分支一个完整功能
2. **频繁提交**：小步快跑，便于 Code Review
3. **合并前测试**：确保代码可以编译和通过测试
4. **编写清晰的合并信息**：便于追溯和理解
5. **及时删除已合并的分支**：保持分支列表清洁

### ❌ 避免做法

1. ❌ 直接在 main/develop 上开发
2. ❌ 功能分支长期不合并（超过 1 周）
3. ❌ 包含多个不相关功能的分支
4. ❌ 跳过 Code Review 直接合并
5. ❌ 合并信息不清楚或缺少说明

---

## 工具推荐

### Git 图形化工具

- **GitKraken**: 跨平台 Git GUI
- **SourceTree**: 免费 Git GUI（Windows/Mac）
- **IDEA Git**: 集成在 IDEA 中
- **GitHub Desktop**: GitHub 官方客户端

### Git 命令别名

可以在 `~/.gitconfig` 中添加别名：

```bash
[alias]
    co = checkout
    br = branch
    ci = commit
    st = status
    lg = log --graph --oneline --all
    merge-no-ff = merge --no-ff
```

使用：
```bash
git co develop  # 切换分支
git lg          # 查看图形化历史
```

---

## 参考资料

- [Git Flow 介绍](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
- [语义化版本](https://semver.org/lang/zh-CN/)
- [约定式提交](https://www.conventionalcommits.org/zh-hans/)
- [GitHub Flow](https://guides.github.com/introduction/flow/)

---

**最后更新**: 2025-02-05
**维护者**: wewins-fota 团队
