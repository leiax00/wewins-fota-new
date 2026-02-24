-- 20260224_01_fix_system_sequences.sql
-- 修复系统管理模块序列漂移问题
-- 场景：初始化脚本中存在手工指定 ID（如 sys_users.id=1），但未统一对齐序列

-- ============================================================================
-- 对齐系统表序列（避免 duplicate key on primary key）
-- ============================================================================
-- 规则：
-- 1) 默认对齐到 MAX(id) + 1
-- 2) sys_permissions 保持至少 2000（保留低位 ID 段给固定权限节点）

SELECT setval(pg_get_serial_sequence('sys_users', 'id'), COALESCE((SELECT MAX(id) FROM sys_users), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('sys_roles', 'id'), COALESCE((SELECT MAX(id) FROM sys_roles), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('sys_permissions', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM sys_permissions), 0) + 1, 2000), false);
SELECT setval(pg_get_serial_sequence('sys_user_role', 'id'), COALESCE((SELECT MAX(id) FROM sys_user_role), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('sys_role_permission', 'id'), COALESCE((SELECT MAX(id) FROM sys_role_permission), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('sys_dict_type', 'id'), COALESCE((SELECT MAX(id) FROM sys_dict_type), 0) + 1, false);
SELECT setval(pg_get_serial_sequence('sys_dict_item', 'id'), COALESCE((SELECT MAX(id) FROM sys_dict_item), 0) + 1, false);

DO $$
BEGIN
    RAISE NOTICE '系统表序列已对齐完成';
    RAISE NOTICE 'sys_users/sys_roles/sys_permissions/sys_user_role/sys_role_permission/sys_dict_type/sys_dict_item';
END $$;
