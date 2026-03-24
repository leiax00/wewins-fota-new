/*
 Navicat Premium Dump SQL

 Source Server         : localhost
 Source Server Type    : MySQL
 Source Server Version : 90400 (9.4.0)
 Source Host           : 192.168.2.4:3306
 Source Schema         : fota_bak

 Target Server Type    : MySQL
 Target Server Version : 90400 (9.4.0)
 File Encoding         : 65001

 Date: 24/03/2026 13:18:22
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for async_task
-- ----------------------------
DROP TABLE IF EXISTS `async_task`;
CREATE TABLE `async_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务唯一标识',
  `biz_type` varchar(64) DEFAULT NULL COMMENT '业务类型，如 DEVICE_IMPORT/FIRMWARE_UPLOAD',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务ID，关联具体业务记录',
  `stage` varchar(32) NOT NULL COMMENT '任务阶段/状态',
  `percent` int NOT NULL DEFAULT '0' COMMENT '进度百分比（0-100）',
  `message` varchar(256) DEFAULT NULL COMMENT '进度描述信息',
  `error_msg` text COMMENT '错误信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  PRIMARY KEY (`id`),
  KEY `idx_async_task_biz_type_biz_id` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步任务进度追踪表';

-- ----------------------------
-- Table structure for device_import_batches
-- ----------------------------
DROP TABLE IF EXISTS `device_import_batches`;
CREATE TABLE `device_import_batches` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_name` varchar(255) NOT NULL COMMENT '批次名称',
  `status` varchar(32) NOT NULL DEFAULT 'IMPORTING' COMMENT 'IMPORTING/SUCCESS/FAILED/PARTIAL',
  `product_id` bigint DEFAULT NULL COMMENT '关联产品ID',
  `source_file` varchar(1024) DEFAULT NULL COMMENT '导入文件路径',
  `total_count` int DEFAULT '0',
  `success_count` int DEFAULT '0',
  `failed_count` int DEFAULT '0',
  `error_message` text COMMENT '失败原因',
  `started_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `finished_at` datetime DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_product_name` (`product_id`,`batch_name`),
  KEY `idx_dib_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备导入批次表';

-- ----------------------------
-- Table structure for device_initial_version_parts
-- ----------------------------
DROP TABLE IF EXISTS `device_initial_version_parts`;
CREATE TABLE `device_initial_version_parts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL,
  `part_name` varchar(64) NOT NULL,
  `version_id` bigint DEFAULT NULL,
  `version` varchar(50) NOT NULL,
  `internal_version` varchar(255) DEFAULT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `recorded_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_divp_device_part` (`device_id`,`part_name`),
  KEY `idx_divp_device_id` (`device_id`),
  KEY `idx_divp_version_id` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备首次版本分片关联表';

-- ----------------------------
-- Table structure for device_tags
-- ----------------------------
DROP TABLE IF EXISTS `device_tags`;
CREATE TABLE `device_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `tag_key` varchar(64) NOT NULL COMMENT '标签key，如 env/region',
  `tag_value` varchar(255) NOT NULL COMMENT '标签value，如 test/CN',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_tags` (`device_id`,`tag_key`),
  KEY `idx_device_tags_kv` (`tag_key`,`tag_value`),
  KEY `idx_device_tags_kv_device` (`tag_key`,`tag_value`,`device_id`),
  KEY `idx_device_tags_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备标签拆行表，支持策略 target_device_tags 匹配';

-- ----------------------------
-- Table structure for device_version_parts
-- ----------------------------
DROP TABLE IF EXISTS `device_version_parts`;
CREATE TABLE `device_version_parts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_id` bigint NOT NULL COMMENT '设备ID',
  `part_name` varchar(64) NOT NULL COMMENT 'part名称，如 main/modem/ui',
  `version_id` bigint DEFAULT NULL COMMENT '关联 firmware_versions.id',
  `version` varchar(50) NOT NULL COMMENT '版本号快照，冗余避免join',
  `internal_version` varchar(255) DEFAULT NULL COMMENT '内部版本号快照',
  `is_primary` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否为 primaryPart',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '版本最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dvp_device_part` (`device_id`,`part_name`),
  KEY `idx_dvp_version_id` (`version_id`),
  KEY `idx_dvp_part_version` (`part_name`,`version_id`),
  KEY `idx_dvp_version_lookup` (`version_id`),
  KEY `idx_dvp_part_version_lookup` (`part_name`,`version_id`),
  KEY `idx_dvp_device_primary` (`device_id`,`is_primary`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备各 part 当前版本快照表';

-- ----------------------------
-- Table structure for devices
-- ----------------------------
DROP TABLE IF EXISTS `devices`;
CREATE TABLE `devices` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '设备唯一标识',
  `imei` varchar(255) NOT NULL COMMENT '设备IMEI号',
  `product_id` bigint NOT NULL COMMENT '关联产品ID',
  `status` varchar(50) NOT NULL COMMENT '设备状态 ACTIVE/INACTIVE/LOST',
  `first_seen_at` datetime DEFAULT NULL COMMENT '首次上线时间',
  `last_seen_at` datetime DEFAULT NULL COMMENT '最后在线时间',
  `import_batch_id` bigint DEFAULT NULL COMMENT '导入批次ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_devices_imei` (`imei`),
  KEY `idx_devices_product_id` (`product_id`),
  KEY `idx_devices_status` (`status`),
  KEY `idx_devices_last_seen_at` (`last_seen_at`),
  KEY `idx_devices_first_seen_at` (`first_seen_at`),
  KEY `idx_devices_import_batch_id` (`import_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='设备表';

-- ----------------------------
-- Table structure for firmware_version_tags
-- ----------------------------
DROP TABLE IF EXISTS `firmware_version_tags`;
CREATE TABLE `firmware_version_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `firmware_version_id` bigint NOT NULL,
  `tag_key` varchar(64) NOT NULL,
  `tag_value` varchar(255) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fvt_version_key` (`firmware_version_id`,`tag_key`),
  KEY `idx_fvt_kv_version` (`tag_key`,`tag_value`,`firmware_version_id`),
  KEY `idx_fvt_version_id` (`firmware_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固件标签关联表';

-- ----------------------------
-- Table structure for firmware_versions
-- ----------------------------
DROP TABLE IF EXISTS `firmware_versions`;
CREATE TABLE `firmware_versions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '固件版本唯一标识',
  `product_id` bigint NOT NULL COMMENT '关联产品ID',
  `version` varchar(50) NOT NULL COMMENT '版本号，如1.0.0',
  `internal_version` varchar(255) DEFAULT NULL COMMENT '内部版本号（build tag）',
  `meta` json DEFAULT NULL COMMENT '扩展元数据，多语言描述/changelog等，仅展示用',
  `package_status` varchar(20) NOT NULL DEFAULT 'NONE' COMMENT '固件包状态：NONE（无包）/ UPLOADED（已上传临时文件）/ READY（已转存对象存储）/ FAILED（失败）',
  `file_name` varchar(255) DEFAULT NULL COMMENT '固件原始文件名',
  `file_url` varchar(1024) DEFAULT NULL COMMENT '固件下载地址',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `md5` varchar(32) DEFAULT NULL COMMENT 'MD5校验和',
  `sha256` varchar(64) DEFAULT NULL COMMENT 'SHA-256校验和',
  `package_uploaded_at` datetime DEFAULT NULL COMMENT '最近一次上传完成时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_fv_product_version` (`product_id`,`version`,`internal_version`),
  KEY `idx_fv_product_package_status` (`product_id`,`package_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固件版本表';

-- ----------------------------
-- Table structure for products
-- ----------------------------
DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '产品唯一标识',
  `name` varchar(255) NOT NULL COMMENT '产品名称',
  `manufacturer` varchar(255) DEFAULT NULL COMMENT '制造商',
  `model` varchar(255) DEFAULT NULL COMMENT '产品型号',
  `check_period_seconds` int NOT NULL DEFAULT '21600' COMMENT '默认检测周期（秒），默认6小时',
  `remark` text COMMENT '产品备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人用户ID',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人用户ID',
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_products_name` (`name`),
  KEY `idx_products_model` (`model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品表';

-- ----------------------------
-- Table structure for sys_dict_item
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_item`;
CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典项ID',
  `dict_type_id` bigint NOT NULL COMMENT '字典类型ID',
  `label` varchar(128) NOT NULL COMMENT '字典项标签',
  `value` varchar(128) NOT NULL COMMENT '字典项值',
  `i18n_key` varchar(256) DEFAULT NULL COMMENT '国际化key（如 device.status.online）',
  `sort_order` int NOT NULL COMMENT '排序号',
  `status` varchar(16) NOT NULL COMMENT '字典项状态',
  `extra` json DEFAULT NULL COMMENT '扩展信息（JSON）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_dict_item` (`dict_type_id`,`value`),
  KEY `ix_dict_item_dict_type_id` (`dict_type_id`),
  KEY `ix_dict_item_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典项表';

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典类型ID',
  `code` varchar(64) NOT NULL COMMENT '字典类型编码（唯一）',
  `name` varchar(128) NOT NULL COMMENT '字典类型名称',
  `i18n_key` varchar(128) DEFAULT NULL COMMENT '国际化key前缀（如 device.status）',
  `status` varchar(16) NOT NULL COMMENT '字典类型状态',
  `description` varchar(256) DEFAULT NULL COMMENT '字典类型描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_dict_type_code` (`code`),
  KEY `ix_dict_type_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型表';

-- ----------------------------
-- Table structure for sys_operation_logs
-- ----------------------------
DROP TABLE IF EXISTS `sys_operation_logs`;
CREATE TABLE `sys_operation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module_code` varchar(64) NOT NULL COMMENT '模块编码',
  `resource_code` varchar(64) NOT NULL COMMENT '资源编码',
  `action_code` varchar(128) NOT NULL COMMENT '动作编码',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型',
  `target_id` varchar(128) DEFAULT NULL COMMENT '操作目标ID',
  `target_name` varchar(255) DEFAULT NULL COMMENT '操作目标名称',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `operator_username` varchar(128) DEFAULT NULL COMMENT '操作人用户名',
  `operator_display_name` varchar(128) DEFAULT NULL COMMENT '操作人显示名',
  `request_method` varchar(16) NOT NULL COMMENT 'HTTP方法',
  `request_path` varchar(255) NOT NULL COMMENT '请求路径',
  `request_query` json DEFAULT NULL COMMENT '请求查询参数',
  `request_body` json DEFAULT NULL COMMENT '请求体内容',
  `client_ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(1024) DEFAULT NULL COMMENT '客户端标识',
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_logs_occurred_at` (`occurred_at` DESC),
  KEY `idx_logs_operator_time` (`operator_id`,`occurred_at` DESC),
  KEY `idx_logs_module_time` (`module_code`,`occurred_at` DESC),
  KEY `idx_logs_action_time` (`action_code`,`occurred_at` DESC),
  FULLTEXT KEY `idx_logs_operator_username_ft` (`operator_username`,`operator_display_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台操作日志表';

-- ----------------------------
-- Table structure for sys_permissions
-- ----------------------------
DROP TABLE IF EXISTS `sys_permissions`;
CREATE TABLE `sys_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `code` varchar(128) NOT NULL COMMENT '权限编码（唯一）',
  `name` varchar(128) NOT NULL COMMENT '权限名称',
  `type` varchar(16) NOT NULL COMMENT '权限类型（API/MENU/BUTTON）',
  `path` varchar(256) DEFAULT NULL COMMENT 'API资源路径',
  `method` varchar(16) DEFAULT NULL COMMENT 'HTTP 方法（GET/POST/PUT/DELETE）',
  `parent_id` bigint DEFAULT NULL COMMENT '父权限ID（用于构建权限树）',
  `status` varchar(16) NOT NULL COMMENT '权限状态',
  `route_path` varchar(256) DEFAULT NULL COMMENT '路由路径（仅 MODULE/MENU 类型有意义）',
  `route_name` varchar(128) DEFAULT NULL COMMENT '路由名称（用于前端路由 name）',
  `component_key` varchar(128) DEFAULT NULL COMMENT '组件标识（前端白名单映射 key）',
  `redirect_path` varchar(256) DEFAULT NULL COMMENT '重定向路径',
  `menu_sort` int DEFAULT '0' COMMENT '同级菜单排序（越小越靠前）',
  `icon` varchar(64) DEFAULT NULL COMMENT '菜单图标（Element Plus 图标名）',
  `external_link` varchar(512) DEFAULT NULL COMMENT '外链 URL（仅允许 https）',
  `menu_config` json DEFAULT NULL COMMENT '菜单扩展配置 JSON（hidden/keepAlive/affix 等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_permissions_code` (`code`),
  KEY `ix_permissions_type` (`type`),
  KEY `ix_permissions_parent_id` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统权限表';

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `ix_role_permission_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

-- ----------------------------
-- Table structure for sys_roles
-- ----------------------------
DROP TABLE IF EXISTS `sys_roles`;
CREATE TABLE `sys_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `code` varchar(64) NOT NULL COMMENT '角色编码（唯一）',
  `name` varchar(128) NOT NULL COMMENT '角色名称',
  `description` varchar(256) DEFAULT NULL COMMENT '角色描述',
  `status` varchar(16) NOT NULL COMMENT '角色状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_roles_code` (`code`),
  KEY `ix_roles_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色表';

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `ix_user_role_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- ----------------------------
-- Table structure for sys_users
-- ----------------------------
DROP TABLE IF EXISTS `sys_users`;
CREATE TABLE `sys_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(64) NOT NULL COMMENT '用户名（唯一）',
  `password_hash` varchar(255) NOT NULL COMMENT '密码哈希（BCrypt）',
  `display_name` varchar(128) DEFAULT NULL COMMENT '显示名称',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号',
  `status` varchar(16) NOT NULL COMMENT '用户状态',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `tenant_id` bigint DEFAULT NULL COMMENT '预留多租户',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_users_username` (`username`),
  UNIQUE KEY `ux_users_email` (`email`),
  UNIQUE KEY `ux_users_phone` (`phone`),
  KEY `ix_users_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

-- ----------------------------
-- Table structure for upgrade_policies
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_policies`;
CREATE TABLE `upgrade_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '策略唯一标识',
  `product_id` bigint NOT NULL COMMENT '关联产品ID',
  `name` varchar(255) NOT NULL COMMENT '策略名称',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'DRAFT/TESTING/VERIFIED/ACTIVE/PAUSED/EXPIRED',
  `priority` int DEFAULT '0' COMMENT '优先级，数值越大优先级越高',
  `gray_rate` int DEFAULT '0' COMMENT '灰度比例（0-100）',
  `trigger_mode` varchar(20) NOT NULL DEFAULT 'BOTH' COMMENT 'AUTO/MANUAL/BOTH',
  `target_mode` varchar(32) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/DEVICE_IDS/DEVICE_BATCHES/DEVICE_TAGS',
  `time_window` json DEFAULT NULL COMMENT '时间窗口配置',
  `target_version_id` bigint NOT NULL COMMENT '目标固件版本ID',
  `remark` text COMMENT '策略备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `created_by` bigint DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `updated_by` bigint DEFAULT NULL,
  `deleted` smallint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_up_product_status` (`product_id`,`status`),
  KEY `idx_up_target_version` (`target_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='升级策略表';

-- ----------------------------
-- Table structure for upgrade_policy_source_versions
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_policy_source_versions`;
CREATE TABLE `upgrade_policy_source_versions` (
  `policy_id` bigint NOT NULL,
  `source_version_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`policy_id`,`source_version_id`),
  KEY `idx_upsv_source_version_policy` (`source_version_id`,`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略源版本关联表';

-- ----------------------------
-- Table structure for upgrade_policy_target_batches
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_policy_target_batches`;
CREATE TABLE `upgrade_policy_target_batches` (
  `policy_id` bigint NOT NULL,
  `batch_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`policy_id`,`batch_id`),
  KEY `idx_uptb_batch_policy` (`batch_id`,`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略目标批次关联表';

-- ----------------------------
-- Table structure for upgrade_policy_target_devices
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_policy_target_devices`;
CREATE TABLE `upgrade_policy_target_devices` (
  `policy_id` bigint NOT NULL,
  `imei` varchar(64) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`policy_id`,`imei`),
  KEY `idx_uptd_imei_policy` (`imei`,`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略目标设备关联表';

-- ----------------------------
-- Table structure for upgrade_policy_target_tags
-- ----------------------------
DROP TABLE IF EXISTS `upgrade_policy_target_tags`;
CREATE TABLE `upgrade_policy_target_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `policy_id` bigint NOT NULL,
  `tag_key` varchar(64) NOT NULL,
  `tag_value` varchar(255) NOT NULL,
  `operator` varchar(16) NOT NULL DEFAULT 'EQ',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uptt_policy_key` (`policy_id`,`tag_key`),
  KEY `idx_uptt_kv_policy` (`tag_key`,`tag_value`,`policy_id`),
  KEY `idx_uptt_policy_id` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='策略目标标签关联表';

SET FOREIGN_KEY_CHECKS = 1;
