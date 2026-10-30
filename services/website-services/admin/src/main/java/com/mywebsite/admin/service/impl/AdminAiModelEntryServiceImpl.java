package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.admin.service.AdminAiModelEntryService.ChainCandidate;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiModelEntryServiceImpl implements AdminAiModelEntryService {

    private final AdminAiModelEntryMapper modelEntryMapper;
    private final AdminAgentMapper adminAgentMapper;
    private final ModelEntryCipher cipher;

    public AdminAiModelEntryServiceImpl(
            AdminAiModelEntryMapper modelEntryMapper,
            AdminAgentMapper adminAgentMapper,
            ModelEntryCipher cipher) {
        this.modelEntryMapper = modelEntryMapper;
        this.adminAgentMapper = adminAgentMapper;
        this.cipher = cipher;
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
        requireAgentIfPresent(request.agentId());
        boolean asDefault = asDefault(request);
        if (asDefault) {
            clearOtherDefaults(request.agentId(), null, normalizeMode(request.mode()));
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
        requireAgentIfPresent(request.agentId());
        AdminAiModelEntryEntity entry = requireEntry(id);
        boolean asDefault = asDefault(request);
        if (asDefault) {
            clearOtherDefaults(request.agentId(), id, normalizeMode(request.mode()));
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
     * 组链规则与 agent-app {@code AgentAiServiceImpl.loadModelChain} 一致。
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
     * 组链规则与 {@link #selectionChain(long, String)} 的原实现一致，并带上菜单需要的 id 与名称。
     */
    private List<ChainLink> chainLinks(long agentId, String mode) {
        String normalized = chainMode(mode);
        List<AdminAiModelEntryEntity> agentEntries = filterMode(enabledEntries(agentId), normalized);
        List<AdminAiModelEntryEntity> globalEntries = filterMode(enabledEntries(null), normalized);
        AdminAiModelEntryEntity primary = firstDefault(agentEntries);
        if (primary == null) {
            primary = firstDefault(globalEntries);
        }
        if (primary == null) {
            return List.of();
        }
        AgentModelSelection primarySelection = toSelection(primary);
        if (primarySelection == null) {
            // 主用坏掉（无 spec / 解密不出 key）时整链不启用，与 currentSelection 旧口径一致：
            // 备援不得越过坏主用顶上，否则坏 key 会被静默掩盖。
            return List.of();
        }
        List<ChainLink> chain = new ArrayList<>();
        chain.add(new ChainLink(primary.getId(), candidateName(primary), primarySelection));
        appendLinks(chain, agentEntries, primary.getId());
        appendLinks(chain, globalEntries, primary.getId());
        return List.copyOf(chain);
    }

    @Override
    public AgentModelSelection embeddingSelection(long agentId) {
        AgentModelSelection fromAgent = firstKeyEmbedding(enabledEntries(agentId));
        if (fromAgent != null) {
            return fromAgent;
        }
        return firstKeyEmbedding(enabledEntries(null));
    }

    /** 启用列表已是默认在前、id 升序。跳过 CLI 和没填 embedding spec 的入口。 */
    private AgentModelSelection firstKeyEmbedding(List<AdminAiModelEntryEntity> entries) {
        for (AdminAiModelEntryEntity entry : entries) {
            if (!"KEY".equals(entryMode(entry)) || !StringUtils.hasText(entry.getEmbeddingSpec())) {
                continue;
            }
            AgentModelSelection selection = toSelection(entry);
            if (selection != null && StringUtils.hasText(selection.embeddingSpec())) {
                return selection;
            }
        }
        return null;
    }

    /** 作用域内启用入口：默认在前，其余 id 升序（与 listEntries 的展示序一致）。 */
    private List<AdminAiModelEntryEntity> enabledEntries(Long agentId) {
        LambdaQueryWrapper<AdminAiModelEntryEntity> query = new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                .eq(AdminAiModelEntryEntity::getEnabled, true)
                .orderByDesc(AdminAiModelEntryEntity::getDefaultEntry)
                .orderByAsc(AdminAiModelEntryEntity::getId);
        if (agentId == null) {
            query.isNull(AdminAiModelEntryEntity::getAgentId);
        } else {
            query.eq(AdminAiModelEntryEntity::getAgentId, agentId);
        }
        return modelEntryMapper.selectList(query);
    }

    private static AdminAiModelEntryEntity firstDefault(List<AdminAiModelEntryEntity> entries) {
        for (AdminAiModelEntryEntity entry : entries) {
            if (Boolean.TRUE.equals(entry.getDefaultEntry())) {
                return entry;
            }
        }
        return null;
    }

    private void appendLinks(List<ChainLink> chain, List<AdminAiModelEntryEntity> entries, Long skipId) {
        for (AdminAiModelEntryEntity entry : entries) {
            if (entry.getId() != null && entry.getId().equals(skipId)) {
                continue;
            }
            AgentModelSelection selection = toSelection(entry);
            if (selection != null) {
                chain.add(new ChainLink(entry.getId(), candidateName(entry), selection));
            }
        }
    }

    private static String candidateName(AdminAiModelEntryEntity entry) {
        if (StringUtils.hasText(entry.getDisplayName())) {
            return entry.getDisplayName().trim();
        }
        return entry.getModelSpec().trim();
    }

    private record ChainLink(Long id, String displayName, AgentModelSelection selection) {}

    private void clearOtherDefaults(Long agentId, Long keepId, String mode) {
        LambdaQueryWrapper<AdminAiModelEntryEntity> query = new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                .eq(AdminAiModelEntryEntity::getDefaultEntry, true)
                .eq(AdminAiModelEntryEntity::getMode, mode);
        if (agentId == null) {
            query.isNull(AdminAiModelEntryEntity::getAgentId);
        } else {
            query.eq(AdminAiModelEntryEntity::getAgentId, agentId);
        }
        for (AdminAiModelEntryEntity current : modelEntryMapper.selectList(query)) {
            if (keepId != null && keepId.equals(current.getId())) {
                continue;
            }
            current.setDefaultEntry(false);
            modelEntryMapper.updateById(current);
        }
    }

    private void requireAgentIfPresent(Long agentId) {
        if (agentId == null) {
            return;
        }
        if (adminAgentMapper.selectById(agentId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.AGENT_NOT_FOUND);
        }
    }

    private AdminAiModelEntryEntity requireEntry(long id) {
        AdminAiModelEntryEntity entry = modelEntryMapper.selectById(id);
        if (entry == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return entry;
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
        entry.setAgentId(request.agentId());
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

    private AgentModelSelection toSelection(AdminAiModelEntryEntity entry) {
        if (entry == null || !StringUtils.hasText(entry.getModelSpec())) {
            return null;
        }
        String apiKey = decryptKey(entry);
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String baseUrl = StringUtils.hasText(entry.getBaseUrl()) ? entry.getBaseUrl().trim() : null;
        return new AgentModelSelection(entry.getModelSpec().trim(), apiKey, baseUrl, embeddingSpecFor(entry));
    }

    /** CLI 客户端 key 只用于回答，不随入口带进嵌入。 */
    private static String embeddingSpecFor(AdminAiModelEntryEntity entry) {
        if ("CLI".equals(entryMode(entry)) || !StringUtils.hasText(entry.getEmbeddingSpec())) {
            return null;
        }
        return entry.getEmbeddingSpec().trim();
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

    private static List<AdminAiModelEntryEntity> filterMode(
            List<AdminAiModelEntryEntity> entries, String mode) {
        if (mode == null) {
            return entries;
        }
        return entries.stream().filter(entry -> mode.equals(entryMode(entry))).toList();
    }

    private static String entryMode(AdminAiModelEntryEntity entry) {
        return StringUtils.hasText(entry.getMode())
                ? entry.getMode().trim().toUpperCase(java.util.Locale.ROOT)
                : "KEY";
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
                entry.getAgentId(),
                entry.getEmbeddingSpec(),
                Boolean.TRUE.equals(entry.getEnabled()),
                Boolean.TRUE.equals(entry.getDefaultEntry()),
                entry.getVersion(),
                entry.getUpdatedAt(),
                normalizeMode(entry.getMode()),
                entry.getAlias());
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
