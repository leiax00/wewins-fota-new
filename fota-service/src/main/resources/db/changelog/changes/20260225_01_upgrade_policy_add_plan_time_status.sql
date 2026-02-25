-- 为升级策略补充计划时间与状态字段

ALTER TABLE upgrade_policies
    ADD COLUMN IF NOT EXISTS plan_time TIMESTAMP;

ALTER TABLE upgrade_policies
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE upgrade_policies
SET status = 'ACTIVE'
WHERE status IS NULL;

COMMENT ON COLUMN upgrade_policies.plan_time IS '计划时间';
COMMENT ON COLUMN upgrade_policies.status IS '策略状态（ACTIVE/PAUSED/EXPIRED）';

CREATE INDEX IF NOT EXISTS idx_up_status ON upgrade_policies(status);
CREATE INDEX IF NOT EXISTS idx_up_plan_time ON upgrade_policies(plan_time);
