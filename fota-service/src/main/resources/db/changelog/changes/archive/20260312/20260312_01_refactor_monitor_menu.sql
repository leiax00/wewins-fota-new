-- 20260312_01_refactor_monitor_menu.sql
-- 将系统监控调整为父菜单，并拆分为四个子页面

-- 1. 将系统监控改为父级菜单
UPDATE sys_permissions
SET route_path = '/monitor',
    route_name = 'MonitorRoot',
    component_key = 'LAYOUT',
    redirect_path = '/monitor/load',
    menu_sort = 60,
    icon = 'Monitor',
    menu_config = '{
      "hidden": false,
      "alwaysShow": true,
      "keepAlive": false,
      "tabClosable": false,
      "breadcrumbHidden": false,
      "tabHidden": false,
      "i18nKey": "menu.monitor"
    }'::jsonb,
    updated_at = now()
WHERE code = 'fota:monitor' AND type = 'MENU';

-- 2. 新增四个监控子页面
INSERT INTO sys_permissions (id, code, name, type, parent_id, status,
                             route_path, route_name, component_key, menu_sort, icon,
                             created_at, updated_at)
VALUES
  (300, 'fota:monitor:load', '负载监控', 'MENU', 250, 'active',
   '/monitor/load', 'MonitorLoad', 'monitor/load', 10, 'DataLine',
   now(), now()),
  (310, 'fota:monitor:cache', '缓存监控', 'MENU', 250, 'active',
   '/monitor/cache', 'MonitorCache', 'monitor/cache', 20, 'Coin',
   now(), now()),
  (320, 'fota:monitor:log', '日志监控', 'MENU', 250, 'active',
   '/monitor/log', 'MonitorLog', 'monitor/log', 30, 'Document',
   now(), now()),
  (330, 'fota:monitor:operation-log', '操作日志', 'MENU', 250, 'active',
   '/monitor/operation-log', 'MonitorOperationLog', 'monitor/operation-log', 40, 'Tickets',
   now(), now())
ON CONFLICT (id) DO UPDATE SET
  route_path = EXCLUDED.route_path,
  route_name = EXCLUDED.route_name,
  component_key = EXCLUDED.component_key,
  menu_sort = EXCLUDED.menu_sort,
  icon = EXCLUDED.icon,
  updated_at = now();

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": false,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.monitorLoad"
}'::jsonb,
    updated_at = now()
WHERE code = 'fota:monitor:load' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": false,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.monitorCache"
}'::jsonb,
    updated_at = now()
WHERE code = 'fota:monitor:cache' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": false,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.monitorLog"
}'::jsonb,
    updated_at = now()
WHERE code = 'fota:monitor:log' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": false,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.monitorOperationLog"
}'::jsonb,
    updated_at = now()
WHERE code = 'fota:monitor:operation-log' AND type = 'MENU';

-- 3. 角色授权
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (250, 300, 310, 320, 330, 1151, 1152)
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (250, 300, 310, 320, 330, 1151)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 4. 验证
SELECT id, code, name, type, parent_id, route_path, route_name, component_key, redirect_path, menu_sort
FROM sys_permissions
WHERE id IN (250, 300, 310, 320, 330, 1151, 1152)
ORDER BY id;
