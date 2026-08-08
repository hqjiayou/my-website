package com.mywebsite.auth.dto;

import java.time.Instant;

public record SiteRefreshCookieDto(
        SiteRefreshResponseDto refreshResponse, String refreshPlain, Instant refreshExpiresAt) {}
