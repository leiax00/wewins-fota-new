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

comment on column products.check_period_seconds is '产品默认检测周期（秒），默认 21600 秒（6 小时）';

comment on column products.remark is '产品备注';

comment on column products.created_at is '创建时间';

comment on column products.created_by is '创建人用户ID';

comment on column products.updated_at is '更新时间';

comment on column products.updated_by is '更新人用户ID';

comment on column products.deleted_at is '软删除时间';

create index if not exists idx_products_name
    on products (name);

create index if not exists idx_products_manufacturer
    on products (manufacturer);

create index if not exists idx_products_deleted_at
    on products (deleted_at)
    where (deleted_at IS NULL);

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

comment on column devices.version_parts is '多部分版本信息（JSONB）: {"parts": {"main": {"versionId": 101, "version": "1.0.0", "updatedAt": "xxx"}}, "primaryPart": "main"}';

comment on column devices.initial_version_parts is '设备第一次上线的版本信息（JSONB）: {"parts": {"main": {"versionId": 101, "version": "1.0.0", "updatedAt": "xxx"}}, "primaryPart": "main"}';

comment on column devices.status is '设备状态（ACTIVE, INACTIVE, LOST, etc.）';

comment on column devices.first_seen_at is '设备第一次上线时间（首次检测时间）';

comment on column devices.last_seen_at is '最后一次在线时间';

comment on column devices.tags is '设备标签（JSONB 对象，KV 结构，如 {"env":"test", "region":"CN", "network":"5G"}）';

comment on column devices.import_batch_id is '导入批次ID';

comment on column devices.created_at is '创建时间';

comment on column devices.created_by is '创建人用户ID';

comment on column devices.updated_at is '更新时间';

comment on column devices.updated_by is '更新人用户ID';

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

comment on column firmware_versions.internal_version is '内部版本号（build tag），用于与 version 组合唯一确定固件版本（如 ASR_YEMEN_M476_V11_B03_Build02）';

comment on column firmware_versions.tags is '版本标签（JSONB 对象，KV 结构，如 {"tag":"build01"}）';

comment on column firmware_versions.meta is '扩展元数据（多语言描述、changelog、扩展字段）';

comment on column firmware_versions.package_status is '固件包状态：NONE（无包）/ UPLOADED（已上传临时文件）/ READY（已转存对象存储）/ FAILED（失败）';

comment on column firmware_versions.file_name is '固件原始文件名（上传时文件名，package_status=READY 时应有值）';

comment on column firmware_versions.file_url is '固件文件下载地址';

comment on column firmware_versions.file_size is '固件文件大小（字节）';

comment on column firmware_versions.md5 is 'MD5 校验和';

comment on column firmware_versions.sha256 is 'SHA-256 校验和';

comment on column firmware_versions.package_uploaded_at is '固件包最近一次上传完成时间（临时上传成功或最终转存成功时更新）';

comment on column firmware_versions.created_at is '创建时间';

comment on column firmware_versions.created_by is '创建人用户ID';

comment on column firmware_versions.updated_at is '更新时间';

comment on column firmware_versions.updated_by is '更新人用户ID';

comment on column firmware_versions.deleted_at is '软删除时间';

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

create index if not exists idx_fv_product_version_internal
    on firmware_versions (product_id, version, internal_version)
    where (deleted_at IS NULL);

comment on index idx_fv_product_version_internal is '固件版本组合约束（仅未删除记录且有 internal_version）';

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

comment on column upgrade_policies.status is '策略状态（DRAFT-草稿, TESTING-测试中, VERIFIED-已验证, ACTIVE-生产中, PAUSED-暂停, EXPIRED-过期）';

comment on column upgrade_policies.priority is '优先级（数值越大优先级越高）';

comment on column upgrade_policies.gray_rate is '灰度比例（0-100）';

comment on column upgrade_policies.trigger_mode is '触发模式（AUTO:仅自动 / MANUAL:仅手动 / BOTH:不限制）';

comment on column upgrade_policies.target_mode is '目标设备模式（ALL/DEVICE_IDS(实际为IMEI列表)/DEVICE_BATCHES/DEVICE_TAGS）';

comment on column upgrade_policies.target_device_batch_ids is '目标设备批次ID列表（JSONB数组）';

comment on column upgrade_policies.target_imeis is '指定设备IMEI列表（JSONB 数组），当 target_mode = DEVICE_IDS 时使用';

comment on column upgrade_policies.target_device_tags is '设备标签过滤条件（JSONB）';

comment on column upgrade_policies.time_window is '时间窗口配置（JSONB）';

comment on column upgrade_policies.target_version_id is '目标固件版本 ID（无外键约束，由应用层保证一致性）';

comment on column upgrade_policies.source_versions is '允许升级的源版本 ID 列表（JSONB 数组，如 [10, 11, 12]）';

comment on column upgrade_policies.remark is '策略备注';

comment on column upgrade_policies.created_at is '创建时间';

comment on column upgrade_policies.created_by is '创建人用户ID';

comment on column upgrade_policies.updated_at is '更新时间';

comment on column upgrade_policies.updated_by is '更新人用户ID';

comment on column upgrade_policies.deleted_at is '软删除时间';

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

comment on column device_import_batches.product_id is '关联的产品ID（导入时指定的产品）';

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

comment on constraint uk_device_import_batches_product_batch on device_import_batches is '同一产品的批次名称唯一约束';

create index if not exists idx_device_import_batches_status
    on device_import_batches (status);

create index if not exists idx_device_import_batches_deleted_at
    on device_import_batches (deleted_at)
    where (deleted_at IS NULL);

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

create table if not exists sys_user_role
(
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id)
);

comment on table sys_user_role is '用户-角色关联表：存储用户与角色的多对多关联关系';

comment on column sys_user_role.user_id is '用户ID（外键）';

comment on column sys_user_role.role_id is '角色ID（外键）';

create table if not exists sys_role_permission
(
    role_id       bigint not null,
    permission_id bigint not null,
    primary key (role_id, permission_id)
);

comment on table sys_role_permission is '角色-权限关联表：存储角色与权限的多对多关联关系';

comment on column sys_role_permission.role_id is '角色ID（外键）';

comment on column sys_role_permission.permission_id is '权限ID（外键）';

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

create table if not exists sys_operation_logs
(
    id                    bigserial
        primary key,
    module_code           varchar(64)             not null,
    resource_code         varchar(64)             not null,
    action_code           varchar(128)            not null,
    operation_type        varchar(32)             not null,
    target_id             varchar(128),
    target_name           varchar(255),
    operator_id           bigint,
    operator_username     varchar(128),
    operator_display_name varchar(128),
    request_method        varchar(16)             not null,
    request_path          varchar(255)            not null,
    request_query         jsonb,
    request_body          jsonb,
    client_ip             varchar(64),
    user_agent            varchar(1024),
    occurred_at           timestamp default now() not null
);

comment on table sys_operation_logs is '后台操作日志表';

comment on column sys_operation_logs.module_code is '模块编码';

comment on column sys_operation_logs.resource_code is '资源编码';

comment on column sys_operation_logs.action_code is '动作编码';

comment on column sys_operation_logs.operation_type is '操作类型';

comment on column sys_operation_logs.target_id is '操作目标ID';

comment on column sys_operation_logs.target_name is '操作目标名称';

comment on column sys_operation_logs.operator_id is '操作人ID';

comment on column sys_operation_logs.operator_username is '操作人用户名';

comment on column sys_operation_logs.operator_display_name is '操作人显示名';

comment on column sys_operation_logs.request_method is 'HTTP方法';

comment on column sys_operation_logs.request_path is '请求路径';

comment on column sys_operation_logs.request_query is '请求查询参数';

comment on column sys_operation_logs.request_body is '请求体内容';

comment on column sys_operation_logs.client_ip is '客户端IP';

comment on column sys_operation_logs.user_agent is '客户端标识';

comment on column sys_operation_logs.occurred_at is '发生时间';

create index if not exists idx_sys_operation_logs_occurred_at
    on sys_operation_logs (occurred_at desc);

create index if not exists idx_sys_operation_logs_operator_time
    on sys_operation_logs (operator_id asc, occurred_at desc);

create index if not exists idx_sys_operation_logs_module_time
    on sys_operation_logs (module_code asc, occurred_at desc);

create index if not exists idx_sys_operation_logs_action_time
    on sys_operation_logs (action_code asc, occurred_at desc);

create index if not exists idx_sys_operation_logs_operator_username_trgm
    on sys_operation_logs using gin (operator_username gin_trgm_ops);

create index if not exists idx_sys_operation_logs_operator_display_name_trgm
    on sys_operation_logs using gin (operator_display_name gin_trgm_ops);

create table if not exists async_task
(
    id         bigserial
        primary key,
    biz_type   varchar(64),
    biz_id     varchar(64),
    stage      varchar(32)             not null,
    percent    integer   default 0     not null,
    message    varchar(256),
    error_msg  text,
    created_at timestamp default now() not null,
    created_by bigint,
    updated_at timestamp default now() not null,
    updated_by bigint
);

create index if not exists idx_async_task_biz_type_biz_id
    on async_task (biz_type, biz_id);

