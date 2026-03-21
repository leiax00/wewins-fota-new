-- 20260312_03_add_cache_monitor_permissions.sql
-- 为缓存监控子页面补充 read/update API 权限

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  (1155, 'fota:monitor:cache:read', '缓存监控-查看', 'API', 310, 'active', now(), now()),
  (1156, 'fota:monitor:cache:update', '缓存监控-缓存清理', 'API', 310, 'active', now(), now())
ON CONFLICT (id) DO UPDATE SET
  code = EXCLUDED.code,
  name = EXCLUDED.name,
  parent_id = EXCLUDED.parent_id,
  status = EXCLUDED.status,
  updated_at = now();

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (1155, 1156)
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (1155)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT id, code, name, type, parent_id
FROM sys_permissions
WHERE id IN (1155, 1156)
ORDER BY id;
