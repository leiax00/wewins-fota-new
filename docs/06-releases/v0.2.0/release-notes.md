# v0.2.0 发布说明

**发布日期**: 2026-03-25
**版本类型**: Minor
**状态**: ✅ 稳定

---

## 概述

本版本是一个重要的功能更新版本，主要新增了 **MySQL 数据库支持** 和 **CDN 预热功能**。现在系统可以同时支持 PostgreSQL 和 MySQL 两种数据库，为用户提供了更灵活的部署选择。同时新增的 CDN 预热功能可以显著提升固件分发的效率。

---

## 新增功能

### 数据库多方言支持

- 支持 PostgreSQL 和 MySQL 双数据库方言
- 数据库迁移脚本支持多方言
- 批量写入适配双方言

### CDN 预热功能

- 增加 CDN 预热及异步任务功能
- 支持预热策略选择（Worker / Edge）
- 统一预热错误消息管理

### 设备管理增强

- 增加 `extTags` 扩展参数，支持设备属性筛选
- 设备列表点击行展开详情
- Excel IMEI 列智能识别及上传状态清理

### 固件管理增强

- 固件列表增加行展开详情功能
- 支持 SSE 断开处理优化
- 支持直接创建无包版本
- I18nField 优化为内联编辑模式

### 存储扩展

- URL 访问模式支持 Cloudflare R2 自定义域名

---

## 改进

- 设备 check 流程优化，设备当前版本信息更完善
- 源版本匹配机制优化
- 页面优化，默认不缓存页面
- PostgreSQL 和 MySQL 功能对齐
- 系统管理员权限配置调整
- 创建版本时接口超时时间优化为 1 小时

---

## 修复

- 修复合并版本信息时的空指针异常
- 修复 MySQL 情况下 JsonNodeTypeHandler 无法正常解析
- 修复关联表写入不正确问题
- 修复上传组件缺失 http-request 属性导致无法上传
- 修复 SSE 错误处理资源泄漏和超时处理不一致问题
- 修复清理临时文件正则不匹配的问题
- 修复 keep-alive 缓存时定时器未停止的问题
- 修复设备窗口限流时重试间隔为负数的问题
- CDN 预热时偶尔无法获取到失败原因

---

## 破坏性变更

> ⚠️ 本版本包含以下破坏性变更，升级前请注意

### 数据库架构变更

本版本重构了数据库模型，使用关联表作为主模型，读取路径不再依赖 JSON 数据。

**升级步骤**：
1. 备份数据库
2. 执行新的迁移脚本
3. 验证数据完整性

---

## 升级指南

### 从 v0.1.0 升级

1. **备份数据库**
   ```bash
   pg_dump fota > fota_backup.sql
   ```

2. **拉取最新代码**
   ```bash
   git fetch origin
   git checkout v0.2.0
   ```

3. **更新配置**
   - 如需使用 MySQL，更新 `application.yml` 中的数据库配置
   - 配置 CDN 预热相关参数（如需要）

4. **执行数据库迁移**
   ```bash
   # PostgreSQL
   ./mvnw flyway:migrate -Ppgsql

   # 或 MySQL
   ./mvnw flyway:migrate -Pmysql
   ```

5. **重启服务**
   ```bash
   docker-compose down && docker-compose up -d
   ```

---

## 已知问题

- 暂无

---

## 贡献者

感谢所有为本版本做出贡献的开发者！

---

**完整更新日志**: 查看 [GitHub Releases](https://github.com/wewins/wewins-fota-new/releases/tag/v0.2.0)
