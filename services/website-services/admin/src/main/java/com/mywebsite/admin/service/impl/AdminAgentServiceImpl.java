package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminAgentConstants;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentDetailDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListItemDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListQuery;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentUpdateRequest;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.admin.service.AdminAiKnowledgeService;
import com.mywebsite.admin.service.AdminAiPromptService;
import com.mywebsite.admin.service.AdminAppearanceService;
import com.mywebsite.admin.service.AdminContentService;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.admin.service.AdminAgentService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAgentServiceImpl implements AdminAgentService {

    private final AdminAgentMapper adminAgentMapper;
    private final AdminAppearanceService adminAppearanceService;
    private final AdminMediaService adminMediaService;
    private final AdminContentService adminContentService;
    private final AdminAiPromptService adminAiPromptService;
    private final AdminAiKnowledgeService adminAiKnowledgeService;
    private final AdminAiConversationService adminAiConversationService;

    public AdminAgentServiceImpl(
            AdminAgentMapper adminAgentMapper,
            AdminAppearanceService adminAppearanceService,
            AdminMediaService adminMediaService,
            AdminContentService adminContentService,
            AdminAiPromptService adminAiPromptService,
            AdminAiKnowledgeService adminAiKnowledgeService,
            AdminAiConversationService adminAiConversationService) {
        this.adminAgentMapper = adminAgentMapper;
        this.adminAppearanceService = adminAppearanceService;
        this.adminMediaService = adminMediaService;
        this.adminContentService = adminContentService;
        this.adminAiPromptService = adminAiPromptService;
        this.adminAiKnowledgeService = adminAiKnowledgeService;
        this.adminAiConversationService = adminAiConversationService;
    }

    @Override
    public PageResult<AdminAgentListItemDto> listSites(AdminAgentListQuery query) {
        int pageNum = query != null && query.pageNum() != null
                ? query.pageNum()
                : AdminAgentConstants.DEFAULT_PAGE_NUM;
        int pageSize = query != null && query.pageSize() != null
                ? Math.min(query.pageSize(), AdminAgentConstants.MAX_PAGE_SIZE)
                : AdminAgentConstants.DEFAULT_PAGE_SIZE;

        LambdaQueryWrapper<AdminAgentEntity> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (StringUtils.hasText(query.status())) {
                requireStatus(query.status());
                wrapper.eq(AdminAgentEntity::getStatus, query.status());
            }
            if (StringUtils.hasText(query.agentType())) {
                requireAgentType(query.agentType());
                wrapper.eq(AdminAgentEntity::getAgentType, query.agentType());
            }
        }
        wrapper.orderByDesc(AdminAgentEntity::getId);

        Page<AdminAgentEntity> page =
                adminAgentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return new PageResult<>(
                page.getRecords().stream().map(this::toListItem).toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    @Override
    public AdminAgentDetailDto getSite(long id) {
        return toDetail(requireSite(id));
    }

    @Override
    @Transactional
    public AdminAgentDetailDto createSite(AdminAgentCreateRequest request) {
        if (!request.agentCode().matches(AdminAgentConstants.AGENT_CODE_PATTERN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAgentMessageKeys.AGENT_CODE_INVALID);
        }
        requireAgentType(request.agentType());
        boolean hasAppearance = request.appearance() != null;
        if (hasAppearance) {
            adminAppearanceService.requireValidConfigV1(request.appearance());
        }
        if (findByAgentCode(request.agentCode()) != null) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminAgentMessageKeys.AGENT_CODE_DUPLICATE);
        }

        AdminAgentEntity entity = new AdminAgentEntity();
        entity.setAgentCode(request.agentCode());
        entity.setAgentName(request.agentName());
        entity.setAgentType(request.agentType());
        entity.setDefaultDomain(blankToNull(request.defaultDomain()));
        entity.setDescription(blankToNull(request.description()));
        entity.setStatus(AdminAgentConstants.STATUS_ACTIVE);
        try {
            adminAgentMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminAgentMessageKeys.AGENT_CODE_DUPLICATE);
        }
        if (hasAppearance) {
            adminAppearanceService.insertForSite(entity.getId(), request.appearance());
        }
        return toDetail(requireSite(entity.getId()));
    }

    @Override
    public AdminAgentDetailDto updateSite(long id, AdminAgentUpdateRequest request) {
        AdminAgentEntity agent = requireSite(id);
        agent.setAgentName(request.agentName());
        if (StringUtils.hasText(request.agentType())) {
            requireAgentType(request.agentType());
            agent.setAgentType(request.agentType());
        }
        agent.setDefaultDomain(blankToNull(request.defaultDomain()));
        agent.setDescription(blankToNull(request.description()));
        agent.setVersion(request.version());
        int updated = adminAgentMapper.updateById(agent);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminAgentMessageKeys.AGENT_VERSION_CONFLICT);
        }
        return toDetail(requireSite(id));
    }

    @Override
    @Transactional
    public void deleteSite(long id) {
        AdminAgentEntity agent = requireSite(id);
        adminAppearanceService.softDeleteByAgentId(id);
        adminContentService.softDeleteByAgentId(id);
        adminMediaService.softDeleteByAgentId(id);
        adminAiPromptService.softDeleteByAgentId(id);
        adminAiKnowledgeService.softDeleteByAgentId(id);
        adminAiConversationService.softDeleteByAgentId(id);
        agent.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        adminAgentMapper.updateById(agent);
        int deleted = adminAgentMapper.deleteById(id);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
    }

    @Override
    public AdminAgentDetailDto updateStatus(long id, AdminAgentStatusUpdateRequest request) {
        requireStatus(request.status());
        AdminAgentEntity agent = requireSite(id);
        agent.setStatus(request.status());
        agent.setVersion(request.version());
        int updated = adminAgentMapper.updateById(agent);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT.getCode(), AdminAgentMessageKeys.AGENT_VERSION_CONFLICT);
        }
        return toDetail(requireSite(id));
    }

    private AdminAgentEntity requireSite(long id) {
        AdminAgentEntity agent = adminAgentMapper.selectById(id);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
        return agent;
    }

    /** 按 agent_code 查未逻辑删除行（{@code @TableLogic}）；不按 ACTIVE 过滤，用于唯一性校验。 */
    private AdminAgentEntity findByAgentCode(String agentCode) {
        return adminAgentMapper.selectOne(
                new LambdaQueryWrapper<AdminAgentEntity>().eq(AdminAgentEntity::getAgentCode, agentCode));
    }

    /** 用途标签校验：非空且符合标签模式（预设标签与自订标签同等对待），不再受旧行业闭集约束。 */
    private static void requireAgentType(String agentType) {
        if (!StringUtils.hasText(agentType) || !agentType.trim().matches(AdminAgentConstants.AGENT_TAG_PATTERN)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAgentMessageKeys.AGENT_TYPE_INVALID);
        }
    }

    private static void requireStatus(String status) {
        if (!AdminAgentConstants.ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAgentMessageKeys.AGENT_STATUS_INVALID);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private AdminAgentListItemDto toListItem(AdminAgentEntity entity) {
        return new AdminAgentListItemDto(
                entity.getId(),
                entity.getAgentCode(),
                entity.getAgentName(),
                entity.getAgentType(),
                entity.getStatus(),
                entity.getDefaultDomain(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AdminAgentDetailDto toDetail(AdminAgentEntity entity) {
        return new AdminAgentDetailDto(
                entity.getId(),
                entity.getAgentCode(),
                entity.getAgentName(),
                entity.getAgentType(),
                entity.getStatus(),
                entity.getDefaultDomain(),
                entity.getDescription(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
