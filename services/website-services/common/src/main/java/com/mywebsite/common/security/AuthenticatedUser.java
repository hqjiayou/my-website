package com.mywebsite.common.security;

import java.util.Set;

public record AuthenticatedUser(
        TokenAudience audience, String subject, String usernameOrNull, Set<String> authorities) {}
