package com.mywebsite.agent.service.impl;

import com.mywebsite.agent.dto.AgentUsageRecord;
import com.mywebsite.agent.entity.AgentAiUsageEntity;
import com.mywebsite.agent.mapper.AgentAiUsageMapper;
import com.mywebsite.agent.service.AgentUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentUsageServiceImpl implements AgentUsageService {

    private static final Logger log = LoggerFactory.getLogger(AgentUsageServiceImpl.class);

    private final AgentAiUsageMapper usageMapper;

    public AgentUsageServiceImpl(AgentAiUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    @Override
    public void recordUsage(AgentUsageRecord usage) {
        if (usage == null || !StringUtils.hasText(usage.source()) || !StringUtils.hasText(usage.status())) {
            return;
        }
        try {
            AgentAiUsageEntity entity = new AgentAiUsageEntity();
            entity.setAgentId(usage.agentId());
            entity.setEntryId(usage.entryId());
            entity.setAlias(trimToNull(usage.alias()));
            entity.setMode(trimToNull(usage.mode()));
            entity.setSource(usage.source().trim());
            entity.setPromptTokens(usage.promptTokens());
            entity.setCompletionTokens(usage.completionTokens());
            entity.setLatencyMs(usage.latencyMs());
            entity.setStatus(usage.status().trim());
            entity.setGatewayAccessKeyId(usage.gatewayAccessKeyId());
            usageMapper.insert(entity);
        } catch (RuntimeException ex) {
            // 记账是观测面：写不进去只丢这一行用量，绝不拖垮聊天/网关主流程。
            log.warn("ai usage record failed source={} agentId={}", usage.source(), usage.agentId(), ex);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
