package com.mywebsite.agent.service.impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentAttachmentDto;
import com.mywebsite.agent.dto.AgentChatRequest;
import com.mywebsite.agent.dto.AgentDocumentContext;
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
import com.mywebsite.agent.dto.AgentAiAttachmentDto;
import com.mywebsite.agent.dto.AgentAiChatRequestDto;
import com.mywebsite.agent.dto.AgentAiModelModesDto;
import com.mywebsite.agent.entity.AgentAiKnowledgeDocumentEntity;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.agent.dto.AgentAiChatResponseDto;
import com.mywebsite.agent.dto.AgentAiDocumentDto;
import com.mywebsite.agent.service.ChatDocumentTextExtractor;
import com.mywebsite.agent.dto.AgentKnowledgeSearchRequestDto;
import com.mywebsite.agent.dto.AgentKnowledgeSearchResponseDto;
import com.mywebsite.agent.service.AgentAiConversationService;
import com.mywebsite.agent.service.AgentAiConversationService.ChatPreparation;
import com.mywebsite.agent.service.AgentAiRateLimitService;
import com.mywebsite.agent.service.AgentAiService;
import com.mywebsite.agent.service.AgentUserMemoryService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public class AgentAiServiceImpl implements AgentAiService {

    private static final Logger log = LoggerFactory.getLogger(AgentAiServiceImpl.class);
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
    /** 文档附件：与 Python ChatDocument 上限一致（份数/文本），字节上限与知识库上传一致。 */
    private static final int MAX_DOCUMENTS = 3;
    private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DOCUMENT_TEXT_CHARS = 60_000;
    private static final int MAX_DOCUMENT_NAME_CHARS = 200;
    /**
     * 文档附件可接受的扩展名，与知识库上传（infra KnowledgeContentConverter 可路由的
     * 非图片格式）保持一致；值是登记给前端/审计用的媒体类型，文本小类统一 text/plain。
     * 图片不走这里（图片附件有独立的视觉通道）。
     */
    private static final Map<String, String> DOCUMENT_MEDIA_TYPES =
            Map.ofEntries(
                    Map.entry("pdf", "application/pdf"),
                    Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    Map.entry("docm", "application/vnd.ms-word.document.macroEnabled.12"),
                    Map.entry("doc", "application/msword"),
                    Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    Map.entry("xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12"),
                    Map.entry("xls", "application/vnd.ms-excel"),
                    Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                    Map.entry("pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12"),
                    Map.entry("ppt", "application/vnd.ms-powerpoint"),
                    Map.entry("html", "text/html"),
                    Map.entry("htm", "text/html"),
                    Map.entry("epub", "application/epub+zip"),
                    Map.entry("odt", "application/vnd.oasis.opendocument.text"),
                    Map.entry("ods", "application/vnd.oasis.opendocument.spreadsheet"),
                    Map.entry("odp", "application/vnd.oasis.opendocument.presentation"),
                    Map.entry("rtf", "application/rtf"),
                    Map.entry("csv", "text/csv"),
                    Map.entry("tsv", "text/tab-separated-values"),
                    Map.entry("txt", "text/plain"),
                    Map.entry("md", "text/markdown"),
                    Map.entry("markdown", "text/markdown"),
                    Map.entry("text", "text/plain"),
                    Map.entry("rst", "text/plain"),
                    Map.entry("adoc", "text/plain"),
                    Map.entry("asciidoc", "text/plain"),
                    Map.entry("org", "text/plain"),
                    Map.entry("tex", "text/plain"),
                    Map.entry("json", "text/plain"),
                    Map.entry("jsonl", "text/plain"),
                    Map.entry("ndjson", "text/plain"),
                    Map.entry("xml", "text/plain"),
                    Map.entry("yaml", "text/plain"),
                    Map.entry("yml", "text/plain"),
                    Map.entry("toml", "text/plain"),
                    Map.entry("ini", "text/plain"),
                    Map.entry("log", "text/plain"),
                    Map.entry("properties", "text/plain"),
                    Map.entry("conf", "text/plain"),
                    Map.entry("cfg", "text/plain"),
                    Map.entry("env", "text/plain"),
                    Map.entry("diff", "text/plain"),
                    Map.entry("patch", "text/plain"),
                    Map.entry("py", "text/plain"),
                    Map.entry("java", "text/plain"),
                    Map.entry("js", "text/plain"),
                    Map.entry("ts", "text/plain"),
                    Map.entry("tsx", "text/plain"),
                    Map.entry("jsx", "text/plain"),
                    Map.entry("css", "text/plain"),
                    Map.entry("scss", "text/plain"),
                    Map.entry("sql", "text/plain"),
                    Map.entry("sh", "text/plain"),
                    Map.entry("bash", "text/plain"),
                    Map.entry("zsh", "text/plain"),
                    Map.entry("ps1", "text/plain"),
                    Map.entry("mjs", "text/plain"),
                    Map.entry("cjs", "text/plain"),
                    Map.entry("go", "text/plain"),
                    Map.entry("rs", "text/plain"),
                    Map.entry("c", "text/plain"),
                    Map.entry("h", "text/plain"),
                    Map.entry("cpp", "text/plain"),
                    Map.entry("hpp", "text/plain"),
                    Map.entry("cs", "text/plain"),
                    Map.entry("rb", "text/plain"),
                    Map.entry("php", "text/plain"),
                    Map.entry("swift", "text/plain"),
                    Map.entry("kt", "text/plain"),
                    Map.entry("kts", "text/plain"),
                    Map.entry("scala", "text/plain"),
                    Map.entry("pl", "text/plain"),
                    Map.entry("r", "text/plain"),
                    Map.entry("lua", "text/plain"),
                    Map.entry("vue", "text/plain"),
                    Map.entry("svelte", "text/plain"),
                    Map.entry("dart", "text/plain"),
                    Map.entry("groovy", "text/plain"),
                    Map.entry("gradle", "text/plain"),
                    Map.entry("hs", "text/plain"),
                    Map.entry("ex", "text/plain"),
                    Map.entry("exs", "text/plain"),
                    Map.entry("tf", "text/plain"),
                    Map.entry("proto", "text/plain"));
    private static final int KNOWLEDGE_NEEDLE_CHARS = 80;
    private static final int KNOWLEDGE_SNIPPET_CHARS = 800;
    private static final int KNOWLEDGE_SNIPPET_LIMIT = 4;

    private final AgentClient agentClient;
    private final AgentProperties agentProperties;
    private final ModelEntryCipher modelEntryCipher;
    private final AgentAiRateLimitService rateLimitService;
    private final AgentAiConversationService conversationService;
    private final AgentUserMemoryService memoryService;
    private final AgentAiModelEntryMapper modelEntryMapper;
    private final AgentAiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final LocalizedMessageService localizedMessageService;
    private final ExecutorService agentAiStreamExecutor;
    private final ObjectMapper objectMapper;

    public AgentAiServiceImpl(
            AgentClient agentClient,
            AgentProperties agentProperties,
            ModelEntryCipher modelEntryCipher,
            AgentAiRateLimitService rateLimitService,
            AgentAiConversationService conversationService,
            AgentUserMemoryService memoryService,
            AgentAiModelEntryMapper modelEntryMapper,
            AgentAiKnowledgeDocumentMapper knowledgeDocumentMapper,
            LocalizedMessageService localizedMessageService,
            ExecutorService agentAiStreamExecutor,
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
        this.agentAiStreamExecutor = agentAiStreamExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentAiChatResponseDto chat(long agentId, AgentAiChatRequestDto request) {
        ValidatedChat validated = validateChatAccess(agentId, request);
        long agentUserId = requireAgentUserId(validated.user());
        String message = validated.message();
        List<AgentAttachmentDto> attachments = toAgentAttachments(validated.attachments());

        ChatPreparation preparation =
                conversationService.prepare(agentId, agentUserId, request.conversationId());
        List<String> memories = memoryService.recentContents(agentId, agentUserId);
        AgentChatRequest agentRequest = buildAgentRequest(
                agentId, message, attachments, validated.documents(), preparation, memories,
                normalizeChatMode(request.mode()));
        AgentChatResponse response = agentClient.chat(agentRequest);
        persistTurn(
                agentId,
                agentUserId,
                preparation,
                message,
                storedAttachments(attachments, validated),
                response.reply(),
                response.todos(),
                response.runMeta(),
                response.citations(),
                response.extractedMemories(),
                requestedModel(agentRequest));

        return new AgentAiChatResponseDto(
                response.reply(),
                String.valueOf(preparation.conversation().getId()),
                response.providerStatus(),
                toCitations(response.citations()));
    }

    @Override
    public AgentKnowledgeSearchResponseDto searchKnowledge(long agentId, AgentKnowledgeSearchRequestDto request) {
        AuthenticatedUser user = requireAgentMember(agentId);
        requireAgentUserId(user);
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String query = request.query().trim();
        if (query.length() > AgentKnowledgeSearchRequestDto.MAX_QUERY_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        boolean vectorDegraded = false;
        try {
            AgentModelSelection selection = embeddingSelection(agentId);
            AgentRetrievalSearchResult result = agentClient.searchRetrieval(new AgentRetrievalSearchRequest(
                    String.valueOf(agentId),
                    query,
                    clampTopK(request.topK()),
                    selection == null ? null : selection.embeddingSpec(),
                    selection == null ? null : selection.apiKey(),
                    selection == null ? null : selection.baseUrl()));
            if (!result.degraded() && !result.hits().isEmpty()) {
                List<AgentKnowledgeSearchResponseDto.Hit> hits = result.hits().stream()
                        .map(hit -> new AgentKnowledgeSearchResponseDto.Hit(
                                hit.sourceId(), hit.chunkIndex(), hit.score(), hit.content()))
                        .toList();
                return new AgentKnowledgeSearchResponseDto(hits, false, result.rerankApplied());
            }
            vectorDegraded = result.degraded();
        } catch (BusinessException ex) {
            if (ex.getCode() != ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()) {
                throw ex;
            }
            vectorDegraded = true;
        }
        List<AgentKnowledgeSearchResponseDto.Hit> textHits = textKnowledgeHits(agentId, query);
        if (!textHits.isEmpty()) {
            return new AgentKnowledgeSearchResponseDto(textHits, false);
        }
        return new AgentKnowledgeSearchResponseDto(List.of(), vectorDegraded);
    }

    @Override
    public List<com.mywebsite.agent.dto.AgentKnowledgeDocumentDtos.ListItem> listKnowledgeDocuments(long agentId) {
        AuthenticatedUser user = requireAgentMember(agentId);
        requireAgentUserId(user);
        return knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity>()
                        .select(
                                AgentAiKnowledgeDocumentEntity::getId,
                                AgentAiKnowledgeDocumentEntity::getTitle,
                                AgentAiKnowledgeDocumentEntity::getUpdatedAt)
                        .eq(AgentAiKnowledgeDocumentEntity::getAgentId, agentId)
                        .eq(AgentAiKnowledgeDocumentEntity::getStatus, AgentAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                        .orderByDesc(AgentAiKnowledgeDocumentEntity::getUpdatedAt)
                        .orderByDesc(AgentAiKnowledgeDocumentEntity::getId))
                .stream()
                .map(doc -> new com.mywebsite.agent.dto.AgentKnowledgeDocumentDtos.ListItem(
                        doc.getId(), doc.getTitle(), doc.getUpdatedAt()))
                .toList();
    }

    @Override
    public com.mywebsite.agent.dto.AgentKnowledgeDocumentDtos.Detail getKnowledgeDocument(
            long agentId, long documentId) {
        AuthenticatedUser user = requireAgentMember(agentId);
        requireAgentUserId(user);
        AgentAiKnowledgeDocumentEntity doc = knowledgeDocumentMapper.selectOne(
                new LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity>()
                        .eq(AgentAiKnowledgeDocumentEntity::getId, documentId)
                        .eq(AgentAiKnowledgeDocumentEntity::getAgentId, agentId)
                        .eq(AgentAiKnowledgeDocumentEntity::getStatus, AgentAiKnowledgeDocumentEntity.STATUS_ACTIVE));
        if (doc == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return new com.mywebsite.agent.dto.AgentKnowledgeDocumentDtos.Detail(
                doc.getId(), doc.getTitle(), doc.getContent(), doc.getUpdatedAt());
    }

    /** 与内容列表 limit 相同：越界夹进 1..{@link AgentKnowledgeSearchRequestDto#MAX_TOP_K}，null 交给 Python 默认。 */
    private static Integer clampTopK(Integer topK) {
        if (topK == null) {
            return null;
        }
        return Math.min(Math.max(topK, 1), AgentKnowledgeSearchRequestDto.MAX_TOP_K);
    }

    private static List<AgentAiChatResponseDto.Citation> toCitations(List<AgentCitation> citations) {
        return citations.stream()
                .map(citation -> new AgentAiChatResponseDto.Citation(
                        citation.sourceId(), citation.chunkIndex(), citation.score()))
                .toList();
    }

    @Override
    public SseEmitter chatStream(long agentId, AgentAiChatRequestDto request) {
        ValidatedChat validated = validateChatAccess(agentId, request);
        long agentUserId = requireAgentUserId(validated.user());
        String message = validated.message();
        List<AgentAttachmentDto> attachments = toAgentAttachments(validated.attachments());

        ChatPreparation preparation =
                conversationService.prepare(agentId, agentUserId, request.conversationId());
        List<String> memories = memoryService.recentContents(agentId, agentUserId);
        AgentChatRequest agentRequest =
                buildAgentRequest(
                        agentId, message, attachments, validated.documents(), preparation, memories,
                        normalizeChatMode(request.mode()));

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        List<AgentAttachmentDto> stored = storedAttachments(attachments, validated);
        agentAiStreamExecutor.execute(
                () -> forwardStream(agentId, agentUserId, preparation, message, stored, agentRequest, emitter));
        return emitter;
    }

    /** 落库附件 = 图片本体 + 文档描述符（描述符无 data，历史回放自动跳过、公开 DTO 渲染为文件芯片）。 */
    private static List<AgentAttachmentDto> storedAttachments(
            List<AgentAttachmentDto> imageAttachments, ValidatedChat validated) {
        if (validated.documentDescriptors().isEmpty()) {
            return imageAttachments;
        }
        List<AgentAttachmentDto> stored = new ArrayList<>(imageAttachments);
        stored.addAll(validated.documentDescriptors());
        return List.copyOf(stored);
    }

    private AgentChatRequest buildAgentRequest(
            long agentId,
            String message,
            List<AgentAttachmentDto> attachments,
            List<AgentDocumentContext> documents,
            ChatPreparation preparation,
            List<String> memories,
            String mode) {
        Long conversationId = preparation.conversation().getId();
        List<AgentModelSelection> chain = loadModelChain(agentId, mode);
        AgentModelSelection embedding = embeddingSelection(agentId);
        return new AgentChatRequest(
                String.valueOf(agentId),
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
                knowledgeSnippets(agentId, message),
                attachments.isEmpty() ? null : attachments,
                memories == null || memories.isEmpty() ? null : memories,
                Boolean.TRUE,
                chain.size() > 1 ? List.copyOf(chain.subList(1, chain.size())) : null,
                documents == null || documents.isEmpty() ? null : documents,
                embedding == null ? null : embedding.embeddingSpec(),
                embedding == null ? null : embedding.apiKey(),
                embedding == null ? null : embedding.baseUrl());
    }

    private void persistTurn(
            long agentId,
            long agentUserId,
            ChatPreparation preparation,
            String userMessage,
            List<AgentAttachmentDto> userAttachments,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentCitation> citations,
            List<String> extractedMemories,
            String modelSpec) {
        conversationService.recordExchange(
                preparation.conversation(),
                userMessage,
                reply,
                todos,
                runMeta,
                userAttachments,
                citations,
                modelSpec);
        memoryService.storeExtracted(
                agentId, agentUserId, preparation.conversation().getId(), extractedMemories);
    }

    private static List<AgentAttachmentDto> toAgentAttachments(List<AgentAiAttachmentDto> attachments) {
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
    private List<String> knowledgeSnippets(long agentId, String query) {
        String needle = needle(query);
        if (needle.isEmpty()) {
            return null;
        }
        List<String> snippets = knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity>()
                        .eq(AgentAiKnowledgeDocumentEntity::getAgentId, agentId)
                        .eq(AgentAiKnowledgeDocumentEntity::getStatus, AgentAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                        .and(wrapper -> wrapper
                                .like(AgentAiKnowledgeDocumentEntity::getTitle, needle)
                                .or()
                                .like(AgentAiKnowledgeDocumentEntity::getContent, needle))
                        .orderByDesc(AgentAiKnowledgeDocumentEntity::getId)
                        .last("LIMIT " + KNOWLEDGE_SNIPPET_LIMIT))
                .stream()
                .map(AgentAiServiceImpl::snippet)
                .filter(StringUtils::hasText)
                .toList();
        return snippets.isEmpty() ? null : snippets;
    }

    private List<AgentKnowledgeSearchResponseDto.Hit> textKnowledgeHits(long agentId, String query) {
        String needle = needle(query);
        if (needle.isEmpty()) {
            return List.of();
        }
        return knowledgeDocumentMapper
                .selectList(new LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity>()
                        .eq(AgentAiKnowledgeDocumentEntity::getAgentId, agentId)
                        .eq(AgentAiKnowledgeDocumentEntity::getStatus, AgentAiKnowledgeDocumentEntity.STATUS_ACTIVE)
                        .and(wrapper -> wrapper
                                .like(AgentAiKnowledgeDocumentEntity::getTitle, needle)
                                .or()
                                .like(AgentAiKnowledgeDocumentEntity::getContent, needle))
                        .orderByDesc(AgentAiKnowledgeDocumentEntity::getId)
                        .last("LIMIT " + KNOWLEDGE_SNIPPET_LIMIT))
                .stream()
                .map(document -> new AgentKnowledgeSearchResponseDto.Hit(
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

    private static String snippet(AgentAiKnowledgeDocumentEntity document) {
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

    /** 聊天请求模式：空 = 不分模式（整链）；只接受 KEY / CLI，其余拒绝。 */
    static String normalizeChatMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.equals("KEY") && !value.equals("CLI")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value;
    }

    /** 入口模式归属：空值按 KEY（与 V35 列默认同口径）。 */
    private static String entryMode(AgentAiModelEntryEntity entry) {
        return StringUtils.hasText(entry.getMode())
                ? entry.getMode().trim().toUpperCase(java.util.Locale.ROOT)
                : "KEY";
    }

    @Override
    public AgentAiModelModesDto modelModes(long agentId) {
        requireAgentMember(agentId);
        List<AgentAiModelModesDto.ModeOption> options = new ArrayList<>();
        for (String mode : List.of("KEY", "CLI")) {
            List<AgentModelSelection> chain = loadModelChain(agentId, mode);
            boolean available = !chain.isEmpty();
            options.add(new AgentAiModelModesDto.ModeOption(
                    mode, available, available ? modelLabel(chain.get(0).spec()) : null));
        }
        return new AgentAiModelModesDto(options);
    }

    /** 本轮实际发给执行层的模型 spec；没有入口时不记。 */
    private static String requestedModel(AgentChatRequest request) {
        if (request.model() == null || !StringUtils.hasText(request.model().spec())) {
            return null;
        }
        return request.model().spec().trim();
    }

    /** spec（provider:model）取冒号后模型名作展示；无冒号用原文。 */
    private static String modelLabel(String spec) {
        if (spec == null) {
            return null;
        }
        int idx = spec.indexOf(':');
        return idx >= 0 && idx + 1 < spec.length() ? spec.substring(idx + 1) : spec;
    }

    /**
     * 向量索引用的凭证，与当前对话模式无关。站点 KEY 入口里第一把带 embedding spec 且能解密的 key，
     * 否则全站同样的入口。都没有时返回 null，检索改用进程嵌入模型。CLI 入口不参与。
     */
    private AgentModelSelection embeddingSelection(long agentId) {
        AgentModelSelection fromAgent = firstKeyEmbedding(enabledModelEntries(agentId));
        if (fromAgent != null) {
            return fromAgent;
        }
        return firstKeyEmbedding(enabledModelEntries(null));
    }

    private AgentModelSelection firstKeyEmbedding(List<AgentAiModelEntryEntity> entries) {
        for (AgentAiModelEntryEntity entry : entries) {
            if (!"KEY".equals(entryMode(entry)) || !StringUtils.hasText(entry.getEmbeddingSpec())) {
                continue;
            }
            AgentModelSelection selection = toModelSelection(entry);
            if (selection != null && StringUtils.hasText(selection.embeddingSpec())) {
                return selection;
            }
        }
        return null;
    }

    /**
     * 生效链（与 admin AdminAiModelEntryServiceImpl.selectionChain 同规则，两处须一致）：
     * 首元是主用（站点默认，否则全站默认），其后依次是同站其他启用入口、全站其他启用入口
     * （各组内默认在前、id 升序）。主用不存在或解密不出 key 时返回空列表，备援不得越过坏主用顶上；
     * 解密不出 key 的备援跳过。mode 非空时先按模式过滤（备援封闭在模式内）；为空时即整链。
     */
    private List<AgentModelSelection> loadModelChain(long agentId, String mode) {
        List<AgentAiModelEntryEntity> agentEntries = enabledModelEntries(agentId);
        List<AgentAiModelEntryEntity> globalEntries = enabledModelEntries(null);
        if (mode != null) {
            agentEntries = agentEntries.stream().filter(entry -> mode.equals(entryMode(entry))).toList();
            globalEntries = globalEntries.stream().filter(entry -> mode.equals(entryMode(entry))).toList();
        }
        AgentAiModelEntryEntity primary = firstDefaultEntry(agentEntries);
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
        appendModelSelections(chain, agentEntries, primary.getId());
        appendModelSelections(chain, globalEntries, primary.getId());
        return List.copyOf(chain);
    }

    /** 作用域内启用入口：默认在前，其余 id 升序。 */
    private List<AgentAiModelEntryEntity> enabledModelEntries(Long agentId) {
        LambdaQueryWrapper<AgentAiModelEntryEntity> query = new LambdaQueryWrapper<AgentAiModelEntryEntity>()
                .eq(AgentAiModelEntryEntity::getEnabled, true)
                .orderByDesc(AgentAiModelEntryEntity::getDefaultEntry)
                .orderByAsc(AgentAiModelEntryEntity::getId);
        if (agentId == null) {
            query.isNull(AgentAiModelEntryEntity::getAgentId);
        } else {
            query.eq(AgentAiModelEntryEntity::getAgentId, agentId);
        }
        List<AgentAiModelEntryEntity> entries = modelEntryMapper.selectList(query);
        return entries == null ? List.of() : entries;
    }

    private static AgentAiModelEntryEntity firstDefaultEntry(List<AgentAiModelEntryEntity> entries) {
        for (AgentAiModelEntryEntity entry : entries) {
            if (Boolean.TRUE.equals(entry.getDefaultEntry())) {
                return entry;
            }
        }
        return null;
    }

    private void appendModelSelections(
            List<AgentModelSelection> chain, List<AgentAiModelEntryEntity> entries, Long skipId) {
        for (AgentAiModelEntryEntity entry : entries) {
            if (entry.getId() != null && entry.getId().equals(skipId)) {
                continue;
            }
            AgentModelSelection selection = toModelSelection(entry);
            if (selection != null) {
                chain.add(selection);
            }
        }
    }

    private AgentModelSelection toModelSelection(AgentAiModelEntryEntity entry) {
        if (entry == null || !StringUtils.hasText(entry.getModelSpec())) {
            return null;
        }
        String apiKey = modelEntryCipher.decrypt(entry.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String baseUrl = StringUtils.hasText(entry.getBaseUrl()) ? entry.getBaseUrl().trim() : null;
        String embeddingSpec = "CLI".equals(entryMode(entry)) || !StringUtils.hasText(entry.getEmbeddingSpec())
                ? null
                : entry.getEmbeddingSpec().trim();
        return new AgentModelSelection(entry.getModelSpec().trim(), apiKey, baseUrl, embeddingSpec);
    }

    private ValidatedChat validateChatAccess(long agentId, AgentAiChatRequestDto request) {
        AuthenticatedUser user = requireAgentMember(agentId);
        if (request == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String message = request.message() == null ? "" : request.message().trim();
        List<AgentAiAttachmentDto> attachments = validateAttachments(request.attachments());
        List<AgentDocumentContext> documents = new ArrayList<>();
        List<AgentAttachmentDto> documentDescriptors = new ArrayList<>();
        validateDocuments(request.documents(), documents, documentDescriptors);
        if (!StringUtils.hasText(message) && attachments.isEmpty() && documents.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (message.length() > MAX_MESSAGE_CHARS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        if (!rateLimitService.tryAcquire(agentId, user.subject())) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
        return new ValidatedChat(user, message, attachments, List.copyOf(documents), List.copyOf(documentDescriptors));
    }

    /**
     * 文档附件校验与文本抽取：格式/大小/份数任一不合、抽取失败或抽不出文本（扫描版 PDF 等）
     * 一律 BAD_REQUEST 诚实拒绝，不把空内容送给模型。抽取文本超上限时截断并标 truncated，
     * 由 Python 在提示中注明后段已省略。原文不落库，落库的只有描述符（文件名＋类型）。
     */
    private static void validateDocuments(
            List<AgentAiDocumentDto> documents,
            List<AgentDocumentContext> extracted,
            List<AgentAttachmentDto> descriptors) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        if (documents.size() > MAX_DOCUMENTS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        for (AgentAiDocumentDto document : documents) {
            if (document == null
                    || !StringUtils.hasText(document.fileName())
                    || document.fileName().trim().length() > MAX_DOCUMENT_NAME_CHARS
                    || !StringUtils.hasText(document.data())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            String fileName = document.fileName().trim();
            String extension = documentExtension(fileName);
            String mediaType = DOCUMENT_MEDIA_TYPES.get(extension);
            if (mediaType == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(document.data());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (decoded.length == 0 || decoded.length > MAX_DOCUMENT_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            String text;
            try {
                text = ChatDocumentTextExtractor.extractText(extension, decoded);
            } catch (ChatDocumentTextExtractor.ExtractionException ex) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (!StringUtils.hasText(text)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            boolean truncated = text.length() > MAX_DOCUMENT_TEXT_CHARS;
            extracted.add(
                    new AgentDocumentContext(
                            fileName, truncated ? text.substring(0, MAX_DOCUMENT_TEXT_CHARS) : text, truncated));
            descriptors.add(AgentAttachmentDto.documentDescriptor(mediaType, fileName));
        }
    }

    private static String documentExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static List<AgentAiAttachmentDto> validateAttachments(List<AgentAiAttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        long total = 0;
        for (AgentAiAttachmentDto attachment : attachments) {
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

    private static AuthenticatedUser requireAgentMember(long agentId) {
        AuthenticatedUser user =
                AuthContext.currentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.agentIdOrNull() == null || !Objects.equals(user.agentIdOrNull(), agentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    private static long requireAgentUserId(AuthenticatedUser user) {
        return SubjectCodes.parseAgentUserId(user.subject())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    void forwardStream(
            long agentId,
            long agentUserId,
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
                        agentId,
                        agentUserId,
                        preparation,
                        userMessage,
                        userAttachments,
                        doneReply.toString(),
                        doneTodos,
                        doneMeta.get(),
                        doneCitations.get(),
                        doneMemories.get(),
                        requestedModel(agentRequest));
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
                            agentId,
                            agentUserId,
                            preparation,
                            userMessage,
                            userAttachments,
                            doneReply.toString(),
                            doneTodos,
                            doneMeta.get(),
                            doneCitations.get(),
                            doneMemories.get(),
                            requestedModel(agentRequest));
                } catch (RuntimeException recordEx) {
                    log.warn("agent ai stream reply was not stored after client disconnect", recordEx);
                }
            }
            emitter.completeWithError(ex.getCause());
        } catch (BusinessException ex) {
            sendEventQuietly(emitter, "ERROR", "{\"errorCode\":\"" + toClientErrorCode(ex) + "\"}");
            emitter.complete();
        } catch (RuntimeException ex) {
            log.warn("agent ai stream forwarding failed", ex);
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
     * 终端用户可见：回复文本、citations、错误码，以及过程事件（THINKING 思考增量、
     * TOOL_START / TOOL_RESULT 的工具名与状态、知识搜索的安全摘要）。仍不下发：
     * 工具返回正文、用量、skills、todos 与原始错误信息（仅后台观测，admin 预览原样透传）。
     * 白名单逐字段重写，返回 null = 不下发；未知事件默认不下发，防 Python 新增字段自动外泄。
     * DONE 在这里重写，不是透传 Python payload。
     */
    private String toClientEventData(String event, JsonNode data, String rawJson) {
        return switch (event) {
            case "TEXT_DELTA" -> rawJson;
            case "THINKING" -> {
                ObjectNode thinking = objectMapper.createObjectNode();
                thinking.put("delta", data.path("delta").asText(""));
                yield thinking.toString();
            }
            case "TOOL_START" -> {
                ObjectNode tool = objectMapper.createObjectNode();
                tool.put("toolName", data.path("toolName").asText(""));
                tool.put("toolCallId", data.path("toolCallId").asText(""));
                yield tool.toString();
            }
            case "TOOL_RESULT" -> {
                ObjectNode tool = objectMapper.createObjectNode();
                tool.put("toolName", data.path("toolName").asText(""));
                tool.put("toolCallId", data.path("toolCallId").asText(""));
                tool.put("ok", data.path("ok").asBoolean(false));
                JsonNode summary = data.get("summary");
                if (summary != null && summary.isObject()) {
                    tool.set("summary", clientSearchSummary(summary));
                }
                yield tool.toString();
            }
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

    /** 知识搜索摘要白名单：查询词、命中数与来源标识；不带片段正文。 */
    private ObjectNode clientSearchSummary(JsonNode summary) {
        ObjectNode safe = objectMapper.createObjectNode();
        safe.put("query", summary.path("query").asText(""));
        safe.put("hitCount", summary.path("hitCount").asInt(0));
        ArrayNode sources = safe.putArray("sources");
        for (JsonNode source : summary.path("sources")) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("sourceId", source.path("sourceId").asText(""));
            item.put("chunkIndex", source.path("chunkIndex").asInt(0));
            item.put("score", source.path("score").asDouble(0));
            sources.add(item);
        }
        return safe;
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

    private List<AgentAiChatResponseDto.Citation> extractCitations(JsonNode done) {
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
            log.warn("agent ai stream DONE carried malformed citations, ignored", ex);
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
            log.warn("agent ai stream DONE carried malformed todos, ignored", ex);
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
            log.warn("agent ai stream DONE carried malformed run metadata, ignored", ex);
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

    private record ValidatedChat(
            AuthenticatedUser user,
            String message,
            List<AgentAiAttachmentDto> attachments,
            List<AgentDocumentContext> documents,
            List<AgentAttachmentDto> documentDescriptors) {}

    private static final class ClientDisconnectedException extends RuntimeException {
        private ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
