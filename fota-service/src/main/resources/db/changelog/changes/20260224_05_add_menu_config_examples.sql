-- 20260224_05_add_menu_config_examples.sql
-- 添加完整的菜单配置示例（包含新增的面包屑和标签页字段）
-- 创建日期: 2026-02-24
-- 作者: fota-team
--
-- 说明：
-- 本脚本为菜单添加完整的配置示例，包括：
-- - breadcrumbHidden: 是否在面包屑中隐藏
-- - tabHidden: 是否在标签页中隐藏
-- - tabClosable: 标签页是否可关闭
--
-- 典型场景：
-- 1. 列表页：正常显示面包屑和标签页
-- 2. 详情页：隐藏面包屑和标签页（通过 activeMenu 高亮列表页菜单）
-- 3. 固定页（如仪表盘）：标签页不可关闭

-- ============================================================================
-- 1. 更新仪表盘配置（固定标签页，不可关闭）
-- ============================================================================

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "affix": true,
  "tabClosable": false,
  "i18nKey": "menu.dashboard"
}'::jsonb
WHERE code = 'fota:dashboard' AND type = 'MENU';

-- ============================================================================
-- 2. 更新列表页配置（正常显示，可缓存，可关闭）
-- ============================================================================

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.product"
}'::jsonb
WHERE code = 'fota:product' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.device"
}'::jsonb
WHERE code = 'fota:device' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.firmware"
}'::jsonb
WHERE code = 'fota:firmware' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.policy"
}'::jsonb
WHERE code = 'fota:policy' AND type = 'MENU';

-- ============================================================================
-- 3. 系统管理菜单配置
-- ============================================================================

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.user"
}'::jsonb
WHERE code = 'sys:user' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.role"
}'::jsonb
WHERE code = 'sys:role' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.permission"
}'::jsonb
WHERE code = 'sys:perm' AND type = 'MENU';

UPDATE sys_permissions
SET menu_config = '{
  "hidden": false,
  "keepAlive": true,
  "tabClosable": true,
  "breadcrumbHidden": false,
  "tabHidden": false,
  "i18nKey": "menu.dict"
}'::jsonb
WHERE code = 'sys:dict_type' AND type = 'MENU';

-- ============================================================================
-- 4. 示例：设备详情页配置（隐藏面包屑和标签页）
--
-- 说明：这是一个假设的示例，展示如何为详情页配置
-- 实际使用时，需要先创建对应的权限记录
--
-- UPDATE sys_permissions
-- SET menu_config = '{
--   "hidden": true,
--   "keepAlive": false,
--   "tabHidden": true,
--   "breadcrumbHidden": true,
--   "activeMenu": "fota:device",
--   "i18nKey": "menu.device.detail"
-- }'::jsonb
-- WHERE code = 'fota:device:detail' AND type = 'MENU';
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '菜单配置示例更新完成';
    RAISE NOTICE '已添加的字段：';
    RAISE NOTICE '  - breadcrumbHidden: 是否在面包屑中隐藏';
    RAISE NOTICE '  - tabHidden: 是否在标签页中隐藏';
    RAISE NOTICE '  - tabClosable: 标签页是否可关闭';
    RAISE NOTICE '';
    RAISE NOTICE '典型配置场景：';
    RAISE NOTICE '  1. 仪表盘：affix=true, tabClosable=false（固定标签页）';
    RAISE NOTICE '  2. 列表页：keepAlive=true, tabClosable=true（可缓存，可关闭）';
    RAISE NOTICE '  3. 详情页：breadcrumbHidden=true, tabHidden=true（隐藏面包屑和标签页）';
END $$;
