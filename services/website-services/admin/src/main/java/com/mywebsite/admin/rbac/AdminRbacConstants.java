package com.mywebsite.admin.rbac;

/** Admin RBAC 稳定常量（与种子数据、权限码规范对齐）。 */
public final class AdminRbacConstants {

    public static final String ROLE_SUPER_ADMIN = "super_admin";

    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 仅 test scope {@code RbacTestController} 使用；不进入生产 Flyway 种子。 */
    public static final String PERMISSION_RBAC_CHECK = "admin:rbac:check";

    private AdminRbacConstants() {}
}
