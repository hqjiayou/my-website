package com.mywebsite.admin.constant;

public final class AdminSiteMessageKeys {

    public static final String SITE_NOT_FOUND = "admin.site.not-found";
    public static final String SITE_CODE_DUPLICATE = "admin.site.code.duplicate";
    public static final String SITE_CODE_INVALID = "admin.site.code.invalid";
    public static final String SITE_TYPE_INVALID = "admin.site.type.invalid";
    public static final String SITE_STATUS_INVALID = "admin.site.status.invalid";
    public static final String SITE_VERSION_CONFLICT = "admin.site.version.conflict";
    /** templateId 与 appearance 必须同有或同无。 */
    public static final String SITE_TEMPLATE_APPEARANCE_PAIR =
            "admin.site.template-appearance.pair";
    public static final String SITE_TEMPLATE_NOT_ENABLED = "admin.site.template.not-enabled";
    public static final String SITE_TEMPLATE_SITE_TYPE_MISMATCH =
            "admin.site.template.site-type-mismatch";

    private AdminSiteMessageKeys() {}
}
