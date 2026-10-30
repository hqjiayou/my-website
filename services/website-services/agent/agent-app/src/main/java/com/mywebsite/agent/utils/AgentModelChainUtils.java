package com.mywebsite.agent.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.entity.AgentAiModelEntryEntity;
import com.mywebsite.agent.mapper.AgentAiModelEntryMapper;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 平台模型入口生效链规则（与 admin {@code AdminAiModelEntryServiceImpl.selectionChain} 同规则，
 * 两处须一致）：站点 chat（{@code AgentAiServiceImpl}）与网关 {@code /v1} 转发
 * （{@code GatewayV1ServiceImpl}）共用这套查询与组装，不另写一份，防止规则分叉。
 *
 * <p>纯静态：不持有状态，调用方自带 {@code AgentAiModelEntryMapper}/{@code ModelEntryCipher}。
 */
public final class AgentModelChainUtils {

    private AgentModelChainUtils() {}

    /** 作用域内启用入口：默认在前，其余 id 升序。{@code agentId} 为 null 即全站作用域。 */
    public static List<AgentAiModelEntryEntity> enabledModelEntries(AgentAiModelEntryMapper mapper, Long agentId) {
        LambdaQueryWrapper<AgentAiModelEntryEntity> query = new LambdaQueryWrapper<AgentAiModelEntryEntity>()
                .eq(AgentAiModelEntryEntity::getEnabled, true)
                .orderByDesc(AgentAiModelEntryEntity::getDefaultEntry)
                .orderByAsc(AgentAiModelEntryEntity::getId);
        if (agentId == null) {
            query.isNull(AgentAiModelEntryEntity::getAgentId);
        } else {
            query.eq(AgentAiModelEntryEntity::getAgentId, agentId);
        }
        List<AgentAiModelEntryEntity> entries = mapper.selectList(query);
        return entries == null ? List.of() : entries;
    }

    public static AgentAiModelEntryEntity firstDefaultEntry(List<AgentAiModelEntryEntity> entries) {
        for (AgentAiModelEntryEntity entry : entries) {
            if (Boolean.TRUE.equals(entry.getDefaultEntry())) {
                return entry;
            }
        }
        return null;
    }

    /** {@code entries} 中除 {@code skipId} 外、解密成功的入口依次追加进 {@code chain}。 */
    public static void appendModelSelections(
            List<AgentModelSelection> chain,
            List<AgentAiModelEntryEntity> entries,
            Long skipId,
            ModelEntryCipher cipher) {
        for (AgentAiModelEntryEntity entry : entries) {
            if (entry.getId() != null && entry.getId().equals(skipId)) {
                continue;
            }
            AgentModelSelection selection = toModelSelection(entry, cipher);
            if (selection != null) {
                chain.add(selection);
            }
        }
    }

    /** 入口解密不出 key 或缺 modelSpec 时返回 null，调用方不得把坏入口顶上链。 */
    public static AgentModelSelection toModelSelection(AgentAiModelEntryEntity entry, ModelEntryCipher cipher) {
        if (entry == null || !StringUtils.hasText(entry.getModelSpec())) {
            return null;
        }
        String apiKey = cipher.decrypt(entry.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        String baseUrl = StringUtils.hasText(entry.getBaseUrl()) ? entry.getBaseUrl().trim() : null;
        String embeddingSpec = "CLI".equals(entryMode(entry)) || !StringUtils.hasText(entry.getEmbeddingSpec())
                ? null
                : entry.getEmbeddingSpec().trim();
        return new AgentModelSelection(entry.getModelSpec().trim(), apiKey, baseUrl, embeddingSpec);
    }

    /** 入口模式归属：空值按 KEY（与 V35 列默认同口径）。 */
    public static String entryMode(AgentAiModelEntryEntity entry) {
        return StringUtils.hasText(entry.getMode())
                ? entry.getMode().trim().toUpperCase(java.util.Locale.ROOT)
                : "KEY";
    }
}
