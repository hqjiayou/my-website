package com.mywebsite.admin.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.mywebsite.admin.dto.AdminAiModelEntryDtos.AdminAiModelEntryWriteRequest;
import com.mywebsite.admin.entity.AdminAiModelEntryEntity;
import com.mywebsite.admin.mapper.AdminAiModelEntryMapper;
import com.mywebsite.admin.mapper.AdminAgentMapper;
import com.mywebsite.agent.dto.AgentModelSelection;
import com.mywebsite.agent.service.AgentEmbeddingClassService;
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

    @Mock
    private AgentEmbeddingClassService embeddingClassService;

    private AdminAiModelEntryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminAiModelEntryServiceImpl(modelEntryMapper, adminAgentMapper, cipher, embeddingClassService);
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
    void softDeleteByAgentIdSoftDeletesEveryScopedEntry() {
        AdminAiModelEntryEntity first = entry(1, 7L, "openai:a", true);
        AdminAiModelEntryEntity second = entry(2, 7L, "openai:b", false);
        when(modelEntryMapper.selectList(any())).thenReturn(List.of(first, second));

        service.softDeleteByAgentId(7L);

        assertThat(first.getDeletedAt()).isNotNull();
        assertThat(second.getDeletedAt()).isNotNull();
        org.mockito.Mockito.verify(modelEntryMapper, org.mockito.Mockito.times(2))
                .updateById(any(AdminAiModelEntryEntity.class));
        org.mockito.Mockito.verify(modelEntryMapper).deleteById(1L);
        org.mockito.Mockito.verify(modelEntryMapper).deleteById(2L);
    }

    // 嵌入类自 Issue #126 UoW-2 起改读登记：本类只委托 agent-core 共享服务，
    // 类的选择/合并/dedupe/物化语义由 AgentEmbeddingClassServiceImplTest 钉死；
    // 这里只钉委托本身与 embeddingSelection 的首元语义。
    @Test
    void embeddingClassesDelegateToRegistrationService() {
        List<AgentModelSelection> registered =
                List.of(
                        new AgentModelSelection(
                                "google:gemini-3-flash-preview", "key-5", null, "google:gemini-embedding-001"),
                        new AgentModelSelection("openai:chat", "key-9", null, "openai:text-embedding-3-small"));
        when(embeddingClassService.embeddingClasses(10L)).thenReturn(registered);

        assertThat(service.embeddingClasses(10L)).isSameAs(registered);
    }

    @Test
    void embeddingSelectionIsFirstRegisteredClass() {
        AgentModelSelection first =
                new AgentModelSelection("google:gemini-3-flash-preview", "key-5", null, "google:gemini-embedding-001");
        AgentModelSelection second =
                new AgentModelSelection("openai:chat", "key-9", null, "openai:text-embedding-3-small");
        when(embeddingClassService.embeddingClasses(10L)).thenReturn(List.of(first, second));

        AgentModelSelection selection = service.embeddingSelection(10L);

        assertThat(selection).isSameAs(first);
        assertThat(selection.embeddingSpec()).isEqualTo("google:gemini-embedding-001");
        assertThat(selection.apiKey()).isEqualTo("key-5");
    }

    @Test
    void embeddingSelectionIsNullWhenNoRegisteredClasses() {
        when(embeddingClassService.embeddingClasses(10L)).thenReturn(List.of());

        assertThat(service.embeddingSelection(10L)).isNull();
        assertThat(service.embeddingClasses(10L)).isEmpty();
    }

    @Test
    void chainStripsEmbeddingSpecFromCliEntry() {
        AdminAiModelEntryEntity cli = entry(2L, 10L, "openai:gpt", true);
        cli.setMode("CLI");
        cli.setEmbeddingSpec("google:gemini-embedding-001");
        when(modelEntryMapper.selectList(any())).thenReturn(List.of(cli), List.of());

        List<AgentModelSelection> chain = service.selectionChain(10L);
        assertThat(chain).hasSize(1);
        assertThat(chain.get(0).embeddingSpec()).isNull();
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
    void chainStaysInsideTheRequestedMode() {
        AdminAiModelEntryEntity cli = entry(2L, 10L, "openai:muse-wonder-1.0", true);
        cli.setMode("CLI");
        AdminAiModelEntryEntity key = entry(1L, null, "google:gemini-3-flash-preview", true);
        key.setMode("KEY");
        when(modelEntryMapper.selectList(any()))
                .thenReturn(List.of(cli), List.of(key), List.of(cli), List.of(key));

        assertThat(specs(service.selectionChain(10L, "CLI"))).containsExactly("openai:muse-wonder-1.0");
        assertThat(specs(service.selectionChain(10L, "key"))).containsExactly("google:gemini-3-flash-preview");
    }

    @Test
    void chainRejectsUnknownMode() {
        assertThatThrownBy(() -> service.selectionChain(10L, "BOGUS")).isInstanceOf(BusinessException.class);
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

    @Test
    void normalizeAliasBlankReturnsNull() {
        assertThat(AdminAiModelEntryServiceImpl.normalizeAlias(null)).isNull();
        assertThat(AdminAiModelEntryServiceImpl.normalizeAlias("   ")).isNull();
    }

    @Test
    void normalizeAliasTrimsAndLowercases() {
        assertThat(AdminAiModelEntryServiceImpl.normalizeAlias(" Gpt-4o_Mini ")).isEqualTo("gpt-4o_mini");
    }

    @Test
    void normalizeAliasRejectsInvalidFormat() {
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeAlias("A"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeAlias("-leading-dash"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AdminAiModelEntryServiceImpl.normalizeAlias("has space"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createEntryRejectsAliasAlreadyTakenByAnotherEntry() {
        when(modelEntryMapper.selectCount(any())).thenReturn(1L);

        AdminAiModelEntryWriteRequest request = new AdminAiModelEntryWriteRequest(
                "Display",
                "google:gemini-3-flash-preview",
                "sk-live-key",
                null,
                null,
                null,
                true,
                false,
                null,
                null,
                "taken-alias");

        assertThatThrownBy(() -> service.createEntry(request)).isInstanceOf(BusinessException.class);
    }
}
