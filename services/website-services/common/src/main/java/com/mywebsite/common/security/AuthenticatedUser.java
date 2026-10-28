package com.mywebsite.common.security;

import java.util.Set;

/**
 * 请求作用域身份快照。
 *
 * <p>{@code agentIdOrNull} 仅 AGENT 受众有值；ADMIN 恒为 {@code null}。
 */
public record AuthenticatedUser(
        TokenAudience audience,
        String subject,
        String usernameOrNull,
        Long agentIdOrNull,
        Set<String> authorities) {}
