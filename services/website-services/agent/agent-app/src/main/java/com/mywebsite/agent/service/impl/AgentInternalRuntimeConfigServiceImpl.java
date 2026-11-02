package com.mywebsite.agent.service.impl;

import com.mywebsite.agent.dto.AgentInternalDtos.RuntimeConfigDto;
import com.mywebsite.agent.entity.AgentRuntimeConfigEntity;
import com.mywebsite.agent.mapper.AgentRuntimeConfigMapper;
import com.mywebsite.agent.service.AgentInternalRuntimeConfigService;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentInternalRuntimeConfigServiceImpl implements AgentInternalRuntimeConfigService {

    private final AgentRuntimeConfigMapper runtimeConfigMapper;
    private final ModelEntryCipher cipher;

    public AgentInternalRuntimeConfigServiceImpl(
            AgentRuntimeConfigMapper runtimeConfigMapper, ModelEntryCipher cipher) {
        this.runtimeConfigMapper = runtimeConfigMapper;
        this.cipher = cipher;
    }

    @Override
    public RuntimeConfigDto currentRuntimeConfig() {
        AgentRuntimeConfigEntity row = runtimeConfigMapper.selectById(AgentRuntimeConfigEntity.SINGLETON_ID);
        if (row == null || row.getConfigVersion() == null || row.getConfigVersion() <= 0) {
            // 页面从未保存过：明确回未配置，调用方据此沿用 env 基线，而非当作拉取失败。
            return new RuntimeConfigDto(false, null, null, null, null, 0L);
        }
        return new RuntimeConfigDto(
                true,
                decryptOrNull(row.getRetrievalDatabaseUrl()),
                row.getAiEmbeddingModel(),
                row.getAiEmbeddingDimensions(),
                decryptOrNull(row.getGeminiApiKey()),
                row.getConfigVersion());
    }

    /** 密文解不开时按未配置该项处理，不把密文当明文发出去（与 ModelEntryCipher 口径一致）。 */
    private String decryptOrNull(String stored) {
        String plain = cipher.decrypt(stored);
        return StringUtils.hasText(plain) ? plain : null;
    }
}
