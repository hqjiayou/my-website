package com.mywebsite.admin.config;

import com.mywebsite.infra.jwt.JwtSecretPresentCondition;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class AdminResourceServerJwtConfiguration {

    /** HS256 资源服务验签（4F 过渡态与 Auth 共用 secret；4G 迁移 RS256 + JWKS 后删除 secret 依赖）。 */
    @Bean
    @Conditional(JwtSecretPresentCondition.class)
    JwtDecoder adminResourceServerJwtDecoder(@Value("${security.jwt.secret}") String secretRaw) {
        if (secretRaw == null || secretRaw.isBlank()) {
            throw new IllegalStateException("security.jwt.secret required for HS256 resource server");
        }
        byte[] keyBytes = secretRaw.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 UTF-8 bytes for HS256");
        }
        return NimbusJwtDecoder.withSecretKey(new SecretKeySpec(keyBytes, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
