package com.mywebsite.admin.constant;

import java.util.Set;

/** Admin 用户管理常量（状态、密码策略；与种子 / 校验对齐）。 */
public final class AdminUserConstants {

    public static final String STATUS_ACTIVE = "ACTIVE";

    public static final String STATUS_DISABLED = "DISABLED";

    public static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_DISABLED);

    public static final int PASSWORD_MIN_LENGTH = 8;

    public static final int PASSWORD_MAX_LENGTH = 128;

    private AdminUserConstants() {}
}
