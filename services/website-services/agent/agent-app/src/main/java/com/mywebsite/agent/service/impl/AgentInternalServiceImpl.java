package com.mywebsite.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentItemDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentContentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentKnowledgeDocumentItemDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentKnowledgeDocumentListDto;
import com.mywebsite.agent.dto.AgentInternalDtos.AgentSummaryDto;
import com.mywebsite.agent.entity.AgentAiKnowledgeDocumentEntity;
import com.mywebsite.agent.entity.AgentContentEntity;
import com.mywebsite.agent.entity.AgentEntity;
import com.mywebsite.agent.mapper.AgentAiKnowledgeDocumentMapper;
import com.mywebsite.agent.mapper.AgentContentMapper;
import com.mywebsite.agent.mapper.AgentMapper;
import com.mywebsite.agent.service.AgentInternalService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentInternalServiceImpl implements AgentInternalService {

    private static final int MAX_CONTENT_LIMIT = 50;

    /** 知识文档枚举分页口径（Issue #181 定案）：limit 默认 100、硬上限 200。 */
    public static final int DEFAULT_KNOWLEDGE_DOCUMENT_LIMIT = 100;

    public static final int MAX_KNOWLEDGE_DOCUMENT_LIMIT = 200;

    private final AgentMapper agentMapper;
    private final AgentContentMapper agentContentMapper;
    private final AgentAiKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeSpaceResolver knowledgeSpaceResolver;

    public AgentInternalServiceImpl(
            AgentMapper agentMapper,
            AgentContentMapper agentContentMapper,
            AgentAiKnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeSpaceResolver knowledgeSpaceResolver) {
        this.agentMapper = agentMapper;
        this.agentContentMapper = agentContentMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeSpaceResolver = knowledgeSpaceResolver;
    }

    @Override
    public AgentSummaryDto getAgentSummary(long agentId) {
        AgentEntity agent = requireSite(agentId);
        return new AgentSummaryDto(agent.getId(), agent.getAgentName(), agent.getAgentType(), agent.getDescription());
    }

    @Override
    public AgentContentListDto listPublishedContents(long agentId, String type, int limit) {
        requireSite(agentId);
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_CONTENT_LIMIT);
        LambdaQueryWrapper<AgentContentEntity> wrapper = new LambdaQueryWrapper<AgentContentEntity>()
                .eq(AgentContentEntity::getAgentId, agentId)
                .eq(AgentContentEntity::getStatus, AgentContentEntity.STATUS_PUBLISHED)
                .orderByAsc(AgentContentEntity::getSortOrder)
                .orderByDesc(AgentContentEntity::getId)
                .last("LIMIT " + effectiveLimit);
        if (StringUtils.hasText(type)) {
            wrapper.eq(AgentContentEntity::getType, type.trim());
        }
        return new AgentContentListDto(agentContentMapper.selectList(wrapper).stream()
                .map(c -> new AgentContentItemDto(c.getId(), c.getType(), c.getTitle(), c.getBody()))
                .toList());
    }

    @Override
    public AgentKnowledgeDocumentListDto listKnowledgeDocuments(
            long agentId,
            String keyword,
            String format,
            OffsetDateTime createdFrom,
            OffsetDateTime createdTo,
            int limit,
            int offset) {
        requireSite(agentId);
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_KNOWLEDGE_DOCUMENT_LIMIT);
        int effectiveOffset = Math.max(offset, 0);
        // 枚举范围 = 该 agent 可达空间集（创建∪订阅，Issue #209 UoW-4 定稿）：
        // 文档 agent_id 已停写（UoW-1），按 document.baseId 归属过滤；空间集为空
        // 时结果为空集（过滤里以不可能命中的 id 兜底，保证 total/items 同口径）。
        List<Long> baseIds = knowledgeSpaceResolver.reachableBaseIds(agentId);
        long total = knowledgeDocumentMapper.selectCount(
                knowledgeDocumentFilter(baseIds, keyword, format, createdFrom, createdTo));
        LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity> wrapper =
                knowledgeDocumentFilter(baseIds, keyword, format, createdFrom, createdTo)
                        // 只取元数据列，正文 content 不进枚举查询。
                        .select(
                                AgentAiKnowledgeDocumentEntity::getId,
                                AgentAiKnowledgeDocumentEntity::getBaseId,
                                AgentAiKnowledgeDocumentEntity::getTitle,
                                AgentAiKnowledgeDocumentEntity::getSourceFormat,
                                AgentAiKnowledgeDocumentEntity::getOriginalFilename,
                                AgentAiKnowledgeDocumentEntity::getIndexStatus,
                                AgentAiKnowledgeDocumentEntity::getCreatedAt,
                                AgentAiKnowledgeDocumentEntity::getUpdatedAt)
                        .orderByAsc(AgentAiKnowledgeDocumentEntity::getCreatedAt)
                        .orderByAsc(AgentAiKnowledgeDocumentEntity::getId)
                        .last("LIMIT " + effectiveLimit + " OFFSET " + effectiveOffset);
        var items = knowledgeDocumentMapper.selectList(wrapper).stream()
                .map(doc -> new AgentKnowledgeDocumentItemDto(
                        doc.getId(),
                        "kb:" + doc.getId(),
                        doc.getBaseId(),
                        doc.getTitle(),
                        doc.getSourceFormat(),
                        doc.getOriginalFilename(),
                        doc.getIndexStatus(),
                        doc.getCreatedAt(),
                        doc.getUpdatedAt()))
                .toList();
        boolean truncated = effectiveOffset + items.size() < total;
        return new AgentKnowledgeDocumentListDto(items, total, effectiveLimit, effectiveOffset, truncated);
    }

    /** 枚举与计数共用同一套过滤条件，保证 total 与 items 口径一致。 */
    private static LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity> knowledgeDocumentFilter(
            List<Long> baseIds, String keyword, String format, OffsetDateTime createdFrom, OffsetDateTime createdTo) {
        LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity> wrapper =
                new LambdaQueryWrapper<AgentAiKnowledgeDocumentEntity>()
                        .eq(AgentAiKnowledgeDocumentEntity::getStatus, AgentAiKnowledgeDocumentEntity.STATUS_ACTIVE);
        if (baseIds.isEmpty()) {
            // 无可达空间：以不可能命中的 id 兜底为空集（IN 空集在部分数据库上非法）。
            wrapper.eq(AgentAiKnowledgeDocumentEntity::getId, -1L);
        } else {
            wrapper.in(AgentAiKnowledgeDocumentEntity::getBaseId, baseIds);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(AgentAiKnowledgeDocumentEntity::getTitle, keyword.trim());
        }
        if (StringUtils.hasText(format)) {
            wrapper.eq(
                    AgentAiKnowledgeDocumentEntity::getSourceFormat,
                    format.trim().toLowerCase(Locale.ROOT));
        }
        if (createdFrom != null) {
            wrapper.ge(AgentAiKnowledgeDocumentEntity::getCreatedAt, createdFrom);
        }
        if (createdTo != null) {
            wrapper.le(AgentAiKnowledgeDocumentEntity::getCreatedAt, createdTo);
        }
        return wrapper;
    }

    private AgentEntity requireSite(long agentId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return agent;
    }
}
