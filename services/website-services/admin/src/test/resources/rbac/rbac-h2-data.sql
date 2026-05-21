DELETE FROM admin_role_permission;
DELETE FROM admin_user_role;
DELETE FROM admin_permission;
DELETE FROM admin_role;
DELETE FROM admin_user;

INSERT INTO admin_user (id, username, display_name, status, deleted, created_at, updated_at, version)
VALUES
    (100, 'editor_user', 'Editor', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (200, 'super_user', 'Super', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (300, 'norole_user', 'No Role', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (400, 'disabled_role_user', 'Disabled Role', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (500, 'admin_mgr', 'Admin Manager', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO admin_role (id, role_code, role_name, status, deleted, created_at, updated_at, version)
VALUES
    (1, 'editor', 'Editor', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2, 'super_admin', 'Super Admin', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (3, 'disabled_role', 'Disabled Role', 'DISABLED', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 'admin', 'Admin', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO admin_permission (id, permission_code, permission_name, resource_type, resource_key, status, deleted, created_at, updated_at, version)
VALUES
    (1, 'admin:role:list', 'List roles', 'ACTION', 'role:list', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2, 'admin:role:detail', 'Role detail', 'ACTION', 'role:detail', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (3, 'admin:role:create', 'Create role', 'ACTION', 'role:create', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 'admin:role:update', 'Update role', 'ACTION', 'role:update', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (5, 'admin:role:delete', 'Delete role', 'ACTION', 'role:delete', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (6, 'admin:role:permission:view', 'View role permissions', 'ACTION', 'role:permission:view', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (7, 'admin:role:permission:update', 'Update role permissions', 'ACTION', 'role:permission:update', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (8, 'admin:permission:list', 'List permissions', 'ACTION', 'permission:list', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (9, 'admin:user:list', 'List users', 'ACTION', 'user:list', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (10, 'admin:rbac:check', 'RBAC check', 'ACTION', 'rbac:check', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (11, 'admin:menu:system', 'System menu', 'MENU', 'system', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (12, 'admin:user:role:view', 'View user roles', 'ACTION', 'user:role:view', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (13, 'admin:user:role:update', 'Update user roles', 'ACTION', 'user:role:update', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO admin_user_role (admin_user_id, admin_role_id, deleted, created_at, updated_at, version)
VALUES
    (100, 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (200, 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (400, 3, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (500, 4, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO admin_role_permission (admin_role_id, admin_permission_id, deleted, created_at, updated_at, version)
VALUES
    (4, 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 3, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 4, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 5, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 6, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 7, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 8, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 12, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 13, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 9, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 10, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 11, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- 显式 id 插入后推进 H2 自增序列，避免后续 insert 撞主键被误判为 role_code 重复
ALTER TABLE admin_user ALTER COLUMN id RESTART WITH 501;
ALTER TABLE admin_role ALTER COLUMN id RESTART WITH 5;
ALTER TABLE admin_permission ALTER COLUMN id RESTART WITH 14;
