package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminAgentMessageKeys;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.admin.service.AdminAiModelEntryService;
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
            clearOtherDefaults(request.agentId(), null);
        }
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        apply(entry, request, request.apiKey().trim(), asDefault);
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
            clearOtherDefaults(request.agentId(), id);
        }
        String apiKey = StringUtils.hasText(request.apiKey()) ? request.apiKey().trim() : decryptKey(entry);
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        apply(entry, request, apiKey, asDefault);
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
        List<AdminAiModelEntryEntity> agentEntries = enabledEntries(agentId);
        List<AdminAiModelEntryEntity> globalEntries = enabledEntries(null);
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
        List<AgentModelSelection> chain = new ArrayList<>();
        chain.add(primarySelection);
        appendSelections(chain, agentEntries, primary.getId());
        appendSelections(chain, globalEntries, primary.getId());
        return List.copyOf(chain);
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

    private void appendSelections(
            List<AgentModelSelection> chain, List<AdminAiModelEntryEntity> entries, Long skipId) {
        for (AdminAiModelEntryEntity entry : entries) {
            if (entry.getId() != null && entry.getId().equals(skipId)) {
                continue;
            }
            AgentModelSelection selection = toSelection(entry);
            if (selection != null) {
                chain.add(selection);
            }
        }
    }

    private void clearOtherDefaults(Long agentId, Long keepId) {
        LambdaQueryWrapper<AdminAiModelEntryEntity> query = new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                .eq(AdminAiModelEntryEntity::getDefaultEntry, true);
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
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminAgentMessageKeys.SITE_NOT_FOUND);
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
            boolean asDefault) {
        entry.setDisplayName(request.displayName().trim());
        entry.setModelSpec(normalizeSpec(request.modelSpec()));
        entry.setApiKey(cipher.encrypt(apiKey));
        entry.setBaseUrl(normalizeBaseUrl(request.baseUrl()));
        entry.setAgentId(request.agentId());
        entry.setEmbeddingSpec(normalizeOptionalSpec(request.embeddingSpec()));
        entry.setEnabled(request.enabled());
        entry.setDefaultEntry(asDefault);
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
        String embeddingSpec = StringUtils.hasText(entry.getEmbeddingSpec()) ? entry.getEmbeddingSpec().trim() : null;
        return new AgentModelSelection(entry.getModelSpec().trim(), apiKey, baseUrl, embeddingSpec);
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
                entry.getUpdatedAt());
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
