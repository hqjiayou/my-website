package com.mywebsite.site.service.impl;

import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.dto.SiteAiChatResponseDto;
import com.mywebsite.site.service.SiteAiRateLimitService;
import com.mywebsite.site.service.SiteAiService;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SiteAiServiceImpl implements SiteAiService {

    private final AgentClient agentClient;
    private final SiteAiRateLimitService rateLimitService;

    public SiteAiServiceImpl(AgentClient agentClient, SiteAiRateLimitService rateLimitService) {
        this.agentClient = agentClient;
        this.rateLimitService = rateLimitService;
    }

    @Override
    public SiteAiChatResponseDto chat(long siteId, SiteAiChatRequestDto request) {
        AuthenticatedUser user =
                AuthContext.currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.siteIdOrNull() == null || !Objects.equals(user.siteIdOrNull(), siteId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (!rateLimitService.tryAcquire(siteId, user.subject())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        AgentChatResponse response = agentClient.chat(new AgentChatRequest(
                String.valueOf(siteId),
                request.message().trim(),
                null,
                request.conversationId(),
                null));
        return new SiteAiChatResponseDto(response.reply(), response.conversationId(), response.providerStatus());
    }
}
