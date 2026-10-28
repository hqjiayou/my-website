package com.mywebsite.auth.dto;

import java.time.Instant;

public record AgentRefreshCookieDto(
        AgentRefreshResponseDto refreshResponse, String refreshPlain, Instant refreshExpiresAt) {}
