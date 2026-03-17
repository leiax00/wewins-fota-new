-- 20260227_03_add_policy_lifecycle_states.sql
-- 添加策略生命周期状态：DRAFT, TESTING, VERIFIED
-- 创建日期: 2026-02-27
-- 作者: FOTA Team

-- 更新 upgrade_policies.status 字段注释
COMMENT ON COLUMN upgrade_policies.status IS '策略状态（DRAFT-草稿, TESTING-测试中, VERIFIED-已验证, ACTIVE-生产中, PAUSED-暂停, EXPIRED-过期）';
