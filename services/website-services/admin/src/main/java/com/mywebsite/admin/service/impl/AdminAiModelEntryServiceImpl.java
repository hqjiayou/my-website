package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.constant.AdminSiteMessageKeys;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryDto;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.mapper.AdminSiteMapper;
import com.mywebsite.admin.service.AdminAiModelEntryService;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminAiModelEntryServiceImpl implements AdminAiModelEntryService {

    private final AdminAiModelEntryMapper modelEntryMapper;
    private final AdminSiteMapper adminSiteMapper;
    private final ModelEntryCipher cipher;

    public AdminAiModelEntryServiceImpl(
            AdminAiModelEntryMapper modelEntryMapper,
            AdminSiteMapper adminSiteMapper,
            ModelEntryCipher cipher) {
        this.modelEntryMapper = modelEntryMapper;
        this.adminSiteMapper = adminSiteMapper;
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
        requireSiteIfPresent(request.siteId());
        boolean asDefault = asDefault(request);
        if (asDefault) {
            clearOtherDefaults(request.siteId(), null);
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
        requireSiteIfPresent(request.siteId());
        AdminAiModelEntryEntity entry = requireEntry(id);
        boolean asDefault = asDefault(request);
        if (asDefault) {
            clearOtherDefaults(request.siteId(), id);
        }
        String apiKey = StringUtils.hasText(request.apiKey()) ? request.apiKey().trim() : plainKey(entry);
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
    public AgentModelSelection currentSelection(long siteId) {
        AdminAiModelEntryEntity entry = selectDefault(siteId);
        if (entry == null) {
            entry = selectDefault(null);
        }
        return toSelection(entry);
    }

    private AdminAiModelEntryEntity selectDefault(Long siteId) {
        LambdaQueryWrapper<AdminAiModelEntryEntity> query = new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                .eq(AdminAiModelEntryEntity::getEnabled, true)
                .eq(AdminAiModelEntryEntity::getDefaultEntry, true);
        if (siteId == null) {
            query.isNull(AdminAiModelEntryEntity::getSiteId);
        } else {
            query.eq(AdminAiModelEntryEntity::getSiteId, siteId);
        }
        return modelEntryMapper.selectOne(query.last("LIMIT 1"));
    }

    private void clearOtherDefaults(Long siteId, Long keepId) {
        LambdaQueryWrapper<AdminAiModelEntryEntity> query = new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                .eq(AdminAiModelEntryEntity::getDefaultEntry, true);
        if (siteId == null) {
            query.isNull(AdminAiModelEntryEntity::getSiteId);
        } else {
            query.eq(AdminAiModelEntryEntity::getSiteId, siteId);
        }
        for (AdminAiModelEntryEntity current : modelEntryMapper.selectList(query)) {
            if (keepId != null && keepId.equals(current.getId())) {
                continue;
            }
            current.setDefaultEntry(false);
            modelEntryMapper.updateById(current);
        }
    }

    private void requireSiteIfPresent(Long siteId) {
        if (siteId == null) {
            return;
        }
        if (adminSiteMapper.selectById(siteId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), AdminSiteMessageKeys.SITE_NOT_FOUND);
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
        entry.setSiteId(request.siteId());
        entry.setEmbeddingSpec(normalizeOptionalSpec(request.embeddingSpec()));
        entry.setEnabled(request.enabled());
        entry.setDefaultEntry(asDefault);
    }

    private String plainKey(AdminAiModelEntryEntity entry) {
        String plain = cipher.decrypt(entry.getApiKey());
        if (!StringUtils.hasText(plain)) {
            return null;
        }
        if (cipher.canEncrypt() && !cipher.isEncrypted(entry.getApiKey())) {
            entry.setApiKey(cipher.encrypt(plain));
            modelEntryMapper.updateById(entry);
        }
        return plain;
    }

    private AgentModelSelection toSelection(AdminAiModelEntryEntity entry) {
        if (entry == null || !StringUtils.hasText(entry.getModelSpec())) {
            return null;
        }
        String apiKey = plainKey(entry);
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
        String plain = plainKey(entry);
        return new AdminAiModelEntryDto(
                entry.getId(),
                entry.getDisplayName(),
                entry.getModelSpec(),
                StringUtils.hasText(plain),
                apiKeyHint(plain),
                entry.getBaseUrl(),
                entry.getSiteId(),
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
