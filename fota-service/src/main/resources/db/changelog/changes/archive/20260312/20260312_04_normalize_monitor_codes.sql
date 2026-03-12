-- 20260312_04_normalize_monitor_codes.sql
-- 统一系统监控权限码：移除 fota: 前缀，并补充日志监控/操作日志查看权限

-- 1. 修正菜单节点 code 和父子关系
UPDATE sys_permissions
SET code = 'monitor',
    parent_id = null,
    updated_at = now()
WHERE id = 30 AND type = 'MENU';

UPDATE sys_permissions
SET code = 'monitor:load',
    parent_id = 30,
    updated_at = now()
WHERE id = 300 AND type = 'MENU';

UPDATE sys_permissions
SET code = 'monitor:cache',
    parent_id = 30,
    updated_at = now()
WHERE id = 310 AND type = 'MENU';

UPDATE sys_permissions
SET code = 'monitor:log',
    parent_id = 30,
    updated_at = now()
WHERE id = 320 AND type = 'MENU';

UPDATE sys_permissions
SET code = 'monitor:operation-log',
    parent_id = 30,
    updated_at = now()
WHERE id = 330 AND type = 'MENU';

-- 2. 统一已有 API 权限 code
UPDATE sys_permissions
SET code = 'monitor:load:read',
    parent_id = 300,
    updated_at = now()
WHERE id = 1153 AND type = 'API';

UPDATE sys_permissions
SET code = 'monitor:load:update',
    parent_id = 300,
    updated_at = now()
WHERE id = 1154 AND type = 'API';

UPDATE sys_permissions
SET code = 'monitor:cache:read',
    parent_id = 310,
    updated_at = now()
WHERE id = 1155 AND type = 'API';

UPDATE sys_permissions
SET code = 'monitor:cache:update',
    parent_id = 310,
    updated_at = now()
WHERE id = 1156 AND type = 'API';

-- 3. 新增日志监控 / 操作日志查看权限
INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  (1157, 'monitor:log:read', '日志监控-查看', 'API', 320, 'active', now(), now()),
  (1158, 'monitor:operation-log:read', '操作日志-查看', 'API', 330, 'active', now(), now())
ON CONFLICT (id) DO UPDATE SET
  code = EXCLUDED.code,
  name = EXCLUDED.name,
  parent_id = EXCLUDED.parent_id,
  status = EXCLUDED.status,
  updated_at = now();

-- 4. 绑定角色权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (30, 300, 310, 320, 330, 1153, 1154, 1155, 1156, 1157, 1158)
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (30, 300, 310, 320, 330, 1153, 1155, 1157, 1158)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 5. 验证
SELECT id, code, name, type, parent_id
FROM sys_permissions
WHERE id IN (30, 300, 310, 320, 330, 1153, 1154, 1155, 1156, 1157, 1158)
ORDER BY id;

-- 删除固件标签中的内部版本字段(移动到了外部字段)
delete from sys_dict_item where id = 10;