package com.mywebsite.site.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.dto.SiteAiChatResponseDto;
import com.mywebsite.site.service.SiteAiConversationService;
import com.mywebsite.site.service.SiteAiConversationService.ChatPreparation;
import com.mywebsite.site.service.SiteAiRateLimitService;
import com.mywebsite.site.service.SiteAiService;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SiteAiServiceImpl implements SiteAiService {

    private static final Logger log = LoggerFactory.getLogger(SiteAiServiceImpl.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L;

    private final AgentClient agentClient;
    private final SiteAiRateLimitService rateLimitService;
    private final SiteAiConversationService conversationService;
    private final ExecutorService siteAiStreamExecutor;
    private final ObjectMapper objectMapper;

    public SiteAiServiceImpl(
            AgentClient agentClient,
            SiteAiRateLimitService rateLimitService,
            SiteAiConversationService conversationService,
            ExecutorService siteAiStreamExecutor,
            ObjectMapper objectMapper) {
        this.agentClient = agentClient;
        this.rateLimitService = rateLimitService;
        this.conversationService = conversationService;
        this.siteAiStreamExecutor = siteAiStreamExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public SiteAiChatResponseDto chat(long siteId, SiteAiChatRequestDto request) {
        AuthenticatedUser user = validateChatAccess(siteId, request);
        long siteUserId = requireSiteUserId(user);
        String message = request.message().trim();

        ChatPreparation preparation =
                conversationService.prepare(siteId, siteUserId, request.conversationId());
        AgentChatResponse response = agentClient.chat(buildAgentRequest(siteId, message, preparation));
        conversationService.recordExchange(preparation.conversation(), message, response.reply());

        return new SiteAiChatResponseDto(
                response.reply(),
                String.valueOf(preparation.conversation().getId()),
                response.providerStatus());
    }

    @Override
    public SseEmitter chatStream(long siteId, SiteAiChatRequestDto request) {
        AuthenticatedUser user = validateChatAccess(siteId, request);
        long siteUserId = requireSiteUserId(user);
        String message = request.message().trim();

        ChatPreparation preparation =
                conversationService.prepare(siteId, siteUserId, request.conversationId());
        AgentChatRequest agentRequest = buildAgentRequest(siteId, message, preparation);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        siteAiStreamExecutor.execute(() -> forwardStream(preparation, message, agentRequest, emitter));
        return emitter;
    }

    private AgentChatRequest buildAgentRequest(long siteId, String message, ChatPreparation preparation) {
        Long conversationId = preparation.conversation().getId();
        return new AgentChatRequest(
                String.valueOf(siteId),
                message,
                null,
                conversationId == null ? null : String.valueOf(conversationId),
                preparation.promptProfile(),
                preparation.history().isEmpty() ? null : preparation.history());
    }

    private AuthenticatedUser validateChatAccess(long siteId, SiteAiChatRequestDto request) {
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
        return user;
    }

    private static long requireSiteUserId(AuthenticatedUser user) {
        return SubjectCodes.parseSiteUserId(user.subject())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void forwardStream(
            ChatPreparation preparation, String userMessage, AgentChatRequest agentRequest, SseEmitter emitter) {
        StringBuilder doneReply = new StringBuilder();
        try {
            agentClient.chatStream(agentRequest, (event, dataJson) -> {
                if ("DONE".equals(event)) {
                    doneReply.append(extractReply(dataJson));
                }
                try {
                    emitter.send(SseEmitter.event().name(event).data(dataJson, MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    // 客户端断连：中断转发即可
                    throw new ClientDisconnectedException(ex);
                }
            });
            if (doneReply.length() > 0) {
                conversationService.recordExchange(preparation.conversation(), userMessage, doneReply.toString());
                sendEventQuietly(
                        emitter,
                        "CONVERSATION",
                        "{\"conversationId\":\"" + preparation.conversation().getId() + "\"}");
            }
            emitter.complete();
        } catch (ClientDisconnectedException ex) {
            emitter.completeWithError(ex.getCause());
        } catch (BusinessException ex) {
            String errorCode = ex.getCode() == ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()
                    ? "EXTERNAL_DEPENDENCY_BLOCKED"
                    : "SYSTEM_ERROR";
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"" + errorCode + "\"}");
            emitter.complete();
        } catch (RuntimeException ex) {
            log.warn("site ai stream forwarding failed", ex);
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"SYSTEM_ERROR\"}");
            emitter.complete();
        }
    }

    private String extractReply(String doneDataJson) {
        try {
            JsonNode node = objectMapper.readTree(doneDataJson);
            JsonNode reply = node.get("reply");
            return reply == null || reply.isNull() ? "" : reply.asText();
        } catch (IOException ex) {
            return "";
        }
    }

    private static void sendEventQuietly(SseEmitter emitter, String event, String dataJson) {
        try {
            emitter.send(SseEmitter.event().name(event).data(dataJson, MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
            // 客户端已断开
        }
    }

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
