package com.mywebsite.auth.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Access JWT iss / aud（前缀 {@code security.jwt}）；TTL 见 {@link com.mywebsite.common.security.config.TokenProperties}。 */
@ConfigurationProperties(prefix = "security.jwt")
public class AuthJwtProperties {

    private String issuer = "my-website";

    /** JWT {@code aud} — Admin Access Token */
    private String audience = "ADMIN";

    /** JWT {@code aud} — Site Access Token */
    private String siteAudience = "SITE";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getSiteAudience() {
        return siteAudience;
    }

    public void setSiteAudience(String siteAudience) {
        this.siteAudience = siteAudience;
    }
}
