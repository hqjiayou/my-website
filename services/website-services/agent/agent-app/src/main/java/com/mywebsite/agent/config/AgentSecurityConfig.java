package com.mywebsite.agent.config;

import com.mywebsite.agent.security.AuthContextSynchronizingAgentFilter;
import com.mywebsite.agent.security.AgentAccessDeniedHandler;
import com.mywebsite.agent.security.AgentAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class AgentSecurityConfig {

    @Bean
    SecurityFilterChain agentSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder agentJwtDecoder,
            Converter<Jwt, JwtAuthenticationToken> agentJwtAuthenticationConverter,
            AgentAuthenticationEntryPoint authenticationEntryPoint,
            AgentAccessDeniedHandler accessDeniedHandler,
            AuthContextSynchronizingAgentFilter authContextSynchronizingAgentFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/api/agent/ping")
                        .permitAll()
                        // 公开内容只读：仅 GET。其他方法仍要认证。
                        .requestMatchers(HttpMethod.GET, "/api/agent/public/**")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        // internal API 由 InternalServiceTokenFilter 鉴权（共享 internal token），不走 JWT
                        .requestMatchers("/internal/**")
                        .permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.decoder(agentJwtDecoder)
                                .jwtAuthenticationConverter(agentJwtAuthenticationConverter)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .anonymous(Customizer.withDefaults())
                .addFilterAfter(authContextSynchronizingAgentFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    Converter<Jwt, JwtAuthenticationToken> agentJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        return jwt -> new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt), jwt.getSubject());
    }
}
