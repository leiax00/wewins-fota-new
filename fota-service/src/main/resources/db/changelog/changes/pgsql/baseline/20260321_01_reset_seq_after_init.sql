-- 系统用户表: 最大 ID=1，预留到 100
SELECT setval('sys_users_id_seq', 100, true);

-- 系统角色表: 最大 ID=4，预留到 100
SELECT setval('sys_roles_id_seq', 100, true);

-- 系统权限表: 最大 ID=1158，预留到 10000
SELECT setval('sys_permissions_id_seq', 10000, true);

-- 字典类型表: 最大 ID=8，预留到 1000
SELECT setval('sys_dict_type_id_seq', 1000, true);

-- 字典项表: 最大 ID=27，预留到 10000
SELECT setval('sys_dict_item_id_seq', 10000, true);