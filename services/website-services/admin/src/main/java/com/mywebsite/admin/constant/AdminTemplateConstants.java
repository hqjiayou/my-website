package com.mywebsite.admin.constant;

import java.util.Set;

/** Template 目录生产常量（状态闭集；siteType 复用 {@link AdminSiteConstants#ALLOWED_SITE_TYPES}）。 */
public final class AdminTemplateConstants {

    public static final String STATUS_ENABLED = "ENABLED";

    public static final String STATUS_DRAFT = "DRAFT";

    public static final String STATUS_DISABLED = "DISABLED";

    public static final Set<String> ALLOWED_STATUSES =
            Set.of(STATUS_ENABLED, STATUS_DRAFT, STATUS_DISABLED);

    public static final int DEFAULT_PAGE_NUM = 1;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    private AdminTemplateConstants() {}
}
