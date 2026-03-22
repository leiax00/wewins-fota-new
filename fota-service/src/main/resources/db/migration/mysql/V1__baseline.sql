-- =============================================
-- products 表
-- =============================================
CREATE TABLE IF NOT EXISTS products
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '产品唯一标识',
    name                 VARCHAR(255) NOT NULL COMMENT '产品名称',
    manufacturer         VARCHAR(255) COMMENT '制造商',
    model                VARCHAR(255) COMMENT '产品型号',
    check_period_seconds INT          NOT NULL DEFAULT 21600 COMMENT '默认检测周期（秒），默认6小时',
    remark               TEXT COMMENT '产品备注',
    created_at           DATETIME              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by           BIGINT COMMENT '创建人用户ID',
    updated_at           DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by           BIGINT COMMENT '更新人用户ID',
    deleted_at           DATETIME COMMENT '软删除时间',

    INDEX idx_products_name (name),
    INDEX idx_products_model (model),
    INDEX idx_products_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='产品表';


-- =============================================
-- device_import_batches 表
-- =============================================
CREATE TABLE IF NOT EXISTS device_import_batches (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_name    VARCHAR(255)  NOT NULL COMMENT '批次名称',
    status        VARCHAR(32)   NOT NULL DEFAULT 'IMPORTING' COMMENT 'IMPORTING/SUCCESS/FAILED/PARTIAL',
    product_id    BIGINT COMMENT '关联产品ID',
    source_file   VARCHAR(1024) COMMENT '导入文件路径',
    total_count   INT           DEFAULT 0,
    success_count INT           DEFAULT 0,
    failed_count  INT           DEFAULT 0,
    error_message TEXT COMMENT '失败原因',
    started_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    finished_at   DATETIME,
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by    BIGINT,
    deleted_at    DATETIME,

    UNIQUE KEY uk_batch_product_name (product_id, batch_name),
    INDEX idx_dib_status (status),
    INDEX idx_dib_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备导入批次表';


-- =============================================
-- devices 主表
-- =============================================
CREATE TABLE IF NOT EXISTS devices
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '设备唯一标识',
    imei                  VARCHAR(255) NOT NULL COMMENT '设备IMEI号',
    product_id            BIGINT       NOT NULL COMMENT '关联产品ID',
    status                VARCHAR(50)  NOT NULL COMMENT '设备状态 ACTIVE/INACTIVE/LOST',
    first_seen_at         DATETIME COMMENT '首次上线时间',
    last_seen_at          DATETIME COMMENT '最后在线时间',
    import_batch_id       BIGINT COMMENT '导入批次ID',
    -- JSON 仅做展示用，不作为查询条件
    version_parts         JSON COMMENT '当前多部分版本快照（展示用）',
    initial_version_parts JSON COMMENT '首次上线版本快照（展示用）',
    tags                  JSON COMMENT '设备标签快照（展示用）',
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by            BIGINT COMMENT '创建人用户ID',
    updated_at            DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by            BIGINT COMMENT '更新人用户ID',

    UNIQUE KEY uk_devices_imei (imei),
    INDEX idx_devices_product_id (product_id),
    INDEX idx_devices_status (status),
    INDEX idx_devices_last_seen_at (last_seen_at),
    INDEX idx_devices_first_seen_at (first_seen_at),
    INDEX idx_devices_import_batch_id (import_batch_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备表';

-- =============================================
-- device_tags  设备标签拆行表
-- 替代 devices.tags JSONB GIN 索引
-- =============================================
CREATE TABLE IF NOT EXISTS device_tags
(
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id  BIGINT       NOT NULL COMMENT '设备ID',
    tag_key    VARCHAR(64)  NOT NULL COMMENT '标签key，如 env/region',
    tag_value  VARCHAR(255) NOT NULL COMMENT '标签value，如 test/CN',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- 同一设备同一 key 唯一
    UNIQUE KEY uk_device_tags (device_id, tag_key),
    -- 策略匹配核心索引：按 key+value 找设备
    INDEX idx_device_tags_kv (tag_key, tag_value)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备标签拆行表，支持策略 target_device_tags 匹配';


-- =============================================
-- device_version_parts  设备各 part 当前版本快照
-- 替代 devices.version_parts JSONB GIN 索引
-- =============================================
CREATE TABLE IF NOT EXISTS device_version_parts
(
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id        BIGINT      NOT NULL COMMENT '设备ID',
    part_name        VARCHAR(64) NOT NULL COMMENT 'part名称，如 main/modem/ui',
    version_id       BIGINT COMMENT '关联 firmware_versions.id',
    version          VARCHAR(50) NOT NULL COMMENT '版本号快照，冗余避免join',
    internal_version VARCHAR(255) COMMENT '内部版本号快照',
    is_primary       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否为 primaryPart',
    updated_at       DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '版本最后更新时间',

    -- 同一设备同一 part 唯一
    UNIQUE KEY uk_dvp_device_part (device_id, part_name),
    -- 按版本查设备列表 / 统计版本分布
    INDEX idx_dvp_version_id (version_id),
    INDEX idx_dvp_part_version (part_name, version_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='设备各 part 当前版本快照表';


-- =============================================
-- firmware_versions 表
-- =============================================
CREATE TABLE IF NOT EXISTS firmware_versions
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '固件版本唯一标识',
    product_id          BIGINT      NOT NULL COMMENT '关联产品ID',
    version             VARCHAR(50) NOT NULL COMMENT '版本号，如1.0.0',
    internal_version    VARCHAR(255) COMMENT '内部版本号（build tag）',
    tags                JSON COMMENT '固件标签，固定key，如{"hardware_version":"v2","region":"CN"}，用于设备匹配',
    meta                JSON COMMENT '扩展元数据，多语言描述/changelog等，仅展示用',
    package_status      VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '固件包状态：NONE（无包）/ UPLOADED（已上传临时文件）/ READY（已转存对象存储）/ FAILED（失败）',
    file_name           VARCHAR(255) COMMENT '固件原始文件名',
    file_url            VARCHAR(1024) COMMENT '固件下载地址',
    file_size           BIGINT COMMENT '文件大小（字节）',
    md5                 VARCHAR(32) COMMENT 'MD5校验和',
    sha256              VARCHAR(64) COMMENT 'SHA-256校验和',
    package_uploaded_at DATETIME COMMENT '最近一次上传完成时间',
    created_at          DATETIME             DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT,
    updated_at          DATETIME             DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    deleted_at          DATETIME COMMENT '软删除时间',

    INDEX idx_fv_product_version (product_id, version, internal_version),
    INDEX idx_fv_product_package_status (product_id, package_status),
    INDEX idx_fv_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='固件版本表';

-- =============================================
-- upgrade_policies 表
-- =============================================
CREATE TABLE IF NOT EXISTS upgrade_policies
(
    id                      BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '策略唯一标识',
    product_id              BIGINT       NOT NULL COMMENT '关联产品ID',
    name                    VARCHAR(255) NOT NULL COMMENT '策略名称',
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'DRAFT/TESTING/VERIFIED/ACTIVE/PAUSED/EXPIRED',
    priority                INT                   DEFAULT 0 COMMENT '优先级，数值越大优先级越高',
    gray_rate               INT                   DEFAULT 0 COMMENT '灰度比例（0-100）',
    trigger_mode            VARCHAR(20)  NOT NULL DEFAULT 'BOTH' COMMENT 'AUTO/MANUAL/BOTH',
    target_mode             VARCHAR(32)  NOT NULL DEFAULT 'ALL' COMMENT 'ALL/DEVICE_IDS/DEVICE_BATCHES/DEVICE_TAGS',
    target_device_batch_ids JSON COMMENT '目标批次ID列表',
    target_imeis            JSON COMMENT '目标IMEI列表，最多100个',
    target_device_tags      JSON COMMENT '设备标签过滤条件',
    time_window             JSON COMMENT '时间窗口配置',
    target_version_id       BIGINT       NOT NULL COMMENT '目标固件版本ID',
    source_versions         JSON COMMENT '允许升级的源版本ID列表',
    remark                  TEXT COMMENT '策略备注',
    created_at              DATETIME              DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT,
    updated_at              DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by              BIGINT,
    deleted_at              DATETIME COMMENT '软删除时间',

    -- 缓存加载：按产品捞所有ACTIVE策略，最核心的查询
    INDEX idx_up_product_status (product_id, status),
    -- 管理后台按目标版本反查关联策略
    INDEX idx_up_target_version (target_version_id),
    INDEX idx_up_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='升级策略表';

-- sys_users
CREATE TABLE IF NOT EXISTS sys_users
(
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名（唯一）',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt）',
    display_name  VARCHAR(128) COMMENT '显示名称',
    email         VARCHAR(128) COMMENT '邮箱',
    phone         VARCHAR(32) COMMENT '手机号',
    status        VARCHAR(16)  NOT NULL COMMENT '用户状态',
    last_login_at DATETIME COMMENT '最后登录时间',
    tenant_id     BIGINT COMMENT '预留多租户',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by    BIGINT,
    deleted_at    DATETIME,

    UNIQUE KEY ux_users_username (username),
    -- email/phone 为 NULL 时不参与唯一判断，MySQL 天然支持
    UNIQUE KEY ux_users_email (email),
    UNIQUE KEY ux_users_phone (phone),
    INDEX ix_users_status (status),
    INDEX ix_users_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';


-- sys_roles
CREATE TABLE IF NOT EXISTS sys_roles
(
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    code        VARCHAR(64)  NOT NULL COMMENT '角色编码（唯一）',
    name        VARCHAR(128) NOT NULL COMMENT '角色名称',
    description VARCHAR(256) COMMENT '角色描述',
    status      VARCHAR(16)  NOT NULL COMMENT '角色状态',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    deleted_at  DATETIME,

    UNIQUE KEY ux_roles_code (code),
    INDEX ix_roles_status (status),
    INDEX ix_roles_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统角色表';


-- sys_permissions
CREATE TABLE IF NOT EXISTS sys_permissions
(
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    code          VARCHAR(128) NOT NULL COMMENT '权限编码（唯一）',
    name          VARCHAR(128) NOT NULL COMMENT '权限名称',
    type          VARCHAR(16)  NOT NULL COMMENT '权限类型（API/MENU/BUTTON）',
    path          VARCHAR(256) COMMENT 'API资源路径',
    method        VARCHAR(16) COMMENT 'HTTP 方法（GET/POST/PUT/DELETE）',
    parent_id     BIGINT COMMENT '父权限ID（用于构建权限树）',
    status        VARCHAR(16)  NOT NULL COMMENT '权限状态',
    route_path    VARCHAR(256) COMMENT '路由路径（仅 MODULE/MENU 类型有意义）',
    route_name    VARCHAR(128) COMMENT '路由名称（用于前端路由 name）',
    component_key VARCHAR(128) COMMENT '组件标识（前端白名单映射 key）',
    redirect_path VARCHAR(256) COMMENT '重定向路径',
    menu_sort     INT                   DEFAULT 0 COMMENT '同级菜单排序（越小越靠前）',
    icon          VARCHAR(64) COMMENT '菜单图标（Element Plus 图标名）',
    external_link VARCHAR(512) COMMENT '外链 URL（仅允许 https）',
    menu_config   JSON COMMENT '菜单扩展配置 JSON（hidden/keepAlive/affix 等）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    BIGINT,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by    BIGINT,
    deleted_at    DATETIME,

    UNIQUE KEY ux_permissions_code (code),
    INDEX ix_permissions_type (type),
    INDEX ix_permissions_parent_id (parent_id),
    INDEX ix_permissions_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统权限表';


-- sys_user_role
CREATE TABLE IF NOT EXISTS sys_user_role
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    INDEX ix_user_role_role_id (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色关联表';


-- sys_role_permission
CREATE TABLE IF NOT EXISTS sys_role_permission
(
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    INDEX ix_role_permission_permission_id (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限关联表';

-- sys_dict_type
CREATE TABLE IF NOT EXISTS sys_dict_type
(
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '字典类型ID',
    code        VARCHAR(64)  NOT NULL COMMENT '字典类型编码（唯一）',
    name        VARCHAR(128) NOT NULL COMMENT '字典类型名称',
    i18n_key    VARCHAR(128) COMMENT '国际化key前缀（如 device.status）',
    status      VARCHAR(16)  NOT NULL COMMENT '字典类型状态',
    description VARCHAR(256) COMMENT '字典类型描述',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  BIGINT,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by  BIGINT,
    deleted_at  DATETIME,

    UNIQUE KEY ux_dict_type_code (code),
    INDEX ix_dict_type_status (status),
    INDEX ix_dict_type_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='字典类型表';


-- sys_dict_item
CREATE TABLE IF NOT EXISTS sys_dict_item
(
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '字典项ID',
    dict_type_id BIGINT       NOT NULL COMMENT '字典类型ID',
    label        VARCHAR(128) NOT NULL COMMENT '字典项标签',
    value        VARCHAR(128) NOT NULL COMMENT '字典项值',
    i18n_key     VARCHAR(256) COMMENT '国际化key（如 device.status.online）',
    sort_order   INT          NOT NULL COMMENT '排序号',
    status       VARCHAR(16)  NOT NULL COMMENT '字典项状态',
    extra        JSON COMMENT '扩展信息（JSON）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   BIGINT,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   BIGINT,
    deleted_at   DATETIME,

    UNIQUE KEY ux_dict_item (dict_type_id, value),
    INDEX ix_dict_item_dict_type_id (dict_type_id),
    INDEX ix_dict_item_status (status),
    INDEX ix_dict_item_deleted_at (deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='字典项表';


-- sys_operation_logs
-- 注意：PG 里用了 gin_trgm_ops 做模糊搜索，MySQL 改用 FULLTEXT
CREATE TABLE IF NOT EXISTS sys_operation_logs
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    module_code           VARCHAR(64)  NOT NULL COMMENT '模块编码',
    resource_code         VARCHAR(64)  NOT NULL COMMENT '资源编码',
    action_code           VARCHAR(128) NOT NULL COMMENT '动作编码',
    operation_type        VARCHAR(32)  NOT NULL COMMENT '操作类型',
    target_id             VARCHAR(128) COMMENT '操作目标ID',
    target_name           VARCHAR(255) COMMENT '操作目标名称',
    operator_id           BIGINT COMMENT '操作人ID',
    operator_username     VARCHAR(128) COMMENT '操作人用户名',
    operator_display_name VARCHAR(128) COMMENT '操作人显示名',
    request_method        VARCHAR(16)  NOT NULL COMMENT 'HTTP方法',
    request_path          VARCHAR(255) NOT NULL COMMENT '请求路径',
    request_query         JSON COMMENT '请求查询参数',
    request_body          JSON COMMENT '请求体内容',
    client_ip             VARCHAR(64) COMMENT '客户端IP',
    user_agent            VARCHAR(1024) COMMENT '客户端标识',
    occurred_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',

    INDEX idx_logs_occurred_at (occurred_at DESC),
    INDEX idx_logs_operator_time (operator_id, occurred_at DESC),
    INDEX idx_logs_module_time (module_code, occurred_at DESC),
    INDEX idx_logs_action_time (action_code, occurred_at DESC),
    -- gin_trgm_ops 改用 FULLTEXT，需要搜索用户名时走 MATCH AGAINST
    FULLTEXT INDEX idx_logs_operator_username_ft (operator_username, operator_display_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='后台操作日志表';


CREATE TABLE IF NOT EXISTS async_task (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '任务唯一标识',
    biz_type   VARCHAR(64)  COMMENT '业务类型，如 DEVICE_IMPORT/FIRMWARE_UPLOAD',
    biz_id     VARCHAR(64)  COMMENT '业务ID，关联具体业务记录',
    stage      VARCHAR(32)  NOT NULL COMMENT '任务阶段/状态',
    percent    INT          NOT NULL DEFAULT 0 COMMENT '进度百分比（0-100）',
    message    VARCHAR(256) COMMENT '进度描述信息',
    error_msg  TEXT COMMENT '错误信息',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    created_by BIGINT COMMENT '创建人用户ID',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    updated_by BIGINT COMMENT '更新人用户ID',

    INDEX idx_async_task_biz_type_biz_id (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步任务进度追踪表';

