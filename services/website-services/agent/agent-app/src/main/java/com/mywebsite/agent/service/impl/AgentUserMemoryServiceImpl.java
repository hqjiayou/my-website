package com.mywebsite.agent.service.impl;

import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.agent.dto.AgentUserMemoryDto;
import com.mywebsite.agent.entity.AgentUserMemoryEntity;
import com.mywebsite.agent.mapper.AgentUserMemoryMapper;
import com.mywebsite.agent.service.AgentUserMemoryService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentUserMemoryServiceImpl implements AgentUserMemoryService {

    public static final int INJECT_LIMIT = 20;
    public static final int STORE_LIMIT = 50;
    static final int CONTENT_MAX_CHARS = 500;

    private final AgentUserMemoryMapper memoryMapper;

    public AgentUserMemoryServiceImpl(AgentUserMemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public List<String> recentContents(long agentId, long userId) {
        List<String> newestFirst = memoryMapper.selectRecentContents(agentId, userId, INJECT_LIMIT);
        if (newestFirst == null || newestFirst.isEmpty()) {
            return List.of();
        }
        List<String> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    @Override
    @Transactional
    public void storeExtracted(long agentId, long userId, Long conversationId, List<String> extracted) {
        LinkedHashSet<String> incoming = normalize(extracted);
        if (incoming.isEmpty()) {
            return;
        }
        List<String> stored = memoryMapper.selectActiveContents(agentId, userId);
        Set<String> known = new HashSet<>(stored == null ? List.of() : stored);
        for (String content : incoming) {
            if (!known.add(content)) {
                continue;
            }
            while (memoryMapper.countActive(agentId, userId) >= STORE_LIMIT) {
                if (!evictOldest(agentId, userId)) {
                    break;
                }
            }
            if (memoryMapper.countActive(agentId, userId) >= STORE_LIMIT) {
                continue;
            }
            AgentUserMemoryEntity entity = new AgentUserMemoryEntity();
            entity.setAgentId(agentId);
            entity.setUserId(userId);
            entity.setContent(content);
            entity.setSourceConversationId(conversationId);
            memoryMapper.insert(entity);
        }
    }

    @Override
    public List<AgentUserMemoryDto> listOwn(long agentId, long userId) {
        List<AgentUserMemoryEntity> rows = memoryMapper.selectOwn(agentId, userId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new AgentUserMemoryDto(row.getId(), row.getContent(), row.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteOwn(long agentId, long userId, long memoryId) {
        AgentUserMemoryEntity entity = memoryMapper.selectById(memoryId);
        if (entity == null
                || !Objects.equals(entity.getAgentId(), agentId)
                || !Objects.equals(entity.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!softDelete(entity)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void clearOwn(long agentId, long userId) {
        List<AgentUserMemoryEntity> rows = memoryMapper.selectOwn(agentId, userId);
        if (rows == null) {
            return;
        }
        for (AgentUserMemoryEntity row : rows) {
            softDelete(row);
        }
    }

    private boolean evictOldest(long agentId, long userId) {
        Long id = memoryMapper.selectOldestId(agentId, userId);
        if (id == null) {
            return false;
        }
        AgentUserMemoryEntity entity = memoryMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        return softDelete(entity);
    }

    private boolean softDelete(AgentUserMemoryEntity entity) {
        entity.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        memoryMapper.updateById(entity);
        return memoryMapper.deleteById(entity.getId()) > 0;
    }

    private static LinkedHashSet<String> normalize(List<String> extracted) {
        LinkedHashSet<String> incoming = new LinkedHashSet<>();
        if (extracted == null) {
            return incoming;
        }
        for (String raw : extracted) {
            if (raw == null) {
                continue;
            }
            String text = raw.trim().replaceAll("\\s+", " ");
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > CONTENT_MAX_CHARS) {
                text = text.substring(0, CONTENT_MAX_CHARS);
            }
            incoming.add(text);
        }
        return incoming;
    }
}
