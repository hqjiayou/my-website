package com.mywebsite.common.security;

/** Registered JWT claim names used across issuance and parsing. */
public final class JwtClaimConstants {

    public static final String TOKEN_TYPE = "token_type";

    public static final String ADMIN_USER_ID = "admin_user_id";

    public static final String SESSION_ID = "session_id";

    public static final String USERNAME = "username";

    private JwtClaimConstants() {}
}
