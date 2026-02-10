-- V3__init_system_tables.sql
-- 系统管理模块表结构初始化
-- 创建日期: 2026-02-06
-- 作者: FOTA 团队
--
-- 表列表:
-- 1. sys_users - 系统用户表
-- 2. sys_roles - 系统角色表
-- 3. sys_permissions - 系统权限表
-- 4. sys_user_role - 用户-角色关联表
-- 5. sys_role_permission - 角色-权限关联表
-- 6. sys_dict_type - 字典类型表
-- 7. sys_dict_item - 字典项表

-- ============================================================================
-- 1. sys_users (系统用户表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128),
    email VARCHAR(128),
    phone VARCHAR(32),
    status VARCHAR(16) NOT NULL,
    last_login_at TIMESTAMP,
    tenant_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE sys_users IS '系统用户表：存储系统用户的基本信息和认证凭据';
COMMENT ON COLUMN sys_users.id IS '用户ID';
COMMENT ON COLUMN sys_users.username IS '用户名（唯一）';
COMMENT ON COLUMN sys_users.password_hash IS '密码哈希（BCrypt）';
COMMENT ON COLUMN sys_users.display_name IS '显示名称';
COMMENT ON COLUMN sys_users.email IS '邮箱';
COMMENT ON COLUMN sys_users.phone IS '手机号';
COMMENT ON COLUMN sys_users.status IS '用户状态';
COMMENT ON COLUMN sys_users.last_login_at IS '最后登录时间';
COMMENT ON COLUMN sys_users.tenant_id IS '租户ID（预留多租户）';

-- 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_users_username ON sys_users(username);
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_users_email ON sys_users(email) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_users_phone ON sys_users(phone) WHERE phone IS NOT NULL;

-- 普通索引
CREATE INDEX IF NOT EXISTS ix_sys_users_status ON sys_users(status);
CREATE INDEX IF NOT EXISTS ix_sys_users_deleted_at ON sys_users(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 2. sys_roles (系统角色表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE sys_roles IS '系统角色表：存储系统角色信息，用于 RBAC 权限控制';
COMMENT ON COLUMN sys_roles.id IS '角色ID';
COMMENT ON COLUMN sys_roles.code IS '角色编码（唯一）';
COMMENT ON COLUMN sys_roles.name IS '角色名称';
COMMENT ON COLUMN sys_roles.description IS '角色描述';
COMMENT ON COLUMN sys_roles.status IS '角色状态';

-- 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_roles_code ON sys_roles(code);

-- 普通索引
CREATE INDEX IF NOT EXISTS ix_sys_roles_status ON sys_roles(status);
CREATE INDEX IF NOT EXISTS ix_sys_roles_deleted_at ON sys_roles(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 3. sys_permissions (系统权限表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(16) NOT NULL,
    path VARCHAR(256),
    method VARCHAR(16),
    parent_id BIGINT,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE sys_permissions IS '系统权限表：存储系统权限信息，支持 API、菜单、按钮等权限类型';
COMMENT ON COLUMN sys_permissions.id IS '权限ID';
COMMENT ON COLUMN sys_permissions.code IS '权限编码（唯一）';
COMMENT ON COLUMN sys_permissions.name IS '权限名称';
COMMENT ON COLUMN sys_permissions.type IS '权限类型（API/MENU/BUTTON）';
COMMENT ON COLUMN sys_permissions.path IS 'API 资源路径';
COMMENT ON COLUMN sys_permissions.method IS 'HTTP 方法（GET/POST/PUT/DELETE）';
COMMENT ON COLUMN sys_permissions.parent_id IS '父权限ID（用于构建权限树）';
COMMENT ON COLUMN sys_permissions.status IS '权限状态';

-- 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_permissions_code ON sys_permissions(code);

-- 普通索引
CREATE INDEX IF NOT EXISTS ix_sys_permissions_type ON sys_permissions(type);
CREATE INDEX IF NOT EXISTS ix_sys_permissions_parent_id ON sys_permissions(parent_id);
CREATE INDEX IF NOT EXISTS ix_sys_permissions_deleted_at ON sys_permissions(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 4. sys_user_role (用户-角色关联表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE sys_user_role IS '用户-角色关联表：存储用户与角色的多对多关联关系';
COMMENT ON COLUMN sys_user_role.id IS '关联ID';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID';
COMMENT ON COLUMN sys_user_role.role_id IS '角色ID';

-- 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_user_role ON sys_user_role(user_id, role_id);

-- 普通索引
CREATE INDEX IF NOT EXISTS ix_sys_user_role_user_id ON sys_user_role(user_id);
CREATE INDEX IF NOT EXISTS ix_sys_user_role_role_id ON sys_user_role(role_id);
CREATE INDEX IF NOT EXISTS ix_sys_user_role_deleted_at ON sys_user_role(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 5. sys_role_permission (角色-权限关联表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGSERIAL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE sys_role_permission IS '角色-权限关联表：存储角色与权限的多对多关联关系';
COMMENT ON COLUMN sys_role_permission.id IS '关联ID';
COMMENT ON COLUMN sys_role_permission.role_id IS '角色ID';
COMMENT ON COLUMN sys_role_permission.permission_id IS '权限ID';

-- 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_role_permission ON sys_role_permission(role_id, permission_id);

-- 普通索引
CREATE INDEX IF NOT EXISTS ix_sys_role_permission_role_id ON sys_role_permission(role_id);
CREATE INDEX IF NOT EXISTS ix_sys_role_permission_permission_id ON sys_role_permission(permission_id);
CREATE INDEX IF NOT EXISTS ix_sys_role_permission_deleted_at ON sys_role_permission(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 6. sys_dict_type (字典类型表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    i18n_key VARCHAR(128),
    status VARCHAR(16) NOT NULL,
    description VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE sys_dict_type IS '字典类型表：存储系统字典类型信息，如设备状态、升级状态等';
COMMENT ON COLUMN sys_dict_type.id IS '字典类型ID';
COMMENT ON COLUMN sys_dict_type.code IS '字典类型编码（唯一）';
COMMENT ON COLUMN sys_dict_type.name IS '字典类型名称';
COMMENT ON COLUMN sys_dict_type.i18n_key IS '国际化key前缀（如 device.status）';
COMMENT ON COLUMN sys_dict_type.status IS '字典类型状态';
COMMENT ON COLUMN sys_dict_type.description IS '字典类型描述';

-- 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_dict_type_code ON sys_dict_type(code);

-- 普通索引
CREATE INDEX IF NOT EXISTS ix_sys_dict_type_status ON sys_dict_type(status);
CREATE INDEX IF NOT EXISTS ix_sys_dict_type_deleted_at ON sys_dict_type(deleted_at) WHERE deleted_at IS NULL;

-- ============================================================================
-- 7. sys_dict_item (字典项表)
-- ============================================================================
CREATE TABLE IF NOT EXISTS sys_dict_item (
    id BIGSERIAL PRIMARY KEY,
    dict_type_id BIGINT NOT NULL,
    label VARCHAR(128) NOT NULL,
    value VARCHAR(128) NOT NULL,
    i18n_key VARCHAR(256),
    sort_order INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    extra JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted_at TIMESTAMP
);

COMMENT ON TABLE sys_dict_item IS '字典项表：存储字典类型下的具体字典项';
COMMENT ON COLUMN sys_dict_item.id IS '字典项ID';
COMMENT ON COLUMN sys_dict_item.dict_type_id IS '字典类型ID';
COMMENT ON COLUMN sys_dict_item.label IS '字典项标签';
COMMENT ON COLUMN sys_dict_item.value IS '字典项值';
COMMENT ON COLUMN sys_dict_item.i18n_key IS '国际化key（如 device.status.online）';
COMMENT ON COLUMN sys_dict_item.sort_order IS '排序号';
COMMENT ON COLUMN sys_dict_item.status IS '字典项状态';
COMMENT ON COLUMN sys_dict_item.extra IS '扩展信息（JSON）';

-- 唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS ux_sys_dict_item ON sys_dict_item(dict_type_id, value);

-- 普通索引
CREATE INDEX IF NOT EXISTS ix_sys_dict_item_dict_type_id ON sys_dict_item(dict_type_id);
CREATE INDEX IF NOT EXISTS ix_sys_dict_item_status ON sys_dict_item(status);
CREATE INDEX IF NOT EXISTS ix_sys_dict_item_deleted_at ON sys_dict_item(deleted_at) WHERE deleted_at IS NULL;

-- JSONB 索引
CREATE INDEX IF NOT EXISTS ix_sys_dict_item_extra_gin ON sys_dict_item USING GIN (extra);

-- ============================================================================
-- 初始化完成
-- ============================================================================
DO $$
BEGIN
    RAISE NOTICE '系统管理模块表结构初始化完成';
    RAISE NOTICE '已创建 7 个核心表：sys_users, sys_roles, sys_permissions, sys_user_role, sys_role_permission, sys_dict_type, sys_dict_item';
    RAISE NOTICE '已创建所有必要的索引和约束';
END $$;
