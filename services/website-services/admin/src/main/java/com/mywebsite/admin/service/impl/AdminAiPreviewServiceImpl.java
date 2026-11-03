package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewChatRequest;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewHistoryMessage;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewModelDto;
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
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.admin.service.AdminAiPreviewService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentUsageRecord;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.utils.AgentEmbeddingClassUtils;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final AgentUsageService usageService;
    private final ObjectMapper objectMapper;

    public AdminAiPreviewServiceImpl(
            AgentClient agentClient,
            AgentProperties agentProperties,
            AdminAgentMapper adminAgentMapper,
            AdminAiPromptProfileMapper promptProfileMapper,
            AdminAiKnowledgeDocumentMapper knowledgeDocumentMapper,
            AdminAiModelEntryService modelEntryService,
            LocalizedMessageService localizedMessageService,
            ExecutorService adminAiStreamExecutor,
            AgentUsageService usageService,
            ObjectMapper objectMapper) {
        this.agentClient = agentClient;
        this.agentProperties = agentProperties;
        this.adminAgentMapper = adminAgentMapper;
        this.promptProfileMapper = promptProfileMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.modelEntryService = modelEntryService;
        this.localizedMessageService = localizedMessageService;
        this.adminAiStreamExecutor = adminAiStreamExecutor;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public SseEmitter previewStream(long agentId, AdminAiPreviewChatRequest request) {
        requireSite(agentId);
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String mode = previewMode(agentId, request.mode());
        // 试聊只打这一条。同模式其他入口和进程 AI_FALLBACK_MODELS 不参与。
        // 点了入口就用这一条，不改默认入口；不在链上则失败，不改走链首。
        AgentModelSelection primary = previewPrimary(agentId, mode, request.modelEntryId());
        if (primary == null) {
            SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
            String errorCode = request.modelEntryId() == null
                    ? "EXTERNAL_DEPENDENCY_BLOCKED"
                    : "MODEL_ENTRY_UNAVAILABLE";
            // 同步 send：ResponseBodyEmitter 对注册前 send 有缓冲重放语义（early sends），
            // 容器/MockMvc 都按序交付。曾改走执行器异步发送，反而与 MockMvc 的响应打印
            // 并发写响应头触发 ConcurrentModificationException（dev CI 41443553218 实锤，
            // Issue #163），故保持同步。
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"" + errorCode + "\"}");
            emitter.complete();
            return emitter;
        }
        List<AgentModelSelection> embeddingClasses = modelEntryService.embeddingClasses(agentId);
        AgentModelSelection embedding = embeddingClasses == null || embeddingClasses.isEmpty()
                ? null
                : embeddingClasses.get(0);
        String timezone = StringUtils.hasText(request.timezone()) ? request.timezone().trim() : null;
        AgentChatRequest agentRequest = new AgentChatRequest(
                String.valueOf(agentId),
                request.message().trim(),
                localizedMessageService.currentLanguageTag(),
                null,
                loadPromptProfile(agentId),
                toHistory(request.history()),
                normalizeSkills(request.skills()),
                toTodos(request.todos()),
                primary,
                callbackBaseUrl(),
                knowledgeSnippets(agentId, request.message().trim()),
                null,
                null,
                null,
                null,
                null,
                embedding == null ? null : embedding.embeddingSpec(),
                embedding == null ? null : embedding.apiKey(),
                embedding == null ? null : embedding.baseUrl(),
                AgentEmbeddingClassUtils.wire(embeddingClasses),
                timezone,
                request.latitude(),
                request.longitude(),
                request.accuracyMeters(),
                // 试聊标记（Issue #179）：执行层据此启用回复内卡片（VISUALIZATION）。
                Boolean.TRUE);
        // 用量记账归属（Issue #140）：试聊只打单入口——点名时就是点名 id，未点名时
        // 是该模式链首（与 previewModel 回填同一口径：chainCandidates 的首元）。
        Long usageEntryId = request.modelEntryId();
        if (usageEntryId == null) {
            List<ChainCandidate> candidates = modelEntryService.chainCandidates(agentId, mode);
            usageEntryId = candidates.isEmpty() ? null : candidates.get(0).id();
        }
        Long usageEntryIdFinal = usageEntryId;
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        adminAiStreamExecutor.execute(() -> forwardStream(agentRequest, emitter, agentId, usageEntryIdFinal, mode));
        return emitter;
    }

    @Override
    public AdminAiPreviewModelDto previewModel(long agentId, String mode) {
        requireSite(agentId);
        String resolvedMode = previewMode(agentId, mode);
        List<ChainCandidate> candidates = modelEntryService.chainCandidates(agentId, resolvedMode);
        String modelSpec = candidates.isEmpty() ? null : candidates.get(0).modelSpec();
        List<AdminAiPreviewModelDto.Entry> entries = candidates.stream()
                .map(item -> new AdminAiPreviewModelDto.Entry(item.id(), item.displayName(), item.modelSpec()))
                .toList();
        return new AdminAiPreviewModelDto(resolvedMode, modelSpec, !candidates.isEmpty(), entries);
    }

    /**
     * 没点名时用链首。点了名必须在链上；不在链上返回 null，由调用方报 MODEL_ENTRY_UNAVAILABLE，不改用链首。
     * 链为空且没点名时也返回 null，调用方报 EXTERNAL_DEPENDENCY_BLOCKED。
     */
    private AgentModelSelection previewPrimary(long agentId, String mode, Long entryId) {
        if (entryId == null) {
            List<AgentModelSelection> chain = modelEntryService.selectionChain(agentId, mode);
            return chain.isEmpty() ? null : chain.get(0);
        }
        try {
            return modelEntryService.selectionOnChain(agentId, mode, entryId);
        } catch (BusinessException ex) {
            if (ex.getCode() == ErrorCode.MODEL_ENTRY_UNAVAILABLE.getCode()) {
                return null;
            }
            throw ex;
        }
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
        String mode = defaultMode(agentId);
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
        return defaultMode(agentId);
    }

    /** previewMode 缺省分支与 overlayEntry 面板共用：有 CLI 默认入口就用 CLI，否则用 KEY。 */
    private String defaultMode(long agentId) {
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

    private void forwardStream(
            AgentChatRequest agentRequest, SseEmitter emitter, long agentId, Long usageEntryId, String mode) {
        long usageStartedNanos = System.nanoTime();
        AtomicReference<AgentRunMeta> doneMeta = new AtomicReference<>();
        AtomicBoolean usageRecorded = new AtomicBoolean(false);
        try {
            agentClient.chatStream(agentRequest, (event, dataJson) -> {
                if ("DONE".equals(event)) {
                    doneMeta.set(parseDoneRunMeta(dataJson));
                }
                try {
                    emitter.send(SseEmitter.event().name(event).data(dataJson, MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    throw new ClientDisconnectedException(ex);
                }
            });
            recordPreviewUsage(agentId, usageEntryId, mode, doneMeta.get(), usageStartedNanos, usageRecorded);
            emitter.complete();
        } catch (ClientDisconnectedException ex) {
            // DONE 已到但最后一帧没发出去：这一轮真实发生过，照常记账（只记一次）。
            recordPreviewUsage(agentId, usageEntryId, mode, doneMeta.get(), usageStartedNanos, usageRecorded);
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

    /** 解析流式 DONE 的 runMeta（与 agent-app extractRunMeta 同法：整个 DONE 节点即 runMeta 形状）。 */
    private AgentRunMeta parseDoneRunMeta(String dataJson) {
        try {
            return objectMapper
                    .readerFor(AgentRunMeta.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(dataJson);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("admin ai preview DONE carried malformed run metadata, usage skipped", ex);
            return null;
        }
    }

    /** 试聊用量记账（Issue #140）：只有拿到 DONE 才记；无 DONE（失败）不记。 */
    private void recordPreviewUsage(
            long agentId,
            Long entryId,
            String mode,
            AgentRunMeta runMeta,
            long startedNanos,
            AtomicBoolean recorded) {
        if (runMeta == null || !recorded.compareAndSet(false, true)) {
            return;
        }
        AgentRunMeta.Usage usage = runMeta.usage();
        usageService.recordUsage(new AgentUsageRecord(
                agentId,
                entryId,
                null,
                mode,
                AgentUsageService.SOURCE_ADMIN_PREVIEW,
                (long) usage.inputTokens(),
                (long) usage.outputTokens(),
                (System.nanoTime() - startedNanos) / 1_000_000L,
                AgentUsageService.STATUS_SUCCESS,
                null));
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
