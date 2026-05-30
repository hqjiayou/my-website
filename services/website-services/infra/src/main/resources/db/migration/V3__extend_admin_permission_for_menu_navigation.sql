-- =============================================================================
-- website-services | Flyway V3 | admin_permission 菜单导航字段扩展（5E）
-- 说明：不新建 admin_menu；MENU 行仍存 admin_permission，resource_type = 'MENU'
-- =============================================================================

ALTER TABLE admin_permission
    ADD COLUMN parent_id   BIGINT NULL,
    ADD COLUMN sort_order  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN route_path  VARCHAR(255) NULL,
    ADD COLUMN icon        VARCHAR(128) NULL;

COMMENT ON COLUMN admin_permission.parent_id IS
    'MENU 父节点 FK（本表 id）；根菜单为 NULL。非 MENU 行保持 NULL';
COMMENT ON COLUMN admin_permission.sort_order IS
    'MENU 同级排序（升序）；非 MENU 行使用 0';
COMMENT ON COLUMN admin_permission.route_path IS
    'MENU 前端路由 path，映射 /api/admin/auth/me data.menus[].path';
COMMENT ON COLUMN admin_permission.icon IS
    'MENU 侧栏图标标识，可空';

ALTER TABLE admin_permission
    ADD CONSTRAINT fk_admin_permission_parent
        FOREIGN KEY (parent_id) REFERENCES admin_permission (id) ON DELETE RESTRICT;

CREATE INDEX idx_admin_permission_parent_active
    ON admin_permission (parent_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_admin_permission_menu_tree_active
    ON admin_permission (resource_type, parent_id, sort_order)
    WHERE deleted = FALSE AND resource_type = 'MENU';
