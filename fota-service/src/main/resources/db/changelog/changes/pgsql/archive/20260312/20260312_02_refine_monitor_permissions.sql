-- 20260312_02_refine_monitor_permissions.sql
-- 将监控接口权限下沉到各子页面，移除父级 monitor read/update API 权限

-- 1. 为负载监控子页面新增 API 权限
INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  (1153, 'fota:monitor:load:read', '负载监控-查看', 'API', 300, 'active', now(), now()),
  (1154, 'fota:monitor:load:update', '负载监控-参数调整', 'API', 300, 'active', now(), now())
ON CONFLICT (id) DO UPDATE SET
  code = EXCLUDED.code,
  name = EXCLUDED.name,
  parent_id = EXCLUDED.parent_id,
  status = EXCLUDED.status,
  updated_at = now();

-- 2. 绑定角色权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (1153, 1154)
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (1153)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 3. 删除旧的父级 monitor API 权限和关联
DELETE FROM sys_role_permission
WHERE permission_id IN (1151, 1152);

DELETE FROM sys_permissions
WHERE id IN (1151, 1152)
  AND type = 'API';

-- 4. 验证
SELECT id, code, name, type, parent_id
FROM sys_permissions
WHERE id IN (1151, 1152, 1153, 1154)
ORDER BY id;
