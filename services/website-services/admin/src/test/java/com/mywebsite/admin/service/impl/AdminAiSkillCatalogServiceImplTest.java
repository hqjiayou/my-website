package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminAiSkillCatalogDtos.AdminAiSkillCatalogDto;
import com.mywebsite.agent.client.AgentClient;
import com.mywebsite.agent.dto.AgentSkillCatalog;
import com.mywebsite.common.enums.ErrorCode;
import com.mywebsite.common.exception.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Skills 货架服务（Issue #214 UoW-6）：平台级目录映射——条目元数据全量透传
 * （含 allowed_tools 与自选开关状态），agent-services 异常原样透传不吞。
 */
class AdminAiSkillCatalogServiceImplTest {

    private AgentClient agentClient;
    private AdminAiSkillCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        agentClient = mock(AgentClient.class);
        service = new AdminAiSkillCatalogServiceImpl(agentClient);
    }

    @Test
    void mapsCatalogEntriesAndSelfSelectFlag() {
        when(agentClient.skillCatalog())
                .thenReturn(new AgentSkillCatalog(
                        List.of(new AgentSkillCatalog.Entry(
                                "doc-qa-cited",
                                "answer with citations",
                                "1",
                                null,
                                null,
                                Map.of("tier", "core"),
                                List.of("search_site_knowledge"))),
                        true));

        AdminAiSkillCatalogDto dto = service.getCatalog();

        assertThat(dto.skillsSelfSelectEnabled()).isTrue();
        assertThat(dto.skills()).hasSize(1);
        assertThat(dto.skills().get(0).id()).isEqualTo("doc-qa-cited");
        assertThat(dto.skills().get(0).description()).isEqualTo("answer with citations");
        assertThat(dto.skills().get(0).metadata()).containsEntry("tier", "core");
        assertThat(dto.skills().get(0).allowedTools()).containsExactly("search_site_knowledge");
    }

    @Test
    void propagatesAgentServicesFailure() {
        when(agentClient.skillCatalog())
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED));

        assertThatThrownBy(() -> service.getCatalog())
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getCode())
                                .isEqualTo(ErrorCode.EXTERNAL_DEPENDENCY_BLOCKED.getCode()));
    }
}
