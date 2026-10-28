package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.common.exception.BusinessException;
import com.mywebsite.infra.crypto.ModelEntryCipher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 生效链顺序：站预设 → 站其他启用 → 全站预设（或站预设缺位时作主用）→ 全站其他启用；坏主用整链不启用。 */
@ExtendWith(MockitoExtension.class)
class AdminAiModelEntryServiceImplTest {

    @Mock
    private AdminAiModelEntryMapper modelEntryMapper;

    @Mock
    private AdminAgentMapper adminAgentMapper;

    @Mock
    private ModelEntryCipher cipher;

    private AdminAiModelEntryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiModelEntryServiceImpl(modelEntryMapper, adminAgentMapper, cipher);
        // 无默认入口的用例走不到解密，stub 放宽避免 strict stub 误报
        lenient().when(cipher.decrypt(any())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            return raw != null && raw.startsWith("enc:") ? raw.substring(4) : raw;
        });
    }

    private static AdminAiModelEntryEntity entry(long id, Long agentId, String spec, boolean isDefault) {
        AdminAiModelEntryEntity entry = new AdminAiModelEntryEntity();
        entry.setId(id);
        entry.setAgentId(agentId);
        entry.setModelSpec(spec);
        entry.setApiKey("enc:key-" + id);
        entry.setEnabled(true);
        entry.setDefaultEntry(isDefault);
        return entry;
    }

    private static List<String> specs(List<AgentModelSelection> chain) {
        return chain.stream().map(AgentModelSelection::spec).toList();
    }

    @Test
    void chainOrdersAgentDefaultThenAgentOthersThenGlobal() {
        // mapper 已按「默认在前、id 升序」返回，service 只负责组链
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-default", true),
                                entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(
                                entry(1L, null, "google:global-default", true),
                                entry(2L, null, "anthropic:global-alt", false)));

        List<AgentModelSelection> chain = service.selectionChain(10L);

        assertThat(specs(chain))
                .containsExactly(
                        "google:agent-default", "openai:agent-alt", "google:global-default", "anthropic:global-alt");
        assertThat(chain.get(0).apiKey()).isEqualTo("key-3");
    }

    @Test
    void currentSelectionReturnsChainHead() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(entry(3L, 10L, "google:agent-default", true)),
                        List.of(entry(1L, null, "google:global-default", true)));

        assertThat(service.currentSelection(10L).spec()).isEqualTo("google:agent-default");
    }

    @Test
    void chainFallsToGlobalDefaultWhenAgentHasNoDefault() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(
                                entry(1L, null, "google:global-default", true),
                                entry(2L, null, "anthropic:global-alt", false)));

        List<AgentModelSelection> chain = service.selectionChain(10L);

        assertThat(specs(chain))
                .containsExactly("google:global-default", "openai:agent-alt", "anthropic:global-alt");
    }

    @Test
    void chainIsEmptyWithoutAnyDefault() {
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(entry(2L, null, "anthropic:global-alt", false)));

        assertThat(service.selectionChain(10L)).isEmpty();
        assertThat(service.currentSelection(10L)).isNull();
    }

    @Test
    void chainIsEmptyWhenPrimaryKeyUndecryptable() {
        when(cipher.decrypt("enc:key-3")).thenReturn("");
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-default", true),
                                entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(entry(1L, null, "google:global-default", true)));

        // 主用坏掉时备援不得顶上，整链不启用
        assertThat(service.selectionChain(10L)).isEmpty();
    }

    @Test
    void chainSkipsUndecryptableFallbacks() {
        when(cipher.decrypt("enc:key-4")).thenReturn("");
        when(modelEntryMapper.selectList(any()))
                .thenReturn(
                        List.of(
                                entry(3L, 10L, "google:agent-default", true),
                                entry(4L, 10L, "openai:agent-alt", false)),
                        List.of(entry(1L, null, "google:global-default", true)));

        assertThat(specs(service.selectionChain(10L)))
                .containsExactly("google:agent-default", "google:global-default");
    }

    @Test
    void normalizeModeDefaultsToKeyAndAcceptsCli() {
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode(null)).isEqualTo("KEY");
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode(" ")).isEqualTo("KEY");
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode(" cli ")).isEqualTo("CLI");
        assertThat(AdminAiModelEntryServiceImpl.normalizeMode("key")).isEqualTo("KEY");
    }

    @Test
    void normalizeModeRejectsUnknown() {
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeMode("BOGUS"))
                .isInstanceOf(BusinessException.class);
    }
}
