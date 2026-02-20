-- 20260220_04_init_fota_permissions.sql
-- 初始化 FOTA 业务模块权限（用于前端路由与后端 RBAC 对齐）
-- 创建日期: 2026-02-20
-- 作者: fota-team

-- ============================================================================
-- 1. 插入 FOTA 业务权限
-- ============================================================================

INSERT INTO sys_permissions (code, name, type, status, created_at, updated_at)
VALUES
  -- 仪表盘
  ('dashboard:view',         '仪表盘-查看',            'API', 'active', now(), now()),

  -- 产品管理
  ('product:read',           '产品管理-查询',          'API', 'active', now(), now()),
  ('product:create',         '产品管理-创建',          'API', 'active', now(), now()),
  ('product:update',         '产品管理-更新',          'API', 'active', now(), now()),
  ('product:delete',         '产品管理-删除',          'API', 'active', now(), now()),

  -- 固件管理
  ('firmware:read',          '固件管理-查询',          'API', 'active', now(), now()),
  ('firmware:create',        '固件管理-创建',          'API', 'active', now(), now()),
  ('firmware:update',        '固件管理-更新',          'API', 'active', now(), now()),
  ('firmware:delete',        '固件管理-删除',          'API', 'active', now(), now()),
  ('firmware:download',      '固件管理-下载',          'API', 'active', now(), now()),

  -- 策略管理
  ('policy:read',            '策略管理-查询',          'API', 'active', now(), now()),
  ('policy:create',          '策略管理-创建',          'API', 'active', now(), now()),
  ('policy:update',          '策略管理-更新',          'API', 'active', now(), now()),
  ('policy:delete',          '策略管理-删除',          'API', 'active', now(), now()),
  ('policy:pause',           '策略管理-暂停',          'API', 'active', now(), now()),

  -- 设备管理
  ('device:read',            '设备管理-查询',          'API', 'active', now(), now()),
  ('device:detail',          '设备管理-详情',          'API', 'active', now(), now()),
  ('device:import',          '设备管理-导入',          'API', 'active', now(), now()),
  ('device:update',          '设备管理-更新',          'API', 'active', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 2. 绑定角色权限
-- ============================================================================

-- 超级管理员 -> FOTA 业务权限（全量）
INSERT INTO sys_role_permission (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, now(), now()
FROM sys_roles r
JOIN sys_permissions p ON p.code IN (
  'dashboard:view',
  'product:read','product:create','product:update','product:delete',
  'firmware:read','firmware:create','firmware:update','firmware:delete','firmware:download',
  'policy:read','policy:create','policy:update','policy:delete','policy:pause',
  'device:read','device:detail','device:import','device:update'
)
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 系统管理员 -> FOTA 业务只读权限
INSERT INTO sys_role_permission (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, now(), now()
FROM sys_roles r
JOIN sys_permissions p ON p.code IN (
  'dashboard:view',
  'product:read',
  'firmware:read',
  'policy:read',
  'device:read','device:detail'
)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 初始化完成
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE 'FOTA 业务权限初始化完成';
    RAISE NOTICE '已新增 19 个业务权限并完成角色绑定';
END $$;
