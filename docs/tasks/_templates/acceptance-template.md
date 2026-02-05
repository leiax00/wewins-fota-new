# Week 1 验收报告

- 周期：
- 负责人：
- 执行模式：3-5人 / 6+人

## 1. Must-have 验收

| 项目 | 目标 | 结果(Pass/Fail) | 证据 | 备注 |
|---|---|---|---|---|
| main/region 启动 | MODE=main/region 可启动 | | | 连续 5 次成功 |
| PostgreSQL 迁移 | 迁移脚本幂等执行 | | | 2 轮无错误 |
| Redis key 规范 | key 设计冻结 v1 | | | 文档已产出 |
| JWT 登录鉴权 | 登录 API 可用 | | | p95 < 200ms |
| 最小授权模型 | ADMIN/USER 生效 | | | 写操作拦截 100% |
| Seed 数据 | 可一键导入 | | | 幂等验证通过 |

## 2. 完成率统计

- Must-have 完成率：___ / 6 = ___%
- Nice-to-have 完成率：___%

## 3. 性能与稳定性快照

- 登录 API 峰值 RPS：___
- 登录 API p95 延迟：___ ms
- 登录 API 错误率：___%
- 启动成功率：___%

## 4. 风险与阻塞

| 风险ID | 描述 | 影响 | 责任人 | 状态 |
|---|---|---|---|---|
| R001 | PostgreSQL 学习曲线 | 首周延期 | | Open/Mitigated/Closed |
| R002 | Redis 连接配置问题 | 联调阻塞 | | Open/Mitigated/Closed |

## 5. 下周输入

### 必做项
- [ ] 核心表迁移完成
- [ ] Redis 缓存链路可用
- [ ] JWT 鉴权集成

### 顺延项
- [ ] Lua 脚本优化
- [ ] 监控看板完善

### 依赖项
- [ ] PostgreSQL 生产环境确认
- [ ] Redis 集群配置

## 6. 附录

- 证据文档链接：
- 压测报告链接：
- 风险登记表链接：`docs/risk-log.md`
