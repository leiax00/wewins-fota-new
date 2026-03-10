-- 20260309_01_add_monitor_permissions.sql
-- 添加系统监控模块权限（Sprint 4）
-- 创建日期: 2026-03-09
-- 作者: fota-team
-- 
-- 说明：
-- Sprint 4 实现了系统监控仪表盘功能，需要添加相应的权限配置
-- 监控功能包括：
-- - 实时系统负载查看
-- - 控制参数调整
-- - Sentinel限流熔断状态查看

-- ============================================================================
-- 1. 插入二级节点（MENU）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, 
                             route_path, route_name, component_key, menu_sort, icon,
                             created_at, updated_at)
VALUES
  (250, 'fota:monitor', '系统监控', 'MENU', 20, 'active', 
   '/monitor', 'Monitor', 'monitor/index', 60, 'Monitor',
   now(), now())
ON CONFLICT (id) DO UPDATE SET
  route_path = EXCLUDED.route_path,
  route_name = EXCLUDED.route_name,
  component_key = EXCLUDED.component_key,
  menu_sort = EXCLUDED.menu_sort,
  icon = EXCLUDED.icon,
  updated_at = now();

-- ============================================================================
-- 2. 插入三级节点（API/BUTTON）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  (1151, 'fota:monitor:read',   '系统监控-查看',    'API', 250, 'active', now(), now()),
  (1152, 'fota:monitor:update', '系统监控-参数调整', 'API', 250, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3. 绑定角色权限
-- ============================================================================

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (250, 1151, 1152)
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (250, 1151)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 4. 更新菜单配置
-- ============================================================================

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": false,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.monitor"
}'::jsonb
WHERE code = 'fota:monitor' AND type = 'MENU';

-- ============================================================================
-- 验证脚本
-- ============================================================================

SELECT id, code, name, type, route_path, route_name, component_key, menu_sort, icon
FROM sys_permissions
WHERE id IN (250, 1151, 1152)
ORDER BY id;

SELECT r.code AS role_code, r.name AS role_name, p.code AS permission_code, p.name AS permission_name
FROM sys_roles r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permissions p ON p.id = rp.permission_id
WHERE p.id IN (250, 1151, 1152)
ORDER BY r.code, p.id;

DO $$
BEGIN
    RAISE NOTICE '系统监控权限添加完成';
    RAISE NOTICE '已新增 3 个权限节点：';
    RAISE NOTICE '  - 250: fota:monitor (MENU) - /monitor';
    RAISE NOTICE '  - 1151: fota:monitor:read (API)';
    RAISE NOTICE '  - 1152: fota:monitor:update (API)';
    RAISE NOTICE '权限已绑定到 super_wewins 和 wewins 角色';
    RAISE NOTICE '菜单已配置路由、组件和图标';
END $$;
