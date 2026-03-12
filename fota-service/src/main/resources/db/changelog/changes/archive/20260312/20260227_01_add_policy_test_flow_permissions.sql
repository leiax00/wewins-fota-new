-- 20260227_01_add_policy_test_flow_permissions.sql
-- 添加策略测试流程相关权限
-- 创建日期: 2026-02-27
-- 作者: fota-team

-- 说明：
-- 本迁移为策略管理添加测试流程相关的三个新权限：
-- 1. fota:policy:start_test - 开始测试（DRAFT -> TESTING）
-- 2. fota:policy:verify - 验证通过（TESTING -> VERIFIED）
-- 3. fota:policy:release - 发布到生产（VERIFIED -> ACTIVE）
--
-- 权限设计：
-- - 测试人员：拥有 start_test 和 verify 权限，不能发布到生产
-- - 审核人员：拥有 release 权限，可以发布到生产并修改/删除生产中的策略
--
-- 状态流转：
-- DRAFT --(start_test)--> TESTING --(verify)--> VERIFIED --(release)--> ACTIVE
--                                                            |
--                                                            v
--                                                         PAUSED/EXPIRED
--                                                         (需要release权限才能删除)

-- ============================================================================
-- 1. 插入新的策略管理权限（BUTTON 类型）
-- ============================================================================

INSERT INTO sys_permissions (id, code, name, type, parent_id, status, created_at, updated_at)
VALUES
  (1136, 'fota:policy:start_test', '策略管理-开始测试', 'BUTTON', 240, 'active', now(), now()),
  (1137, 'fota:policy:verify',     '策略管理-验证通过', 'BUTTON', 240, 'active', now(), now()),
  (1138, 'fota:policy:release',    '策略管理-发布到生产', 'BUTTON', 240, 'active', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 2. 创建测试人员角色（如果不存在）
-- ============================================================================

INSERT INTO sys_roles (code, name, description, status, created_at, updated_at)
VALUES
  ('tester', '测试人员', '负责测试验证固件升级策略，可以开始测试和验证通过，但不能发布到生产', 'active', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 3. 创建审核人员角色（如果不存在）
-- ============================================================================

INSERT INTO sys_roles (code, name, description, status, created_at, updated_at)
VALUES
  ('project_manager', '审核人员', '负责发布策略到生产环境，拥有测试人员的所有权限，并可管理生产中的策略', 'active', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ============================================================================
-- 4. 绑定角色权限
-- ============================================================================

-- 4.1 测试人员角色权限
-- 策略管理基础权限（查询、创建、更新）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.code IN (
  'fota:policy:read',
  'fota:policy:create',
  'fota:policy:update',
  'fota:policy:delete',
  -- 测试流程权限
  'fota:policy:start_test',
  'fota:policy:verify'
  -- 注意：没有 fota:policy:release 和 fota:policy:pause
  -- 没有生产相关的权限
)
WHERE r.code = 'tester'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 给测试人员分配其他 FOTA 只读权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.code IN (
  'fota:dashboard:view',
  'fota:product:read',
  'fota:firmware:read',
  'fota:device:read',
  'fota:device:detail'
)
WHERE r.code = 'tester'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 4.2 审核人员角色权限
-- 策略管理全量权限（包括发布到生产）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.parent_id = 240  -- 策略管理下的所有权限
WHERE r.code = 'project_manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 给审核人员分配其他 FOTA 完整权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id >= 1100 AND p.id < 1200  -- 所有 FOTA 操作权限
WHERE r.code = 'project_manager'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 4.3 超级管理员补充新增权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_roles r
JOIN sys_permissions p ON p.id IN (1136, 1137, 1138)
WHERE r.code = 'super_wewins'
ON CONFLICT (role_id, permission_id) DO NOTHING;