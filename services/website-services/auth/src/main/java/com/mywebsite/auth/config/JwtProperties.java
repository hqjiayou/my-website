package com.mywebsite.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String issuer = "my-website";

    /** JWT {@code aud} claim for Admin access tokens; defaults to ADMIN audience name. */
    private String audience = "ADMIN";

    /**
     * HS256 signing secret (UTF-8 bytes); inject via {@code SECURITY_JWT_SECRET}. Never commit real secrets.
     */
    private String secret = "";

    private Duration accessTokenTtl = Duration.ofMinutes(15);

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

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
