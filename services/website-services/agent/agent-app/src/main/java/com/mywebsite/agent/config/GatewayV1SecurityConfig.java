package com.mywebsite.agent.config;

import com.mywebsite.agent.security.GatewayAccessKeyAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 对外 API 网关 {@code /v1/**} 独立安全链：不走站点 JWT（oauth2ResourceServer 只在
 * {@link AgentSecurityConfig} 的主链里配置），鉴权完全交给 {@link GatewayAccessKeyAuthFilter}。
 *
 * <p>必须用独立的 {@code securityMatcher} + 更靠前的 {@code @Order} 从主链里挖出 {@code /v1/**}：
 * 否则 {@code Authorization: Bearer mwgw_...} 会先被主链的 oauth2ResourceServer 当成站点 JWT
 * 去解码，解不出来直接 401（走站点错误形状），永远到不了这里。
 */
@Configuration
public class GatewayV1SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain gatewayV1SecurityFilterChain(
            HttpSecurity http, GatewayAccessKeyAuthFilter accessKeyAuthFilter) throws Exception {
        http.securityMatcher("/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 授权判定交给 GatewayAccessKeyAuthFilter（未通过直接 401 短路，不落到这里的 permitAll）。
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(accessKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
