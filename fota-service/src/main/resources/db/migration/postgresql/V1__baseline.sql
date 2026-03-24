--
-- PostgreSQL database dump
--

-- Dumped from database version 18.2 (Debian 18.2-1.pgdg12+1)
-- Dumped by pg_dump version 18.2 (Debian 18.2-1.pgdg12+1)

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

--
-- Name: async_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.async_task (
    id bigint NOT NULL,
    biz_type character varying(64),
    biz_id character varying(64),
    stage character varying(32) NOT NULL,
    percent integer DEFAULT 0 NOT NULL,
    message character varying(256),
    error_msg text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint
);

CREATE SEQUENCE public.async_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.async_task_id_seq OWNED BY public.async_task.id;


--
-- Name: device_import_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_import_batches (
    id bigint NOT NULL,
    batch_name character varying(255) NOT NULL,
    status character varying(32) DEFAULT 'IMPORTING'::character varying NOT NULL,
    product_id bigint,
    source_file character varying(1024),
    total_count integer DEFAULT 0,
    success_count integer DEFAULT 0,
    failed_count integer DEFAULT 0,
    error_message text,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    finished_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.device_import_batches IS '设备导入批次表：支持批次状态管理和导入统计';
COMMENT ON COLUMN public.device_import_batches.id IS '批次唯一标识';
COMMENT ON COLUMN public.device_import_batches.batch_name IS '批次名称（用户自定义或自动生成）';
COMMENT ON COLUMN public.device_import_batches.status IS '批次状态（IMPORTING/SUCCESS/FAILED/PARTIAL）';
COMMENT ON COLUMN public.device_import_batches.product_id IS '关联的产品ID（导入时指定的产品）';
COMMENT ON COLUMN public.device_import_batches.source_file IS '导入文件路径或标识';
COMMENT ON COLUMN public.device_import_batches.total_count IS '导入总数量';
COMMENT ON COLUMN public.device_import_batches.success_count IS '成功数量';
COMMENT ON COLUMN public.device_import_batches.failed_count IS '失败数量';
COMMENT ON COLUMN public.device_import_batches.error_message IS '失败原因';
COMMENT ON COLUMN public.device_import_batches.started_at IS '开始导入时间';
COMMENT ON COLUMN public.device_import_batches.finished_at IS '结束导入时间';
COMMENT ON COLUMN public.device_import_batches.created_at IS '创建时间';
COMMENT ON COLUMN public.device_import_batches.created_by IS '创建人用户ID';
COMMENT ON COLUMN public.device_import_batches.updated_at IS '更新时间';
COMMENT ON COLUMN public.device_import_batches.updated_by IS '更新人用户ID';

CREATE SEQUENCE public.device_import_batches_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.device_import_batches_id_seq OWNED BY public.device_import_batches.id;


--
-- Name: device_initial_version_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_initial_version_parts (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    part_name character varying(64) NOT NULL,
    version_id bigint,
    version character varying(50) NOT NULL,
    internal_version character varying(255),
    is_primary smallint DEFAULT 0 NOT NULL,
    recorded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE public.device_initial_version_parts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.device_initial_version_parts_id_seq OWNED BY public.device_initial_version_parts.id;


--
-- Name: device_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_tags (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    tag_key character varying(64) NOT NULL,
    tag_value character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE public.device_tags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.device_tags_id_seq OWNED BY public.device_tags.id;


--
-- Name: device_version_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_version_parts (
    id bigint NOT NULL,
    device_id bigint NOT NULL,
    part_name character varying(64) NOT NULL,
    version_id bigint,
    version character varying(50) NOT NULL,
    internal_version character varying(255),
    is_primary smallint DEFAULT 0 NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE public.device_version_parts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.device_version_parts_id_seq OWNED BY public.device_version_parts.id;


--
-- Name: devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.devices (
    id bigint NOT NULL,
    imei character varying(255) NOT NULL,
    product_id bigint,
    status character varying(50) NOT NULL,
    first_seen_at timestamp without time zone,
    last_seen_at timestamp without time zone,
    import_batch_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint
);

COMMENT ON TABLE public.devices IS '设备表：存储所有设备的基本信息和当前状态';
COMMENT ON COLUMN public.devices.id IS '设备唯一标识';
COMMENT ON COLUMN public.devices.imei IS '设备 IMEI 号（唯一）';
COMMENT ON COLUMN public.devices.product_id IS '关联的产品 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN public.devices.status IS '设备状态（ACTIVE, INACTIVE, LOST, etc.）';
COMMENT ON COLUMN public.devices.first_seen_at IS '设备第一次上线时间（首次检测时间）';
COMMENT ON COLUMN public.devices.last_seen_at IS '最后一次在线时间';
COMMENT ON COLUMN public.devices.import_batch_id IS '导入批次ID';
COMMENT ON COLUMN public.devices.created_at IS '创建时间';
COMMENT ON COLUMN public.devices.created_by IS '创建人用户ID';
COMMENT ON COLUMN public.devices.updated_at IS '更新时间';
COMMENT ON COLUMN public.devices.updated_by IS '更新人用户ID';

CREATE SEQUENCE public.devices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.devices_id_seq OWNED BY public.devices.id;


--
-- Name: firmware_version_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.firmware_version_tags (
    id bigint NOT NULL,
    firmware_version_id bigint NOT NULL,
    tag_key character varying(64) NOT NULL,
    tag_value character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE public.firmware_version_tags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.firmware_version_tags_id_seq OWNED BY public.firmware_version_tags.id;


--
-- Name: firmware_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.firmware_versions (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    version character varying(50) NOT NULL,
    internal_version character varying(255),
    meta jsonb,
    package_status character varying(20) DEFAULT 'NONE'::character varying NOT NULL,
    file_name character varying(255),
    file_url character varying(1024),
    file_size bigint,
    md5 character varying(32),
    sha256 character varying(64),
    package_uploaded_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.firmware_versions IS '固件版本表：存储所有固件版本的信息和文件元数据';
COMMENT ON COLUMN public.firmware_versions.id IS '固件版本唯一标识';
COMMENT ON COLUMN public.firmware_versions.product_id IS '关联的产品 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN public.firmware_versions.version IS '版本号（如 1.0.0）';
COMMENT ON COLUMN public.firmware_versions.internal_version IS '内部版本号（build tag），用于与 version 组合唯一确定固件版本（如 ASR_YEMEN_M476_V11_B03_Build02）';
COMMENT ON COLUMN public.firmware_versions.meta IS '扩展元数据（多语言描述、changelog、扩展字段）';
COMMENT ON COLUMN public.firmware_versions.package_status IS '固件包状态：NONE（无包）/ UPLOADED（已上传临时文件）/ READY（已转存对象存储）/ FAILED（失败）';
COMMENT ON COLUMN public.firmware_versions.file_name IS '固件原始文件名（上传时文件名，package_status=READY 时应有值）';
COMMENT ON COLUMN public.firmware_versions.file_url IS '固件文件下载地址';
COMMENT ON COLUMN public.firmware_versions.file_size IS '固件文件大小（字节）';
COMMENT ON COLUMN public.firmware_versions.md5 IS 'MD5 校验和';
COMMENT ON COLUMN public.firmware_versions.sha256 IS 'SHA-256 校验和';
COMMENT ON COLUMN public.firmware_versions.package_uploaded_at IS '固件包最近一次上传完成时间（临时上传成功或最终转存成功时更新）';
COMMENT ON COLUMN public.firmware_versions.created_at IS '创建时间';
COMMENT ON COLUMN public.firmware_versions.created_by IS '创建人用户ID';
COMMENT ON COLUMN public.firmware_versions.updated_at IS '更新时间';
COMMENT ON COLUMN public.firmware_versions.updated_by IS '更新人用户ID';

CREATE SEQUENCE public.firmware_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.firmware_versions_id_seq OWNED BY public.firmware_versions.id;

--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    manufacturer character varying(255),
    model character varying(255),
    check_period_seconds integer DEFAULT 21600 NOT NULL,
    remark text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.products IS '产品表：存储设备产品的基本信息';
COMMENT ON COLUMN public.products.id IS '产品唯一标识';
COMMENT ON COLUMN public.products.name IS '产品名称';
COMMENT ON COLUMN public.products.manufacturer IS '制造商';
COMMENT ON COLUMN public.products.model IS '产品型号';
COMMENT ON COLUMN public.products.check_period_seconds IS '产品默认检测周期（秒），默认 21600 秒（6 小时）';
COMMENT ON COLUMN public.products.remark IS '产品备注';
COMMENT ON COLUMN public.products.created_at IS '创建时间';
COMMENT ON COLUMN public.products.created_by IS '创建人用户ID';
COMMENT ON COLUMN public.products.updated_at IS '更新时间';
COMMENT ON COLUMN public.products.updated_by IS '更新人用户ID';

CREATE SEQUENCE public.products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.products_id_seq OWNED BY public.products.id;


--
-- Name: sys_dict_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_dict_item (
    id bigint NOT NULL,
    dict_type_id bigint NOT NULL,
    label character varying(128) NOT NULL,
    value character varying(128) NOT NULL,
    i18n_key character varying(256),
    sort_order integer NOT NULL,
    status character varying(16) NOT NULL,
    extra jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.sys_dict_item IS '字典项表：存储字典类型下的具体字典项';
COMMENT ON COLUMN public.sys_dict_item.id IS '字典项ID';
COMMENT ON COLUMN public.sys_dict_item.dict_type_id IS '字典类型ID';
COMMENT ON COLUMN public.sys_dict_item.label IS '字典项标签';
COMMENT ON COLUMN public.sys_dict_item.value IS '字典项值';
COMMENT ON COLUMN public.sys_dict_item.i18n_key IS '国际化key（如 device.status.online）';
COMMENT ON COLUMN public.sys_dict_item.sort_order IS '排序号';
COMMENT ON COLUMN public.sys_dict_item.status IS '字典项状态';
COMMENT ON COLUMN public.sys_dict_item.extra IS '扩展信息（JSON）';

CREATE SEQUENCE public.sys_dict_item_id_seq
    START WITH 10000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.sys_dict_item_id_seq OWNED BY public.sys_dict_item.id;


--
-- Name: sys_dict_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_dict_type (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    i18n_key character varying(128),
    status character varying(16) NOT NULL,
    description character varying(256),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.sys_dict_type IS '字典类型表：存储系统字典类型信息，如设备状态、升级状态等';
COMMENT ON COLUMN public.sys_dict_type.id IS '字典类型ID';
COMMENT ON COLUMN public.sys_dict_type.code IS '字典类型编码（唯一）';
COMMENT ON COLUMN public.sys_dict_type.name IS '字典类型名称';
COMMENT ON COLUMN public.sys_dict_type.i18n_key IS '国际化key前缀（如 device.status）';
COMMENT ON COLUMN public.sys_dict_type.status IS '字典类型状态';
COMMENT ON COLUMN public.sys_dict_type.description IS '字典类型描述';

CREATE SEQUENCE public.sys_dict_type_id_seq
    START WITH 1000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.sys_dict_type_id_seq OWNED BY public.sys_dict_type.id;


--
-- Name: sys_operation_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_operation_logs (
    id bigint NOT NULL,
    module_code character varying(64) NOT NULL,
    resource_code character varying(64) NOT NULL,
    action_code character varying(128) NOT NULL,
    operation_type character varying(32) NOT NULL,
    target_id character varying(128),
    target_name character varying(255),
    operator_id bigint,
    operator_username character varying(128),
    operator_display_name character varying(128),
    request_method character varying(16) NOT NULL,
    request_path character varying(255) NOT NULL,
    request_query jsonb,
    request_body jsonb,
    client_ip character varying(64),
    user_agent character varying(1024),
    occurred_at timestamp without time zone DEFAULT now() NOT NULL
);

COMMENT ON TABLE public.sys_operation_logs IS '后台操作日志表';
COMMENT ON COLUMN public.sys_operation_logs.module_code IS '模块编码';
COMMENT ON COLUMN public.sys_operation_logs.resource_code IS '资源编码';
COMMENT ON COLUMN public.sys_operation_logs.action_code IS '动作编码';
COMMENT ON COLUMN public.sys_operation_logs.operation_type IS '操作类型';
COMMENT ON COLUMN public.sys_operation_logs.target_id IS '操作目标ID';
COMMENT ON COLUMN public.sys_operation_logs.target_name IS '操作目标名称';
COMMENT ON COLUMN public.sys_operation_logs.operator_id IS '操作人ID';
COMMENT ON COLUMN public.sys_operation_logs.operator_username IS '操作人用户名';
COMMENT ON COLUMN public.sys_operation_logs.operator_display_name IS '操作人显示名';
COMMENT ON COLUMN public.sys_operation_logs.request_method IS 'HTTP方法';
COMMENT ON COLUMN public.sys_operation_logs.request_path IS '请求路径';
COMMENT ON COLUMN public.sys_operation_logs.request_query IS '请求查询参数';
COMMENT ON COLUMN public.sys_operation_logs.request_body IS '请求体内容';
COMMENT ON COLUMN public.sys_operation_logs.client_ip IS '客户端IP';
COMMENT ON COLUMN public.sys_operation_logs.user_agent IS '客户端标识';
COMMENT ON COLUMN public.sys_operation_logs.occurred_at IS '发生时间';

CREATE SEQUENCE public.sys_operation_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.sys_operation_logs_id_seq OWNED BY public.sys_operation_logs.id;


--
-- Name: sys_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_permissions (
    id bigint NOT NULL,
    code character varying(128) NOT NULL,
    name character varying(128) NOT NULL,
    type character varying(16) NOT NULL,
    path character varying(256),
    method character varying(16),
    parent_id bigint,
    status character varying(16) NOT NULL,
    route_path character varying(256),
    route_name character varying(128),
    component_key character varying(128),
    redirect_path character varying(256),
    menu_sort integer DEFAULT 0,
    icon character varying(64),
    external_link character varying(512),
    menu_config jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.sys_permissions IS '系统权限表：存储系统权限信息，支持 API、菜单、按钮等权限类型';
COMMENT ON COLUMN public.sys_permissions.id IS '权限ID';
COMMENT ON COLUMN public.sys_permissions.code IS '权限编码（唯一）';
COMMENT ON COLUMN public.sys_permissions.name IS '权限名称';
COMMENT ON COLUMN public.sys_permissions.type IS '权限类型（API/MENU/BUTTON）';
COMMENT ON COLUMN public.sys_permissions.path IS 'API 资源路径';
COMMENT ON COLUMN public.sys_permissions.method IS 'HTTP 方法（GET/POST/PUT/DELETE）';
COMMENT ON COLUMN public.sys_permissions.parent_id IS '父权限ID（用于构建权限树）';
COMMENT ON COLUMN public.sys_permissions.status IS '权限状态';
COMMENT ON COLUMN public.sys_permissions.route_path IS '路由路径（仅 MODULE/MENU 类型有意义）';
COMMENT ON COLUMN public.sys_permissions.route_name IS '路由名称（用于前端路由 name）';
COMMENT ON COLUMN public.sys_permissions.component_key IS '组件标识（前端白名单映射 key）';
COMMENT ON COLUMN public.sys_permissions.redirect_path IS '重定向路径';
COMMENT ON COLUMN public.sys_permissions.menu_sort IS '同级菜单排序（越小越靠前）';
COMMENT ON COLUMN public.sys_permissions.icon IS '菜单图标（Element Plus 图标名）';
COMMENT ON COLUMN public.sys_permissions.external_link IS '外链 URL（仅允许 https）';
COMMENT ON COLUMN public.sys_permissions.menu_config IS '菜单扩展配置 JSON（hidden/keepAlive/affix 等）';

CREATE SEQUENCE public.sys_permissions_id_seq
    START WITH 10000
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.sys_permissions_id_seq OWNED BY public.sys_permissions.id;


--
-- Name: sys_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_role_permission (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);

COMMENT ON TABLE public.sys_role_permission IS '角色-权限关联表：存储角色与权限的多对多关联关系';
COMMENT ON COLUMN public.sys_role_permission.role_id IS '角色ID（外键）';
COMMENT ON COLUMN public.sys_role_permission.permission_id IS '权限ID（外键）';


--
-- Name: sys_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_roles (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(128) NOT NULL,
    description character varying(256),
    status character varying(16) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.sys_roles IS '系统角色表：存储系统角色信息，用于 RBAC 权限控制';
COMMENT ON COLUMN public.sys_roles.id IS '角色ID';
COMMENT ON COLUMN public.sys_roles.code IS '角色编码（唯一）';
COMMENT ON COLUMN public.sys_roles.name IS '角色名称';
COMMENT ON COLUMN public.sys_roles.description IS '角色描述';
COMMENT ON COLUMN public.sys_roles.status IS '角色状态';

CREATE SEQUENCE public.sys_roles_id_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.sys_roles_id_seq OWNED BY public.sys_roles.id;


--
-- Name: sys_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_user_role (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);

COMMENT ON TABLE public.sys_user_role IS '用户-角色关联表：存储用户与角色的多对多关联关系';
COMMENT ON COLUMN public.sys_user_role.user_id IS '用户ID（外键）';
COMMENT ON COLUMN public.sys_user_role.role_id IS '角色ID（外键）';


--
-- Name: sys_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_users (
    id bigint NOT NULL,
    username character varying(64) NOT NULL,
    password_hash character varying(255) NOT NULL,
    display_name character varying(128),
    email character varying(128),
    phone character varying(32),
    status character varying(16) NOT NULL,
    last_login_at timestamp without time zone,
    tenant_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.sys_users IS '系统用户表：存储系统用户的基本信息和认证凭据';
COMMENT ON COLUMN public.sys_users.id IS '用户ID';
COMMENT ON COLUMN public.sys_users.username IS '用户名（唯一）';
COMMENT ON COLUMN public.sys_users.password_hash IS '密码哈希（BCrypt）';
COMMENT ON COLUMN public.sys_users.display_name IS '显示名称';
COMMENT ON COLUMN public.sys_users.email IS '邮箱';
COMMENT ON COLUMN public.sys_users.phone IS '手机号';
COMMENT ON COLUMN public.sys_users.status IS '用户状态';
COMMENT ON COLUMN public.sys_users.last_login_at IS '最后登录时间';
COMMENT ON COLUMN public.sys_users.tenant_id IS '租户ID（预留多租户）';

CREATE SEQUENCE public.sys_users_id_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.sys_users_id_seq OWNED BY public.sys_users.id;


--
-- Name: upgrade_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upgrade_policies (
    id bigint NOT NULL,
    product_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    priority integer DEFAULT 0,
    gray_rate integer DEFAULT 0,
    trigger_mode character varying(20) DEFAULT 'BOTH'::character varying NOT NULL,
    target_mode character varying(32) DEFAULT 'ALL'::character varying NOT NULL,
    time_window jsonb,
    target_version_id bigint NOT NULL,
    remark text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by bigint,
    deleted smallint DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.upgrade_policies IS '升级策略表：存储固件升级策略和配置';
COMMENT ON COLUMN public.upgrade_policies.id IS '策略唯一标识';
COMMENT ON COLUMN public.upgrade_policies.product_id IS '关联的产品 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN public.upgrade_policies.name IS '策略名称';
COMMENT ON COLUMN public.upgrade_policies.status IS '策略状态（DRAFT-草稿, TESTING-测试中, VERIFIED-已验证, ACTIVE-生产中, PAUSED-暂停, EXPIRED-过期）';
COMMENT ON COLUMN public.upgrade_policies.priority IS '优先级（数值越大优先级越高）';
COMMENT ON COLUMN public.upgrade_policies.gray_rate IS '灰度比例（0-100）';
COMMENT ON COLUMN public.upgrade_policies.trigger_mode IS '触发模式（AUTO:仅自动 / MANUAL:仅手动 / BOTH:不限制）';
COMMENT ON COLUMN public.upgrade_policies.target_mode IS '目标设备模式（ALL/DEVICE_IDS(实际为IMEI列表)/DEVICE_BATCHES/DEVICE_TAGS）';
COMMENT ON COLUMN public.upgrade_policies.time_window IS '时间窗口配置（JSONB）';
COMMENT ON COLUMN public.upgrade_policies.target_version_id IS '目标固件版本 ID（无外键约束，由应用层保证一致性）';
COMMENT ON COLUMN public.upgrade_policies.remark IS '策略备注';
COMMENT ON COLUMN public.upgrade_policies.created_at IS '创建时间';
COMMENT ON COLUMN public.upgrade_policies.created_by IS '创建人用户ID';
COMMENT ON COLUMN public.upgrade_policies.updated_at IS '更新时间';
COMMENT ON COLUMN public.upgrade_policies.updated_by IS '更新人用户ID';

CREATE SEQUENCE public.upgrade_policies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.upgrade_policies_id_seq OWNED BY public.upgrade_policies.id;


--
-- Name: upgrade_policy_source_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upgrade_policy_source_versions (
    policy_id bigint NOT NULL,
    source_version_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: upgrade_policy_target_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upgrade_policy_target_batches (
    policy_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: upgrade_policy_target_devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upgrade_policy_target_devices (
    policy_id bigint NOT NULL,
    imei character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: upgrade_policy_target_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upgrade_policy_target_tags (
    id bigint NOT NULL,
    policy_id bigint NOT NULL,
    tag_key character varying(64) NOT NULL,
    tag_value character varying(255) NOT NULL,
    operator character varying(16) DEFAULT 'EQ'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE public.upgrade_policy_target_tags_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;
ALTER SEQUENCE public.upgrade_policy_target_tags_id_seq OWNED BY public.upgrade_policy_target_tags.id;


ALTER TABLE ONLY public.async_task ALTER COLUMN id SET DEFAULT nextval('public.async_task_id_seq'::regclass);
ALTER TABLE ONLY public.device_import_batches ALTER COLUMN id SET DEFAULT nextval('public.device_import_batches_id_seq'::regclass);
ALTER TABLE ONLY public.device_initial_version_parts ALTER COLUMN id SET DEFAULT nextval('public.device_initial_version_parts_id_seq'::regclass);
ALTER TABLE ONLY public.device_tags ALTER COLUMN id SET DEFAULT nextval('public.device_tags_id_seq'::regclass);
ALTER TABLE ONLY public.device_version_parts ALTER COLUMN id SET DEFAULT nextval('public.device_version_parts_id_seq'::regclass);
ALTER TABLE ONLY public.devices ALTER COLUMN id SET DEFAULT nextval('public.devices_id_seq'::regclass);
ALTER TABLE ONLY public.firmware_version_tags ALTER COLUMN id SET DEFAULT nextval('public.firmware_version_tags_id_seq'::regclass);
ALTER TABLE ONLY public.firmware_versions ALTER COLUMN id SET DEFAULT nextval('public.firmware_versions_id_seq'::regclass);
ALTER TABLE ONLY public.products ALTER COLUMN id SET DEFAULT nextval('public.products_id_seq'::regclass);
ALTER TABLE ONLY public.sys_dict_item ALTER COLUMN id SET DEFAULT nextval('public.sys_dict_item_id_seq'::regclass);
ALTER TABLE ONLY public.sys_dict_type ALTER COLUMN id SET DEFAULT nextval('public.sys_dict_type_id_seq'::regclass);
ALTER TABLE ONLY public.sys_operation_logs ALTER COLUMN id SET DEFAULT nextval('public.sys_operation_logs_id_seq'::regclass);
ALTER TABLE ONLY public.sys_permissions ALTER COLUMN id SET DEFAULT nextval('public.sys_permissions_id_seq'::regclass);
ALTER TABLE ONLY public.sys_roles ALTER COLUMN id SET DEFAULT nextval('public.sys_roles_id_seq'::regclass);
ALTER TABLE ONLY public.sys_users ALTER COLUMN id SET DEFAULT nextval('public.sys_users_id_seq'::regclass);
ALTER TABLE ONLY public.upgrade_policies ALTER COLUMN id SET DEFAULT nextval('public.upgrade_policies_id_seq'::regclass);
ALTER TABLE ONLY public.upgrade_policy_target_tags ALTER COLUMN id SET DEFAULT nextval('public.upgrade_policy_target_tags_id_seq'::regclass);

ALTER TABLE ONLY public.async_task ADD CONSTRAINT async_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.device_import_batches
    ADD CONSTRAINT device_import_batches_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.device_initial_version_parts
    ADD CONSTRAINT device_initial_version_parts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.device_tags
    ADD CONSTRAINT device_tags_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.device_version_parts
    ADD CONSTRAINT device_version_parts_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_imei_key UNIQUE (imei);
ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.firmware_version_tags
    ADD CONSTRAINT firmware_version_tags_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.firmware_versions
    ADD CONSTRAINT firmware_versions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.upgrade_policy_source_versions
    ADD CONSTRAINT pk_upgrade_policy_source_versions PRIMARY KEY (policy_id, source_version_id);
ALTER TABLE ONLY public.upgrade_policy_target_batches
    ADD CONSTRAINT pk_upgrade_policy_target_batches PRIMARY KEY (policy_id, batch_id);
ALTER TABLE ONLY public.upgrade_policy_target_devices
    ADD CONSTRAINT pk_upgrade_policy_target_devices PRIMARY KEY (policy_id, imei);
ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_dict_item
    ADD CONSTRAINT sys_dict_item_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_dict_type
    ADD CONSTRAINT sys_dict_type_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_operation_logs
    ADD CONSTRAINT sys_operation_logs_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_permissions
    ADD CONSTRAINT sys_permissions_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_role_permission
    ADD CONSTRAINT sys_role_permission_pkey PRIMARY KEY (role_id, permission_id);
ALTER TABLE ONLY public.sys_roles
    ADD CONSTRAINT sys_roles_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.sys_user_role
    ADD CONSTRAINT sys_user_role_pkey PRIMARY KEY (user_id, role_id);
ALTER TABLE ONLY public.sys_users
    ADD CONSTRAINT sys_users_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.device_import_batches
    ADD CONSTRAINT uk_device_import_batches_product_batch UNIQUE (product_id, batch_name);
COMMENT ON CONSTRAINT uk_device_import_batches_product_batch ON public.device_import_batches IS '同一产品的批次名称唯一约束';
ALTER TABLE ONLY public.device_initial_version_parts
    ADD CONSTRAINT uk_device_initial_version_parts_device_part UNIQUE (device_id, part_name);
ALTER TABLE ONLY public.device_tags
    ADD CONSTRAINT uk_device_tags_device_key UNIQUE (device_id, tag_key);
ALTER TABLE ONLY public.device_version_parts
    ADD CONSTRAINT uk_device_version_parts_device_part UNIQUE (device_id, part_name);
ALTER TABLE ONLY public.firmware_version_tags
    ADD CONSTRAINT uk_firmware_version_tags_version_key UNIQUE (firmware_version_id, tag_key);
ALTER TABLE ONLY public.upgrade_policy_target_tags
    ADD CONSTRAINT uk_upgrade_policy_target_tags_policy_key UNIQUE (policy_id, tag_key);
ALTER TABLE ONLY public.upgrade_policies
    ADD CONSTRAINT upgrade_policies_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.upgrade_policy_target_tags
    ADD CONSTRAINT upgrade_policy_target_tags_pkey PRIMARY KEY (id);

CREATE INDEX idx_async_task_biz_type_biz_id ON public.async_task USING btree (biz_type, biz_id);
CREATE INDEX idx_device_import_batches_status ON public.device_import_batches USING btree (status);
CREATE INDEX idx_device_tags_device_id ON public.device_tags USING btree (device_id);
CREATE INDEX idx_device_tags_kv_device ON public.device_tags USING btree (tag_key, tag_value, device_id);
CREATE INDEX idx_devices_first_seen_at ON public.devices USING btree (first_seen_at);
CREATE INDEX idx_devices_import_batch_id ON public.devices USING btree (import_batch_id);
CREATE INDEX idx_devices_last_seen_at ON public.devices USING btree (last_seen_at);
CREATE INDEX idx_devices_product_id ON public.devices USING btree (product_id);
CREATE INDEX idx_devices_status ON public.devices USING btree (status);
CREATE INDEX idx_divp_device_id ON public.device_initial_version_parts USING btree (device_id);
CREATE INDEX idx_divp_version_id ON public.device_initial_version_parts USING btree (version_id);
CREATE INDEX idx_dvp_device_primary ON public.device_version_parts USING btree (device_id, is_primary);
CREATE INDEX idx_dvp_part_version_lookup ON public.device_version_parts USING btree (part_name, version_id);
CREATE INDEX idx_dvp_version_lookup ON public.device_version_parts USING btree (version_id);
CREATE INDEX idx_fv_meta_gin ON public.firmware_versions USING gin (meta);
CREATE INDEX idx_fv_product_id ON public.firmware_versions USING btree (product_id);
CREATE INDEX idx_fvt_kv_version ON public.firmware_version_tags USING btree (tag_key, tag_value, firmware_version_id);
CREATE INDEX idx_fvt_version_id ON public.firmware_version_tags USING btree (firmware_version_id);
CREATE INDEX idx_products_manufacturer ON public.products USING btree (manufacturer);
CREATE INDEX idx_products_name ON public.products USING btree (name);
CREATE INDEX idx_sys_operation_logs_action_time ON public.sys_operation_logs USING btree (action_code, occurred_at DESC);
CREATE INDEX idx_sys_operation_logs_module_time ON public.sys_operation_logs USING btree (module_code, occurred_at DESC);
CREATE INDEX idx_sys_operation_logs_occurred_at ON public.sys_operation_logs USING btree (occurred_at DESC);
CREATE INDEX idx_sys_operation_logs_operator_display_name_trgm ON public.sys_operation_logs USING gin (operator_display_name public.gin_trgm_ops);
CREATE INDEX idx_sys_operation_logs_operator_time ON public.sys_operation_logs USING btree (operator_id, occurred_at DESC);
CREATE INDEX idx_sys_operation_logs_operator_username_trgm ON public.sys_operation_logs USING gin (operator_username public.gin_trgm_ops);
CREATE INDEX idx_up_priority ON public.upgrade_policies USING btree (priority);
CREATE INDEX idx_up_product_id ON public.upgrade_policies USING btree (product_id);
CREATE INDEX idx_up_status ON public.upgrade_policies USING btree (status);
CREATE INDEX idx_up_target_mode ON public.upgrade_policies USING btree (target_mode);
CREATE INDEX idx_up_target_version ON public.upgrade_policies USING btree (target_version_id);
CREATE INDEX idx_up_time_window_gin ON public.upgrade_policies USING gin (time_window);
CREATE INDEX idx_up_trigger_mode ON public.upgrade_policies USING btree (trigger_mode);
CREATE INDEX idx_upsv_source_version_policy ON public.upgrade_policy_source_versions USING btree (source_version_id, policy_id);
CREATE INDEX idx_uptb_batch_policy ON public.upgrade_policy_target_batches USING btree (batch_id, policy_id);
CREATE INDEX idx_uptd_imei_policy ON public.upgrade_policy_target_devices USING btree (imei, policy_id);
CREATE INDEX idx_uptt_kv_policy ON public.upgrade_policy_target_tags USING btree (tag_key, tag_value, policy_id);
CREATE INDEX idx_uptt_policy_id ON public.upgrade_policy_target_tags USING btree (policy_id);
CREATE INDEX ix_sys_dict_item_dict_type_id ON public.sys_dict_item USING btree (dict_type_id);
CREATE INDEX ix_sys_dict_item_extra_gin ON public.sys_dict_item USING gin (extra);
CREATE INDEX ix_sys_dict_item_status ON public.sys_dict_item USING btree (status);
CREATE INDEX ix_sys_dict_type_status ON public.sys_dict_type USING btree (status);
CREATE INDEX ix_sys_permissions_parent_id ON public.sys_permissions USING btree (parent_id);
CREATE INDEX ix_sys_permissions_type ON public.sys_permissions USING btree (type);
CREATE INDEX ix_sys_roles_status ON public.sys_roles USING btree (status);
CREATE INDEX ix_sys_users_status ON public.sys_users USING btree (status);
CREATE UNIQUE INDEX ux_sys_dict_item ON public.sys_dict_item USING btree (dict_type_id, value);
CREATE UNIQUE INDEX ux_sys_dict_type_code ON public.sys_dict_type USING btree (code);
CREATE UNIQUE INDEX ux_sys_permissions_code ON public.sys_permissions USING btree (code);
CREATE UNIQUE INDEX ux_sys_roles_code ON public.sys_roles USING btree (code);
CREATE UNIQUE INDEX ux_sys_users_email ON public.sys_users USING btree (email) WHERE (email IS NOT NULL);
CREATE UNIQUE INDEX ux_sys_users_phone ON public.sys_users USING btree (phone) WHERE (phone IS NOT NULL);
CREATE UNIQUE INDEX ux_sys_users_username ON public.sys_users USING btree (username);
