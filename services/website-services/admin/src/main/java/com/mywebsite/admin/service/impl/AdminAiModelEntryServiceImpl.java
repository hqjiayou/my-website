package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.service.AdminAiChainPresetService;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
import com.mywebsite.agent.utils.ModelChainResolver;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiModelEntryServiceImpl implements AdminAiModelEntryService {

    private final AdminAiModelEntryMapper modelEntryMapper;
    private final ModelEntryCipher cipher;
    private final AgentEmbeddingClassService embeddingClassService;
    private final AdminAiChainPresetService chainPresetService;

    public AdminAiModelEntryServiceImpl(
            AdminAiModelEntryMapper modelEntryMapper,
            ModelEntryCipher cipher,
            AgentEmbeddingClassService embeddingClassService,
            AdminAiChainPresetService chainPresetService) {
        this.modelEntryMapper = modelEntryMapper;
        this.cipher = cipher;
        this.embeddingClassService = embeddingClassService;
        this.chainPresetService = chainPresetService;
    }

    @Override
    @Transactional
    public List<AdminAiModelEntryDto> listEntries() {
        return modelEntryMapper
                .selectList(new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                        .orderByDesc(AdminAiModelEntryEntity::getDefaultEntry)
                        .orderByAsc(AdminAiModelEntryEntity::getId))
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public AdminAiModelEntryDto createEntry(AdminAiModelEntryWriteRequest request) {
        if (!StringUtils.hasText(request.apiKey())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        boolean asDefault = asDefault(request);
        if (asDefault) {
            // 新入口一律平台原生来源：默认位在平台原生来源 × 模式内互斥。
            clearOtherDefaults(null, null, normalizeMode(request.mode()));
        }
        String alias = normalizeAlias(request.alias());
        requireAliasAvailable(alias, null);
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        apply(entry, request, request.apiKey().trim(), asDefault, alias);
        try {
            modelEntryMapper.insert(entry);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toDto(requireEntry(entry.getId()));
    }

    @Override
    @Transactional
    public AdminAiModelEntryDto updateEntry(long id, AdminAiModelEntryWriteRequest request) {
        if (request.version() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        AdminAiModelEntryEntity entry = requireEntry(id);
        requireNotDerived(entry);
        boolean asDefault = asDefault(request);
        if (asDefault) {
            // 默认位在该入口自己的来源 × 模式内互斥（与 V62 的来源唯一索引同口径）。
            clearOtherDefaults(entry.getSourceAgentId(), id, normalizeMode(request.mode()));
        }
        String apiKey = StringUtils.hasText(request.apiKey()) ? request.apiKey().trim() : decryptKey(entry);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        String alias = normalizeAlias(request.alias());
        requireAliasAvailable(alias, id);
        apply(entry, request, apiKey, asDefault, alias);
        entry.setVersion(request.version());
        try {
            int updated = modelEntryMapper.updateById(entry);
            if (updated == 0) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return toDto(requireEntry(id));
    }

    @Override
    @Transactional
    public void deleteEntry(long id) {
        AdminAiModelEntryEntity entry = requireEntry(id);
        requireNotDerived(entry);
        entry.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        modelEntryMapper.updateById(entry);
        modelEntryMapper.deleteById(id);
    }

    @Override
    @Transactional
    public AgentModelSelection currentSelection(long agentId) {
        List<AgentModelSelection> chain = selectionChain(agentId);
        return chain.isEmpty() ? null : chain.get(0);
    }

    @Override
    @Transactional
    public List<AgentModelSelection> selectionChain(long agentId) {
        return selectionChain(agentId, null);
    }

    /**
     * 组链规则在 agent-core {@code ModelChainResolver}，与 agent-app 共用同一份实现（Issue #132）。
     * 试聊传入模式，使用户端和后台打的是同一条接入；知识转写仍走不分模式的整链。
     */
    @Override
    @Transactional
    public List<AgentModelSelection> selectionChain(long agentId, String mode) {
        return chainLinks(agentId, mode).stream().map(ChainLink::selection).toList();
    }

    @Override
    @Transactional
    public List<ChainCandidate> chainCandidates(long agentId, String mode) {
        return chainLinks(agentId, mode).stream()
                .map(link -> new ChainCandidate(link.id(), link.displayName(), link.selection().spec()))
                .toList();
    }

    @Override
    @Transactional
    public AgentModelSelection selectionOnChain(long agentId, String mode, long entryId) {
        for (ChainLink link : chainLinks(agentId, mode)) {
            if (link.id() != null && link.id() == entryId) {
                return link.selection();
            }
        }
        throw new BusinessException(ErrorCode.MODEL_ENTRY_UNAVAILABLE);
    }

    /**
     * 组链规则在 agent-core 的 {@code ModelChainResolver}（Issue #132，与 agent-app
     * 共用同一份实现）；这里只做自有链成员加载、实体→快照映射与结果回译，规则不在
     * 本类重复。
     *
     * <p>链内置化后（Issue #214 UoW-3）链由 agent 自有字段决定：按模式取该 agent
     * 的有序 entry ids（已删除的自动落选、停用的过滤，成员顺序即名单序），交
     * {@code ModelChainResolver.resolveMemberChain} 按既有规则组链（mode 过滤、
     * 默认/备援序、坏主用整链空）。UoW-6 的来源分层过渡组链整体退役，
     * {@code source_agent_id} 只作来源标注、不再参与组链。agent 未配该模式链时链为空。
     */
    private List<ChainLink> chainLinks(long agentId, String mode) {
        String normalized = chainMode(mode);
        List<Long> entryIds = chainPresetService.ownChainEntryIds(agentId, normalized);
        if (entryIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AdminAiModelEntryEntity> entriesById = new HashMap<>();
        for (AdminAiModelEntryEntity entry : modelEntryMapper.selectBatchIds(entryIds)) {
            entriesById.put(entry.getId(), entry);
        }
        List<AdminAiModelEntryEntity> members = new ArrayList<>();
        for (Long entryId : entryIds) {
            AdminAiModelEntryEntity entry = entriesById.get(entryId);
            if (entry != null && Boolean.TRUE.equals(entry.getEnabled())) {
                members.add(entry);
            }
        }
        return ModelChainResolver.resolveMemberChain(snapshots(members), normalized).stream()
                .map(link -> new ChainLink(link.id(), link.displayName(), link.selection()))
                .toList();
    }

    /**
     * 嵌入类改读登记（Issue #126 UoW-2）：委托 agent-core 的共享服务（含首次
     * 读物化），与 agent-app 同一份实现，不再在本侧扫入口推算。
     */
    @Override
    public List<AgentModelSelection> embeddingClassesForSpace(long baseId) {
        return embeddingClassService.embeddingClassesForSpace(baseId);
    }

    /** 实体→中立快照：解密在本侧完成（解不出为 null），规则引擎只吃快照。 */
    private List<ModelChainResolver.EntrySnapshot> snapshots(List<AdminAiModelEntryEntity> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .map(entry -> new ModelChainResolver.EntrySnapshot(
                        entry.getId(),
                        entry.getDisplayName(),
                        entry.getModelSpec(),
                        decryptKey(entry),
                        entry.getBaseUrl(),
                        entry.getEmbeddingSpec(),
                        Boolean.TRUE.equals(entry.getDefaultEntry()),
                        entry.getMode()))
                .toList();
    }

    private record ChainLink(Long id, String displayName, AgentModelSelection selection) {}

    /** 清除同「来源 × 模式」内的其他默认位（与 V62 的来源唯一索引同口径）。 */
    private void clearOtherDefaults(Long sourceAgentId, Long keepId, String mode) {
        LambdaQueryWrapper<AdminAiModelEntryEntity> query = new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                .eq(AdminAiModelEntryEntity::getDefaultEntry, true)
                .eq(AdminAiModelEntryEntity::getMode, mode);
        if (sourceAgentId == null) {
            query.isNull(AdminAiModelEntryEntity::getSourceAgentId);
        } else {
            query.eq(AdminAiModelEntryEntity::getSourceAgentId, sourceAgentId);
        }
        for (AdminAiModelEntryEntity current : modelEntryMapper.selectList(query)) {
            if (keepId != null && keepId.equals(current.getId())) {
                continue;
            }
            current.setDefaultEntry(false);
            modelEntryMapper.updateById(current);
        }
    }

    private AdminAiModelEntryEntity requireEntry(long id) {
        AdminAiModelEntryEntity entry = modelEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }

    /**
     * 派生行只读（Issue #214 UoW-2）：derived_source 非空的条目由同步维护，
     * 人工改/删一律 409 拦截，文案走 admin.ai.model-entry.derived-read-only。
     */
    private void requireNotDerived(AdminAiModelEntryEntity entry) {
        if (StringUtils.hasText(entry.getDerivedSource())) {
            throw new BusinessException(
                    ErrorCode.CONFLICT.getCode(), "admin.ai.model-entry.derived-read-only");
        }
    }

    /** 停用的入口不能占着默认位，否则下一条无法标成默认。 */
    private static boolean asDefault(AdminAiModelEntryWriteRequest request) {
        return Boolean.TRUE.equals(request.enabled()) && Boolean.TRUE.equals(request.defaultEntry());
    }

    private void apply(
            AdminAiModelEntryEntity entry,
            AdminAiModelEntryWriteRequest request,
            String apiKey,
            boolean asDefault,
            String alias) {
        entry.setDisplayName(request.displayName().trim());
        entry.setModelSpec(normalizeSpec(request.modelSpec()));
        entry.setApiKey(cipher.encrypt(apiKey));
        entry.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        // 入口平台化（Issue #210 UoW-6）：agent_id 停写恒空；来源标注 sourceAgentId
        // 只由 V62 迁移落、新建与编辑都不写（编辑时实体自带值原样回写）。
        entry.setAgentId(null);
        String mode = normalizeMode(request.mode());
        entry.setMode(mode);
        // CLI 的 key 是进代理的客户端 key，不能拿来做嵌入。
        entry.setEmbeddingSpec("CLI".equals(mode) ? null : normalizeOptionalSpec(request.embeddingSpec()));
        entry.setEnabled(request.enabled());
        entry.setDefaultEntry(asDefault);
        entry.setAlias(alias);
    }

    /** 空/空白 → null（清空 alias）；否则 trim 转小写并校验格式。对外模型名，供网关 UoW-2 选模型用。 */
    static String normalizeAlias(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.matches("^[a-z0-9][a-z0-9_-]{1,63}$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value;
    }

    /** 非空 alias 不得被另一条未删除入口占用；DB 部分唯一索引兜底并发竞态。 */
    private void requireAliasAvailable(String alias, Long excludeId) {
        if (alias == null) {
            return;
        }
        LambdaQueryWrapper<AdminAiModelEntryEntity> query =
                new LambdaQueryWrapper<AdminAiModelEntryEntity>().eq(AdminAiModelEntryEntity::getAlias, alias);
        if (excludeId != null) {
            query.ne(AdminAiModelEntryEntity::getId, excludeId);
        }
        if (modelEntryMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    /** 双模式归属：空缺省 KEY（直连 key）；只接受 KEY / CLI，其余拒绝。 */
    static String normalizeMode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "KEY";
        }
        String value = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!value.equals("KEY") && !value.equals("CLI")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value;
    }

    /** 只解密。明文旧数据保持原样，下次保存时才加密，避免读接口改版本号。 */
    private String decryptKey(AdminAiModelEntryEntity entry) {
        String plain = cipher.decrypt(entry.getApiKey());
        return StringUtils.hasText(plain) ? plain : null;
    }

    /** 空 mode 表示整链。CLI / KEY 以外拒绝，避免把拼错的模式当成整链。 */
    private static String chainMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return null;
        }
        String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"CLI".equals(normalized) && !"KEY".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return normalized;
    }

    static String apiKeyHint(String apiKey) {
        if (!StringUtils.hasText(apiKey) || apiKey.trim().length() < 8) {
            return "••••";
        }
        String trimmed = apiKey.trim();
        return "…" + trimmed.substring(trimmed.length() - 4);
    }

    private AdminAiModelEntryDto toDto(AdminAiModelEntryEntity entry) {
        String plain = decryptKey(entry);
        return new AdminAiModelEntryDto(
                entry.getId(),
                entry.getDisplayName(),
                entry.getModelSpec(),
                StringUtils.hasText(plain),
                apiKeyHint(plain),
                entry.getBaseUrl(),
                entry.getSourceAgentId(),
                entry.getEmbeddingSpec(),
                Boolean.TRUE.equals(entry.getEnabled()),
                Boolean.TRUE.equals(entry.getDefaultEntry()),
                entry.getVersion(),
                entry.getUpdatedAt(),
                normalizeMode(entry.getMode()),
                entry.getAlias(),
                StringUtils.hasText(entry.getDerivedSource()),
                entry.getDerivedSource());
    }

    private static String normalizeOptionalSpec(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return normalizeSpec(raw);
    }

    private static String normalizeSpec(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()
                || value.length() > 256
                || containsWhitespace(value)
                || value.contains("://")
                || value.contains("@")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value;
    }

    private static String normalizeBaseUrl(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        boolean http = value.startsWith("https://") || value.startsWith("http://");
        if (!http || value.length() > 512 || value.contains("@")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        return value;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
