package com.mywebsite.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiCliEntrySyncReportDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesPublishedAccountDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesStatusDto;
import com.mywebsite.admin.dto.AdminProxyServicesDtos.AdminProxyServicesUsageDto;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.service.AdminAiCliEntrySyncService;
import com.mywebsite.admin.service.AdminProxyServicesService;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * CLI 派生条目同步实现（Issue #214 UoW-2）。
 *
 * <p>期望集的推导与前端入口编辑器既有口径逐项对齐（modelEntriesCard 的
 * cliModels/cliModelSpec）：claude 账号放出全部模型名，其余账号过滤掉
 * claude 前缀的模型名；spec 含冒号原样用、不含冒号补 {@code openai:} 前缀。
 * upsert 键＝派生行集合内的 modelSpec（代理在账号间路由，条目不分账号，
 * 与人工 CLI 条目的既有语义一致）。
 *
 * <p>安全边界：只有拿到可信快照（代理可达且管理面已配置）才允许停用行；
 * 代理不可达时整体不动，避免代理临时离线把派生清单误清。消失模型只做
 * 停用不硬删：链预设成员 id 继续可解析（组链时按停用过滤，与人工停用同
 * 语义），不产生悬空引用。派生行不占默认位（人工在页面上另行指定）。
 */
@Service
public class AdminAiCliEntrySyncServiceImpl implements AdminAiCliEntrySyncService {

    /** derived_source 取值：CLI 代理派生。人工登记行为 null。 */
    static final String DERIVED_SOURCE_PROXY = "PROXY";

    private final AdminAiModelEntryMapper modelEntryMapper;
    private final AdminProxyServicesService proxyServicesService;
    private final ModelEntryCipher cipher;

    public AdminAiCliEntrySyncServiceImpl(
            AdminAiModelEntryMapper modelEntryMapper,
            AdminProxyServicesService proxyServicesService,
            ModelEntryCipher cipher) {
        this.modelEntryMapper = modelEntryMapper;
        this.proxyServicesService = proxyServicesService;
        this.cipher = cipher;
    }

    @Override
    @Transactional
    public AdminAiCliEntrySyncReportDto syncFromProxy() {
        AdminProxyServicesStatusDto status = proxyServicesService.status();
        if (!status.reachable() || !status.managementConfigured()) {
            return new AdminAiCliEntrySyncReportDto("PROXY_UNAVAILABLE", false, 0, 0, 0, 0, 0);
        }
        AdminProxyServicesUsageDto usage = proxyServicesService.usage();
        List<String> clientKeys = proxyServicesService.listApiKeys().apiKeys();
        String clientKey = clientKeys.isEmpty() ? null : clientKeys.get(0);

        Map<String, String> desired = desiredSpecs(usage.accounts());
        List<AdminAiModelEntryEntity> derivedRows = modelEntryMapper.selectList(
                new LambdaQueryWrapper<AdminAiModelEntryEntity>()
                        .eq(AdminAiModelEntryEntity::getMode, "CLI")
                        .eq(AdminAiModelEntryEntity::getDerivedSource, DERIVED_SOURCE_PROXY));
        Map<String, AdminAiModelEntryEntity> existingBySpec = new LinkedHashMap<>();
        for (AdminAiModelEntryEntity row : derivedRows) {
            existingBySpec.put(row.getModelSpec(), row);
        }

        int created = 0;
        int reactivated = 0;
        int deactivated = 0;
        int unchanged = 0;
        int skippedNoClientKey = 0;

        for (Map.Entry<String, String> want : desired.entrySet()) {
            String spec = want.getKey();
            AdminAiModelEntryEntity row = existingBySpec.remove(spec);
            if (row == null) {
                if (clientKey == null) {
                    skippedNoClientKey++;
                    continue;
                }
                AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
                entry.setDisplayName(want.getValue());
                entry.setModelSpec(spec);
                entry.setApiKey(cipher.encrypt(clientKey));
                entry.setBaseUrl(usage.apiBaseUrl());
                entry.setAgentId(null);
                entry.setEmbeddingSpec(null);
                entry.setEnabled(true);
                entry.setDefaultEntry(false);
                entry.setMode("CLI");
                entry.setAlias(null);
                entry.setDerivedSource(DERIVED_SOURCE_PROXY);
                modelEntryMapper.insert(entry);
                created++;
                continue;
            }
            boolean dirty = false;
            if (!Boolean.TRUE.equals(row.getEnabled())) {
                row.setEnabled(true);
                reactivated++;
                dirty = true;
            } else {
                unchanged++;
            }
            if (clientKey != null && !clientKey.equals(cipher.decrypt(row.getApiKey()))) {
                row.setApiKey(cipher.encrypt(clientKey));
                dirty = true;
            }
            if (!java.util.Objects.equals(row.getBaseUrl(), usage.apiBaseUrl())) {
                row.setBaseUrl(usage.apiBaseUrl());
                dirty = true;
            }
            if (dirty) {
                modelEntryMapper.updateById(row);
            }
        }
        // 期望集之外的派生行＝代理侧已消失的模型：只停用，不硬删（见类注释）。
        for (AdminAiModelEntryEntity stale : existingBySpec.values()) {
            if (Boolean.TRUE.equals(stale.getEnabled())) {
                stale.setEnabled(false);
                modelEntryMapper.updateById(stale);
                deactivated++;
            }
        }
        String statusText = skippedNoClientKey > 0 ? "NO_CLIENT_KEY" : "OK";
        return new AdminAiCliEntrySyncReportDto(
                statusText, true, created, reactivated, deactivated, unchanged, skippedNoClientKey);
    }

    /** 期望集：spec → 展示名（模型 id 原文）。推导口径见类注释。 */
    private Map<String, String> desiredSpecs(List<AdminProxyServicesPublishedAccountDto> accounts) {
        Map<String, String> desired = new LinkedHashMap<>();
        for (AdminProxyServicesPublishedAccountDto account : accounts) {
            if (account.disabled() || account.unavailable()) {
                continue;
            }
            for (String modelId : account.modelIds()) {
                if (!StringUtils.hasText(modelId)) {
                    continue;
                }
                if (!"claude".equals(account.provider())
                        && modelId.toLowerCase(Locale.ROOT).startsWith("claude")) {
                    continue;
                }
                String trimmed = modelId.trim();
                String spec = trimmed.contains(":") ? trimmed : "openai:" + trimmed;
                desired.putIfAbsent(spec, trimmed);
            }
        }
        return desired;
    }
}
