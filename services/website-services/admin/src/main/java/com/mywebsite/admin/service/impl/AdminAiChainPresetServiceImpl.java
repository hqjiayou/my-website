package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.admin.constant.AdminAiChainPresetMessageKeys;
import com.mywebsite.admin.dto.AdminAiChainPresetDtos.AdminAgentChainDto;
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
import java.util.Locale;
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

    static final String MODE_KEY = "KEY";
    static final String MODE_CLI = "CLI";

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

    // ------------------------------------------------------------------
    // 链模板库（原链预设 CRUD；定位见接口注释）
    // ------------------------------------------------------------------

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
        // 成员可能已被删（删入口不设引用守卫、组链时本就静默跳过缺失成员）：请求里
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
        // 链内置化后模板不再被 agent 引用（套用是拷贝），旧「被引用拦截」随引用
        // 退役一并取消：删模板不影响任何 agent 已拷贝的自有链（Issue #214 UoW-3）。
        preset.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        chainPresetMapper.updateById(preset);
        chainPresetMapper.deleteById(id);
    }

    // ------------------------------------------------------------------
    // agent 自有链
    // ------------------------------------------------------------------

    @Override
    public AdminAgentChainDto getAgentChain(long agentId) {
        return toAgentChainDto(requireAgent(agentId));
    }

    @Override
    @Transactional
    public AdminAgentChainDto updateAgentChain(long agentId, String mode, List<Long> entryIds) {
        String normalizedMode = requireMode(mode);
        AdminAgentEntity agent = requireAgent(agentId);
        List<Long> normalized = normalizeEntryIds(entryIds);
        requireEntriesExist(normalized);
        requireEntriesMode(normalized, normalizedMode);
        writeChain(agent, normalizedMode, normalized);
        int updated = agentMapper.updateById(agent);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toAgentChainDto(requireAgent(agentId));
    }

    @Override
    @Transactional
    public AdminAgentChainDto loadAgentChainFromTemplate(long agentId, String mode, long templateId) {
        String normalizedMode = requireMode(mode);
        AdminAgentEntity agent = requireAgent(agentId);
        AdminAiChainPresetEntity template = requirePreset(templateId);
        // 拷贝语义：只取模板成员中属于目标模式的部分（保模板序），写入后与模板脱钩；
        // 模板成员可能已被删——批量查询缺失者按不存在剔除（与组链静默跳过同口径）。
        List<Long> copied = new ArrayList<>();
        List<Long> templateIds = readEntryIds(template);
        if (!templateIds.isEmpty()) {
            java.util.Map<Long, AdminAiModelEntryEntity> byId = new java.util.HashMap<>();
            for (AdminAiModelEntryEntity entry : modelEntryMapper.selectBatchIds(templateIds)) {
                byId.put(entry.getId(), entry);
            }
            for (Long entryId : templateIds) {
                AdminAiModelEntryEntity entry = byId.get(entryId);
                if (entry != null && normalizedMode.equals(entryMode(entry))) {
                    copied.add(entryId);
                }
            }
            if (copied.size() != templateIds.size()) {
                log.info(
                        "chain template {} loaded into agent {} ({}) chain: {} of {} members copied",
                        templateId, agentId, normalizedMode, copied.size(), templateIds.size());
            }
        }
        writeChain(agent, normalizedMode, copied);
        int updated = agentMapper.updateById(agent);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toAgentChainDto(requireAgent(agentId));
    }

    @Override
    @Transactional
    public AdminAiChainPresetDto saveAgentChainAsTemplate(long agentId, String mode, String name) {
        String normalizedMode = requireMode(mode);
        AdminAgentEntity agent = requireAgent(agentId);
        AdminAiChainPresetEntity preset = new AdminAiChainPresetEntity();
        preset.setName(name.trim());
        preset.setPurpose(PURPOSE_CHAT);
        preset.setEntryIds(writeEntryIds(readChain(agent, normalizedMode)));
        chainPresetMapper.insert(preset);
        return toDto(requirePreset(preset.getId()));
    }

    @Override
    public List<Long> ownChainEntryIds(long agentId, String mode) {
        AdminAgentEntity agent = agentMapper.selectById(agentId);
        if (agent == null) {
            return List.of();
        }
        if (mode == null) {
            // 整链（兼容面）：KEY 链在前、CLI 链在后，定义见接口注释。
            List<Long> merged = new ArrayList<>(readChain(agent, MODE_KEY));
            merged.addAll(readChain(agent, MODE_CLI));
            return List.copyOf(merged);
        }
        return readChain(agent, requireMode(mode));
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    private AdminAgentChainDto toAgentChainDto(AdminAgentEntity agent) {
        return new AdminAgentChainDto(
                agent.getId(), readChain(agent, MODE_KEY), readChain(agent, MODE_CLI));
    }

    private List<Long> readChain(AdminAgentEntity agent, String mode) {
        return parseEntryIds(MODE_CLI.equals(mode) ? agent.getChainCliEntryIds() : agent.getChainKeyEntryIds());
    }

    private void writeChain(AdminAgentEntity agent, String mode, List<Long> entryIds) {
        // 空链存 NULL（与「未配链」同形态，V69 回填口径一致）；非空存 JSON 数组文本。
        String text = entryIds.isEmpty() ? null : writeEntryIds(entryIds);
        if (MODE_CLI.equals(mode)) {
            agent.setChainCliEntryIds(text);
        } else {
            agent.setChainKeyEntryIds(text);
        }
    }

    /** 模式收口：只接受 KEY / CLI（大小写不敏感），其余一律 BAD_REQUEST（与组链 chainMode 同口径）。 */
    static String requireMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!MODE_KEY.equals(normalized) && !MODE_CLI.equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return normalized;
    }

    /** 入口模式归属：空值按 KEY（与 V35 列默认、ModelChainResolver.entryMode 同口径）。 */
    private static String entryMode(AdminAiModelEntryEntity entry) {
        return StringUtils.hasText(entry.getMode())
                ? entry.getMode().trim().toUpperCase(Locale.ROOT)
                : MODE_KEY;
    }

    /** 成员的模式必须与目标链一致：异模式成员存进去组链时会被过滤掉，等于静默无效，保存时即拒绝。 */
    private void requireEntriesMode(List<Long> entryIds, String mode) {
        if (entryIds.isEmpty()) {
            return;
        }
        java.util.Map<Long, AdminAiModelEntryEntity> byId = new java.util.HashMap<>();
        for (AdminAiModelEntryEntity entry : modelEntryMapper.selectBatchIds(entryIds)) {
            byId.put(entry.getId(), entry);
        }
        for (Long entryId : entryIds) {
            AdminAiModelEntryEntity entry = byId.get(entryId);
            if (entry != null && !mode.equals(entryMode(entry))) {
                throw new BusinessException(
                        ErrorCode.BAD_REQUEST.getCode(),
                        AdminAiChainPresetMessageKeys.ENTRY_MODE_MISMATCH,
                        entryId);
            }
        }
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

    /** 成员必须全部是现存（未删除）入口；停用入口可入链（组链时过滤、启用后自动回链）。 */
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

    private List<Long> readEntryIds(AdminAiChainPresetEntity preset) {
        return parseEntryIds(preset.getEntryIds());
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
    private List<Long> parseEntryIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<Long> parsed = objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
            return normalizeEntryIds(parsed);
        } catch (JsonProcessingException ex) {
            log.warn("chain entry_ids is malformed JSON, treated as empty: {}", raw, ex);
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
