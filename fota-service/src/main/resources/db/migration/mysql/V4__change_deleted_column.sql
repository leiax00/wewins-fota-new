ALTER TABLE products ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE products SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE products DROP COLUMN deleted_at;

ALTER TABLE device_import_batches ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE device_import_batches SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE device_import_batches DROP COLUMN deleted_at;

ALTER TABLE firmware_versions ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE firmware_versions SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE firmware_versions DROP COLUMN deleted_at;

ALTER TABLE upgrade_policies ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE upgrade_policies SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE upgrade_policies DROP COLUMN deleted_at;

ALTER TABLE sys_users ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE sys_users SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE sys_users DROP COLUMN deleted_at;

ALTER TABLE sys_roles ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE sys_roles SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE sys_roles DROP COLUMN deleted_at;

ALTER TABLE sys_permissions ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE sys_permissions SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE sys_permissions DROP COLUMN deleted_at;

ALTER TABLE sys_dict_type ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE sys_dict_type SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE sys_dict_type DROP COLUMN deleted_at;

ALTER TABLE sys_dict_item ADD COLUMN deleted SMALLINT NOT NULL DEFAULT 0;
UPDATE sys_dict_item SET deleted = 1 WHERE deleted_at IS NOT NULL;
ALTER TABLE sys_dict_item DROP COLUMN deleted_at;
