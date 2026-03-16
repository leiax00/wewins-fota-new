-- 为升级策略补充状态字段

ALTER TABLE upgrade_policies
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE upgrade_policies
SET status = 'ACTIVE'
WHERE status IS NULL;

COMMENT ON COLUMN upgrade_policies.status IS '策略状态（ACTIVE/PAUSED/EXPIRED）';

CREATE INDEX IF NOT EXISTS idx_up_status ON upgrade_policies(status);
