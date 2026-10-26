package com.mywebsite.site.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentAttachmentDto;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentChatResponse;
import com.mywebsite.agent.dto.AgentCitation;
import com.mywebsite.agent.dto.AgentRetrievalSearchRequest;
import com.mywebsite.agent.dto.AgentRetrievalSearchResult;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.security.AuthContext;
import com.mywebsite.common.security.AuthenticatedUser;
import com.mywebsite.common.security.SubjectCodes;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import com.mywebsite.infra.service.LocalizedMessageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.properties.AgentProperties;
import com.mywebsite.site.dto.SiteAiAttachmentDto;
import com.mywebsite.site.dto.SiteAiChatRequestDto;
import com.mywebsite.site.entity.SiteAiKnowledgeDocumentEntity;
import com.mywebsite.site.entity.SiteAiModelEntryEntity;
import com.mywebsite.site.mapper.SiteAiKnowledgeDocumentMapper;
import com.mywebsite.site.mapper.SiteAiModelEntryMapper;
import com.mywebsite.site.dto.SiteAiChatResponseDto;
import com.mywebsite.site.dto.SiteKnowledgeSearchRequestDto;
import com.mywebsite.site.dto.SiteKnowledgeSearchResponseDto;
import com.mywebsite.site.service.SiteAiConversationService;
import com.mywebsite.site.service.SiteAiConversationService.ChatPreparation;
import com.mywebsite.site.service.SiteAiRateLimitService;
import com.mywebsite.site.service.SiteAiService;
import com.mywebsite.site.service.SiteUserMemoryService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
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

    /** 与 agent-services ChatRequest.message 上限一致。超长在进会话和限流之前拒绝。 */
    private static final int MAX_MESSAGE_CHARS = 16_000;
    private static final int MAX_ATTACHMENTS = 3;
    private static final int MAX_ATTACHMENT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_TOTAL_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ATTACHMENT_MEDIA_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final int KNOWLEDGE_NEEDLE_CHARS = 80;
    private static final int KNOWLEDGE_SNIPPET_CHARS = 800;
    private static final int KNOWLEDGE_SNIPPET_LIMIT = 4;

    private final AgentClient agentClient;
    private final AgentProperties agentProperties;
    private final ModelEntryCipher modelEntryCipher;
    private final SiteAiRateLimitService rateLimitService;
    private final SiteAiConversationService conversationService;
    private final SiteUserMemoryService memoryService;
    private final SiteAiModelEntryMapper modelEntryMapper;
    private final SiteAiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final LocalizedMessageService localizedMessageService;
    private final ExecutorService siteAiStreamExecutor;
    private final ObjectMapper objectMapper;

    public SiteAiServiceImpl(
            AgentClient agentClient,
            AgentProperties agentProperties,
            ModelEntryCipher modelEntryCipher,
            SiteAiRateLimitService rateLimitService,
            SiteAiConversationService conversationService,
            SiteUserMemoryService memoryService,
            SiteAiModelEntryMapper modelEntryMapper,
            SiteAiKnowledgeDocumentMapper knowledgeDocumentMapper,
            LocalizedMessageService localizedMessageService,
            ExecutorService siteAiStreamExecutor,
            ObjectMapper objectMapper) {
        this.agentClient = agentClient;
        this.agentProperties = agentProperties;
        this.modelEntryCipher = modelEntryCipher;
        this.rateLimitService = rateLimitService;
        this.conversationService = conversationService;
        this.memoryService = memoryService;
        this.modelEntryMapper = modelEntryMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.localizedMessageService = localizedMessageService;
        this.siteAiStreamExecutor = siteAiStreamExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public SiteAiChatResponseDto chat(long siteId, SiteAiChatRequestDto request) {
        ValidatedChat validated = validateChatAccess(siteId, request);
        long siteUserId = requireSiteUserId(validated.user());
        String message = validated.message();
        List<AgentAttachmentDto> attachments = toAgentAttachments(validated.attachments());

        ChatPreparation preparation =
                conversationService.prepare(siteId, siteUserId, request.conversationId());
        List<String> memories = memoryService.recentContents(siteId, siteUserId);
        AgentChatResponse response =
                agentClient.chat(buildAgentRequest(siteId, message, attachments, preparation, memories));
        persistTurn(
                siteId,
                siteUserId,
                preparation,
                message,
                attachments,
                response.reply(),
                response.todos(),
                response.runMeta(),
                response.citations(),
                response.extractedMemories());

        return new SiteAiChatResponseDto(
                response.reply(),
                String.valueOf(preparation.conversation().getId()),
                response.providerStatus(),
                toCitations(response.citations()));
    }

    @Override
    public SiteKnowledgeSearchResponseDto searchKnowledge(long siteId, SiteKnowledgeSearchRequestDto request) {
        AuthenticatedUser user = requireSiteMember(siteId);
        requireSiteUserId(user);
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String query = request.query().trim();
        if (query.length() > SiteKnowledgeSearchRequestDto.MAX_QUERY_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        boolean vectorDegraded = false;
        try {
            AgentModelSelection selection = loadDefaultModel(siteId);
            AgentRetrievalSearchResult result = agentClient.searchRetrieval(new AgentRetrievalSearchRequest(
                    String.valueOf(siteId),
                    query,
                    clampTopK(request.topK()),
                    selection == null ? null : selection.embeddingSpec(),
                    selection == null ? null : selection.apiKey(),
                    selection == null ? null : selection.baseUrl()));
            if (!result.degraded() && !result.hits().isEmpty()) {
                List<SiteKnowledgeSearchResponseDto.Hit> hits = result.hits().stream()
                        .map(hit -> new SiteKnowledgeSearchResponseDto.Hit(
                                hit.sourceId(), hit.chunkIndex(), hit.score(), hit.content()))
                        .toList();
                return new SiteKnowledgeSearchResponseDto(hits, false, result.rerankApplied());
            }
            vectorDegraded = result.degraded();
        } catch (BusinessException ex) {
            if (ex.getCode() != ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()) {
                throw ex;
            }
            vectorDegraded = true;
        }
        List<SiteKnowledgeSearchResponseDto.Hit> textHits = textKnowledgeHits(siteId, query);
        if (!textHits.isEmpty()) {
            return new SiteKnowledgeSearchResponseDto(textHits, false);
        }
        return new SiteKnowledgeSearchResponseDto(List.of(), vectorDegraded);
    }

    @Override
    public List<com.mywebsite.site.dto.SiteKnowledgeDocumentDtos.ListItem> listKnowledgeDocuments(long siteId) {
        AuthenticatedUser user = requireSiteMember(siteId);
        requireSiteUserId(user);
        return knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<SiteAiKnowledgeDocumentEntity>()
                        .select(
                                SiteAiKnowledgeDocumentEntity::getId,
                                SiteAiKnowledgeDocumentEntity::getTitle,
                                SiteAiKnowledgeDocumentEntity::getUpdatedAt)
                        .eq(SiteAiKnowledgeDocumentEntity::getSiteId, siteId)
                        .eq(SiteAiKnowledgeDocumentEntity::getStatus, SiteAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                        .orderByDesc(SiteAiKnowledgeDocumentEntity::getUpdatedAt)
                        .orderByDesc(SiteAiKnowledgeDocumentEntity::getId))
                .stream()
                .map(doc -> new com.mywebsite.site.dto.SiteKnowledgeDocumentDtos.ListItem(
                        doc.getId(), doc.getTitle(), doc.getUpdatedAt()))
                .toList();
    }

    @Override
    public com.mywebsite.site.dto.SiteKnowledgeDocumentDtos.Detail getKnowledgeDocument(
            long siteId, long documentId) {
        AuthenticatedUser user = requireSiteMember(siteId);
        requireSiteUserId(user);
        SiteAiKnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<SiteAiKnowledgeDocumentEntity>()
                        .eq(SiteAiKnowledgeDocumentEntity::getId, documentId)
                        .eq(SiteAiKnowledgeDocumentEntity::getSiteId, siteId)
                        .eq(SiteAiKnowledgeDocumentEntity::getStatus, SiteAiKnowledgeDocumentEntity.STATUS_ACTIVE));
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new com.mywebsite.site.dto.SiteKnowledgeDocumentDtos.Detail(
                doc.getId(), doc.getTitle(), doc.getContent(), doc.getUpdatedAt());
    }

    /** 与内容列表 limit 相同：越界夹进 1..{@link SiteKnowledgeSearchRequestDto#MAX_TOP_K}，null 交给 Python 默认。 */
    private static Integer clampTopK(Integer topK) {
        if (topK == null) {
            return null;
        }
        return Math.min(Math.max(topK, 1), SiteKnowledgeSearchRequestDto.MAX_TOP_K);
    }

    private static List<SiteAiChatResponseDto.Citation> toCitations(List<AgentCitation> citations) {
        return citations.stream()
                .map(citation -> new SiteAiChatResponseDto.Citation(
                        citation.sourceId(), citation.chunkIndex(), citation.score()))
                .toList();
    }

    @Override
    public SseEmitter chatStream(long siteId, SiteAiChatRequestDto request) {
        ValidatedChat validated = validateChatAccess(siteId, request);
        long siteUserId = requireSiteUserId(validated.user());
        String message = validated.message();
        List<AgentAttachmentDto> attachments = toAgentAttachments(validated.attachments());

        ChatPreparation preparation =
                conversationService.prepare(siteId, siteUserId, request.conversationId());
        List<String> memories = memoryService.recentContents(siteId, siteUserId);
        AgentChatRequest agentRequest =
                buildAgentRequest(siteId, message, attachments, preparation, memories);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        siteAiStreamExecutor.execute(
                () -> forwardStream(siteId, siteUserId, preparation, message, attachments, agentRequest, emitter));
        return emitter;
    }

    private AgentChatRequest buildAgentRequest(
            long siteId,
            String message,
            List<AgentAttachmentDto> attachments,
            ChatPreparation preparation,
            List<String> memories) {
        Long conversationId = preparation.conversation().getId();
        List<AgentModelSelection> chain = loadModelChain(siteId);
        return new AgentChatRequest(
                String.valueOf(siteId),
                message,
                localizedMessageService.currentLanguageTag(),
                conversationId == null ? null : String.valueOf(conversationId),
                preparation.promptProfile(),
                preparation.history().isEmpty() ? null : preparation.history(),
                // 终端用户不能指定 skills（防前台激活任意指令模块）；只用 agent-services 平台默认
                null,
                preparation.todos().isEmpty() ? null : preparation.todos(),
                chain.isEmpty() ? null : chain.get(0),
                callbackBaseUrl(),
                knowledgeSnippets(siteId, message),
                attachments.isEmpty() ? null : attachments,
                memories == null || memories.isEmpty() ? null : memories,
                Boolean.TRUE,
                chain.size() > 1 ? List.copyOf(chain.subList(1, chain.size())) : null);
    }

    private void persistTurn(
            long siteId,
            long siteUserId,
            ChatPreparation preparation,
            String userMessage,
            List<AgentAttachmentDto> userAttachments,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentCitation> citations,
            List<String> extractedMemories) {
        conversationService.recordExchange(
                preparation.conversation(),
                userMessage,
                reply,
                todos,
                runMeta,
                userAttachments,
                citations);
        memoryService.storeExtracted(
                siteId, siteUserId, preparation.conversation().getId(), extractedMemories);
    }

    private static List<AgentAttachmentDto> toAgentAttachments(List<SiteAiAttachmentDto> attachments) {
        if (attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(item -> new AgentAttachmentDto(item.mediaType(), item.data()))
                .toList();
    }

    private String callbackBaseUrl() {
        String configured = agentProperties.getBusinessApiBaseUrl();
        return StringUtils.hasText(configured) ? configured.trim() : null;
    }

    /** 向量索引没有命中时，Python 用这些原文片段回答。没有匹配则不传。 */
    private List<String> knowledgeSnippets(long siteId, String query) {
        String needle = needle(query);
        if (needle.isEmpty()) {
            return null;
        }
        List<String> snippets = knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<SiteAiKnowledgeDocumentEntity>()
                        .eq(SiteAiKnowledgeDocumentEntity::getSiteId, siteId)
                        .eq(SiteAiKnowledgeDocumentEntity::getStatus, SiteAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                        .and(wrapper -> wrapper
                                .like(SiteAiKnowledgeDocumentEntity::getTitle, needle)
                                .or()
                                .like(SiteAiKnowledgeDocumentEntity::getContent, needle))
                        .orderByDesc(SiteAiKnowledgeDocumentEntity::getId)
                        .last("LIMIT " + KNOWLEDGE_SNIPPET_LIMIT))
                .stream()
                .map(SiteAiServiceImpl::snippet)
                .filter(StringUtils::hasText)
                .toList();
        return snippets.isEmpty() ? null : snippets;
    }

    private List<SiteKnowledgeSearchResponseDto.Hit> textKnowledgeHits(long siteId, String query) {
        String needle = needle(query);
        if (needle.isEmpty()) {
            return List.of();
        }
        return knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<SiteAiKnowledgeDocumentEntity>()
                        .eq(SiteAiKnowledgeDocumentEntity::getSiteId, siteId)
                        .eq(SiteAiKnowledgeDocumentEntity::getStatus, SiteAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                        .and(wrapper -> wrapper
                                .like(SiteAiKnowledgeDocumentEntity::getTitle, needle)
                                .or()
                                .like(SiteAiKnowledgeDocumentEntity::getContent, needle))
                        .orderByDesc(SiteAiKnowledgeDocumentEntity::getId)
                        .last("LIMIT " + KNOWLEDGE_SNIPPET_LIMIT))
                .stream()
                .map(document -> new SiteKnowledgeSearchResponseDto.Hit(
                        "kb:" + document.getId(), 0, 1.0d, snippet(document)))
                .filter(hit -> StringUtils.hasText(hit.content()))
                .toList();
    }

    private static String needle(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() > KNOWLEDGE_NEEDLE_CHARS) {
            return trimmed.substring(0, KNOWLEDGE_NEEDLE_CHARS);
        }
        return trimmed;
    }

    private static String snippet(SiteAiKnowledgeDocumentEntity document) {
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

    /** 站点默认入口优先，否则全站默认。没有时不带 model，Python 回退进程 AI_MODEL。 */
    private AgentModelSelection loadDefaultModel(long siteId) {
        List<AgentModelSelection> chain = loadModelChain(siteId);
        return chain.isEmpty() ? null : chain.get(0);
    }

    /**
     * 生效链（与 admin AdminAiModelEntryServiceImpl.selectionChain 同规则，两处须一致）：
     * 首元是主用（站点默认，否则全站默认），其后依次是同站其他启用入口、全站其他启用入口
     * （各组内默认在前、id 升序）。主用不存在或解密不出 key 时返回空列表，备援不得越过坏主用顶上；
     * 解密不出 key 的备援跳过。
     */
    private List<AgentModelSelection> loadModelChain(long siteId) {
        List<SiteAiModelEntryEntity> siteEntries = enabledModelEntries(siteId);
        List<SiteAiModelEntryEntity> globalEntries = enabledModelEntries(null);
        SiteAiModelEntryEntity primary = firstDefaultEntry(siteEntries);
        if (primary == null) {
            primary = firstDefaultEntry(globalEntries);
        }
        if (primary == null) {
            return List.of();
        }
        AgentModelSelection primarySelection = toModelSelection(primary);
        if (primarySelection == null) {
            return List.of();
        }
        List<AgentModelSelection> chain = new ArrayList<>();
        chain.add(primarySelection);
        appendModelSelections(chain, siteEntries, primary.getId());
        appendModelSelections(chain, globalEntries, primary.getId());
        return List.copyOf(chain);
    }

    /** 作用域内启用入口：默认在前，其余 id 升序。 */
    private List<SiteAiModelEntryEntity> enabledModelEntries(Long siteId) {
        LambdaQueryWrapper<SiteAiModelEntryEntity> query = new LambdaQueryWrapper<SiteAiModelEntryEntity>()
                .eq(SiteAiModelEntryEntity::getEnabled, true)
                .orderByDesc(SiteAiModelEntryEntity::getDefaultEntry)
                .orderByAsc(SiteAiModelEntryEntity::getId);
        if (siteId == null) {
            query.isNull(SiteAiModelEntryEntity::getSiteId);
        } else {
            query.eq(SiteAiModelEntryEntity::getSiteId, siteId);
        }
        List<SiteAiModelEntryEntity> entries = modelEntryMapper.selectList(query);
        return entries == null ? List.of() : entries;
    }

    private static SiteAiModelEntryEntity firstDefaultEntry(List<SiteAiModelEntryEntity> entries) {
        for (SiteAiModelEntryEntity entry : entries) {
            if (Boolean.TRUE.equals(entry.getDefaultEntry())) {
                return entry;
            }
        }
        return null;
    }

    private void appendModelSelections(
            List<AgentModelSelection> chain, List<SiteAiModelEntryEntity> entries, Long skipId) {
        for (SiteAiModelEntryEntity entry : entries) {
            if (entry.getId() != null && entry.getId().equals(skipId)) {
                continue;
            }
            AgentModelSelection selection = toModelSelection(entry);
            if (selection != null) {
                chain.add(selection);
            }
        }
    }

    private AgentModelSelection toModelSelection(SiteAiModelEntryEntity entry) {
        if (entry == null || !StringUtils.hasText(entry.getModelSpec())) {
            return null;
        }
        String apiKey = modelEntryCipher.decrypt(entry.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String baseUrl = StringUtils.hasText(entry.getBaseUrl()) ? entry.getBaseUrl().trim() : null;
        String embeddingSpec =
                StringUtils.hasText(entry.getEmbeddingSpec()) ? entry.getEmbeddingSpec().trim() : null;
        return new AgentModelSelection(entry.getModelSpec().trim(), apiKey, baseUrl, embeddingSpec);
    }

    private ValidatedChat validateChatAccess(long siteId, SiteAiChatRequestDto request) {
        AuthenticatedUser user = requireSiteMember(siteId);
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String message = request.message() == null ? "" : request.message().trim();
        List<SiteAiAttachmentDto> attachments = validateAttachments(request.attachments());
        if (!StringUtils.hasText(message) && attachments.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (message.length() > MAX_MESSAGE_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (!rateLimitService.tryAcquire(siteId, user.subject())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return new ValidatedChat(user, message, attachments);
    }

    private static List<SiteAiAttachmentDto> validateAttachments(List<SiteAiAttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        long total = 0;
        for (SiteAiAttachmentDto attachment : attachments) {
            if (attachment == null
                    || !ATTACHMENT_MEDIA_TYPES.contains(attachment.mediaType())
                    || !StringUtils.hasText(attachment.data())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(attachment.data());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (decoded.length > MAX_ATTACHMENT_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            total += decoded.length;
            if (total > MAX_ATTACHMENTS_TOTAL_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
        }
        return List.copyOf(attachments);
    }

    private static AuthenticatedUser requireSiteMember(long siteId) {
        AuthenticatedUser user =
                AuthContext.currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.siteIdOrNull() == null || !Objects.equals(user.siteIdOrNull(), siteId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private static long requireSiteUserId(AuthenticatedUser user) {
        return SubjectCodes.parseSiteUserId(user.subject())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    void forwardStream(
            long siteId,
            long siteUserId,
            ChatPreparation preparation,
            String userMessage,
            List<AgentAttachmentDto> userAttachments,
            AgentChatRequest agentRequest,
            SseEmitter emitter) {
        StringBuilder doneReply = new StringBuilder();
        List<AgentTodoItem> doneTodos = new ArrayList<>();
        AtomicReference<AgentRunMeta> doneMeta = new AtomicReference<>(AgentRunMeta.EMPTY);
        AtomicReference<List<AgentCitation>> doneCitations = new AtomicReference<>(List.of());
        AtomicReference<List<String>> doneMemories = new AtomicReference<>(List.of());
        try {
            agentClient.chatStream(agentRequest, (event, dataJson) -> {
                JsonNode data = readJson(dataJson);
                if ("DONE".equals(event)) {
                    doneReply.append(extractReply(data));
                    doneTodos.addAll(extractTodos(data));
                    doneMeta.set(extractRunMeta(data));
                    doneCitations.set(readDoneCitations(data));
                    doneMemories.set(readExtractedMemories(data));
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
                persistTurn(
                        siteId,
                        siteUserId,
                        preparation,
                        userMessage,
                        userAttachments,
                        doneReply.toString(),
                        doneTodos,
                        doneMeta.get(),
                        doneCitations.get(),
                        doneMemories.get());
                sendEventQuietly(
                        emitter,
                        "CONVERSATION",
                        "{\"conversationId\":\"" + preparation.conversation().getId() + "\"}");
            }
            emitter.complete();
        } catch (ClientDisconnectedException ex) {
            // DONE 已收到但最后一帧没发出去：回复和工具副作用已经发生，必须落库，避免用户重试再写一次预约。
            if (doneReply.length() > 0) {
                try {
                    persistTurn(
                            siteId,
                            siteUserId,
                            preparation,
                            userMessage,
                            userAttachments,
                            doneReply.toString(),
                            doneTodos,
                            doneMeta.get(),
                            doneCitations.get(),
                            doneMemories.get());
                } catch (RuntimeException recordEx) {
                    log.warn("site ai stream reply was not stored after client disconnect", recordEx);
                }
            }
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
     * 终端用户只看到回复文本、citations 与错误码（§22.8）：工具轨迹 / 用量 / skills / todos / 原始错误信息
     * 仅供后台观测（admin 预览原样透传）。返回 null = 不下发；未知事件默认不下发，防 Python 新增字段自动外泄。
     * DONE 在这里重写，不是透传 Python payload。
     */
    private String toClientEventData(String event, JsonNode data, String rawJson) {
        return switch (event) {
            case "TEXT_DELTA" -> rawJson;
            case "START" -> "{}";
            case "DONE" -> {
                ObjectNode done = objectMapper.createObjectNode();
                done.put("reply", extractReply(data));
                done.put("providerStatus", data.path("providerStatus").asText("LIVE"));
                done.set("citations", objectMapper.valueToTree(extractCitations(data)));
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

    private List<SiteAiChatResponseDto.Citation> extractCitations(JsonNode done) {
        return toCitations(readDoneCitations(done));
    }

    private List<AgentCitation> readDoneCitations(JsonNode done) {
        JsonNode citations = done.get("citations");
        if (citations == null || !citations.isArray()) {
            return List.of();
        }
        try {
            AgentCitation[] parsed = objectMapper.treeToValue(citations, AgentCitation[].class);
            if (parsed == null) {
                return List.of();
            }
            List<AgentCitation> mapped = new ArrayList<>();
            for (AgentCitation citation : parsed) {
                if (citation != null) {
                    mapped.add(citation);
                }
            }
            return List.copyOf(mapped);
        } catch (IOException | IllegalArgumentException ex) {
            log.warn("site ai stream DONE carried malformed citations, ignored", ex);
            return List.of();
        }
    }

    private List<String> readExtractedMemories(JsonNode done) {
        JsonNode memories = done.get("extractedMemories");
        if (memories == null || memories.isNull() || !memories.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : memories) {
            if (item != null && item.isTextual()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
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

    private record ValidatedChat(AuthenticatedUser user, String message, List<SiteAiAttachmentDto> attachments) {}

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
