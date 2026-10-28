package com.mywebsite.site.service.impl;

import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.site.dto.SiteUserMemoryDto;
import com.mywebsite.site.entity.SiteUserMemoryEntity;
import com.mywebsite.site.mapper.SiteUserMemoryMapper;
import com.mywebsite.site.service.SiteUserMemoryService;
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
public class SiteUserMemoryServiceImpl implements SiteUserMemoryService {

    public static final int INJECT_LIMIT = 20;
    public static final int STORE_LIMIT = 50;
    static final int CONTENT_MAX_CHARS = 500;

    private final SiteUserMemoryMapper memoryMapper;

    public SiteUserMemoryServiceImpl(SiteUserMemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public List<String> recentContents(long siteId, long userId) {
        List<String> newestFirst = memoryMapper.selectRecentContents(siteId, userId, INJECT_LIMIT);
        if (newestFirst == null || newestFirst.isEmpty()) {
            return List.of();
        }
        List<String> chronological = new ArrayList<>(newestFirst);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    @Override
    @Transactional
    public void storeExtracted(long siteId, long userId, Long conversationId, List<String> extracted) {
        LinkedHashSet<String> incoming = normalize(extracted);
        if (incoming.isEmpty()) {
            return;
        }
        List<String> stored = memoryMapper.selectActiveContents(siteId, userId);
        Set<String> known = new HashSet<>(stored == null ? List.of() : stored);
        for (String content : incoming) {
            if (!known.add(content)) {
                continue;
            }
            while (memoryMapper.countActive(siteId, userId) >= STORE_LIMIT) {
                if (!evictOldest(siteId, userId)) {
                    break;
                }
            }
            if (memoryMapper.countActive(siteId, userId) >= STORE_LIMIT) {
                continue;
            }
            SiteUserMemoryEntity entity = new SiteUserMemoryEntity();
            entity.setSiteId(siteId);
            entity.setUserId(userId);
            entity.setContent(content);
            entity.setSourceConversationId(conversationId);
            memoryMapper.insert(entity);
        }
    }

    @Override
    public List<SiteUserMemoryDto> listOwn(long siteId, long userId) {
        List<SiteUserMemoryEntity> rows = memoryMapper.selectOwn(siteId, userId);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new SiteUserMemoryDto(row.getId(), row.getContent(), row.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteOwn(long siteId, long userId, long memoryId) {
        SiteUserMemoryEntity entity = memoryMapper.selectById(memoryId);
        if (entity == null
                || !Objects.equals(entity.getSiteId(), siteId)
                || !Objects.equals(entity.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!softDelete(entity)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    @Transactional
    public void clearOwn(long siteId, long userId) {
        List<SiteUserMemoryEntity> rows = memoryMapper.selectOwn(siteId, userId);
        if (rows == null) {
            return;
        }
        for (SiteUserMemoryEntity row : rows) {
            softDelete(row);
        }
    }

    private boolean evictOldest(long siteId, long userId) {
        Long id = memoryMapper.selectOldestId(siteId, userId);
        if (id == null) {
            return false;
        }
        SiteUserMemoryEntity entity = memoryMapper.selectById(id);
        if (entity == null) {
            return false;
        }
        return softDelete(entity);
    }

    private boolean softDelete(SiteUserMemoryEntity entity) {
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
