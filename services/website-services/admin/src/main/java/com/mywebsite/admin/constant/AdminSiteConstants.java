package com.mywebsite.admin.constant;

import java.util.Set;

/** Site Core 生产常量（状态 / 站点类型闭集；与 API 契约对齐）。 */
public final class AdminSiteConstants {

    public static final String STATUS_ACTIVE = "ACTIVE";

    public static final String STATUS_DISABLED = "DISABLED";

    public static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_DISABLED);

    public static final String SITE_TYPE_CAR_REPAIR = "CAR_REPAIR";

    public static final String SITE_TYPE_USED_CAR = "USED_CAR";

    public static final String SITE_TYPE_CAKE = "CAKE";

    public static final String SITE_TYPE_SALON = "SALON";

    public static final String SITE_TYPE_CLINIC = "CLINIC";

    public static final String SITE_TYPE_GYOSEISHOSHI = "GYOSEISHOSHI";

    public static final Set<String> ALLOWED_SITE_TYPES = Set.of(
            SITE_TYPE_CAR_REPAIR,
            SITE_TYPE_USED_CAR,
            SITE_TYPE_CAKE,
            SITE_TYPE_SALON,
            SITE_TYPE_CLINIC,
            SITE_TYPE_GYOSEISHOSHI);

    /** siteCode：小写字母开头，后续可为小写字母/数字/_/-，总长 2–64。 */
    public static final String SITE_CODE_PATTERN = "^[a-z][a-z0-9_-]{1,63}$";

    public static final int DEFAULT_PAGE_NUM = 1;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    private AdminSiteConstants() {}
}
