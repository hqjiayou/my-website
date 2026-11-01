package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminAiUsageDtos.AdminAiUsageReportDto;
import com.mywebsite.agent.entity.AgentAiUsageEntity;
import com.mywebsite.agent.mapper.AgentAiUsageMapper;
import com.mywebsite.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminAiUsageServiceImplTest {

    @Test
    void reportAggregatesDailyByAgentAndByModelAndKeepsUnknownTokensVisible() {
        AgentAiUsageMapper mapper = mock(AgentAiUsageMapper.class);
        OffsetDateTime today = OffsetDateTime.now(ZoneOffset.UTC).withHour(10).withMinute(0).withSecond(0).withNano(0);
        AgentAiUsageEntity first = row(1L, today, "PORTAL", 10L, 3L, null, 100L, 50L);
        AgentAiUsageEntity second = row(2L, today, "GATEWAY_V1", null, 3L, "gpt-alias", null, null);
        AgentAiUsageEntity third = row(3L, today.minusDays(1), "PORTAL", 10L, 4L, null, 10L, 5L);
        when(mapper.selectList(any())).thenReturn(List.of(second, first, third));
        AdminAiUsageServiceImpl service = new AdminAiUsageServiceImpl(mapper);

        AdminAiUsageReportDto report = service.usageReport(null, 14, 50);

        assertThat(report.daily()).hasSize(2);
        assertThat(report.daily().get(1).date()).isEqualTo(today.toLocalDate());
        assertThat(report.daily().get(1).calls()).isEqualTo(2);
        assertThat(report.daily().get(1).promptTokens()).isEqualTo(100L);
        assertThat(report.daily().get(1).completionTokens()).isEqualTo(50L);
        // 第二行 tokens 为 NULL：不并入合计，但要计入 unknownTokenCalls。
        assertThat(report.daily().get(1).unknownTokenCalls()).isEqualTo(1);

        assertThat(report.byAgent()).hasSize(2);
        assertThat(report.byAgent().get(0).agentId()).isEqualTo(10L);
        assertThat(report.byAgent().get(0).calls()).isEqualTo(2);

        assertThat(report.byModel()).hasSize(2);
        assertThat(report.byModel().get(0).entryId()).isEqualTo(3L);
        assertThat(report.byModel().get(0).calls()).isEqualTo(2);
        assertThat(report.byModel().get(0).alias()).isEqualTo("gpt-alias");

        // 明细按查询返回顺序（时间倒序）截到 limit。
        assertThat(report.recent()).hasSize(3);
        assertThat(report.recent().get(0).id()).isEqualTo(2L);
    }

    @Test
    void reportRejectsUnknownSource() {
        AgentAiUsageMapper mapper = mock(AgentAiUsageMapper.class);
        AdminAiUsageServiceImpl service = new AdminAiUsageServiceImpl(mapper);

        assertThatThrownBy(() -> service.usageReport("carrier-pigeon", 14, 50))
                .isInstanceOf(BusinessException.class);
    }

    private static AgentAiUsageEntity row(
            Long id,
            OffsetDateTime createdAt,
            String source,
            Long agentId,
            Long entryId,
            String alias,
            Long promptTokens,
            Long completionTokens) {
        AgentAiUsageEntity entity = new AgentAiUsageEntity();
        entity.setId(id);
        entity.setCreatedAt(createdAt);
        entity.setSource(source);
        entity.setAgentId(agentId);
        entity.setEntryId(entryId);
        entity.setAlias(alias);
        entity.setMode("KEY");
        entity.setPromptTokens(promptTokens);
        entity.setCompletionTokens(completionTokens);
        entity.setLatencyMs(10L);
        entity.setStatus("SUCCESS");
        return entity;
    }
}
