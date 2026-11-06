package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.constant.AdminAiChainPresetMessageKeys;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainPresetUpdateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetCreateRequest;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetDto;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAiChainPresetUpdateRequest;
import com.mywebsite.admin.entity.AdminAgentEntity;
import com.mywebsite.admin.entity.AdminAiChainPresetEntity;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.mapper.AdminAiChainPresetMapper;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.service.AdminAiChainPresetService;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiChainPresetServiceImpl implements AdminAiChainPresetService {

    private static final Logger log = LoggerFactory.getLogger(AdminAiChainPresetServiceImpl.class);

    /** 用途 v1 只有对话链（Issue #210 定案：只做对话链预设，OCR 跟随对话链）。 */
    static final String PURPOSE_CHAT = "CHAT";

    private final AdminAiChainPresetMapper chainPresetMapper;
    private final AdminAiModelEntryMapper modelEntryMapper;
    private final AdminAgentMapper agentMapper;
    private final ObjectMapper objectMapper;

    public AdminAiChainPresetServiceImpl(
            AdminAiChainPresetMapper chainPresetMapper,
            AdminAiModelEntryMapper modelEntryMapper,
            AdminAgentMapper agentMapper,
            ObjectMapper objectMapper) {
        this.chainPresetMapper = chainPresetMapper;
        this.modelEntryMapper = modelEntryMapper;
        this.agentMapper = agentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AdminAiChainPresetDto> listPresets() {
        return chainPresetMapper
                .selectList(new LambdaQueryWrapper<AdminAiChainPresetEntity>()
                        .orderByDesc(AdminAiChainPresetEntity::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public AdminAiChainPresetDto getPreset(long id) {
        return toDto(requirePreset(id));
    }

    @Override
    @Transactional
    public AdminAiChainPresetDto createPreset(AdminAiChainPresetCreateRequest request) {
        List<Long> entryIds = normalizeEntryIds(request.entryIds());
        requireEntriesExist(entryIds);
        AdminAiChainPresetEntity preset = new AdminAiChainPresetEntity();
        preset.setName(request.name().trim());
        preset.setPurpose(PURPOSE_CHAT);
        preset.setEntryIds(writeEntryIds(entryIds));
        chainPresetMapper.insert(preset);
        return toDto(requirePreset(preset.getId()));
    }

    @Override
    @Transactional
    public AdminAiChainPresetDto updatePreset(long id, AdminAiChainPresetUpdateRequest request) {
        AdminAiChainPresetEntity preset = requirePreset(id);
        // 成员可能已被删（删入口不设引用守卫、运行时本就静默跳过缺失成员）：请求里
        // 仍带着的存量悬空成员在保存时顺手剔除，否则改个名都会被 ENTRY_NOT_FOUND
        // 卡住；请求新引入的不存在 id 仍按 ENTRY_NOT_FOUND 拒绝（与 create 同口径）。
        List<Long> entryIds =
                dropDanglingStoredMembers(normalizeEntryIds(request.entryIds()), readEntryIds(preset));
        preset.setName(request.name().trim());
        preset.setEntryIds(writeEntryIds(entryIds));
        preset.setVersion(request.version());
        int updated = chainPresetMapper.updateById(preset);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toDto(requirePreset(id));
    }

    @Override
    @Transactional
    public void deletePreset(long id) {
        AdminAiChainPresetEntity preset = requirePreset(id);
        long referencing =
                agentMapper.selectCount(new LambdaQueryWrapper<AdminAgentEntity>()
                        .eq(AdminAgentEntity::getChainPresetId, id));
        if (referencing > 0) {
            // 被引用时拦截并报明确错误：先在 agent 工作台解除引用或改指其他预设。
            // DB 侧另有 FK ON DELETE RESTRICT 兜底物理删除。
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), AdminAiChainPresetMessageKeys.PRESET_REFERENCED, referencing);
        }
        preset.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        chainPresetMapper.updateById(preset);
        chainPresetMapper.deleteById(id);
    }

    @Override
    public AdminAgentChainPresetDto getAgentChainPreset(long agentId) {
        AdminAgentEntity agent = requireAgent(agentId);
        return new AdminAgentChainPresetDto(agent.getId(), agent.getChainPresetId());
    }

    @Override
    @Transactional
    public AdminAgentChainPresetDto updateAgentChainPreset(
            long agentId, AdminAgentChainPresetUpdateRequest request) {
        AdminAgentEntity agent = requireAgent(agentId);
        Long presetId = request == null ? null : request.chainPresetId();
        if (presetId != null) {
            requirePreset(presetId);
        }
        agent.setChainPresetId(presetId);
        int updated = agentMapper.updateById(agent);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return new AdminAgentChainPresetDto(agent.getId(), agent.getChainPresetId());
    }

    @Override
    public List<Long> referencedEntryIds(long agentId) {
        AdminAgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null || agent.getChainPresetId() == null) {
            return List.of();
        }
        AdminAiChainPresetEntity preset = chainPresetMapper.selectById(agent.getChainPresetId());
        if (preset == null) {
            return List.of();
        }
        return readEntryIds(preset);
    }

    private AdminAiChainPresetEntity requirePreset(long id) {
        AdminAiChainPresetEntity preset = chainPresetMapper.selectById(id);
        if (preset == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return preset;
    }

    private AdminAgentEntity requireAgent(long agentId) {
        AdminAgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return agent;
    }

    /** 成员必须全部是现存（未删除）入口；停用入口可入预设（组链时过滤、启用后自动回链）。 */
    /**
     * 更新专用校验：已存在成员保留；存量成员（在预设原 entry_ids 里）若已被删则
     * 剔除并记日志；其余不存在的 id 视为新引入的坏引用，抛 ENTRY_NOT_FOUND。
     */
    private List<Long> dropDanglingStoredMembers(List<Long> entryIds, List<Long> storedIds) {
        if (entryIds.isEmpty()) {
            return entryIds;
        }
        Set<Long> found = new LinkedHashSet<>();
        for (AdminAiModelEntryEntity entry : modelEntryMapper.selectBatchIds(entryIds)) {
            found.add(entry.getId());
        }
        List<Long> kept = new ArrayList<>();
        for (Long entryId : entryIds) {
            if (found.contains(entryId)) {
                kept.add(entryId);
            } else if (storedIds.contains(entryId)) {
                log.warn("chain preset member {} no longer exists; dropping it on update", entryId);
            } else {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST.getCode(), AdminAiChainPresetMessageKeys.ENTRY_NOT_FOUND, entryId);
            }
        }
        return kept;
    }

    private void requireEntriesExist(List<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return;
        }
        Set<Long> found = new LinkedHashSet<>();
        for (AdminAiModelEntryEntity entry : modelEntryMapper.selectBatchIds(entryIds)) {
            found.add(entry.getId());
        }
        for (Long entryId : entryIds) {
            if (!found.contains(entryId)) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST.getCode(), AdminAiChainPresetMessageKeys.ENTRY_NOT_FOUND, entryId);
            }
        }
    }

    private AdminAiChainPresetDto toDto(AdminAiChainPresetEntity preset) {
        return new AdminAiChainPresetDto(
                preset.getId(),
                preset.getName(),
                preset.getPurpose(),
                readEntryIds(preset),
                preset.getVersion(),
                preset.getUpdatedAt());
    }

    /** 落库形态：JSON 数组文本（保序）；空集存 []，与 V63 自动生成行同形态。 */
    private String writeEntryIds(List<Long> entryIds) {
        try {
            return objectMapper.writeValueAsString(entryIds);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    /** 脏 JSON 不阻断读路径：按无成员处理并记 warn（同配置预设默认集的容脏口径）。 */
    private List<Long> readEntryIds(AdminAiChainPresetEntity preset) {
        String raw = preset.getEntryIds();
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<Long> parsed = objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
            return normalizeEntryIds(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("chain preset {} has malformed entry_ids, treated as empty", preset.getId(), ex);
            return List.of();
        }
    }

    /** 保序去重、剔除 null（成员顺序即组链顺序，重复成员无意义）。 */
    static List<Long> normalizeEntryIds(List<Long> entryIds) {
        if (entryIds == null || entryIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long entryId : entryIds) {
            if (entryId != null) {
                unique.add(entryId);
            }
        }
        return new ArrayList<>(unique);
    }
}
