-- 20260224_03_bind_menu_permissions.sql
-- 为角色补充 MODULE/MENU 类型的菜单权限
-- 创建日期: 2026-02-24
-- 作者: fota-team
--
-- 说明：
-- 之前的初始化脚本只为角色绑定了 API 类型的叶子节点权限
-- 根据业务约定"选中子节点必定选中父节点"，需要为角色补充 MODULE/MENU 权限
--
-- 执行后，角色将同时拥有：
-- - API 权限（叶子节点，原有）
-- - MENU 权限（菜单节点，新增）
-- - MODULE 权限（模块节点，新增）

-- ============================================================================
-- 1. 系统管理模块权限补充
-- ============================================================================

-- 超级管理员 -> 系统管理全部权限（MODULE + MENU + API）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.type IN ('MODULE', 'MENU')
  AND p.id IN (
    -- 系统管理模块
    10,  -- sys (MODULE)
    -- 系统管理菜单
    100, -- sys:user (MENU)
    110, -- sys:role (MENU)
    120, -- sys:perm (MENU)
    130  -- sys:dict_type (MENU)
    -- 注意：sys:dict_item 是隐藏菜单，可选
  )
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 系统管理员 -> 系统管理权限（MODULE + MENU + 只读 API）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.type IN ('MODULE', 'MENU')
  AND p.id IN (
    -- 系统管理模块
    10,  -- sys (MODULE)
    -- 系统管理菜单（只读）
    100, -- sys:user (MENU)
    110, -- sys:role (MENU)
    120, -- sys:perm (MENU)
    130  -- sys:dict_type (MENU)
  )
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 2. FOTA 业务模块权限补充
-- ============================================================================

-- 超级管理员 -> FOTA 业务全部权限（MODULE + MENU + API）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.type IN ('MODULE', 'MENU')
  AND p.id >= 20 AND p.id < 300  -- FOTA 业务模块 ID 范围
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 系统管理员 -> FOTA 业务只读权限（MENU + 只读 API）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.type IN ('MODULE', 'MENU')
  AND p.id IN (
    -- FOTA 业务模块（可选，不需要作为分组显示）
    -- 20,  -- fota (MODULE) - 如果需要显示 FOTA 分组则取消注释
    -- FOTA 业务菜单
    200, -- fota:dashboard (MENU)
    210, -- fota:product (MENU)
    220, -- fota:firmware (MENU)
    230, -- fota:policy (MENU)
    240  -- fota:device (MENU)
  )
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 3. 验证脚本
-- ============================================================================

-- 查看每个角色的权限分布（按类型分组）
SELECT
    r.code AS role_code,
    r.name AS role_name,
    p.type AS permission_type,
    COUNT(*) AS permission_count
FROM sys_roles r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permissions p ON p.id = rp.permission_id
GROUP BY r.code, r.name, p.type
ORDER BY r.code, p.type;

-- 查看超级管理员的 MODULE/MENU 权限
SELECT p.id, p.code, p.type, p.name
FROM sys_roles r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permissions p ON p.id = rp.permission_id
WHERE r.code = 'super_wewins'
  AND p.type IN ('MODULE', 'MENU')
ORDER BY p.id;

-- 查看系统管理员的 MODULE/MENU 权限
SELECT p.id, p.code, p.type, p.name
FROM sys_roles r
JOIN sys_role_permission rp ON rp.role_id = r.id
JOIN sys_permissions p ON p.id = rp.permission_id
WHERE r.code = 'wewins'
  AND p.type IN ('MODULE', 'MENU')
ORDER BY p.id;

DO $$
BEGIN
    RAISE NOTICE '角色菜单权限补充完成';
    RAISE NOTICE '现在角色将同时拥有：';
    RAISE NOTICE '  1. API 权限（叶子节点，原有）';
    RAISE NOTICE '  2. MENU 权限（菜单节点，新增）';
    RAISE NOTICE '  3. MODULE 权限（模块节点，新增）';
    RAISE NOTICE '符合"选中子节点必定选中父节点"的业务约定';
END $$;
