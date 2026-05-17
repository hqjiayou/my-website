package com.mywebsite.auth.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Access JWT 语义配置（issuer / audience / TTL），前缀 {@code security.jwt}。 */
@ConfigurationProperties(prefix = "security.jwt")
public class AuthJwtProperties {

    private String issuer = "my-website";

    /** JWT {@code aud} claim for Admin access tokens; defaults to ADMIN audience name. */
    private String audience = "ADMIN";

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

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }
}
