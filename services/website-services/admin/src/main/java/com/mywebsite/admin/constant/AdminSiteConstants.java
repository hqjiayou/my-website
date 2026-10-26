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

    /** 旧行业闭集：仅 site_template 目录仍按此对齐（历史模板数据）；站点本身已改用途标签，不再受此约束。 */
    public static final Set<String> ALLOWED_SITE_TYPES = Set.of(
            SITE_TYPE_CAR_REPAIR,
            SITE_TYPE_USED_CAR,
            SITE_TYPE_CAKE,
            SITE_TYPE_SALON,
            SITE_TYPE_CLINIC,
            SITE_TYPE_GYOSEISHOSHI);

    /** 站点用途标签预设（前端下拉与此对齐；预设之外允许自订标签，见 SITE_TAG_PATTERN）。 */
    public static final Set<String> SITE_TAG_PRESETS = Set.of(
            "PERSONAL", "PORTFOLIO", "BLOG", "KNOWLEDGE", "TOOL", "OTHER");

    /** 用途标签：1–32 字符，字母/数字开头，可含字母、数字、底线、连字符与空格。 */
    public static final String SITE_TAG_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_\\- ]{0,31}$";

    /** siteCode：小写字母开头，后续可为小写字母/数字/_/-，总长 2–64。 */
    public static final String SITE_CODE_PATTERN = "^[a-z][a-z0-9_-]{1,63}$";

    public static final int DEFAULT_PAGE_NUM = 1;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    private AdminSiteConstants() {}
}
