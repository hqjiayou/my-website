package com.mywebsite.agent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mywebsite.agent.dto.AgentMcpSelection;
import com.mywebsite.agent.entity.AgentAiMcpSelectionEntity;
import com.mywebsite.agent.mapper.AgentAiMcpSelectionMapper;
import com.mywebsite.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MCP per-agent 选择（Issue #192 UoW-3）：三态解析、无行 INHERIT、保存校验
 * （CUSTOM 必须有名字、非法 mode 拒绝）、名字去重保序。
 */
class AgentMcpSelectionServiceImplTest {

    private AgentAiMcpSelectionMapper mapper;
    private AgentMcpSelectionServiceImpl service;
    private AgentAiMcpSelectionEntity stored;

    @BeforeEach
    void setUp() {
        mapper = mock(AgentAiMcpSelectionMapper.class);
        service = new AgentMcpSelectionServiceImpl(mapper, new ObjectMapper());
        when(mapper.selectById(7L)).thenAnswer(invocation -> stored);
        when(mapper.insert(any(AgentAiMcpSelectionEntity.class))).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            return 1;
        });
        when(mapper.updateById(any(AgentAiMcpSelectionEntity.class))).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            return 1;
        });
    }

    private AgentAiMcpSelectionEntity row(String mode, String namesJson) {
        AgentAiMcpSelectionEntity entity = new AgentAiMcpSelectionEntity();
        entity.setAgentId(7L);
        entity.setMode(mode);
        entity.setServerNames(namesJson);
        return entity;
    }

    @Test
    void noRowMeansInheritAndWireNamesNull() {
        stored = null;
        assertThat(service.getSelection(7L).mode()).isEqualTo("INHERIT");
        assertThat(service.resolveWireNames(7L)).isNull();
    }

    @Test
    void offWireNamesEmptyList() {
        stored = row("OFF", null);
        assertThat(service.resolveWireNames(7L)).isEqualTo(List.of());
    }

    @Test
    void customWireNamesParsedList() {
        stored = row("CUSTOM", "[\"docs\",\"echo\"]");
        AgentMcpSelection selection = service.getSelection(7L);
        assertThat(selection.serverNames()).containsExactly("docs", "echo");
        assertThat(service.resolveWireNames(7L)).containsExactly("docs", "echo");
    }

    @Test
    void saveCustomDedupesAndRoundTrips() {
        AgentMcpSelection saved = service.saveSelection(7L, "custom", List.of("docs", "docs", " echo "));
        assertThat(saved.mode()).isEqualTo("CUSTOM");
        assertThat(saved.serverNames()).containsExactly("docs", "echo");
        assertThat(stored.getServerNames()).isEqualTo("[\"docs\",\"echo\"]");
    }

    @Test
    void saveInheritClearsNames() {
        stored = row("CUSTOM", "[\"docs\"]");
        AgentMcpSelection saved = service.saveSelection(7L, "INHERIT", List.of("docs"));
        assertThat(saved.mode()).isEqualTo("INHERIT");
        assertThat(stored.getServerNames()).isNull();
        assertThat(service.resolveWireNames(7L)).isNull();
    }

    @Test
    void saveCustomWithoutNamesRejected() {
        assertThatThrownBy(() -> service.saveSelection(7L, "CUSTOM", List.of()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void saveUnknownModeRejected() {
        assertThatThrownBy(() -> service.saveSelection(7L, "SOMETIMES", List.of()))
                .isInstanceOf(BusinessException.class);
    }
}
