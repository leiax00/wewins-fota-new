CREATE TABLE IF NOT EXISTS async_task
(
    id         BIGSERIAL PRIMARY KEY,
    biz_type   VARCHAR(64),
    biz_id     VARCHAR(64),
    stage      VARCHAR(32)  NOT NULL,
    percent    INT          NOT NULL DEFAULT 0,
    message    VARCHAR(256),
    error_msg  TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    created_by BIGINT,
    updated_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_by BIGINT
);

CREATE INDEX IF NOT EXISTS idx_async_task_biz_type_biz_id
    ON async_task (biz_type, biz_id);
