package com.mywebsite.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentItemDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentSummaryDto;
import com.mywebsite.agent.entity.AgentContentEntity;
import com.mywebsite.agent.entity.AgentEntity;
import com.mywebsite.agent.mapper.AgentContentMapper;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.service.AgentInternalService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentInternalServiceImpl implements AgentInternalService {

    private static final int MAX_CONTENT_LIMIT = 50;

    private final AgentMapper agentMapper;
    private final AgentContentMapper agentContentMapper;

    public AgentInternalServiceImpl(AgentMapper agentMapper, AgentContentMapper agentContentMapper) {
        this.agentMapper = agentMapper;
        this.agentContentMapper = agentContentMapper;
    }

    @Override
    public AgentSummaryDto getAgentSummary(long agentId) {
        AgentEntity agent = requireSite(agentId);
        return new AgentSummaryDto(agent.getId(), agent.getAgentName(), agent.getAgentType(), agent.getDescription());
    }

    @Override
    public AgentContentListDto listPublishedContents(long agentId, String type, int limit) {
        requireSite(agentId);
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_CONTENT_LIMIT);
        LambdaQueryWrapper<AgentContentEntity> wrapper = new LambdaQueryWrapper<AgentContentEntity>()
                .eq(AgentContentEntity::getAgentId, agentId)
                .eq(AgentContentEntity::getStatus, AgentContentEntity.STATUS_PUBLISHED)
                .orderByAsc(AgentContentEntity::getSortOrder)
                .orderByDesc(AgentContentEntity::getId)
                .last("LIMIT " + effectiveLimit);
        if (StringUtils.hasText(type)) {
            wrapper.eq(AgentContentEntity::getType, type.trim());
        }
        return new AgentContentListDto(agentContentMapper.selectList(wrapper).stream()
                .map(c -> new AgentContentItemDto(c.getId(), c.getType(), c.getTitle(), c.getBody()))
                .toList());
    }

    private AgentEntity requireSite(long agentId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return agent;
    }
}
