package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAiKnowledgeMessageKeys;
import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassDto;
import com.mywebsite.admin.dto.AdminEmbeddingClassDtos.AdminEmbeddingClassSaveRequest;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiKnowledgeBaseMapper;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.service.AdminEmbeddingRegistrationService;
import com.mywebsite.agent.entity.AgentEmbeddingClassEntity;
import com.mywebsite.agent.mapper.AgentEmbeddingClassMapper;
import com.mywebsite.agent.utils.AgentEmbeddingClassUtils;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 嵌入类登记读写（Issue #126 UoW-1）。
 *
 * <p>整表保存先全量校验、后动数据：资格与唯一性任何一项不过就整表拒绝，
 * 不会出现写一半的状态。类键规范化复用 {@link AgentEmbeddingClassUtils#className}
 * （与推算/消费侧同一口径：{@code google-gla:} 并入 {@code google:}），保证登记时
 * 判重的键就是消费时分组的键。
 */
@Service
public class AdminEmbeddingRegistrationServiceImpl implements AdminEmbeddingRegistrationService {

    private final AgentEmbeddingClassMapper registrationMapper;
    private final AdminAiModelEntryMapper modelEntryMapper;
    private final AdminAiKnowledgeBaseMapper knowledgeBaseMapper;

    public AdminEmbeddingRegistrationServiceImpl(
            AgentEmbeddingClassMapper registrationMapper,
            AdminAiModelEntryMapper modelEntryMapper,
            AdminAiKnowledgeBaseMapper knowledgeBaseMapper) {
        this.registrationMapper = registrationMapper;
        this.modelEntryMapper = modelEntryMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminEmbeddingClassDto> listRegistrations(Long baseId) {
        requireBaseIfPresent(baseId);
        List<AgentEmbeddingClassEntity> rows = selectScopeRows(baseId);
        Map<Long, AdminAiModelEntryEntity> entriesById = loadEntries(rows);
        return rows.stream().map(row -> toDto(row, entriesById.get(row.getEntryId()))).toList();
    }

    @Override
    @Transactional
    public List<AdminEmbeddingClassDto> replaceRegistrations(
            Long baseId, AdminEmbeddingClassSaveRequest request) {
        requireBaseIfPresent(baseId);
        List<AdminEmbeddingClassSaveRequest.Item> items =
                request == null || request.items() == null ? List.of() : request.items();
        if (items.size() > AgentEmbeddingClassUtils.MAX_EMBEDDING_CLASSES) {
            // 上限是整表拒绝（不截断）：截断会让保存结果与提交不一致、静默丢类。
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        // 先校验后落库：逐项核入口存在与资格，同时按 entry 与规范化类键双重判重。
        List<AdminAiModelEntryEntity> validatedEntries = new ArrayList<>(items.size());
        Set<Long> seenEntryIds = new HashSet<>();
        Set<String> seenClassNames = new HashSet<>();
        for (AdminEmbeddingClassSaveRequest.Item item : items) {
            if (item == null || item.entryId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            if (!seenEntryIds.add(item.entryId())) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            AdminAiModelEntryEntity entry = modelEntryMapper.selectById(item.entryId());
            if (entry == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            requireRegistrable(entry);
            String className = AgentEmbeddingClassUtils.className(entry.getEmbeddingSpec());
            if (className == null || !seenClassNames.add(className)) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            validatedEntries.add(entry);
        }

        // 整表替换：旧行逐行软删（与删 agent 级联同一口径），新行按数组序写 sort_order。
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (AgentEmbeddingClassEntity old : selectScopeRows(baseId)) {
            old.setDeletedAt(now);
            registrationMapper.updateById(old);
            registrationMapper.deleteById(old.getId());
        }
        for (int i = 0; i < items.size(); i++) {
            AgentEmbeddingClassEntity row = new AgentEmbeddingClassEntity();
            row.setBaseId(baseId);
            row.setEntryId(items.get(i).entryId());
            row.setEnabled(items.get(i).enabled() == null || items.get(i).enabled());
            row.setSortOrder(i);
            registrationMapper.insert(row);
        }
        return listRegistrations(baseId);
    }

    /** 登记资格：只有 mode=KEY 且 embeddingSpec 非空的入口能成为嵌入类（CLI 入口的 spec 保存时已清空）。 */
    private static void requireRegistrable(AdminAiModelEntryEntity entry) {
        String mode = StringUtils.hasText(entry.getMode())
                ? entry.getMode().trim().toUpperCase(Locale.ROOT)
                : "KEY";
        if (!"KEY".equals(mode) || !StringUtils.hasText(entry.getEmbeddingSpec())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    private List<AgentEmbeddingClassEntity> selectScopeRows(Long baseId) {
        LambdaQueryWrapper<AgentEmbeddingClassEntity> query = new LambdaQueryWrapper<>();
        if (baseId == null) {
            query.isNull(AgentEmbeddingClassEntity::getBaseId);
        } else {
            query.eq(AgentEmbeddingClassEntity::getBaseId, baseId);
        }
        query.orderByAsc(AgentEmbeddingClassEntity::getSortOrder).orderByAsc(AgentEmbeddingClassEntity::getId);
        return registrationMapper.selectList(query);
    }

    /** 批量回读被引用入口（避免逐行查）；入口已删时映射里没有它，DTO 对应字段留空。 */
    private Map<Long, AdminAiModelEntryEntity> loadEntries(List<AgentEmbeddingClassEntity> rows) {
        List<Long> entryIds = rows.stream().map(AgentEmbeddingClassEntity::getEntryId).distinct().toList();
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AdminAiModelEntryEntity> byId = new HashMap<>();
        for (AdminAiModelEntryEntity entry : modelEntryMapper.selectBatchIds(entryIds)) {
            byId.put(entry.getId(), entry);
        }
        return byId;
    }

    private static AdminEmbeddingClassDto toDto(
            AgentEmbeddingClassEntity row, AdminAiModelEntryEntity entry) {
        String spec = entry == null ? null : entry.getEmbeddingSpec();
        return new AdminEmbeddingClassDto(
                row.getId(),
                row.getBaseId(),
                row.getEntryId(),
                entry == null ? null : entry.getDisplayName(),
                spec,
                AgentEmbeddingClassUtils.className(spec),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getSortOrder() == null ? 0 : row.getSortOrder());
    }

    private void requireBaseIfPresent(Long baseId) {
        if (baseId == null) {
            return;
        }
        if (knowledgeBaseMapper.selectById(baseId) == null) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND.getCode(), AdminAiKnowledgeMessageKeys.BASE_NOT_FOUND);
        }
    }
}
