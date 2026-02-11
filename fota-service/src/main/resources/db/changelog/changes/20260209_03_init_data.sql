-- 20260209000003_init_data.sql
-- 初始化系统管理模块数据
-- 创建日期: 2026-02-09
-- 作者: FOTA 团队
--
-- 包含：
-- 1. 系统权限（23 个权限）
-- 2. 系统角色（超级管理员、系统管理员）
-- 3. 默认管理员账户（wewins/wewins@2026）
-- 4. 角色权限关联
-- 5. 用户角色关联
-- 6. 系统字典类型和字典项

-- ============================================================================
-- 1. 插入系统权限
-- ============================================================================

INSERT INTO sys_permissions (code, name, type, status, created_at, updated_at)
VALUES
  -- 用户管理权限
  ('sys:user:read',        '用户管理-查询',        'API', 'active', now(), now()),
  ('sys:user:create',      '用户管理-创建',        'API', 'active', now(), now()),
  ('sys:user:update',      '用户管理-更新',        'API', 'active', now(), now()),
  ('sys:user:delete',      '用户管理-删除',        'API', 'active', now(), now()),
  ('sys:user:assign_role', '用户管理-分配角色',     'API', 'active', now(), now()),

  -- 角色管理权限
  ('sys:role:read',        '角色管理-查询',        'API', 'active', now(), now()),
  ('sys:role:create',      '角色管理-创建',        'API', 'active', now(), now()),
  ('sys:role:update',      '角色管理-更新',        'API', 'active', now(), now()),
  ('sys:role:delete',      '角色管理-删除',        'API', 'active', now(), now()),
  ('sys:role:assign_perm', '角色管理-分配权限',     'API', 'active', now(), now()),

  -- 权限管理权限
  ('sys:perm:read',        '权限管理-查询',        'API', 'active', now(), now()),
  ('sys:perm:create',      '权限管理-创建',        'API', 'active', now(), now()),
  ('sys:perm:update',      '权限管理-更新',        'API', 'active', now(), now()),
  ('sys:perm:delete',      '权限管理-删除',        'API', 'active', now(), now()),

  -- 字典类型管理权限
  ('sys:dict_type:read',   '字典类型-查询',        'API', 'active', now(), now()),
  ('sys:dict_type:create', '字典类型-创建',        'API', 'active', now(), now()),
  ('sys:dict_type:update', '字典类型-更新',        'API', 'active', now(), now()),
  ('sys:dict_type:delete', '字典类型-删除',        'API', 'active', now(), now()),

  -- 字典项管理权限
  ('sys:dict_item:read',   '字典项-查询',          'API', 'active', now(), now()),
  ('sys:dict_item:create', '字典项-创建',          'API', 'active', now(), now()),
  ('sys:dict_item:update', '字典项-更新',          'API', 'active', now(), now()),
  ('sys:dict_item:delete', '字典项-删除',          'API', 'active', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 2. 插入系统角色
-- ============================================================================

INSERT INTO sys_roles (code, name, description, status, created_at, updated_at)
VALUES
  ('super_wewins', '超级管理员', '拥有系统所有权限', 'active', now(), now()),
  ('wewins',       '系统管理员', '拥有系统管理权限', 'active', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 3. 绑定角色权限
-- ============================================================================

-- 超级管理员 -> 全部权限
INSERT INTO sys_role_permission (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, now(), now()
FROM sys_roles r
CROSS JOIN sys_permissions p
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 系统管理员 -> 系统管理权限（除角色-权限分配外）
INSERT INTO sys_role_permission (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, now(), now()
FROM sys_roles r
JOIN sys_permissions p ON p.code IN (
  'sys:user:read','sys:user:create','sys:user:update','sys:user:delete','sys:user:assign_role',
  'sys:role:read','sys:role:create','sys:role:update','sys:role:delete',
  'sys:perm:read',
  'sys:dict_type:read','sys:dict_type:create','sys:dict_type:update','sys:dict_type:delete',
  'sys:dict_item:read','sys:dict_item:create','sys:dict_item:update','sys:dict_item:delete'
)
WHERE r.code = 'wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 4. 插入默认管理员账户
-- ============================================================================

-- wewins@2026 的 BCrypt hash
INSERT INTO sys_users (id, username, password_hash, display_name, status, created_at, updated_at)
VALUES
  (1, 'wewins', '$2a$10$rVvZ.jq8Z/KGvGYK4MYuOex/J9Gx1hJOY8Q3v.X5.YTGx8Gy0kZCW', '系统管理员', 'active', now(), now())
ON CONFLICT (username) DO NOTHING;

-- 绑定用户与角色（wewins -> super_wewins）
INSERT INTO sys_user_role (user_id, role_id, created_at, updated_at)
SELECT u.id, r.id, now(), now()
FROM sys_users u
JOIN sys_roles r ON r.code = 'super_wewins'
WHERE u.username = 'wewins'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ============================================================================
-- 5. 插入系统字典类型
-- ============================================================================

INSERT INTO sys_dict_type (code, name, status, description, created_at, updated_at)
VALUES
  ('user_status', '用户状态', 'active', '用户账户状态', now(), now()),
  ('role_status', '角色状态', 'active', '角色状态', now(), now()),
  ('perm_status', '权限状态', 'active', '权限状态', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 6. 插入系统字典项
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

-- ============================================================================
-- 初始化完成
-- ============================================================================

DO $$
BEGIN
    RAISE NOTICE '系统管理模块初始化数据完成';
    RAISE NOTICE '默认管理员账户: wewins / wewins@2026';
    RAISE NOTICE '已初始化 23 个权限、2 个角色、1 个管理员、3 个字典类型';
END $$;
