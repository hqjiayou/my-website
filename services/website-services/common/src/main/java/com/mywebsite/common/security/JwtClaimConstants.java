package com.mywebsite.common.security;

/** Registered JWT claim names used across issuance and parsing. */
public final class JwtClaimConstants {

    /** Access JWT：{@link com.mywebsite.common.security.SubjectType} 名称（ADMIN / AGENT）。 */
    public static final String SUBJECT_TYPE = "subject_type";

    /**
     * AGENT Access JWT：所属站点主键（数值）。ADMIN 令牌不得写入此声明。
     */
    public static final String SITE_ID = "site_id";

    /** @deprecated 历史字段；新签发令牌不再写入，解析侧已不再依赖。 */
    @Deprecated
    public static final String TOKEN_TYPE = "token_type";

    /** @deprecated 新签发令牌使用 {@code sub}（{@link SubjectCodes}）承载主体标识。 */
    @Deprecated
    public static final String ADMIN_USER_ID = "admin_user_id";

    /** @deprecated Refresh 会话不在 Access JWT 暴露。 */
    @Deprecated
    public static final String SESSION_ID = "session_id";

    /** @deprecated 展示信息改由各业务端 {@code /auth/me} 提供。 */
    @Deprecated
    public static final String USERNAME = "username";

    private JwtClaimConstants() {}
}
