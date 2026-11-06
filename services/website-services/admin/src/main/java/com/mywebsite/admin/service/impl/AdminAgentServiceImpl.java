package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mywebsite.admin.constant.AdminAgentConstants;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentCreateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentDetailDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentKindUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListItemDto;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentListQuery;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentStatusUpdateRequest;
import com.mywebsite.admin.dto.AdminAgentDtos.AdminAgentUpdateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiKnowledgeBaseSubscriptionEntity;
import com.mywebsite.admin.entity.AdminAiPromptTemplateEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseSubscriptionMapper;
import com.mywebsite.admin.mapper.AdminAiPromptTemplateMapper;
import com.mywebsite.admin.service.AdminAiConversationService;
import com.mywebsite.admin.service.AdminAiKnowledgeService;

import com.mywebsite.admin.service.AdminAppearanceService;
import com.mywebsite.admin.service.AdminContentService;
import com.mywebsite.admin.service.AdminMediaService;
import com.mywebsite.admin.service.AdminAgentService;
import com.mywebsite.agent.entity.AgentAiConversationShareEntity;
import com.mywebsite.agent.entity.AgentUserEntity;
import com.mywebsite.agent.entity.AgentUserMemoryEntity;
import com.mywebsite.agent.mapper.AgentAiConversationShareMapper;
import com.mywebsite.agent.mapper.AgentUserMapper;
import com.mywebsite.agent.mapper.AgentUserMemoryMapper;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.common.page.PageResult;
import com.mywebsite.infra.entity.AbstractAuditableEntity;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final AdminAiKnowledgeService adminAiKnowledgeService;
    private final AdminAiConversationService adminAiConversationService;
    private final AgentUserMapper agentUserMapper;
    private final AgentUserMemoryMapper agentUserMemoryMapper;
    private final AgentAiConversationShareMapper conversationShareMapper;
    private final AdminAiKnowledgeBaseSubscriptionMapper subscriptionMapper;
    private final AdminAiPromptTemplateMapper promptTemplateMapper;
    private final ObjectMapper objectMapper;

    public AdminAgentServiceImpl(
            AdminAgentMapper adminAgentMapper,
            AdminAppearanceService adminAppearanceService,
            AdminMediaService adminMediaService,
            AdminContentService adminContentService,
            AdminAiKnowledgeService adminAiKnowledgeService,
            AdminAiConversationService adminAiConversationService,
            AgentUserMapper agentUserMapper,
            AgentUserMemoryMapper agentUserMemoryMapper,
            AgentAiConversationShareMapper conversationShareMapper,
            AdminAiKnowledgeBaseSubscriptionMapper subscriptionMapper,
            AdminAiPromptTemplateMapper promptTemplateMapper,
            ObjectMapper objectMapper) {
        this.adminAgentMapper = adminAgentMapper;
        this.adminAppearanceService = adminAppearanceService;
        this.adminMediaService = adminMediaService;
        this.adminContentService = adminContentService;
        this.adminAiKnowledgeService = adminAiKnowledgeService;
        this.adminAiConversationService = adminAiConversationService;
        this.agentUserMapper = agentUserMapper;
        this.agentUserMemoryMapper = agentUserMemoryMapper;
        this.conversationShareMapper = conversationShareMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<AdminAgentListItemDto> listSites(AdminAgentListQuery query) {
        // 下限钳到 1：pageNum=0/负数时分页插件行为未定义，与 pageSize 上限同风格在组装处收口。
        int pageNum = query != null && query.pageNum() != null
                ? Math.max(query.pageNum(), 1)
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
        List<AdminAgentEntity> records = page.getRecords();
        // 关联摘要（#213；链部分 #214 UoW-3 改、Prompt 部分 UoW-4 改）：订阅数与来源
        // 模板名按本页 id 集批量预载后内存组装，避免逐行查询；对话链与 Prompt 副本
        // 都已内置化，成员数/已配置标记直接由 agent 行字段逐行解析计得。
        Map<Long, Long> subscriptionCounts = subscriptionCounts(records);
        Map<Long, String> promptTemplateNames = presetNames(
                records.stream().map(AdminAgentEntity::getPromptTemplateId).collect(Collectors.toSet()),
                promptTemplateMapper,
                AdminAiPromptTemplateEntity::getName);
        return new PageResult<>(
                records.stream()
                        .map(entity -> toListItem(entity, subscriptionCounts, promptTemplateNames))
                        .toList(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize());
    }

    /** 自有链成员数：字段是 JSON 有序 id 数组文本，脏值按 0 计（列表摘要不阻断）。 */
    private int chainMemberCount(String rawEntryIds) {
        if (!StringUtils.hasText(rawEntryIds)) {
            return 0;
        }
        try {
            List<Long> ids = objectMapper.readValue(rawEntryIds, new TypeReference<List<Long>>() {});
            return ids == null ? 0 : ids.size();
        } catch (JsonProcessingException ex) {
            return 0;
        }
    }

    /** 自有技能集成员数：同链计数的字符串数组版，脏值按 0 计。 */
    private int skillCount(String rawSkillIds) {
        if (!StringUtils.hasText(rawSkillIds)) {
            return 0;
        }
        try {
            List<String> ids = objectMapper.readValue(rawSkillIds, new TypeReference<List<String>>() {});
            return ids == null ? 0 : ids.size();
        } catch (JsonProcessingException ex) {
            return 0;
        }
    }

    /**
     * 本地副本存在性（与解析口径一致，见 agent-app 组装）：留痕、prompt 文本、
     * 温度任一非空即视为已配置——含「只从模板拷了名称型预设、prompt 与温度皆空」
     * 的存量形态（旧解析下同样返回非 null 的 profile）。
     */
    private static boolean promptConfigured(AdminAgentEntity entity) {
        return entity.getPromptTemplateId() != null
                || StringUtils.hasText(entity.getPromptSystemPrompt())
                || entity.getPromptTemperature() != null;
    }

    /** 订阅数＝订阅表行数（含空间创建者行，#209 口径）；按 agent id 集聚合 COUNT/GROUP BY 一次查回。 */
    private Map<Long, Long> subscriptionCounts(List<AdminAgentEntity> agents) {
        if (agents.isEmpty()) {
            return Map.of();
        }
        List<Long> agentIds = agents.stream().map(AdminAgentEntity::getId).toList();
        QueryWrapper<AdminAiKnowledgeBaseSubscriptionEntity> wrapper = new QueryWrapper<>();
        wrapper.select("agent_id", "COUNT(*) AS subscription_count")
                .in("agent_id", agentIds)
                .groupBy("agent_id");
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : subscriptionMapper.selectMaps(wrapper)) {
            Object agentId = columnValue(row, "agent_id");
            Object count = columnValue(row, "subscription_count");
            if (agentId instanceof Number && count instanceof Number) {
                counts.put(((Number) agentId).longValue(), ((Number) count).longValue());
            }
        }
        return counts;
    }

    /** selectMaps 的列键大小写随驱动而异（PG 小写、H2 大写），按不区分大小写取列值。 */
    private static Object columnValue(Map<String, Object> row, String column) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 模板名批量解析：id 集一次查回；查不到的 id 不入表（组装时 name 落 null、id 照实）。 */
    private static <E extends AbstractAuditableEntity> Map<Long, String> presetNames(
            Set<Long> presetIds, BaseMapper<E> mapper, Function<E, String> nameExtractor) {
        Set<Long> ids = presetIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        mapper.selectBatchIds(ids).forEach(preset -> names.put(preset.getId(), nameExtractor.apply(preset)));
        return names;
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
        entity.setAgentKind(normalizeAgentKind(request.agentKind()));
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
        if (StringUtils.hasText(request.agentKind())) {
            requireAgentKind(request.agentKind());
            agent.setAgentKind(request.agentKind());
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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        adminAppearanceService.softDeleteByAgentId(id);
        adminContentService.softDeleteByAgentId(id);
        adminMediaService.softDeleteByAgentId(id);
        // Prompt 配置不再级联：旧 profile 表停写（退役另行）；Prompt 模板是平台
        // 资源、绝不随单个 agent 删除（#214 UoW-4）。agent 行上的本地副本与自有
        // 技能集是 agent 自有数据，随 agent 行本身软删，不需单独清理。
        adminAiKnowledgeService.softDeleteByAgentId(id);
        adminAiConversationService.softDeleteByAgentId(id);
        // 级联补全（#141 UoW-2）：成员关系、用户长期记忆、会话分享，漏删会残留成员行。
        // 三路同走既有软删口径（deleted_at＋@TableLogic）。
        // 模型入口已平台化（#210 UoW-6）：入口是平台资源、不归 agent 所有，删 agent
        // 不再级联软删入口（原 #141 的入口一路随之退役），来源标注留痕不随删。
        softDeleteAll(
                agentUserMapper.selectList(
                        new LambdaQueryWrapper<AgentUserEntity>().eq(AgentUserEntity::getAgentId, id)),
                agentUserMapper,
                now);
        softDeleteAll(
                agentUserMemoryMapper.selectList(
                        new LambdaQueryWrapper<AgentUserMemoryEntity>()
                                .eq(AgentUserMemoryEntity::getAgentId, id)),
                agentUserMemoryMapper,
                now);
        softDeleteAll(
                conversationShareMapper.selectList(
                        new LambdaQueryWrapper<AgentAiConversationShareEntity>()
                                .eq(AgentAiConversationShareEntity::getAgentId, id)),
                conversationShareMapper,
                now);
        agent.setDeletedAt(now);
        adminAgentMapper.updateById(agent);
        int deleted = adminAgentMapper.deleteById(id);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
    }

    /** 与各领域服务 softDeleteByAgentId 相同的逐行软删口径，供 agent-core 三张表在本类内收口。 */
    private static <T extends AbstractAuditableEntity> void softDeleteAll(
            List<T> rows, BaseMapper<T> mapper, OffsetDateTime now) {
        for (T row : rows) {
            row.setDeletedAt(now);
            mapper.updateById(row);
            mapper.deleteById(row.getId());
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

    /** 类型（kind）专属改写（#213）：封闭枚举校验＋乐观锁，口径与 updateStatus 一致。 */
    @Override
    public AdminAgentDetailDto updateKind(long id, AdminAgentKindUpdateRequest request) {
        // 先验 agent 存在、再验枚举：不存在的 agent 一律 404，不因 kind 非法先报 400。
        AdminAgentEntity agent = requireSite(id);
        requireAgentKind(request.kind());
        agent.setAgentKind(request.kind());
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

    /** 类型（kind）封闭枚举校验（#213）：只收 SITE / AI_ONLY，与自由文本的 agentType 分开。 */
    private static void requireAgentKind(String kind) {
        if (kind == null || !AdminAgentConstants.ALLOWED_AGENT_KINDS.contains(kind)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), AdminAgentMessageKeys.AGENT_KIND_INVALID);
        }
    }

    /** 创建时类型归一：缺省（null/空白）落 SITE，给出时走封闭枚举校验。 */
    private static String normalizeAgentKind(String kind) {
        if (!StringUtils.hasText(kind)) {
            return AdminAgentConstants.AGENT_KIND_SITE;
        }
        requireAgentKind(kind);
        return kind;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private AdminAgentListItemDto toListItem(
            AdminAgentEntity entity,
            Map<Long, Long> subscriptionCounts,
            Map<Long, String> promptTemplateNames) {
        return new AdminAgentListItemDto(
                entity.getId(),
                entity.getAgentCode(),
                entity.getAgentName(),
                entity.getAgentType(),
                entity.getStatus(),
                entity.getDefaultDomain(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getAgentKind(),
                subscriptionCounts.getOrDefault(entity.getId(), 0L),
                chainMemberCount(entity.getChainKeyEntryIds()),
                chainMemberCount(entity.getChainCliEntryIds()),
                entity.getPromptTemplateId(),
                entity.getPromptTemplateId() == null
                        ? null
                        : promptTemplateNames.get(entity.getPromptTemplateId()),
                promptConfigured(entity),
                skillCount(entity.getDefaultSkillIds()));
    }

    private AdminAgentDetailDto toDetail(AdminAgentEntity entity) {
        return new AdminAgentDetailDto(
                entity.getId(),
                entity.getAgentCode(),
                entity.getAgentName(),
                entity.getAgentType(),
                entity.getAgentKind(),
                entity.getStatus(),
                entity.getDefaultDomain(),
                entity.getDescription(),
                entity.getPromptTemplateId(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
