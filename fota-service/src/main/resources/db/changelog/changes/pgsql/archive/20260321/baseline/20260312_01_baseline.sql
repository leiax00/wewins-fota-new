-- ----------------------------
-- Table structure for products
-- ----------------------------
DROP TABLE IF EXISTS products;
create table if not exists products
(
    id                   bigserial
    primary key,
    name                 varchar(255)            not null,
    manufacturer         varchar(255),
    model                varchar(255),
    check_period_seconds integer   default 21600 not null,
    remark               text,
    created_at           timestamp default CURRENT_TIMESTAMP,
    created_by           bigint,
    updated_at           timestamp default CURRENT_TIMESTAMP,
    updated_by           bigint,
    deleted_at           timestamp
    );

comment on table products is '产品表：存储设备产品的基本信息';

comment on column products.id is '产品唯一标识';

comment on column products.name is '产品名称';

comment on column products.manufacturer is '制造商';

comment on column products.model is '产品型号';

comment on column products.remark is '产品备注';

comment on column products.created_at is '创建时间';

comment on column products.created_by is '创建人用户ID';

comment on column products.updated_at is '更新时间';

comment on column products.updated_by is '更新人用户ID';

comment on column products.deleted_at is '软删除时间';

comment on column products.check_period_seconds is '产品默认检测周期（秒），默认 21600 秒（6 小时）';

create index if not exists idx_products_name
    on products (name);

create index if not exists idx_products_manufacturer
    on products (manufacturer);

create index if not exists idx_products_deleted_at
    on products (deleted_at)
    where (deleted_at IS NULL);

-- ----------------------------
-- Table structure for devices
-- ----------------------------
DROP TABLE IF EXISTS devices;
create table if not exists devices
(
    id                    bigserial
    primary key,
    imei                  varchar(255) not null
    unique,
    product_id            bigint,
    version_parts         jsonb     default '{"parts": {}, "primaryPart": "main"}'::jsonb,
    initial_version_parts jsonb     default '{"parts": {}, "primaryPart": "main"}'::jsonb,
    status                varchar(50)  not null,
    first_seen_at         timestamp,
    last_seen_at          timestamp,
    tags                  jsonb,
    import_batch_id       bigint,
    created_at            timestamp default CURRENT_TIMESTAMP,
    created_by            bigint,
    updated_at            timestamp default CURRENT_TIMESTAMP,
    updated_by            bigint
    );

comment on table devices is '设备表：存储所有设备的基本信息和当前状态';

comment on column devices.id is '设备唯一标识';

comment on column devices.imei is '设备 IMEI 号（唯一）';

comment on column devices.product_id is '关联的产品 ID（无外键约束，由应用层保证一致性）';

comment on column devices.status is '设备状态（ACTIVE, INACTIVE, LOST, etc.）';

comment on column devices.last_seen_at is '最后一次在线时间';

comment on column devices.tags is '设备标签（JSONB 对象，KV 结构，如 {"env":"test", "region":"CN", "network":"5G"}）';

comment on column devices.import_batch_id is '导入批次ID';

comment on column devices.created_at is '创建时间';

comment on column devices.created_by is '创建人用户ID';

comment on column devices.updated_at is '更新时间';

comment on column devices.updated_by is '更新人用户ID';

comment on column devices.version_parts is '多部分版本信息（JSONB）: {"parts": {"main": {"versionId": 101, "version": "1.0.0", "updatedAt": "xxx"}}, "primaryPart": "main"}';

comment on column devices.first_seen_at is '设备第一次上线时间（首次检测时间）';

comment on column devices.initial_version_parts is '设备第一次上线的版本信息（JSONB）: {"parts": {"main": {"versionId": 101, "version": "1.0.0", "updatedAt": "xxx"}}, "primaryPart": "main"}';

create index if not exists idx_devices_product_id
    on devices (product_id);

create index if not exists idx_devices_status
    on devices (status);

create index if not exists idx_devices_last_seen_at
    on devices (last_seen_at);

create index if not exists idx_devices_tags_gin
    on devices using gin (tags);

create index if not exists idx_devices_import_batch_id
    on devices (import_batch_id);

create index if not exists idx_devices_version_parts_gin
    on devices using gin (version_parts);

create index if not exists idx_devices_initial_version_parts_gin
    on devices using gin (initial_version_parts);

create index if not exists idx_devices_first_seen_at
    on devices (first_seen_at);

-- ----------------------------
-- Table structure for firmware_versions
-- ----------------------------
DROP TABLE IF EXISTS firmware_versions;
create table if not exists firmware_versions
(
    id                  bigserial
    primary key,
    product_id          bigint                                        not null,
    version             varchar(50)                                   not null,
    internal_version    varchar(255),
    tags                jsonb,
    meta                jsonb,
    package_status      varchar(20) default 'NONE'::character varying not null,
    file_name           varchar(255),
    file_url            varchar(1024),
    file_size           bigint,
    md5                 varchar(32),
    sha256              varchar(64),
    package_uploaded_at timestamp,
    created_at          timestamp   default CURRENT_TIMESTAMP,
    created_by          bigint,
    updated_at          timestamp   default CURRENT_TIMESTAMP,
    updated_by          bigint,
    deleted_at          timestamp
    );

comment on table firmware_versions is '固件版本表：存储所有固件版本的信息和文件元数据';

comment on column firmware_versions.id is '固件版本唯一标识';

comment on column firmware_versions.product_id is '关联的产品 ID（无外键约束，由应用层保证一致性）';

comment on column firmware_versions.version is '版本号（如 1.0.0）';

comment on column firmware_versions.file_url is '固件文件下载地址';

comment on column firmware_versions.file_size is '固件文件大小（字节）';

comment on column firmware_versions.md5 is 'MD5 校验和';

comment on column firmware_versions.sha256 is 'SHA-256 校验和';

comment on column firmware_versions.tags is '版本标签（JSONB 对象，KV 结构，如 {"tag":"build01"}）';

comment on column firmware_versions.meta is '扩展元数据（多语言描述、changelog、扩展字段）';

comment on column firmware_versions.created_at is '创建时间';

comment on column firmware_versions.created_by is '创建人用户ID';

comment on column firmware_versions.updated_at is '更新时间';

comment on column firmware_versions.updated_by is '更新人用户ID';

comment on column firmware_versions.deleted_at is '软删除时间';

comment on column firmware_versions.package_status is '固件包状态：NONE（无包）/ UPLOADED（已上传临时文件）/ READY（已转存对象存储）/ FAILED（失败）';

comment on column firmware_versions.package_uploaded_at is '固件包最近一次上传完成时间（临时上传成功或最终转存成功时更新）';

comment on column firmware_versions.file_name is '固件原始文件名（上传时文件名，package_status=READY 时应有值）';

comment on column firmware_versions.internal_version is '内部版本号（build tag），用于与 version 组合唯一确定固件版本（如 ASR_YEMEN_M476_V11_B03_Build02）';

create index if not exists idx_fv_product_id
    on firmware_versions (product_id);

create index if not exists idx_fv_tags_gin
    on firmware_versions using gin (tags);

create index if not exists idx_fv_meta_gin
    on firmware_versions using gin (meta);

create index if not exists idx_fv_deleted_at
    on firmware_versions (deleted_at)
    where (deleted_at IS NULL);

create index if not exists idx_fv_package_status
    on firmware_versions (package_status)
    where (deleted_at IS NULL);

create index if not exists idx_fv_product_package_status
    on firmware_versions (product_id, package_status)
    where (deleted_at IS NULL);

create index if not exists idx_fv_package_uploaded_at
    on firmware_versions (package_uploaded_at)
    where ((deleted_at IS NULL) AND (package_uploaded_at IS NOT NULL));

create index if not exists idx_fv_product_id_internal_version
    on firmware_versions (product_id, internal_version)
    where (deleted_at IS NULL);

comment on index idx_fv_product_id_internal_version is '固件版本查询索引（仅未删除记录）：支持按产品+内部版本号查询';

create unique index if not exists uk_fv_product_version_internal
    on firmware_versions (product_id, version, internal_version)
    where (deleted_at IS NULL);

comment on index uk_fv_product_version_internal is '固件版本组合唯一约束（仅未删除记录且有 internal_version）：产品下相同 version 和 internal_version 组合只能有一个';

-- ----------------------------
-- Table structure for upgrade_policies
-- ----------------------------
DROP TABLE IF EXISTS upgrade_policies;
create table if not exists upgrade_policies
(
    id                      bigserial
    primary key,
    product_id              bigint                                          not null,
    name                    varchar(255)                                    not null,
    status                  varchar(20) default 'ACTIVE'::character varying not null,
    priority                integer     default 0,
    gray_rate               integer     default 0,
    trigger_mode            varchar(20) default 'BOTH'::character varying   not null,
    target_mode             varchar(32) default 'ALL'::character varying    not null,
    target_device_batch_ids jsonb,
    target_imeis            jsonb,
    target_device_tags      jsonb,
    time_window             jsonb,
    target_version_id       bigint                                          not null,
    source_versions         jsonb,
    remark                  text,
    created_at              timestamp   default CURRENT_TIMESTAMP,
    created_by              bigint,
    updated_at              timestamp   default CURRENT_TIMESTAMP,
    updated_by              bigint,
    deleted_at              timestamp
    );

comment on table upgrade_policies is '升级策略表：存储固件升级策略和配置';

comment on column upgrade_policies.id is '策略唯一标识';

comment on column upgrade_policies.product_id is '关联的产品 ID（无外键约束，由应用层保证一致性）';

comment on column upgrade_policies.name is '策略名称';

comment on column upgrade_policies.remark is '策略备注';

comment on column upgrade_policies.target_version_id is '目标固件版本 ID（无外键约束，由应用层保证一致性）';

comment on column upgrade_policies.source_versions is '允许升级的源版本 ID 列表（JSONB 数组，如 [10, 11, 12]）';

comment on column upgrade_policies.priority is '优先级（数值越大优先级越高）';

comment on column upgrade_policies.gray_rate is '灰度比例（0-100）';

comment on column upgrade_policies.trigger_mode is '触发模式（AUTO:仅自动 / MANUAL:仅手动 / BOTH:不限制）';

comment on column upgrade_policies.target_device_tags is '设备标签过滤条件（JSONB）';

comment on column upgrade_policies.time_window is '时间窗口配置（JSONB）';

comment on column upgrade_policies.created_at is '创建时间';

comment on column upgrade_policies.created_by is '创建人用户ID';

comment on column upgrade_policies.updated_at is '更新时间';

comment on column upgrade_policies.updated_by is '更新人用户ID';

comment on column upgrade_policies.deleted_at is '软删除时间';

comment on column upgrade_policies.status is '策略状态（DRAFT-草稿, TESTING-测试中, VERIFIED-已验证, ACTIVE-生产中, PAUSED-暂停, EXPIRED-过期）';

comment on column upgrade_policies.target_mode is '目标设备模式（ALL/DEVICE_IDS(实际为IMEI列表)/DEVICE_BATCHES/DEVICE_TAGS）';

comment on column upgrade_policies.target_device_batch_ids is '目标设备批次ID列表（JSONB数组）';

comment on column upgrade_policies.target_imeis is '指定设备IMEI列表（JSONB 数组），当 target_mode = DEVICE_IDS 时使用';

create index if not exists idx_up_product_id
    on upgrade_policies (product_id);

create index if not exists idx_up_target_version
    on upgrade_policies (target_version_id);

create index if not exists idx_up_priority
    on upgrade_policies (priority);

create index if not exists idx_up_trigger_mode
    on upgrade_policies (trigger_mode);

create index if not exists idx_up_deleted_at
    on upgrade_policies (deleted_at)
    where (deleted_at IS NULL);

create index if not exists idx_up_source_versions_gin
    on upgrade_policies using gin (source_versions);

create index if not exists idx_up_target_device_tags_gin
    on upgrade_policies using gin (target_device_tags);

create index if not exists idx_up_status
    on upgrade_policies (status);

create index if not exists idx_up_target_mode
    on upgrade_policies (target_mode);

create index if not exists idx_up_target_device_batch_ids_gin
    on upgrade_policies using gin (target_device_batch_ids);

create index if not exists idx_up_time_window_gin
    on upgrade_policies using gin (time_window);

create index if not exists idx_up_target_imeis_gin
    on upgrade_policies using gin (target_imeis);


-- ----------------------------
-- Table structure for device_import_batches
-- ----------------------------
DROP TABLE IF EXISTS device_import_batches;
create table if not exists device_import_batches
(
    id            bigserial
    primary key,
    batch_name    varchar(255)                                       not null,
    status        varchar(32) default 'IMPORTING'::character varying not null,
    product_id    bigint,
    source_file   varchar(1024),
    total_count   integer     default 0,
    success_count integer     default 0,
    failed_count  integer     default 0,
    error_message text,
    started_at    timestamp   default CURRENT_TIMESTAMP,
    finished_at   timestamp,
    created_at    timestamp   default CURRENT_TIMESTAMP,
    created_by    bigint,
    updated_at    timestamp   default CURRENT_TIMESTAMP,
    updated_by    bigint,
    deleted_at    timestamp,
    constraint uk_device_import_batches_product_batch
    unique (product_id, batch_name)
    );

comment on table device_import_batches is '设备导入批次表：支持批次状态管理和导入统计';

comment on column device_import_batches.id is '批次唯一标识';

comment on column device_import_batches.batch_name is '批次名称（用户自定义或自动生成）';

comment on column device_import_batches.status is '批次状态（IMPORTING/SUCCESS/FAILED/PARTIAL）';

comment on column device_import_batches.source_file is '导入文件路径或标识';

comment on column device_import_batches.total_count is '导入总数量';

comment on column device_import_batches.success_count is '成功数量';

comment on column device_import_batches.failed_count is '失败数量';

comment on column device_import_batches.error_message is '失败原因';

comment on column device_import_batches.started_at is '开始导入时间';

comment on column device_import_batches.finished_at is '结束导入时间';

comment on column device_import_batches.created_at is '创建时间';

comment on column device_import_batches.created_by is '创建人用户ID';

comment on column device_import_batches.updated_at is '更新时间';

comment on column device_import_batches.updated_by is '更新人用户ID';

comment on column device_import_batches.deleted_at is '软删除时间';

comment on column device_import_batches.product_id is '关联的产品ID（导入时指定的产品）';

comment on constraint uk_device_import_batches_product_batch on device_import_batches is '同一产品的批次名称唯一约束';

create index if not exists idx_device_import_batches_status
    on device_import_batches (status);

create index if not exists idx_device_import_batches_deleted_at
    on device_import_batches (deleted_at)
    where (deleted_at IS NULL);

-- ----------------------------
-- Table structure for sys_users
-- ----------------------------
DROP TABLE IF EXISTS sys_users;
create table if not exists sys_users
(
    id            bigserial
    primary key,
    username      varchar(64)                         not null,
    password_hash varchar(255)                        not null,
    display_name  varchar(128),
    email         varchar(128),
    phone         varchar(32),
    status        varchar(16)                         not null,
    last_login_at timestamp,
    tenant_id     bigint,
    created_at    timestamp default CURRENT_TIMESTAMP not null,
    created_by    bigint,
    updated_at    timestamp default CURRENT_TIMESTAMP not null,
    updated_by    bigint,
    deleted_at    timestamp
    );

comment on table sys_users is '系统用户表：存储系统用户的基本信息和认证凭据';

comment on column sys_users.id is '用户ID';

comment on column sys_users.username is '用户名（唯一）';

comment on column sys_users.password_hash is '密码哈希（BCrypt）';

comment on column sys_users.display_name is '显示名称';

comment on column sys_users.email is '邮箱';

comment on column sys_users.phone is '手机号';

comment on column sys_users.status is '用户状态';

comment on column sys_users.last_login_at is '最后登录时间';

comment on column sys_users.tenant_id is '租户ID（预留多租户）';

create unique index if not exists ux_sys_users_username
    on sys_users (username);

create unique index if not exists ux_sys_users_email
    on sys_users (email)
    where (email IS NOT NULL);

create unique index if not exists ux_sys_users_phone
    on sys_users (phone)
    where (phone IS NOT NULL);

create index if not exists ix_sys_users_status
    on sys_users (status);

create index if not exists ix_sys_users_deleted_at
    on sys_users (deleted_at)
    where (deleted_at IS NULL);

-- ----------------------------
-- Records of sys_users
-- ----------------------------
BEGIN;
INSERT INTO "sys_users" ("id", "username", "password_hash", "display_name", "email", "phone", "status", "last_login_at", "tenant_id", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (1, 'wewins', '$2a$10$EZXnfjmgPCoizFOQcy/YneL9btA2u5jecupZ2MmAbNP.LhAoqj22m', '系统管理员', NULL, NULL, 'active', NULL, NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for sys_roles
-- ----------------------------
DROP TABLE IF EXISTS sys_roles;
create table if not exists sys_roles
(
    id          bigserial
    primary key,
    code        varchar(64)                         not null,
    name        varchar(128)                        not null,
    description varchar(256),
    status      varchar(16)                         not null,
    created_at  timestamp default CURRENT_TIMESTAMP not null,
    created_by  bigint,
    updated_at  timestamp default CURRENT_TIMESTAMP not null,
    updated_by  bigint,
    deleted_at  timestamp
    );

comment on table sys_roles is '系统角色表：存储系统角色信息，用于 RBAC 权限控制';

comment on column sys_roles.id is '角色ID';

comment on column sys_roles.code is '角色编码（唯一）';

comment on column sys_roles.name is '角色名称';

comment on column sys_roles.description is '角色描述';

comment on column sys_roles.status is '角色状态';

create unique index if not exists ux_sys_roles_code
    on sys_roles (code);

create index if not exists ix_sys_roles_status
    on sys_roles (status);

create index if not exists ix_sys_roles_deleted_at
    on sys_roles (deleted_at)
    where (deleted_at IS NULL);

-- ----------------------------
-- Records of sys_roles
-- ----------------------------
BEGIN;
INSERT INTO "sys_roles" ("id", "code", "name", "description", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (1, 'super_wewins', '超级管理员', '拥有系统所有权限', 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_roles" ("id", "code", "name", "description", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (2, 'wewins', '系统管理员', '拥有系统管理权限', 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_roles" ("id", "code", "name", "description", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (3, 'tester', '测试人员', '负责测试验证固件升级策略，可以开始测试和验证通过，但不能发布到生产', 'active', '2026-03-12 07:30:44.656794', NULL, '2026-03-12 07:30:44.656794', NULL, NULL);
INSERT INTO "sys_roles" ("id", "code", "name", "description", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (4, 'project_manager', '审核人员', '负责发布策略到生产环境，拥有测试人员的所有权限，并可管理生产中的策略', 'active', '2026-03-12 07:30:44.656794', NULL, '2026-03-12 07:30:44.656794', NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for sys_permissions
-- ----------------------------
DROP TABLE IF EXISTS sys_permissions;
create table if not exists sys_permissions
(
    id            bigserial
    primary key,
    code          varchar(128)                        not null,
    name          varchar(128)                        not null,
    type          varchar(16)                         not null,
    path          varchar(256),
    method        varchar(16),
    parent_id     bigint,
    status        varchar(16)                         not null,
    route_path    varchar(256),
    route_name    varchar(128),
    component_key varchar(128),
    redirect_path varchar(256),
    menu_sort     integer   default 0,
    icon          varchar(64),
    external_link varchar(512),
    menu_config   jsonb,
    created_at    timestamp default CURRENT_TIMESTAMP not null,
    created_by    bigint,
    updated_at    timestamp default CURRENT_TIMESTAMP not null,
    updated_by    bigint,
    deleted_at    timestamp
    );

comment on table sys_permissions is '系统权限表：存储系统权限信息，支持 API、菜单、按钮等权限类型';

comment on column sys_permissions.id is '权限ID';

comment on column sys_permissions.code is '权限编码（唯一）';

comment on column sys_permissions.name is '权限名称';

comment on column sys_permissions.type is '权限类型（API/MENU/BUTTON）';

comment on column sys_permissions.path is 'API 资源路径';

comment on column sys_permissions.method is 'HTTP 方法（GET/POST/PUT/DELETE）';

comment on column sys_permissions.parent_id is '父权限ID（用于构建权限树）';

comment on column sys_permissions.status is '权限状态';

comment on column sys_permissions.route_path is '路由路径（仅 MODULE/MENU 类型有意义）';

comment on column sys_permissions.route_name is '路由名称（用于前端路由 name）';

comment on column sys_permissions.component_key is '组件标识（前端白名单映射 key）';

comment on column sys_permissions.redirect_path is '重定向路径';

comment on column sys_permissions.menu_sort is '同级菜单排序（越小越靠前）';

comment on column sys_permissions.icon is '菜单图标（Element Plus 图标名）';

comment on column sys_permissions.external_link is '外链 URL（仅允许 https）';

comment on column sys_permissions.menu_config is '菜单扩展配置 JSON（hidden/keepAlive/affix 等）';

create unique index if not exists ux_sys_permissions_code
    on sys_permissions (code);

create index if not exists ix_sys_permissions_type
    on sys_permissions (type);

create index if not exists ix_sys_permissions_parent_id
    on sys_permissions (parent_id);

create index if not exists ix_sys_permissions_deleted_at
    on sys_permissions (deleted_at)
    where (deleted_at IS NULL);

-- ----------------------------
-- Records of sys_permissions
-- ----------------------------
BEGIN;
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1001, 'sys:user:read', '用户管理-查询', 'API', NULL, NULL, 100, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1002, 'sys:user:create', '用户管理-创建', 'API', NULL, NULL, 100, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1003, 'sys:user:update', '用户管理-更新', 'API', NULL, NULL, 100, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1004, 'sys:user:delete', '用户管理-删除', 'API', NULL, NULL, 100, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1005, 'sys:user:assign_role', '用户管理-分配角色', 'API', NULL, NULL, 100, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1011, 'sys:role:read', '角色管理-查询', 'API', NULL, NULL, 110, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1012, 'sys:role:create', '角色管理-创建', 'API', NULL, NULL, 110, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1013, 'sys:role:update', '角色管理-更新', 'API', NULL, NULL, 110, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1014, 'sys:role:delete', '角色管理-删除', 'API', NULL, NULL, 110, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1015, 'sys:role:assign_perm', '角色管理-分配权限', 'API', NULL, NULL, 110, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1021, 'sys:perm:read', '权限管理-查询', 'API', NULL, NULL, 120, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1022, 'sys:perm:create', '权限管理-创建', 'API', NULL, NULL, 120, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1023, 'sys:perm:update', '权限管理-更新', 'API', NULL, NULL, 120, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1024, 'sys:perm:delete', '权限管理-删除', 'API', NULL, NULL, 120, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1031, 'sys:dict_type:read', '字典类型-查询', 'API', NULL, NULL, 130, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1032, 'sys:dict_type:create', '字典类型-创建', 'API', NULL, NULL, 130, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1033, 'sys:dict_type:update', '字典类型-更新', 'API', NULL, NULL, 130, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1034, 'sys:dict_type:delete', '字典类型-删除', 'API', NULL, NULL, 130, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1041, 'sys:dict_item:read', '字典项-查询', 'API', NULL, NULL, 140, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1042, 'sys:dict_item:create', '字典项-创建', 'API', NULL, NULL, 140, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1043, 'sys:dict_item:update', '字典项-更新', 'API', NULL, NULL, 140, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1044, 'sys:dict_item:delete', '字典项-删除', 'API', NULL, NULL, 140, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1101, 'fota:dashboard:view', '仪表盘-查看', 'API', NULL, NULL, 200, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1111, 'fota:product:read', '产品管理-查询', 'API', NULL, NULL, 210, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1112, 'fota:product:create', '产品管理-创建', 'API', NULL, NULL, 210, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1113, 'fota:product:update', '产品管理-更新', 'API', NULL, NULL, 210, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1114, 'fota:product:delete', '产品管理-删除', 'API', NULL, NULL, 210, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1141, 'fota:device:read', '设备管理-查询', 'API', NULL, NULL, 220, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1142, 'fota:device:detail', '设备管理-详情', 'API', NULL, NULL, 220, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1143, 'fota:device:import', '设备管理-导入', 'API', NULL, NULL, 220, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1144, 'fota:device:update', '设备管理-更新', 'API', NULL, NULL, 220, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1121, 'fota:firmware:read', '固件管理-查询', 'API', NULL, NULL, 230, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1122, 'fota:firmware:create', '固件管理-创建', 'API', NULL, NULL, 230, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1123, 'fota:firmware:update', '固件管理-更新', 'API', NULL, NULL, 230, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1124, 'fota:firmware:delete', '固件管理-删除', 'API', NULL, NULL, 230, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1125, 'fota:firmware:download', '固件管理-下载', 'API', NULL, NULL, 230, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1131, 'fota:policy:read', '策略管理-查询', 'API', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1132, 'fota:policy:create', '策略管理-创建', 'API', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1133, 'fota:policy:update', '策略管理-更新', 'API', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1134, 'fota:policy:delete', '策略管理-删除', 'API', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1135, 'fota:policy:pause', '策略管理-暂停', 'API', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (10, 'sys', '系统管理', 'MODULE', NULL, NULL, NULL, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, '/system', 'SystemRoot', 'LAYOUT', '/system/user', 90, 'Setting', NULL, '{"hidden": false, "i18nKey": "menu.system", "alwaysShow": true}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (140, 'sys:dict_item', '字典项管理', 'MENU', NULL, NULL, 10, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, '/system/dict/:id/items', 'SystemDictItems', 'system/dict-items/index', NULL, 50, 'Tickets', NULL, '{"hidden": true, "i18nKey": "menu.dict_item_prefix", "keepAlive": false, "tabHidden": true, "activeMenu": "sys:dict_type", "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (20, 'fota', 'FOTA 业务', 'MODULE', NULL, NULL, NULL, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, '/fota', 'FotaRoot', 'LAYOUT', '/dashboard', 10, 'Promotion', NULL, '{"hidden": true, "alwaysShow": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (220, 'fota:device', '设备管理', 'MENU', NULL, NULL, 20, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, '/device', 'Device', 'device/index', NULL, 30, 'Iphone', NULL, '{"hidden": false, "i18nKey": "menu.device", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (100, 'sys:user', '用户管理', 'MENU', NULL, NULL, 10, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, '/system/user', 'SystemUser', 'system/user/index', NULL, 10, 'User', NULL, '{"hidden": false, "i18nKey": "menu.user", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (110, 'sys:role', '角色管理', 'MENU', NULL, NULL, 10, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, '/system/role', 'SystemRole', 'system/role/index', NULL, 20, 'UserFilled', NULL, '{"hidden": false, "i18nKey": "menu.role", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (120, 'sys:perm', '权限管理', 'MENU', NULL, NULL, 10, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, '/system/permission', 'SystemPermission', 'system/permission/index', NULL, 30, 'Lock', NULL, '{"hidden": false, "i18nKey": "menu.permission", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (200, 'fota:dashboard', '仪表盘', 'MENU', NULL, NULL, 20, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, '/dashboard', 'Dashboard', 'dashboard/index', NULL, 10, 'Odometer', NULL, '{"affix": true, "hidden": false, "i18nKey": "menu.dashboard", "keepAlive": true, "tabClosable": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (210, 'fota:product', '产品管理', 'MENU', NULL, NULL, 20, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, '/product', 'Product', 'product/index', NULL, 20, 'Box', NULL, '{"hidden": false, "i18nKey": "menu.product", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (230, 'fota:firmware', '固件管理', 'MENU', NULL, NULL, 20, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, '/firmware', 'Firmware', 'firmware/index', NULL, 40, 'Cpu', NULL, '{"hidden": false, "i18nKey": "menu.firmware", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (240, 'fota:policy', '策略管理', 'MENU', NULL, NULL, 20, 'active', '2026-03-12 07:30:44.299102', NULL, '2026-03-12 07:30:44.299102', NULL, NULL, '/policy', 'Policy', 'policy/index', NULL, 50, 'Document', NULL, '{"hidden": false, "i18nKey": "menu.policy", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (130, 'sys:dict_type', '字典类型管理', 'MENU', NULL, NULL, 10, 'active', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL, '/system/dict', 'SystemDict', 'system/dict/index', NULL, 40, 'Collection', NULL, '{"hidden": false, "i18nKey": "menu.dict", "keepAlive": true, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1136, 'fota:policy:start_test', '策略管理-开始测试', 'BUTTON', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.656794', NULL, '2026-03-12 07:30:44.656794', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1137, 'fota:policy:verify', '策略管理-验证通过', 'BUTTON', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.656794', NULL, '2026-03-12 07:30:44.656794', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1138, 'fota:policy:release', '策略管理-发布到生产', 'BUTTON', NULL, NULL, 240, 'active', '2026-03-12 07:30:44.656794', NULL, '2026-03-12 07:30:44.656794', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (330, 'monitor:operation-log', '操作日志', 'MENU', NULL, NULL, 30, 'active', '2026-03-12 07:30:44.832525', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, '/monitor/operation-log', 'MonitorOperationLog', 'monitor/operation-log', NULL, 40, 'Tickets', NULL, '{"hidden": false, "i18nKey": "menu.monitorOperationLog", "keepAlive": false, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (30, 'monitor', '系统监控', 'MENU', NULL, NULL, NULL, 'active', '2026-03-12 07:30:44.808122', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, '/monitor', 'MonitorRoot', 'LAYOUT', '/monitor/load', 60, 'Monitor', NULL, '{"hidden": false, "i18nKey": "menu.monitor", "keepAlive": false, "tabHidden": false, "alwaysShow": true, "tabClosable": false, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (300, 'monitor:load', '负载监控', 'MENU', NULL, NULL, 30, 'active', '2026-03-12 07:30:44.832525', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, '/monitor/load', 'MonitorLoad', 'monitor/load', NULL, 10, 'DataLine', NULL, '{"hidden": false, "i18nKey": "menu.monitorLoad", "keepAlive": false, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (310, 'monitor:cache', '缓存监控', 'MENU', NULL, NULL, 30, 'active', '2026-03-12 07:30:44.832525', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, '/monitor/cache', 'MonitorCache', 'monitor/cache', NULL, 20, 'Coin', NULL, '{"hidden": false, "i18nKey": "menu.monitorCache", "keepAlive": false, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (320, 'monitor:log', '日志监控', 'MENU', NULL, NULL, 30, 'active', '2026-03-12 07:30:44.832525', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, '/monitor/log', 'MonitorLog', 'monitor/log', NULL, 30, 'Document', NULL, '{"hidden": false, "i18nKey": "menu.monitorLog", "keepAlive": false, "tabHidden": false, "tabClosable": true, "breadcrumbHidden": false}');
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1153, 'monitor:load:read', '负载监控-查看', 'API', NULL, NULL, 300, 'active', '2026-03-12 07:30:44.865447', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1154, 'monitor:load:update', '负载监控-参数调整', 'API', NULL, NULL, 300, 'active', '2026-03-12 07:30:44.865447', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1155, 'monitor:cache:read', '缓存监控-查看', 'API', NULL, NULL, 310, 'active', '2026-03-12 07:30:44.882408', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1156, 'monitor:cache:update', '缓存监控-缓存清理', 'API', NULL, NULL, 310, 'active', '2026-03-12 07:30:44.882408', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1157, 'monitor:log:read', '日志监控-查看', 'API', NULL, NULL, 320, 'active', '2026-03-12 07:30:44.896201', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
INSERT INTO "sys_permissions" ("id", "code", "name", "type", "path", "method", "parent_id", "status", "created_at", "created_by", "updated_at", "updated_by", "deleted_at", "route_path", "route_name", "component_key", "redirect_path", "menu_sort", "icon", "external_link", "menu_config") VALUES (1158, 'monitor:operation-log:read', '操作日志-查看', 'API', NULL, NULL, 330, 'active', '2026-03-12 07:30:44.896201', NULL, '2026-03-12 07:30:44.896201', NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS sys_user_role;
create table if not exists sys_user_role
(
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id)
    );

comment on table sys_user_role is '用户-角色关联表：存储用户与角色的多对多关联关系';

comment on column sys_user_role.user_id is '用户ID（外键）';

comment on column sys_user_role.role_id is '角色ID（外键）';

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
BEGIN;
INSERT INTO "sys_user_role" ("user_id", "role_id") VALUES (1, 1);
COMMIT;

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS sys_role_permission;
create table if not exists sys_role_permission
(
    role_id       bigint not null,
    permission_id bigint not null,
    primary key (role_id, permission_id)
    );

comment on table sys_role_permission is '角色-权限关联表：存储角色与权限的多对多关联关系';

comment on column sys_role_permission.role_id is '角色ID（外键）';

comment on column sys_role_permission.permission_id is '权限ID（外键）';

-- ----------------------------
-- Records of sys_role_permission
-- ----------------------------
BEGIN;
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1001);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1002);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1003);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1004);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1005);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1011);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1012);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1013);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1014);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1015);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1021);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1022);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1023);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1024);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1031);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1032);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1033);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1034);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1041);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1042);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1043);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1044);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1001);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1002);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1003);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1004);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1005);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1011);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1012);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1013);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1014);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1021);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1031);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1032);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1033);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1034);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1041);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1042);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1043);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1044);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1101);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1111);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1112);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1113);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1114);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1141);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1142);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1143);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1144);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1121);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1122);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1123);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1124);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1125);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1131);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1132);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1133);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1134);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1135);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1101);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1111);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1141);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1142);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1121);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1131);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 10);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 100);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 110);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 120);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 130);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 10);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 100);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 110);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 120);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 130);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 20);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 140);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 200);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 210);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 220);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 230);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 240);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 220);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 200);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 210);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 230);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 240);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1131);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1132);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1133);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1134);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1136);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1137);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1101);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1111);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1141);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1142);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (3, 1121);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1131);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1132);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1133);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1134);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1135);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1136);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1137);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1138);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1101);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1111);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1112);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1113);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1114);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1121);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1122);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1123);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1124);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1125);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1141);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1142);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1143);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (4, 1144);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1136);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1137);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1138);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 30);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 30);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 300);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 310);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 320);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 330);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 300);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 310);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 320);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 330);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1153);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1154);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1153);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1155);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1156);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1155);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1157);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (1, 1158);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1157);
INSERT INTO "sys_role_permission" ("role_id", "permission_id") VALUES (2, 1158);
COMMIT;


-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS sys_dict_type;
create table if not exists sys_dict_type
(
    id          bigserial
    primary key,
    code        varchar(64)                         not null,
    name        varchar(128)                        not null,
    i18n_key    varchar(128),
    status      varchar(16)                         not null,
    description varchar(256),
    created_at  timestamp default CURRENT_TIMESTAMP not null,
    created_by  bigint,
    updated_at  timestamp default CURRENT_TIMESTAMP not null,
    updated_by  bigint,
    deleted_at  timestamp
    );

comment on table sys_dict_type is '字典类型表：存储系统字典类型信息，如设备状态、升级状态等';

comment on column sys_dict_type.id is '字典类型ID';

comment on column sys_dict_type.code is '字典类型编码（唯一）';

comment on column sys_dict_type.name is '字典类型名称';

comment on column sys_dict_type.i18n_key is '国际化key前缀（如 device.status）';

comment on column sys_dict_type.status is '字典类型状态';

comment on column sys_dict_type.description is '字典类型描述';

create unique index if not exists ux_sys_dict_type_code
    on sys_dict_type (code);

create index if not exists ix_sys_dict_type_status
    on sys_dict_type (status);

create index if not exists ix_sys_dict_type_deleted_at
    on sys_dict_type (deleted_at)
    where (deleted_at IS NULL);

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
BEGIN;
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (1, 'user_status', '用户状态', NULL, 'active', '用户账户状态', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (2, 'role_status', '角色状态', NULL, 'active', '角色状态', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (3, 'perm_status', '权限状态', NULL, 'active', '权限状态', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (4, 'region_bootstrap_secret', '分区初始密钥', NULL, 'active', '分区首次握手使用的初始密钥', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (5, 'json_schema.device_tags', '设备标签字段定义', 'jsonSchema.deviceTags', 'active', '用于定义设备 tags 字段的配置化子字段（支持地区、环境、用户等级等）', '2026-03-12 07:30:44.490838', NULL, '2026-03-12 07:30:44.490838', NULL, NULL);
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (6, 'json_schema.firmware_tags', '固件版本标签字段定义', 'jsonSchema.firmwareTags', 'active', '用于定义固件版本 tags 字段的配置化子字段（支持稳定性、构建类型等）', '2026-03-12 07:30:44.490838', NULL, '2026-03-12 07:30:44.490838', NULL, NULL);
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (7, 'json_schema.firmware_meta', '固件版本元数据字段定义', 'jsonSchema.firmwareMeta', 'active', '用于定义固件版本 meta 字段的配置化子字段（支持 i18n、changelog 等）', '2026-03-12 07:30:44.490838', NULL, '2026-03-12 07:30:44.490838', NULL, NULL);
INSERT INTO "sys_dict_type" ("id", "code", "name", "i18n_key", "status", "description", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (8, 'supported_languages', '支持的语言', 'dict.supportedLanguages', 'active', '系统支持的语言列表，用于多语言字段（如固件版本 i18n 升级说明）', '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
COMMIT;

-- ----------------------------
-- Table structure for sys_dict_item
-- ----------------------------
DROP TABLE IF EXISTS sys_dict_item;
create table if not exists sys_dict_item
(
    id           bigserial
    primary key,
    dict_type_id bigint                              not null,
    label        varchar(128)                        not null,
    value        varchar(128)                        not null,
    i18n_key     varchar(256),
    sort_order   integer                             not null,
    status       varchar(16)                         not null,
    extra        jsonb,
    created_at   timestamp default CURRENT_TIMESTAMP not null,
    created_by   bigint,
    updated_at   timestamp default CURRENT_TIMESTAMP not null,
    updated_by   bigint,
    deleted_at   timestamp
    );

comment on table sys_dict_item is '字典项表：存储字典类型下的具体字典项';

comment on column sys_dict_item.id is '字典项ID';

comment on column sys_dict_item.dict_type_id is '字典类型ID';

comment on column sys_dict_item.label is '字典项标签';

comment on column sys_dict_item.value is '字典项值';

comment on column sys_dict_item.i18n_key is '国际化key（如 device.status.online）';

comment on column sys_dict_item.sort_order is '排序号';

comment on column sys_dict_item.status is '字典项状态';

comment on column sys_dict_item.extra is '扩展信息（JSON）';

create unique index if not exists ux_sys_dict_item
    on sys_dict_item (dict_type_id, value);

create index if not exists ix_sys_dict_item_dict_type_id
    on sys_dict_item (dict_type_id);

create index if not exists ix_sys_dict_item_status
    on sys_dict_item (status);

create index if not exists ix_sys_dict_item_deleted_at
    on sys_dict_item (deleted_at)
    where (deleted_at IS NULL);

create index if not exists ix_sys_dict_item_extra_gin
    on sys_dict_item using gin (extra);

-- ----------------------------
-- Records of sys_dict_item
-- ----------------------------
BEGIN;
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (1, 1, '正常', 'active', NULL, 1, 'active', NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (2, 1, '禁用', 'disabled', NULL, 2, 'active', NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (3, 1, '锁定', 'locked', NULL, 3, 'active', NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (4, 2, '正常', 'active', NULL, 1, 'active', NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (5, 2, '禁用', 'disabled', NULL, 2, 'active', NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (6, 3, '正常', 'active', NULL, 1, 'active', NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (7, 3, '禁用', 'disabled', NULL, 2, 'active', NULL, '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (8, 4, 'us-east', 'us-east', NULL, 1, 'active', '{"secret": "your-secret-1"}', '2026-03-12 07:30:44.236216', NULL, '2026-03-12 07:30:44.236216', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (9, 5, '环境', 'env', 'jsonSchema.deviceTags.env', 20, 'active', '{"kind": "json_field_definition", "schema": {"type": "select", "options": [{"label": "生产", "value": "prod"}, {"label": "预发", "value": "staging"}, {"label": "测试", "value": "test"}, {"label": "开发", "value": "dev"}], "required": false, "defaultValue": "prod"}, "schemaVersion": 1}', '2026-03-12 07:30:44.501623', NULL, '2026-03-12 07:30:44.501623', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (11, 8, '简体中文', 'zh-CN', 'language.zhCN', 10, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (12, 8, 'English (US)', 'en-US', 'language.enUS', 20, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (13, 8, '日本語', 'ja-JP', 'language.jaJP', 30, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (14, 8, '한국어', 'ko-KR', 'language.koKR', 40, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (15, 8, 'Deutsch', 'de-DE', 'language.deDE', 50, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (16, 8, 'Français', 'fr-FR', 'language.frFR', 60, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (17, 8, 'Español', 'es-ES', 'language.esES', 70, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (18, 8, 'Italiano', 'it-IT', 'language.itIT', 80, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (19, 8, 'Português', 'pt-PT', 'language.ptPT', 90, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (20, 8, 'Русский', 'ru-RU', 'language.ruRU', 100, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (21, 8, 'العربية', 'ar-SA', 'language.arSA', 110, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (22, 8, '繁體中文', 'zh-TW', 'language.zhTW', 120, 'active', NULL, '2026-03-12 07:30:44.51425', NULL, '2026-03-12 07:30:44.51425', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (23, 7, '显示名称', 'show_name', 'jsonSchema.firmwareMeta.show_name', 5, 'active', '{"kind": "json_field_definition", "schema": {"help": "用于在设备端显示的友好版本名称，留空则根据构建类型和版本号自动生成", "type": "string", "required": false, "validator": {"maxLength": 100, "minLength": 0}, "placeholder": "友好版本名称"}, "schemaVersion": 1}', '2026-03-12 07:30:44.533293', NULL, '2026-03-12 07:30:44.533293', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (24, 7, '构建类型', 'build_type', 'jsonSchema.firmwareMeta.build_type', 10, 'active', '{"kind": "json_field_definition", "schema": {"help": "固件的构建类型", "type": "select", "options": [{"label": "正式版", "value": "release"}, {"label": "调试版", "value": "debug"}, {"label": "测试版", "value": "test"}, {"label": "Beta", "value": "beta"}], "required": false, "defaultValue": "release"}, "schemaVersion": 1}', '2026-03-12 07:30:44.533293', NULL, '2026-03-12 07:30:44.533293', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (25, 7, '升级内容', 'i18n', 'jsonSchema.firmwareMeta.i18n', 25, 'active', '{"kind": "json_field_definition", "schema": {"help": "多语言升级内容，key 为语言代码（BCP-47），value 为对应语言的描述文本", "type": "i18n", "required": false, "validator": {"i18nMaxLength": 2000}, "i18nConfig": {"minLocales": 0, "defaultLocales": ["en-US"], "allowCustomLocale": true}}, "schemaVersion": 1}', '2026-03-12 07:30:44.533293', NULL, '2026-03-12 07:30:44.533293', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (26, 7, '更新日志', 'changelog', 'jsonSchema.firmwareMeta.changelog', 30, 'active', '{"kind": "json_field_definition", "schema": {"help": "固件版本的更新日志（纯文本，支持 Markdown 格式）", "type": "textarea", "required": false, "validator": {"maxLength": 5000, "minLength": 0}, "placeholder": "请输入更新日志，每行一个更新点", "textareaConfig": {"maxRows": 10, "minRows": 4}}, "schemaVersion": 1}', '2026-03-12 07:30:44.533293', NULL, '2026-03-12 07:30:44.533293', NULL, NULL);
INSERT INTO "sys_dict_item" ("id", "dict_type_id", "label", "value", "i18n_key", "sort_order", "status", "extra", "created_at", "created_by", "updated_at", "updated_by", "deleted_at") VALUES (27, 7, '分区固件标识', 'part', 'jsonSchema.firmwareMeta.part', 3, 'active', '{"kind": "json_field_definition", "schema": {"help": "分区固件标识，用于多部分固件升级（主固件、引导加载器等）", "type": "select", "options": [{"label": "主固件", "value": "main"}, {"label": "引导加载器", "value": "bootloader"}, {"label": "固件", "value": "firmware"}, {"label": "应用", "value": "app"}, {"label": "系统", "value": "system"}], "required": false, "defaultValue": "main"}, "schemaVersion": 1}', '2026-03-12 07:30:44.773622', NULL, '2026-03-12 07:30:44.773622', NULL, NULL);
COMMIT;