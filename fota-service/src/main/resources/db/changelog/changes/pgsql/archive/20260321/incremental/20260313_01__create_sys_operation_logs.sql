CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS sys_operation_logs
(
    id                    BIGSERIAL PRIMARY KEY,
    module_code           VARCHAR(64)  NOT NULL,
    resource_code         VARCHAR(64)  NOT NULL,
    action_code           VARCHAR(128) NOT NULL,
    operation_type        VARCHAR(32)  NOT NULL,
    target_id             VARCHAR(128),
    target_name           VARCHAR(255),
    operator_id           BIGINT,
    operator_username     VARCHAR(128),
    operator_display_name VARCHAR(128),
    request_method        VARCHAR(16)  NOT NULL,
    request_path          VARCHAR(255) NOT NULL,
    request_query         JSONB,
    request_body          JSONB,
    client_ip             VARCHAR(64),
    user_agent            VARCHAR(1024),
    occurred_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE sys_operation_logs IS '后台操作日志表';
COMMENT ON COLUMN sys_operation_logs.module_code IS '模块编码';
COMMENT ON COLUMN sys_operation_logs.resource_code IS '资源编码';
COMMENT ON COLUMN sys_operation_logs.action_code IS '动作编码';
COMMENT ON COLUMN sys_operation_logs.operation_type IS '操作类型';
COMMENT ON COLUMN sys_operation_logs.target_id IS '操作目标ID';
COMMENT ON COLUMN sys_operation_logs.target_name IS '操作目标名称';
COMMENT ON COLUMN sys_operation_logs.operator_id IS '操作人ID';
COMMENT ON COLUMN sys_operation_logs.operator_username IS '操作人用户名';
COMMENT ON COLUMN sys_operation_logs.operator_display_name IS '操作人显示名';
COMMENT ON COLUMN sys_operation_logs.request_method IS 'HTTP方法';
COMMENT ON COLUMN sys_operation_logs.request_path IS '请求路径';
COMMENT ON COLUMN sys_operation_logs.request_query IS '请求查询参数';
COMMENT ON COLUMN sys_operation_logs.request_body IS '请求体内容';
COMMENT ON COLUMN sys_operation_logs.client_ip IS '客户端IP';
COMMENT ON COLUMN sys_operation_logs.user_agent IS '客户端标识';
COMMENT ON COLUMN sys_operation_logs.occurred_at IS '发生时间';

CREATE INDEX IF NOT EXISTS idx_sys_operation_logs_occurred_at
    ON sys_operation_logs (occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_sys_operation_logs_operator_time
    ON sys_operation_logs (operator_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_sys_operation_logs_module_time
    ON sys_operation_logs (module_code, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_sys_operation_logs_action_time
    ON sys_operation_logs (action_code, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_sys_operation_logs_operator_username_trgm
    ON sys_operation_logs USING GIN (operator_username gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_sys_operation_logs_operator_display_name_trgm
    ON sys_operation_logs USING GIN (operator_display_name gin_trgm_ops);
