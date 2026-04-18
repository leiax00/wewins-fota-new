ALTER TABLE ONLY public.upgrade_policies
    ADD COLUMN affected_total bigint DEFAULT 0 NOT NULL;

COMMENT ON COLUMN public.upgrade_policies.affected_total IS '策略影响设备总数快照';

-- 统计分析分组节点（仅作为权限父节点，不显示在菜单中）
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config", "created_at", "created_by", "updated_at", "updated_by", "deleted")
VALUES (250, 'fota:statistics', '统计分析', 'MENU', NULL, NULL, 20, 'active', NULL, NULL, NULL, NULL, 60, NULL, NULL, '{"hidden": true}', NOW(), NULL, NOW(), NULL, 0);

-- 统计模块 API 权限
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config", "created_at", "created_by", "updated_at", "updated_by", "deleted")
VALUES (1151, 'fota:statistics:read', '统计分析-查询', 'API', NULL, NULL, 250, 'active', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NOW(), NULL, NOW(), NULL, 0);

-- 超级管理员角色 (role_id=1)
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1151);
-- 运维管理员角色 (role_id=2)
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1151);
-- 开发角色 (role_id=3)
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1151);
-- 策略运营角色 (role_id=4)
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1151);

CREATE TABLE public.stat_version_device_count (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id bigint NOT NULL,
    version_id bigint NOT NULL,
    device_count bigint DEFAULT 0 NOT NULL,
    stat_time timestamp without time zone NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT uk_version_stat_time UNIQUE (version_id, stat_time)
);

CREATE INDEX idx_product_stat_time
    ON public.stat_version_device_count USING btree (product_id, stat_time);

COMMENT ON TABLE public.stat_version_device_count IS '固件版本设备数统计（按版本按天）';
