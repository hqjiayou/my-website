package com.mywebsite.agent.service.impl;

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
import com.mywebsite.agent.dto.AgentAiAttachmentDto;
import com.mywebsite.agent.dto.AgentAiConversationDto;
import com.mywebsite.agent.dto.AgentAiConversationRenameRequestDto;
import com.mywebsite.agent.dto.AgentAiMessageDto;
import com.mywebsite.agent.dto.AgentAiSearchHitDto;
import com.mywebsite.agent.dto.AgentAiShareDto;
import com.mywebsite.agent.dto.AgentAiSharedConversationDto;
import com.mywebsite.agent.entity.AgentAiConversationEntity;
import com.mywebsite.agent.entity.AgentAiConversationShareEntity;
import com.mywebsite.agent.entity.AgentAiMessageEntity;
import com.mywebsite.agent.entity.AgentAiPromptProfileEntity;
import com.mywebsite.agent.mapper.AgentAiConversationMapper;
import com.mywebsite.agent.mapper.AgentAiConversationShareMapper;
import com.mywebsite.agent.mapper.AgentAiMessageMapper;
import com.mywebsite.agent.mapper.AgentAiPromptProfileMapper;
import com.mywebsite.agent.service.AgentAiConversationService;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AgentAiConversationServiceImpl implements AgentAiConversationService {

    /** 每次 chat 携带的最大历史条数（含 USER/ASSISTANT），控制上下文成本。 */
    private static final int HISTORY_LIMIT = 20;

    /** 历史重播带给模型的图片张数上限。按消息 id（追加顺序）从新到旧计数。 */
    private static final int HISTORY_IMAGE_LIMIT = 4;

    /** 文字为空且图片已超出重播上限时的占位，避免送出空 content。 */
    private static final String DROPPED_IMAGE_PLACEHOLDER = "[图片]";
    private static final String DROPPED_DOCUMENT_PLACEHOLDER = "[文件]";

    /** 与 {@link AgentAiConversationRenameRequestDto#TITLE_MAX_LENGTH} 同一上限：生成标题截断，重命名由 DTO 拒绝更长值。 */
    private static final int TITLE_MAX_LENGTH = AgentAiConversationRenameRequestDto.TITLE_MAX_LENGTH;
    private static final int CONVERSATION_LIST_LIMIT = 50;

    private static final Logger log = LoggerFactory.getLogger(AgentAiConversationServiceImpl.class);

    private static final SecureRandom SHARE_TOKEN_RANDOM = new SecureRandom();

    private final AgentAiConversationMapper conversationMapper;
    private final AgentAiMessageMapper messageMapper;
    private final AgentAiPromptProfileMapper promptProfileMapper;
    private final AgentAiConversationShareMapper shareMapper;
    private final ObjectMapper objectMapper;

    public AgentAiConversationServiceImpl(
            AgentAiConversationMapper conversationMapper,
            AgentAiMessageMapper messageMapper,
            AgentAiPromptProfileMapper promptProfileMapper,
            AgentAiConversationShareMapper shareMapper,
            ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.promptProfileMapper = promptProfileMapper;
        this.shareMapper = shareMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatPreparation prepare(long agentId, long agentUserId, String conversationIdOrNull) {
        AgentAiConversationEntity conversation;
        List<AgentChatHistoryMessage> history;
        if (StringUtils.hasText(conversationIdOrNull)) {
            conversation = requireOwnConversation(agentId, agentUserId, parseId(conversationIdOrNull));
            history = loadHistory(conversation.getId());
        } else {
            conversation = new AgentAiConversationEntity();
            conversation.setAgentId(agentId);
            conversation.setAgentUserId(agentUserId);
            history = List.of();
        }
        return new ChatPreparation(conversation, history, loadPromptProfile(agentId), readTodos(conversation));
    }

    @Override
    @Transactional
    public void recordExchange(
            AgentAiConversationEntity conversation,
            String userMessage,
            String reply,
            List<AgentTodoItem> todos,
            AgentRunMeta runMeta) {
        recordExchange(conversation, userMessage, reply, todos, runMeta, null);
    }

    @Override
    @Transactional
    public void recordExchange(
            AgentAiConversationEntity conversation,
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
            AgentAiConversationEntity conversation,
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
        insertMessage(conversation, AgentAiMessageEntity.ROLE_USER, userMessage, null, writeAttachments(userAttachments));
        insertMessage(
                conversation,
                AgentAiMessageEntity.ROLE_ASSISTANT,
                reply,
                writeAgentMeta(runMeta, citations),
                null);
    }

    @Override
    public List<AgentAiConversationDto> listConversations(long agentId, long agentUserId) {
        List<AgentAiConversationEntity> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<AgentAiConversationEntity>()
                        .eq(AgentAiConversationEntity::getAgentId, agentId)
                        .eq(AgentAiConversationEntity::getAgentUserId, agentUserId)
                        .orderByDesc(AgentAiConversationEntity::getLastMessageAt)
                        .last("LIMIT " + CONVERSATION_LIST_LIMIT));
        return conversations.stream().map(AgentAiConversationServiceImpl::toDto).toList();
    }

    @Override
    public List<AgentAiMessageDto> listMessages(long agentId, long agentUserId, long conversationId) {
        requireOwnConversation(agentId, agentUserId, conversationId);
        return messageMapper
                .selectList(new LambdaQueryWrapper<AgentAiMessageEntity>()
                        .eq(AgentAiMessageEntity::getConversationId, conversationId)
                        .orderByAsc(AgentAiMessageEntity::getId))
                .stream()
                .map(m -> new AgentAiMessageDto(
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
    public AgentAiConversationDto renameConversation(
            long agentId, long agentUserId, long conversationId, String title) {
        AgentAiConversationEntity conversation = requireOwnConversation(agentId, agentUserId, conversationId);
        conversation.setTitle(title.trim());
        conversationMapper.updateById(conversation);
        return toDto(conversation);
    }

    @Override
    @Transactional
    public void deleteConversation(long agentId, long agentUserId, long conversationId) {
        requireOwnConversation(agentId, agentUserId, conversationId);
        messageMapper.deleteByConversationId(conversationId);
        conversationMapper.deleteByIdPhysical(conversationId);
    }

    @Override
    @Transactional
    public void truncateMessages(long agentId, long agentUserId, long conversationId, long fromMessageId) {
        AgentAiConversationEntity conversation = requireOwnConversation(agentId, agentUserId, conversationId);
        AgentAiMessageEntity target = messageMapper.selectById(fromMessageId);
        if (target == null || !Objects.equals(target.getConversationId(), conversationId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        int deleted = messageMapper.deleteFromMessage(conversationId, fromMessageId);
        if (deleted <= 0) {
            return;
        }
        List<AgentAiMessageEntity> latest = messageMapper.selectList(
                new LambdaQueryWrapper<AgentAiMessageEntity>()
                        .eq(AgentAiMessageEntity::getConversationId, conversationId)
                        .orderByDesc(AgentAiMessageEntity::getId)
                        .last("LIMIT 1"));
        conversation.setLastMessageAt(
                latest.isEmpty() ? conversation.getCreatedAt() : latest.get(0).getCreatedAt());
        conversation.setAgentTodos(null);
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional
    public AgentAiShareDto createOrRefreshShare(long agentId, long agentUserId, long conversationId) {
        AgentAiConversationEntity conversation = requireOwnConversation(agentId, agentUserId, conversationId);
        String snapshot = buildShareSnapshot(conversation);
        AgentAiConversationShareEntity existing = findShare(conversationId);
        if (existing != null) {
            existing.setSnapshot(snapshot);
            shareMapper.updateById(existing);
            return new AgentAiShareDto(existing.getToken());
        }
        AgentAiConversationShareEntity share = new AgentAiConversationShareEntity();
        share.setConversationId(conversationId);
        share.setAgentId(agentId);
        share.setAgentUserId(agentUserId);
        share.setToken(generateShareToken());
        share.setSnapshot(snapshot);
        shareMapper.insert(share);
        return new AgentAiShareDto(share.getToken());
    }

    @Override
    public AgentAiShareDto getShare(long agentId, long agentUserId, long conversationId) {
        requireOwnConversation(agentId, agentUserId, conversationId);
        AgentAiConversationShareEntity share = findShare(conversationId);
        return share == null ? null : new AgentAiShareDto(share.getToken());
    }

    @Override
    @Transactional
    public void revokeShare(long agentId, long agentUserId, long conversationId) {
        requireOwnConversation(agentId, agentUserId, conversationId);
        shareMapper.deleteByConversationId(conversationId);
    }

    @Override
    public AgentAiSharedConversationDto getSharedConversation(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        AgentAiConversationShareEntity share = shareMapper.selectOne(
                new LambdaQueryWrapper<AgentAiConversationShareEntity>()
                        .eq(AgentAiConversationShareEntity::getToken, token.trim())
                        .last("LIMIT 1"));
        if (share == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        try {
            return objectMapper.readValue(share.getSnapshot(), AgentAiSharedConversationDto.class);
        } catch (JsonProcessingException ex) {
            log.warn("agent ai conversation share {} has malformed snapshot, treated as gone", share.getId(), ex);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    public List<AgentAiSearchHitDto> searchConversations(long agentId, long agentUserId, String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<Long> conversationIds = conversationMapper
                .selectList(new LambdaQueryWrapper<AgentAiConversationEntity>()
                        .select(AgentAiConversationEntity::getId)
                        .eq(AgentAiConversationEntity::getAgentId, agentId)
                        .eq(AgentAiConversationEntity::getAgentUserId, agentUserId))
                .stream()
                .map(AgentAiConversationEntity::getId)
                .toList();
        if (conversationIds.isEmpty()) {
            return List.of();
        }
        String pattern = likePattern(trimmed);
        List<AgentAiMessageEntity> hits = messageMapper.selectList(
                new LambdaQueryWrapper<AgentAiMessageEntity>()
                        .in(AgentAiMessageEntity::getConversationId, conversationIds)
                        .apply("content ILIKE {0} ESCAPE '\\'", pattern)
                        .orderByDesc(AgentAiMessageEntity::getId)
                        .last("LIMIT 30"));
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, String> titles = conversationMapper
                .selectBatchIds(
                        hits.stream().map(AgentAiMessageEntity::getConversationId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(
                        AgentAiConversationEntity::getId,
                        c -> c.getTitle() == null ? "" : c.getTitle(),
                        (a, b) -> a));
        return hits.stream()
                .map(m -> new AgentAiSearchHitDto(
                        m.getConversationId(),
                        titles.getOrDefault(m.getConversationId(), ""),
                        m.getId(),
                        m.getRole(),
                        snippet(m.getContent(), trimmed),
                        m.getCreatedAt()))
                .toList();
    }

    /** LIKE 模式：转义反斜杠与通配符后包裹 %，与 SQL 的 ESCAPE '\' 对应。 */
    static String likePattern(String trimmedQuery) {
        return "%"
                + trimmedQuery.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                + "%";
    }

    /** 围绕首个命中位置截取 ±30 字摘要（空白折叠）；找不到时退化为开头 60 字。 */
    private static String snippet(String content, String query) {
        if (content == null) {
            return "";
        }
        int index = content.toLowerCase().indexOf(query.toLowerCase());
        if (index < 0) {
            return content.length() > 60 ? content.substring(0, 60) + "…" : content;
        }
        int start = Math.max(0, index - 30);
        int end = Math.min(content.length(), index + query.length() + 30);
        String part = content.substring(start, end).replaceAll("\\s+", " ");
        return (start > 0 ? "…" : "") + part + (end < content.length() ? "…" : "");
    }

    private AgentAiConversationShareEntity findShare(long conversationId) {
        return shareMapper.selectOne(
                new LambdaQueryWrapper<AgentAiConversationShareEntity>()
                        .eq(AgentAiConversationShareEntity::getConversationId, conversationId)
                        .last("LIMIT 1"));
    }

    private static String generateShareToken() {
        byte[] bytes = new byte[32];
        SHARE_TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 分享快照：只存正文与附件的计数/文件名——图片数据、引用与过程信息都不进分享。 */
    private String buildShareSnapshot(AgentAiConversationEntity conversation) {
        List<AgentAiMessageEntity> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AgentAiMessageEntity>()
                        .eq(AgentAiMessageEntity::getConversationId, conversation.getId())
                        .orderByAsc(AgentAiMessageEntity::getId));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("title", conversation.getTitle());
        root.put("sharedAt", OffsetDateTime.now().toString());
        List<Map<String, Object>> items = new ArrayList<>();
        for (AgentAiMessageEntity message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            item.put("createdAt", message.getCreatedAt() == null ? null : message.getCreatedAt().toString());
            int imageCount = 0;
            List<String> documentNames = new ArrayList<>();
            List<AgentAiAttachmentDto> attachments = readPublicAttachments(message);
            if (attachments != null) {
                for (AgentAiAttachmentDto attachment : attachments) {
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

    private AgentAiConversationEntity requireOwnConversation(long agentId, long agentUserId, long conversationId) {
        AgentAiConversationEntity conversation = conversationMapper.selectById(conversationId);
        // 跨站/跨用户统一 404，不泄露归属
        if (conversation == null
                || !Objects.equals(conversation.getAgentId(), agentId)
                || !Objects.equals(conversation.getAgentUserId(), agentUserId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return conversation;
    }

    private List<AgentChatHistoryMessage> loadHistory(long conversationId) {
        List<AgentAiMessageEntity> latest = messageMapper.selectList(
                new LambdaQueryWrapper<AgentAiMessageEntity>()
                        .eq(AgentAiMessageEntity::getConversationId, conversationId)
                        .orderByDesc(AgentAiMessageEntity::getId)
                        .last("LIMIT " + HISTORY_LIMIT));
        List<AgentAiMessageEntity> chronological = latest.stream()
                .sorted(Comparator.comparing(AgentAiMessageEntity::getId))
                .toList();
        HistoryReplay replay = replayAttachments(chronological);
        return chronological.stream().map(message -> toHistoryMessage(message, replay)).toList();
    }

    private record HistoryReplay(Map<Long, List<AgentAttachmentDto>> attachments, Set<Long> documentTurns) {}

    /**
     * 全历史最多保留最近 {@link #HISTORY_IMAGE_LIMIT} 张。同一条消息放不下时保留列表前缀，其余丢掉。
     * 文档描述符不回放，只记下轮次 id，供无文字的文档轮在历史里用文件占位符保轮。
     */
    private HistoryReplay replayAttachments(List<AgentAiMessageEntity> chronological) {
        Map<Long, List<AgentAttachmentDto>> replay = new HashMap<>();
        Set<Long> documentTurns = new HashSet<>();
        int remaining = HISTORY_IMAGE_LIMIT;
        for (int i = chronological.size() - 1; i >= 0; i--) {
            AgentAiMessageEntity message = chronological.get(i);
            if (!AgentAiMessageEntity.ROLE_USER.equals(message.getRole())) {
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

    private AgentChatHistoryMessage toHistoryMessage(AgentAiMessageEntity message, HistoryReplay replay) {
        String content = message.getContent() == null ? "" : message.getContent();
        if (!AgentAiMessageEntity.ROLE_USER.equals(message.getRole())
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

    private StoredAttachments readStoredAttachments(AgentAiMessageEntity message) {
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
            log.warn("agent ai message {} has malformed attachments, ignored", message.getId(), ex);
            return new StoredAttachments(true, List.of(), false);
        }
    }

    private List<AgentAiAttachmentDto> readPublicAttachments(AgentAiMessageEntity message) {
        if (!AgentAiMessageEntity.ROLE_USER.equals(message.getRole()) || !StringUtils.hasText(message.getAttachments())) {
            return null;
        }
        try {
            AgentAiAttachmentDto[] parsed = objectMapper.readValue(message.getAttachments(), AgentAiAttachmentDto[].class);
            if (parsed == null || parsed.length == 0) {
                return null;
            }
            List<AgentAiAttachmentDto> items = new ArrayList<>();
            for (AgentAiAttachmentDto item : parsed) {
                if (item != null) {
                    items.add(item);
                }
            }
            return items.isEmpty() ? null : List.copyOf(items);
        } catch (JsonProcessingException ex) {
            log.warn("agent ai message {} has malformed attachments, ignored", message.getId(), ex);
            return null;
        }
    }

    private AgentPromptProfile loadPromptProfile(long agentId) {
        AgentAiPromptProfileEntity profile = promptProfileMapper.selectOne(
                new LambdaQueryWrapper<AgentAiPromptProfileEntity>()
                        .eq(AgentAiPromptProfileEntity::getAgentId, agentId)
                        .last("LIMIT 1"));
        if (profile == null) {
            return null;
        }
        Double temperature = profile.getTemperature() == null ? null : profile.getTemperature().doubleValue();
        return new AgentPromptProfile(profile.getSystemPrompt(), temperature);
    }

    private List<AgentTodoItem> readTodos(AgentAiConversationEntity conversation) {
        String raw = conversation.getAgentTodos();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(raw, AgentTodoItem[].class));
        } catch (JsonProcessingException ex) {
            // 脏快照不阻断聊天：按无 todo 继续，下一轮成功后被覆盖
            log.warn("agent ai conversation {} has malformed agent_todos, ignored", conversation.getId(), ex);
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
    private List<AgentAiMessageDto.Citation> readCitations(AgentAiMessageEntity message) {
        if (!StringUtils.hasText(message.getAgentMeta())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(message.getAgentMeta());
            if (root == null || !root.isObject()) {
                log.warn("agent ai message {} has malformed agent_meta citations, ignored", message.getId());
                return null;
            }
            JsonNode citations = root.get("citations");
            if (citations == null || citations.isNull()) {
                return null;
            }
            if (!citations.isArray()) {
                log.warn("agent ai message {} has malformed agent_meta citations, ignored", message.getId());
                return null;
            }
            AgentAiMessageDto.Citation[] parsed =
                    objectMapper.treeToValue(citations, AgentAiMessageDto.Citation[].class);
            if (parsed == null || parsed.length == 0) {
                return null;
            }
            List<AgentAiMessageDto.Citation> items = new ArrayList<>();
            for (AgentAiMessageDto.Citation item : parsed) {
                if (item != null) {
                    items.add(item);
                }
            }
            return items.isEmpty() ? null : List.copyOf(items);
        } catch (Exception ex) {
            log.warn("agent ai message {} has malformed agent_meta citations, ignored", message.getId(), ex);
            return null;
        }
    }

    /**
     * 过程摘要回放：读 agent_meta 顶层的 tools / searches（与实时事件同一白名单，
     * 只有工具名与成败、查询词与命中数）。两者皆空、缺失或损坏时返回 null，不让消息列表失败。
     * 旧消息没有 searches 时只回放 tools。
     */
    private AgentAiMessageDto.Process readProcess(AgentAiMessageEntity message) {
        if (!StringUtils.hasText(message.getAgentMeta())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(message.getAgentMeta());
            if (root == null || !root.isObject()) {
                return null;
            }
            List<AgentAiMessageDto.Process.Tool> tools = new ArrayList<>();
            JsonNode toolsNode = root.get("tools");
            if (toolsNode != null && toolsNode.isArray()) {
                for (JsonNode item : toolsNode) {
                    if (item != null && item.isObject() && item.hasNonNull("name")) {
                        tools.add(new AgentAiMessageDto.Process.Tool(
                                item.path("name").asText(""), item.path("ok").asBoolean(false)));
                    }
                }
            }
            List<AgentAiMessageDto.Process.Search> searches = new ArrayList<>();
            JsonNode searchesNode = root.get("searches");
            if (searchesNode != null && searchesNode.isArray()) {
                for (JsonNode item : searchesNode) {
                    if (item != null && item.isObject()) {
                        searches.add(new AgentAiMessageDto.Process.Search(
                                item.path("query").asText(""), item.path("hitCount").asInt(0)));
                    }
                }
            }
            if (tools.isEmpty() && searches.isEmpty()) {
                return null;
            }
            return new AgentAiMessageDto.Process(List.copyOf(tools), List.copyOf(searches));
        } catch (Exception ex) {
            log.warn("agent ai message {} has malformed agent_meta process, ignored", message.getId(), ex);
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
            AgentAiConversationEntity conversation,
            String role,
            String content,
            String agentMeta,
            String attachments) {
        AgentAiMessageEntity message = new AgentAiMessageEntity();
        message.setConversationId(conversation.getId());
        message.setAgentId(conversation.getAgentId());
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

    private static AgentAiConversationDto toDto(AgentAiConversationEntity conversation) {
        return new AgentAiConversationDto(
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
