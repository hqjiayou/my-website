package com.mywebsite.site.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mywebsite.agent.dto.AgentAttachmentDto;
import com.mywebsite.agent.dto.AgentChatHistoryMessage;
import com.mywebsite.agent.dto.AgentCitation;
import com.mywebsite.agent.dto.AgentPromptProfile;
import com.mywebsite.agent.dto.AgentRunMeta;
import com.mywebsite.agent.dto.AgentTodoItem;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.site.dto.SiteAiAttachmentDto;
import com.mywebsite.site.dto.SiteAiConversationDto;
import com.mywebsite.site.dto.SiteAiConversationRenameRequestDto;
import com.mywebsite.site.dto.SiteAiMessageDto;
import com.mywebsite.site.dto.SiteAiShareDto;
import com.mywebsite.site.dto.SiteAiSharedConversationDto;
import com.mywebsite.site.entity.SiteAiConversationEntity;
import com.mywebsite.site.entity.SiteAiConversationShareEntity;
import com.mywebsite.site.entity.SiteAiMessageEntity;
import com.mywebsite.site.entity.SiteAiPromptProfileEntity;
import com.mywebsite.site.mapper.SiteAiConversationMapper;
import com.mywebsite.site.mapper.SiteAiConversationShareMapper;
import com.mywebsite.site.mapper.SiteAiMessageMapper;
import com.mywebsite.site.mapper.SiteAiPromptProfileMapper;
import com.mywebsite.site.service.SiteAiConversationService;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SiteAiConversationServiceImpl implements SiteAiConversationService {

    /** 每次 chat 携带的最大历史条数（含 USER/ASSISTANT），控制上下文成本。 */
    private static final int HISTORY_LIMIT = 20;

    /** 历史重播带给模型的图片张数上限。按消息 id（追加顺序）从新到旧计数。 */
    private static final int HISTORY_IMAGE_LIMIT = 4;

    /** 文字为空且图片已超出重播上限时的占位，避免送出空 content。 */
    private static final String DROPPED_IMAGE_PLACEHOLDER = "[图片]";
    private static final String DROPPED_DOCUMENT_PLACEHOLDER = "[文件]";

    /** 与 {@link SiteAiConversationRenameRequestDto#TITLE_MAX_LENGTH} 同一上限：生成标题截断，重命名由 DTO 拒绝更长值。 */
    private static final int TITLE_MAX_LENGTH = SiteAiConversationRenameRequestDto.TITLE_MAX_LENGTH;
    private static final int CONVERSATION_LIST_LIMIT = 50;

    private static final Logger log = LoggerFactory.getLogger(SiteAiConversationServiceImpl.class);

    private static final SecureRandom SHARE_TOKEN_RANDOM = new SecureRandom();

    private final SiteAiConversationMapper conversationMapper;
    private final SiteAiMessageMapper messageMapper;
    private final SiteAiPromptProfileMapper promptProfileMapper;
    private final SiteAiConversationShareMapper shareMapper;
    private final ObjectMapper objectMapper;

    public SiteAiConversationServiceImpl(
            SiteAiConversationMapper conversationMapper,
            SiteAiMessageMapper messageMapper,
            SiteAiPromptProfileMapper promptProfileMapper,
            SiteAiConversationShareMapper shareMapper,
            ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.promptProfileMapper = promptProfileMapper;
        this.shareMapper = shareMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatPreparation prepare(long siteId, long siteUserId, String conversationIdOrNull) {
        SiteAiConversationEntity conversation;
        List<AgentChatHistoryMessage> history;
        if (StringUtils.hasText(conversationIdOrNull)) {
            conversation = requireOwnConversation(siteId, siteUserId, parseId(conversationIdOrNull));
            history = loadHistory(conversation.getId());
        } else {
            conversation = new SiteAiConversationEntity();
            conversation.setSiteId(siteId);
            conversation.setSiteUserId(siteUserId);
            history = List.of();
        }
        return new ChatPreparation(conversation, history, loadPromptProfile(siteId), readTodos(conversation));
    }

    @Override
    @Transactional
    public void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta) {
        recordExchange(conversation, userMessage, reply, todos, runMeta, null);
    }

    @Override
    @Transactional
    public void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentAttachmentDto> userAttachments) {
        recordExchange(conversation, userMessage, reply, todos, runMeta, userAttachments, null);
    }

    @Override
    @Transactional
    public void recordExchange(
            SiteAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta,
            List<AgentAttachmentDto> userAttachments,
            List<AgentCitation> citations) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        conversation.setAgentTodos(writeTodos(todos));
        if (conversation.getId() == null) {
            conversation.setTitle(truncateTitle(userMessage));
            conversation.setLastMessageAt(now);
            conversationMapper.insert(conversation);
        } else {
            conversation.setLastMessageAt(now);
            conversationMapper.updateById(conversation);
        }
        insertMessage(conversation, SiteAiMessageEntity.ROLE_USER, userMessage, null, writeAttachments(userAttachments));
        insertMessage(
                conversation,
                SiteAiMessageEntity.ROLE_ASSISTANT,
                reply,
                writeAgentMeta(runMeta, citations),
                null);
    }

    @Override
    public List<SiteAiConversationDto> listConversations(long siteId, long siteUserId) {
        List<SiteAiConversationEntity> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<SiteAiConversationEntity>()
                        .eq(SiteAiConversationEntity::getSiteId, siteId)
                        .eq(SiteAiConversationEntity::getSiteUserId, siteUserId)
                        .orderByDesc(SiteAiConversationEntity::getLastMessageAt)
                        .last("LIMIT " + CONVERSATION_LIST_LIMIT));
        return conversations.stream().map(SiteAiConversationServiceImpl::toDto).toList();
    }

    @Override
    public List<SiteAiMessageDto> listMessages(long siteId, long siteUserId, long conversationId) {
        requireOwnConversation(siteId, siteUserId, conversationId);
        return messageMapper
                .selectList(new LambdaQueryWrapper<SiteAiMessageEntity>()
                        .eq(SiteAiMessageEntity::getConversationId, conversationId)
                        .orderByAsc(SiteAiMessageEntity::getId))
                .stream()
                .map(m -> new SiteAiMessageDto(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getCreatedAt(),
                        readPublicAttachments(m),
                        readCitations(m),
                        readProcess(m)))
                .toList();
    }

    @Override
    public SiteAiConversationDto renameConversation(
            long siteId, long siteUserId, long conversationId, String title) {
        SiteAiConversationEntity conversation = requireOwnConversation(siteId, siteUserId, conversationId);
        conversation.setTitle(title.trim());
        conversationMapper.updateById(conversation);
        return toDto(conversation);
    }

    @Override
    @Transactional
    public void deleteConversation(long siteId, long siteUserId, long conversationId) {
        requireOwnConversation(siteId, siteUserId, conversationId);
        messageMapper.deleteByConversationId(conversationId);
        conversationMapper.deleteByIdPhysical(conversationId);
    }

    @Override
    @Transactional
    public void truncateMessages(long siteId, long siteUserId, long conversationId, long fromMessageId) {
        SiteAiConversationEntity conversation = requireOwnConversation(siteId, siteUserId, conversationId);
        SiteAiMessageEntity target = messageMapper.selectById(fromMessageId);
        if (target == null || !Objects.equals(target.getConversationId(), conversationId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        int deleted = messageMapper.deleteFromMessage(conversationId, fromMessageId);
        if (deleted <= 0) {
            return;
        }
        List<SiteAiMessageEntity> latest = messageMapper.selectList(
                new LambdaQueryWrapper<SiteAiMessageEntity>()
                        .eq(SiteAiMessageEntity::getConversationId, conversationId)
                        .orderByDesc(SiteAiMessageEntity::getId)
                        .last("LIMIT 1"));
        conversation.setLastMessageAt(
                latest.isEmpty() ? conversation.getCreatedAt() : latest.get(0).getCreatedAt());
        conversation.setAgentTodos(null);
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional
    public SiteAiShareDto createOrRefreshShare(long siteId, long siteUserId, long conversationId) {
        SiteAiConversationEntity conversation = requireOwnConversation(siteId, siteUserId, conversationId);
        String snapshot = buildShareSnapshot(conversation);
        SiteAiConversationShareEntity existing = findShare(conversationId);
        if (existing != null) {
            existing.setSnapshot(snapshot);
            shareMapper.updateById(existing);
            return new SiteAiShareDto(existing.getToken());
        }
        SiteAiConversationShareEntity share = new SiteAiConversationShareEntity();
        share.setConversationId(conversationId);
        share.setSiteId(siteId);
        share.setSiteUserId(siteUserId);
        share.setToken(generateShareToken());
        share.setSnapshot(snapshot);
        shareMapper.insert(share);
        return new SiteAiShareDto(share.getToken());
    }

    @Override
    public SiteAiShareDto getShare(long siteId, long siteUserId, long conversationId) {
        requireOwnConversation(siteId, siteUserId, conversationId);
        SiteAiConversationShareEntity share = findShare(conversationId);
        return share == null ? null : new SiteAiShareDto(share.getToken());
    }

    @Override
    @Transactional
    public void revokeShare(long siteId, long siteUserId, long conversationId) {
        requireOwnConversation(siteId, siteUserId, conversationId);
        shareMapper.deleteByConversationId(conversationId);
    }

    @Override
    public SiteAiSharedConversationDto getSharedConversation(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        SiteAiConversationShareEntity share = shareMapper.selectOne(
                new LambdaQueryWrapper<SiteAiConversationShareEntity>()
                        .eq(SiteAiConversationShareEntity::getToken, token.trim())
                        .last("LIMIT 1"));
        if (share == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            return objectMapper.readValue(share.getSnapshot(), SiteAiSharedConversationDto.class);
        } catch (JsonProcessingException ex) {
            log.warn("site ai conversation share {} has malformed snapshot, treated as gone", share.getId(), ex);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    private SiteAiConversationShareEntity findShare(long conversationId) {
        return shareMapper.selectOne(
                new LambdaQueryWrapper<SiteAiConversationShareEntity>()
                        .eq(SiteAiConversationShareEntity::getConversationId, conversationId)
                        .last("LIMIT 1"));
    }

    private static String generateShareToken() {
        byte[] bytes = new byte[32];
        SHARE_TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 分享快照：只存正文与附件的计数/文件名——图片数据、引用与过程信息都不进分享。 */
    private String buildShareSnapshot(SiteAiConversationEntity conversation) {
        List<SiteAiMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<SiteAiMessageEntity>()
                        .eq(SiteAiMessageEntity::getConversationId, conversation.getId())
                        .orderByAsc(SiteAiMessageEntity::getId));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", conversation.getTitle());
        root.put("sharedAt", OffsetDateTime.now().toString());
        List<Map<String, Object>> items = new ArrayList<>();
        for (SiteAiMessageEntity message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            item.put("createdAt", message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
            int imageCount = 0;
            List<String> documentNames = new ArrayList<>();
            List<SiteAiAttachmentDto> attachments = readPublicAttachments(message);
            if (attachments != null) {
                for (SiteAiAttachmentDto attachment : attachments) {
                    if ("DOCUMENT".equals(attachment.kind())) {
                        if (attachment.fileName() != null) {
                            documentNames.add(attachment.fileName());
                        }
                    } else {
                        imageCount += 1;
                    }
                }
            }
            item.put("imageCount", imageCount);
            item.put("documentNames", documentNames);
            items.add(item);
        }
        root.put("messages", items);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    private SiteAiConversationEntity requireOwnConversation(long siteId, long siteUserId, long conversationId) {
        SiteAiConversationEntity conversation = conversationMapper.selectById(conversationId);
        // 跨站/跨用户统一 404，不泄露归属
        if (conversation == null
                || !Objects.equals(conversation.getSiteId(), siteId)
                || !Objects.equals(conversation.getSiteUserId(), siteUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return conversation;
    }

    private List<AgentChatHistoryMessage> loadHistory(long conversationId) {
        List<SiteAiMessageEntity> latest = messageMapper.selectList(
                new LambdaQueryWrapper<SiteAiMessageEntity>()
                        .eq(SiteAiMessageEntity::getConversationId, conversationId)
                        .orderByDesc(SiteAiMessageEntity::getId)
                        .last("LIMIT " + HISTORY_LIMIT));
        List<SiteAiMessageEntity> chronological = latest.stream()
                .sorted(Comparator.comparing(SiteAiMessageEntity::getId))
                .toList();
        HistoryReplay replay = replayAttachments(chronological);
        return chronological.stream().map(message -> toHistoryMessage(message, replay)).toList();
    }

    private record HistoryReplay(Map<Long, List<AgentAttachmentDto>> attachments, Set<Long> documentTurns) {}

    /**
     * 全历史最多保留最近 {@link #HISTORY_IMAGE_LIMIT} 张。同一条消息放不下时保留列表前缀，其余丢掉。
     * 文档描述符不回放，只记下轮次 id，供无文字的文档轮在历史里用文件占位符保轮。
     */
    private HistoryReplay replayAttachments(List<SiteAiMessageEntity> chronological) {
        Map<Long, List<AgentAttachmentDto>> replay = new HashMap<>();
        Set<Long> documentTurns = new HashSet<>();
        int remaining = HISTORY_IMAGE_LIMIT;
        for (int i = chronological.size() - 1; i >= 0; i--) {
            SiteAiMessageEntity message = chronological.get(i);
            if (!SiteAiMessageEntity.ROLE_USER.equals(message.getRole())) {
                continue;
            }
            StoredAttachments stored = readStoredAttachments(message);
            if (!stored.present()) {
                continue;
            }
            if (stored.hasDocuments()) {
                documentTurns.add(message.getId());
            }
            if (stored.items().isEmpty() || remaining <= 0) {
                replay.put(message.getId(), List.of());
                continue;
            }
            if (stored.items().size() <= remaining) {
                replay.put(message.getId(), stored.items());
                remaining -= stored.items().size();
            } else {
                replay.put(message.getId(), List.copyOf(stored.items().subList(0, remaining)));
                remaining = 0;
            }
        }
        return new HistoryReplay(replay, documentTurns);
    }

    private AgentChatHistoryMessage toHistoryMessage(SiteAiMessageEntity message, HistoryReplay replay) {
        String content = message.getContent() == null ? "" : message.getContent();
        if (!SiteAiMessageEntity.ROLE_USER.equals(message.getRole())
                || !replay.attachments().containsKey(message.getId())) {
            return new AgentChatHistoryMessage(message.getRole(), content);
        }
        List<AgentAttachmentDto> kept = replay.attachments().get(message.getId());
        if (kept.isEmpty()) {
            if (!StringUtils.hasText(content)) {
                content = replay.documentTurns().contains(message.getId())
                        ? DROPPED_DOCUMENT_PLACEHOLDER
                        : DROPPED_IMAGE_PLACEHOLDER;
            }
            return new AgentChatHistoryMessage(message.getRole(), content);
        }
        return new AgentChatHistoryMessage(message.getRole(), content, kept);
    }

    private StoredAttachments readStoredAttachments(SiteAiMessageEntity message) {
        String raw = message.getAttachments();
        if (!StringUtils.hasText(raw)) {
            return StoredAttachments.none();
        }
        try {
            AgentAttachmentDto[] parsed = objectMapper.readValue(raw, AgentAttachmentDto[].class);
            List<AgentAttachmentDto> items = new ArrayList<>();
            boolean hasDocuments = false;
            if (parsed != null) {
                for (AgentAttachmentDto item : parsed) {
                    if (item == null) {
                        continue;
                    }
                    if (AgentAttachmentDto.KIND_DOCUMENT.equals(item.kind())) {
                        hasDocuments = true;
                        continue;
                    }
                    if (StringUtils.hasText(item.mediaType()) && StringUtils.hasText(item.data())) {
                        items.add(item);
                    }
                }
            }
            return new StoredAttachments(true, List.copyOf(items), hasDocuments);
        } catch (JsonProcessingException ex) {
            log.warn("site ai message {} has malformed attachments, ignored", message.getId(), ex);
            return new StoredAttachments(true, List.of(), false);
        }
    }

    private List<SiteAiAttachmentDto> readPublicAttachments(SiteAiMessageEntity message) {
        if (!SiteAiMessageEntity.ROLE_USER.equals(message.getRole()) || !StringUtils.hasText(message.getAttachments())) {
            return null;
        }
        try {
            SiteAiAttachmentDto[] parsed = objectMapper.readValue(message.getAttachments(), SiteAiAttachmentDto[].class);
            if (parsed == null || parsed.length == 0) {
                return null;
            }
            List<SiteAiAttachmentDto> items = new ArrayList<>();
            for (SiteAiAttachmentDto item : parsed) {
                if (item != null) {
                    items.add(item);
                }
            }
            return items.isEmpty() ? null : List.copyOf(items);
        } catch (JsonProcessingException ex) {
            log.warn("site ai message {} has malformed attachments, ignored", message.getId(), ex);
            return null;
        }
    }

    private AgentPromptProfile loadPromptProfile(long siteId) {
        SiteAiPromptProfileEntity profile = promptProfileMapper.selectOne(
                new LambdaQueryWrapper<SiteAiPromptProfileEntity>()
                        .eq(SiteAiPromptProfileEntity::getSiteId, siteId)
                        .last("LIMIT 1"));
        if (profile == null) {
            return null;
        }
        Double temperature = profile.getTemperature() == null ? null : profile.getTemperature().doubleValue();
        return new AgentPromptProfile(profile.getSystemPrompt(), temperature);
    }

    private List<AgentTodoItem> readTodos(SiteAiConversationEntity conversation) {
        String raw = conversation.getAgentTodos();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(raw, AgentTodoItem[].class));
        } catch (JsonProcessingException ex) {
            // 脏快照不阻断聊天：按无 todo 继续，下一轮成功后被覆盖
            log.warn("site ai conversation {} has malformed agent_todos, ignored", conversation.getId(), ex);
            return List.of();
        }
    }

    private String writeTodos(List<AgentTodoItem> todos) {
        if (todos == null || todos.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(todos);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize agent todos", ex);
        }
    }

    private String writeAttachments(List<AgentAttachmentDto> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize chat attachments", ex);
        }
    }

    /**
     * citations 为空时原样返回 {@link #writeRunMeta}。非空时写入 {@code citations} 字段；
     * 已有 run meta 的键保留，观测数据仍可被按原结构读取。
     */
    private String writeAgentMeta(AgentRunMeta runMeta, List<AgentCitation> citations) {
        String meta = writeRunMeta(runMeta);
        if (citations == null || citations.isEmpty()) {
            return meta;
        }
        try {
            ObjectNode node =
                    meta == null ? objectMapper.createObjectNode() : objectMapper.readValue(meta, ObjectNode.class);
            node.set("citations", objectMapper.valueToTree(citations));
            return objectMapper.writeValueAsString(node);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("Failed to serialize citations into agent meta, kept previous payload", ex);
            return meta;
        }
    }

    /** 无 citations、空数组或 agentMeta 损坏时返回 null，不让消息列表失败。 */
    private List<SiteAiMessageDto.Citation> readCitations(SiteAiMessageEntity message) {
        if (!StringUtils.hasText(message.getAgentMeta())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(message.getAgentMeta());
            if (root == null || !root.isObject()) {
                log.warn("site ai message {} has malformed agent_meta citations, ignored", message.getId());
                return null;
            }
            JsonNode citations = root.get("citations");
            if (citations == null || citations.isNull()) {
                return null;
            }
            if (!citations.isArray()) {
                log.warn("site ai message {} has malformed agent_meta citations, ignored", message.getId());
                return null;
            }
            SiteAiMessageDto.Citation[] parsed =
                    objectMapper.treeToValue(citations, SiteAiMessageDto.Citation[].class);
            if (parsed == null || parsed.length == 0) {
                return null;
            }
            List<SiteAiMessageDto.Citation> items = new ArrayList<>();
            for (SiteAiMessageDto.Citation item : parsed) {
                if (item != null) {
                    items.add(item);
                }
            }
            return items.isEmpty() ? null : List.copyOf(items);
        } catch (Exception ex) {
            log.warn("site ai message {} has malformed agent_meta citations, ignored", message.getId(), ex);
            return null;
        }
    }

    /**
     * 过程摘要回放：读 agent_meta 顶层的 tools / searches（与实时事件同一白名单，
     * 只有工具名与成败、查询词与命中数）。两者皆空、缺失或损坏时返回 null，不让消息列表失败。
     * 旧消息没有 searches 时只回放 tools。
     */
    private SiteAiMessageDto.Process readProcess(SiteAiMessageEntity message) {
        if (!StringUtils.hasText(message.getAgentMeta())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(message.getAgentMeta());
            if (root == null || !root.isObject()) {
                return null;
            }
            List<SiteAiMessageDto.Process.Tool> tools = new ArrayList<>();
            JsonNode toolsNode = root.get("tools");
            if (toolsNode != null && toolsNode.isArray()) {
                for (JsonNode item : toolsNode) {
                    if (item != null && item.isObject() && item.hasNonNull("name")) {
                        tools.add(new SiteAiMessageDto.Process.Tool(
                                item.path("name").asText(""), item.path("ok").asBoolean(false)));
                    }
                }
            }
            List<SiteAiMessageDto.Process.Search> searches = new ArrayList<>();
            JsonNode searchesNode = root.get("searches");
            if (searchesNode != null && searchesNode.isArray()) {
                for (JsonNode item : searchesNode) {
                    if (item != null && item.isObject()) {
                        searches.add(new SiteAiMessageDto.Process.Search(
                                item.path("query").asText(""), item.path("hitCount").asInt(0)));
                    }
                }
            }
            if (tools.isEmpty() && searches.isEmpty()) {
                return null;
            }
            return new SiteAiMessageDto.Process(List.copyOf(tools), List.copyOf(searches));
        } catch (Exception ex) {
            log.warn("site ai message {} has malformed agent_meta process, ignored", message.getId(), ex);
            return null;
        }
    }

    /** 观测数据序列化失败不得让已成功的一轮回滚：记日志后存 NULL。 */
    private String writeRunMeta(AgentRunMeta runMeta) {
        if (runMeta == null || runMeta.equals(AgentRunMeta.EMPTY)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(runMeta);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize agent run meta, stored as null", ex);
            return null;
        }
    }

    private void insertMessage(
            SiteAiConversationEntity conversation,
            String role,
            String content,
            String agentMeta,
            String attachments) {
        SiteAiMessageEntity message = new SiteAiMessageEntity();
        message.setConversationId(conversation.getId());
        message.setSiteId(conversation.getSiteId());
        message.setRole(role);
        message.setContent(content);
        message.setAgentMeta(agentMeta);
        message.setAttachments(attachments);
        messageMapper.insert(message);
    }

    /** items 只含可回放的图片；hasDocuments 标记该轮带过文档描述符（文档本身不回放）。 */
    private record StoredAttachments(boolean present, List<AgentAttachmentDto> items, boolean hasDocuments) {
        private static StoredAttachments none() {
            return new StoredAttachments(false, List.of(), false);
        }
    }

    private static SiteAiConversationDto toDto(SiteAiConversationEntity conversation) {
        return new SiteAiConversationDto(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt());
    }

    private static long parseId(String conversationId) {
        try {
            return Long.parseLong(conversationId.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private static String truncateTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= TITLE_MAX_LENGTH ? trimmed : trimmed.substring(0, TITLE_MAX_LENGTH);
    }
}
