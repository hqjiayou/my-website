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

INSERT INTO admin_permission (
    id, permission_code, permission_name, resource_type, resource_key, status,
    parent_id, sort_order, route_path, icon,
    deleted, created_at, updated_at, version)
VALUES
    (1, 'admin:role:list', 'List roles', 'ACTION', 'role:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2, 'admin:role:detail', 'Role detail', 'ACTION', 'role:detail', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (3, 'admin:role:create', 'Create role', 'ACTION', 'role:create', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 'admin:role:update', 'Update role', 'ACTION', 'role:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (5, 'admin:role:delete', 'Delete role', 'ACTION', 'role:delete', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (6, 'admin:role:permission:view', 'View role permissions', 'ACTION', 'role:permission:view', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (7, 'admin:role:permission:update', 'Update role permissions', 'ACTION', 'role:permission:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (8, 'admin:permission:list', 'List permissions', 'ACTION', 'permission:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (9, 'admin:user:list', 'List users', 'ACTION', 'user:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (10, 'admin:rbac:check', 'RBAC check', 'ACTION', 'rbac:check', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (11, 'admin:menu:system', 'System menu', 'MENU', 'system', 'ACTIVE', NULL, 10, NULL, 'setting', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (12, 'admin:user:role:view', 'View user roles', 'ACTION', 'user:role:view', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (13, 'admin:user:role:update', 'Update user roles', 'ACTION', 'user:role:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (14, 'admin:menu:system.user', 'User management menu', 'MENU', 'system.user', 'ACTIVE', 11, 10, '/system/users', 'user', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (15, 'admin:menu:system.role', 'Role management menu', 'MENU', 'system.role', 'ACTIVE', 11, 20, '/system/roles', 'team', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (16, 'admin:menu:system.permission', 'Permission menu', 'MENU', 'system.permission', 'ACTIVE', 11, 30, '/system/permissions', 'safety', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (17, 'admin:menu:system.menu', 'Menu management menu', 'MENU', 'system.menu', 'ACTIVE', 11, 40, '/system/menus', 'menu', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (18, 'admin:menu:list', 'Menu tree list', 'ACTION', 'menu:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (19, 'admin:menu:detail', 'Menu detail', 'ACTION', 'menu:detail', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (20, 'admin:menu:create', 'Create menu', 'ACTION', 'menu:create', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (21, 'admin:menu:update', 'Update menu', 'ACTION', 'menu:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (22, 'admin:menu:delete', 'Delete menu', 'ACTION', 'menu:delete', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (23, 'admin:menu:sort', 'Sort menus', 'ACTION', 'menu:sort', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (24, 'admin:menu:bind', 'Bind menu permissions', 'ACTION', 'menu:bind', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

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
    (4, 14, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 15, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 16, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 17, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 18, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 19, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 20, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 21, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 22, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 23, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 24, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 9, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 10, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 11, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

ALTER TABLE admin_user ALTER COLUMN id RESTART WITH 501;
ALTER TABLE admin_role ALTER COLUMN id RESTART WITH 5;
ALTER TABLE admin_permission ALTER COLUMN id RESTART WITH 25;
