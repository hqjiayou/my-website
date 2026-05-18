package com.mywebsite.infra.security.oauth2;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Admin / Site 等 Servlet Resource Server 共用：识别「Bearer 令牌无效」与生成 {@code WWW-Authenticate} 挑战串。
 */
public final class OauthResourceServerUnauthorizedSupport {

    private static final String DEFAULT_INVALID_TOKEN = "Bearer error=\"invalid_token\"";

    private OauthResourceServerUnauthorizedSupport() {}

    /**
     * OAuth2 Resource Server 在对 JWT 验签/解析失败常见会抛出 {@link OAuth2AuthenticationException} 或 {@link JwtException}，
     * 与「完全未携带 Bearer」等场景区分。
     */
    public static boolean isBearerTokenInvalidCause(AuthenticationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof JwtException) {
                return true;
            }
            if (t instanceof OAuth2AuthenticationException oae) {
                String code = safeErrorCode(oae);
                if (code == null) {
                    continue;
                }
                if ("invalid_token".equalsIgnoreCase(code)) {
                    return true;
                }
                if ("invalid_signature".equalsIgnoreCase(code)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void addWwwAuthenticateIfBearerInvalid(
            jakarta.servlet.http.HttpServletResponse response, AuthenticationException ex) {
        if (!isBearerTokenInvalidCause(ex)) {
            return;
        }
        String challenge = buildWwwAuthenticateChallenge(ex);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
    }

    private static String safeErrorCode(OAuth2AuthenticationException oae) {
        OAuth2Error err = oae.getError();
        return err != null ? err.getErrorCode() : null;
    }

    private static String buildWwwAuthenticateChallenge(AuthenticationException root) {
        for (Throwable t = root; t != null; t = t.getCause()) {
            if (t instanceof OAuth2AuthenticationException oae && oae.getError() != null) {
                OAuth2Error error = oae.getError();
                String code = error.getErrorCode() != null ? error.getErrorCode() : "invalid_token";
                String desc = error.getDescription();
                return formatBearerChallenge(code, desc);
            }
        }
        return DEFAULT_INVALID_TOKEN;
    }

    static String formatBearerChallenge(String code, String description) {
        String normalizedCode = escHeaderParam(code.isBlank() ? "invalid_token" : code);
        StringBuilder sb = new StringBuilder("Bearer error=\"").append(normalizedCode).append("\"");
        if (description != null && !description.isBlank()) {
            sb.append(", error_description=\"").append(escHeaderParam(description)).append("\"");
        }
        return sb.toString();
    }

    /** RFC6750 WWW-Authenticate 引号字段转义最小集。 */
    private static String escHeaderParam(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
