-- 20260224_02_add_menu_fields.sql
-- 为 sys_permissions 增加菜单路由字段（最小集）
-- 创建日期: 2026-02-24
-- 作者: fota-team

-- ============================================================================
-- 1) DDL: 新增菜单字段
-- ============================================================================
ALTER TABLE sys_permissions
    ADD COLUMN IF NOT EXISTS route_path VARCHAR(256),
    ADD COLUMN IF NOT EXISTS route_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS component_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS redirect_path VARCHAR(256),
    ADD COLUMN IF NOT EXISTS menu_sort INT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS icon VARCHAR(64),
    ADD COLUMN IF NOT EXISTS external_link VARCHAR(512),
    ADD COLUMN IF NOT EXISTS menu_config JSONB;

-- ============================================================================
-- 2) 字段注释
-- ============================================================================
COMMENT ON COLUMN sys_permissions.route_path IS '路由路径（仅 MODULE/MENU 类型有意义）';
COMMENT ON COLUMN sys_permissions.route_name IS '路由名称（用于前端路由 name）';
COMMENT ON COLUMN sys_permissions.component_key IS '组件标识（前端白名单映射 key）';
COMMENT ON COLUMN sys_permissions.redirect_path IS '重定向路径';
COMMENT ON COLUMN sys_permissions.menu_sort IS '同级菜单排序（越小越靠前）';
COMMENT ON COLUMN sys_permissions.icon IS '菜单图标（Element Plus 图标名）';
COMMENT ON COLUMN sys_permissions.external_link IS '外链 URL（仅允许 https）';
COMMENT ON COLUMN sys_permissions.menu_config IS '菜单扩展配置 JSON（hidden/keepAlive/affix 等）';

-- ============================================================================
-- 3) 初始化现有菜单数据
--    仅初始化 type IN ('MODULE', 'MENU')
-- ============================================================================

-- 3.1 系统管理模块（sys）
UPDATE sys_permissions
SET route_path    = '/system',
    route_name    = 'SystemRoot',
    component_key = 'LAYOUT',
    redirect_path = '/system/user',
    menu_sort     = 90,
    icon          = 'Setting',
    menu_config   = '{"hidden": false, "alwaysShow": true, "i18nKey": "menu.system"}'::jsonb
WHERE code = 'sys' AND type = 'MODULE';

UPDATE sys_permissions
SET route_path    = '/system/user',
    route_name    = 'SystemUser',
    component_key = 'system/user/index',
    menu_sort     = 10,
    icon          = 'User',
    menu_config   = '{"hidden": false, "keepAlive": true, "i18nKey": "menu.user"}'::jsonb
WHERE code = 'sys:user' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/system/role',
    route_name    = 'SystemRole',
    component_key = 'system/role/index',
    menu_sort     = 20,
    icon          = 'UserFilled',
    menu_config   = '{"hidden": false, "keepAlive": true, "i18nKey": "menu.role"}'::jsonb
WHERE code = 'sys:role' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/system/permission',
    route_name    = 'SystemPermission',
    component_key = 'system/permission/index',
    menu_sort     = 30,
    icon          = 'Lock',
    menu_config   = '{"hidden": false, "keepAlive": true, "i18nKey": "menu.permission"}'::jsonb
WHERE code = 'sys:perm' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/system/dict',
    route_name    = 'SystemDict',
    component_key = 'system/dict/index',
    menu_sort     = 40,
    icon          = 'Collection',
    menu_config   = '{"hidden": false, "keepAlive": true, "i18nKey": "menu.dict"}'::jsonb
WHERE code = 'sys:dict_type' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/system/dict/:id/items',
    route_name    = 'SystemDictItems',
    component_key = 'system/dict-items/index',
    menu_sort     = 50,
    icon          = 'Tickets',
    menu_config   = '{
      "hidden": true,
      "i18nKey": "menu.dict_item_prefix",
      "keepAlive": false,
      "tabHidden": true,
      "breadcrumbHidden": false,
      "activeMenu": "sys:dict_type"
    }'::jsonb
WHERE code = 'sys:dict_item' AND type = 'MENU';

-- 3.2 FOTA 业务模块（fota）
UPDATE sys_permissions
SET route_path    = '/fota',
    route_name    = 'FotaRoot',
    component_key = 'LAYOUT',
    redirect_path = '/dashboard',
    menu_sort     = 10,
    icon          = 'Promotion',
    menu_config   = '{"hidden": true, "alwaysShow": false}'::jsonb
WHERE code = 'fota' AND type = 'MODULE';

UPDATE sys_permissions
SET route_path    = '/dashboard',
    route_name    = 'Dashboard',
    component_key = 'dashboard/index',
    menu_sort     = 10,
    icon          = 'Odometer',
    menu_config   = '{
      "hidden": false,
      "keepAlive": true,
      "affix": true,
      "tabClosable": false,
      "i18nKey": "menu.dashboard"
    }'::jsonb
WHERE code = 'fota:dashboard' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/product',
    route_name    = 'Product',
    component_key = 'product/index',
    menu_sort     = 20,
    icon          = 'Box',
    menu_config   = '{
      "hidden": false,
      "keepAlive": true,
      "tabClosable": true,
      "i18nKey": "menu.product"
    }'::jsonb
WHERE code = 'fota:product' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/device',
    route_name    = 'Device',
    component_key = 'device/index',
    menu_sort     = 30,
    icon          = 'Iphone',
    menu_config   = '{"hidden": false, "keepAlive": true, "i18nKey": "menu.device"}'::jsonb
WHERE code = 'fota:device' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/firmware',
    route_name    = 'Firmware',
    component_key = 'firmware/index',
    menu_sort     = 40,
    icon          = 'Cpu',
    menu_config   = '{"hidden": false, "keepAlive": true, "i18nKey": "menu.firmware"}'::jsonb
WHERE code = 'fota:firmware' AND type = 'MENU';

UPDATE sys_permissions
SET route_path    = '/policy',
    route_name    = 'Policy',
    component_key = 'policy/index',
    menu_sort     = 50,
    icon          = 'Document',
    menu_config   = '{"hidden": false, "keepAlive": true, "i18nKey": "menu.policy"}'::jsonb
WHERE code = 'fota:policy' AND type = 'MENU';

-- ============================================================================
-- 4) 验证脚本
-- ============================================================================

-- A. 查看 MODULE/MENU 初始化结果
SELECT id, code, type, route_path, route_name, component_key, redirect_path, menu_sort, icon, external_link, menu_config
FROM sys_permissions
WHERE type IN ('MODULE', 'MENU')
ORDER BY id;

-- B. API 类型菜单字段应保持 NULL（或默认 0）
SELECT
    COUNT(*) FILTER (WHERE route_path IS NOT NULL) AS api_route_path_not_null,
    COUNT(*) FILTER (WHERE route_name IS NOT NULL) AS api_route_name_not_null,
    COUNT(*) FILTER (WHERE component_key IS NOT NULL) AS api_component_key_not_null,
    COUNT(*) FILTER (WHERE redirect_path IS NOT NULL) AS api_redirect_path_not_null,
    COUNT(*) FILTER (WHERE icon IS NOT NULL) AS api_icon_not_null,
    COUNT(*) FILTER (WHERE external_link IS NOT NULL) AS api_external_link_not_null,
    COUNT(*) FILTER (WHERE menu_config IS NOT NULL) AS api_menu_config_not_null
FROM sys_permissions
WHERE type = 'API';

-- C. 外链协议校验（应为 0）
SELECT COUNT(*) AS invalid_external_link_count
FROM sys_permissions
WHERE external_link IS NOT NULL
  AND external_link !~* '^https://';

DO $$
BEGIN
    RAISE NOTICE '20260224_02_add_menu_fields 执行完成';
END $$;
