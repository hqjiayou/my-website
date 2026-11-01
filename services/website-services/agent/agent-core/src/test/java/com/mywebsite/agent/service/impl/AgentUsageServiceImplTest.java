package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mywebsite.agent.dto.AgentUsageRecord;
import com.mywebsite.agent.entity.AgentAiUsageEntity;
import com.mywebsite.agent.mapper.AgentAiUsageMapper;
import com.mywebsite.agent.service.AgentUsageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentUsageServiceImplTest {

    @Test
    void recordUsageMapsAllFieldsOntoTheEntity() {
        AgentAiUsageMapper mapper = mock(AgentAiUsageMapper.class);
        AgentUsageService service = new AgentUsageServiceImpl(mapper);

        service.recordUsage(new AgentUsageRecord(
                10L, 3L, "gpt-alias", "KEY", AgentUsageService.SOURCE_GATEWAY_V1, 12L, 7L, 345L,
                AgentUsageService.STATUS_SUCCESS, 9L));

        ArgumentCaptor<AgentAiUsageEntity> captor = ArgumentCaptor.forClass(AgentAiUsageEntity.class);
        verify(mapper).insert(captor.capture());
        AgentAiUsageEntity entity = captor.getValue();
        assertThat(entity.getAgentId()).isEqualTo(10L);
        assertThat(entity.getEntryId()).isEqualTo(3L);
        assertThat(entity.getAlias()).isEqualTo("gpt-alias");
        assertThat(entity.getMode()).isEqualTo("KEY");
        assertThat(entity.getSource()).isEqualTo("GATEWAY_V1");
        assertThat(entity.getPromptTokens()).isEqualTo(12L);
        assertThat(entity.getCompletionTokens()).isEqualTo(7L);
        assertThat(entity.getLatencyMs()).isEqualTo(345L);
        assertThat(entity.getStatus()).isEqualTo("SUCCESS");
        assertThat(entity.getGatewayAccessKeyId()).isEqualTo(9L);
    }

    @Test
    void recordUsageKeepsUnknownTokensNullInsteadOfZero() {
        AgentAiUsageMapper mapper = mock(AgentAiUsageMapper.class);
        AgentUsageService service = new AgentUsageServiceImpl(mapper);

        service.recordUsage(new AgentUsageRecord(
                null, null, null, null, AgentUsageService.SOURCE_GATEWAY_V1, null, null, null,
                AgentUsageService.STATUS_UPSTREAM_ERROR, null));

        ArgumentCaptor<AgentAiUsageEntity> captor = ArgumentCaptor.forClass(AgentAiUsageEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getPromptTokens()).isNull();
        assertThat(captor.getValue().getCompletionTokens()).isNull();
    }

    @Test
    void recordUsageSwallowsMapperFailures() {
        AgentAiUsageMapper mapper = mock(AgentAiUsageMapper.class);
        when(mapper.insert(any(AgentAiUsageEntity.class))).thenThrow(new IllegalStateException("db down"));
        AgentUsageService service = new AgentUsageServiceImpl(mapper);

        assertThatCode(() -> service.recordUsage(new AgentUsageRecord(
                        1L, 2L, null, "CLI", AgentUsageService.SOURCE_PORTAL, 1L, 1L, 1L,
                        AgentUsageService.STATUS_SUCCESS, null)))
                .doesNotThrowAnyException();
    }
}
