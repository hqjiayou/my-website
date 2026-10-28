package com.mywebsite.agent.service;

public interface AgentAiRateLimitService {

    /** @return true 表示允许通过；false 表示超限 */
    boolean tryAcquire(long agentId, String subject);
}
