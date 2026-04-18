ALTER TABLE `upgrade_policies`
  ADD COLUMN `affected_total` bigint NOT NULL DEFAULT '0' COMMENT '策略影响设备总数快照';

-- 统计分析分组节点（仅作为权限父节点，不显示在菜单中）
INSERT INTO `sys_permissions` (`id`, `code`, `name`, `type`, `path`, `method`, `parent_id`, `status`, `route_path`, `route_name`, `component_key`, `redirect_path`, `menu_sort`, `icon`, `external_link`, `menu_config`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted`)
VALUES (250, 'fota:statistics', '统计分析', 'MENU', NULL, NULL, 20, 'active', NULL, NULL, NULL, NULL, 60, NULL, NULL, '{"hidden": true}', NOW(), NULL, NOW(), NULL, 0);

-- 统计模块 API 权限
INSERT INTO `sys_permissions` (`id`, `code`, `name`, `type`, `path`, `method`, `parent_id`, `status`, `route_path`, `route_name`, `component_key`, `redirect_path`, `menu_sort`, `icon`, `external_link`, `menu_config`, `created_at`, `created_by`, `updated_at`, `updated_by`, `deleted`)
VALUES (1151, 'fota:statistics:read', '统计分析-查询', 'API', NULL, NULL, 250, 'active', NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, NOW(), NULL, NOW(), NULL, 0);

-- 超级管理员角色 (role_id=1)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (1, 1151);
-- 运维管理员角色 (role_id=2)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (2, 1151);
-- 开发角色 (role_id=3)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (3, 1151);
-- 策略运营角色 (role_id=4)
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`) VALUES (4, 1151);

-- 固件版本设备数统计（每天一条，记录该版本各 part 的存量设备数）
CREATE TABLE `stat_version_device_count` (
  `id`            bigint   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id`    bigint   NOT NULL COMMENT '所属产品ID',
  `version_id`    bigint   NOT NULL COMMENT '固件版本ID',
  `device_count`  bigint   NOT NULL DEFAULT 0 COMMENT '该版本 part 下的设备数（含所有 part，不限主分区）',
  `stat_time`     datetime NOT NULL COMMENT '统计时间（快照时间即计算时间）',
  `created_at`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_stat_time` (`version_id`, `stat_time`),
  KEY `idx_product_stat_time` (`product_id`, `stat_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='固件版本设备数统计（按版本按天）';
