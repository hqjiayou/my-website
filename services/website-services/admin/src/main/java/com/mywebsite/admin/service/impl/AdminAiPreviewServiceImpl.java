package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewChatRequest;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewHistoryMessage;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewSkillDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewTodoItem;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiMcpReadyDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiRuntimeDto;
import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.entity.AdminAiPromptProfileEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminAiPromptProfileMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.admin.service.AdminAiPreviewService;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.dto.AgentRuntimeStatus;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.infra.service.LocalizedMessageService;
import com.mywebsite.common.exception.BusinessException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AdminAiPreviewServiceImpl implements AdminAiPreviewService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiPreviewServiceImpl.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L;
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ASSISTANT");
    private static final int KNOWLEDGE_NEEDLE_CHARS = 80;
    private static final int KNOWLEDGE_SNIPPET_CHARS = 800;
    private static final int KNOWLEDGE_SNIPPET_LIMIT = 4;

    private final AgentClient agentClient;
    private final AgentProperties agentProperties;
    private final AdminAgentMapper adminAgentMapper;
    private final AdminAiPromptProfileMapper promptProfileMapper;
    private final AdminAiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final AdminAiModelEntryService modelEntryService;
    private final LocalizedMessageService localizedMessageService;
    private final ExecutorService adminAiStreamExecutor;

    public AdminAiPreviewServiceImpl(
            AgentClient agentClient,
            AgentProperties agentProperties,
            AdminAgentMapper adminAgentMapper,
            AdminAiPromptProfileMapper promptProfileMapper,
            AdminAiKnowledgeDocumentMapper knowledgeDocumentMapper,
            AdminAiModelEntryService modelEntryService,
            LocalizedMessageService localizedMessageService,
            ExecutorService adminAiStreamExecutor) {
        this.agentClient = agentClient;
        this.agentProperties = agentProperties;
        this.adminAgentMapper = adminAgentMapper;
        this.promptProfileMapper = promptProfileMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.modelEntryService = modelEntryService;
        this.localizedMessageService = localizedMessageService;
        this.adminAiStreamExecutor = adminAiStreamExecutor;
    }

    @Override
    public SseEmitter previewStream(long agentId, AdminAiPreviewChatRequest request) {
        requireSite(agentId);
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String mode = previewMode(agentId, request.mode());
        // 试聊只打该模式的主用入口。同模式其他入口和进程 AI_FALLBACK_MODELS 不参与，
        // 失败就停在用户保存的那条上，不再换成另一种接入的回复。
        List<AgentModelSelection> chain = modelEntryService.selectionChain(agentId, mode);
        AgentModelSelection embedding = modelEntryService.embeddingSelection(agentId);
        AgentChatRequest agentRequest = new AgentChatRequest(
                String.valueOf(agentId),
                request.message().trim(),
                localizedMessageService.currentLanguageTag(),
                null,
                loadPromptProfile(agentId),
                toHistory(request.history()),
                normalizeSkills(request.skills()),
                toTodos(request.todos()),
                chain.isEmpty() ? null : chain.get(0),
                callbackBaseUrl(),
                knowledgeSnippets(agentId, request.message().trim()),
                null,
                null,
                null,
                null,
                null,
                embedding == null ? null : embedding.embeddingSpec(),
                embedding == null ? null : embedding.apiKey(),
                embedding == null ? null : embedding.baseUrl());
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        adminAiStreamExecutor.execute(() -> forwardStream(agentRequest, emitter));
        return emitter;
    }

    @Override
    public List<AdminAiPreviewSkillDto> listSkills(long agentId) {
        requireSite(agentId);
        return agentClient.listSkills().stream()
                .map(skill -> new AdminAiPreviewSkillDto(skill.id(), skill.description(), skill.version()))
                .toList();
    }

    @Override
    public AdminAiRuntimeDto runtimeStatus(long agentId) {
        requireSite(agentId);
        try {
            return new AdminAiRuntimeDto(true, null, overlayEntry(agentId, agentClient.runtimeStatus()));
        } catch (BusinessException ex) {
            return new AdminAiRuntimeDto(false, toClientErrorCode(ex), null);
        }
    }

    @Override
    public AdminAiMcpReadyDto mcpReady(long agentId) {
        requireSite(agentId);
        try {
            return new AdminAiMcpReadyDto(true, null, agentClient.mcpReady());
        } catch (BusinessException ex) {
            return new AdminAiMcpReadyDto(false, toClientErrorCode(ex), null);
        }
    }

    private void requireSite(long agentId) {
        if (adminAgentMapper.selectById(agentId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
    }

    private static List<AgentTodoItem> toTodos(List<AdminAiPreviewTodoItem> todos) {
        if (todos == null || todos.isEmpty()) {
            return null;
        }
        List<AgentTodoItem> mapped = new ArrayList<>(todos.size());
        for (AdminAiPreviewTodoItem item : todos) {
            if (item == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            mapped.add(new AgentTodoItem(item.id().trim(), item.title().trim(), item.status()));
        }
        return mapped;
    }

    /**
     * 面板上的模型名是这次对话会用的入口，不是进程环境变量。
     * 状态用 CONFIGURED：库里有入口，并不表示刚才探测过供应商。
     * 检索状态保持 Python 的原话，不根据错误文案改成可用。
     * 备援列是同一模式里主用之后的入口。试聊本身不走这些备援；用户端同模式对话才会接着试。
     * 有 CLI 默认入口时面板跟试聊的缺省模式一样先看 CLI。
     */
    private AgentRuntimeStatus overlayEntry(long agentId, AgentRuntimeStatus status) {
        String mode = modelEntryService.selectionChain(agentId, "CLI").isEmpty() ? "KEY" : "CLI";
        List<AgentModelSelection> chain = modelEntryService.selectionChain(agentId, mode);
        if (chain.isEmpty() || !StringUtils.hasText(chain.get(0).spec())) {
            return status;
        }
        return new AgentRuntimeStatus(
                chain.get(0).spec(),
                "CONFIGURED",
                chain.subList(1, chain.size()).stream().map(AgentModelSelection::spec).toList(),
                status.mcpStatus(),
                status.mcpServers(),
                status.limits(),
                status.retrievalStatus(),
                status.retrievalBlockedReason(),
                status.harness());
    }

    private String callbackBaseUrl() {
        String configured = agentProperties.getBusinessApiBaseUrl();
        return StringUtils.hasText(configured) ? configured.trim() : null;
    }

    private List<String> knowledgeSnippets(long agentId, String query) {
        String trimmed = query.trim();
        if (trimmed.length() > KNOWLEDGE_NEEDLE_CHARS) {
            trimmed = trimmed.substring(0, KNOWLEDGE_NEEDLE_CHARS);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        String needle = trimmed;
        List<String> snippets = knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .eq(AdminAiKnowledgeDocumentEntity::getAgentId, agentId)
                        .eq(AdminAiKnowledgeDocumentEntity::getStatus, AdminAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                        .and(wrapper -> wrapper
                                .like(AdminAiKnowledgeDocumentEntity::getTitle, needle)
                                .or()
                                .like(AdminAiKnowledgeDocumentEntity::getContent, needle))
                        .orderByDesc(AdminAiKnowledgeDocumentEntity::getId)
                        .last("LIMIT " + KNOWLEDGE_SNIPPET_LIMIT))
                .stream()
                .map(AdminAiPreviewServiceImpl::snippet)
                .filter(StringUtils::hasText)
                .toList();
        return snippets.isEmpty() ? null : snippets;
    }

    private static String snippet(AdminAiKnowledgeDocumentEntity document) {
        String content = document.getContent() == null ? "" : document.getContent().trim();
        if (content.length() > KNOWLEDGE_SNIPPET_CHARS) {
            content = content.substring(0, KNOWLEDGE_SNIPPET_CHARS);
        }
        String title = document.getTitle() == null ? "" : document.getTitle().trim();
        if (!StringUtils.hasText(title)) {
            return content;
        }
        return StringUtils.hasText(content) ? title + "\n" + content : title;
    }

    private AgentPromptProfile loadPromptProfile(long agentId) {
        AdminAiPromptProfileEntity profile = promptProfileMapper.selectOne(
                new LambdaQueryWrapper<AdminAiPromptProfileEntity>()
                        .eq(AdminAiPromptProfileEntity::getAgentId, agentId)
                        .last("LIMIT 1"));
        if (profile == null) {
            return null;
        }
        Double temperature = profile.getTemperature() == null ? null : profile.getTemperature().doubleValue();
        return new AgentPromptProfile(profile.getSystemPrompt(), temperature);
    }

    /**
     * 缺省 skills 保持 null，Python 仍合并进程默认 skill。
     * 请求里带了数组（含空数组）则原样下发，表示用户明确选择，不再补默认 skill。
     */
    private static List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String skill : skills) {
            if (!StringUtils.hasText(skill)) {
                continue;
            }
            String id = skill.trim();
            if (!normalized.contains(id)) {
                normalized.add(id);
            }
        }
        return normalized;
    }

    /** 缺省模式：这条智能体有 CLI 默认入口就用 CLI，否则用 KEY。与概览上优先展示的接入一致。 */
    private String previewMode(long agentId, String requested) {
        if (StringUtils.hasText(requested)) {
            return AdminAiModelEntryServiceImpl.normalizeMode(requested);
        }
        return modelEntryService.selectionChain(agentId, "CLI").isEmpty() ? "KEY" : "CLI";
    }

    private static List<AgentChatHistoryMessage> toHistory(List<AdminAiPreviewHistoryMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        List<AgentChatHistoryMessage> mapped = new ArrayList<>(history.size());
        for (AdminAiPreviewHistoryMessage item : history) {
            if (item == null || !StringUtils.hasText(item.role()) || !StringUtils.hasText(item.content())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            String role = item.role().trim().toUpperCase();
            if (!ALLOWED_ROLES.contains(role)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            mapped.add(new AgentChatHistoryMessage(role, item.content().trim()));
        }
        return mapped;
    }

    private void forwardStream(AgentChatRequest agentRequest, SseEmitter emitter) {
        try {
            agentClient.chatStream(agentRequest, (event, dataJson) -> {
                try {
                    emitter.send(SseEmitter.event().name(event).data(dataJson, MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    throw new ClientDisconnectedException(ex);
                }
            });
            emitter.complete();
        } catch (ClientDisconnectedException ex) {
            emitter.completeWithError(ex.getCause());
        } catch (BusinessException ex) {
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"" + toClientErrorCode(ex) + "\"}");
            emitter.complete();
        } catch (RuntimeException ex) {
            log.warn("admin ai preview stream forwarding failed", ex);
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"SYSTEM_ERROR\"}");
            emitter.complete();
        }
    }

    private static void sendEventQuietly(SseEmitter emitter, String event, String dataJson) {
        try {
            emitter.send(SseEmitter.event().name(event).data(dataJson, MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
            // 客户端已断开
        }
    }

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

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
