-- 20260220_01_init_fota_permissions.sql
-- 初始化 FOTA 业务模块权限（树形结构 + 层级前缀 code）
-- 创建日期: 2026-02-20
-- 作者: fota-team

-- ============================================================================
-- ID 范围规划（接续系统管理权限）
-- ============================================================================
-- 一级节点（MODULE）：1-99
--   - ID 10: 系统管理模块（已在 20260209_03 中创建）
--   - ID 20: FOTA 业务模块
--
-- 二级节点（MENU）：100-299
--   - ID 100-149: 系统管理子模块（已在 20260209_03 中创建）
--   - ID 200-249: FOTA 业务子模块
--     - ID 200: 仪表盘
--     - ID 210: 产品管理
--     - ID 220: 固件管理
--     - ID 230: 策略管理
--     - ID 240: 设备管理
--   - ID 250-299: 预留给未来业务模块
--
-- 三级节点（API/BUTTON）：1000-1999
--   - ID 1000-1099: 系统管理操作（已在 20260209_03 中创建）
--   - ID 1100-1199: FOTA 业务操作
--     - ID 1101: 仪表盘操作（1个）
--     - ID 1111-1114: 产品管理操作（4个）
--     - ID 1121-1125: 固件管理操作（5个）
--     - ID 1131-1135: 策略管理操作（5个）
--     - ID 1141-1144: 设备管理操作（4个）
--
-- 手动新增：从 2000 开始（在 20260209_03 中已对齐序列）

-- ============================================================================
-- 权限 Code 结构（层级前缀）
-- ============================================================================
-- fota (FOTA 业务)
-- ├── fota:dashboard (仪表盘)
-- │   └── fota:dashboard:view
-- ├── fota:product (产品管理)
-- │   ├── fota:product:read
-- │   ├── fota:product:create
-- │   ├── fota:product:update
-- │   └── fota:product:delete
-- ├── fota:firmware (固件管理)
-- │   ├── fota:firmware:read
-- │   ├── fota:firmware:create
-- │   ├── fota:firmware:update
-- │   ├── fota:firmware:delete
-- │   └── fota:firmware:download
-- ├── fota:policy (策略管理)
-- │   ├── fota:policy:read
-- │   ├── fota:policy:create
-- │   ├── fota:policy:update
-- │   ├── fota:policy:delete
-- │   └── fota:policy:pause
-- └── fota:device (设备管理)
--     ├── fota:device:read
--     ├── fota:device:detail
--     ├── fota:device:import
--     └── fota:device:update

-- ============================================================================
-- 1. 插入一级节点（MODULE）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  (20, 'fota', 'FOTA 业务', 'MODULE', NULL, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. 插入二级节点（MENU）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  -- 仪表盘
  (200, 'fota:dashboard', '仪表盘', 'MENU', 20, 'active', now(), now()),

  -- 产品管理
  (210, 'fota:product', '产品管理', 'MENU', 20, 'active', now(), now()),

  -- 固件管理
  (220, 'fota:firmware', '固件管理', 'MENU', 20, 'active', now(), now()),

  -- 策略管理
  (230, 'fota:policy', '策略管理', 'MENU', 20, 'active', now(), now()),

  -- 设备管理
  (240, 'fota:device', '设备管理', 'MENU', 20, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3. 插入三级节点（API/BUTTON）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  -- 仪表盘操作（1个）
  (1101, 'fota:dashboard:view', '仪表盘-查看', 'API', 200, 'active', now(), now()),

  -- 产品管理操作（4个）
  (1111, 'fota:product:read',   '产品管理-查询', 'API', 210, 'active', now(), now()),
  (1112, 'fota:product:create', '产品管理-创建', 'API', 210, 'active', now(), now()),
  (1113, 'fota:product:update', '产品管理-更新', 'API', 210, 'active', now(), now()),
  (1114, 'fota:product:delete', '产品管理-删除', 'API', 210, 'active', now(), now()),

  -- 固件管理操作（5个）
  (1121, 'fota:firmware:read',     '固件管理-查询',   'API', 220, 'active', now(), now()),
  (1122, 'fota:firmware:create',   '固件管理-创建',   'API', 220, 'active', now(), now()),
  (1123, 'fota:firmware:update',   '固件管理-更新',   'API', 220, 'active', now(), now()),
  (1124, 'fota:firmware:delete',   '固件管理-删除',   'API', 220, 'active', now(), now()),
  (1125, 'fota:firmware:download', '固件管理-下载',   'API', 220, 'active', now(), now()),

  -- 策略管理操作（5个）
  (1131, 'fota:policy:read',   '策略管理-查询',  'API', 230, 'active', now(), now()),
  (1132, 'fota:policy:create', '策略管理-创建',  'API', 230, 'active', now(), now()),
  (1133, 'fota:policy:update', '策略管理-更新',  'API', 230, 'active', now(), now()),
  (1134, 'fota:policy:delete', '策略管理-删除',  'API', 230, 'active', now(), now()),
  (1135, 'fota:policy:pause',  '策略管理-暂停',  'API', 230, 'active', now(), now()),

  -- 设备管理操作（4个）
  (1141, 'fota:device:read',   '设备管理-查询',  'API', 240, 'active', now(), now()),
  (1142, 'fota:device:detail', '设备管理-详情',  'API', 240, 'active', now(), now()),
  (1143, 'fota:device:import', '设备管理-导入',  'API', 240, 'active', now(), now()),
  (1144, 'fota:device:update', '设备管理-更新',  'API', 240, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 4. 绑定角色权限（仅绑定叶子节点）
-- ============================================================================

-- 超级管理员 -> FOTA 业务权限（全量）
INSERT INTO sys_role_permission (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, now(), now()
FROM sys_roles r
JOIN sys_permissions p ON p.type = 'API' AND p.id >= 1100 AND p.id < 1200
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 系统管理员 -> FOTA 业务只读权限
INSERT INTO sys_role_permission (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, now(), now()
FROM sys_roles r
JOIN sys_permissions p ON p.code IN (
  -- 仪表盘（只读）
  'fota:dashboard:view',
  -- 产品管理（只读）
  'fota:product:read',
  -- 固件管理（只读）
  'fota:firmware:read',
  -- 策略管理（只读）
  'fota:policy:read',
  -- 设备管理（只读+详情）
  'fota:device:read','fota:device:detail'
)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 初始化完成
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE 'FOTA 业务权限初始化完成';
    RAISE NOTICE '已新增 25 个权限节点（1 个一级模块 + 5 个二级菜单 + 19 个操作权限）';
    RAISE NOTICE '权限 Code 采用层级前缀结构：fota -> fota:product -> fota:product:read';
    RAISE NOTICE '权限 ID 范围：MODULE(1-99) / MENU(100-299) / API(1000-1999)';
    RAISE NOTICE 'FOTA 业务节点：一级(20) + 二级(200-240) + 三级(1101-1144)';
    RAISE NOTICE '序列已对齐到 2000，后续手动新增从 2000 开始';
END $$;
