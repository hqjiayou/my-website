DELETE FROM admin_role_permission;
DELETE FROM admin_user_role;
DELETE FROM admin_permission;
DELETE FROM admin_role;
DELETE FROM admin_user;
DELETE FROM site_reservation;
DELETE FROM site_content;
DELETE FROM media_file;
DELETE FROM site_appearance;
DELETE FROM site;
DELETE FROM site_template;

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
    (24, 'admin:menu:bind', 'Bind menu permissions', 'ACTION', 'menu:bind', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (25, 'admin:site:list', 'List sites', 'ACTION', 'site:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (26, 'admin:site:detail', 'Site detail', 'ACTION', 'site:detail', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (27, 'admin:site:create', 'Create site', 'ACTION', 'site:create', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (28, 'admin:site:update', 'Update site', 'ACTION', 'site:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (29, 'admin:site:delete', 'Delete site', 'ACTION', 'site:delete', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (30, 'admin:site:status', 'Update site status', 'ACTION', 'site:status', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (31, 'admin:menu:apps', 'Apps menu', 'MENU', 'apps', 'ACTIVE', NULL, 20, NULL, 'appstore', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (32, 'admin:menu:apps.sites', 'Sites menu', 'MENU', 'apps.sites', 'ACTIVE', 31, 10, '/apps/sites', 'layers', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (33, 'admin:user:detail', 'User detail', 'ACTION', 'user:detail', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (34, 'admin:user:create', 'Create user', 'ACTION', 'user:create', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (35, 'admin:user:update', 'Update user', 'ACTION', 'user:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (36, 'admin:user:delete', 'Delete user', 'ACTION', 'user:delete', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (37, 'admin:user:status', 'Update user status', 'ACTION', 'user:status', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (38, 'admin:template:list', 'List templates', 'ACTION', 'template:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (39, 'admin:template:detail', 'Template detail', 'ACTION', 'template:detail', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (40, 'admin:template:status', 'Update template status', 'ACTION', 'template:status', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (41, 'admin:menu:apps.templates', 'Templates menu', 'MENU', 'apps.templates', 'ACTIVE', 31, 20, '/apps/templates', 'block', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (42, 'admin:media:list', 'List site media', 'ACTION', 'media:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (43, 'admin:media:upload', 'Upload site media', 'ACTION', 'media:upload', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (44, 'admin:media:download', 'Download site media', 'ACTION', 'media:download', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (45, 'admin:media:delete', 'Delete site media', 'ACTION', 'media:delete', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (46, 'admin:content:list', 'List site content', 'ACTION', 'content:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (47, 'admin:content:create', 'Create site content', 'ACTION', 'content:create', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (48, 'admin:content:update', 'Update site content', 'ACTION', 'content:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (49, 'admin:content:delete', 'Delete site content', 'ACTION', 'content:delete', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (50, 'admin:content:status', 'Update site content status', 'ACTION', 'content:status', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (51, 'admin:reservation:list', 'List site reservations', 'ACTION', 'reservation:list', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (52, 'admin:reservation:create', 'Create site reservation', 'ACTION', 'reservation:create', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (53, 'admin:reservation:update', 'Update site reservation status', 'ACTION', 'reservation:update', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (54, 'admin:reservation:delete', 'Delete site reservation', 'ACTION', 'reservation:delete', 'ACTIVE', NULL, 0, NULL, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO site_template (
    id, code, name, site_type, status, description, default_primary_color, sort_order,
    deleted, created_at, updated_at, version)
VALUES
    (1, 'japanese-car-repair-v1', 'Japanese Car Repair v1', 'CAR_REPAIR', 'ENABLED',
     'Starter for local Japanese car repair shops.', '#B91C1C', 10,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (2, 'used-car-shop-v1', 'Used Car Shop v1', 'USED_CAR', 'DRAFT',
     'Starter for used-car dealers.', '#1D4ED8', 20,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (3, 'cake-shop-v1', 'Cake Shop v1', 'CAKE', 'ENABLED',
     'Starter for cake and dessert shops.', '#DB2777', 30,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 'salon-v1', 'Salon v1', 'SALON', 'DISABLED',
     'Starter for beauty salons.', '#7C3AED', 40,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (5, 'clinic-v1', 'Clinic v1', 'CLINIC', 'DRAFT',
     'Starter for clinics.', '#0F766E', 50,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (6, 'gyoseishoshi-v1', 'Gyoseishoshi Office v1', 'GYOSEISHOSHI', 'DRAFT',
     'Starter for administrative scrivener offices.', '#0F172A', 60,
     FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

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
    (4, 9, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 11, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
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
    (4, 25, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 26, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 27, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 28, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 29, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 30, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 31, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 32, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 33, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 34, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 35, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 36, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 37, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 38, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 39, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 40, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 41, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 42, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 43, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 44, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 45, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 46, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 47, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 48, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 49, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 50, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 51, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 52, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 53, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (4, 54, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 2, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 9, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 10, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (1, 11, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

ALTER TABLE admin_user ALTER COLUMN id RESTART WITH 501;
ALTER TABLE admin_role ALTER COLUMN id RESTART WITH 5;
ALTER TABLE admin_permission ALTER COLUMN id RESTART WITH 55;
ALTER TABLE site ALTER COLUMN id RESTART WITH 1;
ALTER TABLE site_template ALTER COLUMN id RESTART WITH 7;
ALTER TABLE media_file ALTER COLUMN id RESTART WITH 1;
ALTER TABLE site_content ALTER COLUMN id RESTART WITH 1;
ALTER TABLE site_reservation ALTER COLUMN id RESTART WITH 1;
