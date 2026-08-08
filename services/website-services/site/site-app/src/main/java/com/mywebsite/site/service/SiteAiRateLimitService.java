package com.mywebsite.site.service;

public interface SiteAiRateLimitService {

    /** @return true 表示允许通过；false 表示超限 */
    boolean tryAcquire(long siteId, String subject);
}
