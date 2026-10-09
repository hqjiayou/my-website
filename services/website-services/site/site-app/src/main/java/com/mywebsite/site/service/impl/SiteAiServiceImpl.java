package com.mywebsite.site.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
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
    private static final Set<String> CLIENT_ERROR_CODES =
            Set.of("EXTERNAL_DEPENDENCY_BLOCKED", "MCP_UNAVAILABLE", "AGENT_BUDGET_EXCEEDED");

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
        conversationService.recordExchange(
                preparation.conversation(), message, response.reply(), response.todos(), response.runMeta());

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
                preparation.history().isEmpty() ? null : preparation.history(),
                // 终端用户不能指定 skills（防前台激活任意指令模块）；只用 agent-services 平台默认
                null,
                preparation.todos().isEmpty() ? null : preparation.todos());
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
        List<AgentTodoItem> doneTodos = new ArrayList<>();
        AtomicReference<AgentRunMeta> doneMeta = new AtomicReference<>(AgentRunMeta.EMPTY);
        try {
            agentClient.chatStream(agentRequest, (event, dataJson) -> {
                JsonNode data = readJson(dataJson);
                if ("DONE".equals(event)) {
                    doneReply.append(extractReply(data));
                    doneTodos.addAll(extractTodos(data));
                    doneMeta.set(extractRunMeta(data));
                }
                String clientData = toClientEventData(event, data, dataJson);
                if (clientData == null) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event().name(event).data(clientData, MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    // 客户端断连：中断转发即可
                    throw new ClientDisconnectedException(ex);
                }
            });
            if (doneReply.length() > 0) {
                conversationService.recordExchange(
                        preparation.conversation(), userMessage, doneReply.toString(), doneTodos, doneMeta.get());
                sendEventQuietly(
                        emitter,
                        "CONVERSATION",
                        "{\"conversationId\":\"" + preparation.conversation().getId() + "\"}");
            }
            emitter.complete();
        } catch (ClientDisconnectedException ex) {
            emitter.completeWithError(ex.getCause());
        } catch (BusinessException ex) {
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"" + toClientErrorCode(ex) + "\"}");
            emitter.complete();
        } catch (RuntimeException ex) {
            log.warn("site ai stream forwarding failed", ex);
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"SYSTEM_ERROR\"}");
            emitter.complete();
        }
    }

    /** Java 侧 BusinessException → 站点 SSE errorCode（与流内 ERROR 白名单一致）。 */
    private static String toClientErrorCode(BusinessException ex) {
        int code = ex.getCode();
        if (code == ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()) {
            return "EXTERNAL_DEPENDENCY_BLOCKED";
        }
        if (code == ErrorCode.AGENT_BUDGET_EXCEEDED.getCode()) {
            return "AGENT_BUDGET_EXCEEDED";
        }
        return "SYSTEM_ERROR";
    }

    /**
     * 终端用户只看到回复文本与错误码（§22.8）：工具轨迹 / 用量 / skills / todos / 原始错误信息
     * 仅供后台观测（admin 预览原样透传）。返回 null = 不下发；未知事件默认不下发，防 Python 新增字段自动外泄。
     */
    private String toClientEventData(String event, JsonNode data, String rawJson) {
        return switch (event) {
            case "TEXT_DELTA" -> rawJson;
            case "START" -> "{}";
            case "DONE" -> {
                ObjectNode done = objectMapper.createObjectNode();
                done.put("reply", extractReply(data));
                done.put("providerStatus", data.path("providerStatus").asText("LIVE"));
                yield done.toString();
            }
            case "ERROR" -> {
                String code = data.path("errorCode").asText("");
                ObjectNode error = objectMapper.createObjectNode();
                error.put("errorCode", CLIENT_ERROR_CODES.contains(code) ? code : "SYSTEM_ERROR");
                yield error.toString();
            }
            default -> null;
        };
    }

    private JsonNode readJson(String dataJson) {
        try {
            return objectMapper.readTree(dataJson);
        } catch (IOException ex) {
            return objectMapper.missingNode();
        }
    }

    private static String extractReply(JsonNode done) {
        JsonNode reply = done.get("reply");
        return reply == null || reply.isNull() ? "" : reply.asText();
    }

    private List<AgentTodoItem> extractTodos(JsonNode done) {
        JsonNode todos = done.get("todos");
        if (todos == null || !todos.isArray()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.treeToValue(todos, AgentTodoItem[].class));
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("site ai stream DONE carried malformed todos, ignored", ex);
            return List.of();
        }
    }

    private AgentRunMeta extractRunMeta(JsonNode done) {
        if (!done.isObject()) {
            return AgentRunMeta.EMPTY;
        }
        try {
            return objectMapper
                    .readerFor(AgentRunMeta.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(done);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("site ai stream DONE carried malformed run metadata, ignored", ex);
            return AgentRunMeta.EMPTY;
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
