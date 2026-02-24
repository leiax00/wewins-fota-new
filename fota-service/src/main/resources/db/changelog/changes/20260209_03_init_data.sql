-- 20260209000003_init_data.sql
-- 初始化系统管理模块数据
-- 创建日期: 2026-02-09
-- 作者: FOTA 团队
--
-- 包含：
-- 1. 系统权限（树形结构：1个一级模块 + 5个二级菜单 + 22个操作权限）
-- 2. 系统角色（超级管理员、系统管理员）
-- 3. 默认管理员账户（wewins/wewins@2026）
-- 4. 角色权限关联
-- 5. 用户角色关联
-- 6. 系统字典类型和字典项

-- ============================================================================
-- ID 范围规划
-- ============================================================================
-- 一级节点（MODULE）：1-99
--   - ID 10: 系统管理模块
--
-- 二级节点（MENU）：100-299
--   - ID 100-149: 系统管理子模块
--     - ID 100: 用户管理
--     - ID 110: 角色管理
--     - ID 120: 权限管理
--     - ID 130: 字典类型管理
--     - ID 140: 字典项管理
--   - ID 200-299: FOTA 业务子模块（预留）
--
-- 三级节点（API/BUTTON）：1000-1999
--   - ID 1000-1099: 系统管理操作
--     - ID 1001-1005: 用户管理操作（5个）
--     - ID 1011-1015: 角色管理操作（5个）
--     - ID 1021-1024: 权限管理操作（4个）
--     - ID 1031-1034: 字典类型操作（4个）
--     - ID 1041-1044: 字典项操作（4个）
--   - ID 1100-1199: FOTA 业务操作（预留）
--
-- 手动新增：从 2000 开始

-- ============================================================================
-- 1. 插入一级节点（MODULE）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  (10, 'sys', '系统管理', 'MODULE', NULL, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. 插入二级节点（MENU）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  -- 用户管理
  (100, 'sys:user', '用户管理', 'MENU', 10, 'active', now(), now()),

  -- 角色管理
  (110, 'sys:role', '角色管理', 'MENU', 10, 'active', now(), now()),

  -- 权限管理
  (120, 'sys:perm', '权限管理', 'MENU', 10, 'active', now(), now()),

  -- 字典类型管理
  (130, 'sys:dict_type', '字典类型管理', 'MENU', 10, 'active', now(), now()),

  -- 字典项管理
  (140, 'sys:dict_item', '字典项管理', 'MENU', 10, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 3. 插入三级节点（API/BUTTON）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  -- 用户管理操作（5个）
  (1001, 'sys:user:read',        '用户管理-查询',        'API', 100, 'active', now(), now()),
  (1002, 'sys:user:create',      '用户管理-创建',        'API', 100, 'active', now(), now()),
  (1003, 'sys:user:update',      '用户管理-更新',        'API', 100, 'active', now(), now()),
  (1004, 'sys:user:delete',      '用户管理-删除',        'API', 100, 'active', now(), now()),
  (1005, 'sys:user:assign_role', '用户管理-分配角色',     'API', 100, 'active', now(), now()),

  -- 角色管理操作（5个）
  (1011, 'sys:role:read',        '角色管理-查询',        'API', 110, 'active', now(), now()),
  (1012, 'sys:role:create',      '角色管理-创建',        'API', 110, 'active', now(), now()),
  (1013, 'sys:role:update',      '角色管理-更新',        'API', 110, 'active', now(), now()),
  (1014, 'sys:role:delete',      '角色管理-删除',        'API', 110, 'active', now(), now()),
  (1015, 'sys:role:assign_perm', '角色管理-分配权限',     'API', 110, 'active', now(), now()),

  -- 权限管理操作（4个）
  (1021, 'sys:perm:read',        '权限管理-查询',        'API', 120, 'active', now(), now()),
  (1022, 'sys:perm:create',      '权限管理-创建',        'API', 120, 'active', now(), now()),
  (1023, 'sys:perm:update',      '权限管理-更新',        'API', 120, 'active', now(), now()),
  (1024, 'sys:perm:delete',      '权限管理-删除',        'API', 120, 'active', now(), now()),

  -- 字典类型管理操作（4个）
  (1031, 'sys:dict_type:read',   '字典类型-查询',        'API', 130, 'active', now(), now()),
  (1032, 'sys:dict_type:create', '字典类型-创建',        'API', 130, 'active', now(), now()),
  (1033, 'sys:dict_type:update', '字典类型-更新',        'API', 130, 'active', now(), now()),
  (1034, 'sys:dict_type:delete', '字典类型-删除',        'API', 130, 'active', now(), now()),

  -- 字典项管理操作（4个）
  (1041, 'sys:dict_item:read',   '字典项-查询',          'API', 140, 'active', now(), now()),
  (1042, 'sys:dict_item:create', '字典项-创建',          'API', 140, 'active', now(), now()),
  (1043, 'sys:dict_item:update', '字典项-更新',          'API', 140, 'active', now(), now()),
  (1044, 'sys:dict_item:delete', '字典项-删除',          'API', 140, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 4. 插入系统角色
-- ============================================================================

INSERT INTO sys_roles (code, name, description, status, created_at, updated_at)
VALUES
  ('super_wewins', '超级管理员', '拥有系统所有权限', 'active', now(), now()),
  ('wewins',       '系统管理员', '拥有系统管理权限', 'active', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 5. 绑定角色权限（仅绑定叶子节点）
-- ============================================================================

-- 超级管理员 -> 全部叶子权限（API 类型）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.type = 'API'
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 系统管理员 -> 系统管理权限（除角色-权限分配外）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.code IN (
  -- 用户管理（全权限）
  'sys:user:read','sys:user:create','sys:user:update','sys:user:delete','sys:user:assign_role',
  -- 角色管理（只读）
  'sys:role:read','sys:role:create','sys:role:update','sys:role:delete',
  -- 权限管理（只读）
  'sys:perm:read',
  -- 字典类型（全权限）
  'sys:dict_type:read','sys:dict_type:create','sys:dict_type:update','sys:dict_type:delete',
  -- 字典项（全权限）
  'sys:dict_item:read','sys:dict_item:create','sys:dict_item:update','sys:dict_item:delete'
)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 6. 插入默认管理员账户
-- ============================================================================

-- wewins@2026 的 BCrypt hash
INSERT INTO sys_users (id, username, password_hash, display_name, status, created_at, updated_at)
VALUES
  (1, 'wewins', '$2a$10$EZXnfjmgPCoizFOQcy/YneL9btA2u5jecupZ2MmAbNP.LhAoqj22m', '系统管理员', 'active', now(), now())
ON CONFLICT (username) DO NOTHING;

-- 绑定用户与角色（wewins -> super_wewins）
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_users u
JOIN sys_roles r ON r.code = 'super_wewins'
WHERE u.username = 'wewins'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================================================
-- 7. 插入系统字典类型
-- ============================================================================

INSERT INTO sys_dict_type (code, name, status, description, created_at, updated_at)
VALUES
  ('user_status', '用户状态', 'active', '用户账户状态', now(), now()),
  ('role_status', '角色状态', 'active', '角色状态', now(), now()),
  ('perm_status', '权限状态', 'active', '权限状态', now(), now()),
  ('region_bootstrap_secret', '分区初始密钥', 'active', '分区首次握手使用的初始密钥', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 8. 插入系统字典项
-- ============================================================================

-- 用户状态字典项
INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, created_at, updated_at)
SELECT t.id, '正常', 'active', 1, 'active', now(), now()
FROM sys_dict_type t WHERE t.code = 'user_status'
ON CONFLICT (dict_type_id, value) DO NOTHING;

INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, created_at, updated_at)
SELECT t.id, '禁用', 'disabled', 2, 'active', now(), now()
FROM sys_dict_type t WHERE t.code = 'user_status'
ON CONFLICT (dict_type_id, value) DO NOTHING;

INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, created_at, updated_at)
SELECT t.id, '锁定', 'locked', 3, 'active', now(), now()
FROM sys_dict_type t WHERE t.code = 'user_status'
ON CONFLICT (dict_type_id, value) DO NOTHING;

-- 角色状态字典项
INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, created_at, updated_at)
SELECT t.id, '正常', 'active', 1, 'active', now(), now()
FROM sys_dict_type t WHERE t.code = 'role_status'
ON CONFLICT (dict_type_id, value) DO NOTHING;

INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, created_at, updated_at)
SELECT t.id, '禁用', 'disabled', 2, 'active', now(), now()
FROM sys_dict_type t WHERE t.code = 'role_status'
ON CONFLICT (dict_type_id, value) DO NOTHING;

-- 权限状态字典项
INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, created_at, updated_at)
SELECT t.id, '正常', 'active', 1, 'active', now(), now()
FROM sys_dict_type t WHERE t.code = 'perm_status'
ON CONFLICT (dict_type_id, value) DO NOTHING;

INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, created_at, updated_at)
SELECT t.id, '禁用', 'disabled', 2, 'active', now(), now()
FROM sys_dict_type t WHERE t.code = 'perm_status'
ON CONFLICT (dict_type_id, value) DO NOTHING;

-- 分区初始密钥字典项（示例，实际请按环境调整）
-- TODO: 生产环境请替换为实际密钥
INSERT INTO sys_dict_item (dict_type_id, label, value, sort_order, status, extra, created_at, updated_at)
SELECT t.id, 'us-east', 'us-east', 1, 'active', '{"secret":"your-secret-1"}'::jsonb, now(), now()
FROM sys_dict_type t WHERE t.code = 'region_bootstrap_secret'
ON CONFLICT (dict_type_id, value) DO NOTHING;

-- ============================================================================
-- 9. 对齐序列（避免自增 ID 冲突）
-- ============================================================================

SELECT setval('sys_permissions_id_seq', 2000, false);

-- ============================================================================
-- 初始化完成
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '系统管理模块初始化数据完成';
    RAISE NOTICE '默认管理员账户: wewins / wewins@2026';
    RAISE NOTICE '已初始化 28 个权限（1 个一级模块 + 5 个二级菜单 + 22 个操作权限）';
    RAISE NOTICE '权限 Code 采用层级前缀结构：sys -> sys:user -> sys:user:read';
    RAISE NOTICE '权限 ID 范围：MODULE(1-99) / MENU(100-299) / API(1000-1999)';
    RAISE NOTICE '序列已对齐到 2000，后续手动新增从 2000 开始';
END $$;
