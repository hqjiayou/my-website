package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewChatRequest;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewHistoryMessage;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewModelDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewSkillDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiPreviewTodoItem;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiMcpReadyDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiMcpSelectionDto;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiMcpSelectionUpdateRequest;
import com.mywebsite.admin.dto.AdminAiPreviewDtos.AdminAiRuntimeDto;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeDocumentEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseSubscriptionMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeDocumentMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.admin.service.AdminAiPreviewService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentExperienceLessonItem;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentUsageRecord;
import com.mywebsite.agent.dto.AgentMcpSelection;
import com.mywebsite.agent.service.AgentExperienceLessonService;
import com.mywebsite.agent.service.AgentMcpSelectionService;
import com.mywebsite.agent.service.AgentUsageService;
import com.mywebsite.agent.utils.AgentEmbeddingClassUtils;
import com.mywebsite.agent.utils.AgentExperienceLessonUtils;
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
    private final AdminAiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final AdminAiKnowledgeBaseMapper knowledgeBaseMapper;
    private final AdminAiKnowledgeBaseSubscriptionMapper knowledgeSubscriptionMapper;
    private final AdminAiModelEntryService modelEntryService;
    private final LocalizedMessageService localizedMessageService;
    private final ExecutorService adminAiStreamExecutor;
    private final AgentUsageService usageService;
    private final ObjectMapper objectMapper;
    private final AgentExperienceLessonService experienceLessonService;
    private final AgentMcpSelectionService mcpSelectionService;
    private final AdminAiConversationService conversationService;

    public AdminAiPreviewServiceImpl(
            AgentClient agentClient,
            AgentProperties agentProperties,
            AdminAgentMapper adminAgentMapper,
            AdminAiKnowledgeDocumentMapper knowledgeDocumentMapper,
            AdminAiKnowledgeBaseMapper knowledgeBaseMapper,
            AdminAiKnowledgeBaseSubscriptionMapper knowledgeSubscriptionMapper,
            AdminAiModelEntryService modelEntryService,
            LocalizedMessageService localizedMessageService,
            ExecutorService adminAiStreamExecutor,
            AgentUsageService usageService,
            ObjectMapper objectMapper,
            AgentExperienceLessonService experienceLessonService,
            AgentMcpSelectionService mcpSelectionService,
            AdminAiConversationService conversationService) {
        this.agentClient = agentClient;
        this.agentProperties = agentProperties;
        this.adminAgentMapper = adminAgentMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeSubscriptionMapper = knowledgeSubscriptionMapper;
        this.modelEntryService = modelEntryService;
        this.localizedMessageService = localizedMessageService;
        this.adminAiStreamExecutor = adminAiStreamExecutor;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
        this.experienceLessonService = experienceLessonService;
        this.mcpSelectionService = mcpSelectionService;
        this.conversationService = conversationService;
    }

    @Override
    public SseEmitter previewStream(long agentId, AdminAiPreviewChatRequest request) {
        requireSite(agentId);
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (request.conversationId() != null) {
            // 流开始前同步校验：错误 id 走普通 404，而不是流内错误。
            conversationService.requirePreviewConversation(agentId, request.conversationId());
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
        List<AgentModelSelection> embeddingClasses = embeddingClassesForAgent(agentId);
        AgentModelSelection embedding = embeddingClasses == null || embeddingClasses.isEmpty()
                ? null
                : embeddingClasses.get(0);
        String timezone = StringUtils.hasText(request.timezone()) ? request.timezone().trim() : null;
        List<AgentExperienceLessonItem> experienceLessons = approvedExperienceLessons();
        AgentChatRequest agentRequest = new AgentChatRequest(
                String.valueOf(agentId),
                request.message().trim(),
                localizedMessageService.currentLanguageTag(),
                null,
                loadPromptProfile(agentId),
                toHistory(request.history()),
                resolveSkills(agentId, request.skills()),
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
                Boolean.TRUE,
                experienceLessons.isEmpty() ? null : experienceLessons,
                // MCP per-agent 名字过滤（Issue #192 UoW-3）：与正式对话同口径三态。
                mcpWireNames(agentId),
                // 可检索知识空间集（Issue #209 UoW-2）：与正式对话同口径展开。
                knowledgeBaseIds(agentId));
        // 用量记账归属（Issue #140）：试聊只打单入口——点名时就是点名 id，未点名时
        // 是该模式链首（与 previewModel 回填同一口径：chainCandidates 的首元）。
        Long usageEntryId = request.modelEntryId();
        if (usageEntryId == null) {
            List<ChainCandidate> candidates = modelEntryService.chainCandidates(agentId, mode);
            usageEntryId = candidates.isEmpty() ? null : candidates.get(0).id();
        }
        Long usageEntryIdFinal = usageEntryId;
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        adminAiStreamExecutor.execute(() -> forwardStream(
                agentRequest, emitter, agentId, usageEntryIdFinal, mode, request.conversationId()));
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

    /**
     * 已批准经验条目的下发（Issue #184 UoW-4）：经验是平台全局的，试聊与正式
     * 对话同口径每请求现查，批准后在试聊里立刻能看到效果（UoW-5 全链实走的
     * 验证面）。查库失败只记日志、按无条目继续，不拖垮试聊主流程（口径与
     * agent-app 正式链一致）。
     */
    private List<AgentExperienceLessonItem> approvedExperienceLessons() {
        try {
            return AgentExperienceLessonUtils.wire(experienceLessonService.listApproved());
        } catch (RuntimeException ex) {
            log.warn("approved experience lessons lookup failed; continuing without lessons", ex);
            return List.of();
        }
    }

    /**
     * MCP per-agent 名字过滤的下发值（Issue #192 UoW-3）：INHERIT → null、
     * OFF → 空列表、CUSTOM → 名字列表。失败隔离同经验条目：查库失败按
     * INHERIT 继续，不拖垮试聊。
     */
    private List<String> mcpWireNames(long agentId) {
        try {
            return mcpSelectionService.resolveWireNames(agentId);
        } catch (RuntimeException ex) {
            log.warn("mcp selection lookup failed; continuing with INHERIT", ex);
            return null;
        }
    }

    /**
     * 可检索知识空间集的下发值（Issue #209 UoW-2）：该 agent 创建的空间 ∪ 订阅的
     * 空间（订阅表含创建者行，按 base id 去重），字符串化 id；与 agent-app 正式
     * 对话同一展开口径。向量分区键已改 base，执行层据此跨空间检索。失败隔离同
     * MCP 选择：查库失败只记日志、按无空间继续，不拖垮试聊；无空间时返回 null。
     */
    /**
     * 试聊请求级的嵌入类集（Issue #209 UoW-3）：与正式对话同口径——按可检索
     * 空间分别解析登记类集（空间行优先、全站补充）后并集去重，展开序即类
     * 优先级、{@code dedupe} 兜底上限 4 类。无可检索空间时为空列表。
     */
    private List<AgentModelSelection> embeddingClassesForAgent(long agentId) {
        List<String> baseIds = knowledgeBaseIds(agentId);
        if (baseIds == null || baseIds.isEmpty()) {
            return List.of();
        }
        List<AgentModelSelection> union = new ArrayList<>();
        for (String baseId : baseIds) {
            union.addAll(modelEntryService.embeddingClassesForSpace(Long.parseLong(baseId)));
        }
        return AgentEmbeddingClassUtils.dedupe(union);
    }

    private List<String> knowledgeBaseIds(long agentId) {
        try {
            java.util.LinkedHashSet<Long> baseIds = new java.util.LinkedHashSet<>();
            knowledgeBaseMapper
                    .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                            .eq(AdminAiKnowledgeBaseEntity::getCreatedByAgentId, agentId))
                    .forEach(base -> baseIds.add(base.getId()));
            knowledgeSubscriptionMapper
                    .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                            .eq(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId, agentId))
                    .forEach(subscription -> baseIds.add(subscription.getBaseId()));
            if (baseIds.isEmpty()) {
                return null;
            }
            return baseIds.stream().map(String::valueOf).toList();
        } catch (RuntimeException ex) {
            log.warn("knowledge base expansion failed; continuing without knowledge bases", ex);
            return null;
        }
    }

    @Override
    public AdminAiMcpSelectionDto mcpSelection(long agentId) {
        requireSite(agentId);
        AgentMcpSelection selection = mcpSelectionService.getSelection(agentId);
        return new AdminAiMcpSelectionDto(
                selection.mode(), selection.serverNames());
    }

    @Override
    public AdminAiMcpSelectionDto updateMcpSelection(long agentId, AdminAiMcpSelectionUpdateRequest request) {
        requireSite(agentId);
        AgentMcpSelection saved = mcpSelectionService.saveSelection(
                agentId, request.mode(), request.serverNames());
        return new AdminAiMcpSelectionDto(
                saved.mode(), saved.serverNames());
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
            // INHERIT（null）探全局启用集；OFF／CUSTOM 把生效名字交给执行层过滤。
            List<String> onlyNames = mcpWireNames(agentId);
            return new AdminAiMcpReadyDto(
                    true,
                    null,
                    onlyNames == null ? agentClient.mcpReady() : agentClient.mcpReady(onlyNames));
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

    /**
     * 该 agent 可见的知识空间 id 集（创建∪订阅、去重保序）：与
     * {@code AdminAiKnowledgeServiceImpl} 的可见性口径（listBasesForSite）同形，
     * 也与正式对话的 reachableBaseIds 一致。
     */
    private List<Long> visibleBaseIds(long agentId) {
        java.util.LinkedHashSet<Long> baseIds = new java.util.LinkedHashSet<>();
        knowledgeBaseMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseEntity>()
                        .eq(AdminAiKnowledgeBaseEntity::getCreatedByAgentId, agentId))
                .forEach(base -> baseIds.add(base.getId()));
        knowledgeSubscriptionMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity>()
                        .eq(AdminAiKnowledgeBaseSubscriptionEntity::getAgentId, agentId))
                .forEach(subscription -> baseIds.add(subscription.getBaseId()));
        return List.copyOf(baseIds);
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
        // 文档 agent_id 已停写（Issue #209 UoW-1）：按该 agent 可见空间集（创建∪订阅，
        // 与正式对话 reachableBaseIds 同口径）查文档——此前只搜自有空间，向量检索
        // 降级走字面回退时订阅空间文档在试聊里搜不到、与正式对话分叉（解耦深审）。
        // 无可见空间时无片段。
        List<Long> visibleBaseIds = visibleBaseIds(agentId);
        if (visibleBaseIds.isEmpty()) {
            return null;
        }
        List<String> snippets = knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<AdminAiKnowledgeDocumentEntity>()
                        .in(AdminAiKnowledgeDocumentEntity::getBaseId, visibleBaseIds)
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

    /**
     * Prompt 解析（Issue #214 UoW-4）：与正式对话同口径——直接读 agent 行上的
     * 本地副本（{@code prompt_system_prompt}／{@code prompt_temperature}，来源
     * 留痕 {@code prompt_template_id}）。三者皆空返回 null（未配置常态，不记
     * 日志），与旧「无预设返回 null」逐字一致；任一非空即组装 profile（含旧
     * 名称型预设拷出的「只有留痕、prompt 与温度皆空」形态，旧解析同样返回
     * 非 null profile）。配置预设与旧 profile 表自本单起停读。
     */
    private AgentPromptProfile loadPromptProfile(long agentId) {
        AdminAgentEntity agent = adminAgentMapper.selectById(agentId);
        if (agent == null
                || (agent.getPromptTemplateId() == null
                        && !StringUtils.hasText(agent.getPromptSystemPrompt())
                        && agent.getPromptTemperature() == null)) {
            return null;
        }
        Double temperature =
                agent.getPromptTemperature() == null ? null : agent.getPromptTemperature().doubleValue();
        return new AgentPromptProfile(agent.getPromptSystemPrompt(), temperature);
    }

    /**
     * 试聊 skills（Issue #214 UoW-4）：请求显式给出（含空数组）时以请求为准
     * （试聊台的逐轮选集）；请求缺省（null）时改读 agent 自有默认集，与正式
     * 对话同口径——自有集为空/脏 JSON 时返回 null，Python env 兜底不变。
     */
    private List<String> resolveSkills(long agentId, List<String> requestedSkills) {
        if (requestedSkills != null) {
            return normalizeSkills(requestedSkills);
        }
        AdminAgentEntity agent = adminAgentMapper.selectById(agentId);
        if (agent == null || !StringUtils.hasText(agent.getDefaultSkillIds())) {
            return null;
        }
        try {
            List<String> parsed =
                    objectMapper.readValue(agent.getDefaultSkillIds(), new TypeReference<List<String>>() {});
            List<String> skills = normalizeSkills(parsed);
            return skills.isEmpty() ? null : skills;
        } catch (JsonProcessingException ex) {
            log.warn("agent {} has malformed default_skill_ids, preview treated as no default skills", agentId, ex);
            return null;
        }
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
            AgentChatRequest agentRequest,
            SseEmitter emitter,
            long agentId,
            Long usageEntryId,
            String mode,
            Long conversationId) {
        long usageStartedNanos = System.nanoTime();
        AtomicReference<AgentRunMeta> doneMeta = new AtomicReference<>();
        AtomicReference<String> doneReply = new AtomicReference<>("");
        AtomicBoolean usageRecorded = new AtomicBoolean(false);
        try {
            agentClient.chatStream(agentRequest, (event, dataJson) -> {
                if ("DONE".equals(event)) {
                    doneMeta.set(parseDoneRunMeta(dataJson));
                    doneReply.set(parseDoneReply(dataJson));
                }
                try {
                    emitter.send(SseEmitter.event().name(event).data(dataJson, MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    throw new ClientDisconnectedException(ex);
                }
            });
            recordPreviewUsage(agentId, usageEntryId, mode, doneMeta.get(), usageStartedNanos, usageRecorded);
            // 与 agent-app 同口径：只有拿到非空 DONE 回复才落库；失败/无 DONE 不落库、不发 CONVERSATION。
            if (StringUtils.hasText(doneReply.get())) {
                // 预览不带附件：落库的用户消息即下发给执行层的已 trim 文本。
                long persistedId = conversationService.recordPreviewExchange(
                        agentId, conversationId, agentRequest.message(), doneReply.get(), doneMeta.get());
                sendEventQuietly(emitter, "CONVERSATION", "{\"conversationId\":\"" + persistedId + "\"}");
            }
            emitter.complete();
        } catch (ClientDisconnectedException ex) {
            // DONE 已到但最后一帧没发出去：这一轮真实发生过，照常记账（只记一次）并落库。
            recordPreviewUsage(agentId, usageEntryId, mode, doneMeta.get(), usageStartedNanos, usageRecorded);
            if (StringUtils.hasText(doneReply.get())) {
                try {
                    conversationService.recordPreviewExchange(
                            agentId, conversationId, agentRequest.message(), doneReply.get(), doneMeta.get());
                } catch (RuntimeException persistEx) {
                    log.warn("admin ai preview reply was not stored after client disconnect", persistEx);
                }
            }
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

    /** DONE 的 reply 文本；缺失或损坏返回空串（视为无有效 DONE，不落库）。 */
    private String parseDoneReply(String dataJson) {
        try {
            return objectMapper.readTree(dataJson).path("reply").asText("");
        } catch (IOException ex) {
            log.warn("admin ai preview DONE was not valid JSON, reply skipped", ex);
            return "";
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
