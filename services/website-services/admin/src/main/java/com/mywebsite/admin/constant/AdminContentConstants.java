package com.mywebsite.admin.constant;

import java.util.Set;

/** Content 域常量：type / status 闭集与分页。 */
public final class AdminContentConstants {

    public static final String TYPE_HERO = "hero";
    public static final String TYPE_SERVICE = "service";
    public static final String TYPE_CASE = "case";
    public static final String TYPE_FAQ = "faq";
    public static final String TYPE_NEWS = "news";
    public static final String TYPE_ABOUT = "about";
    public static final String TYPE_CTA = "cta";

    public static final Set<String> ALLOWED_TYPES = Set.of(
            TYPE_HERO, TYPE_SERVICE, TYPE_CASE, TYPE_FAQ, TYPE_NEWS, TYPE_ABOUT, TYPE_CTA);

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_HIDDEN = "HIDDEN";

    public static final Set<String> ALLOWED_STATUSES =
            Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_HIDDEN);

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    public static final int DEFAULT_SORT_ORDER = 0;

    private AdminContentConstants() {}
}
