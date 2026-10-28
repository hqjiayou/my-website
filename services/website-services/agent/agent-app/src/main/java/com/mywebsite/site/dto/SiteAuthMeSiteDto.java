package com.mywebsite.site.dto;

/** GET /api/site/auth/me 载荷中的当前站点。 */
public record SiteAuthMeSiteDto(long siteId, String siteCode, String siteName) {}
